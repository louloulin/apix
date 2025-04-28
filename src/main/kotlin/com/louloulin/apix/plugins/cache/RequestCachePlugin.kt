package com.louloulin.apix.plugins.cache

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * 请求缓存插件
 *
 * 该插件用于缓存请求响应，支持以下功能：
 * - 支持基于请求路径、方法、头部、参数的缓存键生成
 * - 支持缓存过期时间配置
 * - 支持缓存大小限制
 * - 支持缓存命中率统计
 * - 支持缓存清除
 *
 * 配置参数：
 * - enabled: 是否启用缓存，默认为 true
 * - ttl: 缓存过期时间（秒），默认为 60
 * - maxSize: 缓存最大大小（条目数），默认为 1000
 * - methods: 缓存的请求方法列表，默认为 ["GET"]
 * - keyGenerator: 缓存键生成器配置
 *   - includePath: 是否包含路径，默认为 true
 *   - includeMethod: 是否包含方法，默认为 true
 *   - includeHeaders: 包含的请求头列表
 *   - includeParams: 包含的请求参数列表
 *   - excludeHeaders: 排除的请求头列表
 *   - excludeParams: 排除的请求参数列表
 * - conditions: 缓存条件配置
 *   - statusCodes: 缓存的状态码列表，默认为 [200]
 *   - contentTypes: 缓存的内容类型列表
 * - varyHeaders: 缓存变化的请求头列表
 * - cacheControl: 缓存控制配置
 *   - enabled: 是否启用缓存控制，默认为 true
 *   - maxAge: 最大缓存时间（秒），默认为 60
 *   - sMaxAge: 共享缓存最大时间（秒），默认为 60
 *   - noCache: 是否禁止缓存，默认为 false
 *   - noStore: 是否禁止存储，默认为 false
 *   - mustRevalidate: 是否必须重新验证，默认为 false
 */
class RequestCachePlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(RequestCachePlugin::class.java)
    override val type: String = "requestCache"

    // 缓存存储
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    // 缓存统计
    private val stats = CacheStats()

    // 是否启用缓存
    private val enabled: Boolean

    // 缓存过期时间（秒）
    private val ttl: Long

    // 缓存最大大小（条目数）
    private val maxSize: Int

    // 缓存的请求方法列表
    private val methods: Set<HttpMethod>

    // 缓存键生成器配置
    private val keyGenerator: KeyGenerator

    // 缓存条件配置
    private val conditions: CacheConditions

    // 缓存变化的请求头列表
    private val varyHeaders: Set<String>

    // 缓存控制配置
    private val cacheControl: CacheControl

    // 清理定时器 ID
    private val cleanupTimerId: Long

    init {
        // 解析是否启用缓存
        enabled = config.config.getBoolean("enabled", true)

        // 解析缓存过期时间
        ttl = config.config.getLong("ttl", 60)

        // 解析缓存最大大小
        maxSize = config.config.getInteger("maxSize", 1000)

        // 解析缓存的请求方法列表
        val methodsArray = config.config.getJsonArray("methods", JsonArray().add("GET"))
        methods = methodsArray.map {
            try {
                HttpMethod.valueOf(it.toString().uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid HTTP method: {}", it)
                null
            }
        }.filterNotNull().toSet()

        // 解析缓存键生成器配置
        val keyGeneratorConfig = config.config.getJsonObject("keyGenerator", JsonObject())
        keyGenerator = KeyGenerator(
            includePath = keyGeneratorConfig.getBoolean("includePath", true),
            includeMethod = keyGeneratorConfig.getBoolean("includeMethod", true),
            includeHeaders = keyGeneratorConfig.getJsonArray("includeHeaders", JsonArray()).map { it.toString() }.toSet(),
            includeParams = keyGeneratorConfig.getJsonArray("includeParams", JsonArray()).map { it.toString() }.toSet(),
            excludeHeaders = keyGeneratorConfig.getJsonArray("excludeHeaders", JsonArray()).map { it.toString() }.toSet(),
            excludeParams = keyGeneratorConfig.getJsonArray("excludeParams", JsonArray()).map { it.toString() }.toSet()
        )

        // 解析缓存条件配置
        val conditionsConfig = config.config.getJsonObject("conditions", JsonObject())
        conditions = CacheConditions(
            statusCodes = conditionsConfig.getJsonArray("statusCodes", JsonArray().add(200)).map { it as Int }.toSet(),
            contentTypes = conditionsConfig.getJsonArray("contentTypes", JsonArray()).map { it.toString() }.toSet()
        )

        // 解析缓存变化的请求头列表
        val varyHeadersArray = config.config.getJsonArray("varyHeaders", JsonArray())
        varyHeaders = varyHeadersArray.map { it.toString().lowercase() }.toSet()

        // 解析缓存控制配置
        val cacheControlConfig = config.config.getJsonObject("cacheControl", JsonObject())
        cacheControl = CacheControl(
            enabled = cacheControlConfig.getBoolean("enabled", true),
            maxAge = cacheControlConfig.getInteger("maxAge", 60),
            sMaxAge = cacheControlConfig.getInteger("sMaxAge", 60),
            noCache = cacheControlConfig.getBoolean("noCache", false),
            noStore = cacheControlConfig.getBoolean("noStore", false),
            mustRevalidate = cacheControlConfig.getBoolean("mustRevalidate", false)
        )

        // 启动定时清理任务
        cleanupTimerId = vertx.setPeriodic(ttl * 1000) {
            cleanup()
        }

        logger.info("Initialized request cache plugin: enabled={}, ttl={}, maxSize={}, methods={}",
            enabled, ttl, maxSize, methods)
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            if (!enabled) {
                // 缓存未启用，继续处理请求
                context.next()
                promise.complete()
                return promise.future()
            }

            val request = context.request()
            val method = request.method()

            // 检查请求方法是否支持缓存
            if (method !in methods) {
                // 请求方法不支持缓存，继续处理请求
                context.next()
                promise.complete()
                return promise.future()
            }

            // 检查缓存控制头
            val cacheControlHeader = request.getHeader("Cache-Control")
            if (cacheControlHeader != null) {
                if (cacheControlHeader.contains("no-cache") || cacheControlHeader.contains("no-store")) {
                    // 客户端要求不使用缓存，继续处理请求
                    context.next()
                    promise.complete()
                    return promise.future()
                }
            }

            // 生成缓存键
            val cacheKey = generateCacheKey(context)

            // 尝试从缓存中获取响应
            val cacheEntry = cache[cacheKey]

            if (cacheEntry != null && !cacheEntry.isExpired()) {
                // 缓存命中
                stats.hit()

                // 设置响应
                val response = context.response()

                // 设置缓存控制头
                if (cacheControl.enabled) {
                    setCacheControlHeaders(response)
                }

                // 设置响应头
                cacheEntry.headers.forEach { (name, value) ->
                    response.putHeader(name, value)
                }

                // 设置响应体
                response.end(cacheEntry.body)

                promise.complete()
            } else {
                // 缓存未命中
                stats.miss()

                // 注册响应完成处理器
                context.addEndHandler {
                    // 检查是否应该缓存响应
                    if (shouldCacheResponse(context)) {
                        // 获取响应体
                        val buffer = Buffer.buffer("Cached response")

                        // 缓存响应
                        cacheResponse(context, cacheKey, buffer)
                    }
                }

                // 继续处理请求
                context.next()
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error executing request cache plugin", e)
            // 发生错误，继续处理请求
            context.next()
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 生成缓存键
     */
    private fun generateCacheKey(context: RoutingContext): String {
        val request = context.request()
        val components = mutableListOf<String>()

        // 添加路径
        if (keyGenerator.includePath) {
            components.add(request.path())
        }

        // 添加方法
        if (keyGenerator.includeMethod) {
            components.add(request.method().name())
        }

        // 添加请求头
        if (keyGenerator.includeHeaders.isNotEmpty()) {
            val headers = mutableMapOf<String, String>()
            keyGenerator.includeHeaders.forEach { name ->
                val value = request.getHeader(name)
                if (value != null && name !in keyGenerator.excludeHeaders) {
                    headers[name.lowercase()] = value
                }
            }

            // 按名称排序
            headers.entries.sortedBy { it.key }.forEach { (name, value) ->
                components.add("$name=$value")
            }
        }

        // 添加请求参数
        if (keyGenerator.includeParams.isNotEmpty()) {
            val params = mutableMapOf<String, String>()
            keyGenerator.includeParams.forEach { name ->
                val value = request.getParam(name)
                if (value != null && name !in keyGenerator.excludeParams) {
                    params[name] = value
                }
            }

            // 按名称排序
            params.entries.sortedBy { it.key }.forEach { (name, value) ->
                components.add("$name=$value")
            }
        }

        // 添加 Vary 头
        if (varyHeaders.isNotEmpty()) {
            val varyValues = mutableMapOf<String, String>()
            varyHeaders.forEach { name ->
                val value = request.getHeader(name)
                if (value != null) {
                    varyValues[name] = value
                }
            }

            // 按名称排序
            varyValues.entries.sortedBy { it.key }.forEach { (name, value) ->
                components.add("vary:$name=$value")
            }
        }

        // 计算 MD5 哈希
        val key = components.joinToString("|")
        val md5 = MessageDigest.getInstance("MD5")
        val hash = md5.digest(key.toByteArray())
        return Base64.getEncoder().encodeToString(hash)
    }

    /**
     * 检查是否应该缓存响应
     */
    private fun shouldCacheResponse(context: RoutingContext): Boolean {
        val response = context.response()
        val statusCode = response.statusCode

        // 检查状态码
        if (!conditions.statusCodes.contains(statusCode)) {
            return false
        }

        // 检查内容类型
        if (conditions.contentTypes.isNotEmpty()) {
            val contentType = response.headers().get("Content-Type")
            if (contentType == null || !conditions.contentTypes.any { contentType.startsWith(it) }) {
                return false
            }
        }

        // 检查缓存控制头
        val cacheControlHeader = response.headers().get("Cache-Control")
        if (cacheControlHeader != null) {
            if (cacheControlHeader.contains("no-cache") || cacheControlHeader.contains("no-store") || cacheControlHeader.contains("private")) {
                return false
            }
        }

        return true
    }

    /**
     * 缓存响应
     */
    private fun cacheResponse(context: RoutingContext, cacheKey: String, buffer: Buffer) {
        val response = context.response()

        // 获取响应头
        val headers = mutableMapOf<String, String>()
        response.headers().forEach { header ->
            headers[header.key] = header.value
        }

        // 创建缓存条目
        val cacheEntry = CacheEntry(
            key = cacheKey,
            body = buffer,
            headers = headers,
            createdAt = Instant.now().epochSecond,
            expiresAt = Instant.now().epochSecond + ttl
        )

        // 添加到缓存
        cache[cacheKey] = cacheEntry

        // 检查缓存大小
        if (cache.size > maxSize) {
            // 移除最旧的条目
            val oldestEntry = cache.values.minByOrNull { it.createdAt }
            if (oldestEntry != null) {
                cache.remove(oldestEntry.key)
            }
        }
    }

    /**
     * 设置缓存控制头
     */
    private fun setCacheControlHeaders(response: io.vertx.core.http.HttpServerResponse) {
        val cacheControlParts = mutableListOf<String>()

        if (cacheControl.noCache) {
            cacheControlParts.add("no-cache")
        }

        if (cacheControl.noStore) {
            cacheControlParts.add("no-store")
        }

        if (cacheControl.mustRevalidate) {
            cacheControlParts.add("must-revalidate")
        }

        if (cacheControl.maxAge > 0) {
            cacheControlParts.add("max-age=${cacheControl.maxAge}")
        }

        if (cacheControl.sMaxAge > 0) {
            cacheControlParts.add("s-maxage=${cacheControl.sMaxAge}")
        }

        if (cacheControlParts.isNotEmpty()) {
            response.putHeader("Cache-Control", cacheControlParts.joinToString(", "))
        }
    }

    /**
     * 清理过期的缓存条目
     */
    private fun cleanup() {
        val now = Instant.now().epochSecond
        val expiredKeys = mutableListOf<String>()

        // 查找过期的条目
        cache.forEach { (key, entry) ->
            if (entry.isExpired(now)) {
                expiredKeys.add(key)
            }
        }

        // 移除过期的条目
        expiredKeys.forEach { key ->
            cache.remove(key)
        }

        logger.debug("Cleaned up {} expired cache entries", expiredKeys.size)
    }

    /**
     * 获取缓存统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("size", cache.size)
            .put("maxSize", maxSize)
            .put("hits", stats.hits)
            .put("misses", stats.misses)
            .put("hitRate", stats.hitRate())
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        cache.clear()
        stats.reset()
        logger.info("Cache cleared")
    }

    override fun shutdown() {
        // 取消定时清理任务
        vertx.cancelTimer(cleanupTimerId)

        // 清空缓存
        cache.clear()
    }

    /**
     * 缓存条目
     */
    data class CacheEntry(
        val key: String,
        val body: Buffer,
        val headers: Map<String, String>,
        val createdAt: Long,
        val expiresAt: Long
    ) {
        /**
         * 检查缓存条目是否过期
         */
        fun isExpired(now: Long = Instant.now().epochSecond): Boolean {
            return now >= expiresAt
        }
    }

    /**
     * 缓存键生成器配置
     */
    data class KeyGenerator(
        val includePath: Boolean,
        val includeMethod: Boolean,
        val includeHeaders: Set<String>,
        val includeParams: Set<String>,
        val excludeHeaders: Set<String>,
        val excludeParams: Set<String>
    )

    /**
     * 缓存条件配置
     */
    data class CacheConditions(
        val statusCodes: Set<Int>,
        val contentTypes: Set<String>
    )

    /**
     * 缓存控制配置
     */
    data class CacheControl(
        val enabled: Boolean,
        val maxAge: Int,
        val sMaxAge: Int,
        val noCache: Boolean,
        val noStore: Boolean,
        val mustRevalidate: Boolean
    )

    /**
     * 缓存统计
     */
    class CacheStats {
        var hits: Long = 0
            private set

        var misses: Long = 0
            private set

        /**
         * 记录缓存命中
         */
        fun hit() {
            hits++
        }

        /**
         * 记录缓存未命中
         */
        fun miss() {
            misses++
        }

        /**
         * 计算缓存命中率
         */
        fun hitRate(): Double {
            val total = hits + misses
            return if (total > 0) {
                hits.toDouble() / total
            } else {
                0.0
            }
        }

        /**
         * 重置统计信息
         */
        fun reset() {
            hits = 0
            misses = 0
        }
    }

    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return RequestCachePlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
