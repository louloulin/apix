package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 缓存穿透防护管理器，用于防止缓存穿透、缓存击穿和缓存雪崩。
 */
class CacheProtectionManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(CacheProtectionManager::class.java)
    
    // 是否启用缓存穿透防护
    private val penetrationProtectionEnabled = AtomicBoolean(true)
    
    // 是否启用缓存击穿防护
    private val breakdownProtectionEnabled = AtomicBoolean(true)
    
    // 是否启用缓存雪崩防护
    private val avalancheProtectionEnabled = AtomicBoolean(true)
    
    // 空值缓存过期时间（毫秒）
    private val nullValueTTL = AtomicLong(60000) // 1 分钟
    
    // 互斥锁过期时间（毫秒）
    private val mutexLockTimeout = AtomicLong(5000) // 5 秒
    
    // 随机过期时间偏移量（毫秒）
    private val randomExpiryOffset = AtomicLong(300000) // 5 分钟
    
    // 缓存命名空间
    private val cacheNamespace = AtomicReference<String>("apix")
    
    // 多级缓存管理器
    private lateinit var cacheManager: MultiLevelCacheManager
    
    // 布隆过滤器管理器
    private lateinit var bloomFilterManager: BloomFilterManager
    
    // 互斥锁
    private val mutexLocks = ConcurrentHashMap<String, MutexLock>()
    
    // 缓存穿透计数器
    private val penetrationCounters = ConcurrentHashMap<String, AtomicInteger>()
    
    // 缓存击穿计数器
    private val breakdownCounters = ConcurrentHashMap<String, AtomicInteger>()
    
    // 缓存雪崩计数器
    private val avalancheCounter = AtomicInteger(0)
    
    // 缓存雪崩检测阈值
    private val avalancheThreshold = AtomicInteger(1000)
    
    // 缓存雪崩检测时间窗口（毫秒）
    private val avalancheTimeWindow = AtomicLong(1000) // 1 秒
    
    // 缓存雪崩上次检测时间
    private val avalancheLastCheckTime = AtomicLong(0)
    
    /**
     * 初始化缓存穿透防护管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化缓存穿透防护管理器")
        
        // 获取缓存配置
        val cacheConfig = config.getJsonObject("cache", JsonObject())
        val protectionConfig = cacheConfig.getJsonObject("protection", JsonObject())
        
        // 更新配置
        penetrationProtectionEnabled.set(protectionConfig.getBoolean("penetrationEnabled", true))
        breakdownProtectionEnabled.set(protectionConfig.getBoolean("breakdownEnabled", true))
        avalancheProtectionEnabled.set(protectionConfig.getBoolean("avalancheEnabled", true))
        nullValueTTL.set(protectionConfig.getLong("nullValueTTL", 60000))
        mutexLockTimeout.set(protectionConfig.getLong("mutexLockTimeout", 5000))
        randomExpiryOffset.set(protectionConfig.getLong("randomExpiryOffset", 300000))
        avalancheThreshold.set(protectionConfig.getInteger("avalancheThreshold", 1000))
        avalancheTimeWindow.set(protectionConfig.getLong("avalancheTimeWindow", 1000))
        cacheNamespace.set(cacheConfig.getString("cacheNamespace", "apix"))
        
        // 获取多级缓存管理器
        cacheManager = MultiLevelCacheManager.getInstance(vertx)
        
        // 获取布隆过滤器管理器
        bloomFilterManager = BloomFilterManager.getInstance(vertx)
        
        // 启动互斥锁清理任务
        startMutexLockCleanupTask()
        
        logger.info("缓存穿透防护管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 启动互斥锁清理任务。
     */
    private fun startMutexLockCleanupTask() {
        // 获取清理间隔
        val cleanupInterval = 1000L // 1 秒
        
        // 启动定时器
        vertx.setPeriodic(cleanupInterval) { _ ->
            cleanupExpiredMutexLocks()
        }
        
        logger.info("互斥锁清理任务已启动，间隔: $cleanupInterval ms")
    }
    
    /**
     * 清理过期的互斥锁。
     */
    private fun cleanupExpiredMutexLocks() {
        val now = System.currentTimeMillis()
        
        // 清理过期的互斥锁
        val expiredLocks = mutexLocks.entries.filter { (_, lock) -> lock.isExpired(now) }
        for ((key, _) in expiredLocks) {
            mutexLocks.remove(key)
        }
    }
    
    /**
     * 防止缓存穿透。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @param loader 数据加载函数
     * @return 数据的 Future
     */
    fun <T> preventCachePenetration(
        key: String,
        namespace: String = cacheNamespace.get(),
        loader: () -> Future<T>
    ): Future<T> {
        // 如果缓存穿透防护未启用，直接执行加载函数
        if (!penetrationProtectionEnabled.get()) {
            return loader()
        }
        
        // 检查布隆过滤器
        if (bloomFilterManager.mightContain(key, namespace)) {
            // 可能存在，尝试从缓存获取
            return cacheManager.get(key, namespace)
                .compose { value ->
                    // 缓存命中
                    @Suppress("UNCHECKED_CAST")
                    if (value == NULL_VALUE) {
                        // 空值缓存命中，返回 null
                        Future.succeededFuture(null as T)
                    } else {
                        // 正常值缓存命中
                        Future.succeededFuture(value as T)
                    }
                }
                .recover { cause ->
                    // 缓存未命中，执行加载函数
                    executeLoader(key, namespace, loader)
                }
        } else {
            // 布隆过滤器判断不存在，直接返回 null
            // 记录缓存穿透
            recordCachePenetration(key, namespace)
            
            // 缓存空值
            cacheManager.put(key, NULL_VALUE, nullValueTTL.get(), namespace)
            
            return Future.succeededFuture(null as T)
        }
    }
    
    /**
     * 防止缓存击穿。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @param loader 数据加载函数
     * @return 数据的 Future
     */
    fun <T> preventCacheBreakdown(
        key: String,
        namespace: String = cacheNamespace.get(),
        loader: () -> Future<T>
    ): Future<T> {
        // 如果缓存击穿防护未启用，直接执行加载函数
        if (!breakdownProtectionEnabled.get()) {
            return loader()
        }
        
        // 尝试从缓存获取
        return cacheManager.get(key, namespace)
            .compose { value ->
                // 缓存命中
                @Suppress("UNCHECKED_CAST")
                if (value == NULL_VALUE) {
                    // 空值缓存命中，返回 null
                    Future.succeededFuture(null as T)
                } else {
                    // 正常值缓存命中
                    Future.succeededFuture(value as T)
                }
            }
            .recover { cause ->
                // 缓存未命中，使用互斥锁防止缓存击穿
                val lockKey = "$namespace:$key:lock"
                
                if (acquireMutexLock(lockKey)) {
                    // 获取锁成功，执行加载函数
                    executeLoader(key, namespace, loader)
                        .onComplete { ar ->
                            // 释放锁
                            releaseMutexLock(lockKey)
                        }
                } else {
                    // 获取锁失败，说明有其他线程正在加载，等待一段时间后重试
                    // 记录缓存击穿
                    recordCacheBreakdown(key, namespace)
                    
                    val promise = Promise.promise<T>()
                    
                    // 等待一段时间后重试
                    vertx.setTimer(100) { _ ->
                        preventCacheBreakdown(key, namespace, loader)
                            .onComplete { ar ->
                                if (ar.succeeded()) {
                                    promise.complete(ar.result())
                                } else {
                                    promise.fail(ar.cause())
                                }
                            }
                    }
                    
                    promise.future()
                }
            }
    }
    
    /**
     * 防止缓存雪崩。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @param ttl 缓存过期时间（毫秒）
     * @param loader 数据加载函数
     * @return 数据的 Future
     */
    fun <T> preventCacheAvalanche(
        key: String,
        namespace: String = cacheNamespace.get(),
        ttl: Long,
        loader: () -> Future<T>
    ): Future<T> {
        // 如果缓存雪崩防护未启用，直接执行加载函数
        if (!avalancheProtectionEnabled.get()) {
            return loader()
        }
        
        // 检测缓存雪崩
        if (detectCacheAvalanche()) {
            // 缓存雪崩，采取措施
            logger.warn("检测到缓存雪崩，采取防护措施")
            
            // 随机延迟一段时间
            val delay = (Math.random() * 1000).toLong()
            
            val promise = Promise.promise<T>()
            
            vertx.setTimer(delay) { _ ->
                // 尝试从缓存获取
                cacheManager.get(key, namespace)
                    .compose { value ->
                        // 缓存命中
                        @Suppress("UNCHECKED_CAST")
                        if (value == NULL_VALUE) {
                            // 空值缓存命中，返回 null
                            Future.succeededFuture(null as T)
                        } else {
                            // 正常值缓存命中
                            Future.succeededFuture(value as T)
                        }
                    }
                    .recover { cause ->
                        // 缓存未命中，执行加载函数
                        executeLoader(key, namespace, loader, ttl + getRandomExpiryOffset())
                    }
                    .onComplete { ar ->
                        if (ar.succeeded()) {
                            promise.complete(ar.result())
                        } else {
                            promise.fail(ar.cause())
                        }
                    }
            }
            
            return promise.future()
        } else {
            // 正常情况，尝试从缓存获取
            return cacheManager.get(key, namespace)
                .compose { value ->
                    // 缓存命中
                    @Suppress("UNCHECKED_CAST")
                    if (value == NULL_VALUE) {
                        // 空值缓存命中，返回 null
                        Future.succeededFuture(null as T)
                    } else {
                        // 正常值缓存命中
                        Future.succeededFuture(value as T)
                    }
                }
                .recover { cause ->
                    // 缓存未命中，执行加载函数
                    executeLoader(key, namespace, loader, ttl + getRandomExpiryOffset())
                }
        }
    }
    
    /**
     * 执行数据加载函数。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @param loader 数据加载函数
     * @param ttl 缓存过期时间（毫秒），默认为 0（永不过期）
     * @return 数据的 Future
     */
    private fun <T> executeLoader(
        key: String,
        namespace: String,
        loader: () -> Future<T>,
        ttl: Long = 0
    ): Future<T> {
        return loader()
            .compose { value ->
                if (value == null) {
                    // 数据为 null，缓存空值
                    cacheManager.put(key, NULL_VALUE, nullValueTTL.get(), namespace)
                        .compose { _ ->
                            // 添加到布隆过滤器
                            bloomFilterManager.add(key, namespace)
                        }
                        .compose { _ ->
                            Future.succeededFuture(null as T)
                        }
                } else {
                    // 数据不为 null，缓存数据
                    cacheManager.put(key, value, ttl, namespace)
                        .compose { _ ->
                            // 添加到布隆过滤器
                            bloomFilterManager.add(key, namespace)
                        }
                        .compose { _ ->
                            Future.succeededFuture(value)
                        }
                }
            }
    }
    
    /**
     * 获取随机过期时间偏移量。
     * 
     * @return 随机过期时间偏移量（毫秒）
     */
    private fun getRandomExpiryOffset(): Long {
        val offset = randomExpiryOffset.get()
        return if (offset > 0) (Math.random() * offset).toLong() else 0
    }
    
    /**
     * 获取互斥锁。
     * 
     * @param lockKey 锁键
     * @return 是否获取成功
     */
    private fun acquireMutexLock(lockKey: String): Boolean {
        val now = System.currentTimeMillis()
        val timeout = mutexLockTimeout.get()
        
        // 创建新锁
        val newLock = MutexLock(now + timeout)
        
        // 尝试获取锁
        val existingLock = mutexLocks.putIfAbsent(lockKey, newLock)
        
        // 如果已存在锁，检查是否过期
        if (existingLock != null) {
            return existingLock.isExpired(now) && mutexLocks.replace(lockKey, existingLock, newLock)
        }
        
        return true
    }
    
    /**
     * 释放互斥锁。
     * 
     * @param lockKey 锁键
     */
    private fun releaseMutexLock(lockKey: String) {
        mutexLocks.remove(lockKey)
    }
    
    /**
     * 记录缓存穿透。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     */
    private fun recordCachePenetration(key: String, namespace: String) {
        val counterKey = "$namespace:$key"
        val counter = penetrationCounters.computeIfAbsent(counterKey) { AtomicInteger(0) }
        counter.incrementAndGet()
    }
    
    /**
     * 记录缓存击穿。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     */
    private fun recordCacheBreakdown(key: String, namespace: String) {
        val counterKey = "$namespace:$key"
        val counter = breakdownCounters.computeIfAbsent(counterKey) { AtomicInteger(0) }
        counter.incrementAndGet()
    }
    
    /**
     * 检测缓存雪崩。
     * 
     * @return 是否发生缓存雪崩
     */
    private fun detectCacheAvalanche(): Boolean {
        val now = System.currentTimeMillis()
        val lastCheckTime = avalancheLastCheckTime.get()
        val timeWindow = avalancheTimeWindow.get()
        
        // 如果距离上次检查时间小于时间窗口，增加计数
        if (now - lastCheckTime < timeWindow) {
            val count = avalancheCounter.incrementAndGet()
            
            // 如果计数超过阈值，认为发生了缓存雪崩
            return count > avalancheThreshold.get()
        } else {
            // 重置计数
            avalancheCounter.set(1)
            avalancheLastCheckTime.set(now)
            return false
        }
    }
    
    /**
     * 获取缓存穿透防护管理器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("penetrationProtectionEnabled", penetrationProtectionEnabled.get())
            .put("breakdownProtectionEnabled", breakdownProtectionEnabled.get())
            .put("avalancheProtectionEnabled", avalancheProtectionEnabled.get())
            .put("nullValueTTL", nullValueTTL.get())
            .put("mutexLockTimeout", mutexLockTimeout.get())
            .put("randomExpiryOffset", randomExpiryOffset.get())
            .put("avalancheThreshold", avalancheThreshold.get())
            .put("avalancheTimeWindow", avalancheTimeWindow.get())
            .put("mutexLocksCount", mutexLocks.size)
            .put("avalancheCounter", avalancheCounter.get())
        
        // 添加缓存穿透统计信息
        val penetrationStats = JsonObject()
        for ((key, counter) in penetrationCounters) {
            penetrationStats.put(key, counter.get())
        }
        status.put("penetrationStats", penetrationStats)
        
        // 添加缓存击穿统计信息
        val breakdownStats = JsonObject()
        for ((key, counter) in breakdownCounters) {
            breakdownStats.put(key, counter.get())
        }
        status.put("breakdownStats", breakdownStats)
        
        return status
    }
    
    /**
     * 互斥锁。
     */
    data class MutexLock(
        val expiresAt: Long
    ) {
        /**
         * 检查锁是否过期。
         * 
         * @param now 当前时间
         * @return 是否过期
         */
        fun isExpired(now: Long): Boolean {
            return now >= expiresAt
        }
    }
    
    companion object {
        // 空值标记
        val NULL_VALUE = JsonObject().put("__null__", true)
        
        // 单例实例
        @Volatile
        private var instance: CacheProtectionManager? = null
        
        /**
         * 获取 CacheProtectionManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return CacheProtectionManager 实例
         */
        fun getInstance(vertx: Vertx): CacheProtectionManager {
            return instance ?: synchronized(this) {
                instance ?: CacheProtectionManager(vertx).also { instance = it }
            }
        }
    }
}
