package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * EventBus管理器，用于管理EventBus的切换和监控。
 * 提供了在原生Vert.x EventBus和优化的JCToolsEventBus之间切换的功能。
 */
class EventBusManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(EventBusManager::class.java)
    
    // 当前使用的EventBus类型
    private val currentType = AtomicReference(EventBusType.VERTX)
    
    // 当前使用的EventBus实例
    private val currentEventBus = AtomicReference<EventBus>(vertx.eventBus())
    
    // JCToolsEventBus实例
    private val jcToolsEventBus by lazy { JCToolsEventBus.getInstance(vertx) }
    
    // 原生Vert.x EventBus实例
    private val vertxEventBus = vertx.eventBus()
    
    /**
     * EventBus类型枚举
     */
    enum class EventBusType {
        VERTX,      // 原生Vert.x EventBus
        JCTOOLS     // 优化的JCToolsEventBus
    }
    
    init {
        logger.info("初始化EventBusManager，当前类型: {}", currentType.get())
    }
    
    /**
     * 获取当前使用的EventBus类型
     */
    fun getCurrentType(): EventBusType {
        return currentType.get()
    }
    
    /**
     * 获取当前使用的EventBus实例
     */
    fun getEventBus(): EventBus {
        return currentEventBus.get()
    }
    
    /**
     * 切换EventBus类型
     */
    fun switchType(type: EventBusType): Future<Boolean> {
        val promise = Promise.promise<Boolean>()
        
        try {
            val oldType = currentType.get()
            
            if (oldType == type) {
                // 类型相同，无需切换
                promise.complete(false)
                return promise.future()
            }
            
            // 切换类型
            currentType.set(type)
            
            // 切换EventBus实例
            when (type) {
                EventBusType.VERTX -> {
                    currentEventBus.set(vertxEventBus)
                    logger.info("已切换到原生Vert.x EventBus")
                }
                EventBusType.JCTOOLS -> {
                    // 确保JCToolsEventBus已启动
                    jcToolsEventBus.start()
                    currentEventBus.set(jcToolsEventBus)
                    logger.info("已切换到优化的JCToolsEventBus")
                }
            }
            
            promise.complete(true)
        } catch (e: Exception) {
            logger.error("切换EventBus类型失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取EventBus统计信息
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("currentType", currentType.get().name)
        
        // 添加JCToolsEventBus统计信息（如果可用）
        if (currentType.get() == EventBusType.JCTOOLS) {
            stats.put("jctools", jcToolsEventBus.getStats())
        }
        
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
