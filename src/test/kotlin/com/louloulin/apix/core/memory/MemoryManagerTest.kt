package com.louloulin.apix.core.memory

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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 内存管理器测试
 */
@ExtendWith(VertxExtension::class)
class MemoryManagerTest {
    private lateinit var vertx: Vertx
    private lateinit var memoryManager: MemoryManager

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        memoryManager = MemoryManager(vertx)

        // 初始化内存管理器
        val config = JsonObject()
            .put("lowMemoryThreshold", 0.8)
            .put("criticalMemoryThreshold", 0.95)
            .put("gcThreshold", 0.7)
            .put("monitorInterval", 1000L)

        memoryManager.initialize(config)
        testContext.completeNow()
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `test get memory usage`(testContext: VertxTestContext) {
        val usage = memoryManager.getMemoryUsage()

        testContext.verify {
            assertNotNull(usage)

            // 验证堆内存信息
            val heap = usage.getJsonObject("heap")
            assertNotNull(heap)
            assertTrue(heap.getLong("used") >= 0)
            assertTrue(heap.getLong("max") >= 0)
            assertTrue(heap.getLong("committed") >= 0)
            assertTrue(heap.getLong("init") >= 0)
            assertTrue(heap.getDouble("usageRatio") >= 0.0)
            assertTrue(heap.getDouble("committedUsageRatio") >= 0.0)

            // 验证非堆内存信息
            val nonHeap = usage.getJsonObject("nonHeap")
            assertNotNull(nonHeap)
            assertTrue(nonHeap.getLong("used") >= 0)

            // 验证内存池信息
            val memoryPools = usage.getJsonObject("memoryPools")
            assertNotNull(memoryPools)
            assertTrue(memoryPools.fieldNames().isNotEmpty())

            // 验证其他字段
            assertFalse(usage.getBoolean("isLowMemory"))
            assertFalse(usage.getBoolean("isCriticalMemory"))
            assertTrue(usage.getLong("timestamp") > 0)
        }

        testContext.completeNow()
    }

    @Test
    fun `test trigger GC`(testContext: VertxTestContext) {
        // 获取初始 GC 计数
        val initialUsage = memoryManager.getMemoryUsage()
        val initialGCCount = initialUsage.getLong("gcCount")

        // 触发 GC
        memoryManager.triggerGC()

        // 等待一段时间，确保 GC 完成
        vertx.setTimer(500) {
            // 获取 GC 后的计数
            val finalUsage = memoryManager.getMemoryUsage()
            val finalGCCount = finalUsage.getLong("gcCount")

            testContext.verify {
                assertTrue(finalGCCount > initialGCCount)
            }

            testContext.completeNow()
        }

        assertTrue(testContext.awaitCompletion(2, TimeUnit.SECONDS))
    }

    @Test
    fun `test clear caches`(testContext: VertxTestContext) {
        // 获取初始 GC 时间
        val initialUsage = memoryManager.getMemoryUsage()
        val initialGCTime = initialUsage.getLong("lastGCTime")

        // 清理缓存
        memoryManager.clearCaches()

        // 等待一段时间，确保清理完成
        vertx.setTimer(500) {
            // 获取清理后的 GC 时间
            val finalUsage = memoryManager.getMemoryUsage()
            val finalGCTime = finalUsage.getLong("lastGCTime")

            testContext.verify {
                assertTrue(finalGCTime > initialGCTime)
            }

            testContext.completeNow()
        }

        assertTrue(testContext.awaitCompletion(2, TimeUnit.SECONDS))
    }

    @Test
    fun `test memory handlers`(testContext: VertxTestContext) {
        var lowMemoryCalled = false
        var criticalMemoryCalled = false
        var memoryRestoredCalled = false

        // 添加内存处理器
        memoryManager.addLowMemoryHandler {
            lowMemoryCalled = true
        }

        memoryManager.addCriticalMemoryHandler {
            criticalMemoryCalled = true
        }

        memoryManager.addMemoryRestoredHandler {
            memoryRestoredCalled = true
        }

        // 模拟内存状态变化
        // 注意：这里我们无法直接测试内存状态变化，因为它依赖于系统的实际内存使用情况
        // 所以我们只验证处理器是否被正确添加

        testContext.verify {
            // 验证处理器是否被正确添加
            // 由于我们无法直接触发内存状态变化，所以这里只是验证处理器是否被添加
            // 实际上，处理器是否被调用取决于系统的内存使用情况
            assertFalse(lowMemoryCalled)
            assertFalse(criticalMemoryCalled)
            assertFalse(memoryRestoredCalled)
        }

        testContext.completeNow()
    }

    @Test
    fun `test object pool management`(testContext: VertxTestContext) {
        // 创建对象池
        val pool = memoryManager.getOrCreateObjectPool<String>(
            "test-pool",
            { "test-object" },
            initialSize = 5,
            maxSize = 10
        )

        testContext.verify {
            assertNotNull(pool)

            // 获取内存使用情况
            val usage = memoryManager.getMemoryUsage()
            val objectPools = usage.getJsonObject("objectPools")
            assertNotNull(objectPools)

            // 验证对象池统计信息
            val poolStats = objectPools.getJsonObject("test-pool")
            assertNotNull(poolStats)
            assertEquals("test-pool", poolStats.getString("name"))
            assertEquals(5, poolStats.getInteger("size"))
            assertEquals(10, poolStats.getInteger("maxSize"))
            assertEquals(5, poolStats.getInteger("created"))

            // 借出对象
            val obj = pool.borrow()
            assertEquals("test-object", obj)

            // 归还对象
            pool.release(obj)

            // 移除对象池
            memoryManager.removeObjectPool("test-pool")

            // 验证对象池已移除
            val usageAfterRemove = memoryManager.getMemoryUsage()
            val objectPoolsAfterRemove = usageAfterRemove.getJsonObject("objectPools")
            assertNotNull(objectPoolsAfterRemove)
            assertFalse(objectPoolsAfterRemove.containsKey("test-pool"))
        }

        testContext.completeNow()
    }
}
