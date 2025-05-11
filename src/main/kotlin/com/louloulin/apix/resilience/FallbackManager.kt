package com.louloulin.apix.resilience

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 降级策略管理器，用于管理系统的降级策略。
 * 降级策略可以在服务不可用时提供备选方案，确保系统的可用性。
 */
class FallbackManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(FallbackManager::class.java)
    
    // 降级是否启用
    private val fallbackEnabled = AtomicBoolean(true)
    
    // 降级配置
    private val fallbackConfigs = ConcurrentHashMap<String, FallbackConfig>()
    
    // 降级策略
    private val fallbackStrategies = ConcurrentHashMap<String, FallbackStrategy>()
    
    // 降级统计信息
    private val fallbackStats = ConcurrentHashMap<String, FallbackStats>()
    
    // 系统降级级别
    private val systemDegradationLevel = AtomicInteger(0)
    
    // 默认降级配置
    private val defaultConfig = FallbackConfig(
        enabled = true,
        strategies = listOf(
            FallbackStrategy(
                level = 1,
                type = "STATIC",
                value = JsonObject().put("message", "Service is temporarily unavailable"),
                statusCode = 503
            ),
            FallbackStrategy(
                level = 2,
                type = "CACHE",
                ttl = 300000
            ),
            FallbackStrategy(
                level = 3,
                type = "SIMPLIFIED",
                fields = listOf("id", "name", "status")
            )
        )
    )
    
    /**
     * 初始化降级策略管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化降级策略管理器")
        
        // 获取配置
        val resilienceConfig = config.getJsonObject("resilience", JsonObject())
        val fallbackConfig = resilienceConfig.getJsonObject("fallback", JsonObject())
        
        // 检查降级是否启用
        fallbackEnabled.set(fallbackConfig.getBoolean("enabled", true))
        
        if (!fallbackEnabled.get()) {
            logger.info("降级策略未启用")
            return Future.succeededFuture()
        }
        
        // 获取系统降级级别
        systemDegradationLevel.set(fallbackConfig.getInteger("systemDegradationLevel", 0))
        
        // 获取服务特定配置
        val servicesConfig = fallbackConfig.getJsonObject("services", JsonObject())
        if (servicesConfig != null) {
            for (serviceName in servicesConfig.fieldNames()) {
                val serviceConfig = servicesConfig.getJsonObject(serviceName)
                val enabled = serviceConfig.getBoolean("enabled", true)
                val strategiesJson = serviceConfig.getJsonArray("strategies")
                
                val strategies = if (strategiesJson != null) {
                    strategiesJson.map { strategyJson ->
                        val strategyObj = strategyJson as JsonObject
                        val level = strategyObj.getInteger("level", 1)
                        val type = strategyObj.getString("type", "STATIC")
                        val value = strategyObj.getJsonObject("value")
                        val statusCode = strategyObj.getInteger("statusCode", 503)
                        val ttl = strategyObj.getLong("ttl", 300000)
                        val fields = strategyObj.getJsonArray("fields")?.map { it.toString() } ?: emptyList()
                        
                        FallbackStrategy(
                            level = level,
                            type = type,
                            value = value,
                            statusCode = statusCode,
                            ttl = ttl,
                            fields = fields
                        )
                    }
                } else {
                    defaultConfig.strategies
                }
                
                fallbackConfigs[serviceName] = FallbackConfig(
                    enabled = enabled,
                    strategies = strategies
                )
                
                logger.info("为服务 $serviceName 配置降级策略: enabled=$enabled, strategies=${strategies.size}")
            }
        }
        
        // 监听系统降级级别变更事件
        vertx.eventBus().consumer<JsonObject>("apix.resilience.degradation.level.change") { message ->
            val newLevel = message.body().getInteger("level", 0)
            val oldLevel = systemDegradationLevel.getAndSet(newLevel)
            
            if (oldLevel != newLevel) {
                logger.info("系统降级级别变更: $oldLevel -> $newLevel")
                
                // 发布系统降级级别变更事件
                vertx.eventBus().publish("apix.resilience.degradation.level.changed", JsonObject()
                    .put("oldLevel", oldLevel)
                    .put("newLevel", newLevel)
                    .put("timestamp", System.currentTimeMillis())
                )
            }
        }
        
        logger.info("降级策略管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 获取指定服务的降级策略。
     * 
     * @param serviceName 服务名称
     * @return 降级策略
     */
    fun getFallbackStrategy(serviceName: String): FallbackStrategy? {
        // 获取服务配置
        val config = fallbackConfigs[serviceName] ?: defaultConfig
        
        // 如果降级未启用，返回 null
        if (!fallbackEnabled.get() || !config.enabled) {
            return null
        }
        
        // 获取当前系统降级级别
        val currentLevel = systemDegradationLevel.get()
        
        // 如果系统降级级别为 0，返回 null
        if (currentLevel == 0) {
            return null
        }
        
        // 查找适用的降级策略
        return config.strategies
            .filter { it.level <= currentLevel }
            .maxByOrNull { it.level }
    }
    
    /**
     * 应用降级策略。
     * 
     * @param serviceName 服务名称
     * @param originalData 原始数据
     * @param errorType 错误类型
     * @return 降级后的数据
     */
    fun <T> applyFallback(serviceName: String, originalData: T?, errorType: String): T? {
        // 获取降级策略
        val strategy = getFallbackStrategy(serviceName) ?: return originalData
        
        // 获取降级统计信息
        val stats = fallbackStats.computeIfAbsent(serviceName) { FallbackStats() }
        
        // 记录降级
        stats.recordFallback(strategy.level, errorType)
        
        // 应用降级策略
        @Suppress("UNCHECKED_CAST")
        return when (strategy.type) {
            "STATIC" -> strategy.value as T?
            "CACHE" -> getCachedData(serviceName) as T? ?: originalData
            "SIMPLIFIED" -> simplifyData(originalData, strategy.fields) as T?
            else -> originalData
        }
    }
    
    /**
     * 获取缓存数据。
     * 
     * @param serviceName 服务名称
     * @return 缓存数据
     */
    private fun getCachedData(serviceName: String): Any? {
        // 在实际实现中，这里应该从缓存中获取数据
        // 为简化实现，这里返回 null
        return null
    }
    
    /**
     * 简化数据。
     * 
     * @param data 原始数据
     * @param fields 保留的字段
     * @return 简化后的数据
     */
    private fun simplifyData(data: Any?, fields: List<String>): Any? {
        if (data == null) {
            return null
        }
        
        return when (data) {
            is JsonObject -> {
                val simplified = JsonObject()
                for (field in fields) {
                    if (data.containsKey(field)) {
                        simplified.put(field, data.getValue(field))
                    }
                }
                simplified
            }
            is Map<*, *> -> {
                val simplified = mutableMapOf<String, Any?>()
                for (field in fields) {
                    if (data.containsKey(field)) {
                        simplified[field] = data[field]
                    }
                }
                simplified
            }
            else -> data
        }
    }
    
    /**
     * 设置系统降级级别。
     * 
     * @param level 降级级别
     */
    fun setSystemDegradationLevel(level: Int) {
        val oldLevel = systemDegradationLevel.getAndSet(level)
        
        if (oldLevel != level) {
            logger.info("系统降级级别变更: $oldLevel -> $level")
            
            // 发布系统降级级别变更事件
            vertx.eventBus().publish("apix.resilience.degradation.level.changed", JsonObject()
                .put("oldLevel", oldLevel)
                .put("newLevel", level)
                .put("timestamp", System.currentTimeMillis())
            )
        }
    }
    
    /**
     * 获取系统降级级别。
     * 
     * @return 系统降级级别
     */
    fun getSystemDegradationLevel(): Int {
        return systemDegradationLevel.get()
    }
    
    /**
     * 获取所有降级策略的统计信息。
     * 
     * @return 包含统计信息的 JsonObject
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("enabled", fallbackEnabled.get())
            .put("systemDegradationLevel", systemDegradationLevel.get())
        
        val fallbacksStats = JsonObject()
        for ((name, fallbackStat) in fallbackStats) {
            fallbacksStats.put(name, fallbackStat.getStats())
        }
        
        stats.put("fallbacks", fallbacksStats)
        return stats
    }
    
    /**
     * 降级配置。
     */
    data class FallbackConfig(
        val enabled: Boolean,
        val strategies: List<FallbackStrategy>
    )
    
    /**
     * 降级策略。
     */
    data class FallbackStrategy(
        val level: Int,
        val type: String,
        val value: JsonObject? = null,
        val statusCode: Int = 503,
        val ttl: Long = 300000,
        val fields: List<String> = emptyList()
    )
    
    /**
     * 降级统计信息。
     */
    class FallbackStats {
        // 降级次数
        private val fallbackCount = AtomicInteger(0)
        
        // 各级别降级次数
        private val levelCounts = ConcurrentHashMap<Int, AtomicInteger>()
        
        // 各错误类型降级次数
        private val errorTypeCounts = ConcurrentHashMap<String, AtomicInteger>()
        
        /**
         * 记录降级。
         * 
         * @param level 降级级别
         * @param errorType 错误类型
         */
        fun recordFallback(level: Int, errorType: String) {
            fallbackCount.incrementAndGet()
            levelCounts.computeIfAbsent(level) { AtomicInteger(0) }.incrementAndGet()
            errorTypeCounts.computeIfAbsent(errorType) { AtomicInteger(0) }.incrementAndGet()
        }
        
        /**
         * 获取统计信息。
         */
        fun getStats(): JsonObject {
            val levelStats = JsonObject()
            for ((level, count) in levelCounts) {
                levelStats.put(level.toString(), count.get())
            }
            
            val errorTypeStats = JsonObject()
            for ((errorType, count) in errorTypeCounts) {
                errorTypeStats.put(errorType, count.get())
            }
            
            return JsonObject()
                .put("fallbackCount", fallbackCount.get())
                .put("levelCounts", levelStats)
                .put("errorTypeCounts", errorTypeStats)
        }
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: FallbackManager? = null
        
        /**
         * 获取 FallbackManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return FallbackManager 实例
         */
        fun getInstance(vertx: Vertx): FallbackManager {
            return instance ?: synchronized(this) {
                instance ?: FallbackManager(vertx).also { instance = it }
            }
        }
    }
}
