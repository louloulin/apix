package com.louloulin.apix.plugins.error

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 插件错误统计类
 * 用于收集和分析插件执行过程中的错误信息
 */
class PluginErrorStats(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginErrorStats::class.java)
    
    // 错误计数
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    
    // 错误类型统计
    private val errorTypes = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicLong>>()
    
    init {
        // 注册EventBus处理器，接收插件错误事件
        vertx.eventBus().consumer<JsonObject>("plugin.error") { message ->
            val event = message.body()
            val pluginId = event.getString("plugin_id")
            val errorType = event.getString("error_type")
            
            // 更新错误计数
            errorCounts.computeIfAbsent(pluginId) { AtomicLong(0) }.incrementAndGet()
            
            // 更新错误类型统计
            errorTypes.computeIfAbsent(pluginId) { ConcurrentHashMap() }
                .computeIfAbsent(errorType) { AtomicLong(0) }
                .incrementAndGet()
            
            // 发布错误统计更新事件
            vertx.eventBus().publish(
                "metrics.plugin.error.recorded",
                JsonObject()
                    .put("pluginId", pluginId)
                    .put("errorType", errorType)
            )
        }
    }
    
    /**
     * 获取插件错误统计
     */
    fun getErrorStats(pluginId: String): JsonObject {
        val stats = JsonObject()
        
        // 错误计数
        val errorCount = errorCounts[pluginId]?.get() ?: 0
        stats.put("errorCount", errorCount)
        
        // 错误类型统计
        val typeStats = JsonObject()
        errorTypes[pluginId]?.forEach { (type, count) ->
            typeStats.put(type, count.get())
        }
        stats.put("errorTypes", typeStats)
        
        return stats
    }
    
    /**
     * 获取所有插件的错误统计
     */
    fun getAllErrorStats(): JsonObject {
        val result = JsonObject()
        
        // 收集所有插件的错误统计
        errorCounts.keys.forEach { pluginId ->
            result.put(pluginId, getErrorStats(pluginId))
        }
        
        return result
    }
    
    /**
     * 重置错误统计
     */
    fun resetErrorStats() {
        errorCounts.clear()
        errorTypes.clear()
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PluginErrorStats? = null
        
        /**
         * 获取PluginErrorStats的单例实例
         */
        fun getInstance(vertx: Vertx): PluginErrorStats {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PluginErrorStats(vertx).also { INSTANCE = it }
            }
        }
    }
}
