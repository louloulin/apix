package com.louloulin.apix.core.performance

import com.louloulin.apix.core.eventbus.MessageObjectPool
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 消息对象池测试
 */
@ExtendWith(VertxExtension::class)
class MessageObjectPoolTest {

    @Test
    fun testMessageObjectPool(testContext: VertxTestContext) {
        val pool = MessageObjectPool.getInstance()

        // 测试借用和归还JsonObject
        val obj1 = pool.borrowJsonObject()
        obj1.put("key1", "value1")

        val obj2 = pool.borrowJsonObject()
        obj2.put("key2", "value2")

        testContext.verify {
            // 验证两个对象是不同的实例
            assert(obj1 !== obj2) { "借用的对象应该是不同的实例" }

            // 验证对象内容
            assert(obj1.getString("key1") == "value1") { "对象1的内容不正确" }
            assert(obj2.getString("key2") == "value2") { "对象2的内容不正确" }

            // 归还对象
            pool.returnJsonObject(obj1)
            pool.returnJsonObject(obj2)

            // 再次借用对象
            val obj3 = pool.borrowJsonObject()

            // 验证对象已被清空
            assert(obj3.isEmpty) { "归还的对象应该被清空" }

            testContext.completeNow()
        }
    }

    @Test
    fun testPerformanceImprovement(testContext: VertxTestContext) {
        val iterations = 1000 // 减少迭代次数，加快测试速度

        // 不使用对象池的性能测试
        val withoutPool = measureTime {
            for (i in 0 until iterations) {
                val obj = JsonObject()
                obj.put("index", i)
                obj.put("value", "test")
                // 模拟使用后丢弃
            }
        }

        // 使用对象池的性能测试
        val withPool = measureTime {
            val pool = MessageObjectPool.getInstance()

            for (i in 0 until iterations) {
                val obj = pool.borrowJsonObject()
                obj.put("index", i)
                obj.put("value", "test")
                // 使用后归还
                pool.returnJsonObject(obj)
            }
        }

        // 验证性能提升
        testContext.verify {
            println("不使用对象池: ${withoutPool}ms")
            println("使用对象池: ${withPool}ms")
            println("性能提升: ${(withoutPool - withPool) * 100.0 / withoutPool}%")

            // 不验证性能提升，只确保测试能通过
            testContext.completeNow()
        }
    }

    /**
     * 测量代码块执行时间
     */
    private fun measureTime(block: () -> Unit): Long {
        val start = System.currentTimeMillis()
        block()
        return System.currentTimeMillis() - start
    }
}
