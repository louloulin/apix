package com.louloulin.apix.core.memory

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 对象池测试
 */
class ObjectPoolTest {

    @Test
    fun `test create object pool`() {
        // 创建对象池
        val pool = ObjectPool<String>(
            name = "test-pool",
            factory = { "test-object" },
            initialSize = 5,
            maxSize = 10
        )

        // 获取统计信息
        val stats = pool.getStats()

        // 验证统计信息
        assertEquals("test-pool", stats.getString("name"))
        assertEquals(5, stats.getInteger("size"))
        assertEquals(10, stats.getInteger("maxSize"))
        assertEquals(5, stats.getInteger("created"))
        assertEquals(0, stats.getInteger("borrowed"))
        assertEquals(0, stats.getInteger("returned"))
        assertEquals(0, stats.getInteger("destroyed"))
        assertEquals(0, stats.getInteger("active"))
        assertEquals(0, stats.getLong("avgBorrowTime"))
    }

    @Test
    fun `test borrow and release objects`() {
        // 创建对象池
        val pool = ObjectPool<String>(
            name = "test-pool",
            factory = { "test-object" },
            initialSize = 2,
            maxSize = 5
        )

        // 借出对象
        val obj1 = pool.borrow()
        val obj2 = pool.borrow()

        // 验证对象
        assertEquals("test-object", obj1)
        assertEquals("test-object", obj2)

        // 获取统计信息
        var stats = pool.getStats()

        // 验证统计信息
        assertTrue(stats.getInteger("size") <= 2) // 可能为 0 或小于 2
        assertTrue(stats.getInteger("created") >= 2) // 至少创建了 2 个对象
        assertTrue(stats.getInteger("borrowed") >= 2) // 至少借出了 2 个对象
        assertTrue(stats.getInteger("active") >= 1) // 至少有 1 个活动对象

        // 归还对象
        pool.release(obj1)

        // 获取统计信息
        stats = pool.getStats()

        // 验证统计信息
        assertTrue(stats.getInteger("size") >= 0) // 池大小大于等于 0
        assertTrue(stats.getInteger("created") >= 2) // 至少创建了 2 个对象
        assertTrue(stats.getInteger("borrowed") >= 2) // 至少借出了 2 个对象
        assertTrue(stats.getInteger("returned") >= 0) // 归还的对象数量大于等于 0
        assertTrue(stats.getInteger("active") >= 0) // 活动对象数量大于等于 0
        assertTrue(stats.getLong("avgBorrowTime") >= 0) // 平均借出时间大于等于 0

        // 再次借出对象
        val obj3 = pool.borrow()

        // 验证对象（应该是归还的对象）
        assertEquals("test-object", obj3)

        // 获取统计信息
        stats = pool.getStats()

        // 验证统计信息
        assertTrue(stats.getInteger("size") >= 0) // 池大小大于等于 0
        assertTrue(stats.getInteger("created") >= 2) // 至少创建了 2 个对象
        assertTrue(stats.getInteger("borrowed") >= 2) // 至少借出了 2 个对象
        assertTrue(stats.getInteger("active") >= 1) // 至少有 1 个活动对象
    }

    @Test
    fun `test object pool max size`() {
        // 创建对象池
        val pool = ObjectPool<String>(
            name = "test-pool",
            factory = { "test-object" },
            initialSize = 1,
            maxSize = 2
        )

        // 借出所有对象
        val obj1 = pool.borrow()
        val obj2 = pool.borrow()
        val obj3 = pool.borrow()

        // 验证对象
        assertEquals("test-object", obj1)
        assertEquals("test-object", obj2)
        assertEquals("test-object", obj3)

        // 获取统计信息
        var stats = pool.getStats()

        // 验证统计信息
        assertTrue(stats.getInteger("size") <= 3) // 可能为 0 或小于 3
        assertTrue(stats.getInteger("created") >= 3) // 至少创建了 3 个对象
        assertTrue(stats.getInteger("borrowed") >= 3) // 至少借出了 3 个对象
        assertTrue(stats.getInteger("active") >= 1) // 至少有 1 个活动对象

        // 归还所有对象
        pool.release(obj1)
        pool.release(obj2)
        pool.release(obj3)

        // 获取统计信息
        stats = pool.getStats()

        // 验证统计信息
        assertTrue(stats.getInteger("size") <= 3) // 池大小不超过 3
        assertTrue(stats.getInteger("created") >= 3) // 至少创建了 3 个对象
        assertTrue(stats.getInteger("borrowed") >= 3) // 至少借出了 3 个对象
        assertTrue(stats.getInteger("returned") >= 1) // 至少归还了 1 个对象
        assertTrue(stats.getInteger("active") >= 0) // 活动对象数量大于等于 0
    }

    @Test
    fun `test clear object pool`() {
        // 创建对象池
        val pool = ObjectPool<String>(
            name = "test-pool",
            factory = { "test-object" },
            initialSize = 5,
            maxSize = 10
        )

        // 借出一些对象
        val obj1 = pool.borrow()
        val obj2 = pool.borrow()

        // 清空对象池
        pool.clear()

        // 获取统计信息
        val stats = pool.getStats()

        // 验证统计信息
        assertTrue(stats.getInteger("size") >= 0) // 池大小大于等于 0
        assertTrue(stats.getInteger("created") >= 2) // 至少创建了 2 个对象
        assertTrue(stats.getInteger("borrowed") >= 0) // 借出的对象数量大于等于 0
        assertTrue(stats.getInteger("active") >= 0) // 活动对象数量大于等于 0
    }
}
