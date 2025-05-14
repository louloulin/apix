package com.louloulin.apix.cache

import com.louloulin.apix.cache.strategy.LFUCacheStrategy
import com.louloulin.apix.cache.strategy.LRUCacheStrategy
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.redis.client.RedisOptions
import org.slf4j.LoggerFactory

/**
 * 缓存工厂类，用于创建不同类型的缓存管理器
 */
object CacheFactory {
    private val logger = LoggerFactory.getLogger(CacheFactory::class.java)

    /**
     * 创建内存缓存管理器
     * @param vertx Vertx 实例
     * @return 内存缓存管理器
     */
    fun createMemoryCache(vertx: Vertx): CacheManager {
        return MemoryCacheManager(vertx)
    }

    /**
     * 创建 Redis 缓存管理器
     * @param vertx Vertx 实例
     * @param redisOptions Redis 配置选项
     * @param keyPrefix 键前缀
     * @return Redis 缓存管理器
     */
    fun createRedisCache(
        vertx: Vertx,
        redisOptions: RedisOptions,
        keyPrefix: String = "apix:cache:"
    ): CacheManager {
        return RedisCacheManager(vertx, redisOptions, keyPrefix)
    }

    /**
     * 创建多级缓存管理器
     * @param vertx Vertx 实例
     * @param cacheManagers 缓存管理器列表，按优先级排序
     * @return 多级缓存管理器
     */
    fun createMultiLevelCache(
        vertx: Vertx,
        cacheManagers: List<CacheManager>
    ): CacheManager {
        return MultiLevelCacheManager(vertx, cacheManagers)
    }

    /**
     * 创建 LRU 缓存策略
     * @param maxSize 最大缓存大小
     * @param defaultTtl 默认 TTL（秒）
     * @param config 配置
     * @return LRU 缓存策略
     */
    fun createLRUCacheStrategy(
        maxSize: Int = 10000,
        defaultTtl: Long = 3600,
        config: JsonObject = JsonObject()
    ): CacheStrategy {
        val strategy = LRUCacheStrategy(maxSize, defaultTtl)
        strategy.initialize(config)
        return strategy
    }

    /**
     * 创建 LFU 缓存策略
     * @param maxSize 最大缓存大小
     * @param defaultTtl 默认 TTL（秒）
     * @param config 配置
     * @return LFU 缓存策略
     */
    fun createLFUCacheStrategy(
        maxSize: Int = 10000,
        defaultTtl: Long = 3600,
        config: JsonObject = JsonObject()
    ): CacheStrategy {
        val strategy = LFUCacheStrategy(maxSize, defaultTtl)
        strategy.initialize(config)
        return strategy
    }

    /**
     * 创建增强版 Redis 缓存管理器，支持分布式缓存和缓存一致性
     * @param vertx Vertx 实例
     * @param redisOptions Redis 配置选项
     * @param keyPrefix 键前缀
     * @param pubSubChannel 发布/订阅通道
     * @return Redis 缓存管理器
     */
    fun createEnhancedRedisCache(
        vertx: Vertx,
        redisOptions: RedisOptions,
        keyPrefix: String = "apix:cache:",
        pubSubChannel: String = "apix:cache:notifications"
    ): RedisCacheManager {
        val cacheManager = RedisCacheManager(vertx, redisOptions, keyPrefix, pubSubChannel)
        cacheManager.initialize()
        return cacheManager
    }

    /**
     * 从配置创建缓存管理器
     * @param vertx Vertx 实例
     * @param config 缓存配置
     * @return 缓存管理器
     */
    fun createFromConfig(vertx: Vertx, config: JsonObject): CacheManager {
        val cacheType = config.getString("type", "memory")

        return when (cacheType.lowercase()) {
            "memory" -> createMemoryCache(vertx)

            "redis" -> {
                val redisConfig = config.getJsonObject("redis", JsonObject())
                val redisOptions = RedisOptions()
                    .setConnectionString(redisConfig.getString("connectionString", "redis://localhost:6379"))
                    .setMaxPoolSize(redisConfig.getInteger("maxPoolSize", 8))
                    .setMaxPoolWaiting(redisConfig.getInteger("maxPoolWaiting", 32))

                val keyPrefix = redisConfig.getString("keyPrefix", "apix:cache:")
                val enhanced = redisConfig.getBoolean("enhanced", true)

                if (enhanced) {
                    val pubSubChannel = redisConfig.getString("pubSubChannel", "apix:cache:notifications")
                    createEnhancedRedisCache(vertx, redisOptions, keyPrefix, pubSubChannel)
                } else {
                    createRedisCache(vertx, redisOptions, keyPrefix)
                }
            }

            "multilevel" -> {
                val cacheConfigs = config.getJsonArray("caches")

                if (cacheConfigs == null || cacheConfigs.isEmpty) {
                    logger.warn("No cache configurations found for multilevel cache, falling back to memory cache")
                    createMemoryCache(vertx)
                } else {
                    val cacheManagers = cacheConfigs.mapNotNull { cacheConfig ->
                        if (cacheConfig is JsonObject) {
                            createFromConfig(vertx, cacheConfig)
                        } else {
                            logger.warn("Invalid cache configuration: $cacheConfig")
                            null
                        }
                    }

                    if (cacheManagers.isEmpty()) {
                        logger.warn("No valid cache managers created for multilevel cache, falling back to memory cache")
                        createMemoryCache(vertx)
                    } else {
                        createMultiLevelCache(vertx, cacheManagers)
                    }
                }
            }

            "lru" -> {
                val lruConfig = config.getJsonObject("lru", JsonObject())
                val maxSize = lruConfig.getInteger("maxSize", 10000)
                val defaultTtl = lruConfig.getLong("defaultTtl", 3600)

                createLRUCacheStrategy(maxSize, defaultTtl, lruConfig)

                // 返回内存缓存管理器，使用 LRU 策略
                val cacheManager = MemoryCacheManager(vertx)
                cacheManager.setCacheStrategy(createLRUCacheStrategy(maxSize, defaultTtl, lruConfig))
                cacheManager
            }

            "lfu" -> {
                val lfuConfig = config.getJsonObject("lfu", JsonObject())
                val maxSize = lfuConfig.getInteger("maxSize", 10000)
                val defaultTtl = lfuConfig.getLong("defaultTtl", 3600)

                createLFUCacheStrategy(maxSize, defaultTtl, lfuConfig)

                // 返回内存缓存管理器，使用 LFU 策略
                val cacheManager = MemoryCacheManager(vertx)
                cacheManager.setCacheStrategy(createLFUCacheStrategy(maxSize, defaultTtl, lfuConfig))
                cacheManager
            }

            else -> {
                logger.warn("Unknown cache type: $cacheType, falling back to memory cache")
                createMemoryCache(vertx)
            }
        }
    }
}
