package com.louloulin.apix.core.thread

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 线程模型优化器，用于动态调整和优化Vert.x线程模型。
 * 
 * 主要功能：
 * 1. 监控系统性能指标
 * 2. 动态调整线程池大小
 * 3. 优化线程亲和性
 * 4. 提供线程模型配置建议
 */
class ThreadModelOptimizer private constructor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ThreadModelOptimizer::class.java)
    
    // 线程池配置
    private val eventLoopPoolSize = AtomicInteger(0)
    private val workerPoolSize = AtomicInteger(0)
    private val internalBlockingPoolSize = AtomicInteger(0)
    
    // 性能指标
    private val cpuUsage = AtomicReference<Double>(0.0)
    private val systemLoadAverage = AtomicReference<Double>(0.0)
    private val threadCount = AtomicInteger(0)
    private val peakThreadCount = AtomicInteger(0)
    private val eventLoopBlockedCount = AtomicLong(0)
    private val workerBlockedCount = AtomicLong(0)
    
    // 历史数据
    private val cpuHistory = ArrayList<Double>()
    private val threadHistory = ArrayList<Int>()
    private val eventLoopBlockedHistory = ArrayList<Long>()
    private val workerBlockedHistory = ArrayList<Long>()
    
    // 配置
    private var monitoringInterval = 5000L // 监控间隔（毫秒）
    private var adjustmentInterval = 30000L // 调整间隔（毫秒）
    private var cpuTargetUsage = 70.0 // 目标CPU使用率（百分比）
    private var maxEventLoopPoolSize = 32 // 最大事件循环池大小
    private var maxWorkerPoolSize = 128 // 最大工作线程池大小
    private var maxInternalBlockingPoolSize = 64 // 最大内部阻塞池大小
    private var enabled = true // 是否启用优化器
    
    // 线程池名称映射
    private val threadPoolNames = ConcurrentHashMap<String, String>()
    
    // 线程亲和性配置
    private var threadAffinityEnabled = false
    private val threadToCoreMappings = ConcurrentHashMap<Long, Int>()
    
    // 操作系统和JVM信息
    private val osBean = ManagementFactory.getOperatingSystemMXBean()
    private val threadBean = ManagementFactory.getThreadMXBean()
    private val runtimeBean = ManagementFactory.getRuntimeMXBean()
    
    // 定时器ID
    private var monitoringTimerId = -1L
    private var adjustmentTimerId = -1L
    
    /**
     * 初始化线程模型优化器
     * 
     * @param config 配置
     */
    fun initialize(config: JsonObject) {
        // 读取配置
        monitoringInterval = config.getLong("monitoringInterval", monitoringInterval)
        adjustmentInterval = config.getLong("adjustmentInterval", adjustmentInterval)
        cpuTargetUsage = config.getDouble("cpuTargetUsage", cpuTargetUsage)
        maxEventLoopPoolSize = config.getInteger("maxEventLoopPoolSize", maxEventLoopPoolSize)
        maxWorkerPoolSize = config.getInteger("maxWorkerPoolSize", maxWorkerPoolSize)
        maxInternalBlockingPoolSize = config.getInteger("maxInternalBlockingPoolSize", maxInternalBlockingPoolSize)
        enabled = config.getBoolean("enabled", enabled)
        threadAffinityEnabled = config.getBoolean("threadAffinityEnabled", threadAffinityEnabled)
        
        // 获取当前线程池大小
        try {
            val vertxField = Vertx::class.java.getDeclaredField("delegate")
            vertxField.isAccessible = true
            val vertxInternal = vertxField.get(vertx)
            
            // 获取事件循环池大小
            val eventLoopField = vertxInternal.javaClass.getDeclaredField("eventLoopThreads")
            eventLoopField.isAccessible = true
            eventLoopPoolSize.set(eventLoopField.getInt(vertxInternal))
            
            // 获取工作线程池大小
            val workerField = vertxInternal.javaClass.getDeclaredField("workerPoolSize")
            workerField.isAccessible = true
            workerPoolSize.set(workerField.getInt(vertxInternal))
            
            // 获取内部阻塞池大小
            val internalBlockingField = vertxInternal.javaClass.getDeclaredField("internalBlockingPoolSize")
            internalBlockingField.isAccessible = true
            internalBlockingPoolSize.set(internalBlockingField.getInt(vertxInternal))
        } catch (e: Exception) {
            logger.warn("无法获取Vert.x线程池大小，使用默认值", e)
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            eventLoopPoolSize.set(availableProcessors * 2)
            workerPoolSize.set(availableProcessors * 4)
            internalBlockingPoolSize.set(availableProcessors * 2)
        }
        
        // 启动监控
        if (enabled) {
            startMonitoring()
        }
        
        logger.info("线程模型优化器初始化完成，监控间隔: ${monitoringInterval}ms, 调整间隔: ${adjustmentInterval}ms")
    }
    
    /**
     * 启动监控
     */
    fun startMonitoring() {
        if (monitoringTimerId != -1L) {
            vertx.cancelTimer(monitoringTimerId)
        }
        
        if (adjustmentTimerId != -1L) {
            vertx.cancelTimer(adjustmentTimerId)
        }
        
        // 启动监控定时器
        monitoringTimerId = vertx.setPeriodic(monitoringInterval) { _ ->
            collectMetrics()
        }
        
        // 启动调整定时器
        adjustmentTimerId = vertx.setPeriodic(adjustmentInterval) { _ ->
            adjustThreadPools()
        }
        
        logger.info("线程模型监控已启动")
    }
    
    /**
     * 停止监控
     */
    fun stopMonitoring() {
        if (monitoringTimerId != -1L) {
            vertx.cancelTimer(monitoringTimerId)
            monitoringTimerId = -1L
        }
        
        if (adjustmentTimerId != -1L) {
            vertx.cancelTimer(adjustmentTimerId)
            adjustmentTimerId = -1L
        }
        
        logger.info("线程模型监控已停止")
    }
    
    /**
     * 收集性能指标
     */
    private fun collectMetrics() {
        try {
            // 获取CPU使用率
            if (osBean is com.sun.management.OperatingSystemMXBean) {
                val sunOsBean = osBean as com.sun.management.OperatingSystemMXBean
                cpuUsage.set(sunOsBean.processCpuLoad * 100)
            }
            
            // 获取系统负载
            systemLoadAverage.set(osBean.systemLoadAverage)
            
            // 获取线程数
            threadCount.set(threadBean.threadCount)
            peakThreadCount.set(threadBean.peakThreadCount)
            
            // 获取阻塞线程数
            val blockedThreads = threadBean.allThreadIds
                .map { threadBean.getThreadInfo(it) }
                .count { it != null && it.threadState == Thread.State.BLOCKED }
            
            // 更新历史数据
            synchronized(cpuHistory) {
                cpuHistory.add(cpuUsage.get())
                threadHistory.add(threadCount.get())
                
                // 保持历史数据不超过100条
                if (cpuHistory.size > 100) {
                    cpuHistory.removeAt(0)
                }
                
                if (threadHistory.size > 100) {
                    threadHistory.removeAt(0)
                }
            }
            
            // 记录日志
            logger.debug("CPU使用率: ${cpuUsage.get()}%, 系统负载: ${systemLoadAverage.get()}, " +
                    "线程数: ${threadCount.get()}, 阻塞线程数: $blockedThreads")
        } catch (e: Exception) {
            logger.error("收集性能指标失败", e)
        }
    }
    
    /**
     * 调整线程池大小
     */
    private fun adjustThreadPools() {
        try {
            // 计算平均CPU使用率
            val avgCpuUsage = synchronized(cpuHistory) {
                if (cpuHistory.isEmpty()) 0.0 else cpuHistory.average()
            }
            
            // 根据CPU使用率调整线程池大小
            if (avgCpuUsage > cpuTargetUsage + 10) {
                // CPU使用率过高，减少线程池大小
                decreaseThreadPools()
            } else if (avgCpuUsage < cpuTargetUsage - 10) {
                // CPU使用率过低，增加线程池大小
                increaseThreadPools()
            }
            
            // 优化线程亲和性
            if (threadAffinityEnabled) {
                optimizeThreadAffinity()
            }
        } catch (e: Exception) {
            logger.error("调整线程池大小失败", e)
        }
    }
    
    /**
     * 增加线程池大小
     */
    private fun increaseThreadPools() {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        
        // 增加事件循环池大小
        val newEventLoopSize = Math.min(eventLoopPoolSize.get() + 2, maxEventLoopPoolSize)
        if (newEventLoopSize > eventLoopPoolSize.get()) {
            logger.info("增加事件循环池大小: ${eventLoopPoolSize.get()} -> $newEventLoopSize")
            eventLoopPoolSize.set(newEventLoopSize)
            System.setProperty("vertx.eventLoopPoolSize", newEventLoopSize.toString())
        }
        
        // 增加工作线程池大小
        val newWorkerSize = Math.min(workerPoolSize.get() + 4, maxWorkerPoolSize)
        if (newWorkerSize > workerPoolSize.get()) {
            logger.info("增加工作线程池大小: ${workerPoolSize.get()} -> $newWorkerSize")
            workerPoolSize.set(newWorkerSize)
            System.setProperty("vertx.workerPoolSize", newWorkerSize.toString())
        }
    }
    
    /**
     * 减少线程池大小
     */
    private fun decreaseThreadPools() {
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        
        // 减少事件循环池大小，但不低于处理器核心数
        val newEventLoopSize = Math.max(eventLoopPoolSize.get() - 2, availableProcessors)
        if (newEventLoopSize < eventLoopPoolSize.get()) {
            logger.info("减少事件循环池大小: ${eventLoopPoolSize.get()} -> $newEventLoopSize")
            eventLoopPoolSize.set(newEventLoopSize)
            System.setProperty("vertx.eventLoopPoolSize", newEventLoopSize.toString())
        }
        
        // 减少工作线程池大小，但不低于处理器核心数的2倍
        val newWorkerSize = Math.max(workerPoolSize.get() - 4, availableProcessors * 2)
        if (newWorkerSize < workerPoolSize.get()) {
            logger.info("减少工作线程池大小: ${workerPoolSize.get()} -> $newWorkerSize")
            workerPoolSize.set(newWorkerSize)
            System.setProperty("vertx.workerPoolSize", newWorkerSize.toString())
        }
    }
    
    /**
     * 优化线程亲和性
     */
    private fun optimizeThreadAffinity() {
        // 此功能仅在Linux系统上支持
        if (!isLinux()) {
            return
        }
        
        try {
            // 获取所有线程
            val threadIds = threadBean.allThreadIds
            val availableProcessors = Runtime.getRuntime().availableProcessors()
            
            // 为每个线程分配一个处理器核心
            for (threadId in threadIds) {
                if (!threadToCoreMappings.containsKey(threadId)) {
                    val coreId = threadId.toInt() % availableProcessors
                    threadToCoreMappings[threadId] = coreId
                    
                    // 在Linux系统上设置线程亲和性
                    // 注意：这需要JNA库支持，这里只是示例代码
                    // setThreadAffinity(threadId, coreId)
                }
            }
        } catch (e: Exception) {
            logger.error("优化线程亲和性失败", e)
        }
    }
    
    /**
     * 获取线程模型统计信息
     */
    fun getThreadModelStats(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            val stats = JsonObject()
                .put("eventLoopPoolSize", eventLoopPoolSize.get())
                .put("workerPoolSize", workerPoolSize.get())
                .put("internalBlockingPoolSize", internalBlockingPoolSize.get())
                .put("cpuUsage", cpuUsage.get())
                .put("systemLoadAverage", systemLoadAverage.get())
                .put("threadCount", threadCount.get())
                .put("peakThreadCount", peakThreadCount.get())
                .put("eventLoopBlockedCount", eventLoopBlockedCount.get())
                .put("workerBlockedCount", workerBlockedCount.get())
                .put("enabled", enabled)
                .put("threadAffinityEnabled", threadAffinityEnabled)
            
            // 添加历史数据
            val cpuHistoryArray = io.vertx.core.json.JsonArray()
            synchronized(cpuHistory) {
                cpuHistory.forEach { cpuHistoryArray.add(it) }
            }
            stats.put("cpuHistory", cpuHistoryArray)
            
            val threadHistoryArray = io.vertx.core.json.JsonArray()
            synchronized(threadHistory) {
                threadHistory.forEach { threadHistoryArray.add(it) }
            }
            stats.put("threadHistory", threadHistoryArray)
            
            // 添加线程池建议
            stats.put("recommendations", getThreadPoolRecommendations())
            
            promise.complete(stats)
        } catch (e: Exception) {
            logger.error("获取线程模型统计信息失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取线程池建议
     */
    private fun getThreadPoolRecommendations(): JsonObject {
        val recommendations = JsonObject()
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        
        // 计算平均CPU使用率
        val avgCpuUsage = synchronized(cpuHistory) {
            if (cpuHistory.isEmpty()) 0.0 else cpuHistory.average()
        }
        
        // 根据CPU使用率和系统负载提供建议
        if (avgCpuUsage > 80) {
            // CPU使用率过高
            recommendations.put("cpuUsage", "高")
                .put("eventLoopPoolSize", Math.max(availableProcessors, eventLoopPoolSize.get() - 2))
                .put("workerPoolSize", Math.max(availableProcessors * 2, workerPoolSize.get() - 4))
                .put("message", "CPU使用率过高，建议减少线程池大小以减少上下文切换")
        } else if (avgCpuUsage < 30) {
            // CPU使用率过低
            recommendations.put("cpuUsage", "低")
                .put("eventLoopPoolSize", Math.min(availableProcessors * 4, eventLoopPoolSize.get() + 2))
                .put("workerPoolSize", Math.min(availableProcessors * 8, workerPoolSize.get() + 4))
                .put("message", "CPU使用率过低，建议增加线程池大小以提高并发处理能力")
        } else {
            // CPU使用率适中
            recommendations.put("cpuUsage", "适中")
                .put("eventLoopPoolSize", eventLoopPoolSize.get())
                .put("workerPoolSize", workerPoolSize.get())
                .put("message", "CPU使用率适中，当前线程池大小合适")
        }
        
        // 添加线程亲和性建议
        if (isLinux() && !threadAffinityEnabled && availableProcessors > 4) {
            recommendations.put("threadAffinity", "建议启用线程亲和性以提高CPU缓存命中率")
        }
        
        return recommendations
    }
    
    /**
     * 检查是否为Linux系统
     */
    private fun isLinux(): Boolean {
        return System.getProperty("os.name").toLowerCase().contains("linux")
    }
    
    companion object {
        @Volatile
        private var instance: ThreadModelOptimizer? = null
        
        /**
         * 获取线程模型优化器实例
         * 
         * @param vertx Vertx实例
         * @return ThreadModelOptimizer实例
         */
        fun getInstance(vertx: Vertx): ThreadModelOptimizer {
            return instance ?: synchronized(this) {
                instance ?: ThreadModelOptimizer(vertx).also { instance = it }
            }
        }
    }
}
