package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
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
 * 故障隔离 Verticle 测试。
 */
@ExtendWith(VertxExtension::class)
class ResilienceVerticleTest {
    
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 模拟配置 Verticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("resilience", JsonObject()
                        .put("bulkhead", JsonObject()
                            .put("enabled", true)
                            .put("default", JsonObject()
                                .put("maxConcurrentCalls", 20)
                                .put("maxWaitTime", 500)
                            )
                        )
                        .put("circuitBreaker", JsonObject()
                            .put("enabled", true)
                            .put("default", JsonObject()
                                .put("failureThreshold", 50)
                                .put("requestVolumeThreshold", 20)
                                .put("windowSizeInMillis", 10000)
                                .put("sleepWindowInMillis", 5000)
                            )
                        )
                        .put("fallback", JsonObject()
                            .put("enabled", true)
                            .put("systemDegradationLevel", 0)
                        )
                        .put("faultInjection", JsonObject()
                            .put("enabled", false)
                            .put("globalFaultProbability", 0)
                        )
                    )
                )
            )
        }
        
        // 部署故障隔离 Verticle
        vertx.deployVerticle(ResilienceVerticle())
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get resilience status`(testContext: VertxTestContext) {
        // 测试获取故障隔离状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.RESILIENCE_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    
                    val bulkhead = result.getJsonObject("bulkhead")
                    assertNotNull(bulkhead)
                    assertTrue(bulkhead.getBoolean("enabled"))
                    
                    val circuitBreaker = result.getJsonObject("circuitBreaker")
                    assertNotNull(circuitBreaker)
                    assertTrue(circuitBreaker.getBoolean("enabled"))
                    
                    val fallback = result.getJsonObject("fallback")
                    assertNotNull(fallback)
                    assertTrue(fallback.getBoolean("enabled"))
                    
                    val faultInjection = result.getJsonObject("faultInjection")
                    assertNotNull(faultInjection)
                    assertEquals(false, faultInjection.getBoolean("enabled"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test set fallback level`(testContext: VertxTestContext) {
        // 测试设置降级级别
        vertx.eventBus().request<JsonObject>(EventBusAddresses.RESILIENCE_FALLBACK_LEVEL_SET, JsonObject()
            .put("level", 2)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals(2, result.getInteger("level"))
                    
                    // 测试获取降级级别
                    vertx.eventBus().request<JsonObject>(EventBusAddresses.RESILIENCE_FALLBACK_LEVEL_GET, JsonObject()) { ar2 ->
                        if (ar2.succeeded()) {
                            val response2 = ar2.result().body()
                            
                            assertTrue(response2.getBoolean("success"))
                            
                            val result2 = response2.getJsonObject("result")
                            assertNotNull(result2)
                            assertEquals(2, result2.getInteger("level"))
                            
                            testContext.completeNow()
                        } else {
                            testContext.failNow(ar2.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
