package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.LocalMap
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 负责缓存管理的 Verticle
 */
class CacheVerticle : BaseVerticle() {
    // 使用基类的 logger

    // 缓存存储
    private lateinit var aiResponseCache: LocalMap<String, String>

    // 缓存统计
    private val cacheStats = ConcurrentHashMap<String, CacheStats>()

    override fun registerEventBusHandlers() {
        // AI 响应缓存相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_GET, this::handleGetCache)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_PUT, this::handlePutCache)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_INVALIDATE, this::handleInvalidateCache)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_CLEAR, this::handleClearCache)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_STATS, this::handleGetCacheStats)
    }

    override fun onStart(startPromise: Promise<Void>) {
        // 初始化缓存
        aiResponseCache = vertx.sharedData().getLocalMap("ai-response-cache")

        logger.info("CacheVerticle started successfully")
        startPromise.complete()
    }

    /**
     * 处理获取缓存请求
     */
    private fun handleGetCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val key = message.body().getString("key")
        val modelId = message.body().getString("modelId", "default")

        if (key == null) {
            sendError(message, 400, "Cache key is required")
            return
        }

        val cacheKey = generateCacheKey(key, modelId)
        val cachedValue = aiResponseCache.get(cacheKey)

        if (cachedValue != null) {
            // 缓存命中
            updateCacheStats(modelId, true)
            sendSuccess(message, JsonObject()
                .put("cached", true)
                .put("value", cachedValue)
            )
        } else {
            // 缓存未命中
            updateCacheStats(modelId, false)
            sendError(message, 404, "Cache miss")
        }
    }

    /**
     * 处理存储缓存请求
     */
    private fun handlePutCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val key = message.body().getString("key")
        val value = message.body().getString("value")
        val modelId = message.body().getString("modelId", "default")
        val ttl = message.body().getLong("ttl", 3600000L) // 默认 1 小时

        if (key == null || value == null) {
            sendError(message, 400, "Both key and value are required")
            return
        }

        val cacheKey = generateCacheKey(key, modelId)

        // 存储缓存
        aiResponseCache.put(cacheKey, value)

        // 设置 TTL
        if (ttl > 0) {
            vertx.setTimer(ttl) { _ ->
                aiResponseCache.remove(cacheKey)
            }
        }

        sendSuccess(message, true)
    }

    /**
     * 处理失效缓存请求
     */
    private fun handleInvalidateCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val key = message.body().getString("key")
        val modelId = message.body().getString("modelId", "default")

        if (key == null) {
            sendError(message, 400, "Cache key is required")
            return
        }

        val cacheKey = generateCacheKey(key, modelId)
        val removed = aiResponseCache.remove(cacheKey) != null

        sendSuccess(message, removed)
    }

    /**
     * 处理清空缓存请求
     */
    private fun handleClearCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val modelId = message.body().getString("modelId")

        if (modelId != null) {
            // 清空特定模型的缓存
            val keysToRemove = mutableListOf<String>()

            aiResponseCache.keys.forEach { key ->
                if (key.startsWith("$modelId:")) {
                    keysToRemove.add(key)
                }
            }

            keysToRemove.forEach { key ->
                aiResponseCache.remove(key)
            }

            // 重置统计信息
            cacheStats.remove(modelId)

            sendSuccess(message, keysToRemove.size)
        } else {
            // 清空所有缓存
            val size = aiResponseCache.size
            aiResponseCache.clear()

            // 重置所有统计信息
            cacheStats.clear()

            sendSuccess(message, size)
        }
    }

    /**
     * 处理获取缓存统计信息请求
     */
    private fun handleGetCacheStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val modelId = message.body().getString("modelId")

        if (modelId != null) {
            // 获取特定模型的缓存统计信息
            val stats = cacheStats[modelId] ?: CacheStats()

            sendSuccess(message, JsonObject()
                .put("modelId", modelId)
                .put("hits", stats.hits)
                .put("misses", stats.misses)
                .put("hitRate", stats.hitRate)
            )
        } else {
            // 获取所有缓存统计信息
            val statsArray = io.vertx.core.json.JsonArray()

            cacheStats.forEach { (modelId, stats) ->
                statsArray.add(JsonObject()
                    .put("modelId", modelId)
                    .put("hits", stats.hits)
                    .put("misses", stats.misses)
                    .put("hitRate", stats.hitRate)
                )
            }

            // 添加总体统计信息
            val totalHits = cacheStats.values.sumOf { it.hits }
            val totalMisses = cacheStats.values.sumOf { it.misses }
            val totalRequests = totalHits + totalMisses
            val totalHitRate = if (totalRequests > 0) totalHits.toDouble() / totalRequests else 0.0

            val totalStats = JsonObject()
                .put("modelId", "total")
                .put("hits", totalHits)
                .put("misses", totalMisses)
                .put("hitRate", totalHitRate)
                .put("size", aiResponseCache.size)

            sendSuccess(message, JsonObject()
                .put("total", totalStats)
                .put("models", statsArray)
            )
        }
    }

    /**
     * 生成缓存键
     */
    private fun generateCacheKey(key: String, modelId: String): String {
        return "$modelId:$key"
    }

    /**
     * 更新缓存统计信息
     */
    private fun updateCacheStats(modelId: String, hit: Boolean) {
        val stats = cacheStats.computeIfAbsent(modelId) { CacheStats() }

        if (hit) {
            stats.hits++
        } else {
            stats.misses++
        }
    }

    /**
     * 缓存统计信息
     */
    private data class CacheStats(
        var hits: Long = 0,
        var misses: Long = 0
    ) {
        val hitRate: Double
            get() {
                val total = hits + misses
                return if (total > 0) hits.toDouble() / total else 0.0
            }
    }
}
