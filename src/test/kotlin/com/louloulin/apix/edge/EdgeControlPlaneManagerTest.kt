package com.louloulin.apix.edge

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
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
import com.louloulin.apix.core.mode.NodeMode
import com.louloulin.apix.core.mode.NodeModeManager

@ExtendWith(VertxExtension::class)
class EdgeControlPlaneManagerTest {
    private val logger = LoggerFactory.getLogger(EdgeControlPlaneManagerTest::class.java)

    private lateinit var vertx: Vertx
    private lateinit var edgeControlPlaneManager: EdgeControlPlaneManager

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建测试配置
        val config = JsonObject()
            .put("node", JsonObject()
                .put("mode", "STANDALONE") // 设置为独立模式，同时具有控制平面和数据平面功能
                .put("edge", JsonObject()
                    .put("controlPlane", JsonObject()
                        .put("enabled", true)
                    )
                )
            )

        // 初始化节点模式管理器
        val nodeModeManager = NodeModeManager.getInstance(vertx)
        nodeModeManager.initialize(config)
            .compose { _ ->
                // 初始化边缘控制平面管理器
                edgeControlPlaneManager = EdgeControlPlaneManager.getInstance(vertx)
                edgeControlPlaneManager.initialize(config)
            }
            .onSuccess {
                logger.info("边缘控制平面管理器初始化成功")
                testContext.completeNow()
            }
            .onFailure { cause ->
                logger.error("边缘控制平面管理器初始化失败", cause)
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
        // 获取边缘控制平面状态
        val status = edgeControlPlaneManager.getStatus()

        testContext.verify {
            // 验证状态
            assertTrue(status.getBoolean("enabled"))
            assertNotNull(status.getJsonObject("config"))
            assertEquals(0, status.getInteger("edgeNodesCount"))
            assertNotNull(status.getString("configVersion"))
            assertTrue(status.getInteger("configHistorySize") > 0)
            assertNotNull(status.getLong("timestamp"))

            testContext.completeNow()
        }
    }

    @Test
    fun testRegisterEdgeNode(testContext: VertxTestContext) {
        // 创建节点信息
        val nodeInfo = JsonObject()
            .put("nodeId", "test-node-1")
            .put("ip", "192.168.1.100")
            .put("port", 8080)
            .put("mode", "data")
            .put("version", "1.0.0")
            .put("startTime", System.currentTimeMillis())

        // 注册边缘节点
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_REGISTER, nodeInfo) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("test-node-1", result.getString("nodeId"))
                    assertNotNull(result.getString("configVersion"))
                    assertNotNull(result.getLong("timestamp"))

                    // 获取边缘节点列表
                    vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODES_GET, JsonObject()) { nodesAr ->
                        if (nodesAr.succeeded()) {
                            val nodesResponse = nodesAr.result().body()

                            assertTrue(nodesResponse.getBoolean("success"))
                            val nodes = nodesResponse.getJsonArray("result")
                            assertNotNull(nodes)
                            assertEquals(1, nodes.size())

                            // 验证节点信息
                            val node = nodes.getJsonObject(0)
                            assertEquals("test-node-1", node.getString("nodeId"))
                            assertEquals("192.168.1.100", node.getString("ip"))
                            assertEquals(8080, node.getInteger("port"))

                            testContext.completeNow()
                        } else {
                            testContext.failNow(nodesAr.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testUpdateConfig(testContext: VertxTestContext) {
        // 创建新配置
        val newConfig = JsonObject()
            .put("test", "value")
            .put("number", 42)
            .put("nested", JsonObject()
                .put("key", "value")
            )

        // 更新配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_UPDATE, JsonObject().put("config", newConfig)) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertNotNull(result.getString("version"))
                    assertNotNull(result.getLong("timestamp"))

                    // 获取配置
                    vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_GET, JsonObject()) { configAr ->
                        if (configAr.succeeded()) {
                            val configResponse = configAr.result().body()

                            assertTrue(configResponse.getBoolean("success"))
                            val config = configResponse.getJsonObject("result")
                            assertNotNull(config)

                            // 验证配置内容
                            val version = config.getString("version")
                            val configData = config.getJsonObject("config")
                            assertNotNull(version)
                            assertNotNull(configData)
                            assertEquals("value", configData.getString("test"))
                            assertEquals(42, configData.getInteger("number"))
                            assertNotNull(configData.getJsonObject("nested"))
                            assertEquals("value", configData.getJsonObject("nested").getString("key"))

                            testContext.completeNow()
                        } else {
                            testContext.failNow(configAr.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testRollbackConfig(testContext: VertxTestContext) {
        // 创建第一个配置
        val config1 = JsonObject()
            .put("version", "1")
            .put("data", "config1")

        // 更新配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_UPDATE, JsonObject().put("config", config1)) { ar1 ->
            if (ar1.succeeded()) {
                val response1 = ar1.result().body()
                val version1 = response1.getJsonObject("result").getString("version")

                // 创建第二个配置
                val config2 = JsonObject()
                    .put("version", "2")
                    .put("data", "config2")

                // 更新配置
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_UPDATE, JsonObject().put("config", config2)) { ar2 ->
                    if (ar2.succeeded()) {
                        // 回滚到第一个配置
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_ROLLBACK, JsonObject().put("version", version1)) { rollbackAr ->
                            if (rollbackAr.succeeded()) {
                                val rollbackResponse = rollbackAr.result().body()

                                testContext.verify {
                                    assertTrue(rollbackResponse.getBoolean("success"))
                                    val result = rollbackResponse.getJsonObject("result")
                                    assertNotNull(result)
                                    assertEquals(version1, result.getString("version"))

                                    // 获取配置
                                    vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_GET, JsonObject()) { configAr ->
                                        if (configAr.succeeded()) {
                                            val configResponse = configAr.result().body()

                                            assertTrue(configResponse.getBoolean("success"))
                                            val config = configResponse.getJsonObject("result")
                                            assertNotNull(config)

                                            // 验证配置内容
                                            val version = config.getString("version")
                                            val configData = config.getJsonObject("config")
                                            assertEquals(version1, version)
                                            assertEquals("1", configData.getString("version"))
                                            assertEquals("config1", configData.getString("data"))

                                            testContext.completeNow()
                                        } else {
                                            testContext.failNow(configAr.cause())
                                        }
                                    }
                                }
                            } else {
                                testContext.failNow(rollbackAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(ar2.cause())
                    }
                }
            } else {
                testContext.failNow(ar1.cause())
            }
        }
    }

    @Test
    fun testNodeHeartbeat(testContext: VertxTestContext) {
        // 创建节点信息
        val nodeInfo = JsonObject()
            .put("nodeId", "test-node-2")
            .put("ip", "192.168.1.101")
            .put("port", 8081)
            .put("mode", "data")
            .put("version", "1.0.0")
            .put("startTime", System.currentTimeMillis())

        // 注册边缘节点
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_REGISTER, nodeInfo) { registerAr ->
            if (registerAr.succeeded()) {
                // 发送心跳
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_HEARTBEAT, JsonObject().put("nodeId", "test-node-2")) { heartbeatAr ->
                    if (heartbeatAr.succeeded()) {
                        val heartbeatResponse = heartbeatAr.result().body()

                        testContext.verify {
                            assertTrue(heartbeatResponse.getBoolean("success"))
                            val result = heartbeatResponse.getJsonObject("result")
                            assertNotNull(result)
                            assertEquals("test-node-2", result.getString("nodeId"))
                            assertNotNull(result.getString("configVersion"))
                            assertNotNull(result.getLong("timestamp"))

                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(heartbeatAr.cause())
                    }
                }
            } else {
                testContext.failNow(registerAr.cause())
            }
        }
    }
}
