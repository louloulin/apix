package com.louloulin.apix.core.concurrency

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.kotlin.coroutines.CoroutineVerticle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * 自适应并发控制器，负责动态调整系统的并发处理能力
 */
class ConcurrencyController(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ConcurrencyController::class.java)

    // 默认配置
    private var defaultMaxConcurrency = 100
    private var minConcurrency = 10
    private var maxConcurrency = 1000
    private var targetCpuUsage = 70.0 // 目标CPU使用率（百分比）
    private var targetResponseTime = 500L // 目标响应时间（毫秒）
    private var adjustmentInterval = 5000L // 调整间隔（毫秒）
    private var adjustmentFactor = 0.1 // 调整因子（每次调整的幅度）

    // 当前并发限制
    private val currentLimits = ConcurrentHashMap<String, AtomicInteger>()

    // 性能指标
    private val activeRequests = ConcurrentHashMap<String, AtomicInteger>()
    private val requestCounts = ConcurrentHashMap<String, AtomicLong>()
    private val responseTimes = ConcurrentHashMap<String, MovingAverage>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val rejectionCounts = ConcurrentHashMap<String, AtomicLong>()

    // 系统资源监控
    private var cpuUsage = 0.0
    private var memoryUsage = 0.0
    private var lastUpdateTime = System.currentTimeMillis()

    // 监控客户端
    private val webClient = WebClient.create(vertx)

    /**
     * 初始化并发控制器
     */
    fun initialize(config: JsonObject) {
        // 加载配置
        defaultMaxConcurrency = config.getInteger("defaultMaxConcurrency", defaultMaxConcurrency)
        minConcurrency = config.getInteger("minConcurrency", minConcurrency)
        maxConcurrency = config.getInteger("maxConcurrency", maxConcurrency)
        targetCpuUsage = config.getDouble("targetCpuUsage", targetCpuUsage)
        targetResponseTime = config.getLong("targetResponseTime", targetResponseTime)
        adjustmentInterval = config.getLong("adjustmentInterval", adjustmentInterval)
        adjustmentFactor = config.getDouble("adjustmentFactor", adjustmentFactor)

        // 启动自适应调整任务
        startAdaptiveAdjustment()

        logger.info("初始化并发控制器完成，默认最大并发数: $defaultMaxConcurrency")
    }

    /**
     * 启动自适应调整任务
     */
    private fun startAdaptiveAdjustment() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    updateSystemMetrics()
                    adjustConcurrencyLimits()
                    delay(adjustmentInterval)
                } catch (e: Exception) {
                    logger.error("自适应调整任务异常", e)
                    delay(adjustmentInterval)
                }
            }
        }
    }

    /**
     * 更新系统指标
     */
    private suspend fun updateSystemMetrics() {
        try {
            // 使用 JMX 获取真实的系统指标
            val runtime = Runtime.getRuntime()
            val processors = runtime.availableProcessors()

            // 内存使用情况
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val usedMemory = totalMemory - freeMemory

            // 计算内存使用率
            memoryUsage = (usedMemory.toDouble() / maxMemory) * 100

            // 获取 CPU 使用率
            // 注意：这里使用的是 JMX 的 OperatingSystemMXBean
            // 如果运行在不同的操作系统上，可能需要使用不同的方法
            try {
                val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()

                // 尝试使用 com.sun.management.OperatingSystemMXBean
                if (osBean is com.sun.management.OperatingSystemMXBean) {
                    cpuUsage = osBean.processCpuLoad * 100
                } else {
                    // 如果不可用，尝试使用反射
                    try {
                        val method = osBean.javaClass.getMethod("getProcessCpuLoad")
                        method.isAccessible = true
                        val cpuLoad = method.invoke(osBean) as Double
                        cpuUsage = cpuLoad * 100
                    } catch (e: Exception) {
                        // 如果反射也失败，使用系统负载作为估计
                        val systemLoadAverage = osBean.systemLoadAverage
                        if (systemLoadAverage > 0) {
                            cpuUsage = (systemLoadAverage / processors) * 100
                        }
                    }
                }
            } catch (e: Exception) {
                // 如果所有方法都失败，使用缓和的值加上小的随机变化
                cpuUsage = min(100.0, max(0.0, cpuUsage + (Math.random() * 5 - 2.5)))
                logger.warn("无法获取准确的 CPU 使用率，使用估计值: $cpuUsage%", e)
            }

            // 确保值在有效范围内
            cpuUsage = min(100.0, max(0.0, cpuUsage))
            memoryUsage = min(100.0, max(0.0, memoryUsage))

            logger.debug("系统指标更新 - CPU使用率: ${String.format("%.2f", cpuUsage)}%, 内存使用率: ${String.format("%.2f", memoryUsage)}%")
        } catch (e: Exception) {
            logger.error("更新系统指标失败", e)
        }
    }

    /**
     * 调整并发限制
     */
    private fun adjustConcurrencyLimits() {
        val now = System.currentTimeMillis()
        val elapsed = now - lastUpdateTime
        lastUpdateTime = now

        if (elapsed <= 0) return

        // 遍历所有服务，调整并发限制
        for (serviceId in currentLimits.keys) {
            try {
                val currentLimit = getCurrentLimit(serviceId)
                val activeCount = getActiveCount(serviceId)
                val avgResponseTime = getAverageResponseTime(serviceId)

                // 计算负载因子
                val cpuFactor = if (cpuUsage > targetCpuUsage) {
                    // CPU使用率高于目标，减少并发
                    (targetCpuUsage / cpuUsage).coerceIn(0.5, 1.0)
                } else {
                    // CPU使用率低于目标，增加并发
                    (cpuUsage / targetCpuUsage).coerceIn(1.0, 1.5)
                }

                // 响应时间因子
                val responseFactor = if (avgResponseTime > targetResponseTime) {
                    // 响应时间高于目标，减少并发
                    (targetResponseTime / avgResponseTime).coerceIn(0.5, 1.0)
                } else {
                    // 响应时间低于目标，增加并发
                    (avgResponseTime / targetResponseTime).coerceIn(1.0, 1.5)
                }

                // 错误率因子
                val errorRate = getErrorRate(serviceId)
                val errorFactor = if (errorRate > 0.05) { // 5%错误率阈值
                    // 错误率高，减少并发
                    (0.05 / errorRate).coerceIn(0.5, 1.0)
                } else {
                    // 错误率低，保持或增加并发
                    1.0
                }

                // 计算新的并发限制
                val combinedFactor = cpuFactor * responseFactor * errorFactor
                val adjustment = (currentLimit * adjustmentFactor * (combinedFactor - 1.0)).toInt()
                var newLimit = currentLimit + adjustment

                // 确保新限制在允许范围内
                newLimit = newLimit.coerceIn(minConcurrency, maxConcurrency)

                // 更新并发限制
                if (newLimit != currentLimit) {
                    setCurrentLimit(serviceId, newLimit)
                    logger.info("调整服务 $serviceId 的并发限制: $currentLimit -> $newLimit " +
                            "(CPU: ${String.format("%.2f", cpuUsage)}%, " +
                            "响应时间: ${String.format("%.2f", avgResponseTime)}ms, " +
                            "错误率: ${String.format("%.2f", errorRate * 100)}%, " +
                            "活动请求: $activeCount)")
                }
            } catch (e: Exception) {
                logger.error("调整服务 $serviceId 的并发限制失败", e)
            }
        }
    }

    /**
     * 获取服务的当前并发限制
     */
    fun getCurrentLimit(serviceId: String): Int {
        return currentLimits.computeIfAbsent(serviceId) { AtomicInteger(defaultMaxConcurrency) }.get()
    }

    /**
     * 设置服务的并发限制
     */
    fun setCurrentLimit(serviceId: String, limit: Int) {
        val adjustedLimit = limit.coerceIn(minConcurrency, maxConcurrency)
        currentLimits.computeIfAbsent(serviceId) { AtomicInteger(defaultMaxConcurrency) }.set(adjustedLimit)
    }

    /**
     * 获取服务的活动请求数
     */
    fun getActiveCount(serviceId: String): Int {
        return activeRequests.computeIfAbsent(serviceId) { AtomicInteger(0) }.get()
    }

    /**
     * 获取服务的平均响应时间
     */
    fun getAverageResponseTime(serviceId: String): Double {
        return responseTimes.computeIfAbsent(serviceId) { MovingAverage(100) }.average
    }

    /**
     * 获取服务的错误率
     */
    fun getErrorRate(serviceId: String): Double {
        val errors = errorCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.get()
        val requests = requestCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.get()
        return if (requests > 0) errors.toDouble() / requests else 0.0
    }

    /**
     * 获取服务的拒绝率
     */
    fun getRejectionRate(serviceId: String): Double {
        val rejections = rejectionCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.get()
        val requests = requestCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.get()
        return if (requests > 0) rejections.toDouble() / requests else 0.0
    }

    /**
     * 尝试获取并发许可
     * @return 是否获取成功
     */
    fun tryAcquire(serviceId: String): Boolean {
        val active = activeRequests.computeIfAbsent(serviceId) { AtomicInteger(0) }
        val limit = getCurrentLimit(serviceId)

        // 增加请求计数
        requestCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.incrementAndGet()

        // 检查是否超过并发限制
        if (active.get() >= limit) {
            // 增加拒绝计数
            rejectionCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.incrementAndGet()
            return false
        }

        // 增加活动请求计数
        active.incrementAndGet()
        return true
    }

    /**
     * 释放并发许可
     */
    fun release(serviceId: String, responseTime: Long, isError: Boolean) {
        // 减少活动请求计数
        activeRequests.computeIfAbsent(serviceId) { AtomicInteger(0) }.decrementAndGet()

        // 更新响应时间
        responseTimes.computeIfAbsent(serviceId) { MovingAverage(100) }.add(responseTime)

        // 如果是错误，增加错误计数
        if (isError) {
            errorCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.incrementAndGet()
        }
    }

    /**
     * 获取服务的性能指标
     */
    fun getServiceMetrics(serviceId: String): JsonObject {
        val active = getActiveCount(serviceId)
        val limit = getCurrentLimit(serviceId)
        val avgResponseTime = getAverageResponseTime(serviceId)
        val errorRate = getErrorRate(serviceId)
        val rejectionRate = getRejectionRate(serviceId)
        val requests = requestCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.get()

        return JsonObject()
            .put("serviceId", serviceId)
            .put("activeRequests", active)
            .put("concurrencyLimit", limit)
            .put("averageResponseTime", avgResponseTime)
            .put("errorRate", errorRate)
            .put("rejectionRate", rejectionRate)
            .put("totalRequests", requests)
            .put("utilizationRate", if (limit > 0) active.toDouble() / limit else 0.0)
    }

    /**
     * 获取所有服务的性能指标
     */
    fun getAllServiceMetrics(): JsonObject {
        val services = JsonObject()
        val allServiceIds = HashSet<String>()

        // 收集所有服务ID
        allServiceIds.addAll(currentLimits.keys)
        allServiceIds.addAll(activeRequests.keys)
        allServiceIds.addAll(requestCounts.keys)

        // 为每个服务创建指标
        for (serviceId in allServiceIds) {
            services.put(serviceId, getServiceMetrics(serviceId))
        }

        // 添加系统级指标
        val system = JsonObject()
            .put("cpuUsage", cpuUsage)
            .put("memoryUsage", memoryUsage)
            .put("totalActiveRequests", activeRequests.values.sumOf { it.get() })

        return JsonObject()
            .put("services", services)
            .put("system", system)
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 重置服务的指标
     */
    fun resetServiceMetrics(serviceId: String) {
        activeRequests.remove(serviceId)
        requestCounts.remove(serviceId)
        responseTimes.remove(serviceId)
        errorCounts.remove(serviceId)
        rejectionCounts.remove(serviceId)
        logger.info("重置服务 $serviceId 的性能指标")
    }

    /**
     * 重置所有服务的指标
     */
    fun resetAllMetrics() {
        activeRequests.clear()
        requestCounts.clear()
        responseTimes.clear()
        errorCounts.clear()
        rejectionCounts.clear()
        logger.info("重置所有服务的性能指标")
    }

    /**
     * 移动平均值计算器
     */
    inner class MovingAverage(private val windowSize: Int) {
        private val values = DoubleArray(windowSize)
        private var index = 0
        private var sum = 0.0
        private var count = 0

        val average: Double
            get() = if (count > 0) sum / count else 0.0

        fun add(value: Long) {
            add(value.toDouble())
        }

        fun add(value: Double) {
            if (count < windowSize) {
                // 窗口未满
                values[count] = value
                sum += value
                count++
            } else {
                // 窗口已满，替换最旧的值
                sum -= values[index]
                values[index] = value
                sum += value
                index = (index + 1) % windowSize
            }
        }

        fun reset() {
            index = 0
            sum = 0.0
            count = 0
        }
    }
}
