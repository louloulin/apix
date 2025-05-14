package com.louloulin.apix.cache.semantic

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 部分响应缓存，支持响应片段的重用
 */
class PartialResponseCache(
    private val vertx: Vertx,
    private val embeddingEngine: EmbeddingEngine,
    private val config: JsonObject = JsonObject()
) {
    private val logger = LoggerFactory.getLogger(PartialResponseCache::class.java)
    
    // 相似度阈值
    private val similarityThreshold = config.getFloat("similarityThreshold", 0.8f)
    
    // 最小片段长度
    private val minChunkLength = config.getInteger("minChunkLength", 50)
    
    // 最大片段长度
    private val maxChunkLength = config.getInteger("maxChunkLength", 500)
    
    // 片段重叠长度
    private val chunkOverlap = config.getInteger("chunkOverlap", 20)
    
    // 缓存大小限制
    private val maxCacheSize = config.getInteger("maxCacheSize", 10000)
    
    // 片段缓存
    private val chunkCache = ConcurrentHashMap<String, ChunkEntry>()
    
    // 相似度匹配器
    private val similarityMatcher = OptimizedSimilarityMatcher(
        vertx,
        JsonObject()
            .put("similarityThreshold", similarityThreshold)
            .put("indexType", config.getString("indexType", "brute_force"))
    )
    
    // 缓存统计
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val partialHits = AtomicLong(0)
    
    /**
     * 缓存片段条目
     */
    data class ChunkEntry(
        val query: String,
        val chunk: String,
        val embedding: FloatArray,
        val metadata: JsonObject = JsonObject(),
        val timestamp: Long = System.currentTimeMillis()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ChunkEntry
            
            if (query != other.query) return false
            if (chunk != other.chunk) return false
            if (!embedding.contentEquals(other.embedding)) return false
            if (metadata != other.metadata) return false
            if (timestamp != other.timestamp) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = query.hashCode()
            result = 31 * result + chunk.hashCode()
            result = 31 * result + embedding.contentHashCode()
            result = 31 * result + metadata.hashCode()
            result = 31 * result + timestamp.hashCode()
            return result
        }
    }
    
    /**
     * 缓存完整响应
     */
    fun cacheResponse(query: String, response: String, metadata: JsonObject = JsonObject()): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 将响应分割成片段
        val chunks = splitIntoChunks(response)
        
        // 为每个片段计算嵌入向量并缓存
        val futures = mutableListOf<Future<Void>>()
        
        for (chunk in chunks) {
            val chunkFuture = cacheChunk(query, chunk, metadata)
            futures.add(chunkFuture)
        }
        
        // 等待所有片段缓存完成
        Future.all(futures)
            .onSuccess {
                promise.complete()
            }
            .onFailure { err ->
                logger.error("Error caching response chunks", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 缓存单个片段
     */
    private fun cacheChunk(query: String, chunk: String, metadata: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 计算片段的嵌入向量
        embeddingEngine.embed(chunk)
            .onSuccess { embedding ->
                // 生成缓存键
                val cacheKey = generateCacheKey(chunk)
                
                // 创建片段条目
                val chunkEntry = ChunkEntry(query, chunk, embedding, metadata)
                
                // 存储片段
                chunkCache[cacheKey] = chunkEntry
                
                // 添加到相似度匹配器
                similarityMatcher.addVector(cacheKey, embedding)
                    .onSuccess {
                        // 如果缓存超过大小限制，移除最旧的条目
                        if (chunkCache.size > maxCacheSize) {
                            pruneCache()
                        }
                        
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("Error adding vector to similarity matcher", err)
                        promise.fail(err)
                    }
            }
            .onFailure { err ->
                logger.error("Error computing embedding for chunk", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 查找与查询相关的响应片段
     */
    fun findRelevantChunks(query: String, maxResults: Int = 5): Future<List<ChunkEntry>> {
        val promise = Promise.promise<List<ChunkEntry>>()
        
        // 计算查询的嵌入向量
        embeddingEngine.embed(query)
            .onSuccess { queryEmbedding ->
                // 使用相似度匹配器查找最相似的片段
                similarityMatcher.findMostSimilar(queryEmbedding, maxResults)
                    .onSuccess { results ->
                        if (results.isEmpty()) {
                            misses.incrementAndGet()
                            promise.complete(emptyList())
                        } else {
                            // 获取相应的片段条目
                            val chunks = results.mapNotNull { (key, similarity) ->
                                chunkCache[key]
                            }
                            
                            if (chunks.isNotEmpty()) {
                                if (chunks.size == maxResults) {
                                    hits.incrementAndGet()
                                } else {
                                    partialHits.incrementAndGet()
                                }
                            } else {
                                misses.incrementAndGet()
                            }
                            
                            promise.complete(chunks)
                        }
                    }
                    .onFailure { err ->
                        logger.error("Error finding similar chunks", err)
                        misses.incrementAndGet()
                        promise.fail(err)
                    }
            }
            .onFailure { err ->
                logger.error("Error computing embedding for query", err)
                misses.incrementAndGet()
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 组合响应片段生成完整响应
     */
    fun composeResponse(chunks: List<ChunkEntry>, query: String): Future<String> {
        val promise = Promise.promise<String>()
        
        if (chunks.isEmpty()) {
            promise.complete("")
            return promise.future()
        }
        
        // 简单地将片段连接起来
        // 注意：实际应用中可能需要更复杂的组合逻辑
        val composedResponse = chunks.joinToString("\n\n") { it.chunk }
        
        promise.complete(composedResponse)
        return promise.future()
    }
    
    /**
     * 生成缓存键
     */
    private fun generateCacheKey(chunk: String): String {
        return "chunk:${chunk.hashCode()}"
    }
    
    /**
     * 将文本分割成片段
     */
    private fun splitIntoChunks(text: String): List<String> {
        val chunks = mutableListOf<String>()
        
        // 如果文本长度小于最小片段长度，直接返回整个文本
        if (text.length <= minChunkLength) {
            chunks.add(text)
            return chunks
        }
        
        // 按段落分割
        val paragraphs = text.split("\n\n")
        
        // 当前片段
        val currentChunk = StringBuilder()
        
        for (paragraph in paragraphs) {
            // 如果当前片段加上新段落超过最大长度，保存当前片段并开始新片段
            if (currentChunk.length + paragraph.length > maxChunkLength) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    
                    // 保留一部分重叠内容
                    val lastPart = currentChunk.toString()
                        .split(" ")
                        .takeLast(chunkOverlap.coerceAtMost(currentChunk.length))
                        .joinToString(" ")
                    
                    currentChunk.clear()
                    currentChunk.append(lastPart)
                    currentChunk.append(" ")
                }
            }
            
            // 添加段落到当前片段
            currentChunk.append(paragraph)
            currentChunk.append("\n\n")
        }
        
        // 添加最后一个片段
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString().trim())
        }
        
        return chunks
    }
    
    /**
     * 清理缓存，移除最旧的条目
     */
    private fun pruneCache() {
        // 按时间戳排序，移除最旧的条目
        val entriesToRemove = chunkCache.entries
            .sortedBy { it.value.timestamp }
            .take(chunkCache.size - maxCacheSize)
        
        for ((key, _) in entriesToRemove) {
            chunkCache.remove(key)
            similarityMatcher.removeVector(key)
        }
    }
    
    /**
     * 清空缓存
     */
    fun clear(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        vertx.executeBlocking<Void>({ blockingPromise ->
            try {
                chunkCache.clear()
                similarityMatcher.clear()
                blockingPromise.complete()
            } catch (e: Exception) {
                logger.error("Error clearing cache", e)
                blockingPromise.fail(e)
            }
        }, false)
            .onSuccess {
                promise.complete()
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("chunkCount", chunkCache.size)
            .put("hits", hits.get())
            .put("partialHits", partialHits.get())
            .put("misses", misses.get())
            .put("hitRatio", if (hits.get() + partialHits.get() + misses.get() > 0) 
                (hits.get() + partialHits.get()).toFloat() / (hits.get() + partialHits.get() + misses.get()) else 0f)
            .put("similarityMatcherStats", similarityMatcher.getStats())
    }
}
