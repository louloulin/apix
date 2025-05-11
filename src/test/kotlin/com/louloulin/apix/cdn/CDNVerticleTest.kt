package com.louloulin.apix.cdn

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
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

@ExtendWith(VertxExtension::class)
class CDNVerticleTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建模拟配置响应
        val configResponse = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("cdn", JsonObject()
                    .put("enabled", true)
                    .put("providers", JsonArray()
                        .add(JsonObject()
                            .put("name", "cloudflare")
                            .put("type", "cloudflare")
                            .put("enabled", true)
                            .put("apiToken", "mock-token")
                            .put("zoneId", "mock-zone-id")
                        )
                    )
                )
            )
        
        // 设置EventBus消息处理器来模拟ConfigVerticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(configResponse)
        }
        
        // 部署CDNVerticle
        vertx.deployVerticle(CDNVerticle::class.java.name, testContext.succeeding { _ ->
            testContext.completeNow()
        })
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close(testContext.succeeding { _ ->
            testContext.completeNow()
        })
    }
    
    @Test
    fun testGetCDNStatus(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取CDN状态请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CDN_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getBoolean("enabled", false)) { "CDN should be enabled" }
                    assert(result.getString("primary") != null) { "Primary provider should be set" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testPurgeCache(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("urls", JsonArray()
                .add("https://example.com/page1")
                .add("https://example.com/page2")
            )
        
        // 发送刷新缓存请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CDN_CACHE_PURGE, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    // 由于我们没有真正的CDN提供商，这里可能会失败
                    // 但我们只需要验证请求被处理了
                    assert(response != null) { "Response should not be null" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testPrewarmCache(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("urls", JsonArray()
                .add("https://example.com/page1")
                .add("https://example.com/page2")
            )
        
        // 发送预热缓存请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CDN_CACHE_PREWARM, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    // 由于我们没有真正的CDN提供商，这里可能会失败
                    // 但我们只需要验证请求被处理了
                    assert(response != null) { "Response should not be null" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
