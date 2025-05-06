package com.louloulin.apix.core.resource

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
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
 * 资源管理器测试类
 */
@ExtendWith(VertxExtension::class)
class ResourceManagerTest {
    private lateinit var vertx: Vertx
    private lateinit var resourceManager: ResourceManager
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        resourceManager = ResourceManager.getInstance(vertx)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    /**
     * 测试获取资源统计信息
     */
    @Test
    fun testGetResourceStats(testContext: VertxTestContext) {
        // 获取资源统计信息
        val stats = resourceManager.getResourceStats()
        
        testContext.verify {
            assertNotNull(stats)
            
            // 验证基本信息
            assertTrue(stats.containsKey("cpu_cores"))
            assertTrue(stats.containsKey("max_memory_mb"))
            assertTrue(stats.containsKey("cpu_usage"))
            assertTrue(stats.containsKey("memory_usage"))
            
            // 验证线程池信息
            val poolsInfo = stats.getJsonObject("pools")
            assertNotNull(poolsInfo)
            
            val workerPoolInfo = poolsInfo.getJsonObject("worker_pool")
            assertNotNull(workerPoolInfo)
            assertTrue(workerPoolInfo.containsKey("current"))
            assertTrue(workerPoolInfo.containsKey("min"))
            assertTrue(workerPoolInfo.containsKey("max"))
            
            val eventLoopPoolInfo = poolsInfo.getJsonObject("event_loop_pool")
            assertNotNull(eventLoopPoolInfo)
            assertTrue(eventLoopPoolInfo.containsKey("current"))
            assertTrue(eventLoopPoolInfo.containsKey("min"))
            assertTrue(eventLoopPoolInfo.containsKey("max"))
            
            val connectionPoolInfo = poolsInfo.getJsonObject("connection_pool")
            assertNotNull(connectionPoolInfo)
            assertTrue(connectionPoolInfo.containsKey("current"))
            assertTrue(connectionPoolInfo.containsKey("min"))
            assertTrue(connectionPoolInfo.containsKey("max"))
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试设置自动调整开关
     */
    @Test
    fun testSetAutoAdjustEnabled(testContext: VertxTestContext) {
        // 设置自动调整开关
        resourceManager.setAutoAdjustEnabled(false)
        
        // 获取资源统计信息
        val stats = resourceManager.getResourceStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertEquals(false, stats.getBoolean("auto_adjust_enabled"))
            
            // 恢复自动调整开关
            resourceManager.setAutoAdjustEnabled(true)
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试设置调整间隔
     */
    @Test
    fun testSetAdjustInterval(testContext: VertxTestContext) {
        // 设置调整间隔
        val interval = 120000L
        resourceManager.setAdjustInterval(interval)
        
        // 获取资源统计信息
        val stats = resourceManager.getResourceStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertEquals(interval, stats.getLong("adjust_interval_ms"))
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试设置线程池大小范围
     */
    @Test
    fun testSetPoolSizeRange(testContext: VertxTestContext) {
        // 设置工作线程池大小范围
        val workerMin = 20
        val workerMax = 100
        resourceManager.setWorkerPoolSizeRange(workerMin, workerMax)
        
        // 设置事件循环线程池大小范围
        val eventLoopMin = 8
        val eventLoopMax = 16
        resourceManager.setEventLoopPoolSizeRange(eventLoopMin, eventLoopMax)
        
        // 设置连接池大小范围
        val connectionMin = 100
        val connectionMax = 1000
        resourceManager.setConnectionPoolSizeRange(connectionMin, connectionMax)
        
        // 获取资源统计信息
        val stats = resourceManager.getResourceStats()
        
        testContext.verify {
            assertNotNull(stats)
            
            val poolsInfo = stats.getJsonObject("pools")
            assertNotNull(poolsInfo)
            
            val workerPoolInfo = poolsInfo.getJsonObject("worker_pool")
            assertNotNull(workerPoolInfo)
            assertEquals(workerMin, workerPoolInfo.getInteger("min"))
            assertEquals(workerMax, workerPoolInfo.getInteger("max"))
            
            val eventLoopPoolInfo = poolsInfo.getJsonObject("event_loop_pool")
            assertNotNull(eventLoopPoolInfo)
            assertEquals(eventLoopMin, eventLoopPoolInfo.getInteger("min"))
            assertEquals(eventLoopMax, eventLoopPoolInfo.getInteger("max"))
            
            val connectionPoolInfo = poolsInfo.getJsonObject("connection_pool")
            assertNotNull(connectionPoolInfo)
            assertEquals(connectionMin, connectionPoolInfo.getInteger("min"))
            assertEquals(connectionMax, connectionPoolInfo.getInteger("max"))
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试注册Verticle部署
     */
    @Test
    fun testRegisterVerticleDeployment(testContext: VertxTestContext) {
        // 部署一个测试Verticle
        vertx.deployVerticle("com.louloulin.apix.core.verticle.TestVerticle", DeploymentOptions().setInstances(2))
            .compose { deploymentId ->
                // 注册Verticle部署
                resourceManager.registerVerticleDeployment("com.louloulin.apix.core.verticle.TestVerticle", deploymentId, 2, 1, 5)
                
                // 获取资源统计信息
                Future.succeededFuture(resourceManager.getResourceStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    val stats = ar.result()
                    assertNotNull(stats)
                    
                    val deploymentsInfo = stats.getJsonObject("verticle_deployments")
                    assertNotNull(deploymentsInfo)
                    
                    val deploymentInfo = deploymentsInfo.getJsonObject("com.louloulin.apix.core.verticle.TestVerticle")
                    assertNotNull(deploymentInfo)
                    assertEquals(2, deploymentInfo.getInteger("current_instances"))
                    assertEquals(1, deploymentInfo.getInteger("min_instances"))
                    assertEquals(5, deploymentInfo.getInteger("max_instances"))
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试更新活跃请求数
     */
    @Test
    fun testUpdateActiveRequests(testContext: VertxTestContext) {
        // 更新活跃请求数
        val count = 100
        resourceManager.updateActiveRequests(count)
        
        // 获取资源统计信息
        val stats = resourceManager.getResourceStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertEquals(count, stats.getInteger("active_requests"))
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试手动触发资源调整
     */
    @Test
    fun testTriggerResourceAdjustment(testContext: VertxTestContext) {
        // 手动触发资源调整
        resourceManager.triggerResourceAdjustment()
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    
                    // 获取资源统计信息
                    val stats = resourceManager.getResourceStats()
                    assertNotNull(stats)
                    
                    // 验证上次调整时间已更新
                    assertTrue(stats.getLong("last_adjust_time_ms") > 0)
                    
                    testContext.completeNow()
                }
            }
    }
}
