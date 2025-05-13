package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 多级缓存管理器，组合多个缓存管理器形成缓存层级
 */
class MultiLevelCacheManager(
    private val vertx: Vertx,
    private val cacheManagers: List<CacheManager>
) : CacheManager {
    private val logger = LoggerFactory.getLogger(MultiLevelCacheManager::class.java)
    
    // 确保至少有一个缓存管理器
    init {
        require(cacheManagers.isNotEmpty()) { "At least one cache manager is required" }
    }
    
    override fun get(key: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        
        // 从第一级缓存开始查找
        getFromLevel(key, 0, promise)
        
        return promise.future()
    }
    
    /**
     * 从指定级别的缓存中获取数据，如果没有找到则继续查找下一级
     */
    private fun getFromLevel(key: String, level: Int, promise: Promise<JsonObject?>) {
        if (level >= cacheManagers.size) {
            // 所有级别都没有找到，返回 null
            promise.complete(null)
            return
        }
        
        val cacheManager = cacheManagers[level]
        
        cacheManager.get(key)
            .onSuccess { value ->
                if (value != null) {
                    // 找到了，返回结果
                    promise.complete(value)
                    
                    // 将结果回填到更高级别的缓存中
                    backfillToHigherLevels(key, value, level)
                } else {
                    // 没有找到，继续查找下一级
                    getFromLevel(key, level + 1, promise)
                }
            }
            .onFailure { err ->
                logger.error("Error getting from cache level $level for key: $key", err)
                // 出错了，继续查找下一级
                getFromLevel(key, level + 1, promise)
            }
    }
    
    /**
     * 将结果回填到更高级别的缓存中
     */
    private fun backfillToHigherLevels(key: String, value: JsonObject, foundLevel: Int) {
        for (level in 0 until foundLevel) {
            cacheManagers[level].set(key, value)
                .onFailure { err ->
                    logger.error("Error backfilling to cache level $level for key: $key", err)
                }
        }
    }
    
    override fun set(key: String, value: JsonObject, ttlSeconds: Long): Future<Void> {
        val promise = Promise.promise<Void>()
        var succeeded = true
        var failureCount = 0
        
        // 设置所有级别的缓存
        val futures = cacheManagers.map { it.set(key, value, ttlSeconds) }
        
        // 等待所有设置操作完成
        Future.join(futures)
            .onComplete {
                if (it.succeeded()) {
                    promise.complete()
                } else {
                    // 只要有一个成功，我们就认为整体成功
                    for (future in futures) {
                        if (future.failed()) {
                            failureCount++
                        }
                    }
                    
                    if (failureCount == cacheManagers.size) {
                        // 所有级别都失败了
                        promise.fail("All cache levels failed to set key: $key")
                    } else {
                        // 至少有一个级别成功了
                        promise.complete()
                    }
                }
            }
        
        return promise.future()
    }
    
    override fun remove(key: String): Future<Void> {
        val promise = Promise.promise<Void>()
        var failureCount = 0
        
        // 从所有级别删除
        val futures = cacheManagers.map { it.remove(key) }
        
        // 等待所有删除操作完成
        Future.join(futures)
            .onComplete {
                if (it.succeeded()) {
                    promise.complete()
                } else {
                    // 只要有一个成功，我们就认为整体成功
                    for (future in futures) {
                        if (future.failed()) {
                            failureCount++
                        }
                    }
                    
                    if (failureCount == cacheManagers.size) {
                        // 所有级别都失败了
                        promise.fail("All cache levels failed to remove key: $key")
                    } else {
                        // 至少有一个级别成功了
                        promise.complete()
                    }
                }
            }
        
        return promise.future()
    }
    
    override fun clear(): Future<Void> {
        val promise = Promise.promise<Void>()
        var failureCount = 0
        
        // 清空所有级别
        val futures = cacheManagers.map { it.clear() }
        
        // 等待所有清空操作完成
        Future.join(futures)
            .onComplete {
                if (it.succeeded()) {
                    promise.complete()
                } else {
                    // 只要有一个成功，我们就认为整体成功
                    for (future in futures) {
                        if (future.failed()) {
                            failureCount++
                        }
                    }
                    
                    if (failureCount == cacheManagers.size) {
                        // 所有级别都失败了
                        promise.fail("All cache levels failed to clear")
                    } else {
                        // 至少有一个级别成功了
                        promise.complete()
                    }
                }
            }
        
        return promise.future()
    }
    
    override fun getStats(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 获取所有级别的统计信息
        val futures = cacheManagers.mapIndexed { index, cacheManager ->
            cacheManager.getStats().map { stats -> Pair(index, stats) }
        }
        
        // 等待所有统计信息获取操作完成
        Future.join(futures)
            .onSuccess {
                val combinedStats = JsonObject()
                
                // 合并所有级别的统计信息
                futures.forEach { future ->
                    if (future.succeeded()) {
                        val (level, stats) = future.result()
                        combinedStats.put("level$level", stats)
                    }
                }
                
                promise.complete(combinedStats)
            }
            .onFailure { err ->
                logger.error("Error getting stats from some cache levels", err)
                
                // 尝试获取成功的统计信息
                val partialStats = JsonObject()
                
                futures.forEach { future ->
                    if (future.succeeded()) {
                        val (level, stats) = future.result()
                        partialStats.put("level$level", stats)
                    }
                }
                
                if (partialStats.isEmpty) {
                    promise.fail(err)
                } else {
                    // 返回部分统计信息
                    partialStats.put("partial", true)
                    partialStats.put("error", err.message)
                    promise.complete(partialStats)
                }
            }
        
        return promise.future()
    }
    
    override fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        var failureCount = 0
        
        // 关闭所有级别
        val futures = cacheManagers.map { it.close() }
        
        // 等待所有关闭操作完成
        Future.join(futures)
            .onComplete {
                if (it.succeeded()) {
                    promise.complete()
                } else {
                    // 只要有一个成功，我们就认为整体成功
                    for (future in futures) {
                        if (future.failed()) {
                            failureCount++
                        }
                    }
                    
                    if (failureCount == cacheManagers.size) {
                        // 所有级别都失败了
                        promise.fail("All cache levels failed to close")
                    } else {
                        // 至少有一个级别成功了
                        promise.complete()
                    }
                }
            }
        
        return promise.future()
    }
}
