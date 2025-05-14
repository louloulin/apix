package com.louloulin.apix.cache.semantic

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * 优化的嵌入式引擎实现，包含向量量化、嵌入缓存和批处理优化
 */
class OptimizedEmbeddingEngine(
    private val vertx: Vertx,
    private val config: JsonObject = JsonObject()
) : EmbeddingEngine {
    private val logger = LoggerFactory.getLogger(OptimizedEmbeddingEngine::class.java)

    // 词汇表大小
    private val vocabularySize = config.getInteger("vocabularySize", 10000)
    
    // 量化位数 (8位或16位)
    private val quantizationBits = config.getInteger("quantizationBits", 8)
    
    // 是否启用嵌入缓存
    private val enableCache = config.getBoolean("enableCache", true)
    
    // 嵌入缓存大小
    private val cacheSize = config.getInteger("cacheSize", 10000)
    
    // 嵌入缓存
    private val embeddingCache = ConcurrentHashMap<String, FloatArray>()
    
    // 缓存命中计数
    private var cacheHits = 0
    private var cacheMisses = 0
    
    // 底层嵌入引擎（可以是简单的或外部的）
    private val baseEmbeddingEngine: EmbeddingEngine
    
    init {
        // 初始化底层嵌入引擎
        val engineType = config.getString("baseEngineType", "simple")
        baseEmbeddingEngine = when (engineType) {
            "simple" -> SimpleEmbeddingEngine(vertx)
            else -> SimpleEmbeddingEngine(vertx)
        }
        
        logger.info("Initialized OptimizedEmbeddingEngine with vocabularySize=$vocabularySize, " +
                   "quantizationBits=$quantizationBits, enableCache=$enableCache, cacheSize=$cacheSize")
    }

    override fun embed(text: String): Future<FloatArray> {
        val promise = Promise.promise<FloatArray>()
        
        // 如果启用了缓存，先检查缓存
        if (enableCache) {
            val cachedEmbedding = embeddingCache[text]
            if (cachedEmbedding != null) {
                cacheHits++
                promise.complete(cachedEmbedding)
                return promise.future()
            }
            cacheMisses++
        }
        
        // 使用底层引擎计算嵌入向量
        baseEmbeddingEngine.embed(text)
            .onSuccess { embedding ->
                // 应用向量量化
                val quantizedEmbedding = quantizeVector(embedding)
                
                // 如果启用了缓存，将结果存入缓存
                if (enableCache) {
                    // 如果缓存已满，移除一个随机条目
                    if (embeddingCache.size >= cacheSize) {
                        val keyToRemove = embeddingCache.keys.firstOrNull()
                        if (keyToRemove != null) {
                            embeddingCache.remove(keyToRemove)
                        }
                    }
                    
                    embeddingCache[text] = quantizedEmbedding
                }
                
                promise.complete(quantizedEmbedding)
            }
            .onFailure { err ->
                logger.error("Error computing embedding for text", err)
                promise.fail(err)
            }
        
        return promise.future()
    }

    override fun embedBatch(texts: List<String>): Future<List<FloatArray>> {
        val promise = Promise.promise<List<FloatArray>>()
        
        // 将文本分为已缓存和未缓存两组
        val cachedResults = mutableMapOf<Int, FloatArray>()
        val uncachedTexts = mutableListOf<String>()
        val uncachedIndices = mutableListOf<Int>()
        
        if (enableCache) {
            texts.forEachIndexed { index, text ->
                val cachedEmbedding = embeddingCache[text]
                if (cachedEmbedding != null) {
                    cacheHits++
                    cachedResults[index] = cachedEmbedding
                } else {
                    cacheMisses++
                    uncachedTexts.add(text)
                    uncachedIndices.add(index)
                }
            }
        } else {
            // 如果缓存未启用，所有文本都需要计算
            uncachedTexts.addAll(texts)
            uncachedIndices.addAll(texts.indices)
        }
        
        // 如果所有文本都已缓存，直接返回结果
        if (uncachedTexts.isEmpty()) {
            val results = Array<FloatArray?>(texts.size) { null }
            cachedResults.forEach { (index, embedding) ->
                results[index] = embedding
            }
            promise.complete(results.filterNotNull())
            return promise.future()
        }
        
        // 使用底层引擎计算未缓存的嵌入向量
        baseEmbeddingEngine.embedBatch(uncachedTexts)
            .onSuccess { embeddings ->
                // 应用向量量化并更新缓存
                val quantizedEmbeddings = embeddings.map { quantizeVector(it) }
                
                // 如果启用了缓存，将结果存入缓存
                if (enableCache) {
                    uncachedTexts.forEachIndexed { i, text ->
                        // 如果缓存已满，移除一个随机条目
                        if (embeddingCache.size >= cacheSize) {
                            val keyToRemove = embeddingCache.keys.firstOrNull()
                            if (keyToRemove != null) {
                                embeddingCache.remove(keyToRemove)
                            }
                        }
                        
                        embeddingCache[text] = quantizedEmbeddings[i]
                    }
                }
                
                // 合并缓存结果和新计算的结果
                val results = Array<FloatArray?>(texts.size) { null }
                cachedResults.forEach { (index, embedding) ->
                    results[index] = embedding
                }
                
                uncachedIndices.forEachIndexed { i, originalIndex ->
                    results[originalIndex] = quantizedEmbeddings[i]
                }
                
                promise.complete(results.filterNotNull())
            }
            .onFailure { err ->
                logger.error("Error computing embeddings for texts", err)
                promise.fail(err)
            }
        
        return promise.future()
    }

    override fun similarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        // 使用优化的相似度计算
        return optimizedCosineSimilarity(embedding1, embedding2)
    }

    override fun textSimilarity(text1: String, text2: String): Future<Float> {
        val promise = Promise.promise<Float>()
        
        // 计算两个文本的嵌入向量
        val embedding1Future = embed(text1)
        val embedding2Future = embed(text2)
        
        // 等待两个嵌入向量都计算完成
        Future.all(embedding1Future, embedding2Future)
            .onSuccess {
                val embedding1 = embedding1Future.result()
                val embedding2 = embedding2Future.result()
                
                // 计算相似度
                val sim = similarity(embedding1, embedding2)
                promise.complete(sim)
            }
            .onFailure { err ->
                logger.error("Error computing text similarity", err)
                promise.fail(err)
            }
        
        return promise.future()
    }

    override fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 清空缓存
        embeddingCache.clear()
        
        // 关闭底层引擎
        baseEmbeddingEngine.close()
            .onSuccess {
                logger.info("OptimizedEmbeddingEngine closed. Cache stats: hits=$cacheHits, misses=$cacheMisses")
                promise.complete()
            }
            .onFailure { err ->
                logger.error("Error closing base embedding engine", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): JsonObject {
        return JsonObject()
            .put("cacheEnabled", enableCache)
            .put("cacheSize", cacheSize)
            .put("currentCacheEntries", embeddingCache.size)
            .put("cacheHits", cacheHits)
            .put("cacheMisses", cacheMisses)
            .put("hitRatio", if (cacheHits + cacheMisses > 0) cacheHits.toFloat() / (cacheHits + cacheMisses) else 0f)
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
     * 优化的余弦相似度计算
     */
    private fun optimizedCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vectors must have the same dimension")
        }
        
        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f
        
        // 使用循环展开和SIMD友好的方式计算
        val size = vec1.size
        var i = 0
        
        // 每次处理4个元素
        while (i + 3 < size) {
            val v1a = vec1[i]
            val v1b = vec1[i + 1]
            val v1c = vec1[i + 2]
            val v1d = vec1[i + 3]
            
            val v2a = vec2[i]
            val v2b = vec2[i + 1]
            val v2c = vec2[i + 2]
            val v2d = vec2[i + 3]
            
            dotProduct += v1a * v2a + v1b * v2b + v1c * v2c + v1d * v2d
            magnitude1 += v1a * v1a + v1b * v1b + v1c * v1c + v1d * v1d
            magnitude2 += v2a * v2a + v2b * v2b + v2c * v2c + v2d * v2d
            
            i += 4
        }
        
        // 处理剩余元素
        while (i < size) {
            dotProduct += vec1[i] * vec2[i]
            magnitude1 += vec1[i] * vec1[i]
            magnitude2 += vec2[i] * vec2[i]
            i++
        }
        
        magnitude1 = sqrt(magnitude1)
        magnitude2 = sqrt(magnitude2)
        
        return if (magnitude1 > 0 && magnitude2 > 0) {
            dotProduct / (magnitude1 * magnitude2)
        } else {
            0f
        }
    }
}
