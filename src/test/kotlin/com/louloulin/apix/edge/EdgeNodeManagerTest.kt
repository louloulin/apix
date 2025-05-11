package com.louloulin.apix.edge

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

@ExtendWith(VertxExtension::class)
class EdgeNodeManagerTest {
    private val logger = LoggerFactory.getLogger(EdgeNodeManagerTest::class.java)

    private lateinit var vertx: Vertx
    private lateinit var edgeNodeManager: EdgeNodeManager

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

        // 初始化边缘节点管理器
        edgeNodeManager = EdgeNodeManager.getInstance(vertx)
        edgeNodeManager.initialize(config)
            .onSuccess {
                logger.info("边缘节点管理器初始化成功")
                testContext.completeNow()
            }
            .onFailure { cause ->
                logger.error("边缘节点管理器初始化失败", cause)
                testContext.failNow(cause)
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
    fun testGetStatus(testContext: VertxTestContext) {
        // 获取边缘节点状态
        val status = edgeNodeManager.getStatus()

        testContext.verify {
            // 验证状态
            assertTrue(status.getBoolean("enabled"))
            assertNotNull(status.getJsonObject("resourceLimits"))
            assertNotNull(status.getJsonObject("resourceUsage"))
            assertTrue(status.getLong("startupTime") >= 0)

            // 验证资源限制
            val resourceLimits = status.getJsonObject("resourceLimits")
            assertEquals(1024L, resourceLimits.getLong("memoryMB"))
            assertEquals(2, resourceLimits.getInteger("cpuCores"))
            assertEquals(1000, resourceLimits.getInteger("maxConnections"))
            assertEquals(10240, resourceLimits.getInteger("bandwidthKBps"))

            testContext.completeNow()
        }
    }

    @Test
    fun testGetResourceUsage(testContext: VertxTestContext) {
        // 获取资源使用情况
        val usage = edgeNodeManager.getResourceUsage()

        testContext.verify {
            // 验证资源使用情况
            assertNotNull(usage.getLong("memoryUsageMB"))
            assertNotNull(usage.getLong("memoryMaxMB"))
            assertNotNull(usage.getLong("memoryUsagePercent"))
            assertNotNull(usage.getInteger("cpuCores"))
            assertNotNull(usage.getLong("timestamp"))

            testContext.completeNow()
        }
    }

    @Test
    fun testEventBusHandlers(testContext: VertxTestContext) {
        // 测试获取边缘节点状态
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("enabled"))

                    // 测试更新资源限制
                    testUpdateResourceLimits(testContext)
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    private fun testUpdateResourceLimits(testContext: VertxTestContext) {
        // 创建新的资源限制
        val newLimits = JsonObject()
            .put("memoryMB", 2048)
            .put("cpuCores", 4)
            .put("maxConnections", 2000)
            .put("bandwidthKBps", 20480)

        // 更新资源限制
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_RESOURCE_LIMITS_UPDATE, JsonObject().put("limits", newLimits)) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    assertTrue(response.getBoolean("success"))

                    // 获取更新后的状态
                    val status = edgeNodeManager.getStatus()
                    val resourceLimits = status.getJsonObject("resourceLimits")

                    // 验证资源限制已更新
                    assertEquals(2048L, resourceLimits.getLong("memoryMB"))
                    assertEquals(4, resourceLimits.getInteger("cpuCores"))
                    assertEquals(2000, resourceLimits.getInteger("maxConnections"))
                    assertEquals(20480, resourceLimits.getInteger("bandwidthKBps"))

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
