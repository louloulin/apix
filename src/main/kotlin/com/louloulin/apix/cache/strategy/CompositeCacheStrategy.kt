package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject

/**
 * 组合缓存策略，组合多个缓存策略
 */
class CompositeCacheStrategy(
    private val strategies: List<CacheStrategy>,
    private val ttlSelectionMode: TtlSelectionMode = TtlSelectionMode.MIN
) : CacheStrategy {
    
    /**
     * TTL 选择模式
     */
    enum class TtlSelectionMode {
        MIN, // 使用最小的 TTL
        MAX, // 使用最大的 TTL
        AVERAGE // 使用平均 TTL
    }
    
    override fun calculateTtl(key: String, value: JsonObject): Long {
        // 如果没有策略，返回 0（永不过期）
        if (strategies.isEmpty()) {
            return 0
        }
        
        // 计算所有策略的 TTL
        val ttls = strategies.map { it.calculateTtl(key, value) }
        
        // 根据选择模式返回 TTL
        return when (ttlSelectionMode) {
            TtlSelectionMode.MIN -> ttls.minOrNull() ?: 0
            TtlSelectionMode.MAX -> ttls.maxOrNull() ?: 0
            TtlSelectionMode.AVERAGE -> {
                if (ttls.isEmpty()) 0 else ttls.sum() / ttls.size
            }
        }
    }
    
    override fun shouldCache(key: String, value: JsonObject): Boolean {
        // 如果没有策略，返回 true
        if (strategies.isEmpty()) {
            return true
        }
        
        // 如果任何一个策略返回 false，则不应该被缓存
        return strategies.all { it.shouldCache(key, value) }
    }
    
    override fun getName(): String {
        return "Composite(${strategies.joinToString(", ") { it.getName() }})"
    }
}
