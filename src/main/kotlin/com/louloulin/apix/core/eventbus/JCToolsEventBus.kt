package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * JCToolsEventBus的兼容实现，实际上是SimpleEventBus的一个包装
 * 这个类不使用JCTools，而是直接使用Vert.x原生EventBus，以支持Native Image编译
 */
class JCToolsEventBus(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(JCToolsEventBus::class.java)

    // 原生EventBus
    private val originalEventBus: EventBus = vertx.eventBus()

    // 是否已启动
    private val started = AtomicBoolean(false)

    // 性能统计
    private val messagesSent = AtomicLong(0)
    private val messagesProcessed = AtomicLong(0)
    private val messagesDropped = AtomicLong(0)

    // 地址缓存
    private val addressCache = ConcurrentHashMap<String, Long>()

    /**
     * 启动JCToolsEventBus
     */
    fun start() {
        if (started.compareAndSet(false, true)) {
            logger.info("Starting JCToolsEventBus wrapper (compatibility mode)")
            logger.info("JCToolsEventBus wrapper started successfully")
        } else {
            logger.info("JCToolsEventBus wrapper already started")
        }
    }

    /**
     * 获取原生EventBus
     */
    fun getOriginalEventBus(): EventBus {
        return originalEventBus
    }

    /**
     * 发送消息，直接使用原生EventBus
     */
    fun sendToQueue(address: String, message: Any) {
        // 检查是否已启动
        if (!started.get()) {
            start()
        }

        // 更新统计信息
        messagesSent.incrementAndGet()

        // 更新地址缓存
        addressCache.put(address, System.currentTimeMillis())

        // 直接使用原生EventBus发送
        originalEventBus.send(address, message)

        // 更新统计信息
        messagesProcessed.incrementAndGet()
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("started", started.get())
            .put("messages_sent", messagesSent.get())
            .put("messages_processed", messagesProcessed.get())
            .put("messages_dropped", messagesDropped.get())
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
        addressCache.clear()

        logger.info("Statistics reset")
    }

    /**
     * 停止JCToolsEventBus
     *
     * @return Future<Void> 停止结果
     */
    fun stop(): io.vertx.core.Future<Void> {
        if (started.compareAndSet(true, false)) {
            logger.info("Stopping JCToolsEventBus wrapper")

            // 清空数据
            addressCache.clear()

            logger.info("JCToolsEventBus wrapper stopped successfully")
        } else {
            logger.info("JCToolsEventBus wrapper already stopped")
        }

        return io.vertx.core.Future.succeededFuture()
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
