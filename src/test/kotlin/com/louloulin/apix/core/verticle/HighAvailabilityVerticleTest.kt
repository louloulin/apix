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
 * 高可用性 Verticle 测试。
 */
@ExtendWith(VertxExtension::class)
class HighAvailabilityVerticleTest {
    
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 模拟配置 Verticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("ha", JsonObject()
                        .put("enabled", true)
                        .put("nodeId", "test-node-1")
                        .put("dataPlaneAutonomy", JsonObject()
                            .put("enabled", true)
                        )
                        .put("multiRegion", JsonObject()
                            .put("enabled", true)
                            .put("currentRegion", "region1")
                            .put("regions", JsonObject()
                                .put("region1", JsonObject()
                                    .put("url", "http://region1.example.com")
                                    .put("priority", 10)
                                )
                            )
                        )
                    )
                    .put("node", JsonObject()
                        .put("mode", "STANDALONE")
                    )
                )
            )
        }
        
        // 模拟路由、服务和插件的事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.route.get.all") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("routes", JsonObject()
                        .put("test-route", JsonObject()
                            .put("id", "test-route")
                            .put("path", "/test")
                        )
                    )
                )
            )
        }
        
        vertx.eventBus().consumer<JsonObject>("apix.service.get.all") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("services", JsonObject()
                        .put("test-service", JsonObject()
                            .put("id", "test-service")
                            .put("url", "http://test-service")
                        )
                    )
                )
            )
        }
        
        vertx.eventBus().consumer<JsonObject>("apix.plugin.get.all") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("plugins", JsonObject()
                        .put("test-plugin", JsonObject()
                            .put("id", "test-plugin")
                            .put("enabled", true)
                        )
                    )
                )
            )
        }
        
        // 部署高可用性 Verticle
        vertx.deployVerticle(HighAvailabilityVerticle())
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get ha status`(testContext: VertxTestContext) {
        // 测试获取高可用性状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.HA_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    
                    val ha = result.getJsonObject("ha")
                    assertNotNull(ha)
                    assertTrue(ha.getBoolean("enabled"))
                    
                    val autonomy = result.getJsonObject("autonomy")
                    assertNotNull(autonomy)
                    assertTrue(autonomy.getBoolean("enabled"))
                    
                    val multiRegion = result.getJsonObject("multiRegion")
                    assertNotNull(multiRegion)
                    assertTrue(multiRegion.getBoolean("enabled"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test control plane probe`(testContext: VertxTestContext) {
        // 测试控制平面探测
        vertx.eventBus().request<JsonObject>(EventBusAddresses.HA_CONTROL_PLANE_PROBE, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("HEALTHY", result.getString("status"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
