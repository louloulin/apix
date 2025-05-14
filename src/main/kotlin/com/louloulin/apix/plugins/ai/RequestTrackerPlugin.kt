package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.AbstractPlugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * AI请求跟踪插件
 *
 * 该插件跟踪AI请求的执行情况，包括请求时间、响应时间、模型使用情况等。
 * 它可以用于监控和分析AI请求的性能和使用情况。
 *
 * 配置参数：
 * - enabled: 是否启用跟踪，默认为true
 * - trackableEndpoints: 可跟踪的端点列表，默认为所有端点
 * - excludedEndpoints: 排除的端点列表，默认为空
 * - maxTrackedRequests: 最大跟踪请求数，默认为10000
 * - cleanupIntervalSeconds: 清理间隔（秒），默认为300（5分钟）
 * - maxRequestAge: 最大请求年龄（秒），默认为3600（1小时）
 * - trackRequestBody: 是否跟踪请求体，默认为false
 * - trackResponseBody: 是否跟踪响应体，默认为false
 * - trackHeaders: 是否跟踪请求头，默认为false
 * - headerPrefix: 跟踪的请求头前缀，默认为"X-"
 */
class RequestTrackerPlugin(
    override val id: String,
    override val type: String = "requestTracker",
    override val config: PluginConfig
) : AbstractPlugin() {
    private val logger = LoggerFactory.getLogger(RequestTrackerPlugin::class.java)

    // 是否启用跟踪
    private val enabled: Boolean

    // 可跟踪的端点列表
    private val trackableEndpoints: Set<String>

    // 排除的端点列表
    private val excludedEndpoints: Set<String>

    // 最大跟踪请求数
    private val maxTrackedRequests: Int

    // 清理间隔（秒）
    private val cleanupIntervalSeconds: Long

    // 最大请求年龄（秒）
    private val maxRequestAge: Long

    // 是否跟踪请求体
    private val trackRequestBody: Boolean

    // 是否跟踪响应体
    private val trackResponseBody: Boolean

    // 是否跟踪请求头
    private val trackHeaders: Boolean

    // 跟踪的请求头前缀
    private val headerPrefix: String

    // 请求跟踪记录
    private val requestTraces = ConcurrentHashMap<String, RequestTrace>()

    // 请求计数器
    private val requestCounter = AtomicLong(0)

    // 成功请求计数器
    private val successCounter = AtomicLong(0)

    // 失败请求计数器
    private val failureCounter = AtomicLong(0)

    // 模型使用计数器
    private val modelUsageCounter = ConcurrentHashMap<String, AtomicLong>()

    // 端点使用计数器
    private val endpointUsageCounter = ConcurrentHashMap<String, AtomicLong>()

    // 响应时间统计
    private val responseTimeStats = ResponseTimeStats()

    // Vertx实例
    private lateinit var vertx: Vertx

    init {
        // 从配置中获取参数
        enabled = config.getBoolean("enabled") ?: true

        // 获取可跟踪的端点列表
        val trackableEndpointsArray = config.getJsonArray("trackableEndpoints")
        trackableEndpoints = if (trackableEndpointsArray == null || trackableEndpointsArray.isEmpty) {
            emptySet()
        } else {
            (0 until trackableEndpointsArray.size()).map { trackableEndpointsArray.getString(it) }.toSet()
        }

        // 获取排除的端点列表
        val excludedEndpointsArray = config.getJsonArray("excludedEndpoints")
        excludedEndpoints = if (excludedEndpointsArray == null || excludedEndpointsArray.isEmpty) {
            emptySet()
        } else {
            (0 until excludedEndpointsArray.size()).map { excludedEndpointsArray.getString(it) }.toSet()
        }

        // 获取其他参数
        maxTrackedRequests = config.getInteger("maxTrackedRequests") ?: 10000
        cleanupIntervalSeconds = config.getLong("cleanupIntervalSeconds") ?: 300
        maxRequestAge = config.getLong("maxRequestAge") ?: 3600
        trackRequestBody = config.getBoolean("trackRequestBody") ?: false
        trackResponseBody = config.getBoolean("trackResponseBody") ?: false
        trackHeaders = config.getBoolean("trackHeaders") ?: false
        headerPrefix = config.getString("headerPrefix") ?: "X-"

        logger.info("RequestTrackerPlugin initialized with maxTrackedRequests: {}", maxTrackedRequests)
    }

    override fun initialize(vertx: Vertx): Future<Void> {
        this.vertx = vertx

        // 启动清理任务
        startCleanupTask()

        logger.info("RequestTrackerPlugin initialized")
        return Future.succeededFuture()
    }

    /**
     * 启动清理任务
     */
    private fun startCleanupTask() {
        vertx.setPeriodic(cleanupIntervalSeconds * 1000) {
            cleanupOldTraces()
        }
    }

    /**
     * 清理旧的跟踪记录
     */
    private fun cleanupOldTraces() {
        val now = System.currentTimeMillis()
        val maxAge = maxRequestAge * 1000
        val oldTraces = requestTraces.entries
            .filter { now - it.value.startTime > maxAge }
            .map { it.key }

        oldTraces.forEach { requestTraces.remove(it) }

        logger.debug("Cleaned up {} old request traces", oldTraces.size)

        // 如果跟踪记录数量超过最大值，删除最旧的记录
        if (requestTraces.size > maxTrackedRequests) {
            val tracesToRemove = requestTraces.size - maxTrackedRequests
            val oldestTraces = requestTraces.entries
                .sortedBy { it.value.startTime }
                .take(tracesToRemove)
                .map { it.key }

            oldestTraces.forEach { requestTraces.remove(it) }

            logger.debug("Removed {} oldest request traces", oldestTraces.size)
        }
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 检查是否启用跟踪
            if (!enabled) {
                logger.debug("Tracking is disabled, skipping")
                promise.complete()
                return promise.future()
            }

            // 检查请求方法是否为POST
            if (context.request().method() != HttpMethod.POST) {
                logger.debug("Request method is not POST, skipping")
                promise.complete()
                return promise.future()
            }

            // 检查端点是否可跟踪
            val path = context.request().path()
            if (trackableEndpoints.isNotEmpty() && !trackableEndpoints.any { path.contains(it) }) {
                logger.debug("Endpoint is not trackable: {}", path)
                promise.complete()
                return promise.future()
            }

            // 检查端点是否被排除
            if (excludedEndpoints.any { path.contains(it) }) {
                logger.debug("Endpoint is excluded: {}", path)
                promise.complete()
                return promise.future()
            }

            // 生成请求ID
            val requestId = UUID.randomUUID().toString()

            // 创建请求跟踪记录
            val trace = RequestTrace(
                id = requestId,
                path = path,
                method = context.request().method().name(),
                startTime = System.currentTimeMillis()
            )

            // 获取请求体
            val body = context.body().asJsonObject()
            if (body != null) {
                // 获取模型
                val model = body.getString("model")
                if (model != null) {
                    trace.model = model

                    // 更新模型使用计数器
                    modelUsageCounter.computeIfAbsent(model) { AtomicLong(0) }.incrementAndGet()
                }

                // 跟踪请求体
                if (trackRequestBody) {
                    trace.requestBody = body.encode()
                }
            }

            // 跟踪请求头
            if (trackHeaders) {
                val headers = JsonObject()
                context.request().headers().forEach { header ->
                    if (header.key.startsWith(headerPrefix)) {
                        headers.put(header.key, header.value)
                    }
                }
                trace.requestHeaders = headers
            }

            // 保存请求跟踪记录
            requestTraces[requestId] = trace

            // 更新请求计数器
            requestCounter.incrementAndGet()

            // 更新端点使用计数器
            endpointUsageCounter.computeIfAbsent(path) { AtomicLong(0) }.incrementAndGet()

            // 设置响应拦截器
            setupResponseInterceptor(context, trace)

            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing RequestTrackerPlugin", e)
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 设置响应拦截器
     */
    private fun setupResponseInterceptor(context: RoutingContext, trace: RequestTrace) {
        // 设置响应结束处理器
        context.response().endHandler { _ ->
            try {
                // 更新请求跟踪记录
                trace.endTime = System.currentTimeMillis()
                trace.duration = trace.endTime - trace.startTime
                trace.statusCode = context.response().statusCode

                // 跟踪响应体
                if (trackResponseBody) {
                    // 注意：在响应结束时无法获取响应体
                    // 这里可以使用其他方式捕获响应体
                }

                // 更新响应时间统计
                responseTimeStats.addResponseTime(trace.duration)

                // 更新请求计数器
                if (trace.statusCode in 200..299) {
                    successCounter.incrementAndGet()
                } else {
                    failureCounter.incrementAndGet()
                }

                logger.debug("Tracked request: {} - {} - {} - {}ms", trace.id, trace.path, trace.statusCode, trace.duration)
            } catch (e: Exception) {
                logger.error("Error tracking response", e)
            }
        }

        // 继续处理请求
        context.next()
    }

    /**
     * 获取请求跟踪记录
     */
    fun getRequestTrace(id: String): RequestTrace? {
        return requestTraces[id]
    }

    /**
     * 获取所有请求跟踪记录
     */
    fun getAllRequestTraces(): List<RequestTrace> {
        return requestTraces.values.toList()
    }

    /**
     * 获取请求统计信息
     */
    fun getRequestStats(): JsonObject {
        return JsonObject()
            .put("totalRequests", requestCounter.get())
            .put("successRequests", successCounter.get())
            .put("failureRequests", failureCounter.get())
            .put("activeTraces", requestTraces.size)
            .put("responseTimeStats", responseTimeStats.toJson())
            .put("modelUsage", JsonObject().apply {
                modelUsageCounter.forEach { (model, count) ->
                    put(model, count.get())
                }
            })
            .put("endpointUsage", JsonObject().apply {
                endpointUsageCounter.forEach { (endpoint, count) ->
                    put(endpoint, count.get())
                }
            })
    }

    override fun shutdown() {
        logger.info("RequestTrackerPlugin shutdown")

        // 清空跟踪记录
        requestTraces.clear()
    }

    /**
     * 请求跟踪记录
     */
    data class RequestTrace(
        val id: String,
        val path: String,
        val method: String,
        val startTime: Long,
        var endTime: Long = 0,
        var duration: Long = 0,
        var statusCode: Int = 0,
        var model: String? = null,
        var requestBody: String? = null,
        var responseBody: String? = null,
        var requestHeaders: JsonObject? = null
    ) {
        /**
         * 转换为JSON对象
         */
        fun toJson(): JsonObject {
            return JsonObject()
                .put("id", id)
                .put("path", path)
                .put("method", method)
                .put("startTime", startTime)
                .put("endTime", endTime)
                .put("duration", duration)
                .put("statusCode", statusCode)
                .put("model", model)
                .put("requestBody", requestBody)
                .put("responseBody", responseBody)
                .put("requestHeaders", requestHeaders)
        }
    }

    /**
     * 响应时间统计
     */
    class ResponseTimeStats {
        private val count = AtomicLong(0)
        private val sum = AtomicLong(0)
        private val min = AtomicLong(Long.MAX_VALUE)
        private val max = AtomicLong(0)

        /**
         * 添加响应时间
         */
        fun addResponseTime(time: Long) {
            count.incrementAndGet()
            sum.addAndGet(time)

            // 更新最小值
            var currentMin = min.get()
            while (time < currentMin) {
                if (min.compareAndSet(currentMin, time)) {
                    break
                }
                currentMin = min.get()
            }

            // 更新最大值
            var currentMax = max.get()
            while (time > currentMax) {
                if (max.compareAndSet(currentMax, time)) {
                    break
                }
                currentMax = max.get()
            }
        }

        /**
         * 获取平均响应时间
         */
        fun getAverage(): Double {
            val currentCount = count.get()
            return if (currentCount > 0) {
                sum.get().toDouble() / currentCount
            } else {
                0.0
            }
        }

        /**
         * 转换为JSON对象
         */
        fun toJson(): JsonObject {
            return JsonObject()
                .put("count", count.get())
                .put("sum", sum.get())
                .put("min", if (min.get() == Long.MAX_VALUE) 0 else min.get())
                .put("max", max.get())
                .put("average", getAverage())
        }
    }
}

/**
 * AI请求跟踪插件工厂
 */
class RequestTrackerPluginFactory : com.louloulin.apix.plugins.PluginFactory {
    override fun create(config: PluginConfig): com.louloulin.apix.plugins.Plugin {
        return RequestTrackerPlugin(config.id, "requestTracker", config)
    }
}
