package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisOptions
import io.vertx.redis.client.Request
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于 Redis 的缓存管理器实现
 */
class RedisCacheManager(
    private val vertx: Vertx,
    private val redisOptions: RedisOptions,
    private val keyPrefix: String = "apix:cache:"
) : CacheManager {
    private val logger = LoggerFactory.getLogger(RedisCacheManager::class.java)
    
    // Redis 客户端
    private val redisClient: Redis = Redis.createClient(vertx, redisOptions)
    private val redis: RedisAPI = RedisAPI.api(redisClient)
    
    // 缓存统计
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val sets = AtomicLong(0)
    private val removes = AtomicLong(0)
    
    override fun get(key: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        val redisKey = keyPrefix + key
        
        redis.get(redisKey)
            .onSuccess { response ->
                if (response != null) {
                    try {
                        val jsonValue = JsonObject(response.toString())
                        hits.incrementAndGet()
                        promise.complete(jsonValue)
                    } catch (e: Exception) {
                        logger.error("Error parsing JSON from Redis for key: $key", e)
                        promise.fail(e)
                    }
                } else {
                    misses.incrementAndGet()
                    promise.complete(null)
                }
            }
            .onFailure { err ->
                logger.error("Error getting cache entry from Redis for key: $key", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun set(key: String, value: JsonObject, ttlSeconds: Long): Future<Void> {
        val promise = Promise.promise<Void>()
        val redisKey = keyPrefix + key
        
        try {
            val jsonString = value.encode()
            
            if (ttlSeconds > 0) {
                // 使用 SETEX 命令设置带过期时间的键值对
                redis.setex(redisKey, ttlSeconds.toString(), jsonString)
                    .onSuccess {
                        sets.incrementAndGet()
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("Error setting cache entry in Redis for key: $key", err)
                        promise.fail(err)
                    }
            } else {
                // 使用 SET 命令设置不过期的键值对
                redis.set(listOf(redisKey, jsonString))
                    .onSuccess {
                        sets.incrementAndGet()
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("Error setting cache entry in Redis for key: $key", err)
                        promise.fail(err)
                    }
            }
        } catch (e: Exception) {
            logger.error("Error encoding JSON for Redis for key: $key", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun remove(key: String): Future<Void> {
        val promise = Promise.promise<Void>()
        val redisKey = keyPrefix + key
        
        redis.del(listOf(redisKey))
            .onSuccess {
                removes.incrementAndGet()
                promise.complete()
            }
            .onFailure { err ->
                logger.error("Error removing cache entry from Redis for key: $key", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun clear(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 使用 KEYS 命令查找所有匹配的键（注意：在生产环境中应谨慎使用 KEYS 命令）
        redis.keys("$keyPrefix*")
            .onSuccess { response ->
                if (response != null && response.size() > 0) {
                    val keysList = mutableListOf<String>()
                    
                    // 将响应转换为键列表
                    for (i in 0 until response.size()) {
                        keysList.add(response.get(i).toString())
                    }
                    
                    if (keysList.isNotEmpty()) {
                        redis.del(keysList)
                            .onSuccess {
                                promise.complete()
                            }
                            .onFailure { err ->
                                logger.error("Error deleting keys from Redis", err)
                                promise.fail(err)
                            }
                    } else {
                        promise.complete()
                    }
                } else {
                    promise.complete()
                }
            }
            .onFailure { err ->
                logger.error("Error getting keys from Redis", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun getStats(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 使用 DBSIZE 命令获取数据库大小
        redis.dbsize()
            .onSuccess { response ->
                val dbSize = response.toLong()
                
                val stats = JsonObject()
                    .put("hits", hits.get())
                    .put("misses", misses.get())
                    .put("sets", sets.get())
                    .put("removes", removes.get())
                    .put("hitRatio", if (hits.get() + misses.get() > 0) {
                        hits.get().toDouble() / (hits.get() + misses.get())
                    } else {
                        0.0
                    })
                    .put("dbSize", dbSize)
                
                promise.complete(stats)
            }
            .onFailure { err ->
                logger.error("Error getting Redis stats", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    override fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 关闭 Redis 客户端
            redisClient.close()
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error closing Redis client", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
}
