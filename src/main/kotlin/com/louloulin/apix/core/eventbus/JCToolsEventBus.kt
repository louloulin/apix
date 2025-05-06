package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.jctools.queues.MpscArrayQueue
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

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
    private val messageQueue = MpscArrayQueue<Any>(10000)

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
        // 批量处理消息
        val batchSize = 100
        var processed = 0

        while (processed < batchSize) {
            val message = messageQueue.poll() ?: break

            try {
                // 处理消息
                when (message) {
                    is String -> {
                        logger.debug("Processing message: {}", message)
                    }
                    is JsonObject -> {
                        logger.debug("Processing JSON message: {}", message.encode())
                    }
                    else -> {
                        logger.debug("Processing unknown message type: {}", message.javaClass.name)
                    }
                }

                // 将消息转发到原生EventBus
                if (message is JsonObject) {
                    val address = message.getString("_address")
                    val body = message.getJsonObject("_body")
                    if (address != null && body != null) {
                        originalEventBus.send(address, body)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error processing message", e)
            }

            processed++
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

        // 创建包装消息
        val wrappedMessage = JsonObject()
            .put("_address", address)
            .put("_body", message)
            .put("_timestamp", System.currentTimeMillis())

        // 添加到队列
        val success = messageQueue.offer(wrappedMessage)

        if (!success) {
            logger.warn("Failed to add message to queue: queue is full")

            // 队列满时，直接使用原生EventBus发送
            originalEventBus.send(address, message)
        }
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: JCToolsEventBus? = null

        /**
         * 获取JCToolsEventBus的单例实例
         */
        fun getInstance(vertx: Vertx): JCToolsEventBus {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JCToolsEventBus(vertx).also { INSTANCE = it }
            }
        }
    }
}
