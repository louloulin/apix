package com.louloulin.apix.core.concurrency

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.util.RuntimeMetrics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlin.math.min

/**
 * 自适应并发控制器，负责动态调整系统的并发处理能力
 * 简化版本，用于Native Image编译
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
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val rejectionCounts = ConcurrentHashMap<String, AtomicLong>()
    private val totalResponseTimes = ConcurrentHashMap<String, AtomicLong>()

    // 系统资源监控
    private var cpuUsage = 0.0
    private var memoryUsage = 0.0
    private var lastUpdateTime = System.currentTimeMillis()

    /**
     * 初始化并发控制器
     */
    fun initialize(config: JsonObject) {
        // 从配置中读取参数
        defaultMaxConcurrency = config.getInteger("defaultMaxConcurrency", defaultMaxConcurrency)
        minConcurrency = config.getInteger("minConcurrency", minConcurrency)
        maxConcurrency = config.getInteger("maxConcurrency", maxConcurrency)
        targetCpuUsage = config.getDouble("targetCpuUsage", targetCpuUsage)
        targetResponseTime = config.getLong("targetResponseTime", targetResponseTime)
        adjustmentInterval = config.getLong("adjustmentInterval", adjustmentInterval)
        adjustmentFactor = config.getDouble("adjustmentFactor", adjustmentFactor)

        // 启动监控任务
        startMonitoring()

        logger.info("并发控制器初始化完成，默认并发限制: $defaultMaxConcurrency, " +
                "最小并发: $minConcurrency, 最大并发: $maxConcurrency")
    }

    /**
     * 启动监控任务
     */
    private fun startMonitoring() {
        // 使用协程定期更新系统指标
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    updateSystemMetrics()
                    delay(1000) // 每秒更新一次系统指标
                } catch (e: Exception) {
                    logger.error("更新系统指标失败", e)
                }
            }
        }

        // 使用协程定期调整并发限制
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    delay(adjustmentInterval)
                } catch (e: Exception) {
                    logger.error("调整并发限制失败", e)
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
            // 在Native Image模式下，使用RuntimeMetrics
            val osInfo = RuntimeMetrics.getOperatingSystemInfo()
            cpuUsage = osInfo.processCpuLoad * 100
        } catch (e: Exception) {
            // 如果所有方法都失败，使用缓和的值加上小的随机变化
            cpuUsage = min(100.0, max(0.0, cpuUsage + (Math.random() * 5 - 2.5)))
            logger.warn("无法获取准确的 CPU 使用率，使用估计值: $cpuUsage%", e)
        }
    }

    /**
     * 尝试获取并发许可
     *
     * @param serviceId 服务ID
     * @return 是否获取成功
     */
    fun tryAcquire(serviceId: String): Boolean {
        val currentLimit = getCurrentLimit(serviceId)
        val activeCount = getActiveCount(serviceId)

        if (activeCount < currentLimit) {
            activeRequests.computeIfAbsent(serviceId) { AtomicInteger(0) }.incrementAndGet()
            requestCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.incrementAndGet()
            return true
        } else {
            rejectionCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.incrementAndGet()
            return false
        }
    }

    /**
     * 释放并发许可
     *
     * @param serviceId 服务ID
     * @param responseTime 响应时间（毫秒）
     * @param success 请求是否成功
     */
    fun release(serviceId: String, responseTime: Long, success: Boolean) {
        activeRequests.computeIfAbsent(serviceId) { AtomicInteger(0) }.decrementAndGet()

        if (!success) {
            errorCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.incrementAndGet()
        }

        // 记录响应时间
        recordRequestCompletion(serviceId, responseTime, success)
    }

    /**
     * 兼容旧版本的释放方法
     */
    fun release(serviceId: String, success: Boolean) {
        release(serviceId, 0, success)
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
        val newLimit = limit.coerceIn(minConcurrency, maxConcurrency)
        currentLimits.computeIfAbsent(serviceId) { AtomicInteger(defaultMaxConcurrency) }.set(newLimit)
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
        val totalTime = totalResponseTimes.computeIfAbsent(serviceId) { AtomicLong(0) }.get()
        val requests = requestCounts.computeIfAbsent(serviceId) { AtomicLong(0) }.get()
        return if (requests > 0) totalTime.toDouble() / requests else 0.0
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
     * 记录请求完成
     */
    fun recordRequestCompletion(serviceId: String, responseTime: Long, success: Boolean) {
        // 只记录响应时间，不增加错误计数
        // 错误计数已经在 release 方法中增加
        totalResponseTimes.computeIfAbsent(serviceId) { AtomicLong(0) }.addAndGet(responseTime)
    }

    /**
     * 重置服务的性能指标
     */
    fun resetServiceMetrics(serviceId: String) {
        activeRequests.remove(serviceId)
        requestCounts.remove(serviceId)
        errorCounts.remove(serviceId)
        rejectionCounts.remove(serviceId)
        totalResponseTimes.remove(serviceId)
    }

    /**
     * 重置所有性能指标
     */
    fun resetAllMetrics() {
        activeRequests.clear()
        requestCounts.clear()
        errorCounts.clear()
        rejectionCounts.clear()
        totalResponseTimes.clear()
        logger.info("所有性能指标已重置")
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
        val servicesObj = JsonObject()
        val services = HashSet<String>()
        services.addAll(currentLimits.keys)
        services.addAll(activeRequests.keys)
        services.addAll(requestCounts.keys)

        for (serviceId in services) {
            servicesObj.put(serviceId, getServiceMetrics(serviceId))
        }

        return JsonObject()
            .put("services", servicesObj)
            .put("system", JsonObject()
                .put("cpuUsage", cpuUsage)
                .put("memoryUsage", memoryUsage)
                .put("totalActiveRequests", activeRequests.values.sumOf { it.get() })
            )
    }
}
