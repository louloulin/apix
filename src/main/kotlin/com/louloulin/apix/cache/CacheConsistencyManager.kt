package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 缓存一致性管理器，负责确保分布式环境下的缓存一致性。
 */
class CacheConsistencyManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(CacheConsistencyManager::class.java)
    
    // 缓存一致性是否启用
    private val consistencyEnabled = AtomicBoolean(true)
    
    // 缓存一致性策略
    private val consistencyStrategy = AtomicReference<String>("eventual")
    
    // 缓存版本号
    private val cacheVersions = ConcurrentHashMap<String, AtomicLong>()
    
    // 缓存锁
    private val cacheLocks = ConcurrentHashMap<String, AtomicBoolean>()
    
    // 缓存命名空间
    private val cacheNamespace = AtomicReference<String>("apix")
    
    // 多级缓存管理器
    private lateinit var cacheManager: MultiLevelCacheManager
    
    /**
     * 初始化缓存一致性管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化缓存一致性管理器")
        
        // 获取缓存配置
        val cacheConfig = config.getJsonObject("cache", JsonObject())
        val consistencyConfig = cacheConfig.getJsonObject("consistency", JsonObject())
        
        // 更新配置
        consistencyEnabled.set(consistencyConfig.getBoolean("enabled", true))
        consistencyStrategy.set(consistencyConfig.getString("strategy", "eventual"))
        cacheNamespace.set(cacheConfig.getString("namespace", "apix"))
        
        // 获取多级缓存管理器
        cacheManager = MultiLevelCacheManager.getInstance(vertx)
        
        // 如果一致性未启用，直接返回
        if (!consistencyEnabled.get()) {
            logger.info("缓存一致性未启用")
            return Future.succeededFuture()
        }
        
        // 注册缓存一致性事件处理器
        registerConsistencyHandlers()
        
        logger.info("缓存一致性管理器初始化完成，策略: ${consistencyStrategy.get()}")
        return Future.succeededFuture()
    }
    
    /**
     * 注册缓存一致性事件处理器。
     */
    private fun registerConsistencyHandlers() {
        // 监听缓存更新事件
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.cache.update") { message ->
            val key = message.body().getString("key")
            val namespace = message.body().getString("namespace", cacheNamespace.get())
            val version = message.body().getLong("version", 0)
            
            if (key != null) {
                // 更新缓存版本号
                updateCacheVersion(key, namespace, version)
                
                logger.debug("收到缓存更新事件: key=$key, namespace=$namespace, version=$version")
            }
        }
        
        // 监听缓存失效事件
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.cache.invalidate") { message ->
            val key = message.body().getString("key")
            val namespace = message.body().getString("namespace", cacheNamespace.get())
            val version = message.body().getLong("version", 0)
            
            if (key != null) {
                // 更新缓存版本号
                updateCacheVersion(key, namespace, version)
                
                // 从本地缓存中移除
                invalidateLocalCache(key, namespace)
                
                logger.debug("收到缓存失效事件: key=$key, namespace=$namespace, version=$version")
            }
        }
        
        // 监听缓存清空事件
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.cache.clear") { message ->
            val namespace = message.body().getString("namespace", cacheNamespace.get())
            val version = message.body().getLong("version", 0)
            
            // 清空本地缓存
            clearLocalCache(namespace)
            
            logger.debug("收到缓存清空事件: namespace=$namespace, version=$version")
        }
    }
    
    /**
     * 更新缓存版本号。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @param version 版本号
     */
    private fun updateCacheVersion(key: String, namespace: String, version: Long) {
        val versionKey = "$namespace:$key"
        val currentVersion = cacheVersions.computeIfAbsent(versionKey) { AtomicLong(0) }
        
        // 如果新版本号大于当前版本号，更新版本号
        var updated = false
        while (!updated) {
            val current = currentVersion.get()
            if (version > current) {
                updated = currentVersion.compareAndSet(current, version)
            } else {
                break
            }
        }
    }
    
    /**
     * 使本地缓存中的键失效。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     */
    private fun invalidateLocalCache(key: String, namespace: String) {
        // 从多级缓存管理器中移除
        cacheManager.remove(key, namespace)
    }
    
    /**
     * 清空本地缓存。
     * 
     * @param namespace 命名空间
     */
    private fun clearLocalCache(namespace: String) {
        // 清空多级缓存管理器中的缓存
        cacheManager.clear(namespace)
    }
    
    /**
     * 获取缓存锁。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @return 是否获取成功
     */
    fun acquireLock(key: String, namespace: String = cacheNamespace.get()): Boolean {
        // 如果一致性未启用或策略为最终一致性，直接返回成功
        if (!consistencyEnabled.get() || consistencyStrategy.get() == "eventual") {
            return true
        }
        
        val lockKey = "$namespace:$key"
        val lock = cacheLocks.computeIfAbsent(lockKey) { AtomicBoolean(false) }
        
        // 尝试获取锁
        return lock.compareAndSet(false, true)
    }
    
    /**
     * 释放缓存锁。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     */
    fun releaseLock(key: String, namespace: String = cacheNamespace.get()) {
        // 如果一致性未启用或策略为最终一致性，直接返回
        if (!consistencyEnabled.get() || consistencyStrategy.get() == "eventual") {
            return
        }
        
        val lockKey = "$namespace:$key"
        val lock = cacheLocks[lockKey]
        
        // 释放锁
        lock?.set(false)
    }
    
    /**
     * 检查缓存版本是否最新。
     * 
     * @param key 缓存键
     * @param version 版本号
     * @param namespace 命名空间
     * @return 是否最新
     */
    fun isLatestVersion(key: String, version: Long, namespace: String = cacheNamespace.get()): Boolean {
        // 如果一致性未启用或策略为最终一致性，直接返回成功
        if (!consistencyEnabled.get() || consistencyStrategy.get() == "eventual") {
            return true
        }
        
        val versionKey = "$namespace:$key"
        val currentVersion = cacheVersions[versionKey]?.get() ?: 0
        
        return version >= currentVersion
    }
    
    /**
     * 发布缓存更新事件。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @return 新的版本号
     */
    fun publishUpdateEvent(key: String, namespace: String = cacheNamespace.get()): Long {
        // 如果一致性未启用，直接返回 0
        if (!consistencyEnabled.get()) {
            return 0
        }
        
        val versionKey = "$namespace:$key"
        val currentVersion = cacheVersions.computeIfAbsent(versionKey) { AtomicLong(0) }
        val newVersion = currentVersion.incrementAndGet()
        
        // 发布缓存更新事件
        vertx.eventBus().publish("${namespace}.cache.update", JsonObject()
            .put("key", key)
            .put("namespace", namespace)
            .put("version", newVersion)
        )
        
        return newVersion
    }
    
    /**
     * 发布缓存失效事件。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @return 新的版本号
     */
    fun publishInvalidateEvent(key: String, namespace: String = cacheNamespace.get()): Long {
        // 如果一致性未启用，直接返回 0
        if (!consistencyEnabled.get()) {
            return 0
        }
        
        val versionKey = "$namespace:$key"
        val currentVersion = cacheVersions.computeIfAbsent(versionKey) { AtomicLong(0) }
        val newVersion = currentVersion.incrementAndGet()
        
        // 发布缓存失效事件
        vertx.eventBus().publish("${namespace}.cache.invalidate", JsonObject()
            .put("key", key)
            .put("namespace", namespace)
            .put("version", newVersion)
        )
        
        return newVersion
    }
    
    /**
     * 发布缓存清空事件。
     * 
     * @param namespace 命名空间
     * @return 新的版本号
     */
    fun publishClearEvent(namespace: String = cacheNamespace.get()): Long {
        // 如果一致性未启用，直接返回 0
        if (!consistencyEnabled.get()) {
            return 0
        }
        
        val versionKey = "$namespace:*"
        val currentVersion = cacheVersions.computeIfAbsent(versionKey) { AtomicLong(0) }
        val newVersion = currentVersion.incrementAndGet()
        
        // 发布缓存清空事件
        vertx.eventBus().publish("${namespace}.cache.clear", JsonObject()
            .put("namespace", namespace)
            .put("version", newVersion)
        )
        
        return newVersion
    }
    
    /**
     * 获取缓存一致性管理器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        return JsonObject()
            .put("enabled", consistencyEnabled.get())
            .put("strategy", consistencyStrategy.get())
            .put("namespace", cacheNamespace.get())
            .put("locksCount", cacheLocks.size)
            .put("versionsCount", cacheVersions.size)
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: CacheConsistencyManager? = null
        
        /**
         * 获取 CacheConsistencyManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return CacheConsistencyManager 实例
         */
        fun getInstance(vertx: Vertx): CacheConsistencyManager {
            return instance ?: synchronized(this) {
                instance ?: CacheConsistencyManager(vertx).also { instance = it }
            }
        }
    }
}
