package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 智能TTL缓存策略
 * 根据模型类型、查询类型和历史访问模式动态调整TTL
 */
class SmartTTLCacheStrategy(
    private val defaultTtl: Long = 3600, // 默认TTL为1小时
    private val minTtl: Long = 60,       // 最小TTL为1分钟
    private val maxTtl: Long = 86400,    // 最大TTL为1天
    private val learningRate: Double = 0.1, // 学习率
    private val modelWeights: Map<String, Double> = emptyMap(), // 模型权重
    private val queryTypeWeights: Map<String, Double> = emptyMap() // 查询类型权重
) : CacheStrategy {
    private val logger = LoggerFactory.getLogger(SmartTTLCacheStrategy::class.java)

    // 模型特定的TTL配置
    private val modelTtlConfig = ConcurrentHashMap<String, Long>()

    // 查询类型特定的TTL配置
    private val queryTypeTtlConfig = ConcurrentHashMap<String, Long>()

    // 访问统计
    private val accessStats = ConcurrentHashMap<String, AccessStats>()

    // 总访问次数
    private val totalAccesses = AtomicLong(0)

    // 总命中次数
    private val totalHits = AtomicLong(0)

    /**
     * 访问统计类
     */
    private inner class AccessStats {
        val accessTimes = mutableListOf<Long>() // 访问时间列表
        val hitCount = AtomicLong(0) // 命中次数
        val missCount = AtomicLong(0) // 未命中次数
        var lastAccessTime: Long = 0 // 最后访问时间
        var lastHitTime: Long = 0 // 最后命中时间

        /**
         * 记录访问
         * @param time 访问时间
         * @param hit 是否命中
         */
        fun recordAccess(time: Long, hit: Boolean) {
            accessTimes.add(time)
            lastAccessTime = time
            if (hit) {
                hitCount.incrementAndGet()
                lastHitTime = time
            } else {
                missCount.incrementAndGet()
            }

            // 保持访问时间列表不超过100个元素
            if (accessTimes.size > 100) {
                accessTimes.removeAt(0)
            }
        }

        /**
         * 获取命中率
         * @return 命中率
         */
        fun getHitRate(): Double {
            val total = hitCount.get() + missCount.get()
            return if (total > 0) hitCount.get().toDouble() / total else 0.0
        }

        /**
         * 获取访问频率（每小时访问次数）
         * @return 访问频率
         */
        fun getAccessFrequency(): Double {
            if (accessTimes.size < 2) return 0.0
            val timeSpan = (accessTimes.last() - accessTimes.first()) / 1000.0 // 秒
            if (timeSpan <= 0) return 0.0
            return accessTimes.size * 3600.0 / timeSpan // 每小时访问次数
        }

        /**
         * 获取平均访问间隔（秒）
         * @return 平均访问间隔
         */
        fun getAverageInterval(): Double {
            if (accessTimes.size < 2) return 0.0
            val intervals = mutableListOf<Long>()
            for (i in 1 until accessTimes.size) {
                intervals.add(accessTimes[i] - accessTimes[i-1])
            }
            return intervals.average() / 1000.0 // 转换为秒
        }
    }

    /**
     * 获取策略名称
     * @return 策略名称
     */
    override fun getName(): String {
        return "SmartTTL"
    }

    /**
     * 判断是否应该缓存
     * @param key 缓存键
     * @param value 缓存值
     * @return 是否应该缓存
     */
    override fun shouldCache(key: String, value: JsonObject): Boolean {
        // 默认所有内容都应该被缓存
        return true
    }

    /**
     * 计算TTL
     * @param key 缓存键
     * @param value 缓存值
     * @return TTL（秒）
     */
    override fun calculateTtl(key: String, value: JsonObject): Long {
        // 提取模型ID和查询类型
        val modelId = extractModelId(value)
        val queryType = extractQueryType(value)
        
        // 获取基础TTL
        val baseTtl = getBaseTtl(modelId, queryType)
        
        // 获取访问统计
        val stats = accessStats.computeIfAbsent(key) { AccessStats() }
        
        // 根据访问统计调整TTL
        val adjustedTtl = adjustTtlByStats(baseTtl, stats)
        
        logger.debug("计算TTL: key=$key, modelId=$modelId, queryType=$queryType, baseTtl=$baseTtl, adjustedTtl=$adjustedTtl")
        
        return adjustedTtl
    }

    /**
     * 获取基础TTL
     * @param modelId 模型ID
     * @param queryType 查询类型
     * @return 基础TTL
     */
    private fun getBaseTtl(modelId: String?, queryType: String?): Long {
        // 优先使用模型特定的TTL
        if (modelId != null && modelTtlConfig.containsKey(modelId)) {
            return modelTtlConfig[modelId]!!
        }

        // 其次使用查询类型特定的TTL
        if (queryType != null && queryTypeTtlConfig.containsKey(queryType)) {
            return queryTypeTtlConfig[queryType]!!
        }

        // 最后使用默认TTL
        return defaultTtl
    }

    /**
     * 根据访问统计调整TTL
     * @param baseTtl 基础TTL
     * @param stats 访问统计
     * @return 调整后的TTL
     */
    private fun adjustTtlByStats(baseTtl: Long, stats: AccessStats): Long {
        // 如果没有足够的访问数据，返回基础TTL
        if (stats.accessTimes.size < 5) {
            return baseTtl
        }

        // 计算访问频率和平均访问间隔
        val frequency = stats.getAccessFrequency()
        val avgInterval = stats.getAverageInterval()
        val hitRate = stats.getHitRate()

        // 根据访问频率调整TTL
        // 访问频率高 -> 增加TTL
        // 访问频率低 -> 减少TTL
        val frequencyFactor = if (frequency > 10.0) 1.2 else if (frequency < 1.0) 0.8 else 1.0

        // 根据平均访问间隔调整TTL
        // 访问间隔短 -> 增加TTL
        // 访问间隔长 -> 减少TTL
        val intervalFactor = if (avgInterval > 0) {
            when {
                avgInterval < 60 -> 1.3 // 小于1分钟
                avgInterval < 300 -> 1.2 // 小于5分钟
                avgInterval < 3600 -> 1.0 // 小于1小时
                avgInterval < 86400 -> 0.8 // 小于1天
                else -> 0.6 // 大于1天
            }
        } else 1.0

        // 根据命中率调整TTL
        // 命中率高 -> 增加TTL
        // 命中率低 -> 减少TTL
        val hitRateFactor = if (hitRate > 0.8) 1.3 else if (hitRate < 0.2) 0.7 else 1.0

        // 综合调整因子
        val adjustmentFactor = frequencyFactor * intervalFactor * hitRateFactor

        // 应用调整因子
        val adjustedTtl = (baseTtl * adjustmentFactor).toLong()

        // 确保TTL在允许范围内
        return adjustedTtl.coerceIn(minTtl, maxTtl)
    }

    /**
     * 缓存命中回调
     * @param key 缓存键
     */
    fun onCacheHit(key: String) {
        totalAccesses.incrementAndGet()
        totalHits.incrementAndGet()
        
        val stats = accessStats.computeIfAbsent(key) { AccessStats() }
        stats.recordAccess(System.currentTimeMillis(), true)
    }

    /**
     * 缓存未命中回调
     * @param key 缓存键
     */
    fun onCacheMiss(key: String) {
        totalAccesses.incrementAndGet()
        
        val stats = accessStats.computeIfAbsent(key) { AccessStats() }
        stats.recordAccess(System.currentTimeMillis(), false)
    }

    /**
     * 设置缓存回调
     * @param key 缓存键
     * @param value 缓存值
     */
    fun onCacheSet(key: String, value: JsonObject) {
        // 可以在这里添加额外的逻辑
    }

    /**
     * 移除缓存回调
     * @param key 缓存键
     */
    fun onCacheRemove(key: String) {
        // 清理访问统计
        accessStats.remove(key)
    }

    /**
     * 获取统计信息
     * @return 统计信息
     */
    fun getStats(): JsonObject {
        val hitRate = if (totalAccesses.get() > 0) {
            totalHits.get().toDouble() / totalAccesses.get()
        } else 0.0

        return JsonObject()
            .put("name", getName())
            .put("totalAccesses", totalAccesses.get())
            .put("totalHits", totalHits.get())
            .put("hitRate", hitRate)
            .put("keysTracked", accessStats.size)
    }

    /**
     * 设置模型特定的TTL
     * @param modelId 模型ID
     * @param ttl TTL（秒）
     */
    fun setModelTtl(modelId: String, ttl: Long) {
        modelTtlConfig[modelId] = ttl
    }

    /**
     * 设置查询类型特定的TTL
     * @param queryType 查询类型
     * @param ttl TTL（秒）
     */
    fun setQueryTypeTtl(queryType: String, ttl: Long) {
        queryTypeTtlConfig[queryType] = ttl
    }

    /**
     * 从缓存值中提取模型ID
     * @param value 缓存值
     * @return 模型ID，如果不存在则返回null
     */
    private fun extractModelId(value: JsonObject): String? {
        // 尝试从不同的字段中提取模型ID
        return when {
            value.containsKey("model") -> value.getString("model")
            value.containsKey("modelId") -> value.getString("modelId")
            value.containsKey("model_id") -> value.getString("model_id")
            else -> null
        }
    }

    /**
     * 从缓存值中提取查询类型
     * @param value 缓存值
     * @return 查询类型，如果不存在则返回null
     */
    private fun extractQueryType(value: JsonObject): String? {
        // 尝试从不同的字段中提取查询类型
        return when {
            value.containsKey("type") -> value.getString("type")
            value.containsKey("queryType") -> value.getString("queryType")
            value.containsKey("query_type") -> value.getString("query_type")
            else -> null
        }
    }
}
