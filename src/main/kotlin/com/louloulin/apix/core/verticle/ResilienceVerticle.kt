package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.resilience.BulkheadManager
import com.louloulin.apix.resilience.CircuitBreakerManager
import com.louloulin.apix.resilience.FallbackManager
import com.louloulin.apix.resilience.FaultInjectionManager
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 故障隔离 Verticle，负责管理系统的故障隔离功能。
 */
class ResilienceVerticle : BaseVerticle() {
    // 使用 BaseVerticle 中的 logger
    
    // 舱壁管理器
    private lateinit var bulkheadManager: BulkheadManager
    
    // 熔断器管理器
    private lateinit var circuitBreakerManager: CircuitBreakerManager
    
    // 降级策略管理器
    private lateinit var fallbackManager: FallbackManager
    
    // 故障注入管理器
    private lateinit var faultInjectionManager: FaultInjectionManager
    
    override fun registerEventBusHandlers() {
        // 故障隔离相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_STATUS_GET, this::handleGetResilienceStatus)
        
        // 舱壁相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_BULKHEAD_EXECUTE, this::handleExecuteWithBulkhead)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_BULKHEAD_STATS_GET, this::handleGetBulkheadStats)
        
        // 熔断器相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_CIRCUIT_BREAKER_EXECUTE, this::handleExecuteWithCircuitBreaker)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_CIRCUIT_BREAKER_STATS_GET, this::handleGetCircuitBreakerStats)
        
        // 降级策略相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FALLBACK_APPLY, this::handleApplyFallback)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FALLBACK_LEVEL_SET, this::handleSetFallbackLevel)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FALLBACK_LEVEL_GET, this::handleGetFallbackLevel)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FALLBACK_STATS_GET, this::handleGetFallbackStats)
        
        // 故障注入相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FAULT_INJECTION_MODE_SET, this::handleSetFaultInjectionMode)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FAULT_INJECTION_CHECK, this::handleCheckFaultInjection)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FAULT_INJECTION_INJECT, this::handleInjectFault)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESILIENCE_FAULT_INJECTION_STATS_GET, this::handleGetFaultInjectionStats)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // 初始化舱壁管理器
                    bulkheadManager = BulkheadManager.getInstance(vertx)
                    
                    // 初始化熔断器管理器
                    circuitBreakerManager = CircuitBreakerManager.getInstance(vertx)
                    
                    // 初始化降级策略管理器
                    fallbackManager = FallbackManager.getInstance(vertx)
                    
                    // 初始化故障注入管理器
                    faultInjectionManager = FaultInjectionManager.getInstance(vertx)
                    
                    // 初始化所有管理器
                    bulkheadManager.initialize(config)
                        .compose { _ -> circuitBreakerManager.initialize(config) }
                        .compose { _ -> fallbackManager.initialize(config) }
                        .compose { _ -> faultInjectionManager.initialize(config) }
                        .onSuccess { _ ->
                            logger.info("ResilienceVerticle 启动成功")
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("ResilienceVerticle 启动失败", cause)
                            startPromise.fail(cause)
                        }
                } else {
                    val errorMsg = "获取配置失败: ${configResponse.getString("message", "未知错误")}"
                    logger.error(errorMsg)
                    startPromise.fail(errorMsg)
                }
            } else {
                logger.error("获取配置失败", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("ResilienceVerticle 停止")
        stopPromise.complete()
    }
    
    /**
     * 处理获取故障隔离状态请求。
     */
    private fun handleGetResilienceStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val status = JsonObject()
            .put("bulkhead", bulkheadManager.getStats())
            .put("circuitBreaker", circuitBreakerManager.getStats())
            .put("fallback", fallbackManager.getStats())
            .put("faultInjection", faultInjectionManager.getStats())
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }
    
    /**
     * 处理使用舱壁执行操作请求。
     */
    private fun handleExecuteWithBulkhead(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val serviceName = request.getString("serviceName")
        val action = request.getString("action")
        
        if (serviceName == null || action == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 serviceName 或 action 参数")
            )
            return
        }
        
        // 获取舱壁
        val bulkhead = bulkheadManager.getBulkhead(serviceName)
        
        // 尝试获取许可
        bulkhead.tryAcquirePermission()
            .onSuccess { acquired ->
                if (acquired) {
                    // 获取许可成功，执行操作
                    // 在实际实现中，这里应该执行实际的操作
                    // 为简化实现，这里直接返回成功
                    
                    // 释放许可
                    bulkhead.releasePermission()
                    
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", JsonObject()
                            .put("serviceName", serviceName)
                            .put("action", action)
                            .put("status", "executed")
                        )
                    )
                } else {
                    // 获取许可失败，返回错误
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "舱壁已满，无法执行操作")
                    )
                }
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "获取舱壁许可失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理获取舱壁统计信息请求。
     */
    private fun handleGetBulkheadStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        message.reply(JsonObject()
            .put("success", true)
            .put("result", bulkheadManager.getStats())
        )
    }
    
    /**
     * 处理使用熔断器执行操作请求。
     */
    private fun handleExecuteWithCircuitBreaker(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val serviceName = request.getString("serviceName")
        val action = request.getString("action")
        
        if (serviceName == null || action == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 serviceName 或 action 参数")
            )
            return
        }
        
        // 获取熔断器
        val circuitBreaker = circuitBreakerManager.getCircuitBreaker(serviceName)
        
        // 检查熔断器状态
        if (circuitBreaker.isOpen()) {
            // 熔断器打开，返回错误
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "熔断器已打开，无法执行操作")
            )
            return
        }
        
        // 熔断器关闭或半开，执行操作
        // 在实际实现中，这里应该执行实际的操作
        // 为简化实现，这里直接返回成功
        
        // 记录成功
        circuitBreaker.recordSuccess()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("serviceName", serviceName)
                .put("action", action)
                .put("status", "executed")
            )
        )
    }
    
    /**
     * 处理获取熔断器统计信息请求。
     */
    private fun handleGetCircuitBreakerStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        message.reply(JsonObject()
            .put("success", true)
            .put("result", circuitBreakerManager.getStats())
        )
    }
    
    /**
     * 处理应用降级策略请求。
     */
    private fun handleApplyFallback(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val serviceName = request.getString("serviceName")
        val originalData = request.getJsonObject("originalData")
        val errorType = request.getString("errorType", "FAILURE")
        
        if (serviceName == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 serviceName 参数")
            )
            return
        }
        
        // 应用降级策略
        val fallbackData = fallbackManager.applyFallback(serviceName, originalData, errorType)
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("serviceName", serviceName)
                .put("fallbackData", fallbackData)
            )
        )
    }
    
    /**
     * 处理设置降级级别请求。
     */
    private fun handleSetFallbackLevel(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val level = request.getInteger("level")
        
        if (level == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 level 参数")
            )
            return
        }
        
        // 设置系统降级级别
        fallbackManager.setSystemDegradationLevel(level)
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("level", level)
            )
        )
    }
    
    /**
     * 处理获取降级级别请求。
     */
    private fun handleGetFallbackLevel(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val level = fallbackManager.getSystemDegradationLevel()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("level", level)
            )
        )
    }
    
    /**
     * 处理获取降级策略统计信息请求。
     */
    private fun handleGetFallbackStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        message.reply(JsonObject()
            .put("success", true)
            .put("result", fallbackManager.getStats())
        )
    }
    
    /**
     * 处理设置故障注入模式请求。
     */
    private fun handleSetFaultInjectionMode(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val enabled = request.getBoolean("enabled")
        val duration = request.getLong("duration", 0)
        
        if (enabled == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 enabled 参数")
            )
            return
        }
        
        // 设置故障注入模式
        faultInjectionManager.setFaultInjectionMode(enabled, duration)
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("enabled", enabled)
                .put("duration", duration)
            )
        )
    }
    
    /**
     * 处理检查故障注入请求。
     */
    private fun handleCheckFaultInjection(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val serviceName = request.getString("serviceName")
        
        if (serviceName == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 serviceName 参数")
            )
            return
        }
        
        // 检查是否应该注入故障
        val shouldInject = faultInjectionManager.shouldInjectFault(serviceName)
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("serviceName", serviceName)
                .put("shouldInject", shouldInject)
            )
        )
    }
    
    /**
     * 处理注入故障请求。
     */
    private fun handleInjectFault(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val serviceName = request.getString("serviceName")
        
        if (serviceName == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 serviceName 参数")
            )
            return
        }
        
        // 注入故障
        faultInjectionManager.injectFault<JsonObject>(serviceName)
            .onSuccess { result ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", result)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "注入故障失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理获取故障注入统计信息请求。
     */
    private fun handleGetFaultInjectionStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        message.reply(JsonObject()
            .put("success", true)
            .put("result", faultInjectionManager.getStats())
        )
    }
}
