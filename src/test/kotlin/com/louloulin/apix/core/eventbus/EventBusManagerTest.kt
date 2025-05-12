package com.louloulin.apix.core.eventbus

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.Vertx
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for EventBusManager.
 */
class EventBusManagerTest : BaseVertxTest() {

    private lateinit var eventBusManager: EventBusManager

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 初始化 EventBusManager
            eventBusManager = EventBusManager.getInstance(vertx)
            testContext.completeNow()
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    override fun cleanup() {
        try {
            // 关闭EventBus管理器
            eventBusManager.shutdown()

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
    fun testEventBusManagerSwitchType(testContext: VertxTestContext) {
        try {
            // Test switching to SIMPLE type
            eventBusManager.switchType(EventBusManager.EventBusType.SIMPLE)
                .compose { _ ->
                    // Verify type is SIMPLE
                    testContext.verify {
                        assertEquals(EventBusManager.EventBusType.SIMPLE, eventBusManager.getCurrentType())
                    }

                    // Switch to JCTOOLS type
                    eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
                }
                .compose { _ ->
                    // Verify type is JCTOOLS
                    testContext.verify {
                        assertEquals(EventBusManager.EventBusType.JCTOOLS, eventBusManager.getCurrentType())
                    }

                    // Switch to DISTRIBUTED type
                    eventBusManager.switchType(EventBusManager.EventBusType.DISTRIBUTED)
                }
                .compose { _ ->
                    // Verify type is DISTRIBUTED
                    testContext.verify {
                        assertEquals(EventBusManager.EventBusType.DISTRIBUTED, eventBusManager.getCurrentType())
                    }

                    // Switch to HIGH_PERFORMANCE type
                    eventBusManager.switchType(EventBusManager.EventBusType.HIGH_PERFORMANCE)
                }
                .compose { _ ->
                    // Verify type is HIGH_PERFORMANCE
                    testContext.verify {
                        assertEquals(EventBusManager.EventBusType.HIGH_PERFORMANCE, eventBusManager.getCurrentType())
                    }

                    // Switch back to VERTX type
                    eventBusManager.switchType(EventBusManager.EventBusType.VERTX)
                }
                .onComplete { ar ->
                    handleAsyncResult(testContext, ar) { _ ->
                        // Verify type is VERTX
                        testContext.verify {
                            assertEquals(EventBusManager.EventBusType.VERTX, eventBusManager.getCurrentType())
                            testContext.completeNow()
                        }
                    }
                }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testEventBusManagerSendAndPublish(testContext: VertxTestContext) {
        try {
            // 创建一个计数器来跟踪消息接收
            val messageCounter = java.util.concurrent.atomic.AtomicInteger(0)

            // 注册消费者
            val consumer = vertx.eventBus().consumer<String>("test.address") { message ->
                try {
                    logger.info("Received message: ${message.body()}")
                    testContext.verify {
                        assertEquals("Hello, World!", message.body())
                        if (messageCounter.incrementAndGet() >= 2) {
                            // 只有当收到两条消息时才完成测试
                            testContext.completeNow()
                        }
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            // 等待一小段时间确保消费者注册完成
            vertx.setTimer(500) { _ ->
                // 发送消息
                eventBusManager.send("test.address", "Hello, World!")

                // 发布消息
                eventBusManager.publish("test.address", "Hello, World!")
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testEventBusManagerGetStats(testContext: VertxTestContext) {
        try {
            // Send a few messages
            for (i in 0 until 5) {
                eventBusManager.send("test.stats", "Message $i")
            }

            // Get stats
            val stats = eventBusManager.getStats()

            testContext.verify {
                assertNotNull(stats)
                assertTrue(stats.getLong("messages_sent") >= 5)

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
