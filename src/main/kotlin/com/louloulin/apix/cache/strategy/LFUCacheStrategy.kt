package com.louloulin.apix.cache.strategy

import com.louloulin.apix.cache.CacheStrategy
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LFU（最不经常使用）缓存策略实现
 * 当缓存达到最大容量时，移除访问频率最低的缓存项
 */
class LFUCacheStrategy(
    private val maxSize: Int = 10000,
    private val defaultTtl: Long = 3600 // 默认TTL为1小时
) : CacheStrategy {
    private val logger = LoggerFactory.getLogger(LFUCacheStrategy::class.java)

    // 缓存项访问频率映射
    private val frequencyMap = ConcurrentHashMap<String, AtomicInteger>()

    // 当前缓存大小
    private val currentSize = AtomicInteger(0)

    // 模型特定的TTL配置
    private val modelTtlConfig = ConcurrentHashMap<String, Long>()

    // 查询类型特定的TTL配置
    private val queryTypeTtlConfig = ConcurrentHashMap<String, Long>()

    /**
     * 获取策略名称
     * @return 策略名称
     */
    override fun getName(): String {
        return "LFU"
    }

    /**
     * 初始化缓存策略
     * @param config 配置信息
     */
    fun initialize(config: JsonObject) {
        // 设置最大缓存大小
        val configMaxSize = config.getInteger("maxSize", maxSize)
        if (configMaxSize > 0) {
            currentSize.set(0)
        }

        // 设置默认TTL
        val configDefaultTtl = config.getLong("defaultTtl", defaultTtl)

        // 设置模型特定的TTL
        val modelTtlJson = config.getJsonObject("modelTtl")
        if (modelTtlJson != null) {
            for (modelId in modelTtlJson.fieldNames()) {
                val ttl = modelTtlJson.getLong(modelId)
                if (ttl > 0) {
                    modelTtlConfig[modelId] = ttl
                }
            }
        }

        // 设置查询类型特定的TTL
        val queryTypeTtlJson = config.getJsonObject("queryTypeTtl")
        if (queryTypeTtlJson != null) {
            for (queryType in queryTypeTtlJson.fieldNames()) {
                val ttl = queryTypeTtlJson.getLong(queryType)
                if (ttl > 0) {
                    queryTypeTtlConfig[queryType] = ttl
                }
            }
        }

        logger.info("LFU缓存策略初始化完成，最大大小: $configMaxSize, 默认TTL: ${configDefaultTtl}")
    }

    override fun shouldCache(key: String, value: JsonObject): Boolean {
        // 检查是否达到最大缓存大小
        if (currentSize.get() >= maxSize) {
            // 移除访问频率最低的缓存项
            evictIfNeeded()
        }

        return true
    }

    override fun calculateTtl(key: String, value: JsonObject): Long {
        // 提取模型ID和查询类型
        val modelId = extractModelId(value)
        val queryType = extractQueryType(value)

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

    override fun onCacheHit(key: String) {
        // 增加访问频率
        frequencyMap.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
    }

    override fun onCacheMiss(key: String) {
        // 不需要处理
    }

    override fun onCacheSet(key: String, value: JsonObject) {
        // 初始化访问频率
        frequencyMap[key] = AtomicInteger(1)

        // 增加缓存大小
        currentSize.incrementAndGet()
    }

    override fun onCacheRemove(key: String) {
        // 从频率映射中移除
        frequencyMap.remove(key)

        // 减少缓存大小
        currentSize.decrementAndGet()
    }

    override fun getStats(): JsonObject {
        return JsonObject()
            .put("type", "LFU")
            .put("maxSize", maxSize)
            .put("currentSize", currentSize.get())
            .put("defaultTtl", defaultTtl)
            .put("modelTtlConfig", JsonObject(modelTtlConfig.mapValues { it.value }))
            .put("queryTypeTtlConfig", JsonObject(queryTypeTtlConfig.mapValues { it.value }))
    }

    /**
     * 如果需要，驱逐缓存项
     */
    private fun evictIfNeeded() {
        // 找到访问频率最低的缓存项
        var lowestFrequencyKey: String? = null
        var lowestFrequency = Int.MAX_VALUE

        for ((key, frequency) in frequencyMap) {
            val freq = frequency.get()
            if (freq < lowestFrequency) {
                lowestFrequency = freq
                lowestFrequencyKey = key
            }
        }

        // 移除访问频率最低的缓存项
        if (lowestFrequencyKey != null) {
            frequencyMap.remove(lowestFrequencyKey)
            currentSize.decrementAndGet()
            logger.debug("LFU缓存策略驱逐缓存项: $lowestFrequencyKey, 频率: $lowestFrequency")
        }
    }

    /**
     * 从缓存值中提取模型ID
     */
    private fun extractModelId(value: JsonObject): String? {
        // 尝试从不同的字段中提取模型ID
        val response = value.getJsonObject("response")
        if (response != null) {
            // 尝试从response中提取
            val modelId = response.getString("model")
            if (modelId != null) {
                return modelId
            }
        }

        // 尝试从metadata中提取
        val metadata = value.getJsonObject("metadata")
        if (metadata != null) {
            val modelId = metadata.getString("model")
            if (modelId != null) {
                return modelId
            }
        }

        // 直接从value中提取
        return value.getString("model")
    }

    /**
     * 从缓存值中提取查询类型
     */
    private fun extractQueryType(value: JsonObject): String? {
        // 尝试从不同的字段中提取查询类型
        val metadata = value.getJsonObject("metadata")
        if (metadata != null) {
            val queryType = metadata.getString("queryType")
            if (queryType != null) {
                return queryType
            }
        }

        // 直接从value中提取
        return value.getString("queryType")
    }
}
