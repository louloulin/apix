package com.louloulin.apix.cache

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

                createRedisCache(vertx, redisOptions, keyPrefix)
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

            else -> {
                logger.warn("Unknown cache type: $cacheType, falling back to memory cache")
                createMemoryCache(vertx)
            }
        }
    }
}
