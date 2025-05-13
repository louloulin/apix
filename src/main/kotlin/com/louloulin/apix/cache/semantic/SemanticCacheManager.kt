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
    private val similarityThreshold: Float = 0.8f
) : CacheManager {
    private val logger = LoggerFactory.getLogger(SemanticCacheManager::class.java)
    
    // 缓存键到嵌入向量的映射
    private val keyEmbeddings = ConcurrentHashMap<String, FloatArray>()
    
    // 缓存统计
    private val semanticHits = AtomicLong(0)
    private val exactHits = AtomicLong(0)
    private val misses = AtomicLong(0)
    
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
     * 查找语义相似的缓存条目
     */
    fun findSimilar(query: String): Future<Pair<String, JsonObject>?> {
        val promise = Promise.promise<Pair<String, JsonObject>?>()
        
        // 计算查询的嵌入向量
        embeddingEngine.embed(query)
            .onSuccess { queryEmbedding ->
                // 查找最相似的缓存条目
                var bestKey: String? = null
                var bestSimilarity = 0f
                
                for ((key, embedding) in keyEmbeddings) {
                    val similarity = embeddingEngine.similarity(queryEmbedding, embedding)
                    
                    if (similarity > similarityThreshold && similarity > bestSimilarity) {
                        bestSimilarity = similarity
                        bestKey = key
                    }
                }
                
                if (bestKey != null) {
                    // 找到了相似的缓存条目
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
                                misses.incrementAndGet()
                                promise.complete(null)
                            }
                        }
                        .onFailure { err ->
                            logger.error("Error getting cache entry for key: $bestKey", err)
                            misses.incrementAndGet()
                            promise.complete(null)
                        }
                } else {
                    // 没有找到相似的缓存条目
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
                        keyEmbeddings[cacheKey] = embedding
                        promise.complete()
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
                promise.complete()
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
                promise.complete()
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
                // 添加语义缓存的统计信息
                val stats = JsonObject()
                    .put("underlying", underlyingStats)
                    .put("semanticHits", semanticHits.get())
                    .put("exactHits", exactHits.get())
                    .put("misses", misses.get())
                    .put("totalHits", semanticHits.get() + exactHits.get())
                    .put("hitRatio", if (semanticHits.get() + exactHits.get() + misses.get() > 0) {
                        (semanticHits.get() + exactHits.get()).toDouble() / (semanticHits.get() + exactHits.get() + misses.get())
                    } else {
                        0.0
                    })
                    .put("semanticHitRatio", if (semanticHits.get() + exactHits.get() > 0) {
                        semanticHits.get().toDouble() / (semanticHits.get() + exactHits.get())
                    } else {
                        0.0
                    })
                    .put("embeddingCount", keyEmbeddings.size)
                    .put("similarityThreshold", similarityThreshold)
                
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
        
        // 关闭底层缓存
        underlyingCache.close()
            .compose {
                // 关闭嵌入式引擎
                embeddingEngine.close()
            }
            .onSuccess {
                // 清空嵌入向量映射
                keyEmbeddings.clear()
                promise.complete()
            }
            .onFailure { err ->
                logger.error("Error closing cache", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
}
