package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 批量消息处理器，用于聚合和批处理 EventBus 消息。
 * 这个类可以将多个小消息聚合成一个大消息，减少系统开销，特别是在高并发场景下。
 */
class BatchMessageProcessor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(BatchMessageProcessor::class.java)

    // 批处理配置 - 高并发优化
    private val batchSize = 1000               // 每批消息的最大数量，增加到 1000
    private val batchTimeoutMs = 20L           // 批处理超时时间，减少到 20 毫秒
    private val maxQueueSize = 100000          // 最大队列大小，增加到 100000

    // 消息队列，按地址分组
    private val messageQueues = ConcurrentHashMap<String, ConcurrentLinkedQueue<MessageEntry<*>>>()

    // 批处理计时器，按地址分组
    private val batchTimers = ConcurrentHashMap<String, Long>()

    // 队列大小计数器，按地址分组
    private val queueSizes = ConcurrentHashMap<String, AtomicInteger>()

    // 统计信息
    private val totalProcessedMessages = AtomicLong(0)
    private val totalBatches = AtomicLong(0)
    private val totalBatchTime = AtomicLong(0)

    /**
     * 消息条目，包含消息内容和回调。
     */
    private data class MessageEntry<T>(
        val message: Any,
        val promise: Promise<T>
    )

    init {
        logger.info("批量消息处理器已初始化，批大小: {}, 超时: {}ms", batchSize, batchTimeoutMs)
    }

    /**
     * 发送消息，可能会被批处理。
     *
     * @param address 目标地址
     * @param message 消息内容
     * @return 包含响应的 Future
     */
    fun <T> send(address: String, message: Any): Future<T> {
        val promise = Promise.promise<T>()

        // 检查队列大小
        val queueSize = queueSizes.computeIfAbsent(address) { AtomicInteger(0) }
        if (queueSize.get() >= maxQueueSize) {
            // 队列已满，直接发送消息
            sendImmediately(address, message, promise)
            return promise.future()
        }

        // 获取或创建消息队列
        val queue = messageQueues.computeIfAbsent(address) { ConcurrentLinkedQueue() }

        // 添加消息到队列
        queue.add(MessageEntry(message, promise))
        queueSize.incrementAndGet()

        // 检查是否需要启动批处理计时器
        if (!batchTimers.containsKey(address)) {
            startBatchTimer(address)
        }

        // 检查是否达到批处理大小
        if (queue.size >= batchSize) {
            processBatch(address)
        }

        return promise.future()
    }

    /**
     * 启动批处理计时器。
     *
     * @param address 目标地址
     */
    private fun startBatchTimer(address: String) {
        val timerId = vertx.setTimer(batchTimeoutMs) { _ ->
            batchTimers.remove(address)
            processBatch(address)
        }
        batchTimers[address] = timerId
    }

    /**
     * 处理一批消息。
     *
     * @param address 目标地址
     */
    private fun processBatch(address: String) {
        val queue = messageQueues[address] ?: return
        val queueSize = queueSizes[address] ?: return

        // 取消计时器
        batchTimers.remove(address)?.let { timerId ->
            vertx.cancelTimer(timerId)
        }

        // 提取批量消息
        val batch = mutableListOf<MessageEntry<*>>()
        val batchMessages = JsonArray()

        var count = 0
        while (count < batchSize && queue.isNotEmpty()) {
            val entry = queue.poll() ?: break
            batch.add(entry)
            batchMessages.add(entry.message)
            count++
        }

        if (batch.isEmpty()) {
            return
        }

        // 更新队列大小
        queueSize.addAndGet(-count)

        // 更新统计信息
        totalProcessedMessages.addAndGet(count.toLong())
        totalBatches.incrementAndGet()

        // 创建批量消息
        val batchMessage = JsonObject()
            .put("action", "batch")
            .put("messages", batchMessages)

        // 记录开始时间
        val startTime = System.currentTimeMillis()

        // 发送批量消息
        vertx.eventBus().request<JsonArray>(address, batchMessage) { ar ->
            // 更新批处理时间统计
            val batchTime = System.currentTimeMillis() - startTime
            totalBatchTime.addAndGet(batchTime)

            if (ar.succeeded()) {
                // 批量请求成功，分发响应
                val responses = ar.result().body()

                // 确保响应数量与请求数量匹配
                if (responses.size() == batch.size) {
                    // 分发响应
                    for (i in 0 until batch.size) {
                        val entry = batch[i]
                        val response = responses.getValue(i)

                        @Suppress("UNCHECKED_CAST")
                        (entry.promise as Promise<Any>).complete(response)
                    }
                } else {
                    // 响应数量不匹配，返回错误
                    val error = "批量响应数量不匹配: 预期 ${batch.size}, 实际 ${responses.size()}"
                    logger.error(error)

                    batch.forEach { entry ->
                        @Suppress("UNCHECKED_CAST")
                        (entry.promise as Promise<Any>).fail(error)
                    }
                }
            } else {
                // 批量请求失败，所有请求都失败
                val cause = ar.cause()
                logger.warn("批量请求失败: {}", cause.message)

                batch.forEach { entry ->
                    entry.promise.fail(cause)
                }
            }
        }
    }

    /**
     * 立即发送单个消息，不进行批处理。
     *
     * @param address 目标地址
     * @param message 消息内容
     * @param promise 用于接收响应的 Promise
     */
    private fun <T> sendImmediately(address: String, message: Any, promise: Promise<T>) {
        vertx.eventBus().request<T>(address, message) { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result().body())
            } else {
                promise.fail(ar.cause())
            }
        }
    }

    /**
     * 获取批处理统计信息。
     *
     * @return 包含统计信息的 JsonObject
     */
    fun getStats(): JsonObject {
        val avgBatchSize = if (totalBatches.get() > 0) {
            totalProcessedMessages.get().toDouble() / totalBatches.get()
        } else {
            0.0
        }

        val avgBatchTime = if (totalBatches.get() > 0) {
            totalBatchTime.get().toDouble() / totalBatches.get()
        } else {
            0.0
        }

        return JsonObject()
            .put("totalProcessedMessages", totalProcessedMessages.get())
            .put("totalBatches", totalBatches.get())
            .put("avgBatchSize", avgBatchSize)
            .put("avgBatchTimeMs", avgBatchTime)
            .put("activeQueues", messageQueues.size)
            .put("queueSizes", JsonObject().apply {
                queueSizes.forEach { (address, size) ->
                    put(address, size.get())
                }
            })
    }

    /**
     * 注册批量消息处理器。
     *
     * @param address 目标地址
     * @param handler 批量消息处理器
     */
    fun registerBatchHandler(address: String, handler: (JsonArray) -> Future<JsonArray>) {
        vertx.eventBus().consumer<JsonObject>(address) { message ->
            val body = message.body()

            if (body.getString("action") == "batch") {
                val messages = body.getJsonArray("messages")

                handler(messages).onComplete { ar ->
                    if (ar.succeeded()) {
                        message.reply(ar.result())
                    } else {
                        message.fail(500, ar.cause().message)
                    }
                }
            } else {
                // 非批量消息，单独处理
                val singleMessage = JsonArray().add(body)

                handler(singleMessage).onComplete { ar ->
                    if (ar.succeeded()) {
                        val responses = ar.result()
                        if (responses.size() > 0) {
                            message.reply(responses.getValue(0))
                        } else {
                            message.fail(500, "No response")
                        }
                    } else {
                        message.fail(500, ar.cause().message)
                    }
                }
            }
        }
    }

    companion object {
        // 单例实例
        private var INSTANCE: BatchMessageProcessor? = null

        /**
         * 获取 BatchMessageProcessor 的单例实例。
         *
         * @param vertx Vertx 实例
         * @return BatchMessageProcessor 实例
         */
        fun getInstance(vertx: Vertx): BatchMessageProcessor {
            if (INSTANCE == null) {
                synchronized(BatchMessageProcessor::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = BatchMessageProcessor(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
