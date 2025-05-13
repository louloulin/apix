package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject

/**
 * 缓存策略接口，定义了缓存的过期策略
 */
interface CacheStrategy {
    /**
     * 计算缓存项的过期时间（秒）
     * @param key 缓存键
     * @param value 缓存值
     * @return 过期时间（秒），0 表示永不过期
     */
    fun calculateTtl(key: String, value: JsonObject): Long
    
    /**
     * 判断缓存项是否应该被缓存
     * @param key 缓存键
     * @param value 缓存值
     * @return 是否应该被缓存
     */
    fun shouldCache(key: String, value: JsonObject): Boolean
    
    /**
     * 获取策略名称
     * @return 策略名称
     */
    fun getName(): String
}
