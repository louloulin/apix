package com.louloulin.apix.cache

import io.vertx.core.json.JsonObject

/**
 * 缓存策略接口，定义缓存行为
 */
interface CacheStrategy {
    /**
     * 获取策略名称
     * @return 策略名称
     */
    fun getName(): String
    /**
     * 判断是否应该缓存指定的键值对
     * @param key 缓存键
     * @param value 缓存值
     * @return 是否应该缓存
     */
    fun shouldCache(key: String, value: JsonObject): Boolean

    /**
     * 计算缓存项的TTL（生存时间）
     * @param key 缓存键
     * @param value 缓存值
     * @return TTL（秒），0表示永不过期
     */
    fun calculateTtl(key: String, value: JsonObject): Long

    /**
     * 缓存命中时的回调
     * @param key 缓存键
     */
    fun onCacheHit(key: String)

    /**
     * 缓存未命中时的回调
     * @param key 缓存键
     */
    fun onCacheMiss(key: String)

    /**
     * 设置缓存时的回调
     * @param key 缓存键
     * @param value 缓存值
     */
    fun onCacheSet(key: String, value: JsonObject)

    /**
     * 移除缓存时的回调
     * @param key 缓存键
     */
    fun onCacheRemove(key: String)

    /**
     * 获取策略统计信息
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject
}
