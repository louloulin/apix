package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
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
 * 多级缓存 Verticle 测试。
 */
@ExtendWith(VertxExtension::class)
class MultiLevelCacheVerticleTest {
    
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 模拟配置 Verticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("cache", JsonObject()
                        .put("localCacheEnabled", true)
                        .put("distributedCacheEnabled", true)
                        .put("redisCacheEnabled", false)
                        .put("cacheConsistencyEnabled", true)
                        .put("cacheWarmupEnabled", true)
                        .put("cacheNamespace", "test")
                        .put("cleanupInterval", 60000)
                        .put("consistency", JsonObject()
                            .put("enabled", true)
                            .put("strategy", "eventual")
                        )
                        .put("warmup", JsonObject()
                            .put("enabled", true)
                            .put("mode", "async")
                            .put("timeout", 60000)
                        )
                    )
                )
            )
        }
        
        // 注册测试预热处理器
        vertx.eventBus().consumer<JsonObject>("test.warmup.handler") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("message", "Warmup handler executed")
            )
        }
        
        // 部署多级缓存 Verticle
        vertx.deployVerticle(MultiLevelCacheVerticle())
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test put and get cache`(testContext: VertxTestContext) {
        // 存储缓存
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_PUT, JsonObject()
            .put("key", "test-key")
            .put("value", "test-value")
            .put("ttl", 60000)
            .put("namespace", "test")
        ) { putAr ->
            if (putAr.succeeded()) {
                val putResponse = putAr.result().body()
                
                testContext.verify {
                    assertTrue(putResponse.getBoolean("success"))
                    
                    // 获取缓存
                    vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_GET, JsonObject()
                        .put("key", "test-key")
                        .put("namespace", "test")
                    ) { getAr ->
                        if (getAr.succeeded()) {
                            val getResponse = getAr.result().body()
                            
                            assertTrue(getResponse.getBoolean("success"))
                            assertEquals("test-value", getResponse.getValue("result"))
                            
                            testContext.completeNow()
                        } else {
                            testContext.failNow(getAr.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(putAr.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test remove cache`(testContext: VertxTestContext) {
        // 存储缓存
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_PUT, JsonObject()
            .put("key", "test-key")
            .put("value", "test-value")
            .put("ttl", 60000)
            .put("namespace", "test")
        ) { putAr ->
            if (putAr.succeeded()) {
                // 移除缓存
                vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_REMOVE, JsonObject()
                    .put("key", "test-key")
                    .put("namespace", "test")
                ) { removeAr ->
                    if (removeAr.succeeded()) {
                        val removeResponse = removeAr.result().body()
                        
                        testContext.verify {
                            assertTrue(removeResponse.getBoolean("success"))
                            
                            // 尝试获取已移除的缓存
                            vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_GET, JsonObject()
                                .put("key", "test-key")
                                .put("namespace", "test")
                            ) { getAr ->
                                if (getAr.succeeded()) {
                                    val getResponse = getAr.result().body()
                                    
                                    assertEquals(false, getResponse.getBoolean("success"))
                                    
                                    testContext.completeNow()
                                } else {
                                    testContext.completeNow()
                                }
                            }
                        }
                    } else {
                        testContext.failNow(removeAr.cause())
                    }
                }
            } else {
                testContext.failNow(putAr.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get cache stats`(testContext: VertxTestContext) {
        // 获取缓存统计信息
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_STATS_GET, JsonObject()
            .put("namespace", "test")
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("test", result.getString("namespace"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test start warmup`(testContext: VertxTestContext) {
        // 开始缓存预热
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_WARMUP_START, JsonObject()
            .put("keys", JsonArray().add("test-key-1").add("test-key-2"))
            .put("handlers", JsonArray().add("test.warmup.handler"))
            .put("namespace", "test")
            .put("mode", "async")
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("warming", result.getString("status"))
                    
                    // 等待一段时间，让预热完成
                    vertx.setTimer(1000) {
                        // 获取预热状态
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_WARMUP_STATUS_GET, JsonObject()) { statusAr ->
                            if (statusAr.succeeded()) {
                                val statusResponse = statusAr.result().body()
                                
                                assertTrue(statusResponse.getBoolean("success"))
                                
                                val statusResult = statusResponse.getJsonObject("result")
                                assertNotNull(statusResult)
                                
                                // 状态可能是 warming 或 completed，取决于预热是否完成
                                val status = statusResult.getString("status")
                                assertTrue(status == "warming" || status == "completed")
                                
                                testContext.completeNow()
                            } else {
                                testContext.failNow(statusAr.cause())
                            }
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
