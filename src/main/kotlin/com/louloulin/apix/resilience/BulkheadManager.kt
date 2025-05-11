package com.louloulin.apix.resilience

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 舱壁模式管理器，用于实现请求隔离和资源隔离。
 * 舱壁模式可以防止单个服务的故障影响整个系统。
 */
class BulkheadManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(BulkheadManager::class.java)
    
    // 舱壁是否启用
    private val bulkheadEnabled = AtomicBoolean(true)
    
    // 舱壁配置
    private val bulkheadConfigs = ConcurrentHashMap<String, BulkheadConfig>()
    
    // 舱壁实例
    private val bulkheads = ConcurrentHashMap<String, Bulkhead>()
    
    // 舱壁统计信息
    private val bulkheadStats = ConcurrentHashMap<String, BulkheadStats>()
    
    // 默认舱壁配置
    private val defaultConfig = BulkheadConfig(
        maxConcurrentCalls = 20,
        maxWaitTime = 500
    )
    
    /**
     * 初始化舱壁管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化舱壁管理器")
        
        // 获取配置
        val resilienceConfig = config.getJsonObject("resilience", JsonObject())
        val bulkheadConfig = resilienceConfig.getJsonObject("bulkhead", JsonObject())
        
        // 检查舱壁是否启用
        bulkheadEnabled.set(bulkheadConfig.getBoolean("enabled", true))
        
        if (!bulkheadEnabled.get()) {
            logger.info("舱壁模式未启用")
            return Future.succeededFuture()
        }
        
        // 获取默认配置
        val defaultConfigJson = bulkheadConfig.getJsonObject("default", JsonObject())
        if (defaultConfigJson != null) {
            val maxConcurrentCalls = defaultConfigJson.getInteger("maxConcurrentCalls", defaultConfig.maxConcurrentCalls)
            val maxWaitTime = defaultConfigJson.getLong("maxWaitTime", defaultConfig.maxWaitTime)
            
            defaultConfig.maxConcurrentCalls = maxConcurrentCalls
            defaultConfig.maxWaitTime = maxWaitTime
        }
        
        // 获取服务特定配置
        val servicesConfig = bulkheadConfig.getJsonObject("services", JsonObject())
        if (servicesConfig != null) {
            for (serviceName in servicesConfig.fieldNames()) {
                val serviceConfig = servicesConfig.getJsonObject(serviceName)
                val maxConcurrentCalls = serviceConfig.getInteger("maxConcurrentCalls", defaultConfig.maxConcurrentCalls)
                val maxWaitTime = serviceConfig.getLong("maxWaitTime", defaultConfig.maxWaitTime)
                
                bulkheadConfigs[serviceName] = BulkheadConfig(
                    maxConcurrentCalls = maxConcurrentCalls,
                    maxWaitTime = maxWaitTime
                )
                
                logger.info("为服务 $serviceName 配置舱壁: maxConcurrentCalls=$maxConcurrentCalls, maxWaitTime=$maxWaitTime")
            }
        }
        
        logger.info("舱壁管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 获取指定服务的舱壁。
     * 
     * @param serviceName 服务名称
     * @return 舱壁实例
     */
    fun getBulkhead(serviceName: String): Bulkhead {
        return bulkheads.computeIfAbsent(serviceName) { name ->
            val config = bulkheadConfigs[name] ?: defaultConfig
            val stats = bulkheadStats.computeIfAbsent(name) { BulkheadStats() }
            
            Bulkhead(name, config, stats)
        }
    }
    
    /**
     * 使用舱壁执行操作。
     * 
     * @param serviceName 服务名称
     * @param action 要执行的操作
     * @param fallback 失败后的备选方案
     * @return 操作结果的 Future
     */
    fun <T> executeWithBulkhead(
        serviceName: String,
        action: () -> Future<T>,
        fallback: (Throwable) -> Future<T>
    ): Future<T> {
        // 如果舱壁未启用，直接执行操作
        if (!bulkheadEnabled.get()) {
            return action()
                .recover { throwable -> fallback(throwable) }
        }
        
        // 获取舱壁
        val bulkhead = getBulkhead(serviceName)
        
        // 尝试获取许可
        return bulkhead.tryAcquirePermission()
            .compose { acquired ->
                if (acquired) {
                    // 获取许可成功，执行操作
                    action()
                        .onComplete { ar ->
                            // 释放许可
                            bulkhead.releasePermission()
                            
                            // 更新统计信息
                            if (ar.succeeded()) {
                                bulkhead.stats.recordSuccess()
                            } else {
                                bulkhead.stats.recordFailure()
                            }
                        }
                        .recover { throwable ->
                            // 操作失败，执行备选方案
                            fallback(throwable)
                        }
                } else {
                    // 获取许可失败，执行备选方案
                    bulkhead.stats.recordRejected()
                    fallback(BulkheadFullException("Bulkhead $serviceName is full"))
                }
            }
    }
    
    /**
     * 获取所有舱壁的统计信息。
     * 
     * @return 包含统计信息的 JsonObject
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("enabled", bulkheadEnabled.get())
        
        val bulkheadsStats = JsonObject()
        for ((name, bulkhead) in bulkheads) {
            bulkheadsStats.put(name, bulkhead.getStats())
        }
        
        stats.put("bulkheads", bulkheadsStats)
        return stats
    }
    
    /**
     * 舱壁配置。
     */
    data class BulkheadConfig(
        var maxConcurrentCalls: Int,
        var maxWaitTime: Long
    )
    
    /**
     * 舱壁统计信息。
     */
    class BulkheadStats {
        // 当前活动调用数
        private val activeCalls = AtomicLong(0)
        
        // 总调用数
        private val totalCalls = AtomicLong(0)
        
        // 成功调用数
        private val successCalls = AtomicLong(0)
        
        // 失败调用数
        private val failedCalls = AtomicLong(0)
        
        // 被拒绝的调用数
        private val rejectedCalls = AtomicLong(0)
        
        /**
         * 增加活动调用数。
         */
        fun incrementActiveCalls() {
            activeCalls.incrementAndGet()
            totalCalls.incrementAndGet()
        }
        
        /**
         * 减少活动调用数。
         */
        fun decrementActiveCalls() {
            activeCalls.decrementAndGet()
        }
        
        /**
         * 记录成功调用。
         */
        fun recordSuccess() {
            successCalls.incrementAndGet()
        }
        
        /**
         * 记录失败调用。
         */
        fun recordFailure() {
            failedCalls.incrementAndGet()
        }
        
        /**
         * 记录被拒绝的调用。
         */
        fun recordRejected() {
            rejectedCalls.incrementAndGet()
        }
        
        /**
         * 获取统计信息。
         */
        fun getStats(): JsonObject {
            return JsonObject()
                .put("activeCalls", activeCalls.get())
                .put("totalCalls", totalCalls.get())
                .put("successCalls", successCalls.get())
                .put("failedCalls", failedCalls.get())
                .put("rejectedCalls", rejectedCalls.get())
        }
    }
    
    /**
     * 舱壁实现。
     */
    inner class Bulkhead(
        val name: String,
        val config: BulkheadConfig,
        val stats: BulkheadStats
    ) {
        // 信号量，用于限制并发调用数
        private val semaphore = Semaphore(config.maxConcurrentCalls)
        
        /**
         * 尝试获取许可。
         * 
         * @return 是否获取成功的 Future
         */
        fun tryAcquirePermission(): Future<Boolean> {
            val promise = Promise.promise<Boolean>()
            
            try {
                // 尝试获取许可
                val acquired = semaphore.tryAcquire(config.maxWaitTime, TimeUnit.MILLISECONDS)
                
                if (acquired) {
                    // 获取许可成功，增加活动调用数
                    stats.incrementActiveCalls()
                }
                
                promise.complete(acquired)
            } catch (e: InterruptedException) {
                // 获取许可被中断
                promise.complete(false)
            }
            
            return promise.future()
        }
        
        /**
         * 释放许可。
         */
        fun releasePermission() {
            // 减少活动调用数
            stats.decrementActiveCalls()
            
            // 释放许可
            semaphore.release()
        }
        
        /**
         * 获取舱壁状态。
         */
        fun getStats(): JsonObject {
            return JsonObject()
                .put("name", name)
                .put("config", JsonObject()
                    .put("maxConcurrentCalls", config.maxConcurrentCalls)
                    .put("maxWaitTime", config.maxWaitTime)
                )
                .put("availablePermits", semaphore.availablePermits())
                .put("stats", stats.getStats())
        }
    }
    
    /**
     * 舱壁已满异常。
     */
    class BulkheadFullException(message: String) : Exception(message)
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: BulkheadManager? = null
        
        /**
         * 获取 BulkheadManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return BulkheadManager 实例
         */
        fun getInstance(vertx: Vertx): BulkheadManager {
            return instance ?: synchronized(this) {
                instance ?: BulkheadManager(vertx).also { instance = it }
            }
        }
    }
}
