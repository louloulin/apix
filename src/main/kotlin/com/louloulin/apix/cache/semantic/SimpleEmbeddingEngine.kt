package com.louloulin.apix.cache.semantic

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * 简单的嵌入式引擎实现，使用基本的词频统计作为嵌入向量
 * 注意：这只是一个简单的实现，实际应用中应该使用更高级的嵌入模型
 */
class SimpleEmbeddingEngine(private val vertx: Vertx) : EmbeddingEngine {
    private val logger = LoggerFactory.getLogger(SimpleEmbeddingEngine::class.java)
    
    // 词汇表大小
    private val vocabularySize = 10000
    
    override fun embed(text: String): Future<FloatArray> {
        val promise = Promise.promise<FloatArray>()
        
        vertx.executeBlocking<FloatArray>({ promise ->
            try {
                val embedding = computeEmbedding(text)
                promise.complete(embedding)
            } catch (e: Exception) {
                logger.error("Error computing embedding for text", e)
                promise.fail(e)
            }
        }, false)
            .onSuccess { embedding ->
                promise.complete(embedding)
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun embedBatch(texts: List<String>): Future<List<FloatArray>> {
        val promise = Promise.promise<List<FloatArray>>()
        
        vertx.executeBlocking<List<FloatArray>>({ promise ->
            try {
                val embeddings = texts.map { computeEmbedding(it) }
                promise.complete(embeddings)
            } catch (e: Exception) {
                logger.error("Error computing embeddings for texts", e)
                promise.fail(e)
            }
        }, false)
            .onSuccess { embeddings ->
                promise.complete(embeddings)
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun similarity(embedding1: FloatArray, embedding2: FloatArray): Float {
        return cosineSimilarity(embedding1, embedding2)
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
        promise.complete()
        return promise.future()
    }
    
    /**
     * 计算文本的嵌入向量
     * 这里使用简单的词频统计作为嵌入向量
     */
    private fun computeEmbedding(text: String): FloatArray {
        // 将文本转换为小写并分词
        val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        
        // 计算词频
        val wordFreq = mutableMapOf<String, Int>()
        words.forEach { word ->
            wordFreq[word] = wordFreq.getOrDefault(word, 0) + 1
        }
        
        // 创建嵌入向量（使用词的哈希值作为索引）
        val embedding = FloatArray(vocabularySize) { 0f }
        
        wordFreq.forEach { (word, freq) ->
            val index = (word.hashCode() and 0x7FFFFFFF) % vocabularySize
            embedding[index] = freq.toFloat()
        }
        
        // 归一化嵌入向量
        normalizeVector(embedding)
        
        return embedding
    }
    
    /**
     * 归一化向量（使其长度为1）
     */
    private fun normalizeVector(vector: FloatArray) {
        val magnitude = sqrt(vector.map { it * it }.sum())
        
        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }
    }
    
    /**
     * 计算两个向量的余弦相似度
     */
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
        if (vec1.size != vec2.size) {
            throw IllegalArgumentException("Vectors must have the same dimension")
        }
        
        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f
        
        for (i in vec1.indices) {
            dotProduct += vec1[i] * vec2[i]
            magnitude1 += vec1[i] * vec1[i]
            magnitude2 += vec2[i] * vec2[i]
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
