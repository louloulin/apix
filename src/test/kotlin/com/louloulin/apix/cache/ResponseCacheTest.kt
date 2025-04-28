package com.louloulin.apix.cache

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.CacheVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * AI 响应缓存测试
 */
@ExtendWith(VertxExtension::class)
class ResponseCacheTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 部署 CacheVerticle
        vertx.deployVerticle(CacheVerticle())
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testCachePutAndGet(testContext: VertxTestContext) {
        val key = "test-key"
        val value = JsonObject().put("message", "Hello, World!").encode()
        val modelId = "gpt-4"
        
        // 存储缓存
        val putMessage = JsonObject()
            .put("key", key)
            .put("value", value)
            .put("modelId", modelId)
            .put("ttl", 60000) // 1 分钟
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_PUT, putMessage) { putAr ->
            if (putAr.succeeded()) {
                val putResponse = putAr.result().body()
                testContext.verify {
                    assert(putResponse.getBoolean("success") == true) { "Expected success to be true" }
                }
                
                // 获取缓存
                val getMessage = JsonObject()
                    .put("key", key)
                    .put("modelId", modelId)
                
                vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_GET, getMessage) { getAr ->
                    if (getAr.succeeded()) {
                        val getResponse = getAr.result().body()
                        testContext.verify {
                            assert(getResponse.getBoolean("success") == true) { "Expected success to be true" }
                            val result = getResponse.getJsonObject("result")
                            assert(result.getBoolean("cached") == true) { "Expected cached to be true" }
                            assert(result.getString("value") == value) { "Expected value to match" }
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(getAr.cause())
                    }
                }
            } else {
                testContext.failNow(putAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testCacheInvalidate(testContext: VertxTestContext) {
        val key = "test-key-invalidate"
        val value = JsonObject().put("message", "Hello, World!").encode()
        val modelId = "gpt-4"
        
        // 存储缓存
        val putMessage = JsonObject()
            .put("key", key)
            .put("value", value)
            .put("modelId", modelId)
            .put("ttl", 60000) // 1 分钟
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_PUT, putMessage) { putAr ->
            if (putAr.succeeded()) {
                // 失效缓存
                val invalidateMessage = JsonObject()
                    .put("key", key)
                    .put("modelId", modelId)
                
                vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_INVALIDATE, invalidateMessage) { invalidateAr ->
                    if (invalidateAr.succeeded()) {
                        val invalidateResponse = invalidateAr.result().body()
                        testContext.verify {
                            assert(invalidateResponse.getBoolean("success") == true) { "Expected success to be true" }
                            assert(invalidateResponse.getBoolean("result") == true) { "Expected result to be true" }
                        }
                        
                        // 尝试获取已失效的缓存
                        val getMessage = JsonObject()
                            .put("key", key)
                            .put("modelId", modelId)
                        
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_GET, getMessage) { getAr ->
                            if (getAr.succeeded()) {
                                val getResponse = getAr.result().body()
                                testContext.verify {
                                    assert(getResponse.getBoolean("success") == false) { "Expected success to be false" }
                                    assert(getResponse.getInteger("errorCode") == 404) { "Expected error code to be 404" }
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(getAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(invalidateAr.cause())
                    }
                }
            } else {
                testContext.failNow(putAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testCacheStats(testContext: VertxTestContext) {
        val key1 = "test-key-stats-1"
        val key2 = "test-key-stats-2"
        val value = JsonObject().put("message", "Hello, World!").encode()
        val modelId = "gpt-4"
        
        // 存储缓存
        val putMessage1 = JsonObject()
            .put("key", key1)
            .put("value", value)
            .put("modelId", modelId)
            .put("ttl", 60000) // 1 分钟
        
        val putMessage2 = JsonObject()
            .put("key", key2)
            .put("value", value)
            .put("modelId", "gpt-3.5-turbo") // 不同的模型
            .put("ttl", 60000) // 1 分钟
        
        // 存储两个缓存条目
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_PUT, putMessage1) { putAr1 ->
            if (putAr1.succeeded()) {
                vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_PUT, putMessage2) { putAr2 ->
                    if (putAr2.succeeded()) {
                        // 获取第一个缓存条目（命中）
                        val getMessage1 = JsonObject()
                            .put("key", key1)
                            .put("modelId", modelId)
                        
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_GET, getMessage1) { getAr1 ->
                            if (getAr1.succeeded()) {
                                // 获取不存在的缓存条目（未命中）
                                val getMessage3 = JsonObject()
                                    .put("key", "non-existent-key")
                                    .put("modelId", modelId)
                                
                                vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_GET, getMessage3) { getAr3 ->
                                    // 获取缓存统计信息
                                    vertx.eventBus().request<JsonObject>(EventBusAddresses.CACHE_STATS, JsonObject()) { statsAr ->
                                        if (statsAr.succeeded()) {
                                            val statsResponse = statsAr.result().body()
                                            testContext.verify {
                                                assert(statsResponse.getBoolean("success") == true) { "Expected success to be true" }
                                                val result = statsResponse.getJsonObject("result")
                                                val total = result.getJsonObject("total")
                                                assert(total.getInteger("size") == 2) { "Expected size to be 2" }
                                                assert(total.getInteger("hits") == 1) { "Expected hits to be 1" }
                                                assert(total.getInteger("misses") == 1) { "Expected misses to be 1" }
                                                testContext.completeNow()
                                            }
                                        } else {
                                            testContext.failNow(statsAr.cause())
                                        }
                                    }
                                }
                            } else {
                                testContext.failNow(getAr1.cause())
                            }
                        }
                    } else {
                        testContext.failNow(putAr2.cause())
                    }
                }
            } else {
                testContext.failNow(putAr1.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
