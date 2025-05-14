package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 缓存策略工厂类，用于创建不同类型的缓存策略
 */
object CacheStrategyFactory {
    private val logger = LoggerFactory.getLogger(CacheStrategyFactory::class.java)

    /**
     * 创建 TTL 缓存策略
     * @param defaultTtl 默认过期时间（秒）
     * @param keyPatternTtls 根据键模式配置的过期时间
     * @return TTL 缓存策略
     */
    fun createTtlStrategy(
        defaultTtl: Long = 3600,
        keyPatternTtls: Map<Regex, Long> = emptyMap()
    ): CacheStrategy {
        return TtlCacheStrategy(defaultTtl, keyPatternTtls)
    }

    /**
     * 创建模型特定的缓存策略
     * @param modelTtls 模型名称到过期时间的映射
     * @param defaultTtl 默认过期时间（秒）
     * @return 模型特定的缓存策略
     */
    fun createModelSpecificStrategy(
        modelTtls: Map<String, Long> = emptyMap(),
        defaultTtl: Long = 3600
    ): CacheStrategy {
        return ModelSpecificCacheStrategy(modelTtls, defaultTtl)
    }

    /**
     * 创建用户特定的缓存策略
     * @param userTtls 用户 ID 到过期时间的映射
     * @param userGroupTtls 用户组到过期时间的映射
     * @param defaultTtl 默认过期时间（秒）
     * @return 用户特定的缓存策略
     */
    fun createUserSpecificStrategy(
        userTtls: Map<String, Long> = emptyMap(),
        userGroupTtls: Map<String, Long> = emptyMap(),
        defaultTtl: Long = 3600
    ): CacheStrategy {
        return UserSpecificCacheStrategy(userTtls, userGroupTtls, defaultTtl)
    }

    /**
     * 创建组合缓存策略
     * @param strategies 缓存策略列表
     * @param ttlSelectionMode TTL 选择模式
     * @return 组合缓存策略
     */
    fun createCompositeStrategy(
        strategies: List<CacheStrategy>,
        ttlSelectionMode: CompositeCacheStrategy.TtlSelectionMode = CompositeCacheStrategy.TtlSelectionMode.MIN
    ): CacheStrategy {
        return CompositeCacheStrategy(strategies, ttlSelectionMode)
    }

    /**
     * 创建智能TTL缓存策略
     * @param defaultTtl 默认TTL（秒）
     * @param minTtl 最小TTL（秒）
     * @param maxTtl 最大TTL（秒）
     * @param learningRate 学习率
     * @param modelWeights 模型权重
     * @param queryTypeWeights 查询类型权重
     * @return 智能TTL缓存策略
     */
    fun createSmartTTLStrategy(
        defaultTtl: Long = 3600,
        minTtl: Long = 60,
        maxTtl: Long = 86400,
        learningRate: Double = 0.1,
        modelWeights: Map<String, Double> = emptyMap(),
        queryTypeWeights: Map<String, Double> = emptyMap()
    ): CacheStrategy {
        return SmartTTLCacheStrategy(
            defaultTtl,
            minTtl,
            maxTtl,
            learningRate,
            modelWeights,
            queryTypeWeights
        )
    }

    /**
     * 从配置创建缓存策略
     * @param config 缓存策略配置
     * @return 缓存策略
     */
    fun createFromConfig(config: JsonObject): CacheStrategy {
        val strategyType = config.getString("type", "ttl")

        return when (strategyType.lowercase()) {
            "ttl" -> {
                val defaultTtl = config.getLong("defaultTtl", 3600)
                val keyPatternTtls = mutableMapOf<Regex, Long>()

                val patterns = config.getJsonObject("keyPatternTtls")
                if (patterns != null) {
                    for (key in patterns.fieldNames()) {
                        val ttl = patterns.getLong(key)
                        if (ttl != null) {
                            try {
                                val regex = Regex(key)
                                keyPatternTtls[regex] = ttl
                            } catch (e: Exception) {
                                logger.warn("Invalid regex pattern: $key", e)
                            }
                        }
                    }
                }

                createTtlStrategy(defaultTtl, keyPatternTtls)
            }

            "model" -> {
                val defaultTtl = config.getLong("defaultTtl", 3600)
                val modelTtls = mutableMapOf<String, Long>()

                val models = config.getJsonObject("modelTtls")
                if (models != null) {
                    for (key in models.fieldNames()) {
                        val ttl = models.getLong(key)
                        if (ttl != null) {
                            modelTtls[key] = ttl
                        }
                    }
                }

                createModelSpecificStrategy(modelTtls, defaultTtl)
            }

            "user" -> {
                val defaultTtl = config.getLong("defaultTtl", 3600)
                val userTtls = mutableMapOf<String, Long>()
                val userGroupTtls = mutableMapOf<String, Long>()

                val users = config.getJsonObject("userTtls")
                if (users != null) {
                    for (key in users.fieldNames()) {
                        val ttl = users.getLong(key)
                        if (ttl != null) {
                            userTtls[key] = ttl
                        }
                    }
                }

                val userGroups = config.getJsonObject("userGroupTtls")
                if (userGroups != null) {
                    for (key in userGroups.fieldNames()) {
                        val ttl = userGroups.getLong(key)
                        if (ttl != null) {
                            userGroupTtls[key] = ttl
                        }
                    }
                }

                createUserSpecificStrategy(userTtls, userGroupTtls, defaultTtl)
            }

            "smartttl", "smart_ttl", "smart" -> {
                val defaultTtl = config.getLong("defaultTtl", 3600)
                val minTtl = config.getLong("minTtl", 60)
                val maxTtl = config.getLong("maxTtl", 86400)
                val learningRate = config.getDouble("learningRate", 0.1)

                // 解析模型权重
                val modelWeights = mutableMapOf<String, Double>()
                val modelWeightsObj = config.getJsonObject("modelWeights")
                if (modelWeightsObj != null) {
                    for (key in modelWeightsObj.fieldNames()) {
                        val weight = modelWeightsObj.getDouble(key)
                        if (weight != null) {
                            modelWeights[key] = weight
                        }
                    }
                }

                // 解析查询类型权重
                val queryTypeWeights = mutableMapOf<String, Double>()
                val queryTypeWeightsObj = config.getJsonObject("queryTypeWeights")
                if (queryTypeWeightsObj != null) {
                    for (key in queryTypeWeightsObj.fieldNames()) {
                        val weight = queryTypeWeightsObj.getDouble(key)
                        if (weight != null) {
                            queryTypeWeights[key] = weight
                        }
                    }
                }

                createSmartTTLStrategy(
                    defaultTtl,
                    minTtl,
                    maxTtl,
                    learningRate,
                    modelWeights,
                    queryTypeWeights
                )
            }

            "composite" -> {
                val strategiesConfig = config.getJsonArray("strategies")
                val strategies = mutableListOf<CacheStrategy>()

                if (strategiesConfig != null) {
                    for (i in 0 until strategiesConfig.size()) {
                        val strategyConfig = strategiesConfig.getJsonObject(i)
                        if (strategyConfig != null) {
                            strategies.add(createFromConfig(strategyConfig))
                        }
                    }
                }

                val ttlSelectionModeStr = config.getString("ttlSelectionMode", "MIN")
                val ttlSelectionMode = try {
                    CompositeCacheStrategy.TtlSelectionMode.valueOf(ttlSelectionModeStr.uppercase())
                } catch (e: Exception) {
                    logger.warn("Invalid TTL selection mode: $ttlSelectionModeStr, falling back to MIN", e)
                    CompositeCacheStrategy.TtlSelectionMode.MIN
                }

                createCompositeStrategy(strategies, ttlSelectionMode)
            }

            else -> {
                logger.warn("Unknown cache strategy type: $strategyType, falling back to TTL strategy")
                createTtlStrategy()
            }
        }
    }
}
