package com.louloulin.apix.core.integration

import com.louloulin.apix.core.connection.ConnectionWarmerManager
import com.louloulin.apix.core.eventbus.EventBusManager
import com.louloulin.apix.core.metrics.LatencyRecorder
import com.louloulin.apix.core.metrics.PerformanceMonitor
import com.louloulin.apix.core.resource.ResourceManager
import com.louloulin.apix.core.telemetry.OpenTelemetryTracer
import io.vertx.core.DeploymentOptions
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 集成测试类，用于测试所有已实现功能的协同工作。
 */
@ExtendWith(VertxExtension::class)
class IntegrationTest {
    private lateinit var vertx: Vertx
    private lateinit var eventBusManager: EventBusManager
    private lateinit var connectionWarmerManager: ConnectionWarmerManager
    private lateinit var latencyRecorder: LatencyRecorder
    private lateinit var performanceMonitor: PerformanceMonitor
    private lateinit var resourceManager: ResourceManager
    private lateinit var openTelemetryTracer: OpenTelemetryTracer
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 初始化所有组件
        eventBusManager = EventBusManager.getInstance(vertx)
        connectionWarmerManager = ConnectionWarmerManager.getInstance(vertx)
        latencyRecorder = LatencyRecorder.getInstance()
        performanceMonitor = PerformanceMonitor.getInstance(vertx)
        resourceManager = ResourceManager.getInstance(vertx)
        openTelemetryTracer = OpenTelemetryTracer.getInstance(vertx)
        
        // 初始化OpenTelemetry，使用日志导出器
        val telemetryConfig = JsonObject()
            .put("service_name", "apix-integration-test")
            .put("sampling_ratio", 1.0)
            .put("exporter", JsonObject()
                .put("type", "logging")
            )
        
        openTelemetryTracer.initialize(telemetryConfig)
        
        // 创建一个测试HTTP服务器
        val router = Router.router(vertx)
        
        // 添加性能监控中间件
        router.route().handler(performanceMonitor.createPerformanceMonitorHandler())
        
        // 添加OpenTelemetry追踪中间件
        router.route().handler(openTelemetryTracer.createTracingMiddleware())
        
        // 添加测试路由
        router.get("/api/test").handler { ctx ->
            // 记录延迟
            latencyRecorder.recordLatency("test.latency", 10, TimeUnit.MILLISECONDS)
            
            // 创建一个子Span
            val parentContext = ctx.get<io.opentelemetry.context.Context>("otel.context")
            val span = openTelemetryTracer.createSpan("test-operation", parentContext)
            
            // 使用EventBus发送消息
            eventBusManager.getEventBus().send("test.address", "test message")
            
            // 结束Span
            span.end()
            
            // 返回成功响应
            ctx.response().end(JsonObject()
                .put("success", true)
                .put("message", "Integration test successful")
                .encode()
            )
        }
        
        router.get("/api/slow").handler { ctx ->
            // 模拟慢请求
            vertx.setTimer(1500) {
                // 记录延迟
                latencyRecorder.recordLatency("test.slow", 1500, TimeUnit.MILLISECONDS)
                
                // 返回响应
                ctx.response().end(JsonObject()
                    .put("success", true)
                    .put("message", "Slow request completed")
                    .encode()
                )
            }
        }
        
        router.get("/api/error").handler { ctx ->
            // 记录延迟
            latencyRecorder.recordLatency("test.error", 50, TimeUnit.MILLISECONDS)
            
            // 创建一个子Span
            val parentContext = ctx.get<io.opentelemetry.context.Context>("otel.context")
            val span = openTelemetryTracer.createSpan("error-operation", parentContext)
            
            // 设置Span状态为错误
            span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Test error")
            
            // 结束Span
            span.end()
            
            // 返回错误响应
            ctx.response().setStatusCode(500).end(JsonObject()
                .put("success", false)
                .put("error", "Test error")
                .encode()
            )
        }
        
        // 注册EventBus消息处理器
        vertx.eventBus().consumer<String>("test.address") { message ->
            // 记录消息处理
            latencyRecorder.recordLatency("eventbus.message", 5, TimeUnit.MILLISECONDS)
            
            // 回复消息
            message.reply("Message received")
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
     * 测试所有组件的集成
     */
    @Test
    fun testIntegration(testContext: VertxTestContext) {
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 发送请求到测试路由
        client.request(HttpMethod.GET, 8888, "localhost", "/api/test")
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(200, response.statusCode())
                }
                
                // 发送请求到慢请求路由
                client.request(HttpMethod.GET, 8888, "localhost", "/api/slow")
            }
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(200, response.statusCode())
                }
                
                // 发送请求到错误路由
                client.request(HttpMethod.GET, 8888, "localhost", "/api/error")
            }
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(500, response.statusCode())
                }
                
                // 等待一段时间，确保所有异步操作完成
                vertx.setTimer(2000) { Future.succeededFuture() }
            }
            .compose { _ ->
                // 获取延迟统计信息
                val latencyStats = latencyRecorder.getAllLatencyStats()
                
                testContext.verify {
                    assertNotNull(latencyStats)
                    assertTrue(latencyStats.containsKey("test.latency"))
                    assertTrue(latencyStats.containsKey("test.slow"))
                    assertTrue(latencyStats.containsKey("test.error"))
                    assertTrue(latencyStats.containsKey("eventbus.message"))
                }
                
                // 获取性能监控统计信息
                Future.succeededFuture(performanceMonitor.getStats())
            }
            .compose { stats ->
                testContext.verify {
                    assertNotNull(stats)
                    assertEquals(3, stats.getLong("requests"))
                    assertEquals(1, stats.getLong("errors"))
                }
                
                // 获取OpenTelemetry统计信息
                Future.succeededFuture(openTelemetryTracer.getStats())
            }
            .compose { stats ->
                testContext.verify {
                    assertNotNull(stats)
                    assertTrue(stats.getLong("traces_created") > 0)
                    assertTrue(stats.getLong("trace_errors") > 0)
                }
                
                // 获取资源管理器统计信息
                Future.succeededFuture(resourceManager.getResourceStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    assertNotNull(stats)
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试EventBus类型切换
     */
    @Test
    fun testEventBusTypeSwitch(testContext: VertxTestContext) {
        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .compose { success ->
                testContext.verify {
                    assertTrue(success)
                    assertEquals(EventBusManager.EventBusType.JCTOOLS, eventBusManager.getCurrentType())
                }
                
                // 发送消息
                val message = JsonObject().put("test", "value")
                vertx.eventBus().request<JsonObject>("test.json", message)
            }
            .compose { response ->
                // 切换回原生EventBus
                eventBusManager.switchType(EventBusManager.EventBusType.VERTX)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    assertEquals(EventBusManager.EventBusType.VERTX, eventBusManager.getCurrentType())
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试连接预热
     */
    @Test
    fun testConnectionWarming(testContext: VertxTestContext) {
        // 预热连接
        connectionWarmerManager.warmConnections("localhost", 8888, 5, false)
            .compose { result ->
                testContext.verify {
                    assertTrue(result.getInteger("success", 0) > 0)
                }
                
                // 获取预热连接
                connectionWarmerManager.getWarmedConnection("localhost", 8888)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    // 连接可能为null，因为这是一个测试环境
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试资源调整
     */
    @Test
    fun testResourceAdjustment(testContext: VertxTestContext) {
        // 手动触发资源调整
        resourceManager.triggerResourceAdjustment()
            .compose { _ ->
                // 获取资源统计信息
                Future.succeededFuture(resourceManager.getResourceStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    assertNotNull(stats)
                    
                    testContext.completeNow()
                }
            }
    }
}
