package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.json.JsonObject

/**
 * 缓存管理器接口，定义了缓存的基本操作
 */
interface CacheManager {
    /**
     * 获取缓存项
     * @param key 缓存键
     * @return 缓存值的 Future
     */
    fun get(key: String): Future<JsonObject?>
    
    /**
     * 设置缓存项
     * @param key 缓存键
     * @param value 缓存值
     * @param ttlSeconds 过期时间（秒），默认为 0（永不过期）
     * @return 操作结果的 Future
     */
    fun set(key: String, value: JsonObject, ttlSeconds: Long = 0): Future<Void>
    
    /**
     * 删除缓存项
     * @param key 缓存键
     * @return 操作结果的 Future
     */
    fun remove(key: String): Future<Void>
    
    /**
     * 清空所有缓存
     * @return 操作结果的 Future
     */
    fun clear(): Future<Void>
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计信息的 Future
     */
    fun getStats(): Future<JsonObject>
    
    /**
     * 关闭缓存管理器
     * @return 操作结果的 Future
     */
    fun close(): Future<Void>
}
