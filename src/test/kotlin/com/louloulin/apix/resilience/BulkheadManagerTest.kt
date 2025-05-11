package com.louloulin.apix.resilience

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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 舱壁管理器测试。
 */
@ExtendWith(VertxExtension::class)
class BulkheadManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var bulkheadManager: BulkheadManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建舱壁管理器
        bulkheadManager = BulkheadManager.getInstance(vertx)
        
        // 初始化舱壁管理器
        val config = JsonObject()
            .put("resilience", JsonObject()
                .put("bulkhead", JsonObject()
                    .put("enabled", true)
                    .put("default", JsonObject()
                        .put("maxConcurrentCalls", 20)
                        .put("maxWaitTime", 500)
                    )
                    .put("services", JsonObject()
                        .put("testService", JsonObject()
                            .put("maxConcurrentCalls", 5)
                            .put("maxWaitTime", 200)
                        )
                    )
                )
            )
        
        bulkheadManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get bulkhead`(testContext: VertxTestContext) {
        // 获取舱壁
        val bulkhead = bulkheadManager.getBulkhead("testService")
        
        testContext.verify {
            assertNotNull(bulkhead)
            assertEquals("testService", bulkhead.name)
            assertEquals(5, bulkhead.config.maxConcurrentCalls)
            assertEquals(200L, bulkhead.config.maxWaitTime)
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test execute with bulkhead`(testContext: VertxTestContext) {
        // 使用舱壁执行操作
        bulkheadManager.executeWithBulkhead(
            "testService",
            {
                // 模拟操作
                val promise = io.vertx.core.Promise.promise<String>()
                vertx.setTimer(100) {
                    promise.complete("Success")
                }
                promise.future()
            },
            { throwable ->
                // 模拟备选方案
                io.vertx.core.Future.succeededFuture("Fallback")
            }
        ).onSuccess { result ->
            testContext.verify {
                assertEquals("Success", result)
                
                // 获取舱壁统计信息
                val stats = bulkheadManager.getStats()
                assertNotNull(stats)
                
                testContext.completeNow()
            }
        }.onFailure { cause ->
            testContext.failNow(cause)
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
