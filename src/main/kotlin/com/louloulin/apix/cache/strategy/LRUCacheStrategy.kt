package com.louloulin.apix.cache.strategy

import com.louloulin.apix.cache.CacheStrategy
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * LRU（最近最少使用）缓存策略实现
 * 当缓存达到最大容量时，移除最长时间未被访问的缓存项
 */
class LRUCacheStrategy(
    private val maxSize: Int = 10000,
    private val defaultTtl: Long = 3600 // 默认TTL为1小时
) : CacheStrategy {
    private val logger = LoggerFactory.getLogger(LRUCacheStrategy::class.java)

    // 缓存访问记录，按访问时间排序
    private val accessQueue = ConcurrentLinkedQueue<String>()

    // 缓存项访问时间映射
    private val accessMap = ConcurrentHashMap<String, Long>()

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
        return "LRU"
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

        logger.info("LRU缓存策略初始化完成，最大大小: $configMaxSize, 默认TTL: ${configDefaultTtl}")
    }

    override fun shouldCache(key: String, value: JsonObject): Boolean {
        // 检查是否达到最大缓存大小
        if (currentSize.get() >= maxSize) {
            // 移除最长时间未被访问的缓存项
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
        // 更新访问时间
        val currentTime = System.currentTimeMillis()
        accessMap[key] = currentTime

        // 将key移到队列末尾（最近访问）
        accessQueue.remove(key)
        accessQueue.add(key)
    }

    override fun onCacheMiss(key: String) {
        // 不需要处理
    }

    override fun onCacheSet(key: String, value: JsonObject) {
        // 更新访问时间
        val currentTime = System.currentTimeMillis()
        accessMap[key] = currentTime

        // 将key添加到队列末尾（最近访问）
        accessQueue.add(key)

        // 增加缓存大小
        currentSize.incrementAndGet()
    }

    override fun onCacheRemove(key: String) {
        // 从访问记录中移除
        accessMap.remove(key)
        accessQueue.remove(key)

        // 减少缓存大小
        currentSize.decrementAndGet()
    }

    override fun getStats(): JsonObject {
        return JsonObject()
            .put("type", "LRU")
            .put("maxSize", maxSize)
            .put("currentSize", currentSize.get())
            .put("defaultTtl", defaultTtl)
            .put("modelTtlConfig", JsonObject(modelTtlConfig.mapValues { it.value }))
            .put("queryTypeTtlConfig", JsonObject(queryTypeTtlConfig.mapValues { it.value }))
    }

    /**
     * 如果需要，驱逐缓存项
     * 增强版LRU策略，考虑了访问时间和频率
     */
    private fun evictIfNeeded() {
        // 需要驱逐的数量，驱逐缓存大小的10%
        val evictionCount = Math.max(1, maxSize / 10)
        logger.debug("LRU缓存策略需要驱逐 $evictionCount 个缓存项")

        // 按照访问时间排序，驱逐最早访问的项
        val currentTime = System.currentTimeMillis()
        val sortedEntries = accessMap.entries
            .sortedBy { it.value } // 按访问时间排序
            .take(evictionCount * 2) // 取出最早访问的一部分项

        // 从这些项中选择要驱逐的项
        val toEvict = sortedEntries
            .sortedWith(compareBy<Map.Entry<String, Long>> {
                // 首先按访问时间排序
                it.value
            }.thenBy {
                // 其次按照在队列中的位置排序（访问频率）
                val index = accessQueue.indexOf(it.key)
                if (index == -1) Int.MAX_VALUE else index
            })
            .take(evictionCount)

        // 驱逐选定的项
        for ((key, _) in toEvict) {
            accessMap.remove(key)
            accessQueue.remove(key)
            currentSize.decrementAndGet()
            logger.debug("LRU缓存策略驱逐缓存项: $key")
        }

        // 如果还需要继续驱逐，使用简单的LRU策略
        while (currentSize.get() >= maxSize) {
            val oldestKey = accessQueue.poll()
            if (oldestKey != null) {
                accessMap.remove(oldestKey)
                currentSize.decrementAndGet()
                logger.debug("LRU缓存策略驱逐缓存项(备用方式): $oldestKey")
            } else {
                break
            }
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
