package com.louloulin.apix.core.verticle

import com.louloulin.apix.cluster.ClusterConfig
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.AsyncMap
import io.vertx.core.shareddata.LocalMap
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 负责缓存管理的 Verticle
 */
class CacheVerticle : BaseVerticle() {
    // 使用基类的 logger

    // 缓存存储 - 本地模式
    private var localCache: LocalMap<String, String>? = null

    // 缓存存储 - 集群模式
    private var clusterCache: AsyncMap<String, String>? = null

    // 是否使用集群模式
    private var clustered = false

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
        // 检查是否在集群模式
        clustered = vertx.isClustered()

        if (clustered) {
            // 集群模式 - 使用分布式缓存
            logger.info("Initializing distributed cache in clustered mode")
            initializeClusteredCache(startPromise)
        } else {
            // 非集群模式 - 使用本地缓存
            logger.info("Initializing local cache in non-clustered mode")
            localCache = vertx.sharedData().getLocalMap("ai-response-cache")
            logger.info("CacheVerticle started successfully with local cache")
            startPromise.complete()
        }
    }

    /**
     * 初始化集群缓存
     */
    private fun initializeClusteredCache(startPromise: Promise<Void>) {
        vertx.sharedData().getAsyncMap<String, String>("ai-response-cache") { ar ->
            if (ar.succeeded()) {
                clusterCache = ar.result()
                logger.info("CacheVerticle started successfully with distributed cache")
                startPromise.complete()
            } else {
                logger.error("Failed to initialize distributed cache", ar.cause())
                // 如果分布式缓存初始化失败，回退到本地缓存
                logger.warn("Falling back to local cache")
                localCache = vertx.sharedData().getLocalMap("ai-response-cache")
                clustered = false
                startPromise.complete()
            }
        }
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

        if (clustered && clusterCache != null) {
            // 使用分布式缓存
            clusterCache!!.get(cacheKey) { ar ->
                if (ar.succeeded()) {
                    val cachedValue = ar.result()
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
                } else {
                    logger.error("Failed to get from distributed cache", ar.cause())
                    updateCacheStats(modelId, false)
                    sendError(message, 500, "Failed to access cache: ${ar.cause().message}")
                }
            }
        } else {
            // 使用本地缓存
            val cachedValue = localCache?.get(cacheKey)
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

        if (clustered && clusterCache != null) {
            // 使用分布式缓存
            clusterCache!!.put(cacheKey, value) { ar ->
                if (ar.succeeded()) {
                    // 设置 TTL
                    if (ttl > 0) {
                        vertx.setTimer(ttl) { _ ->
                            clusterCache!!.remove(cacheKey) { _ -> }
                        }
                    }
                    sendSuccess(message, true)
                } else {
                    logger.error("Failed to put to distributed cache", ar.cause())
                    sendError(message, 500, "Failed to store in cache: ${ar.cause().message}")
                }
            }
        } else {
            // 使用本地缓存
            localCache?.put(cacheKey, value)

            // 设置 TTL
            if (ttl > 0) {
                vertx.setTimer(ttl) { _ ->
                    localCache?.remove(cacheKey)
                }
            }

            sendSuccess(message, true)
        }
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

        if (clustered && clusterCache != null) {
            // 使用分布式缓存
            clusterCache!!.remove(cacheKey) { ar ->
                if (ar.succeeded()) {
                    sendSuccess(message, ar.result() != null)
                } else {
                    logger.error("Failed to invalidate distributed cache", ar.cause())
                    sendError(message, 500, "Failed to invalidate cache: ${ar.cause().message}")
                }
            }
        } else {
            // 使用本地缓存
            val removed = localCache?.remove(cacheKey) != null
            sendSuccess(message, removed)
        }
    }

    /**
     * 处理清空缓存请求
     */
    private fun handleClearCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val modelId = message.body().getString("modelId")

        if (clustered && clusterCache != null) {
            // 集群模式
            if (modelId != null) {
                // 清空特定模型的缓存
                // 获取所有键值对
                clusterCache!!.entries { entriesAr ->
                    if (entriesAr.succeeded()) {
                        val entries = entriesAr.result()
                        val keysToRemove = mutableListOf<String>()

                        // 找出匹配的键
                        entries.forEach { entry ->
                            if (entry.key.startsWith("$modelId:")) {
                                keysToRemove.add(entry.key)
                            }
                        }

                        // 删除匹配的键
                        var removedCount = 0
                        for (key in keysToRemove) {
                            clusterCache!!.remove(key) { _ -> removedCount++ }
                        }

                        // 重置统计信息
                        cacheStats.remove(modelId)

                        sendSuccess(message, keysToRemove.size)
                    } else {
                        logger.error("Failed to get entries from distributed cache", entriesAr.cause())
                        sendError(message, 500, "Failed to clear cache: ${entriesAr.cause().message}")
                    }
                }
            } else {
                // 清空所有缓存
                clusterCache!!.clear { clearAr ->
                    if (clearAr.succeeded()) {
                        // 重置所有统计信息
                        cacheStats.clear()
                        sendSuccess(message, true)
                    } else {
                        logger.error("Failed to clear distributed cache", clearAr.cause())
                        sendError(message, 500, "Failed to clear cache: ${clearAr.cause().message}")
                    }
                }
            }
        } else {
            // 本地模式
            if (modelId != null) {
                // 清空特定模型的缓存
                val keysToRemove = mutableListOf<String>()

                localCache?.keys?.forEach { key ->
                    if (key.startsWith("$modelId:")) {
                        keysToRemove.add(key)
                    }
                }

                keysToRemove.forEach { key ->
                    localCache?.remove(key)
                }

                // 重置统计信息
                cacheStats.remove(modelId)

                sendSuccess(message, keysToRemove.size)
            } else {
                // 清空所有缓存
                val size = localCache?.size ?: 0
                localCache?.clear()

                // 重置所有统计信息
                cacheStats.clear()

                sendSuccess(message, size)
            }
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
                .put("size", if (clustered && clusterCache != null) -1 else localCache?.size ?: 0)

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
