package com.louloulin.apix.edge.sync

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import io.vertx.core.buffer.Buffer
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Timeout

class EdgeSyncManagerTest : BaseVertxTest() {

    private lateinit var syncManager: EdgeSyncManager
    private lateinit var bandwidthMonitor: BandwidthMonitor
    private lateinit var networkDetector: NetworkConditionDetector
    private lateinit var diffCalculator: DataDiffCalculator
    private lateinit var conflictResolver: ConflictResolver

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建同步管理器
            syncManager = EdgeSyncManager.getInstance(vertx)

            // 初始化同步管理器 - 使用较小的同步间隔以减少测试时间
            val config = JsonObject()
                .put("enabled", true)
                .put("syncInterval", 1000) // 减少同步间隔，避免长时间运行
                .put("strategy", JsonObject()
                    .put("type", "incremental")
                    .put("maxIncrementalVersionGap", 5)
                )

            // 创建其他测试所需的组件
            bandwidthMonitor = BandwidthMonitor(vertx)
            networkDetector = NetworkConditionDetector(vertx)
            diffCalculator = DataDiffCalculator()
            conflictResolver = ConflictResolver()

            syncManager.initialize(config)
                .onComplete { ar ->
                    if (ar.succeeded()) {
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
        // 停止带宽监控和网络检测器
        try {
            // 确保停止所有周期性任务
            syncManager.shutdown()
            bandwidthMonitor.stop()
            networkDetector.stop()

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
    fun testCompressAndDecompress(testContext: VertxTestContext) {
        try {
            // 创建测试数据
            val testData = JsonObject()
                .put("name", "test")
                .put("value", "This is a test string that should be compressed")
                .put("nested", JsonObject()
                    .put("key1", "value1")
                    .put("key2", "value2")
                )

            // 压缩数据
            val compressed = syncManager.compressData(testData.encode())

            // 解压数据
            val decompressed = syncManager.decompressData(compressed)

            // 验证解压后的数据与原始数据相同
            val decompressedJson = JsonObject(decompressed)
            testContext.verify {
                assertEquals(testData.getString("name"), decompressedJson.getString("name"))
                assertEquals(testData.getString("value"), decompressedJson.getString("value"))
                assertEquals(testData.getJsonObject("nested").getString("key1"),
                             decompressedJson.getJsonObject("nested").getString("key1"))
                assertEquals(testData.getJsonObject("nested").getString("key2"),
                             decompressedJson.getJsonObject("nested").getString("key2"))
            }

            testContext.completeNow()
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testCalculateDiffAndApplyDiff(testContext: VertxTestContext) {
        try {
            // 创建基础数据
            val baseData = JsonObject()
                .put("key1", "value1")
                .put("key2", "value2")
                .put("key3", "value3")

            // 创建目标数据（有添加、修改和删除操作）
            val targetData = JsonObject()
                .put("key1", "value1")          // 保持不变
                .put("key2", "modified value")  // 修改
                .put("key4", "new value")       // 添加
                // key3 被删除

            // 计算差异
            val diff = diffCalculator.calculateDiff(baseData, targetData)

            // 验证差异
            testContext.verify {
                // 验证添加操作
                val added = diff.getJsonObject("added")
                assertEquals("new value", added.getString("key4"))

                // 验证修改操作
                val modified = diff.getJsonObject("modified")
                assertEquals("modified value", modified.getString("key2"))

                // 验证删除操作
                val deleted = diff.getJsonArray("deleted")
                assertEquals("key3", deleted.getString(0))
            }

            // 应用差异
            val mergedData = diffCalculator.applyDiff(baseData, diff)

            // 验证合并后的数据
            testContext.verify {
                assertEquals("value1", mergedData.getString("key1"))
                assertEquals("modified value", mergedData.getString("key2"))
                assertEquals("new value", mergedData.getString("key4"))
                assertFalse(mergedData.containsKey("key3"))
            }

            testContext.completeNow()
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testConflictDetectionAndResolution(testContext: VertxTestContext) {
        try {
            // 创建基础数据
            val baseData = JsonObject()
                .put("key1", "value1")
                .put("key2", JsonObject()
                    .put("nested1", "nestedValue1")
                    .put("nested2", "nestedValue2")
                )

            // 创建合并后的数据（有冲突）
            val mergedData = JsonObject()
                .put("key1", "conflictValue")
                .put("key2", JsonObject()
                    .put("nested1", "nestedValue1")
                    .put("nested2", "conflictNestedValue")
                )

            // 检测冲突
            val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

            // 验证冲突
            testContext.verify {
                assertEquals(2, conflicts.size)

                // 验证第一个冲突
                val conflict1 = conflicts.find { it.path == "key1" }
                assertNotNull(conflict1)
                assertEquals("value1", conflict1?.baseValue)
                assertEquals("conflictValue", conflict1?.mergedValue)

                // 验证第二个冲突
                val conflict2 = conflicts.find { it.path == "key2.nested2" }
                assertNotNull(conflict2)
                assertEquals("nestedValue2", conflict2?.baseValue)
                assertEquals("conflictNestedValue", conflict2?.mergedValue)
            }

            // 解决冲突
            val resolvedData = conflictResolver.resolveConflicts(baseData, mergedData, conflicts)

            // 验证解决后的数据
            testContext.verify {
                assertEquals("conflictValue", resolvedData.getString("key1"))
                assertEquals("nestedValue1", resolvedData.getJsonObject("key2").getString("nested1"))
                assertEquals("conflictNestedValue", resolvedData.getJsonObject("key2").getString("nested2"))
            }

            testContext.completeNow()
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testBandwidthMonitor(testContext: VertxTestContext) {
        try {
            // 启动带宽监控
            bandwidthMonitor.start()

            // 记录一些数据传输
            bandwidthMonitor.recordBytesSent(1024 * 1024) // 1MB
            bandwidthMonitor.recordBytesReceived(2 * 1024 * 1024) // 2MB

            // 等待一段时间，让带宽监控器更新统计信息
            waitForService(2000) {
                try {
                    // 获取带宽使用情况
                    val usage = bandwidthMonitor.getCurrentBandwidthUsage()

                    // 验证带宽使用情况
                    testContext.verify {
                        assertTrue(usage.bytesPerSecond > 0)
                        assertTrue(usage.usageRatio >= 0.0)
                    }

                    // 停止带宽监控
                    bandwidthMonitor.stop()

                    testContext.completeNow()
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
    fun testNetworkConditionDetector(testContext: VertxTestContext) {
        try {
            // 启动网络条件检测
            networkDetector.start()

            // 等待一段时间，让网络条件检测器更新状态
            waitForService(3000) {
                try {
                    // 获取网络条件
                    val condition = networkDetector.getCurrentCondition()

                    // 验证网络条件
                    testContext.verify {
                        assertNotEquals(NetworkStatus.UNKNOWN, condition.status)
                        assertTrue(condition.latency >= 0)
                        assertTrue(condition.packetLoss >= 0.0)
                    }

                    // 停止网络条件检测
                    networkDetector.stop()

                    testContext.completeNow()
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
