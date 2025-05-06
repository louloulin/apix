package com.louloulin.apix.core.metrics

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 性能监控器，用于监控系统性能指标。
 * 使用HdrHistogram记录延迟分布，提供详细的性能统计信息。
 */
class PerformanceMonitor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PerformanceMonitor::class.java)
    
    // 延迟记录器
    private val latencyRecorder = LatencyRecorder.getInstance()
    
    // 请求计数器
    private val requestCounter = AtomicLong(0)
    
    // 错误计数器
    private val errorCounter = AtomicLong(0)
    
    // 路径请求计数器
    private val pathRequestCounters = ConcurrentHashMap<String, AtomicLong>()
    
    // 路径错误计数器
    private val pathErrorCounters = ConcurrentHashMap<String, AtomicLong>()
    
    // 状态码计数器
    private val statusCodeCounters = ConcurrentHashMap<Int, AtomicLong>()
    
    // 慢请求阈值（毫秒）
    private var slowRequestThreshold = 1000L
    
    // 慢请求记录
    private val slowRequests = ConcurrentHashMap<String, MutableList<SlowRequestInfo>>()
    
    // 活跃请求数
    private val activeRequests = AtomicInteger(0)
    
    // 最大并发请求数
    private val maxConcurrentRequests = AtomicInteger(0)
    
    // 启动时间
    private val startTime = System.currentTimeMillis()
    
    /**
     * 慢请求信息
     */
    data class SlowRequestInfo(
        val path: String,
        val method: String,
        val responseTime: Long,
        val statusCode: Int,
        val timestamp: Long
    )
    
    /**
     * 创建性能监控中间件
     */
    fun createPerformanceMonitorHandler(): Handler<RoutingContext> {
        return Handler { context ->
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
                val statusCode = context.response().statusCode()
                
                // 增加状态码计数
                statusCodeCounters.computeIfAbsent(statusCode) { AtomicLong(0) }.incrementAndGet()
                
                // 记录延迟
                latencyRecorder.recordLatency("http.request", responseTime, TimeUnit.MILLISECONDS)
                latencyRecorder.recordLatency("http.request.$path", responseTime, TimeUnit.MILLISECONDS)
                
                // 检查是否是错误响应
                if (statusCode >= 400) {
                    errorCounter.incrementAndGet()
                    pathErrorCounters.computeIfAbsent(path) { AtomicLong(0) }.incrementAndGet()
                    
                    // 记录错误延迟
                    latencyRecorder.recordLatency("http.error", responseTime, TimeUnit.MILLISECONDS)
                    latencyRecorder.recordLatency("http.error.$path", responseTime, TimeUnit.MILLISECONDS)
                }
                
                // 检查是否是慢请求
                if (responseTime > slowRequestThreshold) {
                    recordSlowRequest(context.request(), path, responseTime, statusCode)
                    
                    // 记录慢请求延迟
                    latencyRecorder.recordLatency("http.slow", responseTime, TimeUnit.MILLISECONDS)
                    latencyRecorder.recordLatency("http.slow.$path", responseTime, TimeUnit.MILLISECONDS)
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
     */
    private fun updateMaxConcurrentRequests(currentActive: Int) {
        var current: Int
        var max: Int
        do {
            current = maxConcurrentRequests.get()
            max = maxOf(current, currentActive)
            if (current == max) {
                break
            }
        } while (!maxConcurrentRequests.compareAndSet(current, max))
    }
    
    /**
     * 记录慢请求
     */
    private fun recordSlowRequest(request: HttpServerRequest, path: String, responseTime: Long, statusCode: Int) {
        val method = request.method().name()
        val timestamp = System.currentTimeMillis()
        
        val slowRequestInfo = SlowRequestInfo(path, method, responseTime, statusCode, timestamp)
        
        // 添加到慢请求列表
        slowRequests.computeIfAbsent(path) { mutableListOf() }.add(slowRequestInfo)
        
        // 限制慢请求列表大小
        val maxSlowRequests = 100
        val requests = slowRequests[path]
        if (requests != null && requests.size > maxSlowRequests) {
            synchronized(requests) {
                if (requests.size > maxSlowRequests) {
                    // 移除最旧的慢请求
                    requests.removeAt(0)
                }
            }
        }
        
        // 记录慢请求日志
        logger.warn("慢请求: path={}, method={}, responseTime={}ms, statusCode={}", path, method, responseTime, statusCode)
    }
    
    /**
     * 标准化路径
     */
    private fun normalizePath(path: String): String {
        // 移除路径中的ID等变量部分
        val segments = path.split("/")
        val normalizedSegments = mutableListOf<String>()
        
        for (segment in segments) {
            if (segment.isEmpty()) {
                continue
            }
            
            // 如果段是数字或UUID，替换为占位符
            if (segment.matches(Regex("\\d+")) || segment.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))) {
                normalizedSegments.add("{id}")
            } else {
                normalizedSegments.add(segment)
            }
        }
        
        return "/" + normalizedSegments.joinToString("/")
    }
    
    /**
     * 获取性能统计信息
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
        
        // 添加基本统计信息
        stats.put("uptime_ms", System.currentTimeMillis() - startTime)
        stats.put("requests", requestCounter.get())
        stats.put("errors", errorCounter.get())
        stats.put("active_requests", activeRequests.get())
        stats.put("max_concurrent_requests", maxConcurrentRequests.get())
        stats.put("slow_request_threshold_ms", slowRequestThreshold)
        
        // 添加延迟统计信息
        stats.put("latency", latencyRecorder.getLatencyStats("http.request"))
        
        // 添加错误延迟统计信息
        val errorLatency = latencyRecorder.getLatencyStats("http.error")
        if (errorLatency != null) {
            stats.put("error_latency", errorLatency)
        }
        
        // 添加慢请求延迟统计信息
        val slowLatency = latencyRecorder.getLatencyStats("http.slow")
        if (slowLatency != null) {
            stats.put("slow_latency", slowLatency)
        }
        
        // 添加路径统计信息
        val pathStats = JsonObject()
        for ((path, counter) in pathRequestCounters) {
            val pathStat = JsonObject()
                .put("requests", counter.get())
                .put("errors", pathErrorCounters.getOrDefault(path, AtomicLong(0)).get())
            
            // 添加路径延迟统计信息
            val pathLatency = latencyRecorder.getLatencyStats("http.request.$path")
            if (pathLatency != null) {
                pathStat.put("latency", pathLatency)
            }
            
            pathStats.put(path, pathStat)
        }
        stats.put("paths", pathStats)
        
        // 添加状态码统计信息
        val statusCodeStats = JsonObject()
        for ((statusCode, counter) in statusCodeCounters) {
            statusCodeStats.put(statusCode.toString(), counter.get())
        }
        stats.put("status_codes", statusCodeStats)
        
        return stats
    }
    
    /**
     * 获取慢请求信息
     */
    fun getSlowRequests(): JsonObject {
        val result = JsonObject()
        
        for ((path, requests) in slowRequests) {
            val requestsArray = io.vertx.core.json.JsonArray()
            
            for (request in requests) {
                requestsArray.add(JsonObject()
                    .put("path", request.path)
                    .put("method", request.method)
                    .put("response_time_ms", request.responseTime)
                    .put("status_code", request.statusCode)
                    .put("timestamp", request.timestamp)
                )
            }
            
            result.put(path, requestsArray)
        }
        
        return result
    }
    
    /**
     * 设置慢请求阈值
     */
    fun setSlowRequestThreshold(threshold: Long) {
        slowRequestThreshold = threshold
        logger.info("设置慢请求阈值: {}ms", threshold)
    }
    
    /**
     * 重置统计信息
     */
    fun resetStats() {
        requestCounter.set(0)
        errorCounter.set(0)
        activeRequests.set(0)
        maxConcurrentRequests.set(0)
        
        pathRequestCounters.clear()
        pathErrorCounters.clear()
        statusCodeCounters.clear()
        slowRequests.clear()
        
        latencyRecorder.resetAllLatencyStats()
        
        logger.info("重置性能统计信息")
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PerformanceMonitor? = null
        
        /**
         * 获取PerformanceMonitor的单例实例
         */
        fun getInstance(vertx: Vertx): PerformanceMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PerformanceMonitor(vertx).also { INSTANCE = it }
            }
        }
    }
}
