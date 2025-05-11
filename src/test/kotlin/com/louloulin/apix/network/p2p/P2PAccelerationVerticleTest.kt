package com.louloulin.apix.network.p2p

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
class P2PAccelerationVerticleTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建模拟配置响应
        val configResponse = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("p2p", JsonObject()
                    .put("enabled", true)
                    .put("nodes", JsonArray()
                        .add(JsonObject()
                            .put("id", "node1")
                            .put("name", "Node 1")
                            .put("address", "192.0.2.1")
                            .put("port", 8080)
                            .put("region", "us-east")
                            .put("enabled", true)
                        )
                        .add(JsonObject()
                            .put("id", "node2")
                            .put("name", "Node 2")
                            .put("address", "192.0.2.2")
                            .put("port", 8080)
                            .put("region", "us-west")
                            .put("enabled", true)
                        )
                    )
                    .put("regions", JsonArray()
                        .add(JsonObject()
                            .put("id", "us-east")
                            .put("connections", JsonArray().add("us-west"))
                        )
                        .add(JsonObject()
                            .put("id", "us-west")
                            .put("connections", JsonArray().add("us-east"))
                        )
                    )
                )
            )
        
        // 设置EventBus消息处理器来模拟ConfigVerticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(configResponse)
        }
        
        // 部署P2PAccelerationVerticle
        vertx.deployVerticle(P2PAccelerationVerticle::class.java.name, testContext.succeeding { _ ->
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
    fun testGetP2PStatus(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取点对点加速状态请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.P2P_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getBoolean("enabled", false)) { "P2P should be enabled" }
                    assert(result.getJsonArray("nodes").size() > 0) { "Should have at least one node" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testGetBestRoute(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("sourceRegion", "us-east")
            .put("targetRegion", "us-west")
        
        // 发送获取最佳路由请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.P2P_ROUTE_GET, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response != null) { "Response should not be null" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testAddAccelerationNode(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("id", "node3")
            .put("name", "Node 3")
            .put("address", "192.0.2.3")
            .put("port", 8080)
            .put("region", "eu-west")
        
        // 发送添加加速节点请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.P2P_NODE_ADD, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response != null) { "Response should not be null" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
