package com.louloulin.apix.edge

import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.ConfigVerticle

@ExtendWith(VertxExtension::class)
class EdgeNodeVerticleTest {
    private val logger = LoggerFactory.getLogger(EdgeNodeVerticleTest::class.java)

    private lateinit var vertx: Vertx

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建测试配置
        val config = JsonObject()
            .put("node", JsonObject()
                .put("edge", JsonObject()
                    .put("enabled", true)
                    .put("resourceLimits", JsonObject()
                        .put("memoryMB", 1024)
                        .put("cpuCores", 2)
                        .put("maxConnections", 1000)
                        .put("bandwidthKBps", 10240)
                    )
                )
            )

        // 部署ConfigVerticle
        val configOptions = DeploymentOptions().setConfig(config)
        vertx.deployVerticle(ConfigVerticle::class.java.name, configOptions) { configAr ->
            if (configAr.succeeded()) {
                logger.info("ConfigVerticle部署成功")

                // 部署EdgeNodeVerticle
                vertx.deployVerticle(EdgeNodeVerticle::class.java.name) { edgeAr ->
                    if (edgeAr.succeeded()) {
                        logger.info("EdgeNodeVerticle部署成功")
                        testContext.completeNow()
                    } else {
                        logger.error("EdgeNodeVerticle部署失败", edgeAr.cause())
                        testContext.failNow(edgeAr.cause())
                    }
                }
            } else {
                logger.error("ConfigVerticle部署失败", configAr.cause())
                testContext.failNow(configAr.cause())
            }
        }
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close()
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetNodeStatus(testContext: VertxTestContext) {
        // 获取边缘节点状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("enabled"))
                    assertNotNull(result.getJsonObject("resourceLimits"))
                    assertNotNull(result.getJsonObject("resourceUsage"))
                    assertTrue(result.getLong("startupTime") >= 0)

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testGetResourceUsage(testContext: VertxTestContext) {
        // 获取资源使用情况
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_RESOURCE_USAGE_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertNotNull(result.getLong("memoryUsageMB"))
                    assertNotNull(result.getLong("memoryMaxMB"))
                    assertNotNull(result.getLong("memoryUsagePercent"))
                    assertNotNull(result.getInteger("cpuCores"))
                    assertNotNull(result.getLong("timestamp"))

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testUpdateResourceLimits(testContext: VertxTestContext) {
        // 创建新的资源限制
        val newLimits = JsonObject()
            .put("memoryMB", 2048)
            .put("cpuCores", 4)
            .put("maxConnections", 2000)
            .put("bandwidthKBps", 20480)

        // 更新资源限制
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_RESOURCE_LIMITS_UPDATE, JsonObject().put("limits", newLimits)) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    assertTrue(response.getBoolean("success"))

                    // 获取更新后的状态
                    vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_STATUS_GET, JsonObject()) { statusAr ->
                        if (statusAr.succeeded()) {
                            val statusResponse = statusAr.result().body()
                            val status = statusResponse.getJsonObject("result")
                            val resourceLimits = status.getJsonObject("resourceLimits")

                            // 验证资源限制已更新
                            assertEquals(2048L, resourceLimits.getLong("memoryMB"))
                            assertEquals(4, resourceLimits.getInteger("cpuCores"))
                            assertEquals(2000, resourceLimits.getInteger("maxConnections"))
                            assertEquals(20480, resourceLimits.getInteger("bandwidthKBps"))

                            testContext.completeNow()
                        } else {
                            testContext.failNow(statusAr.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
