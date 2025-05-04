package com.louloulin.apix.plugins

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.plugin.Plugin
import com.louloulin.apix.core.plugin.PluginContext
import com.louloulin.apix.core.plugin.PluginType
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * 限流插件，支持多种限流算法
 */
class RateLimitPlugin : Plugin {
    private val logger = LoggerFactory.getLogger(RateLimitPlugin::class.java)
    
    override val name: String = "rate-limit"
    override val version: String = "1.0.0"
    override val description: String = "限流插件，支持多种限流算法"
    override val type: PluginType = PluginType.REQUEST
    
    private lateinit var context: PluginContext
    private var enabled: Boolean = true
    
    // 限流配置
    private var algorithm: String = "token_bucket"
    private var keyPrefix: String = "ratelimit:"
    private var keyResolver: String = "ip"
    private var limit: Int = 100
    private var window: Long = 60000L
    private var capacity: Int = 100
    private var refillRate: Double = 1.0
    private var refillInterval: Long = 1000L
    private var statusCode: Int = 429
    private var errorMessage: String = "Too Many Requests"
    private var headers: Boolean = true
    
    override fun init(context: PluginContext, config: JsonObject): Future<Void> {
        this.context = context
        
        // 加载配置
        enabled = config.getBoolean("enabled", enabled)
        algorithm = config.getString("algorithm", algorithm)
        keyPrefix = config.getString("keyPrefix", keyPrefix)
        keyResolver = config.getString("keyResolver", keyResolver)
        limit = config.getInteger("limit", limit)
        window = config.getLong("window", window)
        capacity = config.getInteger("capacity", capacity)
        refillRate = config.getDouble("refillRate", refillRate)
        refillInterval = config.getLong("refillInterval", refillInterval)
        statusCode = config.getInteger("statusCode", statusCode)
        errorMessage = config.getString("errorMessage", errorMessage)
        headers = config.getBoolean("headers", headers)
        
        logger.info("初始化限流插件，算法: $algorithm, 限制: $limit, 窗口: ${window}ms")
        
        // 创建限流器
        return createLimiter()
    }
    
    override fun handleRequest(context: RoutingContext): Future<Void> {
        if (!enabled) {
            return Future.succeededFuture()
        }
        
        val promise = Promise.promise<Void>()
        
        // 解析限流键
        val key = resolveKey(context.request())
        
        // 检查是否允许通过
        checkRateLimit(key).onComplete { ar ->
            if (ar.succeeded()) {
                val result = ar.result()
                val allowed = result.getBoolean("allowed", false)
                
                if (allowed) {
                    // 允许通过
                    if (headers) {
                        addRateLimitHeaders(context.response(), result)
                    }
                    promise.complete()
                } else {
                    // 超过限制
                    if (headers) {
                        addRateLimitHeaders(context.response(), result)
                    }
                    
                    // 返回错误响应
                    context.response()
                        .setStatusCode(statusCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", errorMessage)
                            .put("status", statusCode)
                            .encode()
                        )
                    
                    // 标记请求已处理
                    promise.complete()
                }
            } else {
                // 检查失败
                logger.error("检查限流失败", ar.cause())
                promise.complete()
            }
        }
        
        return promise.future()
    }
    
    override fun handleResponse(context: RoutingContext): Future<Void> {
        return Future.succeededFuture()
    }
    
    override fun close(): Future<Void> {
        return Future.succeededFuture()
    }
    
    /**
     * 创建限流器
     */
    private fun createLimiter(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 创建限流器配置
        val config = JsonObject()
            .put("key", "$keyPrefix:global")
            .put("type", algorithm)
        
        // 根据算法类型设置参数
        when (algorithm) {
            "token_bucket" -> {
                config
                    .put("capacity", capacity)
                    .put("refillRate", refillRate)
                    .put("refillInterval", refillInterval)
                    .put("initialTokens", capacity)
            }
            "sliding_window" -> {
                config
                    .put("limit", limit)
                    .put("windowSize", window)
                    .put("precision", 10)
            }
            "leaky_bucket" -> {
                config
                    .put("capacity", capacity)
                    .put("leakRate", refillRate)
                    .put("leakInterval", refillInterval)
            }
        }
        
        // 发送创建限流器请求
        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.RATE_LIMIT_CREATE, config)
            .onSuccess {
                promise.complete()
            }
            .onFailure {
                logger.error("创建限流器失败", it)
                promise.fail(it)
            }
        
        return promise.future()
    }
    
    /**
     * 检查限流
     */
    private fun checkRateLimit(key: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 创建检查请求
        val request = JsonObject()
            .put("key", "$keyPrefix:$key")
            .put("type", algorithm)
            .put("tokens", 1)
        
        // 根据算法类型设置参数
        when (algorithm) {
            "token_bucket" -> {
                request
                    .put("capacity", capacity)
                    .put("refillRate", refillRate)
                    .put("refillInterval", refillInterval)
            }
            "sliding_window" -> {
                request
                    .put("limit", limit)
                    .put("windowSize", window)
                    .put("precision", 10)
            }
            "leaky_bucket" -> {
                request
                    .put("capacity", capacity)
                    .put("leakRate", refillRate)
                    .put("leakInterval", refillInterval)
            }
        }
        
        // 发送检查请求
        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.RATE_LIMIT_CHECK, request)
            .onSuccess {
                promise.complete(it.body())
            }
            .onFailure {
                logger.error("检查限流失败", it)
                promise.fail(it)
            }
        
        return promise.future()
    }
    
    /**
     * 解析限流键
     */
    private fun resolveKey(request: HttpServerRequest): String {
        return when (keyResolver) {
            "ip" -> request.remoteAddress().host()
            "path" -> request.path()
            "method" -> request.method().name()
            "method_path" -> "${request.method().name()}:${request.path()}"
            "header" -> request.getHeader("X-API-Key") ?: "anonymous"
            else -> request.remoteAddress().host()
        }
    }
    
    /**
     * 添加限流响应头
     */
    private fun addRateLimitHeaders(response: HttpServerResponse, result: JsonObject) {
        when (algorithm) {
            "token_bucket" -> {
                val tokens = result.getInteger("tokens", 1)
                val remaining = result.getInteger("remaining", 0)
                val capacity = result.getInteger("capacity", this.capacity)
                val reset = result.getLong("reset", 0)
                
                response.putHeader("X-RateLimit-Limit", capacity.toString())
                response.putHeader("X-RateLimit-Remaining", remaining.toString())
                response.putHeader("X-RateLimit-Reset", reset.toString())
            }
            "sliding_window" -> {
                val limit = result.getInteger("limit", this.limit)
                val remaining = result.getInteger("remaining", 0)
                val reset = result.getLong("reset", 0)
                
                response.putHeader("X-RateLimit-Limit", limit.toString())
                response.putHeader("X-RateLimit-Remaining", remaining.toString())
                response.putHeader("X-RateLimit-Reset", reset.toString())
            }
            "leaky_bucket" -> {
                val capacity = result.getInteger("capacity", this.capacity)
                val remaining = result.getInteger("remaining", 0)
                val reset = result.getLong("reset", 0)
                
                response.putHeader("X-RateLimit-Limit", capacity.toString())
                response.putHeader("X-RateLimit-Remaining", remaining.toString())
                response.putHeader("X-RateLimit-Reset", reset.toString())
            }
        }
    }
}
