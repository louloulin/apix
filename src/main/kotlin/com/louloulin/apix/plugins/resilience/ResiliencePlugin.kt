package com.louloulin.apix.plugins.resilience

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import java.net.URL
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 请求重试和熔断插件
 * 
 * 该插件用于增强请求的弹性，支持以下功能：
 * - 请求重试：在请求失败时自动重试
 * - 熔断器：在服务不可用时自动熔断，防止级联故障
 * - 超时控制：设置请求超时时间
 * - 后备响应：在请求失败时返回后备响应
 * 
 * 配置参数：
 * - retry: 重试配置
 *   - enabled: 是否启用重试，默认为 true
 *   - maxAttempts: 最大重试次数，默认为 3
 *   - delay: 重试延迟（毫秒），默认为 1000
 *   - retryOn: 重试条件，可选值为 "5xx", "4xx", "timeout", "all"，默认为 ["5xx", "timeout"]
 * - circuitBreaker: 熔断器配置
 *   - enabled: 是否启用熔断器，默认为 true
 *   - failureThreshold: 失败阈值，默认为 50（百分比）
 *   - requestVolumeThreshold: 请求量阈值，默认为 20
 *   - windowSizeInMillis: 窗口大小（毫秒），默认为 10000
 *   - sleepWindowInMillis: 睡眠窗口（毫秒），默认为 5000
 * - timeout: 超时配置
 *   - enabled: 是否启用超时，默认为 true
 *   - timeoutInMillis: 超时时间（毫秒），默认为 5000
 * - fallback: 后备配置
 *   - enabled: 是否启用后备，默认为 true
 *   - statusCode: 后备状态码，默认为 503
 *   - body: 后备响应体
 *   - headers: 后备响应头
 */
class ResiliencePlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(ResiliencePlugin::class.java)
    override val type: String = "resilience"
    
    // Web 客户端
    private val webClient: WebClient
    
    // 重试配置
    private val retryEnabled: Boolean
    private val maxAttempts: Int
    private val delay: Long
    private val retryOn: Set<RetryCondition>
    
    // 熔断器配置
    private val circuitBreakerEnabled: Boolean
    private val failureThreshold: Int
    private val requestVolumeThreshold: Int
    private val windowSizeInMillis: Long
    private val sleepWindowInMillis: Long
    
    // 超时配置
    private val timeoutEnabled: Boolean
    private val timeoutInMillis: Long
    
    // 后备配置
    private val fallbackEnabled: Boolean
    private val fallbackStatusCode: Int
    private val fallbackBody: String?
    private val fallbackHeaders: Map<String, String>
    
    // 熔断器状态
    private val circuitBreakerState = ConcurrentHashMap<String, CircuitBreakerState>()
    
    init {
        // 创建 Web 客户端
        webClient = WebClient.create(vertx, WebClientOptions()
            .setKeepAlive(true)
            .setMaxPoolSize(100)
        )
        
        // 解析重试配置
        val retryConfig = config.config.getJsonObject("retry", JsonObject())
        retryEnabled = retryConfig.getBoolean("enabled", true)
        maxAttempts = retryConfig.getInteger("maxAttempts", 3)
        delay = retryConfig.getLong("delay", 1000)
        
        val retryOnArray = retryConfig.getJsonArray("retryOn", JsonArray().add("5xx").add("timeout"))
        retryOn = retryOnArray.map { 
            when (it.toString().lowercase()) {
                "4xx" -> RetryCondition.CLIENT_ERROR
                "5xx" -> RetryCondition.SERVER_ERROR
                "timeout" -> RetryCondition.TIMEOUT
                "all" -> RetryCondition.ALL
                else -> null
            }
        }.filterNotNull().toSet()
        
        // 解析熔断器配置
        val circuitBreakerConfig = config.config.getJsonObject("circuitBreaker", JsonObject())
        circuitBreakerEnabled = circuitBreakerConfig.getBoolean("enabled", true)
        failureThreshold = circuitBreakerConfig.getInteger("failureThreshold", 50)
        requestVolumeThreshold = circuitBreakerConfig.getInteger("requestVolumeThreshold", 20)
        windowSizeInMillis = circuitBreakerConfig.getLong("windowSizeInMillis", 10000)
        sleepWindowInMillis = circuitBreakerConfig.getLong("sleepWindowInMillis", 5000)
        
        // 解析超时配置
        val timeoutConfig = config.config.getJsonObject("timeout", JsonObject())
        timeoutEnabled = timeoutConfig.getBoolean("enabled", true)
        timeoutInMillis = timeoutConfig.getLong("timeoutInMillis", 5000)
        
        // 解析后备配置
        val fallbackConfig = config.config.getJsonObject("fallback", JsonObject())
        fallbackEnabled = fallbackConfig.getBoolean("enabled", true)
        fallbackStatusCode = fallbackConfig.getInteger("statusCode", 503)
        fallbackBody = fallbackConfig.getString("body")
        
        val fallbackHeadersConfig = fallbackConfig.getJsonObject("headers", JsonObject())
        fallbackHeaders = fallbackHeadersConfig.map.mapValues { it.value.toString() }
        
        logger.info("Initialized resilience plugin: retryEnabled={}, maxAttempts={}, circuitBreakerEnabled={}, timeoutEnabled={}", 
            retryEnabled, maxAttempts, circuitBreakerEnabled, timeoutEnabled)
    }
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 获取目标 URL
            val targetUrl = context.get<String>("targetUrl")
            if (targetUrl == null) {
                // 没有目标 URL，继续处理请求
                context.next()
                promise.complete()
                return promise.future()
            }
            
            // 解析目标 URL
            val url = URL(targetUrl)
            val host = url.host
            val port = if (url.port == -1) url.defaultPort else url.port
            val path = url.path + if (url.query != null) "?${url.query}" else ""
            
            // 获取熔断器状态
            val circuitBreakerKey = "$host:$port"
            val circuitBreaker = getCircuitBreaker(circuitBreakerKey)
            
            // 检查熔断器状态
            if (circuitBreakerEnabled && circuitBreaker.isOpen()) {
                // 熔断器打开，返回后备响应
                if (fallbackEnabled) {
                    sendFallbackResponse(context, "Circuit breaker is open")
                } else {
                    // 没有后备响应，返回 503 错误
                    context.response()
                        .setStatusCode(503)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", "Service Unavailable")
                            .put("message", "Circuit breaker is open")
                            .encode()
                        )
                }
                promise.complete()
                return promise.future()
            }
            
            // 执行请求
            executeRequest(context, url, 1).onComplete { ar ->
                if (ar.succeeded()) {
                    // 请求成功
                    val response = ar.result()
                    
                    // 更新熔断器状态
                    if (circuitBreakerEnabled) {
                        circuitBreaker.recordSuccess()
                    }
                    
                    // 设置响应
                    context.response()
                        .setStatusCode(response.statusCode())
                        .putHeader("Content-Type", response.getHeader("Content-Type") ?: "application/json")
                        .end(response.body())
                    
                    promise.complete()
                } else {
                    // 请求失败
                    logger.error("Request failed", ar.cause())
                    
                    // 更新熔断器状态
                    if (circuitBreakerEnabled) {
                        circuitBreaker.recordFailure()
                    }
                    
                    // 返回后备响应
                    if (fallbackEnabled) {
                        sendFallbackResponse(context, ar.cause().message ?: "Request failed")
                    } else {
                        // 没有后备响应，返回 500 错误
                        context.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("error", "Internal Server Error")
                                .put("message", ar.cause().message ?: "Request failed")
                                .encode()
                            )
                    }
                    
                    promise.complete()
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing resilience plugin", e)
            // 发生错误，继续处理请求
            context.next()
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 执行请求
     */
    private fun executeRequest(context: RoutingContext, url: URL, attempt: Int): Future<HttpResponse<Buffer>> {
        val promise = Promise.promise<HttpResponse<Buffer>>()
        
        try {
            // 获取请求方法
            val method = context.request().method()
            
            // 创建请求
            val request = when (method) {
                HttpMethod.GET -> webClient.getAbs(url.toString())
                HttpMethod.POST -> webClient.postAbs(url.toString())
                HttpMethod.PUT -> webClient.putAbs(url.toString())
                HttpMethod.DELETE -> webClient.deleteAbs(url.toString())
                HttpMethod.PATCH -> webClient.patchAbs(url.toString())
                else -> webClient.getAbs(url.toString())
            }
            
            // 设置请求头
            context.request().headers().forEach { header ->
                request.putHeader(header.key, header.value)
            }
            
            // 设置超时
            if (timeoutEnabled) {
                request.timeout(timeoutInMillis)
            }
            
            // 发送请求
            val body = context.body().buffer()
            if (body != null && method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
                request.sendBuffer(body) { ar ->
                    handleResponse(ar, context, url, attempt, promise)
                }
            } else {
                request.send { ar ->
                    handleResponse(ar, context, url, attempt, promise)
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing request", e)
            if (shouldRetry(e, attempt)) {
                // 重试请求
                retryRequest(context, url, attempt, promise)
            } else {
                promise.fail(e)
            }
        }
        
        return promise.future()
    }
    
    /**
     * 处理响应
     */
    private fun handleResponse(ar: io.vertx.core.AsyncResult<HttpResponse<Buffer>>, context: RoutingContext, url: URL, attempt: Int, promise: Promise<HttpResponse<Buffer>>) {
        if (ar.succeeded()) {
            val response = ar.result()
            val statusCode = response.statusCode()
            
            if (shouldRetry(statusCode, attempt)) {
                // 重试请求
                retryRequest(context, url, attempt, promise)
            } else {
                promise.complete(response)
            }
        } else {
            val cause = ar.cause()
            logger.error("Request failed", cause)
            
            if (shouldRetry(cause, attempt)) {
                // 重试请求
                retryRequest(context, url, attempt, promise)
            } else {
                promise.fail(cause)
            }
        }
    }
    
    /**
     * 重试请求
     */
    private fun retryRequest(context: RoutingContext, url: URL, attempt: Int, promise: Promise<HttpResponse<Buffer>>) {
        val nextAttempt = attempt + 1
        
        // 延迟重试
        vertx.setTimer(delay) {
            executeRequest(context, url, nextAttempt).onComplete { ar ->
                if (ar.succeeded()) {
                    promise.complete(ar.result())
                } else {
                    promise.fail(ar.cause())
                }
            }
        }
    }
    
    /**
     * 检查是否应该重试（基于状态码）
     */
    private fun shouldRetry(statusCode: Int, attempt: Int): Boolean {
        if (!retryEnabled || attempt >= maxAttempts) {
            return false
        }
        
        return when {
            RetryCondition.ALL in retryOn -> true
            RetryCondition.CLIENT_ERROR in retryOn && statusCode in 400..499 -> true
            RetryCondition.SERVER_ERROR in retryOn && statusCode in 500..599 -> true
            else -> false
        }
    }
    
    /**
     * 检查是否应该重试（基于异常）
     */
    private fun shouldRetry(cause: Throwable, attempt: Int): Boolean {
        if (!retryEnabled || attempt >= maxAttempts) {
            return false
        }
        
        return when {
            RetryCondition.ALL in retryOn -> true
            RetryCondition.TIMEOUT in retryOn && cause.message?.contains("timeout") == true -> true
            else -> false
        }
    }
    
    /**
     * 发送后备响应
     */
    private fun sendFallbackResponse(context: RoutingContext, errorMessage: String) {
        val response = context.response()
        
        // 设置状态码
        response.setStatusCode(fallbackStatusCode)
        
        // 设置响应头
        fallbackHeaders.forEach { (name, value) ->
            response.putHeader(name, value)
        }
        
        // 设置默认的 Content-Type
        if (!fallbackHeaders.containsKey("Content-Type")) {
            response.putHeader("Content-Type", "application/json")
        }
        
        // 设置响应体
        if (fallbackBody != null) {
            response.end(fallbackBody)
        } else {
            response.end(JsonObject()
                .put("error", "Service Unavailable")
                .put("message", errorMessage)
                .encode()
            )
        }
    }
    
    /**
     * 获取熔断器
     */
    private fun getCircuitBreaker(key: String): CircuitBreakerState {
        return circuitBreakerState.computeIfAbsent(key) {
            CircuitBreakerState(
                failureThreshold = failureThreshold,
                requestVolumeThreshold = requestVolumeThreshold,
                windowSizeInMillis = windowSizeInMillis,
                sleepWindowInMillis = sleepWindowInMillis
            )
        }
    }
    
    override fun shutdown() {
        // 关闭 Web 客户端
        webClient.close()
        
        // 清空熔断器状态
        circuitBreakerState.clear()
    }
    
    /**
     * 重试条件
     */
    enum class RetryCondition {
        CLIENT_ERROR,  // 4xx 错误
        SERVER_ERROR,  // 5xx 错误
        TIMEOUT,       // 超时
        ALL            // 所有错误
    }
    
    /**
     * 熔断器状态
     */
    class CircuitBreakerState(
        private val failureThreshold: Int,
        private val requestVolumeThreshold: Int,
        private val windowSizeInMillis: Long,
        private val sleepWindowInMillis: Long
    ) {
        // 熔断器状态
        private var state = State.CLOSED
        
        // 上次状态变更时间
        private var lastStateChangeTime = Instant.now().toEpochMilli()
        
        // 请求计数
        private val requestCount = AtomicInteger(0)
        
        // 失败计数
        private val failureCount = AtomicInteger(0)
        
        // 上次重置时间
        private var lastResetTime = Instant.now().toEpochMilli()
        
        /**
         * 检查熔断器是否打开
         */
        fun isOpen(): Boolean {
            val now = Instant.now().toEpochMilli()
            
            // 检查是否需要重置计数
            if (now - lastResetTime > windowSizeInMillis) {
                resetCounts()
            }
            
            // 检查是否需要尝试半开状态
            if (state == State.OPEN && now - lastStateChangeTime > sleepWindowInMillis) {
                state = State.HALF_OPEN
                lastStateChangeTime = now
            }
            
            return state == State.OPEN
        }
        
        /**
         * 记录成功
         */
        fun recordSuccess() {
            val now = Instant.now().toEpochMilli()
            
            // 检查是否需要重置计数
            if (now - lastResetTime > windowSizeInMillis) {
                resetCounts()
            }
            
            // 增加请求计数
            requestCount.incrementAndGet()
            
            // 如果是半开状态，则关闭熔断器
            if (state == State.HALF_OPEN) {
                state = State.CLOSED
                lastStateChangeTime = now
            }
        }
        
        /**
         * 记录失败
         */
        fun recordFailure() {
            val now = Instant.now().toEpochMilli()
            
            // 检查是否需要重置计数
            if (now - lastResetTime > windowSizeInMillis) {
                resetCounts()
            }
            
            // 增加请求计数和失败计数
            requestCount.incrementAndGet()
            failureCount.incrementAndGet()
            
            // 如果是半开状态，则打开熔断器
            if (state == State.HALF_OPEN) {
                state = State.OPEN
                lastStateChangeTime = now
                return
            }
            
            // 检查是否需要打开熔断器
            val requests = requestCount.get()
            val failures = failureCount.get()
            
            if (requests >= requestVolumeThreshold) {
                val failureRate = (failures * 100) / requests
                if (failureRate >= failureThreshold) {
                    state = State.OPEN
                    lastStateChangeTime = now
                }
            }
        }
        
        /**
         * 重置计数
         */
        private fun resetCounts() {
            requestCount.set(0)
            failureCount.set(0)
            lastResetTime = Instant.now().toEpochMilli()
        }
        
        /**
         * 熔断器状态
         */
        enum class State {
            CLOSED,     // 关闭状态，正常工作
            OPEN,       // 打开状态，快速失败
            HALF_OPEN   // 半开状态，尝试恢复
        }
    }
    
    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return ResiliencePlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
