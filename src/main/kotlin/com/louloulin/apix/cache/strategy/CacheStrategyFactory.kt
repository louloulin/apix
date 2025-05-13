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
