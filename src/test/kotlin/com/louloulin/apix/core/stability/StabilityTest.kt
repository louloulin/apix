package com.louloulin.apix.core.stability

import com.louloulin.apix.core.eventbus.EventBusManager
import com.louloulin.apix.core.metrics.LatencyRecorder
import com.louloulin.apix.core.metrics.PerformanceMonitor
import com.louloulin.apix.core.resource.ResourceManager
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.assertTrue

/**
 * 稳定性测试类，用于测试系统在长时间运行下的稳定性。
 */
@ExtendWith(VertxExtension::class)
class StabilityTest {
    private val logger = LoggerFactory.getLogger(StabilityTest::class.java)
    
    private lateinit var vertx: Vertx
    private lateinit var eventBusManager: EventBusManager
    private lateinit var latencyRecorder: LatencyRecorder
    private lateinit var performanceMonitor: PerformanceMonitor
    private lateinit var resourceManager: ResourceManager
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 初始化组件
        eventBusManager = EventBusManager.getInstance(vertx)
        latencyRecorder = LatencyRecorder.getInstance()
        performanceMonitor = PerformanceMonitor.getInstance(vertx)
        resourceManager = ResourceManager.getInstance(vertx)
        
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
        vertx.createHttpServer(HttpServerOptions().setPort(8890))
            .requestHandler(router)
            .listen()
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    /**
     * 测试长时间运行稳定性
     */
    @Test
    fun testLongRunningStability(testContext: VertxTestContext) {
        // 测试参数
        val testDuration = 60000L // 1分钟
        val requestInterval = 100L // 100毫秒
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        val lastRequestTime = AtomicLong(0)
        
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        // 定期发送请求
        val timerId = vertx.setPeriodic(requestInterval) { _ ->
            // 检查是否达到测试时间
            if (System.currentTimeMillis() - startTime > testDuration) {
                vertx.cancelTimer(timerId)
                
                // 等待一段时间，确保所有请求完成
                vertx.setTimer(1000) {
                    // 输出稳定性测试结果
                    val endTime = System.currentTimeMillis()
                    val duration = endTime - startTime
                    val requestsPerSecond = successCount.get() * 1000.0 / duration
                    
                    logger.info("长时间运行稳定性测试结果:")
                    logger.info("- 持续时间: {}ms", duration)
                    logger.info("- 成功请求数: {}", successCount.get())
                    logger.info("- 错误请求数: {}", errorCount.get())
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
                    
                    // 获取资源管理器统计信息
                    val resourceStats = resourceManager.getResourceStats()
                    logger.info("- 资源管理器统计:")
                    logger.info("  - CPU使用率: {}%", String.format("%.2f", resourceStats.getDouble("cpu_usage") * 100))
                    logger.info("  - 内存使用率: {}%", String.format("%.2f", resourceStats.getDouble("memory_usage") * 100))
                    
                    testContext.verify {
                        assertTrue(successCount.get() > 0)
                        assertTrue(errorCount.get() == 0)
                        
                        testContext.completeNow()
                    }
                }
                
                return@setPeriodic
            }
            
            // 发送请求
            lastRequestTime.set(System.currentTimeMillis())
            
            client.request(HttpMethod.GET, 8890, "localhost", "/api/echo?param=value&timestamp=" + System.currentTimeMillis())
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
                }
        }
        
        // 定期触发资源调整
        vertx.setPeriodic(10000) { _ ->
            resourceManager.triggerResourceAdjustment()
        }
        
        // 定期切换EventBus类型
        vertx.setPeriodic(20000) { _ ->
            val currentType = eventBusManager.getCurrentType()
            val newType = if (currentType == EventBusManager.EventBusType.VERTX) {
                EventBusManager.EventBusType.JCTOOLS
            } else {
                EventBusManager.EventBusType.VERTX
            }
            
            eventBusManager.switchType(newType)
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        logger.info("已切换EventBus类型: {} -> {}", currentType, newType)
                    } else {
                        logger.error("切换EventBus类型失败", ar.cause())
                    }
                }
        }
        
        // 定期重置统计信息
        vertx.setPeriodic(30000) { _ ->
            latencyRecorder.resetAllLatencyStats()
            logger.info("已重置延迟统计信息")
        }
    }
    
    /**
     * 测试内存泄漏
     */
    @Test
    fun testMemoryLeak(testContext: VertxTestContext) {
        // 测试参数
        val iterations = 100
        val messagesPerIteration = 1000
        val latch = CountDownLatch(iterations * messagesPerIteration)
        
        // 记录开始时间
        val startTime = System.currentTimeMillis()
        
        // 记录初始内存使用
        val initialMemoryUsage = getMemoryUsage()
        logger.info("初始内存使用: {}MB", initialMemoryUsage)
        
        // 执行多次迭代
        for (i in 0 until iterations) {
            // 发送大量消息
            for (j in 0 until messagesPerIteration) {
                val message = JsonObject()
                    .put("iteration", i)
                    .put("index", j)
                    .put("value", "test")
                    .put("timestamp", System.currentTimeMillis())
                
                vertx.eventBus().send("test.memory", message) { ar ->
                    latch.countDown()
                }
            }
            
            // 每10次迭代记录一次内存使用
            if (i % 10 == 0) {
                val memoryUsage = getMemoryUsage()
                logger.info("迭代 {}: 内存使用 {}MB", i, memoryUsage)
            }
        }
        
        // 等待所有消息处理完成
        latch.await(30, TimeUnit.SECONDS)
        
        // 强制垃圾回收
        System.gc()
        
        // 记录最终内存使用
        val finalMemoryUsage = getMemoryUsage()
        logger.info("最终内存使用: {}MB", finalMemoryUsage)
        
        // 记录结束时间
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // 输出内存泄漏测试结果
        logger.info("内存泄漏测试结果:")
        logger.info("- 迭代次数: {}", iterations)
        logger.info("- 每次迭代消息数: {}", messagesPerIteration)
        logger.info("- 总消息数: {}", iterations * messagesPerIteration)
        logger.info("- 持续时间: {}ms", duration)
        logger.info("- 初始内存使用: {}MB", initialMemoryUsage)
        logger.info("- 最终内存使用: {}MB", finalMemoryUsage)
        logger.info("- 内存增长: {}MB", finalMemoryUsage - initialMemoryUsage)
        
        testContext.verify {
            // 内存增长不应该超过50MB
            assertTrue(finalMemoryUsage - initialMemoryUsage < 50)
            
            testContext.completeNow()
        }
    }
    
    /**
     * 获取当前内存使用（MB）
     */
    private fun getMemoryUsage(): Double {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        return usedMemory / (1024.0 * 1024.0)
    }
}
