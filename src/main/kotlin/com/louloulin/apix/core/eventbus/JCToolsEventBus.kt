package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.jctools.queues.MpscArrayQueue
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于JCTools的高性能EventBus实现
 */
class JCToolsEventBus(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(JCToolsEventBus::class.java)

    // 原生EventBus
    private val originalEventBus: EventBus = vertx.eventBus()

    // 是否已启动
    private val started = AtomicBoolean(false)

    // 消息队列
    private val messageQueue = MpscArrayQueue<Any>(100000) // 增大队列容量

    // 性能统计
    private val messagesSent = AtomicLong(0)
    private val messagesProcessed = AtomicLong(0)
    private val messagesDropped = AtomicLong(0)
    private val queueFullCount = AtomicLong(0)

    // 批处理配置
    private val minBatchSize = 10
    private val maxBatchSize = 1000
    private val adaptiveBatchSize = AtomicLong(100) // 自适应批大小

    // 地址缓存
    private val addressCache = ConcurrentHashMap<String, Long>()

    /**
     * 启动JCToolsEventBus
     */
    fun start() {
        if (started.compareAndSet(false, true)) {
            logger.info("Starting JCToolsEventBus")

            // 启动消息处理器
            startMessageProcessor()

            logger.info("JCToolsEventBus started successfully")
        } else {
            logger.info("JCToolsEventBus already started")
        }
    }

    /**
     * 启动消息处理器
     */
    private fun startMessageProcessor() {
        logger.info("Starting message processor")

        // 创建定时器，定期处理消息队列
        vertx.setPeriodic(1) { _ ->
            processMessages()
        }

        logger.info("Message processor started successfully")
    }

    /**
     * 处理消息队列中的消息
     */
    private fun processMessages() {
        // 获取当前批大小
        val currentBatchSize = adaptiveBatchSize.get().toInt()
        var processed = 0
        val startTime = System.nanoTime()

        // 批量处理消息
        while (processed < currentBatchSize) {
            val message = messageQueue.poll() ?: break

            try {
                // 处理消息
                when (message) {
                    is String -> {
                        logger.trace("Processing message: {}", message)
                    }
                    is JsonObject -> {
                        logger.trace("Processing JSON message: {}", message.encode())

                        // 将消息转发到原生EventBus
                        val address = message.getString("_address")
                        val body = message.getJsonObject("_body")
                        if (address != null && body != null) {
                            // 更新地址缓存
                            addressCache.put(address, System.currentTimeMillis())

                            // 发送消息
                            originalEventBus.send(address, body)

                            // 更新统计信息
                            messagesProcessed.incrementAndGet()
                        } else {
                            // 消息格式错误
                            messagesDropped.incrementAndGet()
                        }
                    }
                    else -> {
                        logger.debug("Processing unknown message type: {}", message.javaClass.name)
                        messagesDropped.incrementAndGet()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing message", e)
                messagesDropped.incrementAndGet()
            }

            processed++
        }

        // 计算处理时间
        val processingTime = System.nanoTime() - startTime

        // 自适应调整批大小
        if (processed > 0) {
            adjustBatchSize(processed, processingTime)
        }
    }

    /**
     * 自适应调整批大小
     */
    private fun adjustBatchSize(processed: Int, processingTime: Long) {
        val currentBatchSize = adaptiveBatchSize.get().toInt()
        var newBatchSize = currentBatchSize

        // 如果处理了当前批大小的所有消息，并且处理时间很短，增加批大小
        if (processed == currentBatchSize && processingTime < 1_000_000) { // 小于1毫秒
            newBatchSize = Math.min(maxBatchSize, (currentBatchSize * 1.2).toInt())
        }
        // 如果处理时间过长，减小批大小
        else if (processingTime > 10_000_000) { // 大于10毫秒
            newBatchSize = Math.max(minBatchSize, (currentBatchSize * 0.8).toInt())
        }

        // 更新批大小
        if (newBatchSize != currentBatchSize) {
            adaptiveBatchSize.set(newBatchSize.toLong())
            logger.debug("Adjusted batch size: {} -> {}", currentBatchSize, newBatchSize)
        }
    }

    /**
     * 获取原生EventBus
     */
    fun getOriginalEventBus(): EventBus {
        return originalEventBus
    }

    /**
     * 发送消息到队列
     */
    fun sendToQueue(address: String, message: Any) {
        // 检查是否已启动
        if (!started.get()) {
            start()
        }

        // 更新统计信息
        messagesSent.incrementAndGet()

        // 创建包装消息
        val wrappedMessage = JsonObject()
            .put("_address", address)
            .put("_body", message)
            .put("_timestamp", System.currentTimeMillis())

        // 添加到队列
        val success = messageQueue.offer(wrappedMessage)

        if (!success) {
            // 更新队列满计数
            queueFullCount.incrementAndGet()

            logger.warn("Failed to add message to queue: queue is full")

            // 队列满时，直接使用原生EventBus发送
            originalEventBus.send(address, message)

            // 更新统计信息
            messagesProcessed.incrementAndGet()
        }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        // 使用当前队列大小和固定容量
        val queueSize = 0 // 无法直接获取队列大小
        val queueCapacity = 100000 // 队列容量固定值
        val queueUtilization = if (queueCapacity > 0) queueSize.toDouble() / queueCapacity else 0.0

        return JsonObject()
            .put("started", started.get())
            .put("messages_sent", messagesSent.get())
            .put("messages_processed", messagesProcessed.get())
            .put("messages_dropped", messagesDropped.get())
            .put("queue_full_count", queueFullCount.get())
            .put("queue_size", queueSize)
            .put("queue_capacity", queueCapacity)
            .put("queue_utilization", queueUtilization)
            .put("batch_size", adaptiveBatchSize.get())
            .put("address_count", addressCache.size)
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        messagesSent.set(0)
        messagesProcessed.set(0)
        messagesDropped.set(0)
        queueFullCount.set(0)
        addressCache.clear()

        logger.info("Statistics reset")
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: JCToolsEventBus? = null

        /**
         * 获取JCToolsEventBus的单例实侌
         */
        fun getInstance(vertx: Vertx): JCToolsEventBus {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JCToolsEventBus(vertx).also { INSTANCE = it }
            }
        }
    }
}
