package com.louloulin.apix.core.performance

import com.louloulin.apix.core.eventbus.EventBusManager
import com.louloulin.apix.core.metrics.LatencyRecorder
import com.louloulin.apix.core.metrics.PerformanceMonitor
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
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

/**
 * 性能测试类，用于测试系统在高负载下的性能。
 */
@ExtendWith(VertxExtension::class)
class PerformanceTest {
    private val logger = LoggerFactory.getLogger(PerformanceTest::class.java)
    
    private lateinit var vertx: Vertx
    private lateinit var eventBusManager: EventBusManager
    private lateinit var latencyRecorder: LatencyRecorder
    private lateinit var performanceMonitor: PerformanceMonitor
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 初始化组件
        eventBusManager = EventBusManager.getInstance(vertx)
        latencyRecorder = LatencyRecorder.getInstance()
        performanceMonitor = PerformanceMonitor.getInstance(vertx)
        
        // 创建一个测试HTTP服务器
        val router = Router.router(vertx)
        
        // 添加性能监控中间件
        router.route().handler(performanceMonitor.createPerformanceMonitorHandler())
        
        // 添加测试路由
        router.get("/api/echo").handler { ctx ->
            // 返回请求参数
            val params = ctx.queryParams()
            val response = JsonObject()
            
            for (param in params.names()) {
                response.put(param, params.get(param))
            }
            
            ctx.response().end(response.encode())
        }
        
        router.post("/api/process").handler { ctx ->
            // 处理请求体
            ctx.request().bodyHandler { buffer ->
                try {
                    val body = buffer.toJsonObject()
                    
                    // 使用EventBus发送消息
                    eventBusManager.getEventBus().send("process.data", body)
                    
                    // 返回成功响应
                    ctx.response().end(JsonObject()
                        .put("success", true)
                        .put("processed", body.size())
                        .encode()
                    )
                } catch (e: Exception) {
                    // 返回错误响应
                    ctx.response().setStatusCode(400).end(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                        .encode()
                    )
                }
            }
        }
        
        // 注册EventBus消息处理器
        vertx.eventBus().consumer<JsonObject>("process.data") { message ->
            // 处理数据
            val body = message.body()
            
            // 记录处理延迟
            latencyRecorder.recordLatency("process.data", 5, TimeUnit.MILLISECONDS)
            
            // 回复消息
            message.reply(JsonObject()
                .put("success", true)
                .put("processed", body.size())
            )
        }
        
        // 启动HTTP服务器
        vertx.createHttpServer(HttpServerOptions().setPort(8889))
            .requestHandler(router)
            .listen()
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    /**
     * 测试HTTP GET性能
     */
    @Test
    fun testHttpGetPerformance(testContext: VertxTestContext) {
        // 测试参数
        val requestCount = 10000
        val concurrency = 100
        val latch = CountDownLatch(requestCount)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        // 发送请求
        for (i in 0 until requestCount) {
            // 控制并发
            while (requestCount - latch.count > concurrency) {
                Thread.sleep(1)
            }
            
            client.request(HttpMethod.GET, 8889, "localhost", "/api/echo?param=value&index=$i")
                .compose { request -> request.send() }
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        val response = ar.result()
                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet()
                        } else {
                            errorCount.incrementAndGet()
                        }
                    } else {
                        errorCount.incrementAndGet()
                    }
                    
                    latch.countDown()
                }
        }
        
        // 等待所有请求完成
        latch.await(30, TimeUnit.SECONDS)
        
        // 记录结束时间
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val requestsPerSecond = requestCount * 1000.0 / duration
        
        // 输出性能结果
        logger.info("HTTP GET性能测试结果:")
        logger.info("- 请求数: {}", requestCount)
        logger.info("- 成功数: {}", successCount.get())
        logger.info("- 错误数: {}", errorCount.get())
        logger.info("- 持续时间: {}ms", duration)
        logger.info("- 每秒请求数: {}", String.format("%.2f", requestsPerSecond))
        
        // 获取延迟统计信息
        val latencyStats = latencyRecorder.getLatencyStats("http.request")
        if (latencyStats != null) {
            val percentiles = latencyStats.getJsonObject("percentiles")
            logger.info("- 延迟统计:")
            logger.info("  - 最小值: {}ms", percentiles.getDouble("min"))
            logger.info("  - 最大值: {}ms", percentiles.getDouble("max"))
            logger.info("  - 平均值: {}ms", percentiles.getDouble("mean"))
            logger.info("  - p50: {}ms", percentiles.getDouble("p50"))
            logger.info("  - p90: {}ms", percentiles.getDouble("p90"))
            logger.info("  - p99: {}ms", percentiles.getDouble("p99"))
        }
        
        // 获取性能监控统计信息
        val performanceStats = performanceMonitor.getStats()
        logger.info("- 性能监控统计:")
        logger.info("  - 请求数: {}", performanceStats.getLong("requests"))
        logger.info("  - 错误数: {}", performanceStats.getLong("errors"))
        logger.info("  - 最大并发请求数: {}", performanceStats.getLong("max_concurrent_requests"))
        
        testContext.verify {
            assertTrue(successCount.get() > 0)
            assertTrue(requestsPerSecond > 1000) // 至少每秒1000请求
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试HTTP POST性能
     */
    @Test
    fun testHttpPostPerformance(testContext: VertxTestContext) {
        // 测试参数
        val requestCount = 5000
        val concurrency = 50
        val latch = CountDownLatch(requestCount)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        // 发送请求
        for (i in 0 until requestCount) {
            // 控制并发
            while (requestCount - latch.count > concurrency) {
                Thread.sleep(1)
            }
            
            // 创建请求体
            val body = JsonObject()
                .put("index", i)
                .put("value", "test")
                .put("timestamp", System.currentTimeMillis())
            
            client.request(HttpMethod.POST, 8889, "localhost", "/api/process")
                .compose { request -> 
                    request.putHeader("Content-Type", "application/json")
                    request.send(body.toBuffer())
                }
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        val response = ar.result()
                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet()
                        } else {
                            errorCount.incrementAndGet()
                        }
                    } else {
                        errorCount.incrementAndGet()
                    }
                    
                    latch.countDown()
                }
        }
        
        // 等待所有请求完成
        latch.await(30, TimeUnit.SECONDS)
        
        // 记录结束时间
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val requestsPerSecond = requestCount * 1000.0 / duration
        
        // 输出性能结果
        logger.info("HTTP POST性能测试结果:")
        logger.info("- 请求数: {}", requestCount)
        logger.info("- 成功数: {}", successCount.get())
        logger.info("- 错误数: {}", errorCount.get())
        logger.info("- 持续时间: {}ms", duration)
        logger.info("- 每秒请求数: {}", String.format("%.2f", requestsPerSecond))
        
        // 获取延迟统计信息
        val latencyStats = latencyRecorder.getLatencyStats("http.request")
        if (latencyStats != null) {
            val percentiles = latencyStats.getJsonObject("percentiles")
            logger.info("- 延迟统计:")
            logger.info("  - 最小值: {}ms", percentiles.getDouble("min"))
            logger.info("  - 最大值: {}ms", percentiles.getDouble("max"))
            logger.info("  - 平均值: {}ms", percentiles.getDouble("mean"))
            logger.info("  - p50: {}ms", percentiles.getDouble("p50"))
            logger.info("  - p90: {}ms", percentiles.getDouble("p90"))
            logger.info("  - p99: {}ms", percentiles.getDouble("p99"))
        }
        
        // 获取EventBus延迟统计信息
        val eventBusLatencyStats = latencyRecorder.getLatencyStats("process.data")
        if (eventBusLatencyStats != null) {
            val percentiles = eventBusLatencyStats.getJsonObject("percentiles")
            logger.info("- EventBus延迟统计:")
            logger.info("  - 最小值: {}ms", percentiles.getDouble("min"))
            logger.info("  - 最大值: {}ms", percentiles.getDouble("max"))
            logger.info("  - 平均值: {}ms", percentiles.getDouble("mean"))
            logger.info("  - p50: {}ms", percentiles.getDouble("p50"))
            logger.info("  - p90: {}ms", percentiles.getDouble("p90"))
            logger.info("  - p99: {}ms", percentiles.getDouble("p99"))
        }
        
        testContext.verify {
            assertTrue(successCount.get() > 0)
            assertTrue(requestsPerSecond > 500) // 至少每秒500请求
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试EventBus性能
     */
    @Test
    fun testEventBusPerformance(testContext: VertxTestContext) {
        // 测试参数
        val messageCount = 100000
        val latch = CountDownLatch(messageCount)
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        
        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .compose { success ->
                testContext.verify {
                    assertTrue(success)
                }
                
                // 注册消息处理器
                vertx.eventBus().consumer<JsonObject>("test.performance") { message ->
                    // 回复消息
                    message.reply(JsonObject().put("success", true))
                    
                    // 记录成功
                    successCount.incrementAndGet()
                    
                    // 减少计数
                    latch.countDown()
                }
                
                // 记录开始时间
                val startTime = System.currentTimeMillis()
                
                // 发送消息
                for (i in 0 until messageCount) {
                    val message = JsonObject()
                        .put("index", i)
                        .put("value", "test")
                        .put("timestamp", System.currentTimeMillis())
                    
                    vertx.eventBus().send("test.performance", message)
                }
                
                // 等待所有消息处理完成
                val waitResult = latch.await(30, TimeUnit.SECONDS)
                
                // 记录结束时间
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                val messagesPerSecond = messageCount * 1000.0 / duration
                
                // 输出性能结果
                logger.info("EventBus性能测试结果:")
                logger.info("- 消息数: {}", messageCount)
                logger.info("- 成功数: {}", successCount.get())
                logger.info("- 错误数: {}", errorCount.get())
                logger.info("- 持续时间: {}ms", duration)
                logger.info("- 每秒消息数: {}", String.format("%.2f", messagesPerSecond))
                logger.info("- 所有消息处理完成: {}", waitResult)
                
                // 获取EventBus统计信息
                Future.succeededFuture(eventBusManager.getStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    logger.info("- EventBus统计信息: {}", stats.encode())
                    
                    testContext.completeNow()
                }
            }
    }
}
