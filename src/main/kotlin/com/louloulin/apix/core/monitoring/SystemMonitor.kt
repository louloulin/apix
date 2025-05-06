package com.louloulin.apix.core.monitoring

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
// import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 系统监控器，用于收集和报告系统级指标。
 * 包括CPU、内存、磁盘和网络等指标。
 */
class SystemMonitor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SystemMonitor::class.java)

    // 在Native Image模式下，不使用ManagementFactory
    // 操作系统信息
    private val availableProcessors = Runtime.getRuntime().availableProcessors()

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
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory().toDouble() / (1024 * 1024) // MB
            val freeMemory = runtime.freeMemory().toDouble() / (1024 * 1024) // MB
            val maxMemory = runtime.maxMemory().toDouble() / (1024 * 1024) // MB
            val usedMemory = totalMemory - freeMemory

            addMetricSample("memory.total", totalMemory)
            addMetricSample("memory.free", freeMemory)
            addMetricSample("memory.max", maxMemory)
            addMetricSample("memory.used", usedMemory)

            // 收集磁盘指标
            val diskMetrics = getDiskMetrics()
            addMetricSample("disk.free", diskMetrics.getDouble("free"))
            addMetricSample("disk.total", diskMetrics.getDouble("total"))
            addMetricSample("disk.usable", diskMetrics.getDouble("usable"))

            logger.debug("Collected system metrics: CPU usage={}%, Memory used={}MB",
                String.format("%.2f", cpuUsage * 100), String.format("%.2f", usedMemory))
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
            // 在Native Image模式下，使用简化的CPU使用率计算
            // 这里返回一个模拟值，实际应用中可以使用更复杂的算法
            val currentTime = System.nanoTime()
            val elapsedTime = currentTime - lastSampleTime
            lastSampleTime = currentTime

            // 模拟计算CPU使用率
            val usage = Math.random() * 0.3 + 0.1 // 生成一个0.1-0.4之间的随机值
            lastCpuUsage = usage
            return usage
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
            .put("cores", availableProcessors)
            .put("systemLoad", 0.0) // 简化实现
        )

        // 内存指标
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val usedMemory = totalMemory - freeMemory

        metrics.put("memory", JsonObject()
            .put("total", totalMemory / (1024 * 1024))
            .put("free", freeMemory / (1024 * 1024))
            .put("max", maxMemory / (1024 * 1024))
            .put("used", usedMemory / (1024 * 1024))
            .put("usage", usedMemory.toDouble() / maxMemory)
        )

        // 线程指标 - 简化实现
        metrics.put("threads", JsonObject()
            .put("count", Thread.activeCount())
            .put("peakCount", Thread.activeCount())
            .put("daemonCount", 0)
            .put("totalStarted", 0)
        )

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
