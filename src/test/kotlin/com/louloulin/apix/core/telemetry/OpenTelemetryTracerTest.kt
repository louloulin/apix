package com.louloulin.apix.core.telemetry

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * OpenTelemetry追踪器测试类
 */
@ExtendWith(VertxExtension::class)
class OpenTelemetryTracerTest {
    private lateinit var vertx: Vertx
    private lateinit var openTelemetryTracer: OpenTelemetryTracer
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        openTelemetryTracer = OpenTelemetryTracer.getInstance(vertx)
        
        // 初始化OpenTelemetry，使用日志导出器
        val config = JsonObject()
            .put("service_name", "apix-test")
            .put("sampling_ratio", 1.0)
            .put("exporter", JsonObject()
                .put("type", "logging")
            )
        
        openTelemetryTracer.initialize(config)
        
        // 创建一个测试HTTP服务器
        val router = Router.router(vertx)
        
        // 添加追踪中间件
        router.route().handler(openTelemetryTracer.createTracingMiddleware())
        
        // 添加测试路由
        router.get("/test/success").handler { ctx ->
            // 创建一个子Span
            val parentContext = ctx.get<io.opentelemetry.context.Context>("otel.context")
            val span = openTelemetryTracer.createSpan("test-operation", parentContext)
            
            // 添加一些属性
            span.setAttribute("test.attribute", "test-value")
            
            // 添加一个事件
            span.addEvent("test-event")
            
            // 结束Span
            span.end()
            
            // 返回成功响应
            ctx.response().end("Success")
        }
        
        router.get("/test/error").handler { ctx ->
            // 创建一个子Span
            val parentContext = ctx.get<io.opentelemetry.context.Context>("otel.context")
            val span = openTelemetryTracer.createSpan("test-error-operation", parentContext)
            
            // 设置Span状态为错误
            span.setStatus(StatusCode.ERROR, "Test error")
            
            // 记录一个异常
            span.recordException(RuntimeException("Test exception"))
            
            // 结束Span
            span.end()
            
            // 返回错误响应
            ctx.response().setStatusCode(500).end("Error")
        }
        
        // 启动HTTP服务器
        vertx.createHttpServer(HttpServerOptions().setPort(8888))
            .requestHandler(router)
            .listen()
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    /**
     * 测试获取统计信息
     */
    @Test
    fun testGetStats(testContext: VertxTestContext) {
        // 获取统计信息
        val stats = openTelemetryTracer.getStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertTrue(stats.getBoolean("initialized"))
            assertEquals("apix-test", stats.getString("service_name"))
            assertEquals(1.0, stats.getDouble("sampling_ratio"))
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试创建Span
     */
    @Test
    fun testCreateSpan(testContext: VertxTestContext) {
        // 创建一个Span
        val span = openTelemetryTracer.createSpan("test-span")
        
        // 添加一些属性
        span.setAttribute("test.attribute", "test-value")
        
        // 添加一个事件
        span.addEvent("test-event")
        
        // 结束Span
        span.end()
        
        // 获取统计信息
        val stats = openTelemetryTracer.getStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertTrue(stats.getLong("traces_created") > 0)
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试追踪中间件
     */
    @Test
    fun testTracingMiddleware(testContext: VertxTestContext) {
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 发送请求到成功路由
        client.request(HttpMethod.GET, 8888, "localhost", "/test/success")
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(200, response.statusCode())
                }
                
                // 发送请求到错误路由
                client.request(HttpMethod.GET, 8888, "localhost", "/test/error")
            }
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(500, response.statusCode())
                }
                
                // 等待一段时间，确保所有Span都被处理
                vertx.setTimer(1000) { Future.succeededFuture() }
            }
            .compose { _ ->
                // 获取统计信息
                Future.succeededFuture(openTelemetryTracer.getStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    assertNotNull(stats)
                    
                    // 验证追踪计数
                    assertTrue(stats.getLong("traces_created") >= 4) // 2个请求 + 2个子Span
                    assertTrue(stats.getLong("trace_errors") >= 1) // 至少有1个错误
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试设置采样率
     */
    @Test
    fun testSetSamplingRatio(testContext: VertxTestContext) {
        // 设置采样率
        openTelemetryTracer.setSamplingRatio(0.5)
        
        // 获取统计信息
        val stats = openTelemetryTracer.getStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertEquals(0.5, stats.getDouble("sampling_ratio"))
            
            testContext.completeNow()
        }
    }
}
