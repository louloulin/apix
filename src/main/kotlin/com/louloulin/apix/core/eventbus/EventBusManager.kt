package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * EventBus管理器，用于管理EventBus实例
 */
class EventBusManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(EventBusManager::class.java)

    // 当前EventBus类型
    private val currentType = AtomicReference(EventBusType.VERTX)

    // JCToolsEventBus实例
    private val jcToolsEventBus = JCToolsEventBus.getInstance(vertx)

    // 性能统计
    private val messagesSent = AtomicLong(0)
    private val switchCount = AtomicLong(0)
    private val lastSwitchTime = AtomicLong(0)
    private val startTime = AtomicLong(System.currentTimeMillis())

    /**
     * EventBus类型
     */
    enum class EventBusType {
        VERTX,      // 原生Vert.x EventBus
        JCTOOLS     // 基于JCTools的EventBus
    }

    /**
     * 获取当前EventBus类型
     */
    fun getCurrentType(): EventBusType {
        return currentType.get()
    }

    /**
     * 获取EventBus实例
     */
    fun getEventBus(): EventBus {
        return when (currentType.get()) {
            EventBusType.VERTX -> vertx.eventBus()
            EventBusType.JCTOOLS -> jcToolsEventBus.getOriginalEventBus()
        }
    }

    /**
     * 发送消息
     */
    fun send(address: String, message: Any) {
        // 更新统计信息
        messagesSent.incrementAndGet()

        when (currentType.get()) {
            EventBusType.VERTX -> vertx.eventBus().send(address, message)
            EventBusType.JCTOOLS -> jcToolsEventBus.sendToQueue(address, message)
        }
    }

    /**
     * 发布消息
     */
    fun publish(address: String, message: Any) {
        // 更新统计信息
        messagesSent.incrementAndGet()

        // 发布消息始终使用原生EventBus
        vertx.eventBus().publish(address, message)
    }

    /**
     * 切换EventBus类型
     */
    fun switchType(type: EventBusType): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            // 如果类型相同，直接返回成功
            if (currentType.get() == type) {
                promise.complete(true)
                return promise.future()
            }

            logger.info("Switching EventBus type from {} to {}", currentType.get(), type)

            // 更新统计信息
            switchCount.incrementAndGet()
            lastSwitchTime.set(System.currentTimeMillis())

            // 切换类型
            when (type) {
                EventBusType.VERTX -> {
                    // 切换到原生EventBus
                    currentType.set(EventBusType.VERTX)
                    logger.info("Switched to VERTX EventBus")
                    promise.complete(true)
                }
                EventBusType.JCTOOLS -> {
                    // 切换到JCToolsEventBus
                    jcToolsEventBus.start()
                    currentType.set(EventBusType.JCTOOLS)
                    logger.info("Switched to JCToolsEventBus")
                    promise.complete(true)
                }
            }
        } catch (e: Exception) {
            logger.error("Error switching EventBus type", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        val currentTimeMillis = System.currentTimeMillis()
        val uptime = currentTimeMillis - startTime.get()

        val stats = JsonObject()
            .put("type", currentType.get().name)
            .put("messages_sent", messagesSent.get())
            .put("switch_count", switchCount.get())
            .put("last_switch_time", lastSwitchTime.get())
            .put("start_time", startTime.get())
            .put("uptime_ms", uptime)
            .put("timestamp", currentTimeMillis)

        // 添加JCToolsEventBus统计信息
        if (currentType.get() == EventBusType.JCTOOLS) {
            stats.put("jctools", jcToolsEventBus.getStats())
        }

        return stats
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        messagesSent.set(0)

        // 重置JCToolsEventBus统计信息
        jcToolsEventBus.resetStats()

        logger.info("Statistics reset")
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: EventBusManager? = null

        /**
         * 获取EventBusManager的单例实例
         */
        fun getInstance(vertx: Vertx): EventBusManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EventBusManager(vertx).also { INSTANCE = it }
            }
        }
    }
}
