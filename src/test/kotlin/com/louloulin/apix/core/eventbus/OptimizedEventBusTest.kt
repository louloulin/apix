package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 优化后的EventBus测试类
 */
@ExtendWith(VertxExtension::class)
class OptimizedEventBusTest {
    private val logger = LoggerFactory.getLogger(OptimizedEventBusTest::class.java)

    private lateinit var vertx: Vertx
    private lateinit var jcToolsEventBus: JCToolsEventBus
    private lateinit var eventBusManager: EventBusManager

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        jcToolsEventBus = JCToolsEventBus.getInstance(vertx)
        eventBusManager = EventBusManager.getInstance(vertx)

        // 启动JCToolsEventBus
        jcToolsEventBus.start()
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }

    /**
     * 测试JCToolsEventBus统计信息
     */
    @Test
    fun testJCToolsEventBusStats(testContext: VertxTestContext) {
        // 获取统计信息
        val stats = jcToolsEventBus.getStats()

        testContext.verify {
            assertNotNull(stats)
            assertTrue(stats.getBoolean("started"))
            assertEquals(0, stats.getLong("messages_sent"))
            assertEquals(0, stats.getLong("messages_processed"))

            testContext.completeNow()
        }
    }

    /**
     * 测试EventBusManager统计信息
     */
    @Test
    fun testEventBusManagerStats(testContext: VertxTestContext) {
        // 获取统计信息
        val stats = eventBusManager.getStats()

        testContext.verify {
            assertNotNull(stats)
            assertEquals(EventBusManager.EventBusType.VERTX.name, stats.getString("type"))
            assertEquals(0, stats.getLong("messages_sent"))

            testContext.completeNow()
        }
    }

    /**
     * 测试发送消息
     */
    @Test
    fun testSendMessage(testContext: VertxTestContext) {
        // 注册消费者
        vertx.eventBus().consumer<JsonObject>("test.send") { message ->
            testContext.verify {
                assertEquals("test value", message.body().getString("value"))
                testContext.completeNow()
            }
        }

        // 发送消息
        eventBusManager.send("test.send", JsonObject().put("value", "test value"))
    }

    /**
     * 测试发布消息
     */
    @Test
    fun testPublishMessage(testContext: VertxTestContext) {
        // 创建多个消费者
        val consumerCount = 3
        val latch = CountDownLatch(consumerCount)
        val receivedCount = AtomicInteger(0)

        for (i in 1..consumerCount) {
            vertx.eventBus().consumer<JsonObject>("test.publish") { message ->
                receivedCount.incrementAndGet()
                latch.countDown()
            }
        }

        // 发布消息
        eventBusManager.publish("test.publish", JsonObject().put("value", "test value"))

        // 等待所有消费者接收消息
        latch.await(5, TimeUnit.SECONDS)

        testContext.verify {
            assertEquals(consumerCount, receivedCount.get())
            testContext.completeNow()
        }
    }

    /**
     * 测试切换EventBus类型
     */
    @Test
    fun testSwitchEventBusType(testContext: VertxTestContext) {
        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .compose { success ->
                testContext.verify {
                    assertTrue(success)
                    assertEquals(EventBusManager.EventBusType.JCTOOLS, eventBusManager.getCurrentType())
                }

                // 获取统计信息
                val stats = eventBusManager.getStats()
                testContext.verify {
                    assertNotNull(stats)
                    assertEquals(EventBusManager.EventBusType.JCTOOLS.name, stats.getString("type"))
                    assertEquals(1, stats.getLong("switch_count"))
                    assertNotNull(stats.getJsonObject("jctools"))
                }

                // 切换回原生EventBus
                eventBusManager.switchType(EventBusManager.EventBusType.VERTX)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    assertEquals(EventBusManager.EventBusType.VERTX, eventBusManager.getCurrentType())

                    // 获取统计信息
                    val stats = eventBusManager.getStats()
                    assertEquals(2, stats.getLong("switch_count"))

                    testContext.completeNow()
                }
            }
    }

    /**
     * 测试高并发发送消息
     */
    @Test
    fun testHighConcurrencySend(testContext: VertxTestContext) {
        // 测试参数
        val messageCount = 1000
        val latch = CountDownLatch(messageCount)
        val receivedCount = AtomicInteger(0)

        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .compose<Boolean> { success ->
                testContext.verify {
                    assertTrue(success)
                }

                // 注册消费者
                vertx.eventBus().consumer<JsonObject>("test.concurrency") { message ->
                    receivedCount.incrementAndGet()
                    latch.countDown()
                }

                // 发送大量消息
                for (i in 1..messageCount) {
                    val message = JsonObject()
                        .put("index", i)
                        .put("value", "test")
                        .put("timestamp", System.currentTimeMillis())

                    eventBusManager.send("test.concurrency", message)
                }

                // 等待所有消息处理完成
                val waitResult = latch.await(10, TimeUnit.SECONDS)

                testContext.verify {
                    assertTrue(waitResult, "等待消息处理超时")
                    assertEquals(messageCount, receivedCount.get())

                    // 获取统计信息
                    val stats = eventBusManager.getStats()
                    val jcToolsStats = stats.getJsonObject("jctools")

                    logger.info("EventBusManager统计信息: {}", stats.encode())
                    logger.info("JCToolsEventBus统计信息: {}", jcToolsStats.encode())

                    assertTrue(stats.getLong("messages_sent") >= messageCount)
                    assertTrue(jcToolsStats.getLong("messages_processed") >= messageCount)

                    testContext.completeNow()
                }

                Future.succeededFuture<Boolean>(true)
            }
    }

    /**
     * 测试重置统计信息
     */
    @Test
    fun testResetStats(testContext: VertxTestContext) {
        // 发送一些消息
        for (i in 1..10) {
            eventBusManager.send("test.reset", JsonObject().put("index", i))
        }

        // 获取统计信息
        val statsBefore = eventBusManager.getStats()

        testContext.verify {
            assertEquals(10, statsBefore.getLong("messages_sent"))
        }

        // 重置统计信息
        eventBusManager.resetStats()

        // 获取重置后的统计信息
        val statsAfter = eventBusManager.getStats()

        testContext.verify {
            assertEquals(0, statsAfter.getLong("messages_sent"))
            testContext.completeNow()
        }
    }
}
