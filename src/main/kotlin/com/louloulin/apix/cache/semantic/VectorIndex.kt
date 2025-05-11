package com.louloulin.apix.cache.semantic

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * 向量索引，用于存储和查询向量。
 * 支持分片存储和查询。
 */
class VectorIndex {
    private val logger = LoggerFactory.getLogger(VectorIndex::class.java)

    // 分片索引
    private val shardIndices = ConcurrentHashMap<String, ShardIndex>()

    // 索引配置
    private var config = JsonObject()

    /**
     * 初始化向量索引。
     *
     * @param config 索引配置
     */
    fun initialize(config: JsonObject) {
        this.config = config
        logger.info("向量索引初始化完成")
    }

    /**
     * 添加向量到索引。
     *
     * @param shardId 分片ID
     * @param key 键
     * @param vector 向量
     */
    fun add(shardId: String, key: String, vector: JsonArray) {
        // 获取或创建分片索引
        val shardIndex = shardIndices.computeIfAbsent(shardId) { ShardIndex() }

        // 添加向量到分片索引
        shardIndex.add(key, vector)

        logger.debug("向量添加到索引: 分片=$shardId, 键=$key")
    }

    /**
     * 从索引中移除向量。
     *
     * @param shardId 分片ID
     * @param key 键
     */
    fun remove(shardId: String, key: String) {
        // 获取分片索引
        val shardIndex = shardIndices[shardId]
        if (shardIndex != null) {
            // 从分片索引中移除向量
            shardIndex.remove(key)
            logger.debug("向量从索引中移除: 分片=$shardId, 键=$key")
        }
    }

    /**
     * 在索引中搜索向量。
     *
     * @param shardId 分片ID
     * @param queryVector 查询向量
     * @param threshold 相似度阈值
     * @return List<VectorMatch> 匹配结果
     */
    fun search(shardId: String, queryVector: JsonArray, threshold: Double): List<VectorMatch> {
        // 获取分片索引
        val shardIndex = shardIndices[shardId]
        if (shardIndex == null) {
            logger.debug("分片索引不存在: $shardId")
            return emptyList()
        }

        // 在分片索引中搜索
        return shardIndex.search(queryVector, threshold)
    }

    /**
     * 获取分片数据。
     *
     * @param shardId 分片ID
     * @return JsonObject 分片数据
     */
    fun getShardData(shardId: String): JsonObject {
        // 获取分片索引
        val shardIndex = shardIndices[shardId]
        if (shardIndex == null) {
            logger.debug("分片索引不存在: $shardId")
            return JsonObject()
        }

        // 获取分片数据
        return shardIndex.getData()
    }

    /**
     * 设置分片数据。
     *
     * @param shardId 分片ID
     * @param data 分片数据
     */
    fun setShardData(shardId: String, data: JsonObject) {
        // 创建新的分片索引
        val shardIndex = ShardIndex()

        // 设置分片数据
        shardIndex.setData(data)

        // 更新分片索引
        shardIndices[shardId] = shardIndex

        logger.debug("分片数据已设置: $shardId, 向量数量=${shardIndex.size()}")
    }

    /**
     * 获取分片大小。
     *
     * @param shardId 分片ID
     * @return Int 分片大小
     */
    fun getShardSize(shardId: String): Int {
        // 获取分片索引
        val shardIndex = shardIndices[shardId]
        return shardIndex?.size() ?: 0
    }

    /**
     * 获取总大小。
     *
     * @return Int 总大小
     */
    fun getTotalSize(): Int {
        var totalSize = 0
        for (shardIndex in shardIndices.values) {
            totalSize += shardIndex.size()
        }
        return totalSize
    }

    /**
     * 分片索引类。
     */
    inner class ShardIndex {
        // 向量映射
        private val vectors = ConcurrentHashMap<String, JsonArray>()

        /**
         * 添加向量。
         *
         * @param key 键
         * @param vector 向量
         */
        fun add(key: String, vector: JsonArray) {
            vectors[key] = vector
        }

        /**
         * 移除向量。
         *
         * @param key 键
         */
        fun remove(key: String) {
            vectors.remove(key)
        }

        /**
         * 搜索向量。
         *
         * @param queryVector 查询向量
         * @param threshold 相似度阈值
         * @return List<VectorMatch> 匹配结果
         */
        fun search(queryVector: JsonArray, threshold: Double): List<VectorMatch> {
            val matches = mutableListOf<VectorMatch>()

            // 计算与每个向量的相似度
            for ((key, vector) in vectors) {
                val similarity = cosineSimilarity(queryVector, vector)
                if (similarity >= threshold) {
                    matches.add(VectorMatch(key, similarity))
                }
            }

            // 按相似度降序排序
            matches.sortByDescending { it.similarity }

            // 限制结果数量
            val maxResults = config.getInteger("maxResults", 10)
            return if (matches.size > maxResults) {
                matches.subList(0, maxResults)
            } else {
                matches
            }
        }

        /**
         * 获取分片数据。
         *
         * @return JsonObject 分片数据
         */
        fun getData(): JsonObject {
            val data = JsonObject()
            for ((key, vector) in vectors) {
                data.put(key, vector)
            }
            return data
        }

        /**
         * 设置分片数据。
         *
         * @param data 分片数据
         */
        fun setData(data: JsonObject) {
            vectors.clear()
            for (key in data.fieldNames()) {
                val vector = data.getJsonArray(key)
                if (vector != null) {
                    vectors[key] = vector
                }
            }
        }

        /**
         * 获取分片大小。
         *
         * @return Int 分片大小
         */
        fun size(): Int {
            return vectors.size
        }
    }

    /**
     * 计算余弦相似度。
     *
     * @param vector1 向量1
     * @param vector2 向量2
     * @return Double 相似度
     */
    private fun cosineSimilarity(vector1: JsonArray, vector2: JsonArray): Double {
        if (vector1.size() != vector2.size()) {
            return 0.0
        }

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        for (i in 0 until vector1.size()) {
            val v1 = vector1.getDouble(i)
            val v2 = vector2.getDouble(i)
            dotProduct += v1 * v2
            norm1 += v1 * v1
            norm2 += v2 * v2
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0
        }

        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }

    /**
     * 向量匹配类。
     *
     * @param key 键
     * @param similarity 相似度
     */
    data class VectorMatch(
        val key: String,
        val similarity: Double
    )
}
