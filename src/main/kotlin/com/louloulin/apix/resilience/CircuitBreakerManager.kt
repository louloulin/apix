package com.louloulin.apix.resilience

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 熔断器管理器，用于管理系统的熔断器。
 * 熔断器可以防止系统持续调用可能失败的操作，从而防止级联故障。
 */
class CircuitBreakerManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(CircuitBreakerManager::class.java)
    
    // 熔断器是否启用
    private val circuitBreakerEnabled = AtomicBoolean(true)
    
    // 熔断器配置
    private val circuitBreakerConfigs = ConcurrentHashMap<String, CircuitBreakerConfig>()
    
    // 熔断器实例
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()
    
    // 默认熔断器配置
    private val defaultConfig = CircuitBreakerConfig(
        failureThreshold = 50,
        requestVolumeThreshold = 20,
        windowSizeInMillis = 10000,
        sleepWindowInMillis = 5000,
        errorTypes = listOf("TIMEOUT", "FAILURE", "REJECTION")
    )
    
    /**
     * 初始化熔断器管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化熔断器管理器")
        
        // 获取配置
        val resilienceConfig = config.getJsonObject("resilience", JsonObject())
        val circuitBreakerConfig = resilienceConfig.getJsonObject("circuitBreaker", JsonObject())
        
        // 检查熔断器是否启用
        circuitBreakerEnabled.set(circuitBreakerConfig.getBoolean("enabled", true))
        
        if (!circuitBreakerEnabled.get()) {
            logger.info("熔断器未启用")
            return Future.succeededFuture()
        }
        
        // 获取默认配置
        val defaultConfigJson = circuitBreakerConfig.getJsonObject("default", JsonObject())
        if (defaultConfigJson != null) {
            val failureThreshold = defaultConfigJson.getInteger("failureThreshold", defaultConfig.failureThreshold)
            val requestVolumeThreshold = defaultConfigJson.getInteger("requestVolumeThreshold", defaultConfig.requestVolumeThreshold)
            val windowSizeInMillis = defaultConfigJson.getLong("windowSizeInMillis", defaultConfig.windowSizeInMillis)
            val sleepWindowInMillis = defaultConfigJson.getLong("sleepWindowInMillis", defaultConfig.sleepWindowInMillis)
            val errorTypesJson = defaultConfigJson.getJsonArray("errorTypes")
            val errorTypes = if (errorTypesJson != null) {
                errorTypesJson.map { it.toString() }
            } else {
                defaultConfig.errorTypes
            }
            
            defaultConfig.failureThreshold = failureThreshold
            defaultConfig.requestVolumeThreshold = requestVolumeThreshold
            defaultConfig.windowSizeInMillis = windowSizeInMillis
            defaultConfig.sleepWindowInMillis = sleepWindowInMillis
            defaultConfig.errorTypes = errorTypes
        }
        
        // 获取服务特定配置
        val servicesConfig = circuitBreakerConfig.getJsonObject("services", JsonObject())
        if (servicesConfig != null) {
            for (serviceName in servicesConfig.fieldNames()) {
                val serviceConfig = servicesConfig.getJsonObject(serviceName)
                val failureThreshold = serviceConfig.getInteger("failureThreshold", defaultConfig.failureThreshold)
                val requestVolumeThreshold = serviceConfig.getInteger("requestVolumeThreshold", defaultConfig.requestVolumeThreshold)
                val windowSizeInMillis = serviceConfig.getLong("windowSizeInMillis", defaultConfig.windowSizeInMillis)
                val sleepWindowInMillis = serviceConfig.getLong("sleepWindowInMillis", defaultConfig.sleepWindowInMillis)
                val errorTypesJson = serviceConfig.getJsonArray("errorTypes")
                val errorTypes = if (errorTypesJson != null) {
                    errorTypesJson.map { it.toString() }
                } else {
                    defaultConfig.errorTypes
                }
                
                circuitBreakerConfigs[serviceName] = CircuitBreakerConfig(
                    failureThreshold = failureThreshold,
                    requestVolumeThreshold = requestVolumeThreshold,
                    windowSizeInMillis = windowSizeInMillis,
                    sleepWindowInMillis = sleepWindowInMillis,
                    errorTypes = errorTypes
                )
                
                logger.info("为服务 $serviceName 配置熔断器: failureThreshold=$failureThreshold, requestVolumeThreshold=$requestVolumeThreshold")
            }
        }
        
        // 启动熔断器状态检查
        startCircuitBreakerStateCheck()
        
        logger.info("熔断器管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 启动熔断器状态检查。
     */
    private fun startCircuitBreakerStateCheck() {
        // 每隔 1 秒检查一次熔断器状态
        vertx.setPeriodic(1000) { _ ->
            for ((name, circuitBreaker) in circuitBreakers) {
                // 检查熔断器状态
                val oldState = circuitBreaker.state.get()
                circuitBreaker.checkState()
                val newState = circuitBreaker.state.get()
                
                // 如果状态发生变化，记录日志
                if (oldState != newState) {
                    logger.info("熔断器 $name 状态变化: $oldState -> $newState")
                    
                    // 发布熔断器状态变化事件
                    vertx.eventBus().publish("apix.resilience.circuitbreaker.state.change", JsonObject()
                        .put("name", name)
                        .put("oldState", oldState.name)
                        .put("newState", newState.name)
                        .put("timestamp", System.currentTimeMillis())
                    )
                }
            }
        }
    }
    
    /**
     * 获取指定服务的熔断器。
     * 
     * @param serviceName 服务名称
     * @return 熔断器实例
     */
    fun getCircuitBreaker(serviceName: String): CircuitBreaker {
        return circuitBreakers.computeIfAbsent(serviceName) { name ->
            val config = circuitBreakerConfigs[name] ?: defaultConfig
            CircuitBreaker(name, config)
        }
    }
    
    /**
     * 使用熔断器执行操作。
     * 
     * @param serviceName 服务名称
     * @param action 要执行的操作
     * @param fallback 失败后的备选方案
     * @return 操作结果的 Future
     */
    fun <T> executeWithCircuitBreaker(
        serviceName: String,
        action: () -> Future<T>,
        fallback: (Throwable) -> Future<T>
    ): Future<T> {
        // 如果熔断器未启用，直接执行操作
        if (!circuitBreakerEnabled.get()) {
            return action()
                .recover { throwable -> fallback(throwable) }
        }
        
        // 获取熔断器
        val circuitBreaker = getCircuitBreaker(serviceName)
        
        // 检查熔断器状态
        if (circuitBreaker.isOpen()) {
            // 熔断器打开，执行备选方案
            return fallback(CircuitBreakerOpenException("Circuit breaker $serviceName is open"))
        }
        
        // 熔断器关闭或半开，执行操作
        return action()
            .onSuccess { result ->
                // 操作成功，记录成功
                circuitBreaker.recordSuccess()
            }
            .onFailure { throwable ->
                // 操作失败，记录失败
                val errorType = getErrorType(throwable)
                circuitBreaker.recordFailure(errorType)
            }
            .recover { throwable ->
                // 操作失败，执行备选方案
                fallback(throwable)
            }
    }
    
    /**
     * 获取错误类型。
     * 
     * @param throwable 异常
     * @return 错误类型
     */
    private fun getErrorType(throwable: Throwable): String {
        return when (throwable) {
            is java.util.concurrent.TimeoutException -> "TIMEOUT"
            is BulkheadManager.BulkheadFullException -> "REJECTION"
            else -> "FAILURE"
        }
    }
    
    /**
     * 获取所有熔断器的统计信息。
     * 
     * @return 包含统计信息的 JsonObject
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("enabled", circuitBreakerEnabled.get())
        
        val circuitBreakersStats = JsonObject()
        for ((name, circuitBreaker) in circuitBreakers) {
            circuitBreakersStats.put(name, circuitBreaker.getStats())
        }
        
        stats.put("circuitBreakers", circuitBreakersStats)
        return stats
    }
    
    /**
     * 熔断器配置。
     */
    data class CircuitBreakerConfig(
        var failureThreshold: Int,
        var requestVolumeThreshold: Int,
        var windowSizeInMillis: Long,
        var sleepWindowInMillis: Long,
        var errorTypes: List<String>
    )
    
    /**
     * 熔断器状态。
     */
    enum class CircuitBreakerState {
        CLOSED,     // 关闭状态，允许请求通过
        OPEN,       // 打开状态，拒绝所有请求
        HALF_OPEN   // 半开状态，允许部分请求通过以测试服务是否恢复
    }
    
    /**
     * 熔断器实现。
     */
    inner class CircuitBreaker(
        val name: String,
        val config: CircuitBreakerConfig
    ) {
        // 熔断器状态
        val state = AtomicReference(CircuitBreakerState.CLOSED)
        
        // 请求计数
        private val requestCount = AtomicInteger(0)
        
        // 失败计数
        private val failureCount = AtomicInteger(0)
        
        // 错误类型计数
        private val errorTypeCounts = ConcurrentHashMap<String, AtomicInteger>()
        
        // 上次重置时间
        private var lastResetTime = Instant.now().toEpochMilli()
        
        // 上次状态变化时间
        private var lastStateChangeTime = Instant.now().toEpochMilli()
        
        // 半开状态下的成功计数
        private val halfOpenSuccessCount = AtomicInteger(0)
        
        // 半开状态下的失败计数
        private val halfOpenFailureCount = AtomicInteger(0)
        
        /**
         * 检查熔断器状态。
         */
        fun checkState() {
            val now = Instant.now().toEpochMilli()
            
            // 检查是否需要重置计数
            if (now - lastResetTime > config.windowSizeInMillis) {
                resetCounts()
            }
            
            // 检查是否需要尝试半开状态
            if (state.get() == CircuitBreakerState.OPEN && now - lastStateChangeTime > config.sleepWindowInMillis) {
                state.set(CircuitBreakerState.HALF_OPEN)
                lastStateChangeTime = now
                halfOpenSuccessCount.set(0)
                halfOpenFailureCount.set(0)
            }
        }
        
        /**
         * 检查熔断器是否打开。
         */
        fun isOpen(): Boolean {
            checkState()
            return state.get() == CircuitBreakerState.OPEN
        }
        
        /**
         * 记录成功。
         */
        fun recordSuccess() {
            val now = Instant.now().toEpochMilli()
            
            // 检查是否需要重置计数
            if (now - lastResetTime > config.windowSizeInMillis) {
                resetCounts()
            }
            
            // 增加请求计数
            requestCount.incrementAndGet()
            
            // 如果是半开状态，检查是否需要关闭熔断器
            if (state.get() == CircuitBreakerState.HALF_OPEN) {
                val successCount = halfOpenSuccessCount.incrementAndGet()
                
                // 如果半开状态下的成功次数达到阈值，关闭熔断器
                if (successCount >= 3) {
                    state.set(CircuitBreakerState.CLOSED)
                    lastStateChangeTime = now
                }
            }
        }
        
        /**
         * 记录失败。
         * 
         * @param errorType 错误类型
         */
        fun recordFailure(errorType: String) {
            val now = Instant.now().toEpochMilli()
            
            // 检查是否需要重置计数
            if (now - lastResetTime > config.windowSizeInMillis) {
                resetCounts()
            }
            
            // 检查错误类型是否需要计入失败
            if (config.errorTypes.contains(errorType)) {
                // 增加请求计数和失败计数
                requestCount.incrementAndGet()
                failureCount.incrementAndGet()
                
                // 增加错误类型计数
                errorTypeCounts.computeIfAbsent(errorType) { AtomicInteger(0) }.incrementAndGet()
                
                // 如果是半开状态，立即打开熔断器
                if (state.get() == CircuitBreakerState.HALF_OPEN) {
                    halfOpenFailureCount.incrementAndGet()
                    state.set(CircuitBreakerState.OPEN)
                    lastStateChangeTime = now
                    return
                }
                
                // 检查是否需要打开熔断器
                val requests = requestCount.get()
                val failures = failureCount.get()
                
                if (requests >= config.requestVolumeThreshold) {
                    val failureRate = (failures * 100) / requests
                    if (failureRate >= config.failureThreshold) {
                        state.set(CircuitBreakerState.OPEN)
                        lastStateChangeTime = now
                    }
                }
            } else {
                // 错误类型不计入失败，只增加请求计数
                requestCount.incrementAndGet()
            }
        }
        
        /**
         * 重置计数。
         */
        private fun resetCounts() {
            requestCount.set(0)
            failureCount.set(0)
            errorTypeCounts.clear()
            lastResetTime = Instant.now().toEpochMilli()
        }
        
        /**
         * 获取熔断器状态。
         */
        fun getStats(): JsonObject {
            val errorTypeStats = JsonObject()
            for ((errorType, count) in errorTypeCounts) {
                errorTypeStats.put(errorType, count.get())
            }
            
            return JsonObject()
                .put("name", name)
                .put("state", state.get().name)
                .put("requestCount", requestCount.get())
                .put("failureCount", failureCount.get())
                .put("errorTypeCounts", errorTypeStats)
                .put("failureRate", if (requestCount.get() > 0) (failureCount.get() * 100) / requestCount.get() else 0)
                .put("lastResetTime", lastResetTime)
                .put("lastStateChangeTime", lastStateChangeTime)
                .put("config", JsonObject()
                    .put("failureThreshold", config.failureThreshold)
                    .put("requestVolumeThreshold", config.requestVolumeThreshold)
                    .put("windowSizeInMillis", config.windowSizeInMillis)
                    .put("sleepWindowInMillis", config.sleepWindowInMillis)
                    .put("errorTypes", config.errorTypes)
                )
        }
    }
    
    /**
     * 熔断器打开异常。
     */
    class CircuitBreakerOpenException(message: String) : Exception(message)
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: CircuitBreakerManager? = null
        
        /**
         * 获取 CircuitBreakerManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return CircuitBreakerManager 实例
         */
        fun getInstance(vertx: Vertx): CircuitBreakerManager {
            return instance ?: synchronized(this) {
                instance ?: CircuitBreakerManager(vertx).also { instance = it }
            }
        }
    }
}
