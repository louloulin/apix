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
 * 智能缓存策略 Verticle 测试。
 */
@ExtendWith(VertxExtension::class)
class SmartCacheVerticleTest {
    
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
                        .put("adaptiveTTL", JsonObject()
                            .put("enabled", true)
                            .put("strategy", "frequency")
                            .put("minTTL", 1000)
                            .put("maxTTL", 86400000)
                            .put("defaultTTL", 60000)
                            .put("adjustmentFactor", 1.5)
                            .put("frequencyThreshold", 10)
                            .put("intervalThreshold", 60000)
                        )
                        .put("hotData", JsonObject()
                            .put("enabled", true)
                            .put("algorithm", "frequency")
                            .put("threshold", 10)
                            .put("timeWindow", 60000)
                            .put("maxSize", 1000)
                            .put("strategy", "local")
                        )
                        .put("protection", JsonObject()
                            .put("penetrationEnabled", true)
                            .put("breakdownEnabled", true)
                            .put("avalancheEnabled", true)
                            .put("nullValueTTL", 60000)
                            .put("mutexLockTimeout", 5000)
                            .put("randomExpiryOffset", 300000)
                            .put("avalancheThreshold", 1000)
                            .put("avalancheTimeWindow", 1000)
                        )
                        .put("bloomFilter", JsonObject()
                            .put("enabled", true)
                            .put("expectedElements", 1000000)
                            .put("falsePositiveRate", 0.01)
                            .put("syncInterval", 60000)
                        )
                        .put("cacheNamespace", "test")
                    )
                )
            )
        }
        
        // 模拟数据加载器
        vertx.eventBus().consumer<JsonObject>("test.loader") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", "test-value")
            )
        }
        
        // 部署多级缓存 Verticle
        vertx.deployVerticle(MultiLevelCacheVerticle())
            .compose { _ ->
                // 部署智能缓存策略 Verticle
                vertx.deployVerticle(SmartCacheVerticle())
            }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get adaptive TTL status`(testContext: VertxTestContext) {
        // 获取自适应 TTL 状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_ADAPTIVE_TTL_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("enabled"))
                    assertEquals("frequency", result.getString("strategy"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get hot data status`(testContext: VertxTestContext) {
        // 获取热点数据状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_HOT_DATA_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("enabled"))
                    assertEquals("frequency", result.getString("algorithm"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get cache protection status`(testContext: VertxTestContext) {
        // 获取缓存穿透防护状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_PROTECTION_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("penetrationProtectionEnabled"))
                    assertTrue(result.getBoolean("breakdownProtectionEnabled"))
                    assertTrue(result.getBoolean("avalancheProtectionEnabled"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get bloom filter status`(testContext: VertxTestContext) {
        // 获取布隆过滤器状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_BLOOM_FILTER_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("enabled"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
