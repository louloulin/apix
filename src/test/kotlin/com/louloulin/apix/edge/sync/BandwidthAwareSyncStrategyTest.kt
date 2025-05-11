package com.louloulin.apix.edge.sync

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.*
import io.vertx.core.Future
import io.vertx.core.Promise
import com.louloulin.apix.core.common.EventBusAddresses
import java.util.concurrent.TimeUnit
import org.mockito.Mockito
import org.mockito.Mockito.`when`

@ExtendWith(VertxExtension::class)
class BandwidthAwareSyncStrategyTest {

    private lateinit var vertx: Vertx
    private lateinit var bandwidthMonitor: BandwidthMonitor
    private lateinit var networkDetector: NetworkConditionDetector
    private lateinit var syncStrategy: BandwidthAwareSyncStrategy

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建带宽监控器
        bandwidthMonitor = BandwidthMonitor(vertx)

        // 创建网络条件检测器
        networkDetector = NetworkConditionDetector(vertx)

        // 创建同步策略
        val config = JsonObject()
            .put("defaultRateLimit", 1024 * 1024) // 1MB/s
            .put("minRateLimit", 10 * 1024) // 10KB/s
            .put("maxBandwidthUsage", 0.8)
            .put("baseDelay", 1000)
            .put("highUsageDelay", 3000)
            .put("mediumUsageDelay", 1500)

        syncStrategy = BandwidthAwareSyncStrategy(vertx, config, bandwidthMonitor, networkDetector)

        // 注册模拟的事件总线处理器
        setupMockEventBusHandlers()

        testContext.completeNow()
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        testContext.completeNow()
    }

    /**
     * 设置模拟的事件总线处理器。
     */
    private fun setupMockEventBusHandlers() {
        // 模拟速率限制同步
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONTROL_PLANE_RATE_LIMITED_SYNC) { message ->
            val dataType = message.body().getString("dataType")
            val currentVersion = message.body().getLong("currentVersion", 0L)
            val rateLimit = message.body().getLong("rateLimit", 0L)

            // 返回模拟的同步结果
            val result = JsonObject()
                .put("dataType", dataType)
                .put("version", currentVersion + 1)
                .put("status", "SYNCED")
                .put("syncType", "RATE_LIMITED")
                .put("rateLimit", rateLimit)

            val response = JsonObject()
                .put("success", true)
                .put("result", result)

            message.reply(response)
        }
    }

    @Test
    fun testSyncWithGoodNetworkCondition(testContext: VertxTestContext) {
        // 模拟良好的网络条件
        val goodCondition = NetworkCondition(NetworkStatus.GOOD, 50, 0.0)

        // 模拟低带宽使用率
        val lowBandwidthUsage = BandwidthUsage(512 * 1024, 10 * 1024 * 1024, 0.05)

        // 使用模拟对象
        val mockNetworkDetector = Mockito.mock(NetworkConditionDetector::class.java)
        `when`(mockNetworkDetector.getCurrentCondition()).thenReturn(goodCondition)

        val mockBandwidthMonitor = Mockito.mock(BandwidthMonitor::class.java)
        `when`(mockBandwidthMonitor.getCurrentBandwidthUsage()).thenReturn(lowBandwidthUsage)

        // 创建使用模拟对象的同步策略
        val mockSyncStrategy = BandwidthAwareSyncStrategy(
            vertx,
            JsonObject().put("defaultRateLimit", 1024 * 1024),
            mockBandwidthMonitor,
            mockNetworkDetector
        )

        // 执行同步
        mockSyncStrategy.sync("routes", 5L)
            .onComplete(testContext.succeeding { result ->
                // 验证结果
                testContext.verify {
                    assertEquals("routes", result.getString("dataType"))
                    assertEquals(6L, result.getLong("version"))
                    assertEquals("SYNCED", result.getString("status"))
                    assertEquals("RATE_LIMITED", result.getString("syncType"))
                }

                testContext.completeNow()
            })
    }

    @Test
    fun testSyncWithPoorNetworkCondition(testContext: VertxTestContext) {
        // 模拟较差的网络条件
        val poorCondition = NetworkCondition(NetworkStatus.POOR, 500, 0.1)

        // 模拟高带宽使用率
        val highBandwidthUsage = BandwidthUsage(8 * 1024 * 1024, 10 * 1024 * 1024, 0.85)

        // 使用模拟对象
        val mockNetworkDetector = Mockito.mock(NetworkConditionDetector::class.java)
        `when`(mockNetworkDetector.getCurrentCondition()).thenReturn(poorCondition)

        val mockBandwidthMonitor = Mockito.mock(BandwidthMonitor::class.java)
        `when`(mockBandwidthMonitor.getCurrentBandwidthUsage()).thenReturn(highBandwidthUsage)

        // 创建使用模拟对象的同步策略
        val mockSyncStrategy = BandwidthAwareSyncStrategy(
            vertx,
            JsonObject()
                .put("defaultRateLimit", 1024 * 1024)
                .put("maxBandwidthUsage", 0.8)
                .put("baseDelay", 100) // 使用较短的延迟以加快测试
                .put("highUsageDelay", 200),
            mockBandwidthMonitor,
            mockNetworkDetector
        )

        // 执行同步，应该延迟执行
        mockSyncStrategy.sync("routes", 5L)
            .onComplete(testContext.succeeding { result ->
                // 验证结果
                testContext.verify {
                    assertEquals("routes", result.getString("dataType"))
                    assertEquals(6L, result.getLong("version"))
                    assertEquals("SYNCED", result.getString("status"))
                    assertEquals("RATE_LIMITED", result.getString("syncType"))
                }

                testContext.completeNow()
            })

        // 等待测试完成
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun testSyncWithUnavailableNetwork(testContext: VertxTestContext) {
        // 模拟网络不可用
        val unavailableCondition = NetworkCondition(NetworkStatus.UNAVAILABLE, 0, 1.0)

        // 使用模拟对象
        val mockNetworkDetector = Mockito.mock(NetworkConditionDetector::class.java)
        `when`(mockNetworkDetector.getCurrentCondition()).thenReturn(unavailableCondition)

        val mockBandwidthMonitor = Mockito.mock(BandwidthMonitor::class.java)
        `when`(mockBandwidthMonitor.getCurrentBandwidthUsage()).thenReturn(BandwidthUsage(0, 1024 * 1024, 0.0))

        // 创建使用模拟对象的同步策略
        val mockSyncStrategy = BandwidthAwareSyncStrategy(
            vertx,
            JsonObject().put("defaultRateLimit", 1024 * 1024),
            mockBandwidthMonitor,
            mockNetworkDetector
        )

        // 执行同步，应该失败
        mockSyncStrategy.sync("routes", 5L)
            .onComplete(testContext.failing { throwable ->
                // 验证错误
                testContext.verify {
                    assertEquals("Network unavailable", throwable.message)
                }

                testContext.completeNow()
            })
    }

    @Test
    fun testAdjustSyncStrategy(testContext: VertxTestContext) {
        // 模拟不同的网络条件和带宽使用情况
        val conditions = listOf(
            Pair(NetworkCondition(NetworkStatus.GOOD, 50, 0.0), BandwidthUsage(1 * 1024 * 1024, 10 * 1024 * 1024, 0.1)),
            Pair(NetworkCondition(NetworkStatus.FAIR, 200, 0.05), BandwidthUsage(5 * 1024 * 1024, 10 * 1024 * 1024, 0.5)),
            Pair(NetworkCondition(NetworkStatus.POOR, 500, 0.1), BandwidthUsage(8 * 1024 * 1024, 10 * 1024 * 1024, 0.8))
        )

        // 对每种情况执行同步
        var index = 0
        var lastRateLimit = 0L

        fun testNextCondition() {
            if (index < conditions.size) {
                val (condition, usage) = conditions[index]

                // 使用模拟对象
                val mockNetworkDetector = Mockito.mock(NetworkConditionDetector::class.java)
                `when`(mockNetworkDetector.getCurrentCondition()).thenReturn(condition)

                val mockBandwidthMonitor = Mockito.mock(BandwidthMonitor::class.java)
                `when`(mockBandwidthMonitor.getCurrentBandwidthUsage()).thenReturn(usage)

                // 创建使用模拟对象的同步策略
                val mockSyncStrategy = BandwidthAwareSyncStrategy(
                    vertx,
                    JsonObject().put("defaultRateLimit", 1024 * 1024),
                    mockBandwidthMonitor,
                    mockNetworkDetector
                )

                // 执行同步
                mockSyncStrategy.sync("routes", 5L)
                    .onComplete { ar ->
                        if (ar.succeeded()) {
                            val result = ar.result()
                            val currentRateLimit = result.getLong("rateLimit", 0L)

                            // 验证速率限制根据网络条件和带宽使用情况调整
                            if (index > 0) {
                                // 网络条件越差，速率限制应该越低
                                testContext.verify {
                                    assertTrue(currentRateLimit <= lastRateLimit)
                                }
                            }

                            lastRateLimit = currentRateLimit
                            index++

                            // 测试下一个条件
                            if (index < conditions.size) {
                                testNextCondition()
                            } else {
                                testContext.completeNow()
                            }
                        } else {
                            testContext.failNow(ar.cause())
                        }
                    }
            }
        }

        // 开始测试
        testNextCondition()

        // 等待测试完成
        assertTrue(testContext.awaitCompletion(10, TimeUnit.SECONDS))
    }
}
