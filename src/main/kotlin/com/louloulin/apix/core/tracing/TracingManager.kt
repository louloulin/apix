package com.louloulin.apix.core.tracing

import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 追踪管理器，用于实现分布式追踪功能。
 * 支持与Zipkin、Jaeger等追踪系统集成。
 */
class TracingManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(TracingManager::class.java)
    
    // 追踪配置
    private var tracingEnabled = false
    private var samplingRate = 0.1 // 默认采样率10%
    
    // 活跃的追踪
    private val activeTraces = ConcurrentHashMap<String, Trace>()
    
    init {
        // 注册EventBus处理器
        registerEventBusHandlers()
        
        logger.info("追踪管理器初始化完成")
    }
    
    /**
     * 注册EventBus处理器
     */
    private fun registerEventBusHandlers() {
        // 设置追踪配置
        vertx.eventBus().consumer<JsonObject>("tracing.configure") { message ->
            val body = message.body()
            val enabled = body.getBoolean("enabled", tracingEnabled)
            val rate = body.getDouble("samplingRate", samplingRate)
            
            configureTracing(enabled, rate)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("enabled", tracingEnabled)
                .put("samplingRate", samplingRate)
            )
        }
        
        // 获取追踪统计信息
        vertx.eventBus().consumer<JsonObject>("tracing.stats") { message ->
            val stats = getTracingStats()
            message.reply(stats)
        }
    }
    
    /**
     * 配置追踪
     * 
     * @param enabled 是否启用追踪
     * @param samplingRate 采样率（0-1之间的值）
     */
    fun configureTracing(enabled: Boolean, samplingRate: Double) {
        this.tracingEnabled = enabled
        this.samplingRate = samplingRate.coerceIn(0.0, 1.0)
        
        logger.info("追踪配置已更新: enabled={}, samplingRate={}", tracingEnabled, samplingRate)
    }
    
    /**
     * 开始请求追踪
     * 
     * @param request HTTP请求
     * @return 追踪ID
     */
    fun startRequestTrace(request: HttpServerRequest): String {
        if (!tracingEnabled || !shouldSample()) {
            return ""
        }
        
        // 从请求头中获取追踪ID，如果没有则创建新的
        val traceId = request.getHeader("X-Trace-ID") ?: UUID.randomUUID().toString()
        
        // 创建追踪
        val trace = Trace(
            id = traceId,
            path = request.path(),
            method = request.method().name(),
            startTime = System.currentTimeMillis(),
            clientIp = request.remoteAddress().host()
        )
        
        // 存储追踪
        activeTraces[traceId] = trace
        
        // 添加请求头
        request.headers().set("X-Trace-ID", traceId)
        
        return traceId
    }
    
    /**
     * 结束请求追踪
     * 
     * @param traceId 追踪ID
     * @param statusCode HTTP状态码
     * @param error 错误信息（如果有）
     */
    fun endRequestTrace(traceId: String, statusCode: Int, error: String? = null) {
        if (traceId.isEmpty() || !tracingEnabled) {
            return
        }
        
        val trace = activeTraces[traceId] ?: return
        
        // 更新追踪信息
        trace.endTime = System.currentTimeMillis()
        trace.duration = trace.endTime - trace.startTime
        trace.statusCode = statusCode
        trace.error = error
        
        // 发送追踪信息
        sendTrace(trace)
        
        // 移除活跃追踪
        activeTraces.remove(traceId)
    }
    
    /**
     * 添加追踪标签
     * 
     * @param traceId 追踪ID
     * @param key 标签键
     * @param value 标签值
     */
    fun addTag(traceId: String, key: String, value: String) {
        if (traceId.isEmpty() || !tracingEnabled) {
            return
        }
        
        val trace = activeTraces[traceId] ?: return
        trace.tags[key] = value
    }
    
    /**
     * 添加追踪事件
     * 
     * @param traceId 追踪ID
     * @param name 事件名称
     * @param data 事件数据
     */
    fun addEvent(traceId: String, name: String, data: JsonObject? = null) {
        if (traceId.isEmpty() || !tracingEnabled) {
            return
        }
        
        val trace = activeTraces[traceId] ?: return
        
        val event = TraceEvent(
            name = name,
            timestamp = System.currentTimeMillis(),
            data = data
        )
        
        trace.events.add(event)
    }
    
    /**
     * 发送追踪信息到追踪系统
     * 
     * @param trace 追踪信息
     */
    private fun sendTrace(trace: Trace) {
        // 这里可以实现与Zipkin、Jaeger等追踪系统的集成
        // 目前只是记录日志
        
        if (trace.error != null) {
            logger.warn("请求追踪: {} {} - {}ms - 状态码: {} - 错误: {}", 
                trace.method, trace.path, trace.duration, trace.statusCode, trace.error)
        } else if (trace.duration > 1000) {
            logger.warn("请求追踪: {} {} - {}ms - 状态码: {} - 慢请求", 
                trace.method, trace.path, trace.duration, trace.statusCode)
        } else {
            logger.debug("请求追踪: {} {} - {}ms - 状态码: {}", 
                trace.method, trace.path, trace.duration, trace.statusCode)
        }
    }
    
    /**
     * 获取追踪统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getTracingStats(): JsonObject {
        val stats = JsonObject()
            .put("enabled", tracingEnabled)
            .put("samplingRate", samplingRate)
            .put("activeTraces", activeTraces.size)
        
        // 添加路径统计
        val pathStats = JsonObject()
        activeTraces.values.groupBy { it.path }.forEach { (path, traces) ->
            pathStats.put(path, traces.size)
        }
        stats.put("pathStats", pathStats)
        
        return stats
    }
    
    /**
     * 判断是否应该采样
     * 
     * @return 是否采样
     */
    private fun shouldSample(): Boolean {
        return Math.random() < samplingRate
    }
    
    /**
     * 创建追踪中间件
     * 
     * @return 追踪中间件处理器
     */
    fun createTracingHandler(): (RoutingContext) -> Unit {
        return { context ->
            val request = context.request()
            val traceId = startRequestTrace(request)
            
            // 将追踪ID添加到上下文中
            context.put("traceId", traceId)
            
            // 添加响应处理器
            context.addHeadersEndHandler { v ->
                val response = context.response()
                
                // 添加追踪ID到响应头
                if (traceId.isNotEmpty()) {
                    response.putHeader("X-Trace-ID", traceId)
                }
                
                // 结束追踪
                endRequestTrace(
                    traceId = traceId,
                    statusCode = response.statusCode,
                    error = context.failure()?.message
                )
            }
            
            // 继续处理请求
            context.next()
        }
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: TracingManager? = null
        
        /**
         * 获取TracingManager的单例实例
         * 
         * @param vertx Vertx实例
         * @return TracingManager实例
         */
        fun getInstance(vertx: Vertx): TracingManager {
            if (INSTANCE == null) {
                synchronized(TracingManager::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = TracingManager(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
    
    /**
     * 追踪信息
     */
    data class Trace(
        val id: String,
        val path: String,
        val method: String,
        val startTime: Long,
        val clientIp: String,
        var endTime: Long = 0,
        var duration: Long = 0,
        var statusCode: Int = 0,
        var error: String? = null,
        val tags: MutableMap<String, String> = mutableMapOf(),
        val events: MutableList<TraceEvent> = mutableListOf()
    )
    
    /**
     * 追踪事件
     */
    data class TraceEvent(
        val name: String,
        val timestamp: Long,
        val data: JsonObject? = null
    )
}
