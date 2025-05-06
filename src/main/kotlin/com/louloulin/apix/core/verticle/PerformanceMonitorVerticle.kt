package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.metrics.LatencyRecorder
import com.louloulin.apix.core.metrics.PerformanceMonitor
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * 性能监控Verticle，用于管理系统性能监控。
 * 提供了性能统计信息查询、慢请求查询、统计信息重置等功能。
 */
class PerformanceMonitorVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(PerformanceMonitorVerticle::class.java)
    
    // 性能监控器
    private lateinit var performanceMonitor: PerformanceMonitor
    
    // 延迟记录器
    private val latencyRecorder = LatencyRecorder.getInstance()
    
    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting PerformanceMonitorVerticle")
        
        // 初始化性能监控器
        performanceMonitor = PerformanceMonitor.getInstance(vertx)
        
        // 注册性能统计信息查询处理器
        vertx.eventBus().consumer<JsonObject>("performance.stats") { message ->
            val stats = performanceMonitor.getStats()
            message.reply(stats)
        }
        
        // 注册慢请求查询处理器
        vertx.eventBus().consumer<JsonObject>("performance.slow") { message ->
            val slowRequests = performanceMonitor.getSlowRequests()
            message.reply(slowRequests)
        }
        
        // 注册延迟统计信息查询处理器
        vertx.eventBus().consumer<JsonObject>("performance.latency") { message ->
            val body = message.body()
            val name = body.getString("name", "http.request")
            val unit = TimeUnit.valueOf(body.getString("unit", "MILLISECONDS"))
            
            val latencyStats = latencyRecorder.getLatencyStats(name, unit)
            if (latencyStats != null) {
                message.reply(latencyStats)
            } else {
                message.reply(JsonObject()
                    .put("error", "No latency stats found for $name")
                )
            }
        }
        
        // 注册所有延迟统计信息查询处理器
        vertx.eventBus().consumer<JsonObject>("performance.latency.all") { message ->
            val body = message.body()
            val unit = TimeUnit.valueOf(body.getString("unit", "MILLISECONDS"))
            
            val allLatencyStats = latencyRecorder.getAllLatencyStats(unit)
            message.reply(allLatencyStats)
        }
        
        // 注册延迟直方图查询处理器
        vertx.eventBus().consumer<JsonObject>("performance.histogram") { message ->
            val body = message.body()
            val name = body.getString("name", "http.request")
            val unit = TimeUnit.valueOf(body.getString("unit", "MILLISECONDS"))
            
            val histogram = latencyRecorder.getHistogramChart(name, unit)
            message.reply(JsonObject()
                .put("name", name)
                .put("unit", unit.name)
                .put("histogram", histogram)
            )
        }
        
        // 注册设置慢请求阈值处理器
        vertx.eventBus().consumer<JsonObject>("performance.slow.threshold") { message ->
            val body = message.body()
            val threshold = body.getLong("threshold", 1000L)
            
            performanceMonitor.setSlowRequestThreshold(threshold)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("threshold", threshold)
            )
        }
        
        // 注册重置统计信息处理器
        vertx.eventBus().consumer<JsonObject>("performance.reset") { message ->
            performanceMonitor.resetStats()
            
            message.reply(JsonObject()
                .put("success", true)
            )
        }
        
        // 设置定期统计信息记录
        vertx.setPeriodic(60000) { // 每分钟记录一次
            val stats = performanceMonitor.getStats()
            logger.info("性能统计信息: {}", stats.encode())
        }
        
        startPromise.complete()
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping PerformanceMonitorVerticle")
        stopPromise.complete()
    }
}
