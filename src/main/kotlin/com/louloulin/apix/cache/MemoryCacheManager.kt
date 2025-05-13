package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于内存的缓存管理器实现
 */
class MemoryCacheManager(private val vertx: Vertx) : CacheManager {
    private val logger = LoggerFactory.getLogger(MemoryCacheManager::class.java)
    
    // 缓存存储
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    
    // 缓存统计
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val sets = AtomicLong(0)
    private val removes = AtomicLong(0)
    
    // 缓存条目类
    private data class CacheEntry(
        val value: JsonObject,
        val expirationTime: Long // 0 表示永不过期
    )
    
    init {
        // 启动定期清理过期缓存的任务
        startCleanupTask()
    }
    
    /**
     * 启动定期清理过期缓存的任务
     */
    private fun startCleanupTask() {
        val cleanupIntervalMs = 60000L // 每分钟清理一次
        
        vertx.setPeriodic(cleanupIntervalMs) { _ ->
            try {
                val now = System.currentTimeMillis()
                val expiredKeys = cache.entries
                    .filter { it.value.expirationTime > 0 && it.value.expirationTime <= now }
                    .map { it.key }
                
                expiredKeys.forEach { cache.remove(it) }
                
                if (expiredKeys.isNotEmpty()) {
                    logger.debug("Cleaned up ${expiredKeys.size} expired cache entries")
                }
            } catch (e: Exception) {
                logger.error("Error during cache cleanup", e)
            }
        }
    }
    
    override fun get(key: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        
        try {
            val entry = cache[key]
            
            if (entry != null) {
                val now = System.currentTimeMillis()
                
                // 检查是否过期
                if (entry.expirationTime == 0L || entry.expirationTime > now) {
                    hits.incrementAndGet()
                    promise.complete(entry.value)
                } else {
                    // 已过期，删除并返回 null
                    cache.remove(key)
                    misses.incrementAndGet()
                    promise.complete(null)
                }
            } else {
                misses.incrementAndGet()
                promise.complete(null)
            }
        } catch (e: Exception) {
            logger.error("Error getting cache entry for key: $key", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun set(key: String, value: JsonObject, ttlSeconds: Long): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            val expirationTime = if (ttlSeconds > 0) {
                System.currentTimeMillis() + (ttlSeconds * 1000)
            } else {
                0L // 永不过期
            }
            
            cache[key] = CacheEntry(value, expirationTime)
            sets.incrementAndGet()
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error setting cache entry for key: $key", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun remove(key: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            cache.remove(key)
            removes.incrementAndGet()
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error removing cache entry for key: $key", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun clear(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            cache.clear()
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error clearing cache", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun getStats(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            val stats = JsonObject()
                .put("size", cache.size)
                .put("hits", hits.get())
                .put("misses", misses.get())
                .put("sets", sets.get())
                .put("removes", removes.get())
                .put("hitRatio", if (hits.get() + misses.get() > 0) {
                    hits.get().toDouble() / (hits.get() + misses.get())
                } else {
                    0.0
                })
            
            promise.complete(stats)
        } catch (e: Exception) {
            logger.error("Error getting cache stats", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            cache.clear()
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error closing cache", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
}
