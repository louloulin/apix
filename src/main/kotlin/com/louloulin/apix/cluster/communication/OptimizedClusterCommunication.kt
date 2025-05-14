package com.louloulin.apix.cluster.communication

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * 优化的集群通信实现，提供消息压缩、批处理和高效序列化
 */
class OptimizedClusterCommunication(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(OptimizedClusterCommunication::class.java)

    // 压缩级别 (0-9)，0表示不压缩，9表示最大压缩
    private var compressionLevel = 6

    // 是否启用压缩
    private var compressionEnabled = true

    // 压缩阈值（字节），小于此值的消息不压缩
    private var compressionThreshold = 1024

    // 批处理队列
    private val batchQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<BatchItem>>()

    // 批处理定时器ID
    private var batchTimerId: Long = -1

    // 批处理间隔（毫秒）
    private var batchInterval = 50L

    // 批处理最大消息数
    private var batchMaxSize = 100

    // 是否启用批处理
    private var batchingEnabled = true

    // 统计信息
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val bytesSent = AtomicLong(0)
    private val bytesReceived = AtomicLong(0)
    private val compressedBytesSent = AtomicLong(0)
    private val compressedBytesReceived = AtomicLong(0)
    private val compressionRatio = AtomicLong(0)
    private val batchesSent = AtomicLong(0)
    private val batchesReceived = AtomicLong(0)

    // 是否已启动
    private val started = AtomicBoolean(false)

    /**
     * 批处理项
     */
    private data class BatchItem(
        val message: Any,
        val options: DeliveryOptions?,
        val promise: Promise<Any>?
    )

    /**
     * 初始化并启动
     */
    fun start(): Future<Void> {
        if (started.compareAndSet(false, true)) {
            logger.info("启动优化的集群通信")

            // 注册消息处理器
            registerMessageHandlers()

            // 启动批处理定时器
            if (batchingEnabled) {
                startBatchProcessor()
            }
        }

        return Future.succeededFuture()
    }

    /**
     * 停止
     */
    fun stop(): Future<Void> {
        if (started.compareAndSet(true, false)) {
            logger.info("停止优化的集群通信")

            // 停止批处理定时器
            if (batchTimerId != -1L) {
                vertx.cancelTimer(batchTimerId)
                batchTimerId = -1L
            }

            // 处理剩余的批处理队列
            processBatchQueues()
        }

        return Future.succeededFuture()
    }

    /**
     * 注册消息处理器
     */
    private fun registerMessageHandlers() {
        // 注册批处理消息处理器
        vertx.eventBus().consumer<Buffer>("apix.cluster.batch") { message ->
            handleBatchMessage(message.body())
        }

        // 注册压缩消息处理器
        vertx.eventBus().consumer<Buffer>("apix.cluster.compressed") { message ->
            handleCompressedMessage(message.body())
        }
    }

    /**
     * 启动批处理处理器
     */
    private fun startBatchProcessor() {
        batchTimerId = vertx.setPeriodic(batchInterval) { _ ->
            processBatchQueues()
        }
    }

    /**
     * 处理所有批处理队列
     */
    private fun processBatchQueues() {
        for ((address, queue) in batchQueues) {
            if (!queue.isEmpty()) {
                processBatchQueue(address, queue)
            }
        }
    }

    /**
     * 处理单个批处理队列
     */
    private fun processBatchQueue(address: String, queue: ConcurrentLinkedQueue<BatchItem>) {
        val batch = mutableListOf<BatchItem>()

        // 从队列中取出消息，最多取batchMaxSize个
        while (batch.size < batchMaxSize) {
            val item = queue.poll() ?: break
            batch.add(item)
        }

        if (batch.isNotEmpty()) {
            sendBatch(address, batch)
        }
    }

    /**
     * 发送批处理消息
     */
    private fun sendBatch(address: String, batch: List<BatchItem>) {
        try {
            // 创建批处理消息
            val batchArray = JsonArray()

            for (item in batch) {
                val messageObj = JsonObject()

                // 添加消息内容
                when (item.message) {
                    is JsonObject -> messageObj.put("content", item.message)
                    is JsonArray -> messageObj.put("content", item.message)
                    is String -> messageObj.put("content", item.message)
                    is Buffer -> messageObj.put("content", item.message.toString())
                    else -> {
                        logger.warn("不支持的消息类型: {}", item.message.javaClass.name)
                        item.promise?.fail("不支持的消息类型: ${item.message.javaClass.name}")
                        continue
                    }
                }

                // 添加消息头
                if (item.options != null) {
                    val headers = JsonObject()
                    val headerMap = item.options.headers
                    for (headerName in headerMap.names()) {
                        headers.put(headerName, headerMap.get(headerName))
                    }
                    messageObj.put("headers", headers)
                }

                batchArray.add(messageObj)
            }

            // 序列化批处理消息
            val batchBuffer = Buffer.buffer(batchArray.encode())

            // 发送批处理消息
            val options = DeliveryOptions()
                .addHeader("batch-size", batch.size.toString())
                .addHeader("original-address", address)

            // 如果需要压缩，则压缩后发送
            if (compressionEnabled && batchBuffer.length() > compressionThreshold) {
                val compressedBuffer = compressBuffer(batchBuffer)
                vertx.eventBus().send("apix.cluster.compressed", compressedBuffer, options)

                // 更新统计信息
                compressedBytesSent.addAndGet(compressedBuffer.length().toLong())
                bytesSent.addAndGet(batchBuffer.length().toLong())
                if (batchBuffer.length() > 0) {
                    compressionRatio.set((compressedBuffer.length() * 100L) / batchBuffer.length())
                }
            } else {
                vertx.eventBus().send("apix.cluster.batch", batchBuffer, options)

                // 更新统计信息
                bytesSent.addAndGet(batchBuffer.length().toLong())
            }

            // 更新统计信息
            messagesSent.addAndGet(batch.size.toLong())
            batchesSent.incrementAndGet()

            // 完成所有Promise
            for (item in batch) {
                item.promise?.complete(null)
            }
        } catch (e: Exception) {
            logger.error("发送批处理消息失败", e)

            // 失败时，完成所有Promise
            for (item in batch) {
                item.promise?.fail(e)
            }
        }
    }

    /**
     * 处理批处理消息
     */
    private fun handleBatchMessage(buffer: Buffer) {
        try {
            // 更新统计信息
            bytesReceived.addAndGet(buffer.length().toLong())

            // 解析批处理消息
            val batchArray = JsonArray(buffer.toString())

            // 更新统计信息
            messagesReceived.addAndGet(batchArray.size().toLong())
            batchesReceived.incrementAndGet()

            // 处理每个消息
            for (i in 0 until batchArray.size()) {
                val messageObj = batchArray.getJsonObject(i)
                val content = messageObj.getValue("content")
                val headers = messageObj.getJsonObject("headers", JsonObject())

                // 获取原始地址
                val originalAddress = headers.getString("original-address", "")
                if (originalAddress.isNotEmpty()) {
                    // 创建DeliveryOptions
                    val options = DeliveryOptions()
                    for (header in headers) {
                        options.addHeader(header.key, header.value.toString())
                    }

                    // 发送到原始地址
                    vertx.eventBus().send(originalAddress, content, options)
                }
            }
        } catch (e: Exception) {
            logger.error("处理批处理消息失败", e)
        }
    }

    /**
     * 处理压缩消息
     */
    private fun handleCompressedMessage(buffer: Buffer) {
        try {
            // 更新统计信息
            compressedBytesReceived.addAndGet(buffer.length().toLong())

            // 解压缩消息
            val decompressedBuffer = decompressBuffer(buffer)

            // 更新统计信息
            bytesReceived.addAndGet(decompressedBuffer.length().toLong())
            if (buffer.length() > 0) {
                compressionRatio.set((buffer.length() * 100L) / decompressedBuffer.length())
            }

            // 处理解压缩后的批处理消息
            handleBatchMessage(decompressedBuffer)
        } catch (e: Exception) {
            logger.error("处理压缩消息失败", e)
        }
    }

    /**
     * 压缩Buffer
     */
    private fun compressBuffer(buffer: Buffer): Buffer {
        val input = buffer.bytes
        val deflater = Deflater(compressionLevel)
        deflater.setInput(input)
        deflater.finish()

        val output = ByteArray(input.size * 2) // 预留足够空间
        val compressedSize = deflater.deflate(output)
        deflater.end()

        return Buffer.buffer().appendBytes(output, 0, compressedSize)
    }

    /**
     * 解压缩Buffer
     */
    private fun decompressBuffer(buffer: Buffer): Buffer {
        val input = buffer.bytes
        val inflater = Inflater()
        inflater.setInput(input)

        val output = ByteArray(input.size * 10) // 预留足够空间
        val decompressedSize = inflater.inflate(output)
        inflater.end()

        return Buffer.buffer().appendBytes(output, 0, decompressedSize)
    }

    /**
     * 发送消息
     */
    fun send(address: String, message: Any, options: DeliveryOptions? = null): Future<Any> {
        val promise = Promise.promise<Any>()

        if (!started.get()) {
            promise.fail("优化的集群通信未启动")
            return promise.future()
        }

        // 如果启用了批处理，则添加到批处理队列
        if (batchingEnabled) {
            val queue = batchQueues.computeIfAbsent(address) { ConcurrentLinkedQueue() }
            queue.add(BatchItem(message, options, promise))

            // 如果队列达到最大大小，立即处理
            if (queue.size >= batchMaxSize) {
                processBatchQueue(address, queue)
            }
        } else {
            // 否则直接发送
            sendDirect(address, message, options)
                .onSuccess { promise.complete(it) }
                .onFailure { promise.fail(it) }
        }

        return promise.future()
    }

    /**
     * 直接发送消息（不经过批处理）
     */
    private fun sendDirect(address: String, message: Any, options: DeliveryOptions? = null): Future<Any> {
        val promise = Promise.promise<Any>()

        try {
            // 序列化消息
            val buffer = when (message) {
                is JsonObject -> Buffer.buffer(message.encode())
                is JsonArray -> Buffer.buffer(message.encode())
                is String -> Buffer.buffer(message)
                is Buffer -> message
                else -> {
                    promise.fail("不支持的消息类型: ${message.javaClass.name}")
                    return promise.future()
                }
            }

            // 更新统计信息
            messagesSent.incrementAndGet()
            bytesSent.addAndGet(buffer.length().toLong())

            // 如果需要压缩，则压缩后发送
            if (compressionEnabled && buffer.length() > compressionThreshold) {
                val compressedBuffer = compressBuffer(buffer)

                // 更新统计信息
                compressedBytesSent.addAndGet(compressedBuffer.length().toLong())
                if (buffer.length() > 0) {
                    compressionRatio.set((compressedBuffer.length() * 100L) / buffer.length())
                }

                // 创建DeliveryOptions
                val newOptions = if (options != null) {
                    val newOpt = DeliveryOptions()
                    // 复制超时设置
                    newOpt.setSendTimeout(options.sendTimeout)
                    // 复制所有头信息
                    val headers = options.headers
                    for (headerName in headers.names()) {
                        newOpt.addHeader(headerName, headers.get(headerName))
                    }
                    newOpt
                } else {
                    DeliveryOptions()
                }
                newOptions.addHeader("compressed", "true")

                // 发送压缩消息
                vertx.eventBus().request<Any>(address, compressedBuffer, newOptions)
                    .onSuccess { promise.complete(it.body()) }
                    .onFailure { promise.fail(it) }
            } else {
                // 直接发送未压缩消息
                vertx.eventBus().request<Any>(address, message, options)
                    .onSuccess { promise.complete(it.body()) }
                    .onFailure { promise.fail(it) }
            }
        } catch (e: Exception) {
            logger.error("发送消息失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 发布消息
     */
    fun publish(address: String, message: Any, options: DeliveryOptions? = null) {
        if (!started.get()) {
            logger.warn("优化的集群通信未启动，无法发布消息")
            return
        }

        try {
            // 序列化消息
            val buffer = when (message) {
                is JsonObject -> Buffer.buffer(message.encode())
                is JsonArray -> Buffer.buffer(message.encode())
                is String -> Buffer.buffer(message)
                is Buffer -> message
                else -> {
                    logger.warn("不支持的消息类型: {}", message.javaClass.name)
                    return
                }
            }

            // 更新统计信息
            messagesSent.incrementAndGet()
            bytesSent.addAndGet(buffer.length().toLong())

            // 如果需要压缩，则压缩后发送
            if (compressionEnabled && buffer.length() > compressionThreshold) {
                val compressedBuffer = compressBuffer(buffer)

                // 更新统计信息
                compressedBytesSent.addAndGet(compressedBuffer.length().toLong())
                if (buffer.length() > 0) {
                    compressionRatio.set((compressedBuffer.length() * 100L) / buffer.length())
                }

                // 创建DeliveryOptions
                val newOptions = if (options != null) {
                    val newOpt = DeliveryOptions()
                    // 复制超时设置
                    newOpt.setSendTimeout(options.sendTimeout)
                    // 复制所有头信息
                    val headers = options.headers
                    for (headerName in headers.names()) {
                        newOpt.addHeader(headerName, headers.get(headerName))
                    }
                    newOpt
                } else {
                    DeliveryOptions()
                }
                newOptions.addHeader("compressed", "true")

                // 发布压缩消息
                vertx.eventBus().publish(address, compressedBuffer, newOptions)
            } else {
                // 直接发布未压缩消息
                vertx.eventBus().publish(address, message, options)
            }
        } catch (e: Exception) {
            logger.error("发布消息失败", e)
        }
    }

    /**
     * 配置压缩
     */
    fun configureCompression(enabled: Boolean, level: Int = 6, threshold: Int = 1024): OptimizedClusterCommunication {
        this.compressionEnabled = enabled
        this.compressionLevel = level.coerceIn(0, 9)
        this.compressionThreshold = threshold
        return this
    }

    /**
     * 配置批处理
     */
    fun configureBatching(enabled: Boolean, interval: Long = 50, maxSize: Int = 100): OptimizedClusterCommunication {
        this.batchingEnabled = enabled
        this.batchInterval = interval
        this.batchMaxSize = maxSize

        // 如果已启动，更新批处理定时器
        if (started.get() && batchingEnabled) {
            if (batchTimerId != -1L) {
                vertx.cancelTimer(batchTimerId)
            }
            startBatchProcessor()
        }

        return this
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("messagesSent", messagesSent.get())
            .put("messagesReceived", messagesReceived.get())
            .put("bytesSent", bytesSent.get())
            .put("bytesReceived", bytesReceived.get())
            .put("compressedBytesSent", compressedBytesSent.get())
            .put("compressedBytesReceived", compressedBytesReceived.get())
            .put("compressionRatio", compressionRatio.get())
            .put("batchesSent", batchesSent.get())
            .put("batchesReceived", batchesReceived.get())
            .put("compressionEnabled", compressionEnabled)
            .put("compressionLevel", compressionLevel)
            .put("compressionThreshold", compressionThreshold)
            .put("batchingEnabled", batchingEnabled)
            .put("batchInterval", batchInterval)
            .put("batchMaxSize", batchMaxSize)
    }
}
