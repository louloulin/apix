package com.louloulin.apix.core.concurrency

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 并发控制器测试
 */
@ExtendWith(VertxExtension::class)
class ConcurrencyControllerTest {
    private lateinit var vertx: Vertx
    private lateinit var concurrencyController: ConcurrencyController
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        concurrencyController = ConcurrencyController(vertx)
        
        // 初始化并发控制器
        val config = JsonObject()
            .put("defaultMaxConcurrency", 10)
            .put("minConcurrency", 5)
            .put("maxConcurrency", 100)
            .put("targetCpuUsage", 70.0)
            .put("targetResponseTime", 500L)
            .put("adjustmentInterval", 1000L)
            .put("adjustmentFactor", 0.1)
        
        concurrencyController.initialize(config)
        testContext.completeNow()
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `test get current limit`(testContext: VertxTestContext) {
        // 获取默认限制
        val defaultLimit = concurrencyController.getCurrentLimit("test-service")
        testContext.verify {
            assertEquals(10, defaultLimit)
        }
        
        // 设置新限制
        concurrencyController.setCurrentLimit("test-service", 20)
        val newLimit = concurrencyController.getCurrentLimit("test-service")
        testContext.verify {
            assertEquals(20, newLimit)
        }
        
        testContext.completeNow()
    }
    
    @Test
    fun `test try acquire and release`(testContext: VertxTestContext) {
        val serviceId = "test-service"
        
        // 设置并发限制为 5
        concurrencyController.setCurrentLimit(serviceId, 5)
        
        // 尝试获取 5 个许可
        val results = mutableListOf<Boolean>()
        for (i in 1..5) {
            results.add(concurrencyController.tryAcquire(serviceId))
        }
        
        // 尝试获取第 6 个许可（应该失败）
        val sixthResult = concurrencyController.tryAcquire(serviceId)
        
        testContext.verify {
            // 前 5 个许可应该都获取成功
            assertTrue(results.all { it })
            // 第 6 个许可应该获取失败
            assertFalse(sixthResult)
            // 活动请求数应该是 5
            assertEquals(5, concurrencyController.getActiveCount(serviceId))
        }
        
        // 释放一个许可
        concurrencyController.release(serviceId, 100, false)
        
        // 再次尝试获取许可（应该成功）
        val newResult = concurrencyController.tryAcquire(serviceId)
        
        testContext.verify {
            assertTrue(newResult)
            assertEquals(5, concurrencyController.getActiveCount(serviceId))
        }
        
        testContext.completeNow()
    }
    
    @Test
    fun `test get service metrics`(testContext: VertxTestContext) {
        val serviceId = "test-service"
        
        // 设置并发限制
        concurrencyController.setCurrentLimit(serviceId, 10)
        
        // 获取 5 个许可
        for (i in 1..5) {
            concurrencyController.tryAcquire(serviceId)
        }
        
        // 释放 2 个许可，其中 1 个是错误
        concurrencyController.release(serviceId, 100, false)
        concurrencyController.release(serviceId, 200, true)
        
        // 获取服务指标
        val metrics = concurrencyController.getServiceMetrics(serviceId)
        
        testContext.verify {
            assertEquals(serviceId, metrics.getString("serviceId"))
            assertEquals(3, metrics.getInteger("activeRequests"))
            assertEquals(10, metrics.getInteger("concurrencyLimit"))
            assertEquals(150.0, metrics.getDouble("averageResponseTime"))
            assertEquals(0.2, metrics.getDouble("errorRate"))
            assertEquals(0.0, metrics.getDouble("rejectionRate"))
            assertEquals(5, metrics.getLong("totalRequests"))
            assertEquals(0.3, metrics.getDouble("utilizationRate"))
        }
        
        testContext.completeNow()
    }
    
    @Test
    fun `test reset service metrics`(testContext: VertxTestContext) {
        val serviceId = "test-service"
        
        // 设置并发限制
        concurrencyController.setCurrentLimit(serviceId, 10)
        
        // 获取 5 个许可
        for (i in 1..5) {
            concurrencyController.tryAcquire(serviceId)
        }
        
        // 释放 2 个许可
        concurrencyController.release(serviceId, 100, false)
        concurrencyController.release(serviceId, 200, true)
        
        // 重置服务指标
        concurrencyController.resetServiceMetrics(serviceId)
        
        // 获取服务指标
        val metrics = concurrencyController.getServiceMetrics(serviceId)
        
        testContext.verify {
            assertEquals(serviceId, metrics.getString("serviceId"))
            assertEquals(0, metrics.getInteger("activeRequests"))
            assertEquals(10, metrics.getInteger("concurrencyLimit"))
            assertEquals(0.0, metrics.getDouble("averageResponseTime"))
            assertEquals(0.0, metrics.getDouble("errorRate"))
            assertEquals(0.0, metrics.getDouble("rejectionRate"))
            assertEquals(0, metrics.getLong("totalRequests"))
            assertEquals(0.0, metrics.getDouble("utilizationRate"))
        }
        
        testContext.completeNow()
    }
    
    @Test
    fun `test get all service metrics`(testContext: VertxTestContext) {
        // 设置两个服务的并发限制
        concurrencyController.setCurrentLimit("service1", 10)
        concurrencyController.setCurrentLimit("service2", 20)
        
        // 获取许可
        concurrencyController.tryAcquire("service1")
        concurrencyController.tryAcquire("service1")
        concurrencyController.tryAcquire("service2")
        
        // 获取所有服务指标
        val allMetrics = concurrencyController.getAllServiceMetrics()
        
        testContext.verify {
            val services = allMetrics.getJsonObject("services")
            assertTrue(services.containsKey("service1"))
            assertTrue(services.containsKey("service2"))
            
            val service1Metrics = services.getJsonObject("service1")
            assertEquals(2, service1Metrics.getInteger("activeRequests"))
            assertEquals(10, service1Metrics.getInteger("concurrencyLimit"))
            
            val service2Metrics = services.getJsonObject("service2")
            assertEquals(1, service2Metrics.getInteger("activeRequests"))
            assertEquals(20, service2Metrics.getInteger("concurrencyLimit"))
            
            val system = allMetrics.getJsonObject("system")
            assertTrue(system.containsKey("cpuUsage"))
            assertTrue(system.containsKey("memoryUsage"))
            assertEquals(3, system.getInteger("totalActiveRequests"))
        }
        
        testContext.completeNow()
    }
}
