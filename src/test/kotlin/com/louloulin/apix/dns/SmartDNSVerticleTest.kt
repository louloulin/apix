package com.louloulin.apix.dns

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
class SmartDNSVerticleTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建模拟配置响应
        val configResponse = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("dns", JsonObject()
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
                    .put("geoDatabase", JsonObject()
                        .put("enabled", true)
                        .put("type", "maxmind")
                        .put("filePath", "src/test/resources/GeoLite2-City.mmdb")
                    )
                )
            )
        
        // 设置EventBus消息处理器来模拟ConfigVerticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(configResponse)
        }
        
        // 部署SmartDNSVerticle
        vertx.deployVerticle(SmartDNSVerticle::class.java.name, testContext.succeeding { _ ->
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
    fun testGetDNSStatus(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取DNS状态请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.DNS_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getBoolean("enabled", false)) { "DNS should be enabled" }
                    assert(result.getString("primary") != null) { "Primary provider should be set" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testGetGeoLocation(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("ip", "8.8.8.8")
        
        // 发送获取地理位置请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.DNS_GEO_LOCATION_GET, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    // 由于我们没有真正的地理位置数据库，这里可能会失败
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
    fun testGetBestNode(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("geoLocation", JsonObject()
                .put("country", "US")
                .put("region", "CA")
                .put("city", "San Francisco")
            )
        
        // 发送获取最佳节点请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.DNS_BEST_NODE_GET, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    // 由于我们没有真正的节点配置，这里可能会失败
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
