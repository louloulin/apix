package com.louloulin.apix.core.monitoring

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能监控器，用于监控API性能和请求统计。
 * 提供了请求计数、响应时间统计和性能分析等功能。
 */
class PerformanceMonitor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PerformanceMonitor::class.java)

    // 请求计数器
    private val requestCounter = AtomicLong(0)

    // 错误计数器
    private val errorCounter = AtomicLong(0)

    // 路径请求计数器
    private val pathRequestCounters = ConcurrentHashMap<String, AtomicLong>()

    // 路径错误计数器
    private val pathErrorCounters = ConcurrentHashMap<String, AtomicLong>()

    // 路径响应时间（毫秒）
    private val pathResponseTimes = ConcurrentHashMap<String, ResponseTimeStats>()

    // 状态码计数器
    private val statusCodeCounters = ConcurrentHashMap<Int, AtomicLong>()

    // 慢请求阈值（毫秒）
    private var slowRequestThreshold = 1000L

    // 慢请求记录
    private val slowRequests = ConcurrentHashMap<String, MutableList<SlowRequestInfo>>()

    // 活跃请求计数
    private val activeRequests = AtomicInteger(0)

    // 最大并发请求数
    private val maxConcurrentRequests = AtomicInteger(0)

    /**
     * 初始化性能监控器
     */
    init {
        logger.info("Performance monitor initialized")

        // 定期清理旧的慢请求记录
        vertx.setPeriodic(3600000) { // 每小时清理一次
            cleanupOldSlowRequests()
        }
    }

    /**
     * 创建性能监控中间件
     *
     * @return 性能监控中间件处理器
     */
    fun createPerformanceMonitorHandler(): (RoutingContext) -> Unit {
        return { context ->
            // 记录请求开始时间
            val startTime = System.currentTimeMillis()

            // 增加活跃请求计数
            val currentActive = activeRequests.incrementAndGet()

            // 更新最大并发请求数
            updateMaxConcurrentRequests(currentActive)

            // 获取请求路径
            val path = normalizePath(context.request().path())

            // 增加请求计数
            requestCounter.incrementAndGet()
            pathRequestCounters.computeIfAbsent(path) { AtomicLong(0) }.incrementAndGet()

            // 添加响应处理器
            context.addHeadersEndHandler { v ->
                // 计算响应时间
                val responseTime = System.currentTimeMillis() - startTime

                // 减少活跃请求计数
                activeRequests.decrementAndGet()

                // 获取状态码
                val statusCode = context.response().statusCode

                // 增加状态码计数
                statusCodeCounters.computeIfAbsent(statusCode) { AtomicLong(0) }.incrementAndGet()

                // 更新响应时间统计
                updateResponseTimeStats(path, responseTime)

                // 检查是否是错误响应
                if (statusCode >= 400) {
                    errorCounter.incrementAndGet()
                    pathErrorCounters.computeIfAbsent(path) { AtomicLong(0) }.incrementAndGet()
                }

                // 检查是否是慢请求
                if (responseTime > slowRequestThreshold) {
                    recordSlowRequest(context.request(), path, responseTime, statusCode)
                }

                // 添加性能指标到响应头
                context.response().putHeader("X-Response-Time", responseTime.toString())
            }

            // 继续处理请求
            context.next()
        }
    }

    /**
     * 更新最大并发请求数
     *
     * @param currentActive 当前活跃请求数
     */
    private fun updateMaxConcurrentRequests(currentActive: Int) {
        var current = maxConcurrentRequests.get()
        while (currentActive > current) {
            if (maxConcurrentRequests.compareAndSet(current, currentActive)) {
                break
            }
            current = maxConcurrentRequests.get()
        }
    }

    /**
     * 更新响应时间统计
     *
     * @param path 请求路径
     * @param responseTime 响应时间（毫秒）
     */
    private fun updateResponseTimeStats(path: String, responseTime: Long) {
        val stats = pathResponseTimes.computeIfAbsent(path) { ResponseTimeStats() }

        synchronized(stats) {
            // 更新计数
            stats.count++

            // 更新总响应时间
            stats.totalTime += responseTime

            // 更新最小响应时间
            if (responseTime < stats.minTime || stats.minTime == 0L) {
                stats.minTime = responseTime
            }

            // 更新最大响应时间
            if (responseTime > stats.maxTime) {
                stats.maxTime = responseTime
            }

            // 更新响应时间分布
            when {
                responseTime < 10 -> stats.under10ms++
                responseTime < 50 -> stats.under50ms++
                responseTime < 100 -> stats.under100ms++
                responseTime < 500 -> stats.under500ms++
                responseTime < 1000 -> stats.under1000ms++
                else -> stats.over1000ms++
            }
        }
    }

    /**
     * 记录慢请求
     *
     * @param request HTTP请求
     * @param path 请求路径
     * @param responseTime 响应时间（毫秒）
     * @param statusCode HTTP状态码
     */
    private fun recordSlowRequest(request: HttpServerRequest, path: String, responseTime: Long, statusCode: Int) {
        val info = SlowRequestInfo(
            path = path,
            method = request.method().name(),
            responseTime = responseTime,
            timestamp = System.currentTimeMillis(),
            statusCode = statusCode,
            clientIp = request.remoteAddress().host(),
            userAgent = request.getHeader("User-Agent") ?: "Unknown"
        )

        // 添加到慢请求记录
        val pathSlowRequests = slowRequests.computeIfAbsent(path) { mutableListOf() }

        synchronized(pathSlowRequests) {
            // 限制每个路径的慢请求记录数量
            if (pathSlowRequests.size >= 100) {
                pathSlowRequests.removeAt(0)
            }

            pathSlowRequests.add(info)
        }

        logger.warn("Slow request detected: {} {} - {}ms", info.method, path, responseTime)
    }

    /**
     * 清理旧的慢请求记录
     */
    private fun cleanupOldSlowRequests() {
        val now = System.currentTimeMillis()
        val maxAge = 24 * 60 * 60 * 1000L // 24小时

        slowRequests.forEach { (path, requests) ->
            synchronized(requests) {
                val iterator = requests.iterator()
                while (iterator.hasNext()) {
                    val request = iterator.next()
                    if (now - request.timestamp > maxAge) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * 标准化路径
     *
     * @param path 原始路径
     * @return 标准化后的路径
     */
    private fun normalizePath(path: String): String {
        // 移除路径中的数字ID
        val normalized = path.replace(Regex("/\\d+"), "/{id}")

        // 移除查询参数
        val queryIndex = normalized.indexOf('?')
        return if (queryIndex >= 0) {
            normalized.substring(0, queryIndex)
        } else {
            normalized
        }
    }

    /**
     * 设置慢请求阈值
     *
     * @param threshold 阈值（毫秒）
     */
    fun setSlowRequestThreshold(threshold: Long) {
        slowRequestThreshold = threshold
        logger.info("Slow request threshold set to {}ms", threshold)
    }

    /**
     * 获取性能统计信息
     *
     * @return 包含性能统计信息的JsonObject
     */
    fun getPerformanceStats(): JsonObject {
        val stats = JsonObject()

        // 请求统计
        stats.put("requests", JsonObject()
            .put("total", requestCounter.get())
            .put("errors", errorCounter.get())
            .put("active", activeRequests.get())
            .put("maxConcurrent", maxConcurrentRequests.get())
            .put("errorRate", if (requestCounter.get() > 0) {
                errorCounter.get().toDouble() / requestCounter.get()
            } else {
                0.0
            })
        )

        // 状态码统计
        val statusCodes = JsonObject()
        statusCodeCounters.forEach { (code, count) ->
            statusCodes.put(code.toString(), count.get())
        }
        stats.put("statusCodes", statusCodes)

        // 路径统计
        val paths = JsonObject()
        pathRequestCounters.forEach { (path, count) ->
            val pathStats = JsonObject()
                .put("requests", count.get())
                .put("errors", pathErrorCounters.getOrDefault(path, AtomicLong(0)).get())

            // 添加响应时间统计
            val timeStats = pathResponseTimes[path]
            if (timeStats != null) {
                pathStats.put("responseTime", JsonObject()
                    .put("min", timeStats.minTime)
                    .put("max", timeStats.maxTime)
                    .put("avg", if (timeStats.count > 0) {
                        timeStats.totalTime.toDouble() / timeStats.count
                    } else {
                        0.0
                    })
                    .put("distribution", JsonObject()
                        .put("under10ms", timeStats.under10ms)
                        .put("under50ms", timeStats.under50ms)
                        .put("under100ms", timeStats.under100ms)
                        .put("under500ms", timeStats.under500ms)
                        .put("under1000ms", timeStats.under1000ms)
                        .put("over1000ms", timeStats.over1000ms)
                    )
                )
            }

            paths.put(path, pathStats)
        }
        stats.put("paths", paths)

        // 慢请求统计
        val slow = JsonObject()
        var totalSlowRequests = 0

        slowRequests.forEach { (path, requests) ->
            val slowRequestsArray = JsonArray()

            synchronized(requests) {
                // 只返回最近的10个慢请求
                val recentRequests = requests.takeLast(10)
                totalSlowRequests += recentRequests.size

                recentRequests.forEach { request ->
                    slowRequestsArray.add(JsonObject()
                        .put("method", request.method)
                        .put("path", request.path)
                        .put("responseTime", request.responseTime)
                        .put("timestamp", request.timestamp)
                        .put("statusCode", request.statusCode)
                        .put("clientIp", request.clientIp)
                        .put("userAgent", request.userAgent)
                    )
                }
            }

            slow.put(path, slowRequestsArray)
        }

        stats.put("slowRequests", JsonObject()
            .put("threshold", slowRequestThreshold)
            .put("total", totalSlowRequests)
            .put("details", slow)
        )

        return stats
    }

    /**
     * 重置性能统计信息
     */
    fun resetStats() {
        requestCounter.set(0)
        errorCounter.set(0)
        pathRequestCounters.clear()
        pathErrorCounters.clear()
        pathResponseTimes.clear()
        statusCodeCounters.clear()
        slowRequests.clear()
        // 不重置maxConcurrentRequests，这是一个历史最大值

        logger.info("Performance statistics reset")
    }

    /**
     * 响应时间统计类
     */
    inner class ResponseTimeStats {
        var count: Long = 0
        var totalTime: Long = 0
        var minTime: Long = 0
        var maxTime: Long = 0
        var under10ms: Long = 0
        var under50ms: Long = 0
        var under100ms: Long = 0
        var under500ms: Long = 0
        var under1000ms: Long = 0
        var over1000ms: Long = 0
    }

    /**
     * 慢请求信息类
     */
    data class SlowRequestInfo(
        val path: String,
        val method: String,
        val responseTime: Long,
        val timestamp: Long,
        val statusCode: Int,
        val clientIp: String,
        val userAgent: String
    )

    companion object {
        // 单例实例
        private var INSTANCE: PerformanceMonitor? = null

        /**
         * 获取PerformanceMonitor的单例实例
         *
         * @param vertx Vertx实例
         * @return PerformanceMonitor实例
         */
        fun getInstance(vertx: Vertx): PerformanceMonitor {
            if (INSTANCE == null) {
                synchronized(PerformanceMonitor::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = PerformanceMonitor(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
