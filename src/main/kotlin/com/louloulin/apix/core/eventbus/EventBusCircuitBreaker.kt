package com.louloulin.apix.core.eventbus

import io.vertx.circuitbreaker.CircuitBreaker
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * EventBus熔断器管理器，用于管理不同服务的熔断器实例。
 * 提供带超时和熔断机制的EventBus请求方法，防止服务故障导致请求堆积。
 */
class EventBusCircuitBreaker(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(EventBusCircuitBreaker::class.java)

    // 不同服务的超时配置（毫秒）
    private val timeoutConfig = mapOf(
        "config-service" to 500L,  // 配置服务500ms超时
        "auth-service" to 1000L,   // 认证服务1秒超时
        "metrics-service" to 800L, // 指标服务800ms超时
        "default" to 2000L         // 默认超时2秒
    )

    // 熔断器配置
    private val breakerOptions = mapOf(
        "config-service" to CircuitBreakerOptions()
            .setMaxFailures(5)           // 5次失败后开启熔断
            .setTimeout(600)             // 操作超时时间（毫秒）
            .setResetTimeout(10000),     // 熔断器重置时间（毫秒）

        "auth-service" to CircuitBreakerOptions()
            .setMaxFailures(3)           // 3次失败后开启熔断
            .setTimeout(1200)            // 操作超时时间（毫秒）
            .setResetTimeout(5000),      // 熔断器重置时间（毫秒）

        "default" to CircuitBreakerOptions()
            .setMaxFailures(10)          // 10次失败后开启熔断
            .setTimeout(2500)            // 操作超时时间（毫秒）
            .setResetTimeout(30000)      // 熔断器重置时间（毫秒）
    )

    // 熔断器缓存
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()

    /**
     * 获取指定服务的熔断器
     *
     * @param serviceName 服务名称
     * @return 熔断器实例
     */
    fun getCircuitBreaker(serviceName: String): CircuitBreaker {
        return circuitBreakers.computeIfAbsent(serviceName) { name ->
            val options = breakerOptions[name] ?: breakerOptions["default"]!!
            CircuitBreaker.create("$name-breaker", vertx, options)
                .openHandler {
                    logger.warn("熔断器开启: $name")
                }
                .closeHandler {
                    logger.info("熔断器关闭: $name")
                }
                .halfOpenHandler {
                    logger.info("熔断器半开: $name")
                }
        }
    }

    /**
     * 带超时和熔断的EventBus请求
     *
     * @param address 目标地址
     * @param message 消息内容
     * @param fallback 失败后的备选方案
     * @param timeout 超时时间（毫秒），如果未指定则使用配置的超时时间
     * @return 包含响应的Future
     */
    fun <T> requestWithCircuitBreaker(
        address: String,
        message: Any,
        fallback: (Throwable) -> Message<T>,
        timeout: Long = getTimeout(address)
    ): Future<Message<T>> {
        val serviceName = getServiceName(address)
        val circuitBreaker = getCircuitBreaker(serviceName)
        val options = DeliveryOptions().setSendTimeout(timeout)

        return circuitBreaker.executeWithFallback<Message<T>>({ promise ->
            vertx.eventBus().request<T>(address, message, options) { ar ->
                if (ar.succeeded()) {
                    promise.complete(ar.result())
                } else {
                    logger.warn("EventBus请求失败: address=$address, error=${ar.cause().message}")
                    promise.fail(ar.cause())
                }
            }
        }, fallback)
    }

    /**
     * 带超时和熔断的EventBus请求，返回消息体
     *
     * @param address 目标地址
     * @param message 消息内容
     * @param fallback 失败后的备选方案
     * @param timeout 超时时间（毫秒），如果未指定则使用配置的超时时间
     * @return 包含响应消息体的Future
     */
    fun <T> requestBodyWithCircuitBreaker(
        address: String,
        message: Any,
        fallback: (Throwable) -> T,
        timeout: Long = getTimeout(address)
    ): Future<T> {
        return requestWithCircuitBreaker<T>(address, message, { t -> null as Message<T> }, timeout)
            .map { it.body() }
            .recover { Future.succeededFuture(fallback(it)) }
    }

    /**
     * 获取指定地址的超时时间
     *
     * @param address 目标地址
     * @return 超时时间（毫秒）
     */
    private fun getTimeout(address: String): Long {
        val serviceName = getServiceName(address)
        return timeoutConfig[serviceName] ?: timeoutConfig["default"]!!
    }

    /**
     * 从地址中提取服务名称
     *
     * @param address 目标地址
     * @return 服务名称
     */
    private fun getServiceName(address: String): String {
        val parts = address.split(".")
        return if (parts.size > 1) {
            "${parts[0]}-service"
        } else {
            "default"
        }
    }

    /**
     * 获取所有熔断器的状态
     *
     * @return 包含熔断器状态的JsonObject
     */
    fun getCircuitBreakersStatus(): JsonObject {
        val status = JsonObject()

        circuitBreakers.forEach { (name, breaker) ->
            status.put(name, JsonObject()
                .put("state", breaker.state().name.lowercase())
                .put("failures", breaker.failureCount())
            )
        }

        return status
    }

    companion object {
        // 单例实例
        private var INSTANCE: EventBusCircuitBreaker? = null

        /**
         * 获取EventBusCircuitBreaker的单例实例
         *
         * @param vertx Vertx实例
         * @return EventBusCircuitBreaker实例
         */
        fun getInstance(vertx: Vertx): EventBusCircuitBreaker {
            if (INSTANCE == null) {
                synchronized(EventBusCircuitBreaker::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = EventBusCircuitBreaker(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
