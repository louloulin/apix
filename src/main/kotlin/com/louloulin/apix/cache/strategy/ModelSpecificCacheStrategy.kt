package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject

/**
 * 模型特定的缓存策略，根据 AI 模型的特性配置缓存策略
 */
class ModelSpecificCacheStrategy(
    private val modelTtls: Map<String, Long> = emptyMap(), // 模型名称到过期时间的映射
    private val defaultTtl: Long = 3600 // 默认过期时间（秒）
) : CacheStrategy {
    
    override fun calculateTtl(key: String, value: JsonObject): Long {
        // 从缓存值中提取模型名称
        val modelName = extractModelName(value)
        
        // 根据模型名称查找匹配的过期时间
        return if (modelName != null && modelTtls.containsKey(modelName)) {
            modelTtls[modelName] ?: defaultTtl
        } else {
            defaultTtl
        }
    }
    
    override fun shouldCache(key: String, value: JsonObject): Boolean {
        // 从缓存值中提取模型名称
        val modelName = extractModelName(value)
        
        // 如果模型名称存在于配置中，则应该被缓存
        return modelName != null && modelTtls.containsKey(modelName)
    }
    
    override fun getName(): String {
        return "ModelSpecific"
    }
    
    /**
     * 从缓存值中提取模型名称
     * @param value 缓存值
     * @return 模型名称，如果不存在则返回 null
     */
    private fun extractModelName(value: JsonObject): String? {
        // 尝试从不同的字段中提取模型名称
        return value.getString("model") ?:
               value.getJsonObject("metadata")?.getString("model") ?:
               value.getJsonObject("request")?.getString("model") ?:
               value.getJsonObject("response")?.getString("model")
    }
}
