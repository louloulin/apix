package com.louloulin.apix.cache.semantic

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * 优化的相似度匹配器，提供高效的向量相似度搜索
 */
class OptimizedSimilarityMatcher(
    private val vertx: Vertx,
    private val config: JsonObject = JsonObject()
) {
    private val logger = LoggerFactory.getLogger(OptimizedSimilarityMatcher::class.java)
    
    // 向量维度
    private val vectorDimension = config.getInteger("vectorDimension", 10000)
    
    // 相似度阈值
    private val similarityThreshold = config.getFloat("similarityThreshold", 0.8f)
    
    // 索引类型：brute_force, lsh, hnsw
    private val indexType = config.getString("indexType", "brute_force")
    
    // LSH 参数
    private val lshBands = config.getInteger("lshBands", 20)
    private val lshRows = config.getInteger("lshRows", 5)
    
    // HNSW 参数
    private val hnswM = config.getInteger("hnswM", 16)
    private val hnswEfConstruction = config.getInteger("hnswEfConstruction", 200)
    private val hnswEfSearch = config.getInteger("hnswEfSearch", 100)
    
    // 向量存储
    private val vectors = ConcurrentHashMap<String, FloatArray>()
    
    // LSH 索引
    private val lshIndex = if (indexType == "lsh") createLSHIndex() else null
    
    // HNSW 索引
    private val hnswIndex = if (indexType == "hnsw") createHNSWIndex() else null
    
    init {
        logger.info("Initialized OptimizedSimilarityMatcher with indexType=$indexType, " +
                   "vectorDimension=$vectorDimension, similarityThreshold=$similarityThreshold")
    }
    
    /**
     * 添加向量到索引
     */
    fun addVector(key: String, vector: FloatArray): Future<Void> {
        val promise = Promise.promise<Void>()
        
        vertx.executeBlocking<Void>({ blockingPromise ->
            try {
                vectors[key] = vector
                
                // 根据索引类型添加到相应的索引
                when (indexType) {
                    "lsh" -> lshIndex?.addVector(key, vector)
                    "hnsw" -> hnswIndex?.addVector(key, vector)
                }
                
                blockingPromise.complete()
            } catch (e: Exception) {
                logger.error("Error adding vector to index", e)
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
     * 移除向量从索引
     */
    fun removeVector(key: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        vertx.executeBlocking<Void>({ blockingPromise ->
            try {
                vectors.remove(key)
                
                // 根据索引类型从相应的索引移除
                when (indexType) {
                    "lsh" -> lshIndex?.removeVector(key)
                    "hnsw" -> hnswIndex?.removeVector(key)
                }
                
                blockingPromise.complete()
            } catch (e: Exception) {
                logger.error("Error removing vector from index", e)
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
     * 查找最相似的向量
     */
    fun findMostSimilar(queryVector: FloatArray, maxResults: Int = 1): Future<List<Pair<String, Float>>> {
        val promise = Promise.promise<List<Pair<String, Float>>>()
        
        vertx.executeBlocking<List<Pair<String, Float>>>({ blockingPromise ->
            try {
                val results = when (indexType) {
                    "lsh" -> findMostSimilarLSH(queryVector, maxResults)
                    "hnsw" -> findMostSimilarHNSW(queryVector, maxResults)
                    else -> findMostSimilarBruteForce(queryVector, maxResults)
                }
                
                blockingPromise.complete(results)
            } catch (e: Exception) {
                logger.error("Error finding most similar vectors", e)
                blockingPromise.fail(e)
            }
        }, false)
            .onSuccess { results ->
                promise.complete(results)
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 暴力搜索最相似的向量
     */
    private fun findMostSimilarBruteForce(queryVector: FloatArray, maxResults: Int): List<Pair<String, Float>> {
        // 计算所有向量的相似度
        val similarities = mutableListOf<Pair<String, Float>>()
        
        for ((key, vector) in vectors) {
            val similarity = cosineSimilarity(queryVector, vector)
            
            if (similarity >= similarityThreshold) {
                similarities.add(Pair(key, similarity))
            }
        }
        
        // 按相似度降序排序并返回前 maxResults 个结果
        return similarities.sortedByDescending { it.second }.take(maxResults)
    }
    
    /**
     * 使用 LSH 查找最相似的向量
     */
    private fun findMostSimilarLSH(queryVector: FloatArray, maxResults: Int): List<Pair<String, Float>> {
        // 使用 LSH 索引查找候选集
        val candidates = lshIndex?.findCandidates(queryVector) ?: emptySet()
        
        // 计算候选向量的相似度
        val similarities = mutableListOf<Pair<String, Float>>()
        
        for (key in candidates) {
            val vector = vectors[key] ?: continue
            val similarity = cosineSimilarity(queryVector, vector)
            
            if (similarity >= similarityThreshold) {
                similarities.add(Pair(key, similarity))
            }
        }
        
        // 按相似度降序排序并返回前 maxResults 个结果
        return similarities.sortedByDescending { it.second }.take(maxResults)
    }
    
    /**
     * 使用 HNSW 查找最相似的向量
     */
    private fun findMostSimilarHNSW(queryVector: FloatArray, maxResults: Int): List<Pair<String, Float>> {
        // 使用 HNSW 索引查找最近邻
        val neighbors = hnswIndex?.findNearest(queryVector, maxResults) ?: emptyList()
        
        // 转换为所需的结果格式
        return neighbors.map { (key, distance) ->
            // HNSW 返回的是距离，需要转换为相似度
            val similarity = 1.0f - distance
            Pair(key, similarity)
        }.filter { it.second >= similarityThreshold }
    }
    
    /**
     * 计算余弦相似度
     */
    private fun cosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float {
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
    
    /**
     * 创建 LSH 索引
     */
    private fun createLSHIndex(): LSHIndex {
        return LSHIndex(vectorDimension, lshBands, lshRows)
    }
    
    /**
     * 创建 HNSW 索引
     */
    private fun createHNSWIndex(): HNSWIndex {
        return HNSWIndex(vectorDimension, hnswM, hnswEfConstruction, hnswEfSearch)
    }
    
    /**
     * 清空索引
     */
    fun clear() {
        vectors.clear()
        lshIndex?.clear()
        hnswIndex?.clear()
    }
    
    /**
     * 获取索引统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("indexType", indexType)
            .put("vectorCount", vectors.size)
            .put("vectorDimension", vectorDimension)
            .put("similarityThreshold", similarityThreshold)
    }
    
    /**
     * LSH 索引实现
     */
    private inner class LSHIndex(
        private val dimension: Int,
        private val bands: Int,
        private val rows: Int
    ) {
        // 哈希函数
        private val hashFunctions = Array(bands * rows) { i ->
            val a = FloatArray(dimension) { (Math.random() * 2 - 1).toFloat() }
            val b = (Math.random() * 10).toFloat()
            Pair(a, b)
        }
        
        // 哈希桶
        private val hashBuckets = Array(bands) { mutableMapOf<Int, MutableSet<String>>() }
        
        /**
         * 添加向量到索引
         */
        fun addVector(key: String, vector: FloatArray) {
            // 计算向量的 LSH 签名
            val signature = computeSignature(vector)
            
            // 将向量添加到相应的哈希桶
            for (i in 0 until bands) {
                val bandHash = signature.sliceArray(i * rows until (i + 1) * rows).contentHashCode()
                hashBuckets[i].computeIfAbsent(bandHash) { mutableSetOf() }.add(key)
            }
        }
        
        /**
         * 从索引中移除向量
         */
        fun removeVector(key: String) {
            // 从所有哈希桶中移除向量
            for (band in hashBuckets) {
                for (bucket in band.values) {
                    bucket.remove(key)
                }
            }
        }
        
        /**
         * 查找候选集
         */
        fun findCandidates(queryVector: FloatArray): Set<String> {
            // 计算查询向量的 LSH 签名
            val signature = computeSignature(queryVector)
            
            // 收集所有候选向量
            val candidates = mutableSetOf<String>()
            
            for (i in 0 until bands) {
                val bandHash = signature.sliceArray(i * rows until (i + 1) * rows).contentHashCode()
                val bucket = hashBuckets[i][bandHash]
                
                if (bucket != null) {
                    candidates.addAll(bucket)
                }
            }
            
            return candidates
        }
        
        /**
         * 计算向量的 LSH 签名
         */
        private fun computeSignature(vector: FloatArray): IntArray {
            val signature = IntArray(bands * rows)
            
            for (i in 0 until bands * rows) {
                val (a, b) = hashFunctions[i]
                
                // 计算向量与哈希函数的点积
                var dotProduct = 0f
                for (j in 0 until dimension) {
                    dotProduct += a[j] * vector[j]
                }
                
                // 应用哈希函数
                signature[i] = ((dotProduct + b) / 10).toInt()
            }
            
            return signature
        }
        
        /**
         * 清空索引
         */
        fun clear() {
            for (band in hashBuckets) {
                band.clear()
            }
        }
    }
    
    /**
     * HNSW 索引实现（简化版）
     * 注意：这是一个简化的实现，实际应用中应该使用专业的库如 hnswlib-kotlin
     */
    private inner class HNSWIndex(
        private val dimension: Int,
        private val m: Int,
        private val efConstruction: Int,
        private val efSearch: Int
    ) {
        // 图结构
        private val graph = ConcurrentHashMap<String, MutableSet<String>>()
        
        // 入口点
        private var entryPoint: String? = null
        
        /**
         * 添加向量到索引
         */
        fun addVector(key: String, vector: FloatArray) {
            // 如果是第一个向量，设为入口点
            if (entryPoint == null) {
                entryPoint = key
                graph[key] = mutableSetOf()
                return
            }
            
            // 查找最近的 M 个邻居
            val neighbors = findNearestNeighbors(vector, m)
            
            // 添加边
            graph[key] = neighbors.map { it.first }.toMutableSet()
            
            // 为每个邻居添加反向边
            for ((neighborKey, _) in neighbors) {
                graph.computeIfAbsent(neighborKey) { mutableSetOf() }.add(key)
                
                // 如果邻居的边数超过 M，移除最远的边
                val neighborEdges = graph[neighborKey]!!
                if (neighborEdges.size > m) {
                    val neighborVector = vectors[neighborKey] ?: continue
                    val edgesToRemove = neighborEdges
                        .map { edge -> Pair(edge, cosineSimilarity(neighborVector, vectors[edge] ?: return@map Pair(edge, 0f))) }
                        .sortedBy { it.second }
                        .take(neighborEdges.size - m)
                        .map { it.first }
                    
                    neighborEdges.removeAll(edgesToRemove.toSet())
                }
            }
        }
        
        /**
         * 从索引中移除向量
         */
        fun removeVector(key: String) {
            // 移除所有指向该向量的边
            for (edges in graph.values) {
                edges.remove(key)
            }
            
            // 移除该向量的所有边
            graph.remove(key)
            
            // 如果移除的是入口点，选择新的入口点
            if (entryPoint == key) {
                entryPoint = graph.keys.firstOrNull()
            }
        }
        
        /**
         * 查找最近的 K 个邻居
         */
        fun findNearest(queryVector: FloatArray, k: Int): List<Pair<String, Float>> {
            if (entryPoint == null) {
                return emptyList()
            }
            
            // 使用贪婪搜索查找最近邻
            val visited = mutableSetOf<String>()
            val candidates = mutableListOf<Pair<String, Float>>()
            
            // 从入口点开始
            var current = entryPoint!!
            var currentVector = vectors[current] ?: return emptyList()
            var currentDistance = 1.0f - cosineSimilarity(queryVector, currentVector)
            
            candidates.add(Pair(current, currentDistance))
            visited.add(current)
            
            // 贪婪搜索
            var improved = true
            while (improved) {
                improved = false
                
                // 检查当前节点的所有邻居
                for (neighbor in graph[current] ?: emptySet()) {
                    if (neighbor in visited) continue
                    
                    val neighborVector = vectors[neighbor] ?: continue
                    val distance = 1.0f - cosineSimilarity(queryVector, neighborVector)
                    
                    candidates.add(Pair(neighbor, distance))
                    visited.add(neighbor)
                    
                    // 如果找到更近的邻居，移动到该邻居
                    if (distance < currentDistance) {
                        current = neighbor
                        currentDistance = distance
                        improved = true
                    }
                }
            }
            
            // 返回最近的 K 个邻居
            return candidates.sortedBy { it.second }.take(k)
        }
        
        /**
         * 查找最近的 K 个邻居（内部使用）
         */
        private fun findNearestNeighbors(queryVector: FloatArray, k: Int): List<Pair<String, Float>> {
            // 计算与所有向量的相似度
            val similarities = mutableListOf<Pair<String, Float>>()
            
            for ((key, vector) in vectors) {
                val similarity = cosineSimilarity(queryVector, vector)
                similarities.add(Pair(key, similarity))
            }
            
            // 按相似度降序排序并返回前 K 个结果
            return similarities.sortedByDescending { it.second }.take(k)
        }
        
        /**
         * 清空索引
         */
        fun clear() {
            graph.clear()
            entryPoint = null
        }
    }
}
