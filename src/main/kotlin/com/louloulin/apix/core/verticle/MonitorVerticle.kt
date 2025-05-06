package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import com.louloulin.apix.core.util.RuntimeMetrics
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 负责监控和指标收集的 Verticle
 */
class MonitorVerticle : BaseVerticle() {
    // 请求计数器
    private val requestCounter = AtomicLong(0)

    // 错误计数器
    private val errorCounter = AtomicLong(0)

    // 路由请求计数器
    private val routeRequestCounters = ConcurrentHashMap<String, AtomicLong>()

    // 服务请求计数器
    private val serviceRequestCounters = ConcurrentHashMap<String, AtomicLong>()

    // 响应时间统计（毫秒）
    private val responseTimeStats = ConcurrentHashMap<String, MutableList<Long>>()

    // 最后一次重置时间
    private var lastResetTime = System.currentTimeMillis()

    override fun registerEventBusHandlers() {
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.METRICS_GET, this::handleGetMetrics)
        vertx.eventBus().consumer<Void>(EventBusAddresses.METRICS_RESET, this::handleResetMetrics)

        // 监听请求事件
        vertx.eventBus().consumer<JsonObject>("apix.request.completed") { message ->
            val data = message.body()
            incrementRequestCounter(data)
        }

        // 监听错误事件
        vertx.eventBus().consumer<JsonObject>("apix.request.error") { message ->
            val data = message.body()
            incrementErrorCounter(data)
        }
    }

    override fun onStart(startPromise: Promise<Void>) {
        // 定期清理过期的响应时间统计数据（保留最近 1 小时的数据）
        vertx.setPeriodic(60 * 60 * 1000) { // 每小时
            val cutoffTime = System.currentTimeMillis() - (60 * 60 * 1000) // 1 小时前
            responseTimeStats.forEach { (route, times) ->
                responseTimeStats[route] = times.filter { it > cutoffTime }.toMutableList()
            }
        }

        logger.info("MonitorVerticle started successfully")
        startPromise.complete()
    }

    /**
     * 处理获取指标请求
     */
    private fun handleGetMetrics(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val type = message.body().getString("type", "all")

        when (type) {
            "system" -> sendSuccess(message, getSystemMetrics())
            "requests" -> sendSuccess(message, getRequestMetrics())
            "routes" -> sendSuccess(message, getRouteMetrics())
            "services" -> sendSuccess(message, getServiceMetrics())
            "all" -> {
                val metrics = JsonObject()
                    .put("system", getSystemMetrics())
                    .put("requests", getRequestMetrics())
                    .put("routes", getRouteMetrics())
                    .put("services", getServiceMetrics())
                    .put("lastResetTime", lastResetTime)

                sendSuccess(message, metrics)
            }
            else -> sendError(message, 400, "Invalid metrics type: $type")
        }
    }

    /**
     * 处理重置指标请求
     */
    private fun handleResetMetrics(message: io.vertx.core.eventbus.Message<Void>) {
        requestCounter.set(0)
        errorCounter.set(0)
        routeRequestCounters.clear()
        serviceRequestCounters.clear()
        responseTimeStats.clear()
        lastResetTime = System.currentTimeMillis()

        sendSuccess(message, true)
    }

    /**
     * 增加请求计数器
     */
    private fun incrementRequestCounter(data: JsonObject) {
        requestCounter.incrementAndGet()

        val routeId = data.getString("routeId")
        if (routeId != null) {
            routeRequestCounters.computeIfAbsent(routeId) { AtomicLong(0) }.incrementAndGet()
        }

        val serviceId = data.getString("serviceId")
        if (serviceId != null) {
            serviceRequestCounters.computeIfAbsent(serviceId) { AtomicLong(0) }.incrementAndGet()
        }

        val responseTime = data.getLong("responseTime")
        if (responseTime != null && routeId != null) {
            responseTimeStats.computeIfAbsent(routeId) { mutableListOf() }.add(responseTime)
        }
    }

    /**
     * 增加错误计数器
     */
    private fun incrementErrorCounter(data: JsonObject) {
        errorCounter.incrementAndGet()
    }

    /**
     * 获取系统指标
     */
    private fun getSystemMetrics(): JsonObject {
        val runtime = Runtime.getRuntime()
        val startTime = System.currentTimeMillis() - vertx.deploymentIDs().size * 1000
        val heapMemoryUsage = RuntimeMetrics.getHeapMemoryUsage()
        val nonHeapMemoryUsage = RuntimeMetrics.getNonHeapMemoryUsage()
        val threadInfo = RuntimeMetrics.getThreadInfo()

        return JsonObject()
            .put("jvm", JsonObject()
                .put("uptime", startTime)
                .put("heapMemory", JsonObject()
                    .put("init", heapMemoryUsage.init)
                    .put("used", heapMemoryUsage.used)
                    .put("committed", heapMemoryUsage.committed)
                    .put("max", heapMemoryUsage.max)
                )
                .put("nonHeapMemory", JsonObject()
                    .put("init", nonHeapMemoryUsage.init)
                    .put("used", nonHeapMemoryUsage.used)
                    .put("committed", nonHeapMemoryUsage.committed)
                    .put("max", nonHeapMemoryUsage.max)
                )
                .put("threads", JsonObject()
                    .put("count", threadInfo.threadCount)
                    .put("peakCount", threadInfo.peakThreadCount)
                    .put("daemonCount", threadInfo.daemonThreadCount)
                    .put("totalStarted", threadInfo.totalStartedThreadCount)
                )
            )
            .put("system", JsonObject()
                .put("availableProcessors", runtime.availableProcessors())
                .put("freeMemory", runtime.freeMemory())
                .put("totalMemory", runtime.totalMemory())
                .put("maxMemory", runtime.maxMemory())
            )
    }

    /**
     * 获取请求指标
     */
    private fun getRequestMetrics(): JsonObject {
        return JsonObject()
            .put("total", requestCounter.get())
            .put("errors", errorCounter.get())
            .put("successRate", if (requestCounter.get() > 0)
                (requestCounter.get() - errorCounter.get()) * 100.0 / requestCounter.get()
                else 100.0
            )
    }

    /**
     * 获取路由指标
     */
    private fun getRouteMetrics(): JsonArray {
        val metrics = JsonArray()

        routeRequestCounters.forEach { (routeId, counter) ->
            val times = responseTimeStats[routeId] ?: emptyList()
            val avgResponseTime = if (times.isNotEmpty()) times.average() else 0.0

            metrics.add(JsonObject()
                .put("routeId", routeId)
                .put("requests", counter.get())
                .put("avgResponseTime", avgResponseTime)
            )
        }

        return metrics
    }

    /**
     * 获取服务指标
     */
    private fun getServiceMetrics(): JsonArray {
        val metrics = JsonArray()

        serviceRequestCounters.forEach { (serviceId, counter) ->
            metrics.add(JsonObject()
                .put("serviceId", serviceId)
                .put("requests", counter.get())
            )
        }

        return metrics
    }
}
