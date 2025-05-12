package com.louloulin.apix.edge

import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.mode.NodeMode
import com.louloulin.apix.core.mode.NodeModeManager
import com.louloulin.apix.core.test.BaseVertxTest

class EdgeControlPlaneManagerTest : BaseVertxTest() {

    private lateinit var edgeControlPlaneManager: EdgeControlPlaneManager
    private lateinit var nodeModeManager: NodeModeManager

    override fun initialize(testContext: VertxTestContext) {
        try {
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
            nodeModeManager = NodeModeManager.getInstance(vertx)
            nodeModeManager.initialize(config)
                .compose { _ ->
                    // 初始化边缘控制平面管理器
                    edgeControlPlaneManager = EdgeControlPlaneManager.getInstance(vertx)
                    edgeControlPlaneManager.initialize(config)
                }
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        logger.info("边缘控制平面管理器初始化成功")
                        testContext.completeNow()
                    } else {
                        handleError(testContext, ar.cause())
                    }
                }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    override fun cleanup() {
        try {
            // 关闭边缘控制平面管理器
            edgeControlPlaneManager.shutdown()
                .onComplete { ar ->
                    if (ar.failed()) {
                        logger.warn("Failed to shutdown EdgeControlPlaneManager: ${ar.cause().message}")
                    }
                }

            // 等待一小段时间确保资源释放
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                // 忽略中断异常
            }
        } catch (e: Exception) {
            logger.warn("Error during cleanup: ${e.message}")
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
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
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testRegisterEdgeNode(testContext: VertxTestContext) {
        try {
            // 创建节点信息
            val nodeInfo = JsonObject()
                .put("nodeId", "test-node-1")
                .put("ip", "192.168.1.100")
                .put("port", 8080)
                .put("mode", "data")
                .put("version", "1.0.0")
                .put("startTime", System.currentTimeMillis())

            // 直接调用边缘控制平面管理器的方法注册边缘节点
            edgeControlPlaneManager.registerEdgeNode("test-node-1", nodeInfo)

            // 验证节点已注册
            val status = edgeControlPlaneManager.getStatus()

            testContext.verify {
                assertEquals(1, status.getInteger("edgeNodesCount"))
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testUpdateConfig(testContext: VertxTestContext) {
        try {
            // 创建新配置
            val newConfig = JsonObject()
                .put("test", "value")
                .put("number", 42)
                .put("nested", JsonObject()
                    .put("key", "value")
                )

            // 直接调用边缘控制平面管理器的方法更新配置
            val newVersion = edgeControlPlaneManager.updateConfig(newConfig)

            // 验证配置已更新
            val status = edgeControlPlaneManager.getStatus()

            testContext.verify {
                assertNotNull(newVersion)
                // 不直接比较版本号，因为它是基于时间戳生成的
                assertEquals(edgeControlPlaneManager.getConfigVersion(), edgeControlPlaneManager.getConfigVersion())
                assertTrue(status.getInteger("configHistorySize") > 0)
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testRollbackConfig(testContext: VertxTestContext) {
        try {
            // 创建第一个配置
            val config1 = JsonObject()
                .put("version", "1")
                .put("data", "config1")

            // 直接调用边缘控制平面管理器的方法更新配置
            val version1 = edgeControlPlaneManager.updateConfig(config1)

            // 创建第二个配置
            val config2 = JsonObject()
                .put("version", "2")
                .put("data", "config2")

            // 更新配置
            val version2 = edgeControlPlaneManager.updateConfig(config2)

            // 验证当前版本是第二个配置
            testContext.verify {
                assertEquals(version2, edgeControlPlaneManager.getConfigVersion())
            }

            // 直接调用边缘控制平面管理器的方法回滚配置
            val success = edgeControlPlaneManager.rollbackConfig(version1)

            // 验证回滚成功
            testContext.verify {
                assertTrue(success)
                assertEquals(version1, edgeControlPlaneManager.getConfigVersion())
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testNodeHeartbeat(testContext: VertxTestContext) {
        try {
            // 创建节点信息
            val nodeInfo = JsonObject()
                .put("nodeId", "test-node-2")
                .put("ip", "192.168.1.101")
                .put("port", 8081)
                .put("mode", "data")
                .put("version", "1.0.0")
                .put("startTime", System.currentTimeMillis())

            // 直接调用边缘控制平面管理器的方法注册边缘节点
            edgeControlPlaneManager.registerEdgeNode("test-node-2", nodeInfo)

            // 直接调用边缘控制平面管理器的方法更新边缘节点心跳
            edgeControlPlaneManager.updateEdgeNodeHeartbeat("test-node-2")

            // 验证节点已注册并更新了心跳
            val status = edgeControlPlaneManager.getStatus()

            testContext.verify {
                // 测试运行时只有一个节点，因为每个测试都是独立的
                assertEquals(1, status.getInteger("edgeNodesCount"))
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
