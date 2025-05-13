package com.louloulin.apix.cache

import com.louloulin.apix.cache.semantic.SemanticCacheFactory
import com.louloulin.apix.cache.semantic.SemanticCacheManager
import com.louloulin.apix.cache.strategy.CacheStrategy
import com.louloulin.apix.cache.strategy.CacheStrategyFactory
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 缓存服务，提供高级缓存功能
 */
class CacheService(
    private val vertx: Vertx,
    private val cacheManager: CacheManager,
    private val cacheStrategy: CacheStrategy
) {
    private val logger = LoggerFactory.getLogger(CacheService::class.java)
    
    /**
     * 获取缓存项
     * @param key 缓存键
     * @return 缓存值的 Future
     */
    fun get(key: String): Future<JsonObject?> {
        return cacheManager.get(key)
    }
    
    /**
     * 根据查询文本获取缓存
     * @param query 查询文本
     * @return 缓存值的 Future
     */
    fun getByQuery(query: String): Future<JsonObject?> {
        // 如果缓存管理器是语义缓存管理器，使用语义查询
        if (cacheManager is SemanticCacheManager) {
            return cacheManager.getByQuery(query)
        } else {
            // 否则，使用普通查询
            val cacheKey = "query:$query"
            return cacheManager.get(cacheKey)
                .map { value ->
                    if (value != null) {
                        value.getJsonObject("response")
                    } else {
                        null
                    }
                }
        }
    }
    
    /**
     * 设置缓存项
     * @param key 缓存键
     * @param value 缓存值
     * @return 操作结果的 Future
     */
    fun set(key: String, value: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 检查是否应该缓存
        if (cacheStrategy.shouldCache(key, value)) {
            // 计算 TTL
            val ttl = cacheStrategy.calculateTtl(key, value)
            
            // 设置缓存
            cacheManager.set(key, value, ttl)
                .onSuccess {
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("Error setting cache entry for key: $key", err)
                    promise.fail(err)
                }
        } else {
            // 不应该缓存
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 根据查询文本设置缓存
     * @param query 查询文本
     * @param response 响应
     * @return 操作结果的 Future
     */
    fun setByQuery(query: String, response: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 创建缓存条目
        val cacheEntry = JsonObject()
            .put("query", query)
            .put("response", response)
            .put("timestamp", System.currentTimeMillis())
        
        // 检查是否应该缓存
        if (cacheStrategy.shouldCache("query:$query", cacheEntry)) {
            // 计算 TTL
            val ttl = cacheStrategy.calculateTtl("query:$query", cacheEntry)
            
            // 如果缓存管理器是语义缓存管理器，使用语义缓存
            if (cacheManager is SemanticCacheManager) {
                cacheManager.setByQuery(query, response, ttl)
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("Error setting cache entry for query: $query", err)
                        promise.fail(err)
                    }
            } else {
                // 否则，使用普通缓存
                val cacheKey = "query:$query"
                cacheManager.set(cacheKey, cacheEntry, ttl)
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("Error setting cache entry for query: $query", err)
                        promise.fail(err)
                    }
            }
        } else {
            // 不应该缓存
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 删除缓存项
     * @param key 缓存键
     * @return 操作结果的 Future
     */
    fun remove(key: String): Future<Void> {
        return cacheManager.remove(key)
    }
    
    /**
     * 清空所有缓存
     * @return 操作结果的 Future
     */
    fun clear(): Future<Void> {
        return cacheManager.clear()
    }
    
    /**
     * 获取缓存统计信息
     * @return 缓存统计信息的 Future
     */
    fun getStats(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        cacheManager.getStats()
            .onSuccess { stats ->
                // 添加策略信息
                val enhancedStats = stats.copy()
                    .put("strategy", cacheStrategy.getName())
                
                promise.complete(enhancedStats)
            }
            .onFailure { err ->
                logger.error("Error getting cache stats", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 关闭缓存服务
     * @return 操作结果的 Future
     */
    fun close(): Future<Void> {
        return cacheManager.close()
    }
    
    companion object {
        /**
         * 从配置创建缓存服务
         * @param vertx Vertx 实例
         * @param config 缓存配置
         * @return 缓存服务
         */
        fun createFromConfig(vertx: Vertx, config: JsonObject): CacheService {
            val logger = LoggerFactory.getLogger(CacheService::class.java)
            
            // 创建缓存管理器
            val cacheType = config.getString("type", "memory")
            val cacheManager = when (cacheType.lowercase()) {
                "semantic" -> {
                    val semanticConfig = config.getJsonObject("semantic", JsonObject())
                    SemanticCacheFactory.createFromConfig(vertx, semanticConfig)
                }
                else -> {
                    CacheFactory.createFromConfig(vertx, config)
                }
            }
            
            // 创建缓存策略
            val strategyConfig = config.getJsonObject("strategy", JsonObject())
            val cacheStrategy = CacheStrategyFactory.createFromConfig(strategyConfig)
            
            return CacheService(vertx, cacheManager, cacheStrategy)
        }
    }
}
