package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
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
            
            // 切换类型
            when (type) {
                EventBusType.VERTX -> {
                    // 切换到原生EventBus
                    currentType.set(EventBusType.VERTX)
                    promise.complete(true)
                }
                EventBusType.JCTOOLS -> {
                    // 切换到JCToolsEventBus
                    jcToolsEventBus.start()
                    currentType.set(EventBusType.JCTOOLS)
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
        val stats = JsonObject()
            .put("type", currentType.get().name)
        
        return stats
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
