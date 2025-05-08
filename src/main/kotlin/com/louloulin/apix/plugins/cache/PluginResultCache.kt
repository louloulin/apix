package com.louloulin.apix.plugins.cache

import com.louloulin.apix.plugins.Plugin
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 插件执行结果缓存
 * 用于缓存插件执行结果，提高性能
 */
class PluginResultCache(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginResultCache::class.java)

    // 缓存存储
    private val cache = ConcurrentHashMap<String, CacheEntry<Future<Void>>>()

    // 默认缓存过期时间（毫秒）
    private val defaultTtl = 60000L // 1分钟

    /**
     * 获取缓存的结果
     *
     * @param context 路由上下文
     * @param plugin 插件
     * @return 缓存的结果，如果没有缓存则返回null
     */
    fun get(context: RoutingContext, plugin: Plugin): Future<Void>? {
        val cacheKey = generateCacheKey(context, plugin)
        val entry = cache[cacheKey]

        if (entry != null) {
            // 检查是否过期
            if (System.currentTimeMillis() > entry.expiresAt) {
                // 已过期，移除缓存
                cache.remove(cacheKey)
                return null
            }

            logger.debug("Cache hit for plugin: {}", plugin.id)
            return entry.value
        }

        logger.debug("Cache miss for plugin: {}", plugin.id)
        return null
    }

    /**
     * 缓存结果
     *
     * @param context 路由上下文
     * @param plugin 插件
     * @param result 执行结果
     * @param ttl 缓存过期时间（毫秒），默认为1分钟
     */
    fun put(context: RoutingContext, plugin: Plugin, result: Future<Void>, ttl: Long = defaultTtl) {
        // 检查插件是否可缓存
        if (!isCacheable(plugin)) {
            logger.debug("Plugin {} is not cacheable", plugin.id)
            return
        }

        val cacheKey = generateCacheKey(context, plugin)
        val expiresAt = System.currentTimeMillis() + ttl

        cache[cacheKey] = CacheEntry(result, expiresAt)
        logger.debug("Cached result for plugin: {}, expires in {} ms", plugin.id, ttl)

        // 设置定时器，在过期时间到达后清除缓存
        vertx.setTimer(ttl) {
            cache.remove(cacheKey)
            logger.debug("Cache entry expired for plugin: {}", plugin.id)
        }
    }

    /**
     * 清除缓存
     *
     * @param plugin 插件，如果为null则清除所有缓存
     */
    fun clear(plugin: Plugin? = null) {
        if (plugin == null) {
            // 清除所有缓存
            cache.clear()
            logger.debug("Cleared all cache entries")
        } else {
            // 清除特定插件的缓存
            val keysToRemove = cache.keys.filter { it.startsWith("${plugin.id}:") }
            keysToRemove.forEach { cache.remove(it) }
            logger.debug("Cleared cache entries for plugin: {}", plugin.id)
        }
    }

    /**
     * 生成缓存键
     *
     * @param context 路由上下文
     * @param plugin 插件
     * @return 缓存键
     */
    private fun generateCacheKey(context: RoutingContext, plugin: Plugin): String {
        // 基本键：插件ID + 请求路径 + 请求方法
        val baseKey = "${plugin.id}:${context.request().path()}:${context.request().method()}"

        // 获取插件的缓存键生成配置
        val cacheConfig = plugin.config.getJsonObject("cache") ?: JsonObject()
        val includeHeaders = cacheConfig.getJsonArray("includeHeaders")?.map { it.toString() } ?: emptyList()
        val includeParams = cacheConfig.getJsonArray("includeParams")?.map { it.toString() } ?: emptyList()

        // 添加指定的请求头
        val headersPart = if (includeHeaders.isNotEmpty()) {
            includeHeaders.map { header ->
                val value = context.request().getHeader(header) ?: ""
                "$header=$value"
            }.joinToString("|")
        } else {
            ""
        }

        // 添加指定的请求参数
        val paramsPart = if (includeParams.isNotEmpty()) {
            includeParams.map { param ->
                val value = context.request().getParam(param) ?: ""
                "$param=$value"
            }.joinToString("|")
        } else {
            ""
        }

        return if (headersPart.isNotEmpty() || paramsPart.isNotEmpty()) {
            "$baseKey:$headersPart:$paramsPart"
        } else {
            baseKey
        }
    }

    /**
     * 检查插件是否可缓存
     *
     * @param plugin 插件
     * @return 是否可缓存
     */
    private fun isCacheable(plugin: Plugin): Boolean {
        // 从插件配置中获取是否可缓存
        return plugin.config.config.getBoolean("cacheable", false)
    }

    /**
     * 缓存条目
     */
    private data class CacheEntry<T>(
        val value: T,
        val expiresAt: Long
    )

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PluginResultCache? = null

        /**
         * 获取单例实例
         */
        fun getInstance(vertx: Vertx): PluginResultCache {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PluginResultCache(vertx).also { INSTANCE = it }
            }
        }
    }
}
