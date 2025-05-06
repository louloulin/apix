package com.louloulin.apix.core.resource

import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

/**
 * 资源管理器，用于自动调整系统资源分配。
 * 根据系统负载动态调整资源分配，例如调整线程池大小、连接池大小、Verticle实例数等。
 */
class ResourceManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ResourceManager::class.java)
    
    // 系统资源信息
    private val cpuCores = Runtime.getRuntime().availableProcessors()
    private val maxMemory = Runtime.getRuntime().maxMemory()
    
    // 资源使用统计
    private val cpuUsage = AtomicReference(0.0)
    private val memoryUsage = AtomicReference(0.0)
    private val activeConnections = AtomicInteger(0)
    private val activeRequests = AtomicInteger(0)
    private val maxConcurrentRequests = AtomicInteger(0)
    
    // 资源调整配置
    private val minWorkerPoolSize = AtomicInteger(10)
    private val maxWorkerPoolSize = AtomicInteger(50)
    private val minEventLoopPoolSize = AtomicInteger(cpuCores)
    private val maxEventLoopPoolSize = AtomicInteger(cpuCores * 2)
    private val minConnectionPoolSize = AtomicInteger(50)
    private val maxConnectionPoolSize = AtomicInteger(500)
    
    // 当前资源配置
    private val currentWorkerPoolSize = AtomicInteger(minWorkerPoolSize.get())
    private val currentEventLoopPoolSize = AtomicInteger(minEventLoopPoolSize.get())
    private val currentConnectionPoolSize = AtomicInteger(minConnectionPoolSize.get())
    
    // Verticle部署信息
    private val verticleDeployments = ConcurrentHashMap<String, VerticleDeploymentInfo>()
    
    // 自动调整开关
    private val autoAdjustEnabled = AtomicBoolean(true)
    
    // 上次调整时间
    private val lastAdjustTime = AtomicLong(System.currentTimeMillis())
    
    // 调整间隔（毫秒）
    private val adjustInterval = AtomicLong(60000) // 默认1分钟
    
    // MBean服务器
    private val mBeanServer = ManagementFactory.getPlatformMBeanServer()
    
    /**
     * Verticle部署信息
     */
    data class VerticleDeploymentInfo(
        val verticleName: String,
        val minInstances: Int,
        val maxInstances: Int,
        val currentInstances: AtomicInteger,
        val deploymentIds: MutableList<String>
    )
    
    init {
        // 启动资源监控
        startResourceMonitoring()
        
        // 启动自动资源调整
        if (autoAdjustEnabled.get()) {
            startAutoAdjustment()
        }
    }
    
    /**
     * 启动资源监控
     */
    private fun startResourceMonitoring() {
        // 定期监控系统资源使用情况
        vertx.setPeriodic(5000) { _ ->
            try {
                // 更新CPU使用率
                updateCpuUsage()
                
                // 更新内存使用率
                updateMemoryUsage()
                
                // 记录资源使用情况
                if (logger.isDebugEnabled) {
                    logger.debug("资源使用情况: CPU={}%, Memory={}%, ActiveConnections={}, ActiveRequests={}",
                        String.format("%.2f", cpuUsage.get() * 100),
                        String.format("%.2f", memoryUsage.get() * 100),
                        activeConnections.get(),
                        activeRequests.get()
                    )
                }
            } catch (e: Exception) {
                logger.error("监控系统资源时发生错误", e)
            }
        }
    }
    
    /**
     * 启动自动资源调整
     */
    private fun startAutoAdjustment() {
        // 定期调整资源分配
        vertx.setPeriodic(adjustInterval.get()) { _ ->
            if (autoAdjustEnabled.get()) {
                try {
                    // 检查是否需要调整
                    val now = System.currentTimeMillis()
                    if (now - lastAdjustTime.get() >= adjustInterval.get()) {
                        // 调整资源
                        adjustResources()
                        
                        // 更新上次调整时间
                        lastAdjustTime.set(now)
                    }
                } catch (e: Exception) {
                    logger.error("调整资源时发生错误", e)
                }
            }
        }
    }
    
    /**
     * 更新CPU使用率
     */
    private fun updateCpuUsage() {
        try {
            val operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
            
            if (operatingSystemMXBean is com.sun.management.OperatingSystemMXBean) {
                // 获取系统CPU使用率
                val systemCpuLoad = operatingSystemMXBean.systemCpuLoad
                
                // 获取进程CPU使用率
                val processCpuLoad = operatingSystemMXBean.processCpuLoad
                
                // 更新CPU使用率（使用进程CPU使用率）
                if (processCpuLoad >= 0) {
                    cpuUsage.set(processCpuLoad)
                }
                
                if (logger.isTraceEnabled) {
                    logger.trace("CPU使用率: 系统={}%, 进程={}%",
                        String.format("%.2f", systemCpuLoad * 100),
                        String.format("%.2f", processCpuLoad * 100)
                    )
                }
            } else {
                // 如果不支持com.sun.management.OperatingSystemMXBean，使用其他方式估算
                val systemLoadAverage = operatingSystemMXBean.systemLoadAverage
                if (systemLoadAverage >= 0) {
                    // 估算CPU使用率（系统负载平均值 / CPU核心数）
                    val estimatedCpuUsage = min(1.0, systemLoadAverage / cpuCores)
                    cpuUsage.set(estimatedCpuUsage)
                    
                    if (logger.isTraceEnabled) {
                        logger.trace("估算CPU使用率: {}%", String.format("%.2f", estimatedCpuUsage * 100))
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("获取CPU使用率时发生错误", e)
        }
    }
    
    /**
     * 更新内存使用率
     */
    private fun updateMemoryUsage() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val memUsage = usedMemory.toDouble() / maxMemory
            
            memoryUsage.set(memUsage)
            
            if (logger.isTraceEnabled) {
                logger.trace("内存使用情况: 已用={}MB, 最大={}MB, 使用率={}%",
                    usedMemory / (1024 * 1024),
                    maxMemory / (1024 * 1024),
                    String.format("%.2f", memUsage * 100)
                )
            }
        } catch (e: Exception) {
            logger.warn("获取内存使用率时发生错误", e)
        }
    }
    
    /**
     * 调整资源
     */
    private fun adjustResources() {
        // 获取当前资源使用情况
        val cpu = cpuUsage.get()
        val memory = memoryUsage.get()
        val requests = activeRequests.get()
        val maxRequests = maxConcurrentRequests.get()
        
        logger.info("开始调整资源: CPU={}%, Memory={}%, ActiveRequests={}, MaxConcurrentRequests={}",
            String.format("%.2f", cpu * 100),
            String.format("%.2f", memory * 100),
            requests,
            maxRequests
        )
        
        // 调整工作线程池大小
        adjustWorkerPoolSize(cpu, memory, requests, maxRequests)
        
        // 调整事件循环线程池大小
        adjustEventLoopPoolSize(cpu, memory, requests, maxRequests)
        
        // 调整连接池大小
        adjustConnectionPoolSize(cpu, memory, requests, maxRequests)
        
        // 调整Verticle实例数
        adjustVerticleInstances(cpu, memory, requests, maxRequests)
    }
    
    /**
     * 调整工作线程池大小
     */
    private fun adjustWorkerPoolSize(cpu: Double, memory: Double, requests: Int, maxRequests: Int) {
        val currentSize = currentWorkerPoolSize.get()
        var newSize = currentSize
        
        // 根据CPU使用率调整
        if (cpu > 0.8) {
            // CPU使用率高，减小线程池大小
            newSize = max(minWorkerPoolSize.get(), (currentSize * 0.8).toInt())
        } else if (cpu < 0.3 && memory < 0.7) {
            // CPU使用率低且内存充足，增加线程池大小
            newSize = min(maxWorkerPoolSize.get(), (currentSize * 1.2).toInt())
        }
        
        // 根据请求数调整
        if (requests > currentSize * 10) {
            // 请求数远大于线程池大小，增加线程池大小
            newSize = min(maxWorkerPoolSize.get(), (currentSize * 1.2).toInt())
        } else if (requests < currentSize * 2 && currentSize > minWorkerPoolSize.get()) {
            // 请求数远小于线程池大小，减小线程池大小
            newSize = max(minWorkerPoolSize.get(), (currentSize * 0.8).toInt())
        }
        
        // 应用新的线程池大小
        if (newSize != currentSize) {
            logger.info("调整工作线程池大小: {} -> {}", currentSize, newSize)
            
            // 更新当前线程池大小
            currentWorkerPoolSize.set(newSize)
            
            // 应用新的线程池大小
            applyWorkerPoolSize(newSize)
        }
    }
    
    /**
     * 应用工作线程池大小
     */
    private fun applyWorkerPoolSize(size: Int) {
        try {
            // 设置Vert.x选项
            val options = JsonObject()
                .put("workerPoolSize", size)
            
            // 应用选项
            vertx.getOrCreateContext().config().mergeIn(options)
            
            logger.info("已应用新的工作线程池大小: {}", size)
        } catch (e: Exception) {
            logger.error("应用工作线程池大小时发生错误", e)
        }
    }
    
    /**
     * 调整事件循环线程池大小
     */
    private fun adjustEventLoopPoolSize(cpu: Double, memory: Double, requests: Int, maxRequests: Int) {
        val currentSize = currentEventLoopPoolSize.get()
        var newSize = currentSize
        
        // 根据CPU使用率调整
        if (cpu > 0.8) {
            // CPU使用率高，减小线程池大小
            newSize = max(minEventLoopPoolSize.get(), (currentSize * 0.9).toInt())
        } else if (cpu < 0.3 && memory < 0.7) {
            // CPU使用率低且内存充足，增加线程池大小
            newSize = min(maxEventLoopPoolSize.get(), (currentSize * 1.1).toInt())
        }
        
        // 应用新的线程池大小
        if (newSize != currentSize) {
            logger.info("调整事件循环线程池大小: {} -> {}", currentSize, newSize)
            
            // 更新当前线程池大小
            currentEventLoopPoolSize.set(newSize)
            
            // 应用新的线程池大小
            applyEventLoopPoolSize(newSize)
        }
    }
    
    /**
     * 应用事件循环线程池大小
     */
    private fun applyEventLoopPoolSize(size: Int) {
        try {
            // 设置Vert.x选项
            val options = JsonObject()
                .put("eventLoopPoolSize", size)
            
            // 应用选项
            vertx.getOrCreateContext().config().mergeIn(options)
            
            logger.info("已应用新的事件循环线程池大小: {}", size)
        } catch (e: Exception) {
            logger.error("应用事件循环线程池大小时发生错误", e)
        }
    }
    
    /**
     * 调整连接池大小
     */
    private fun adjustConnectionPoolSize(cpu: Double, memory: Double, requests: Int, maxRequests: Int) {
        val currentSize = currentConnectionPoolSize.get()
        var newSize = currentSize
        
        // 根据请求数调整
        if (requests > currentSize * 0.8) {
            // 请求数接近连接池大小，增加连接池大小
            newSize = min(maxConnectionPoolSize.get(), (currentSize * 1.2).toInt())
        } else if (requests < currentSize * 0.2 && currentSize > minConnectionPoolSize.get()) {
            // 请求数远小于连接池大小，减小连接池大小
            newSize = max(minConnectionPoolSize.get(), (currentSize * 0.8).toInt())
        }
        
        // 应用新的连接池大小
        if (newSize != currentSize) {
            logger.info("调整连接池大小: {} -> {}", currentSize, newSize)
            
            // 更新当前连接池大小
            currentConnectionPoolSize.set(newSize)
            
            // 应用新的连接池大小
            applyConnectionPoolSize(newSize)
        }
    }
    
    /**
     * 应用连接池大小
     */
    private fun applyConnectionPoolSize(size: Int) {
        try {
            // 发布连接池大小变更事件
            vertx.eventBus().publish("connection.pool.resize", JsonObject()
                .put("size", size)
            )
            
            logger.info("已发布连接池大小变更事件: {}", size)
        } catch (e: Exception) {
            logger.error("应用连接池大小时发生错误", e)
        }
    }
    
    /**
     * 调整Verticle实例数
     */
    private fun adjustVerticleInstances(cpu: Double, memory: Double, requests: Int, maxRequests: Int) {
        // 遍历所有Verticle部署
        for ((verticleName, deploymentInfo) in verticleDeployments) {
            val currentInstances = deploymentInfo.currentInstances.get()
            var newInstances = currentInstances
            
            // 根据CPU使用率和请求数调整
            if (cpu > 0.8 || requests > maxRequests * 0.8) {
                // 负载高，增加实例数
                newInstances = min(deploymentInfo.maxInstances, currentInstances + 1)
            } else if ((cpu < 0.3 && memory < 0.7) || requests < maxRequests * 0.2) {
                // 负载低，减少实例数
                newInstances = max(deploymentInfo.minInstances, currentInstances - 1)
            }
            
            // 应用新的实例数
            if (newInstances != currentInstances) {
                logger.info("调整Verticle实例数: {}={} -> {}", verticleName, currentInstances, newInstances)
                
                if (newInstances > currentInstances) {
                    // 增加实例
                    deployAdditionalVerticleInstances(verticleName, newInstances - currentInstances)
                } else {
                    // 减少实例
                    undeployExcessVerticleInstances(deploymentInfo, currentInstances - newInstances)
                }
            }
        }
    }
    
    /**
     * 部署额外的Verticle实例
     */
    private fun deployAdditionalVerticleInstances(verticleName: String, count: Int): Future<Void> {
        val promise = Promise.promise<Void>()
        
        val deploymentInfo = verticleDeployments[verticleName]
        if (deploymentInfo == null) {
            promise.fail("未找到Verticle部署信息: $verticleName")
            return promise.future()
        }
        
        // 创建部署选项
        val options = DeploymentOptions()
            .setInstances(count)
        
        // 部署Verticle
        vertx.deployVerticle(verticleName, options)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val deploymentId = ar.result()
                    
                    // 更新部署信息
                    synchronized(deploymentInfo) {
                        deploymentInfo.deploymentIds.add(deploymentId)
                        deploymentInfo.currentInstances.addAndGet(count)
                    }
                    
                    logger.info("已部署额外的Verticle实例: {}={}, deploymentId={}", verticleName, count, deploymentId)
                    promise.complete()
                } else {
                    logger.error("部署额外的Verticle实例时发生错误: {}", verticleName, ar.cause())
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 取消部署多余的Verticle实例
     */
    private fun undeployExcessVerticleInstances(deploymentInfo: VerticleDeploymentInfo, count: Int): Future<Void> {
        val promise = Promise.promise<Void>()
        
        synchronized(deploymentInfo) {
            val toUndeploy = min(count, deploymentInfo.deploymentIds.size)
            if (toUndeploy <= 0) {
                promise.complete()
                return promise.future()
            }
            
            // 取消部署最后部署的实例
            val deploymentIds = deploymentInfo.deploymentIds.takeLast(toUndeploy)
            
            // 创建取消部署Future列表
            val futures = mutableListOf<Future<Void>>()
            
            for (deploymentId in deploymentIds) {
                val undeployFuture = vertx.undeploy(deploymentId)
                    .onComplete { ar ->
                        if (ar.succeeded()) {
                            // 更新部署信息
                            synchronized(deploymentInfo) {
                                deploymentInfo.deploymentIds.remove(deploymentId)
                                deploymentInfo.currentInstances.decrementAndGet()
                            }
                            
                            logger.info("已取消部署Verticle实例: {}, deploymentId={}", deploymentInfo.verticleName, deploymentId)
                        } else {
                            logger.error("取消部署Verticle实例时发生错误: {}, deploymentId={}", deploymentInfo.verticleName, deploymentId, ar.cause())
                        }
                    }
                
                futures.add(undeployFuture)
            }
            
            // 等待所有取消部署完成
            Future.all(futures)
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        promise.complete()
                    } else {
                        promise.fail(ar.cause())
                    }
                }
        }
        
        return promise.future()
    }
    
    /**
     * 注册Verticle部署
     */
    fun registerVerticleDeployment(
        verticleName: String,
        deploymentId: String,
        instances: Int,
        minInstances: Int = 1,
        maxInstances: Int = 10
    ) {
        val deploymentInfo = verticleDeployments.computeIfAbsent(verticleName) {
            VerticleDeploymentInfo(
                verticleName = verticleName,
                minInstances = minInstances,
                maxInstances = maxInstances,
                currentInstances = AtomicInteger(0),
                deploymentIds = mutableListOf()
            )
        }
        
        // 更新部署信息
        synchronized(deploymentInfo) {
            deploymentInfo.deploymentIds.add(deploymentId)
            deploymentInfo.currentInstances.addAndGet(instances)
        }
        
        logger.info("已注册Verticle部署: {}={}, deploymentId={}, min={}, max={}", verticleName, instances, deploymentId, minInstances, maxInstances)
    }
    
    /**
     * 取消注册Verticle部署
     */
    fun unregisterVerticleDeployment(verticleName: String, deploymentId: String) {
        val deploymentInfo = verticleDeployments[verticleName]
        if (deploymentInfo != null) {
            // 更新部署信息
            synchronized(deploymentInfo) {
                val removed = deploymentInfo.deploymentIds.remove(deploymentId)
                if (removed) {
                    deploymentInfo.currentInstances.decrementAndGet()
                    logger.info("已取消注册Verticle部署: {}, deploymentId={}", verticleName, deploymentId)
                }
            }
        }
    }
    
    /**
     * 更新活跃连接数
     */
    fun updateActiveConnections(count: Int) {
        activeConnections.set(count)
    }
    
    /**
     * 更新活跃请求数
     */
    fun updateActiveRequests(count: Int) {
        activeRequests.set(count)
        
        // 更新最大并发请求数
        val current = activeRequests.get()
        var max: Int
        do {
            max = maxConcurrentRequests.get()
            if (current <= max) {
                break
            }
        } while (!maxConcurrentRequests.compareAndSet(max, current))
    }
    
    /**
     * 设置自动调整开关
     */
    fun setAutoAdjustEnabled(enabled: Boolean) {
        val oldValue = autoAdjustEnabled.getAndSet(enabled)
        if (oldValue != enabled) {
            logger.info("自动资源调整: {}", if (enabled) "已启用" else "已禁用")
            
            if (enabled) {
                // 启动自动调整
                startAutoAdjustment()
            }
        }
    }
    
    /**
     * 设置调整间隔
     */
    fun setAdjustInterval(interval: Long) {
        adjustInterval.set(interval)
        logger.info("已设置资源调整间隔: {}ms", interval)
    }
    
    /**
     * 设置工作线程池大小范围
     */
    fun setWorkerPoolSizeRange(min: Int, max: Int) {
        minWorkerPoolSize.set(min)
        maxWorkerPoolSize.set(max)
        logger.info("已设置工作线程池大小范围: {}-{}", min, max)
    }
    
    /**
     * 设置事件循环线程池大小范围
     */
    fun setEventLoopPoolSizeRange(min: Int, max: Int) {
        minEventLoopPoolSize.set(min)
        maxEventLoopPoolSize.set(max)
        logger.info("已设置事件循环线程池大小范围: {}-{}", min, max)
    }
    
    /**
     * 设置连接池大小范围
     */
    fun setConnectionPoolSizeRange(min: Int, max: Int) {
        minConnectionPoolSize.set(min)
        maxConnectionPoolSize.set(max)
        logger.info("已设置连接池大小范围: {}-{}", min, max)
    }
    
    /**
     * 获取资源使用统计信息
     */
    fun getResourceStats(): JsonObject {
        val stats = JsonObject()
            .put("cpu_cores", cpuCores)
            .put("max_memory_mb", maxMemory / (1024 * 1024))
            .put("cpu_usage", cpuUsage.get())
            .put("memory_usage", memoryUsage.get())
            .put("active_connections", activeConnections.get())
            .put("active_requests", activeRequests.get())
            .put("max_concurrent_requests", maxConcurrentRequests.get())
            .put("auto_adjust_enabled", autoAdjustEnabled.get())
            .put("adjust_interval_ms", adjustInterval.get())
            .put("last_adjust_time_ms", lastAdjustTime.get())
        
        // 添加线程池信息
        val poolsInfo = JsonObject()
            .put("worker_pool", JsonObject()
                .put("current", currentWorkerPoolSize.get())
                .put("min", minWorkerPoolSize.get())
                .put("max", maxWorkerPoolSize.get())
            )
            .put("event_loop_pool", JsonObject()
                .put("current", currentEventLoopPoolSize.get())
                .put("min", minEventLoopPoolSize.get())
                .put("max", maxEventLoopPoolSize.get())
            )
            .put("connection_pool", JsonObject()
                .put("current", currentConnectionPoolSize.get())
                .put("min", minConnectionPoolSize.get())
                .put("max", maxConnectionPoolSize.get())
            )
        
        stats.put("pools", poolsInfo)
        
        // 添加Verticle部署信息
        val deploymentsInfo = JsonObject()
        for ((verticleName, deploymentInfo) in verticleDeployments) {
            deploymentsInfo.put(verticleName, JsonObject()
                .put("current_instances", deploymentInfo.currentInstances.get())
                .put("min_instances", deploymentInfo.minInstances)
                .put("max_instances", deploymentInfo.maxInstances)
                .put("deployment_ids", deploymentInfo.deploymentIds)
            )
        }
        
        stats.put("verticle_deployments", deploymentsInfo)
        
        return stats
    }
    
    /**
     * 手动触发资源调整
     */
    fun triggerResourceAdjustment(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 调整资源
            adjustResources()
            
            // 更新上次调整时间
            lastAdjustTime.set(System.currentTimeMillis())
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("手动触发资源调整时发生错误", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: ResourceManager? = null
        
        /**
         * 获取ResourceManager的单例实例
         */
        fun getInstance(vertx: Vertx): ResourceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceManager(vertx).also { INSTANCE = it }
            }
        }
    }
}
