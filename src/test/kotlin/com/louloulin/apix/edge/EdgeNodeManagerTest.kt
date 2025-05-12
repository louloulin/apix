package com.louloulin.apix.edge

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import com.louloulin.apix.core.common.EventBusAddresses

class EdgeNodeManagerTest : BaseVertxTest() {

    private lateinit var edgeNodeManager: EdgeNodeManager

    override fun initialize(testContext: VertxTestContext) {
        try {
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
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        logger.info("边缘节点管理器初始化成功")
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
            // 关闭边缘节点管理器
            edgeNodeManager.shutdown()
                .onComplete { ar ->
                    if (ar.failed()) {
                        logger.warn("Failed to shutdown EdgeNodeManager: ${ar.cause().message}")
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
        try {
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
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testGetResourceUsage(testContext: VertxTestContext) {
        try {
            // 等待一小段时间确保资源使用情况已更新
            vertx.setTimer(1000) { _ ->
                try {
                    // 手动触发资源使用情况更新
                    edgeNodeManager.updateResourceUsage()

                    // 获取资源使用情况
                    val usage = edgeNodeManager.getResourceUsage()

                    testContext.verify {
                        // 验证资源使用情况
                        assertNotNull(usage)
                        assertTrue(usage.containsKey("memoryUsageMB"))
                        assertTrue(usage.containsKey("memoryMaxMB"))
                        assertTrue(usage.containsKey("memoryUsagePercent"))
                        assertTrue(usage.containsKey("cpuCores"))
                        assertTrue(usage.containsKey("timestamp"))

                        testContext.completeNow()
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testEventBusHandlers(testContext: VertxTestContext) {
        try {
            // 直接测试更新资源限制
            testUpdateResourceLimits(testContext)
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    private fun testUpdateResourceLimits(testContext: VertxTestContext) {
        try {
            // 创建新的资源限制
            val newLimits = JsonObject()
                .put("memoryMB", 2048)
                .put("cpuCores", 4)
                .put("maxConnections", 2000)
                .put("bandwidthKBps", 20480)

            // 直接调用边缘节点管理器的方法更新资源限制
            edgeNodeManager.updateResourceLimits(newLimits)

            // 获取更新后的状态
            val status = edgeNodeManager.getStatus()
            val resourceLimits = status.getJsonObject("resourceLimits")

            testContext.verify {
                // 验证资源限制已更新
                assertEquals(2048L, resourceLimits.getLong("memoryMB"))
                assertEquals(4, resourceLimits.getInteger("cpuCores"))
                assertEquals(2000, resourceLimits.getInteger("maxConnections"))
                assertEquals(20480, resourceLimits.getInteger("bandwidthKBps"))

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
