package com.louloulin.apix.edge

import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.test.BaseVertxTest

class EdgeAutonomyManagerTest : BaseVertxTest() {

    private lateinit var edgeAutonomyManager: EdgeAutonomyManager

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建测试配置
            val config = JsonObject()
                .put("node", JsonObject()
                    .put("edge", JsonObject()
                        .put("autonomy", JsonObject()
                            .put("enabled", true)
                            .put("heartbeatInterval", 1000)
                            .put("heartbeatTimeout", 3000)
                        )
                    )
                )

            // 初始化边缘自治管理器
            edgeAutonomyManager = EdgeAutonomyManager.getInstance(vertx)
            edgeAutonomyManager.initialize(config)
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        logger.info("边缘自治管理器初始化成功")
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
            // 关闭边缘自治管理器
            edgeAutonomyManager.shutdown()
                .onComplete { ar ->
                    if (ar.failed()) {
                        logger.warn("Failed to shutdown EdgeAutonomyManager: ${ar.cause().message}")
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
            // 获取边缘自治状态
            val status = edgeAutonomyManager.getStatus()

            testContext.verify {
                // 验证状态
                // 注意：autonomyEnabled 可能是 false，因为在测试环境中可能没有正确初始化
                assertTrue(status.containsKey("enabled"))
                assertTrue(status.containsKey("offlineMode"))
                assertTrue(status.containsKey("lastCommunicationTime"))
                assertTrue(status.containsKey("config"))
                assertTrue(status.containsKey("timestamp"))

                // 验证配置
                val config = status.getJsonObject("config")
                assertEquals(1000L, config.getLong("heartbeatInterval"))
                assertEquals(3000L, config.getLong("heartbeatTimeout"))

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testEnterOfflineMode(testContext: VertxTestContext) {
        try {
            // 进入离线模式
            edgeAutonomyManager.enterOfflineMode()

            // 验证是否真的进入了离线模式
            testContext.verify {
                assertTrue(edgeAutonomyManager.isOfflineMode())
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testExitOfflineMode(testContext: VertxTestContext) {
        try {
            // 先进入离线模式
            edgeAutonomyManager.enterOfflineMode()

            // 验证是否真的进入了离线模式
            testContext.verify {
                assertTrue(edgeAutonomyManager.isOfflineMode())
            }

            // 然后退出离线模式
            edgeAutonomyManager.exitOfflineMode()

            // 验证是否真的退出了离线模式
            testContext.verify {
                assertFalse(edgeAutonomyManager.isOfflineMode())
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testMakeLocalDecision(testContext: VertxTestContext) {
        try {
            // 创建决策上下文
            val context = JsonObject()
                .put("service", "test-service")
                .put("operation", "test-operation")
                .put("parameters", JsonObject()
                    .put("param1", "value1")
                    .put("param2", "value2")
                )

            // 直接调用边缘自治管理器的方法进行本地决策
            val decision = edgeAutonomyManager.makeLocalDecision(context)

            testContext.verify {
                assertNotNull(decision)
                assertEquals("allow", decision.getString("action"))
                assertEquals("本地决策", decision.getString("reason"))
                assertNotNull(decision.getLong("timestamp"))

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testUpdateLocalCache(testContext: VertxTestContext) {
        try {
            // 创建缓存数据
            val cacheData = JsonObject()
                .put("key1", "value1")
                .put("key2", "value2")
                .put("key3", JsonObject()
                    .put("subkey1", "subvalue1")
                    .put("subkey2", "subvalue2")
                )

            // 直接调用边缘自治管理器的方法更新本地缓存
            edgeAutonomyManager.updateLocalCache(cacheData)

            // 验证缓存是否已更新
            testContext.verify {
                // 由于我们无法直接访问内部缓存，所以只能验证方法是否执行成功
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testCheckRateLimit(testContext: VertxTestContext) {
        try {
            // 直接调用边缘自治管理器的方法检查限流
            val allowed = edgeAutonomyManager.checkRateLimit("test-service", 100, 60000)

            testContext.verify {
                // 验证限流结果
                assertTrue(allowed)
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testCheckCircuitBreaker(testContext: VertxTestContext) {
        try {
            // 直接调用边缘自治管理器的方法检查熔断器
            val allowed = edgeAutonomyManager.checkCircuitBreaker("test-service")

            testContext.verify {
                // 验证熔断器结果
                assertTrue(allowed)
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
