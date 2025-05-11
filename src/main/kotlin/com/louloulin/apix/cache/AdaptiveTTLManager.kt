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
 * 自适应 TTL 管理器，根据访问模式动态调整缓存过期时间。
 */
class AdaptiveTTLManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(AdaptiveTTLManager::class.java)
    
    // 是否启用自适应 TTL
    private val adaptiveTTLEnabled = AtomicBoolean(true)
    
    // 自适应 TTL 策略
    private val adaptiveStrategy = AtomicReference<String>("frequency")
    
    // 最小 TTL（毫秒）
    private val minTTL = AtomicLong(1000)
    
    // 最大 TTL（毫秒）
    private val maxTTL = AtomicLong(86400000) // 1 天
    
    // 默认 TTL（毫秒）
    private val defaultTTL = AtomicLong(60000) // 1 分钟
    
    // TTL 调整因子
    private val ttlAdjustmentFactor = AtomicReference<Double>(1.5)
    
    // 访问频率阈值
    private val frequencyThreshold = AtomicInteger(10)
    
    // 访问间隔阈值（毫秒）
    private val intervalThreshold = AtomicLong(60000) // 1 分钟
    
    // 访问统计信息
    private val accessStats = ConcurrentHashMap<String, AccessStats>()
    
    // 缓存命名空间
    private val cacheNamespace = AtomicReference<String>("apix")
    
    // 统计信息更新定时器 ID
    private var statsUpdateTimerId = -1L
    
    /**
     * 初始化自适应 TTL 管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化自适应 TTL 管理器")
        
        // 获取缓存配置
        val cacheConfig = config.getJsonObject("cache", JsonObject())
        val adaptiveTTLConfig = cacheConfig.getJsonObject("adaptiveTTL", JsonObject())
        
        // 更新配置
        adaptiveTTLEnabled.set(adaptiveTTLConfig.getBoolean("enabled", true))
        adaptiveStrategy.set(adaptiveTTLConfig.getString("strategy", "frequency"))
        minTTL.set(adaptiveTTLConfig.getLong("minTTL", 1000))
        maxTTL.set(adaptiveTTLConfig.getLong("maxTTL", 86400000))
        defaultTTL.set(adaptiveTTLConfig.getLong("defaultTTL", 60000))
        ttlAdjustmentFactor.set(adaptiveTTLConfig.getDouble("adjustmentFactor", 1.5))
        frequencyThreshold.set(adaptiveTTLConfig.getInteger("frequencyThreshold", 10))
        intervalThreshold.set(adaptiveTTLConfig.getLong("intervalThreshold", 60000))
        cacheNamespace.set(cacheConfig.getString("cacheNamespace", "apix"))
        
        // 如果自适应 TTL 未启用，直接返回
        if (!adaptiveTTLEnabled.get()) {
            logger.info("自适应 TTL 未启用")
            return Future.succeededFuture()
        }
        
        // 启动统计信息更新任务
        startStatsUpdateTask()
        
        // 注册缓存访问事件处理器
        registerCacheAccessHandlers()
        
        logger.info("自适应 TTL 管理器初始化完成，策略: ${adaptiveStrategy.get()}")
        return Future.succeededFuture()
    }
    
    /**
     * 启动统计信息更新任务。
     */
    private fun startStatsUpdateTask() {
        // 获取更新间隔
        val updateInterval = 60000L // 1 分钟
        
        // 停止之前的定时器
        if (statsUpdateTimerId != -1L) {
            vertx.cancelTimer(statsUpdateTimerId)
        }
        
        // 启动新的定时器
        statsUpdateTimerId = vertx.setPeriodic(updateInterval) { _ ->
            updateAccessStats()
        }
        
        logger.info("统计信息更新任务已启动，间隔: $updateInterval ms")
    }
    
    /**
     * 注册缓存访问事件处理器。
     */
    private fun registerCacheAccessHandlers() {
        // 监听缓存访问事件
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.cache.access") { message ->
            val key = message.body().getString("key")
            val namespace = message.body().getString("namespace", cacheNamespace.get())
            val hit = message.body().getBoolean("hit", true)
            
            if (key != null) {
                // 记录缓存访问
                recordAccess(key, namespace, hit)
            }
        }
    }
    
    /**
     * 更新访问统计信息。
     */
    private fun updateAccessStats() {
        val now = System.currentTimeMillis()
        
        // 更新所有访问统计信息
        for ((key, stats) in accessStats) {
            // 计算访问频率
            val frequency = stats.getAccessFrequency()
            
            // 计算平均访问间隔
            val avgInterval = stats.getAverageInterval()
            
            // 根据策略调整 TTL
            val newTTL = when (adaptiveStrategy.get()) {
                "frequency" -> adjustTTLByFrequency(stats.currentTTL, frequency)
                "interval" -> adjustTTLByInterval(stats.currentTTL, avgInterval)
                "hybrid" -> adjustTTLHybrid(stats.currentTTL, frequency, avgInterval)
                else -> stats.currentTTL
            }
            
            // 更新 TTL
            if (newTTL != stats.currentTTL) {
                stats.currentTTL = newTTL
                
                // 发布 TTL 更新事件
                vertx.eventBus().publish("${cacheNamespace.get()}.cache.ttl.update", JsonObject()
                    .put("key", key.substringAfter(':'))
                    .put("namespace", key.substringBefore(':'))
                    .put("ttl", newTTL)
                    .put("timestamp", now)
                )
                
                logger.debug("更新缓存 TTL: key=$key, ttl=$newTTL, frequency=$frequency, avgInterval=$avgInterval")
            }
            
            // 清理过期的访问记录
            stats.cleanupOldAccesses(now - 86400000) // 清理 1 天前的记录
        }
    }
    
    /**
     * 记录缓存访问。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @param hit 是否命中
     */
    fun recordAccess(key: String, namespace: String, hit: Boolean) {
        val statsKey = "$namespace:$key"
        val stats = accessStats.computeIfAbsent(statsKey) { AccessStats(defaultTTL.get()) }
        
        // 记录访问
        stats.recordAccess(System.currentTimeMillis(), hit)
    }
    
    /**
     * 根据访问频率调整 TTL。
     * 
     * @param currentTTL 当前 TTL
     * @param frequency 访问频率
     * @return 新的 TTL
     */
    private fun adjustTTLByFrequency(currentTTL: Long, frequency: Double): Long {
        // 如果访问频率高于阈值，增加 TTL
        return if (frequency > frequencyThreshold.get()) {
            Math.min(maxTTL.get(), (currentTTL * ttlAdjustmentFactor.get()).toLong())
        } else {
            // 否则，减少 TTL
            Math.max(minTTL.get(), (currentTTL / ttlAdjustmentFactor.get()).toLong())
        }
    }
    
    /**
     * 根据访问间隔调整 TTL。
     * 
     * @param currentTTL 当前 TTL
     * @param avgInterval 平均访问间隔
     * @return 新的 TTL
     */
    private fun adjustTTLByInterval(currentTTL: Long, avgInterval: Long): Long {
        // 如果平均访问间隔小于阈值，增加 TTL
        return if (avgInterval > 0 && avgInterval < intervalThreshold.get()) {
            Math.min(maxTTL.get(), (currentTTL * ttlAdjustmentFactor.get()).toLong())
        } else {
            // 否则，减少 TTL
            Math.max(minTTL.get(), (currentTTL / ttlAdjustmentFactor.get()).toLong())
        }
    }
    
    /**
     * 混合策略调整 TTL。
     * 
     * @param currentTTL 当前 TTL
     * @param frequency 访问频率
     * @param avgInterval 平均访问间隔
     * @return 新的 TTL
     */
    private fun adjustTTLHybrid(currentTTL: Long, frequency: Double, avgInterval: Long): Long {
        // 根据访问频率和访问间隔综合调整 TTL
        val frequencyScore = if (frequency > frequencyThreshold.get()) 1.0 else -1.0
        val intervalScore = if (avgInterval > 0 && avgInterval < intervalThreshold.get()) 1.0 else -1.0
        
        val combinedScore = (frequencyScore + intervalScore) / 2.0
        
        return when {
            combinedScore > 0 -> Math.min(maxTTL.get(), (currentTTL * ttlAdjustmentFactor.get()).toLong())
            combinedScore < 0 -> Math.max(minTTL.get(), (currentTTL / ttlAdjustmentFactor.get()).toLong())
            else -> currentTTL
        }
    }
    
    /**
     * 获取缓存键的推荐 TTL。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @return 推荐的 TTL
     */
    fun getRecommendedTTL(key: String, namespace: String = cacheNamespace.get()): Long {
        // 如果自适应 TTL 未启用，返回默认 TTL
        if (!adaptiveTTLEnabled.get()) {
            return defaultTTL.get()
        }
        
        val statsKey = "$namespace:$key"
        val stats = accessStats[statsKey]
        
        // 如果没有统计信息，返回默认 TTL
        return stats?.currentTTL ?: defaultTTL.get()
    }
    
    /**
     * 获取自适应 TTL 管理器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", adaptiveTTLEnabled.get())
            .put("strategy", adaptiveStrategy.get())
            .put("minTTL", minTTL.get())
            .put("maxTTL", maxTTL.get())
            .put("defaultTTL", defaultTTL.get())
            .put("adjustmentFactor", ttlAdjustmentFactor.get())
            .put("frequencyThreshold", frequencyThreshold.get())
            .put("intervalThreshold", intervalThreshold.get())
        
        // 添加统计信息
        val statsJson = JsonObject()
        for ((key, stats) in accessStats) {
            statsJson.put(key, JsonObject()
                .put("currentTTL", stats.currentTTL)
                .put("accessCount", stats.getAccessCount())
                .put("hitCount", stats.getHitCount())
                .put("missCount", stats.getMissCount())
                .put("hitRate", stats.getHitRate())
                .put("frequency", stats.getAccessFrequency())
                .put("averageInterval", stats.getAverageInterval())
            )
        }
        status.put("stats", statsJson)
        
        return status
    }
    
    /**
     * 访问统计信息。
     */
    class AccessStats(
        var currentTTL: Long
    ) {
        // 访问记录
        private val accesses = ConcurrentHashMap<Long, Boolean>()
        
        // 访问计数
        private val accessCount = AtomicInteger(0)
        
        // 命中计数
        private val hitCount = AtomicInteger(0)
        
        // 未命中计数
        private val missCount = AtomicInteger(0)
        
        // 上次访问时间
        private val lastAccessTime = AtomicLong(0)
        
        /**
         * 记录访问。
         * 
         * @param timestamp 访问时间戳
         * @param hit 是否命中
         */
        fun recordAccess(timestamp: Long, hit: Boolean) {
            // 记录访问
            accesses[timestamp] = hit
            
            // 更新计数
            accessCount.incrementAndGet()
            if (hit) {
                hitCount.incrementAndGet()
            } else {
                missCount.incrementAndGet()
            }
            
            // 更新上次访问时间
            lastAccessTime.set(timestamp)
        }
        
        /**
         * 清理旧的访问记录。
         * 
         * @param before 清理此时间戳之前的记录
         */
        fun cleanupOldAccesses(before: Long) {
            // 获取需要清理的记录
            val keysToRemove = accesses.keys.filter { it < before }
            
            // 清理记录
            for (key in keysToRemove) {
                val hit = accesses.remove(key)
                
                // 更新计数
                accessCount.decrementAndGet()
                if (hit == true) {
                    hitCount.decrementAndGet()
                } else {
                    missCount.decrementAndGet()
                }
            }
        }
        
        /**
         * 获取访问次数。
         * 
         * @return 访问次数
         */
        fun getAccessCount(): Int {
            return accessCount.get()
        }
        
        /**
         * 获取命中次数。
         * 
         * @return 命中次数
         */
        fun getHitCount(): Int {
            return hitCount.get()
        }
        
        /**
         * 获取未命中次数。
         * 
         * @return 未命中次数
         */
        fun getMissCount(): Int {
            return missCount.get()
        }
        
        /**
         * 获取命中率。
         * 
         * @return 命中率
         */
        fun getHitRate(): Double {
            val total = accessCount.get()
            return if (total > 0) hitCount.get().toDouble() / total else 0.0
        }
        
        /**
         * 获取访问频率（次/分钟）。
         * 
         * @return 访问频率
         */
        fun getAccessFrequency(): Double {
            val now = System.currentTimeMillis()
            val timeWindow = 60000L // 1 分钟
            
            // 计算时间窗口内的访问次数
            val recentAccesses = accesses.keys.count { now - it <= timeWindow }
            
            return recentAccesses.toDouble()
        }
        
        /**
         * 获取平均访问间隔（毫秒）。
         * 
         * @return 平均访问间隔
         */
        fun getAverageInterval(): Long {
            val timestamps = accesses.keys.sorted()
            
            if (timestamps.size < 2) {
                return 0
            }
            
            var totalInterval = 0L
            for (i in 1 until timestamps.size) {
                totalInterval += timestamps[i] - timestamps[i - 1]
            }
            
            return totalInterval / (timestamps.size - 1)
        }
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: AdaptiveTTLManager? = null
        
        /**
         * 获取 AdaptiveTTLManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return AdaptiveTTLManager 实例
         */
        fun getInstance(vertx: Vertx): AdaptiveTTLManager {
            return instance ?: synchronized(this) {
                instance ?: AdaptiveTTLManager(vertx).also { instance = it }
            }
        }
    }
}
