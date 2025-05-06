package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.telemetry.OpenTelemetryTracer
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * OpenTelemetry Verticle，用于管理OpenTelemetry追踪器。
 * 提供了追踪器初始化、配置和统计信息查询等功能。
 */
class OpenTelemetryVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(OpenTelemetryVerticle::class.java)
    
    // OpenTelemetry追踪器
    private lateinit var openTelemetryTracer: OpenTelemetryTracer
    
    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting OpenTelemetryVerticle")
        
        // 初始化OpenTelemetry追踪器
        openTelemetryTracer = OpenTelemetryTracer.getInstance(vertx)
        
        // 从配置中获取OpenTelemetry配置
        val config = config().getJsonObject("opentelemetry", JsonObject())
        
        // 初始化OpenTelemetry
        openTelemetryTracer.initialize(config)
        
        // 注册OpenTelemetry统计信息查询处理器
        vertx.eventBus().consumer<JsonObject>("opentelemetry.stats") { message ->
            val stats = openTelemetryTracer.getStats()
            message.reply(stats)
        }
        
        // 注册设置采样率处理器
        vertx.eventBus().consumer<JsonObject>("opentelemetry.sampling") { message ->
            val body = message.body()
            val ratio = body.getDouble("ratio", 0.1)
            
            openTelemetryTracer.setSamplingRatio(ratio)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("sampling_ratio", ratio)
            )
        }
        
        // 注册获取追踪中间件处理器
        vertx.eventBus().consumer<JsonObject>("opentelemetry.middleware") { message ->
            // 创建中间件
            val middleware = openTelemetryTracer.createTracingMiddleware()
            
            // 将中间件转换为可序列化的形式
            message.reply(JsonObject()
                .put("success", true)
                .put("message", "Tracing middleware is available through OpenTelemetryTracer.getInstance(vertx).createTracingMiddleware()")
            )
        }
        
        // 设置定期统计信息记录
        vertx.setPeriodic(60000) { // 每分钟记录一次
            val stats = openTelemetryTracer.getStats()
            logger.info("OpenTelemetry统计信息: {}", stats.encode())
        }
        
        startPromise.complete()
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping OpenTelemetryVerticle")
        stopPromise.complete()
    }
}
