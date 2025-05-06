package com.louloulin.apix.core.monitoring

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 系统监控器，用于收集和报告系统级指标。
 * 包括CPU、内存、磁盘和网络等指标。
 */
class SystemMonitor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SystemMonitor::class.java)
    
    // 操作系统MXBean
    private val osBean = ManagementFactory.getOperatingSystemMXBean()
    
    // 内存MXBean
    private val memoryBean = ManagementFactory.getMemoryMXBean()
    
    // 线程MXBean
    private val threadBean = ManagementFactory.getThreadMXBean()
    
    // 类加载MXBean
    private val classLoadingBean = ManagementFactory.getClassLoadingMXBean()
    
    // 垃圾收集器MXBeans
    private val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    
    // 上次CPU时间
    private var lastCpuTime = 0L
    
    // 上次采样时间
    private var lastSampleTime = System.nanoTime()
    
    // 上次CPU使用率
    private var lastCpuUsage = 0.0
    
    // 指标历史
    private val metricsHistory = ConcurrentHashMap<String, List<Double>>()
    
    // 计数器
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    
    /**
     * 初始化系统监控器
     */
    init {
        logger.info("System monitor initialized")
        
        // 定期收集系统指标
        vertx.setPeriodic(5000) { // 每5秒收集一次
            collectSystemMetrics()
        }
    }
    
    /**
     * 收集系统指标
     */
    private fun collectSystemMetrics() {
        try {
            // 收集CPU指标
            val cpuUsage = getCpuUsage()
            addMetricSample("cpu.usage", cpuUsage)
            
            // 收集内存指标
            val heapMemoryUsage = memoryBean.heapMemoryUsage
            val nonHeapMemoryUsage = memoryBean.nonHeapMemoryUsage
            
            val heapUsed = heapMemoryUsage.used.toDouble() / (1024 * 1024) // MB
            val heapMax = heapMemoryUsage.max.toDouble() / (1024 * 1024) // MB
            val nonHeapUsed = nonHeapMemoryUsage.used.toDouble() / (1024 * 1024) // MB
            
            addMetricSample("memory.heap.used", heapUsed)
            addMetricSample("memory.heap.max", heapMax)
            addMetricSample("memory.nonheap.used", nonHeapUsed)
            
            // 收集线程指标
            val threadCount = threadBean.threadCount
            val peakThreadCount = threadBean.peakThreadCount
            val daemonThreadCount = threadBean.daemonThreadCount
            
            addMetricSample("thread.count", threadCount.toDouble())
            addMetricSample("thread.peak", peakThreadCount.toDouble())
            addMetricSample("thread.daemon", daemonThreadCount.toDouble())
            
            // 收集类加载指标
            val loadedClassCount = classLoadingBean.loadedClassCount
            val totalLoadedClassCount = classLoadingBean.totalLoadedClassCount
            val unloadedClassCount = classLoadingBean.unloadedClassCount
            
            addMetricSample("class.loaded", loadedClassCount.toDouble())
            addMetricSample("class.total", totalLoadedClassCount.toDouble())
            addMetricSample("class.unloaded", unloadedClassCount.toDouble())
            
            // 收集GC指标
            gcBeans.forEach { gcBean ->
                val gcName = gcBean.name.replace(" ", "_").lowercase()
                val gcCount = gcBean.collectionCount
                val gcTime = gcBean.collectionTime
                
                addMetricSample("gc.$gcName.count", gcCount.toDouble())
                addMetricSample("gc.$gcName.time", gcTime.toDouble())
            }
            
            // 收集磁盘指标
            val diskMetrics = getDiskMetrics()
            addMetricSample("disk.free", diskMetrics.getDouble("free"))
            addMetricSample("disk.total", diskMetrics.getDouble("total"))
            addMetricSample("disk.usable", diskMetrics.getDouble("usable"))
            
            logger.debug("Collected system metrics: CPU usage={}%, Heap used={}MB", 
                String.format("%.2f", cpuUsage * 100), String.format("%.2f", heapUsed))
        } catch (e: Exception) {
            logger.error("Error collecting system metrics", e)
        }
    }
    
    /**
     * 获取CPU使用率
     * 
     * @return CPU使用率（0-1之间的值）
     */
    private fun getCpuUsage(): Double {
        try {
            // 尝试使用com.sun.management.OperatingSystemMXBean（如果可用）
            val sunOsBean = osBean as? com.sun.management.OperatingSystemMXBean
            
            if (sunOsBean != null) {
                // 直接获取CPU使用率
                return sunOsBean.processCpuLoad
            } else {
                // 使用系统负载作为近似值
                return osBean.systemLoadAverage / osBean.availableProcessors
            }
        } catch (e: Exception) {
            logger.warn("Error getting CPU usage", e)
            return lastCpuUsage
        }
    }
    
    /**
     * 获取磁盘指标
     * 
     * @return 包含磁盘指标的JsonObject
     */
    private fun getDiskMetrics(): JsonObject {
        val metrics = JsonObject()
        
        try {
            val root = java.io.File("/")
            val total = root.totalSpace.toDouble() / (1024 * 1024 * 1024) // GB
            val free = root.freeSpace.toDouble() / (1024 * 1024 * 1024) // GB
            val usable = root.usableSpace.toDouble() / (1024 * 1024 * 1024) // GB
            
            metrics.put("total", total)
            metrics.put("free", free)
            metrics.put("usable", usable)
        } catch (e: Exception) {
            logger.warn("Error getting disk metrics", e)
            metrics.put("total", 0)
            metrics.put("free", 0)
            metrics.put("usable", 0)
        }
        
        return metrics
    }
    
    /**
     * 添加指标样本
     * 
     * @param name 指标名称
     * @param value 指标值
     */
    private fun addMetricSample(name: String, value: Double) {
        val samples = metricsHistory.getOrDefault(name, emptyList()).toMutableList()
        
        // 限制历史样本数量
        if (samples.size >= 60) { // 保留最近60个样本（5分钟）
            samples.removeAt(0)
        }
        
        samples.add(value)
        metricsHistory[name] = samples
    }
    
    /**
     * 增加计数器
     * 
     * @param name 计数器名称
     * @param value 增加的值
     */
    fun incrementCounter(name: String, value: Long = 1) {
        counters.computeIfAbsent(name) { AtomicLong(0) }.addAndGet(value)
    }
    
    /**
     * 获取计数器值
     * 
     * @param name 计数器名称
     * @return 计数器值
     */
    fun getCounter(name: String): Long {
        return counters.getOrDefault(name, AtomicLong(0)).get()
    }
    
    /**
     * 获取系统指标
     * 
     * @return 包含系统指标的JsonObject
     */
    fun getSystemMetrics(): JsonObject {
        val metrics = JsonObject()
        
        // CPU指标
        metrics.put("cpu", JsonObject()
            .put("usage", getCpuUsage())
            .put("cores", osBean.availableProcessors)
            .put("systemLoad", osBean.systemLoadAverage)
        )
        
        // 内存指标
        val heapMemoryUsage = memoryBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryBean.nonHeapMemoryUsage
        
        metrics.put("memory", JsonObject()
            .put("heap", JsonObject()
                .put("init", heapMemoryUsage.init / (1024 * 1024))
                .put("used", heapMemoryUsage.used / (1024 * 1024))
                .put("committed", heapMemoryUsage.committed / (1024 * 1024))
                .put("max", heapMemoryUsage.max / (1024 * 1024))
                .put("usage", heapMemoryUsage.used.toDouble() / heapMemoryUsage.max)
            )
            .put("nonHeap", JsonObject()
                .put("init", nonHeapMemoryUsage.init / (1024 * 1024))
                .put("used", nonHeapMemoryUsage.used / (1024 * 1024))
                .put("committed", nonHeapMemoryUsage.committed / (1024 * 1024))
                .put("max", nonHeapMemoryUsage.max / (1024 * 1024))
            )
        )
        
        // 线程指标
        metrics.put("threads", JsonObject()
            .put("count", threadBean.threadCount)
            .put("peakCount", threadBean.peakThreadCount)
            .put("daemonCount", threadBean.daemonThreadCount)
            .put("totalStarted", threadBean.totalStartedThreadCount)
        )
        
        // 类加载指标
        metrics.put("classes", JsonObject()
            .put("loaded", classLoadingBean.loadedClassCount)
            .put("totalLoaded", classLoadingBean.totalLoadedClassCount)
            .put("unloaded", classLoadingBean.unloadedClassCount)
        )
        
        // GC指标
        val gc = JsonObject()
        gcBeans.forEach { gcBean ->
            val gcName = gcBean.name.replace(" ", "_").lowercase()
            gc.put(gcName, JsonObject()
                .put("count", gcBean.collectionCount)
                .put("time", gcBean.collectionTime)
            )
        }
        metrics.put("gc", gc)
        
        // 磁盘指标
        metrics.put("disk", getDiskMetrics())
        
        // 计数器
        val countersJson = JsonObject()
        counters.forEach { (name, value) ->
            countersJson.put(name, value.get())
        }
        metrics.put("counters", countersJson)
        
        return metrics
    }
    
    /**
     * 获取指标历史
     * 
     * @param name 指标名称
     * @return 指标历史值列表
     */
    fun getMetricHistory(name: String): List<Double> {
        return metricsHistory.getOrDefault(name, emptyList())
    }
    
    /**
     * 获取所有指标历史
     * 
     * @return 包含所有指标历史的JsonObject
     */
    fun getAllMetricsHistory(): JsonObject {
        val history = JsonObject()
        
        metricsHistory.forEach { (name, values) ->
            history.put(name, values)
        }
        
        return history
    }
    
    /**
     * 重置计数器
     * 
     * @param name 计数器名称，如果为null则重置所有计数器
     */
    fun resetCounter(name: String? = null) {
        if (name != null) {
            counters.remove(name)
        } else {
            counters.clear()
        }
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: SystemMonitor? = null
        
        /**
         * 获取SystemMonitor的单例实例
         * 
         * @param vertx Vertx实例
         * @return SystemMonitor实例
         */
        fun getInstance(vertx: Vertx): SystemMonitor {
            if (INSTANCE == null) {
                synchronized(SystemMonitor::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = SystemMonitor(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
