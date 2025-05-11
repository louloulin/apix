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
 * 熔断器管理器测试。
 */
@ExtendWith(VertxExtension::class)
class CircuitBreakerManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var circuitBreakerManager: CircuitBreakerManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建熔断器管理器
        circuitBreakerManager = CircuitBreakerManager.getInstance(vertx)
        
        // 初始化熔断器管理器
        val config = JsonObject()
            .put("resilience", JsonObject()
                .put("circuitBreaker", JsonObject()
                    .put("enabled", true)
                    .put("default", JsonObject()
                        .put("failureThreshold", 50)
                        .put("requestVolumeThreshold", 20)
                        .put("windowSizeInMillis", 10000)
                        .put("sleepWindowInMillis", 5000)
                        .put("errorTypes", JsonObject().put("0", "TIMEOUT").put("1", "FAILURE").put("2", "REJECTION"))
                    )
                    .put("services", JsonObject()
                        .put("testService", JsonObject()
                            .put("failureThreshold", 30)
                            .put("requestVolumeThreshold", 10)
                            .put("windowSizeInMillis", 5000)
                            .put("sleepWindowInMillis", 2000)
                            .put("errorTypes", JsonObject().put("0", "TIMEOUT").put("1", "FAILURE"))
                        )
                    )
                )
            )
        
        circuitBreakerManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get circuit breaker`(testContext: VertxTestContext) {
        // 获取熔断器
        val circuitBreaker = circuitBreakerManager.getCircuitBreaker("testService")
        
        testContext.verify {
            assertNotNull(circuitBreaker)
            assertEquals("testService", circuitBreaker.name)
            assertEquals(30, circuitBreaker.config.failureThreshold)
            assertEquals(10, circuitBreaker.config.requestVolumeThreshold)
            assertEquals(5000L, circuitBreaker.config.windowSizeInMillis)
            assertEquals(2000L, circuitBreaker.config.sleepWindowInMillis)
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test execute with circuit breaker`(testContext: VertxTestContext) {
        // 使用熔断器执行操作
        circuitBreakerManager.executeWithCircuitBreaker(
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
                
                // 获取熔断器统计信息
                val stats = circuitBreakerManager.getStats()
                assertNotNull(stats)
                
                testContext.completeNow()
            }
        }.onFailure { cause ->
            testContext.failNow(cause)
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
