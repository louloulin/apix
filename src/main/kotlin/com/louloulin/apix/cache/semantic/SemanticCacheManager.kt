package com.louloulin.apix.cache.semantic

import com.louloulin.apix.cache.CacheManager
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 语义缓存管理器，基于语义相似度进行缓存查找
 */
class SemanticCacheManager(
    private val vertx: Vertx,
    private val embeddingEngine: EmbeddingEngine,
    private val underlyingCache: CacheManager,
    private val similarityThreshold: Float = 0.8f,
    private val config: JsonObject = JsonObject()
) : CacheManager {
    private val logger = LoggerFactory.getLogger(SemanticCacheManager::class.java)
    
    // 缓存键到嵌入向量的映射
    private val keyEmbeddings = ConcurrentHashMap<String, FloatArray>()
    
    // 缓存统计
    private val semanticHits = AtomicLong(0)
    private val exactHits = AtomicLong(0)
    private val misses = AtomicLong(0)
    
    // 相似度匹配器
    private val similarityMatcher = OptimizedSimilarityMatcher(
        vertx,
        config.getJsonObject("similarityMatcher", JsonObject())
            .put("similarityThreshold", similarityThreshold)
    )
    
    // 部分响应缓存
    private val partialResponseCache = if (config.getBoolean("enablePartialResponseCache", false)) {
        PartialResponseCache(
            vertx,
            embeddingEngine,
            config.getJsonObject("partialResponseCache", JsonObject())
                .put("similarityThreshold", similarityThreshold)
        )
    } else {
        null
    }
    
    // 是否启用向量量化
    private val enableVectorQuantization = config.getBoolean("enableVectorQuantization", false)
    
    // 量化位数
    private val quantizationBits = config.getInteger("quantizationBits", 8)
    
    /**
     * 语义缓存条目，包含原始查询、响应和嵌入向量
     */
    data class SemanticCacheEntry(
        val query: String,
        val response: JsonObject,
        val embedding: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as SemanticCacheEntry
            
            if (query != other.query) return false
            if (response != other.response) return false
            if (!embedding.contentEquals(other.embedding)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = query.hashCode()
            result = 31 * result + response.hashCode()
            result = 31 * result + embedding.contentHashCode()
            return result
        }
    }
    
    /**
     * 生成缓存键
     */
    private fun generateCacheKey(query: String): String {
        return "semantic:${query.hashCode()}"
    }
    
    /**
     * 应用向量量化，减少内存占用
     */
    private fun quantizeVector(vector: FloatArray): FloatArray {
        // 如果量化位数为0，不进行量化
        if (quantizationBits <= 0) {
            return vector
        }
        
        // 找到向量的最大和最小值
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        
        for (value in vector) {
            if (value < minVal) minVal = value
            if (value > maxVal) maxVal = value
        }
        
        // 如果向量是常量，不进行量化
        if (maxVal == minVal) {
            return vector
        }
        
        val range = maxVal - minVal
        val quantizedVector = FloatArray(vector.size)
        
        // 量化到指定位数
        val levels = (1 shl quantizationBits) - 1
        
        for (i in vector.indices) {
            // 归一化到 0-1 范围
            val normalized = (vector[i] - minVal) / range
            
            // 量化到指定级别
            val quantized = (normalized * levels).toInt()
            
            // 反量化回浮点数
            quantizedVector[i] = (quantized.toFloat() / levels) * range + minVal
        }
        
        return quantizedVector
    }
    
    /**
     * 查找语义相似的缓存条目
     */
    fun findSimilar(query: String): Future<Pair<String, JsonObject>?> {
        val promise = Promise.promise<Pair<String, JsonObject>?>()
        
        // 计算查询的嵌入向量
        embeddingEngine.embed(query)
            .onSuccess { queryEmbedding ->
                // 使用优化的相似度匹配器查找最相似的缓存条目
                similarityMatcher.findMostSimilar(queryEmbedding, 1)
                    .onSuccess { results ->
                        if (results.isEmpty()) {
                            promise.complete(null)
                            return@onSuccess
                        }
                        
                        val (bestKey, bestSimilarity) = results[0]
                        
                        // 从底层缓存中获取缓存条目
                        underlyingCache.get(bestKey)
                            .onSuccess { cachedValue ->
                                if (cachedValue != null) {
                                    val originalQuery = cachedValue.getString("query")
                                    val response = cachedValue.getJsonObject("response")
                                    
                                    if (originalQuery != null && response != null) {
                                        semanticHits.incrementAndGet()
                                        promise.complete(Pair(originalQuery, response))
                                    } else {
                                        logger.warn("Invalid cache entry format for key: $bestKey")
                                        misses.incrementAndGet()
                                        promise.complete(null)
                                    }
                                } else {
                                    // 缓存条目已过期或被删除
                                    keyEmbeddings.remove(bestKey)
                                    similarityMatcher.removeVector(bestKey)
                                    misses.incrementAndGet()
                                    promise.complete(null)
                                }
                            }
                            .onFailure { err ->
                                logger.error("Error getting cache entry for key: $bestKey", err)
                                misses.incrementAndGet()
                                promise.complete(null)
                            }
                    }
                    .onFailure { err ->
                        logger.error("Error finding similar cache entry", err)
                        misses.incrementAndGet()
                        promise.complete(null)
                    }
            }
            .onFailure { err ->
                logger.error("Error computing embedding for query", err)
                misses.incrementAndGet()
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun get(key: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        
        // 首先尝试精确匹配
        underlyingCache.get(key)
            .onSuccess { cachedValue ->
                if (cachedValue != null) {
                    exactHits.incrementAndGet()
                    promise.complete(cachedValue)
                } else {
                    // 如果 key 是查询文本，尝试语义匹配
                    if (key.startsWith("query:")) {
                        val query = key.substring(6)
                        findSimilar(query)
                            .onSuccess { result ->
                                if (result != null) {
                                    val (originalQuery, response) = result
                                    promise.complete(response)
                                } else {
                                    misses.incrementAndGet()
                                    promise.complete(null)
                                }
                            }
                            .onFailure { err ->
                                logger.error("Error finding similar cache entry", err)
                                misses.incrementAndGet()
                                promise.fail(err)
                            }
                    } else {
                        misses.incrementAndGet()
                        promise.complete(null)
                    }
                }
            }
            .onFailure { err ->
                logger.error("Error getting cache entry for key: $key", err)
                misses.incrementAndGet()
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 根据查询文本获取缓存
     */
    fun getByQuery(query: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        
        // 生成缓存键
        val cacheKey = generateCacheKey(query)
        
        // 首先尝试精确匹配
        underlyingCache.get(cacheKey)
            .onSuccess { cachedValue ->
                if (cachedValue != null) {
                    val response = cachedValue.getJsonObject("response")
                    
                    if (response != null) {
                        exactHits.incrementAndGet()
                        promise.complete(response)
                    } else {
                        logger.warn("Invalid cache entry format for key: $cacheKey")
                        misses.incrementAndGet()
                        promise.complete(null)
                    }
                } else {
                    // 尝试语义匹配
                    findSimilar(query)
                        .onSuccess { result ->
                            if (result != null) {
                                val (originalQuery, response) = result
                                promise.complete(response)
                            } else {
                                misses.incrementAndGet()
                                promise.complete(null)
                            }
                        }
                        .onFailure { err ->
                            logger.error("Error finding similar cache entry", err)
                            misses.incrementAndGet()
                            promise.fail(err)
                        }
                }
            }
            .onFailure { err ->
                logger.error("Error getting cache entry for query: $query", err)
                misses.incrementAndGet()
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun set(key: String, value: JsonObject, ttlSeconds: Long): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 如果 key 是查询文本，进行语义缓存
        if (key.startsWith("query:")) {
            val query = key.substring(6)
            setByQuery(query, value, ttlSeconds)
                .onSuccess {
                    promise.complete()
                }
                .onFailure { err ->
                    promise.fail(err)
                }
        } else {
            // 否则，直接使用底层缓存
            underlyingCache.set(key, value, ttlSeconds)
                .onSuccess {
                    promise.complete()
                }
                .onFailure { err ->
                    promise.fail(err)
                }
        }
        
        return promise.future()
    }
    
    /**
     * 根据查询文本设置缓存
     */
    fun setByQuery(query: String, response: JsonObject, ttlSeconds: Long): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 计算查询的嵌入向量
        embeddingEngine.embed(query)
            .onSuccess { embedding ->
                // 如果启用了向量量化，应用量化
                val finalEmbedding = if (enableVectorQuantization) {
                    quantizeVector(embedding)
                } else {
                    embedding
                }
                
                // 生成缓存键
                val cacheKey = generateCacheKey(query)
                
                // 创建缓存条目
                val cacheEntry = JsonObject()
                    .put("query", query)
                    .put("response", response)
                    .put("timestamp", System.currentTimeMillis())
                
                // 存储到底层缓存
                underlyingCache.set(cacheKey, cacheEntry, ttlSeconds)
                    .onSuccess {
                        // 存储嵌入向量
                        keyEmbeddings[cacheKey] = finalEmbedding
                        
                        // 添加到相似度匹配器
                        similarityMatcher.addVector(cacheKey, finalEmbedding)
                            .onSuccess {
                                // 如果启用了部分响应缓存，缓存响应片段
                                if (partialResponseCache != null && response.containsKey("content")) {
                                    val content = response.getString("content", "")
                                    if (content.isNotEmpty()) {
                                        partialResponseCache.cacheResponse(query, content, JsonObject().put("source", "semantic_cache"))
                                            .onSuccess {
                                                promise.complete()
                                            }
                                            .onFailure { err ->
                                                logger.warn("Error caching response chunks", err)
                                                promise.complete() // 即使部分响应缓存失败，也认为缓存成功
                                            }
                                    } else {
                                        promise.complete()
                                    }
                                } else {
                                    promise.complete()
                                }
                            }
                            .onFailure { err ->
                                logger.error("Error adding vector to similarity matcher", err)
                                promise.fail(err)
                            }
                    }
                    .onFailure { err ->
                        logger.error("Error setting cache entry for query: $query", err)
                        promise.fail(err)
                    }
            }
            .onFailure { err ->
                logger.error("Error computing embedding for query: $query", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun remove(key: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 从底层缓存中删除
        underlyingCache.remove(key)
            .onSuccess {
                // 从嵌入向量映射中删除
                keyEmbeddings.remove(key)
                
                // 从相似度匹配器中删除
                similarityMatcher.removeVector(key)
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.warn("Error removing vector from similarity matcher", err)
                        promise.complete() // 即使删除向量失败，也认为删除成功
                    }
            }
            .onFailure { err ->
                logger.error("Error removing cache entry for key: $key", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun clear(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 清空底层缓存
        underlyingCache.clear()
            .onSuccess {
                // 清空嵌入向量映射
                keyEmbeddings.clear()
                
                // 清空相似度匹配器
                similarityMatcher.clear()
                
                // 清空部分响应缓存
                if (partialResponseCache != null) {
                    partialResponseCache.clear()
                        .onSuccess {
                            promise.complete()
                        }
                        .onFailure { err ->
                            logger.warn("Error clearing partial response cache", err)
                            promise.complete() // 即使清空部分响应缓存失败，也认为清空成功
                        }
                } else {
                    promise.complete()
                }
            }
            .onFailure { err ->
                logger.error("Error clearing cache", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun getStats(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 获取底层缓存的统计信息
        underlyingCache.getStats()
            .onSuccess { underlyingStats ->
                val stats = JsonObject()
                    .put("semanticHits", semanticHits.get())
                    .put("exactHits", exactHits.get())
                    .put("misses", misses.get())
                    .put("totalRequests", semanticHits.get() + exactHits.get() + misses.get())
                    .put("hitRatio", if (semanticHits.get() + exactHits.get() + misses.get() > 0) 
                        (semanticHits.get() + exactHits.get()).toFloat() / (semanticHits.get() + exactHits.get() + misses.get()) else 0f)
                    .put("embeddingCount", keyEmbeddings.size)
                    .put("underlyingCache", underlyingStats)
                    .put("similarityMatcher", similarityMatcher.getStats())
                
                // 添加部分响应缓存的统计信息
                if (partialResponseCache != null) {
                    stats.put("partialResponseCache", partialResponseCache.getStats())
                }
                
                promise.complete(stats)
            }
            .onFailure { err ->
                logger.error("Error getting cache stats", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 关闭部分响应缓存
        val partialCacheFuture = if (partialResponseCache != null) {
            partialResponseCache.clear()
        } else {
            Future.succeededFuture()
        }
        
        partialCacheFuture
            .compose {
                // 关闭底层缓存
                underlyingCache.close()
            }
            .compose {
                // 关闭嵌入式引擎
                embeddingEngine.close()
            }
            .onSuccess {
                // 清空嵌入向量映射
                keyEmbeddings.clear()
                similarityMatcher.clear()
                promise.complete()
            }
            .onFailure { err ->
                logger.error("Error closing cache", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
}
