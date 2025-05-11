package com.louloulin.apix.resilience

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 故障注入管理器，用于模拟各种故障场景，测试系统的弹性。
 */
class FaultInjectionManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(FaultInjectionManager::class.java)
    
    // 故障注入是否启用
    private val faultInjectionEnabled = AtomicBoolean(false)
    
    // 故障注入配置
    private val faultInjectionConfigs = ConcurrentHashMap<String, FaultInjectionConfig>()
    
    // 故障注入统计信息
    private val faultInjectionStats = ConcurrentHashMap<String, FaultInjectionStats>()
    
    // 全局故障注入概率
    private val globalFaultProbability = AtomicInteger(0)
    
    // 故障注入模式
    private val faultInjectionMode = AtomicBoolean(false)
    
    // 故障注入模式开始时间
    private val faultInjectionModeStartTime = AtomicLong(0)
    
    // 故障注入模式持续时间（毫秒）
    private val faultInjectionModeDuration = AtomicLong(0)
    
    /**
     * 初始化故障注入管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化故障注入管理器")
        
        // 获取配置
        val resilienceConfig = config.getJsonObject("resilience", JsonObject())
        val faultInjectionConfig = resilienceConfig.getJsonObject("faultInjection", JsonObject())
        
        // 检查故障注入是否启用
        faultInjectionEnabled.set(faultInjectionConfig.getBoolean("enabled", false))
        
        if (!faultInjectionEnabled.get()) {
            logger.info("故障注入未启用")
            return Future.succeededFuture()
        }
        
        // 获取全局故障注入概率
        globalFaultProbability.set(faultInjectionConfig.getInteger("globalFaultProbability", 0))
        
        // 获取服务特定配置
        val servicesConfig = faultInjectionConfig.getJsonObject("services", JsonObject())
        if (servicesConfig != null) {
            for (serviceName in servicesConfig.fieldNames()) {
                val serviceConfig = servicesConfig.getJsonObject(serviceName)
                val enabled = serviceConfig.getBoolean("enabled", true)
                val probability = serviceConfig.getInteger("probability", 0)
                val faultTypes = serviceConfig.getJsonArray("faultTypes")?.map { it.toString() } ?: emptyList()
                val latencyMs = serviceConfig.getLong("latencyMs", 1000)
                val errorCode = serviceConfig.getInteger("errorCode", 500)
                val errorMessage = serviceConfig.getString("errorMessage", "Injected fault")
                
                faultInjectionConfigs[serviceName] = FaultInjectionConfig(
                    enabled = enabled,
                    probability = probability,
                    faultTypes = faultTypes,
                    latencyMs = latencyMs,
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
                
                logger.info("为服务 $serviceName 配置故障注入: enabled=$enabled, probability=$probability")
            }
        }
        
        // 监听故障注入模式变更事件
        vertx.eventBus().consumer<JsonObject>("apix.resilience.fault.injection.mode.change") { message ->
            val enabled = message.body().getBoolean("enabled", false)
            val duration = message.body().getLong("duration", 0)
            
            setFaultInjectionMode(enabled, duration)
        }
        
        // 启动故障注入模式检查
        startFaultInjectionModeCheck()
        
        logger.info("故障注入管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 启动故障注入模式检查。
     */
    private fun startFaultInjectionModeCheck() {
        // 每秒检查一次故障注入模式是否需要关闭
        vertx.setPeriodic(1000) { _ ->
            if (faultInjectionMode.get()) {
                val now = System.currentTimeMillis()
                val startTime = faultInjectionModeStartTime.get()
                val duration = faultInjectionModeDuration.get()
                
                if (duration > 0 && now - startTime > duration) {
                    // 故障注入模式持续时间已到，关闭故障注入模式
                    setFaultInjectionMode(false, 0)
                }
            }
        }
    }
    
    /**
     * 设置故障注入模式。
     * 
     * @param enabled 是否启用
     * @param duration 持续时间（毫秒），0 表示无限期
     */
    fun setFaultInjectionMode(enabled: Boolean, duration: Long) {
        val oldMode = faultInjectionMode.getAndSet(enabled)
        
        if (enabled) {
            faultInjectionModeStartTime.set(System.currentTimeMillis())
            faultInjectionModeDuration.set(duration)
            
            if (!oldMode) {
                logger.info("启用故障注入模式，持续时间: ${if (duration > 0) "${duration}ms" else "无限期"}")
                
                // 发布故障注入模式变更事件
                vertx.eventBus().publish("apix.resilience.fault.injection.mode.changed", JsonObject()
                    .put("enabled", true)
                    .put("duration", duration)
                    .put("timestamp", System.currentTimeMillis())
                )
            }
        } else {
            faultInjectionModeStartTime.set(0)
            faultInjectionModeDuration.set(0)
            
            if (oldMode) {
                logger.info("关闭故障注入模式")
                
                // 发布故障注入模式变更事件
                vertx.eventBus().publish("apix.resilience.fault.injection.mode.changed", JsonObject()
                    .put("enabled", false)
                    .put("timestamp", System.currentTimeMillis())
                )
            }
        }
    }
    
    /**
     * 检查是否应该注入故障。
     * 
     * @param serviceName 服务名称
     * @return 是否应该注入故障
     */
    fun shouldInjectFault(serviceName: String): Boolean {
        // 如果故障注入未启用，不注入故障
        if (!faultInjectionEnabled.get()) {
            return false
        }
        
        // 如果故障注入模式未启用，不注入故障
        if (!faultInjectionMode.get()) {
            return false
        }
        
        // 获取服务配置
        val config = faultInjectionConfigs[serviceName]
        
        // 如果服务没有配置或未启用，使用全局概率
        val probability = if (config == null || !config.enabled) {
            globalFaultProbability.get()
        } else {
            config.probability
        }
        
        // 如果概率为 0，不注入故障
        if (probability <= 0) {
            return false
        }
        
        // 根据概率决定是否注入故障
        val random = ThreadLocalRandom.current().nextInt(100)
        return random < probability
    }
    
    /**
     * 注入故障。
     * 
     * @param serviceName 服务名称
     * @return 包含故障信息的 Future
     */
    fun <T> injectFault(serviceName: String): Future<T> {
        // 获取服务配置
        val config = faultInjectionConfigs[serviceName]
        
        // 获取故障统计信息
        val stats = faultInjectionStats.computeIfAbsent(serviceName) { FaultInjectionStats() }
        
        // 选择故障类型
        val faultType = selectFaultType(config)
        
        // 记录故障注入
        stats.recordFaultInjection(faultType)
        
        // 注入故障
        val promise = Promise.promise<T>()
        
        when (faultType) {
            "LATENCY" -> {
                // 注入延迟
                val latencyMs = config?.latencyMs ?: 1000
                logger.debug("为服务 $serviceName 注入延迟: ${latencyMs}ms")
                
                vertx.setTimer(latencyMs) {
                    promise.fail("Injected latency fault")
                }
            }
            "ERROR" -> {
                // 注入错误
                val errorCode = config?.errorCode ?: 500
                val errorMessage = config?.errorMessage ?: "Injected fault"
                logger.debug("为服务 $serviceName 注入错误: $errorCode - $errorMessage")
                
                promise.fail("Injected error fault: $errorCode - $errorMessage")
            }
            "TIMEOUT" -> {
                // 注入超时
                logger.debug("为服务 $serviceName 注入超时")
                
                promise.fail(java.util.concurrent.TimeoutException("Injected timeout fault"))
            }
            else -> {
                // 未知故障类型，不注入故障
                promise.fail("Unknown fault type: $faultType")
            }
        }
        
        return promise.future()
    }
    
    /**
     * 选择故障类型。
     * 
     * @param config 故障注入配置
     * @return 故障类型
     */
    private fun selectFaultType(config: FaultInjectionConfig?): String {
        // 如果没有配置，默认使用延迟故障
        if (config == null || config.faultTypes.isEmpty()) {
            return "LATENCY"
        }
        
        // 随机选择一种故障类型
        val index = ThreadLocalRandom.current().nextInt(config.faultTypes.size)
        return config.faultTypes[index]
    }
    
    /**
     * 获取所有故障注入的统计信息。
     * 
     * @return 包含统计信息的 JsonObject
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("enabled", faultInjectionEnabled.get())
            .put("globalFaultProbability", globalFaultProbability.get())
            .put("faultInjectionMode", faultInjectionMode.get())
        
        if (faultInjectionMode.get()) {
            stats.put("faultInjectionModeStartTime", faultInjectionModeStartTime.get())
            stats.put("faultInjectionModeDuration", faultInjectionModeDuration.get())
            
            val now = System.currentTimeMillis()
            val startTime = faultInjectionModeStartTime.get()
            val duration = faultInjectionModeDuration.get()
            
            if (duration > 0) {
                stats.put("faultInjectionModeRemainingTime", Math.max(0, duration - (now - startTime)))
            }
        }
        
        val faultsStats = JsonObject()
        for ((name, faultStat) in faultInjectionStats) {
            faultsStats.put(name, faultStat.getStats())
        }
        
        stats.put("faults", faultsStats)
        return stats
    }
    
    /**
     * 故障注入配置。
     */
    data class FaultInjectionConfig(
        val enabled: Boolean,
        val probability: Int,
        val faultTypes: List<String>,
        val latencyMs: Long,
        val errorCode: Int,
        val errorMessage: String
    )
    
    /**
     * 故障注入统计信息。
     */
    class FaultInjectionStats {
        // 故障注入次数
        private val faultCount = AtomicInteger(0)
        
        // 各故障类型注入次数
        private val faultTypeCounts = ConcurrentHashMap<String, AtomicInteger>()
        
        /**
         * 记录故障注入。
         * 
         * @param faultType 故障类型
         */
        fun recordFaultInjection(faultType: String) {
            faultCount.incrementAndGet()
            faultTypeCounts.computeIfAbsent(faultType) { AtomicInteger(0) }.incrementAndGet()
        }
        
        /**
         * 获取统计信息。
         */
        fun getStats(): JsonObject {
            val faultTypeStats = JsonObject()
            for ((faultType, count) in faultTypeCounts) {
                faultTypeStats.put(faultType, count.get())
            }
            
            return JsonObject()
                .put("faultCount", faultCount.get())
                .put("faultTypeCounts", faultTypeStats)
        }
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: FaultInjectionManager? = null
        
        /**
         * 获取 FaultInjectionManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return FaultInjectionManager 实例
         */
        fun getInstance(vertx: Vertx): FaultInjectionManager {
            return instance ?: synchronized(this) {
                instance ?: FaultInjectionManager(vertx).also { instance = it }
            }
        }
    }
}
