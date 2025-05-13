package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject

/**
 * TTL 缓存策略，根据配置的过期时间进行缓存
 */
class TtlCacheStrategy(
    private val defaultTtl: Long = 3600, // 默认过期时间（秒）
    private val keyPatternTtls: Map<Regex, Long> = emptyMap() // 根据键模式配置的过期时间
) : CacheStrategy {
    
    override fun calculateTtl(key: String, value: JsonObject): Long {
        // 根据键模式查找匹配的过期时间
        for ((pattern, ttl) in keyPatternTtls) {
            if (pattern.matches(key)) {
                return ttl
            }
        }
        
        // 如果没有匹配的模式，返回默认过期时间
        return defaultTtl
    }
    
    override fun shouldCache(key: String, value: JsonObject): Boolean {
        // 默认所有内容都应该被缓存
        return true
    }
    
    override fun getName(): String {
        return "TTL"
    }
}
