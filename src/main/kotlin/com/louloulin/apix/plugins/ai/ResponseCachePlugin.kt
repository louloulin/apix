package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * 响应缓存插件，用于缓存AI服务的响应以减少重复请求。
 */
class ResponseCachePlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(ResponseCachePlugin::class.java)
    
    override val type: String = "response-cache"
    
    // 配置值
    private val ttlSeconds: Int = config.getInteger("ttl_seconds", 300) ?: 300
    private val maxSize: Int = config.getInteger("max_size", 1000) ?: 1000
    private val methods: List<String> = config.getJsonArray("methods")
        ?.map { it.toString().uppercase() } ?: listOf("POST")
    private val statusCodes: List<Int> = config.getJsonArray("status_codes")
        ?.map { (it as Number).toInt() } ?: listOf(200)
    
    // 缓存存储
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    
    /**
     * 缓存条目。
     */
    data class CacheEntry(
        val statusCode: Int,
        val headers: Map<String, String>,
        val body: Buffer,
        val expiresAt: Long
    ) {
        /**
         * 检查缓存条目是否已过期。
         */
        fun isExpired(): Boolean {
            return Instant.now().epochSecond > expiresAt
        }
    }
    
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
            val cachedResponse = cache[cacheKey]
            if (cachedResponse != null && !cachedResponse.isExpired()) {
                // 使用缓存的响应
                logger.debug("Cache hit for key: {}", cacheKey)
                
                val response = context.response()
                
                // 设置状态码
                response.setStatusCode(cachedResponse.statusCode)
                
                // 设置头部
                cachedResponse.headers.forEach { (name, value) ->
                    response.putHeader(name, value)
                }
                
                // 添加缓存头部
                response.putHeader("X-Cache", "HIT")
                
                // 发送缓存的响应体
                response.end(cachedResponse.body)
                
                promise.complete()
            } else {
                // 缓存未命中，继续处理请求
                logger.debug("Cache miss for key: {}", cacheKey)
                
                // 添加响应处理器来缓存响应
                context.addBodyEndHandler {
                    val response = context.response()
                    val statusCode = response.statusCode
                    
                    // 只缓存配置的状态码
                    if (statusCodes.contains(statusCode)) {
                        // 获取响应头
                        val headers = mutableMapOf<String, String>()
                        response.headers().forEach { header ->
                            headers[header.key] = header.value
                        }
                        
                        // 获取响应体
                        val body = context.body().buffer()
                        if (body != null) {
                            // 创建缓存条目
                            val expiresAt = Instant.now().epochSecond + ttlSeconds
                            val entry = CacheEntry(statusCode, headers, body, expiresAt)
                            
                            // 添加到缓存
                            cache[cacheKey] = entry
                            
                            // 如果缓存大小超过限制，移除最旧的条目
                            if (cache.size > maxSize) {
                                removeOldestEntry()
                            }
                            
                            logger.debug("Cached response for key: {}", cacheKey)
                        }
                    }
                }
                
                // 添加缓存头部
                context.response().putHeader("X-Cache", "MISS")
                
                promise.complete()
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
     * 计算字符串的SHA-256哈希。
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 移除最旧的缓存条目。
     */
    private fun removeOldestEntry() {
        val now = Instant.now().epochSecond
        
        // 首先尝试移除已过期的条目
        val expiredKeys = cache.entries
            .filter { it.value.expiresAt < now }
            .map { it.key }
        
        if (expiredKeys.isNotEmpty()) {
            expiredKeys.forEach { cache.remove(it) }
            return
        }
        
        // 如果没有过期的条目，移除最旧的条目
        val oldestEntry = cache.entries
            .minByOrNull { it.value.expiresAt }
        
        if (oldestEntry != null) {
            cache.remove(oldestEntry.key)
        }
    }
    
    /**
     * 清除缓存。
     */
    fun clearCache() {
        cache.clear()
        logger.info("Cache cleared")
    }
    
    override fun shutdown() {
        // 清除缓存
        cache.clear()
    }
}
