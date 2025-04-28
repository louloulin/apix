package com.louloulin.apix.plugins.ai

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant

/**
 * 响应缓存插件，用于缓存AI服务的响应以减少重复请求。
 */
class ResponseCachePlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(ResponseCachePlugin::class.java)

    override val type: String = "response-cache"

    // 配置值
    private val ttlSeconds: Int = config.config.getInteger("ttl_seconds", 300) ?: 300
    private val maxSize: Int = config.config.getInteger("max_size", 1000) ?: 1000
    private val methods: List<String> = config.config.getJsonArray("methods")
        ?.map { it.toString().uppercase() } ?: listOf("POST")
    private val statusCodes: List<Int> = config.config.getJsonArray("status_codes")
        ?.map { (it as Number).toInt() } ?: listOf(200)

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val request = context.request()
            val method = request.method().name()

            // 只缓存配置的方法
            if (!methods.contains(method)) {
                promise.complete()
                return promise.future()
            }

            // 计算缓存键
            val cacheKey = calculateCacheKey(context)

            // 检查缓存
            val message = JsonObject()
                .put("key", cacheKey)
                .put("modelId", getModelId(context))

            vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_GET, message) { ar ->
                if (ar.succeeded()) {
                    val response = ar.result().body()

                    if (response.getBoolean("success", false)) {
                        // 缓存命中
                        val result = response.getJsonObject("result")
                        val cachedValue = result.getString("value")

                        logger.debug("Cache hit for key: {}", cacheKey)

                        // 设置缓存头部
                        context.response().putHeader("X-Cache", "HIT")

                        // 返回缓存的响应
                        context.response()
                            .putHeader("Content-Type", "application/json")
                            .end(cachedValue)

                        promise.complete()
                    } else {
                        // 缓存未命中，继续处理请求
                        handleCacheMiss(context, cacheKey, promise)
                    }
                } else {
                    // 缓存服务出错，继续处理请求
                    logger.error("Error getting cache: {}", ar.cause().message)
                    handleCacheMiss(context, cacheKey, promise)
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing response cache plugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 计算请求的缓存键。
     */
    private fun calculateCacheKey(context: RoutingContext): String {
        val request = context.request()
        val method = request.method().name()
        val path = request.path()
        val query = request.query() ?: ""

        // 对于POST请求，包含请求体
        val bodyHash = if (request.method() == HttpMethod.POST) {
            val body = context.body().buffer()
            if (body != null) {
                sha256(body.toString())
            } else {
                ""
            }
        } else {
            ""
        }

        return "$method:$path:$query:$bodyHash"
    }

    /**
     * 处理缓存未命中的情况
     */
    private fun handleCacheMiss(context: RoutingContext, cacheKey: String, promise: Promise<Void>) {
        logger.debug("Cache miss for key: {}", cacheKey)

        // 设置缓存头部
        context.response().putHeader("X-Cache", "MISS")

        // 保存原始的 end 方法
        val originalEnd = context.response().endHandler(null)

        // 替换 end 方法，以便在响应结束时缓存结果
        val bodyBuffer = Buffer.buffer()

        context.response().bodyEndHandler {
            // 检查状态码是否可缓存
            val statusCode = context.response().statusCode
            if (statusCodes.contains(statusCode)) {
                // 缓存响应
                val responseBody = bodyBuffer.toString()

                val message = JsonObject()
                    .put("key", cacheKey)
                    .put("value", responseBody)
                    .put("modelId", getModelId(context))
                    .put("ttl", ttlSeconds * 1000L) // 转换为毫秒

                vertx.eventBus().send(EventBusAddresses.CACHE_PUT, message)

                logger.debug("Cached response for key: {}", cacheKey)
            }
        }

        // 拦截写入操作，以便收集响应体
        // 使用响应的 bodyEndHandler 来收集响应体
        context.response().bodyEndHandler {
            // 在这里已经有了响应体的处理逻辑
        }

        // 继续处理请求
        context.next()
        promise.complete()
    }

    /**
     * 获取模型 ID
     */
    private fun getModelId(context: RoutingContext): String {
        // 从请求体中获取模型 ID
        val body = context.body().asJsonObject()

        if (body != null) {
            val modelId = body.getString("model")
            if (modelId != null) {
                return modelId
            }
        }

        // 从查询参数中获取模型 ID
        val modelId = context.request().getParam("model")
        if (modelId != null) {
            return modelId
        }

        // 使用默认模型 ID
        return "default"
    }

    /**
     * 计算字符串的SHA-256哈希。
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun shutdown() {
        // 无需释放资源
    }
}
