package com.louloulin.apix.core.config

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

/**
 * Vert.x配置优化器，用于根据系统资源动态调整Vert.x配置
 */
class VertxConfigOptimizer {
    private val logger = LoggerFactory.getLogger(VertxConfigOptimizer::class.java)
    
    // 性能配置文件
    enum class PerformanceProfile {
        HIGH_THROUGHPUT,  // 高吞吐量配置
        LOW_LATENCY,      // 低延迟配置
        BALANCED,         // 平衡配置
        MEMORY_OPTIMIZED, // 内存优化配置
        CUSTOM            // 自定义配置
    }
    
    // 当前性能配置文件
    private val currentProfile = AtomicReference(PerformanceProfile.BALANCED)
    
    // 配置缓存
    private val configCache = mutableMapOf<PerformanceProfile, JsonObject>()
    
    /**
     * 获取优化的Vert.x配置
     * 
     * @param profile 性能配置文件
     * @return 优化后的Vert.x配置
     */
    fun getOptimizedConfig(profile: PerformanceProfile = currentProfile.get()): JsonObject {
        // 如果缓存中有配置，直接返回
        if (configCache.containsKey(profile)) {
            return configCache[profile]!!.copy()
        }
        
        // 否则加载配置
        val config = when (profile) {
            PerformanceProfile.HIGH_THROUGHPUT -> loadHighThroughputConfig()
            PerformanceProfile.LOW_LATENCY -> loadLowLatencyConfig()
            PerformanceProfile.BALANCED -> loadBalancedConfig()
            PerformanceProfile.MEMORY_OPTIMIZED -> loadMemoryOptimizedConfig()
            PerformanceProfile.CUSTOM -> loadCustomConfig()
        }
        
        // 缓存配置
        configCache[profile] = config
        
        return config.copy()
    }
    
    /**
     * 设置当前性能配置文件
     * 
     * @param profile 性能配置文件
     */
    fun setCurrentProfile(profile: PerformanceProfile) {
        currentProfile.set(profile)
        logger.info("Set current performance profile to: {}", profile)
    }
    
    /**
     * 获取当前性能配置文件
     * 
     * @return 当前性能配置文件
     */
    fun getCurrentProfile(): PerformanceProfile {
        return currentProfile.get()
    }
    
    /**
     * 根据系统资源自动选择最佳性能配置文件
     * 
     * @return 选择的性能配置文件
     */
    fun autoSelectProfile(): PerformanceProfile {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val maxMemory = Runtime.getRuntime().maxMemory()
        val memoryGB = maxMemory / (1024 * 1024 * 1024)
        
        val profile = when {
            availableProcessors >= 16 && memoryGB >= 32 -> PerformanceProfile.HIGH_THROUGHPUT
            availableProcessors >= 8 && memoryGB >= 16 -> PerformanceProfile.BALANCED
            memoryGB < 8 -> PerformanceProfile.MEMORY_OPTIMIZED
            availableProcessors <= 4 -> PerformanceProfile.LOW_LATENCY
            else -> PerformanceProfile.BALANCED
        }
        
        logger.info("Auto-selected performance profile: {} (CPUs: {}, Memory: {} GB)", 
                   profile, availableProcessors, memoryGB)
        
        currentProfile.set(profile)
        return profile
    }
    
    /**
     * 加载高吞吐量配置
     */
    private fun loadHighThroughputConfig(): JsonObject {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        
        return loadBaseConfig().apply {
            put("eventLoopPoolSize", availableProcessors * 2)
            put("workerPoolSize", availableProcessors * 16)
            put("internalBlockingPoolSize", availableProcessors * 8)
            
            val eventBus = getJsonObject("eventBus", JsonObject())
            eventBus.put("acceptBacklog", 100000)
            eventBus.put("receiveBufferSize", 65536)
            eventBus.put("sendBufferSize", 65536)
            eventBus.put("reconnectAttempts", 10)
            eventBus.put("reconnectInterval", 2000)
            put("eventBus", eventBus)
            
            put("blockedThreadCheckInterval", 5000)
            put("maxEventLoopExecuteTime", 10000000000L)
            put("maxWorkerExecuteTime", 120000000000L)
        }
    }
    
    /**
     * 加载低延迟配置
     */
    private fun loadLowLatencyConfig(): JsonObject {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        
        return loadBaseConfig().apply {
            put("eventLoopPoolSize", availableProcessors)
            put("workerPoolSize", availableProcessors * 4)
            put("internalBlockingPoolSize", availableProcessors * 2)
            
            val eventBus = getJsonObject("eventBus", JsonObject())
            eventBus.put("acceptBacklog", 10000)
            eventBus.put("receiveBufferSize", 32768)
            eventBus.put("sendBufferSize", 32768)
            eventBus.put("reconnectAttempts", 5)
            eventBus.put("reconnectInterval", 1000)
            put("eventBus", eventBus)
            
            put("blockedThreadCheckInterval", 1000)
            put("maxEventLoopExecuteTime", 2000000000L)
            put("maxWorkerExecuteTime", 60000000000L)
        }
    }
    
    /**
     * 加载平衡配置
     */
    private fun loadBalancedConfig(): JsonObject {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        
        return loadBaseConfig().apply {
            put("eventLoopPoolSize", availableProcessors)
            put("workerPoolSize", availableProcessors * 8)
            put("internalBlockingPoolSize", availableProcessors * 4)
            
            val eventBus = getJsonObject("eventBus", JsonObject())
            eventBus.put("acceptBacklog", 50000)
            eventBus.put("receiveBufferSize", 32768)
            eventBus.put("sendBufferSize", 32768)
            eventBus.put("reconnectAttempts", 5)
            eventBus.put("reconnectInterval", 1000)
            put("eventBus", eventBus)
            
            put("blockedThreadCheckInterval", 2000)
            put("maxEventLoopExecuteTime", 5000000000L)
            put("maxWorkerExecuteTime", 60000000000L)
        }
    }
    
    /**
     * 加载内存优化配置
     */
    private fun loadMemoryOptimizedConfig(): JsonObject {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        
        return loadBaseConfig().apply {
            put("eventLoopPoolSize", Math.max(1, availableProcessors / 2))
            put("workerPoolSize", availableProcessors * 2)
            put("internalBlockingPoolSize", availableProcessors)
            
            val eventBus = getJsonObject("eventBus", JsonObject())
            eventBus.put("acceptBacklog", 5000)
            eventBus.put("receiveBufferSize", 16384)
            eventBus.put("sendBufferSize", 16384)
            eventBus.put("reconnectAttempts", 3)
            eventBus.put("reconnectInterval", 1000)
            put("eventBus", eventBus)
            
            put("blockedThreadCheckInterval", 5000)
            put("maxEventLoopExecuteTime", 5000000000L)
            put("maxWorkerExecuteTime", 60000000000L)
        }
    }
    
    /**
     * 加载自定义配置
     */
    private fun loadCustomConfig(): JsonObject {
        val customConfigPath = System.getProperty("apix.vertx.config.custom", "config/vertx-config-custom.json")
        val customConfigFile = File(customConfigPath)
        
        return if (customConfigFile.exists()) {
            try {
                val configContent = Files.readString(Paths.get(customConfigPath))
                val customConfig = JsonObject(configContent)
                logger.info("Loaded custom Vert.x configuration from: {}", customConfigPath)
                customConfig
            } catch (e: Exception) {
                logger.error("Failed to load custom Vert.x configuration from: {}", customConfigPath, e)
                loadBalancedConfig()
            }
        } else {
            logger.warn("Custom Vert.x configuration file not found at: {}, using balanced configuration", customConfigPath)
            loadBalancedConfig()
        }
    }
    
    /**
     * 加载基础配置
     */
    private fun loadBaseConfig(): JsonObject {
        val baseConfigPath = "vertx-config-optimized.json"
        
        return try {
            val configContent = this.javaClass.classLoader.getResourceAsStream(baseConfigPath)?.bufferedReader()?.readText()
            if (configContent != null) {
                JsonObject(configContent)
            } else {
                logger.warn("Base Vert.x configuration file not found: {}, using default configuration", baseConfigPath)
                JsonObject()
            }
        } catch (e: Exception) {
            logger.error("Failed to load base Vert.x configuration", e)
            JsonObject()
        }
    }
    
    companion object {
        private val INSTANCE = VertxConfigOptimizer()
        
        /**
         * 获取VertxConfigOptimizer实例
         * 
         * @return VertxConfigOptimizer实例
         */
        fun getInstance(): VertxConfigOptimizer {
            return INSTANCE
        }
    }
}
