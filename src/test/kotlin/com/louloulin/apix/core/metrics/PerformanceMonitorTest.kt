package com.louloulin.apix.core.metrics

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerOptions
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
 * 性能监控器测试类
 */
@ExtendWith(VertxExtension::class)
class PerformanceMonitorTest {
    private lateinit var vertx: Vertx
    private lateinit var performanceMonitor: PerformanceMonitor
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        performanceMonitor = PerformanceMonitor.getInstance(vertx)
        
        // 创建一个测试HTTP服务器
        val router = Router.router(vertx)
        
        // 添加性能监控中间件
        router.route().handler(performanceMonitor.createPerformanceMonitorHandler())
        
        // 添加测试路由
        router.get("/test/fast").handler { ctx ->
            ctx.response().end("Fast response")
        }
        
        router.get("/test/slow").handler { ctx ->
            // 模拟慢请求
            vertx.setTimer(1500) {
                ctx.response().end("Slow response")
            }
        }
        
        router.get("/test/error").handler { ctx ->
            ctx.response().setStatusCode(500).end("Error response")
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
     * 测试性能监控中间件
     */
    @Test
    fun testPerformanceMonitorMiddleware(testContext: VertxTestContext) {
        // 发送一些请求
        val client = vertx.createHttpClient()
        
        // 发送快速请求
        client.request(HttpMethod.GET, 8888, "localhost", "/test/fast")
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(200, response.statusCode())
                    assertTrue(response.headers().contains("X-Response-Time"))
                }
                
                // 发送慢请求
                client.request(HttpMethod.GET, 8888, "localhost", "/test/slow")
            }
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(200, response.statusCode())
                    assertTrue(response.headers().contains("X-Response-Time"))
                }
                
                // 发送错误请求
                client.request(HttpMethod.GET, 8888, "localhost", "/test/error")
            }
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(500, response.statusCode())
                    assertTrue(response.headers().contains("X-Response-Time"))
                }
                
                // 等待一段时间，确保所有请求都被处理
                vertx.setTimer(1000) { Future.succeededFuture() }
            }
            .compose { _ ->
                // 获取性能统计信息
                Future.succeededFuture(performanceMonitor.getStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    assertNotNull(stats)
                    
                    // 验证请求计数
                    assertEquals(3, stats.getLong("requests"))
                    assertEquals(1, stats.getLong("errors"))
                    
                    // 验证路径统计信息
                    val pathStats = stats.getJsonObject("paths")
                    assertNotNull(pathStats)
                    assertTrue(pathStats.containsKey("/test/fast"))
                    assertTrue(pathStats.containsKey("/test/slow"))
                    assertTrue(pathStats.containsKey("/test/error"))
                    
                    // 验证状态码统计信息
                    val statusCodeStats = stats.getJsonObject("status_codes")
                    assertNotNull(statusCodeStats)
                    assertEquals(2, statusCodeStats.getLong("200"))
                    assertEquals(1, statusCodeStats.getLong("500"))
                    
                    // 验证延迟统计信息
                    val latency = stats.getJsonObject("latency")
                    assertNotNull(latency)
                    assertEquals(3, latency.getLong("count"))
                    
                    // 获取慢请求信息
                    val slowRequests = performanceMonitor.getSlowRequests()
                    assertNotNull(slowRequests)
                    assertTrue(slowRequests.containsKey("/test/slow"))
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试设置慢请求阈值
     */
    @Test
    fun testSetSlowRequestThreshold(testContext: VertxTestContext) {
        // 设置慢请求阈值
        performanceMonitor.setSlowRequestThreshold(500)
        
        // 发送一个请求
        val client = vertx.createHttpClient()
        
        client.request(HttpMethod.GET, 8888, "localhost", "/test/fast")
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(200, response.statusCode())
                }
                
                // 等待一段时间，确保请求被处理
                vertx.setTimer(1000) { Future.succeededFuture() }
            }
            .compose { _ ->
                // 获取性能统计信息
                Future.succeededFuture(performanceMonitor.getStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    assertNotNull(stats)
                    
                    // 验证慢请求阈值
                    assertEquals(500, stats.getLong("slow_request_threshold_ms"))
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试重置统计信息
     */
    @Test
    fun testResetStats(testContext: VertxTestContext) {
        // 发送一个请求
        val client = vertx.createHttpClient()
        
        client.request(HttpMethod.GET, 8888, "localhost", "/test/fast")
            .compose { request -> request.send() }
            .compose { response ->
                testContext.verify {
                    assertEquals(200, response.statusCode())
                }
                
                // 等待一段时间，确保请求被处理
                vertx.setTimer(1000) { Future.succeededFuture() }
            }
            .compose { _ ->
                // 获取性能统计信息
                val stats = performanceMonitor.getStats()
                testContext.verify {
                    assertEquals(1, stats.getLong("requests"))
                }
                
                // 重置统计信息
                performanceMonitor.resetStats()
                
                // 再次获取性能统计信息
                Future.succeededFuture(performanceMonitor.getStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    assertNotNull(stats)
                    
                    // 验证请求计数已重置
                    assertEquals(0, stats.getLong("requests"))
                    
                    testContext.completeNow()
                }
            }
    }
}
