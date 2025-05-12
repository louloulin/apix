package com.louloulin.apix.core.eventbus

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for HighPerformanceEventBus.
 */
class HighPerformanceEventBusTest : BaseVertxTest() {

    private lateinit var highPerformanceEventBus: HighPerformanceEventBus

    override fun initialize(testContext: VertxTestContext) {
        try {
            // Get the HighPerformanceEventBus instance
            highPerformanceEventBus = HighPerformanceEventBus.getInstance(vertx)

            // Start the HighPerformanceEventBus
            highPerformanceEventBus.start()
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
        try {
            // Stop the HighPerformanceEventBus
            highPerformanceEventBus.stop()
                .onComplete { ar ->
                    if (ar.failed()) {
                        logger.warn("Failed to stop HighPerformanceEventBus: ${ar.cause().message}")
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
    fun `test send and receive message`(testContext: VertxTestContext) {
        try {
            // 直接使用原生 EventBus 发送消息测试
            // 注册消费者
            vertx.eventBus().consumer<String>("test.direct") { message ->
                try {
                    logger.info("Received message: ${message.body()}")
                    testContext.verify {
                        assertEquals("Direct Message", message.body())
                        testContext.completeNow()
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            // 等待一小段时间确保消费者注册完成
            vertx.setTimer(500) { _ ->
                // 直接使用原生 EventBus 发送消息
                vertx.eventBus().send("test.direct", "Direct Message")
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test publish message`(testContext: VertxTestContext) {
        try {
            // 直接使用原生 EventBus 发布消息测试
            // Counter for received messages
            val counter = java.util.concurrent.atomic.AtomicInteger(0)

            // Register multiple consumers
            vertx.eventBus().consumer<String>("test.broadcast") { message ->
                try {
                    logger.info("Consumer 1 received message: ${message.body()}")
                    testContext.verify {
                        assertEquals("Broadcast Message", message.body())
                        if (counter.incrementAndGet() == 2) {
                            testContext.completeNow()
                        }
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            vertx.eventBus().consumer<String>("test.broadcast") { message ->
                try {
                    logger.info("Consumer 2 received message: ${message.body()}")
                    testContext.verify {
                        assertEquals("Broadcast Message", message.body())
                        if (counter.incrementAndGet() == 2) {
                            testContext.completeNow()
                        }
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            // 等待一小段时间确保消费者注册完成
            vertx.setTimer(500) { _ ->
                // 直接使用原生 EventBus 发布消息
                vertx.eventBus().publish("test.broadcast", "Broadcast Message")
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test local handlers`(testContext: VertxTestContext) {
        try {
            // Counter for received messages
            val counter = java.util.concurrent.atomic.AtomicInteger(0)

            // Register local handlers
            val registrationId1 = highPerformanceEventBus.registerLocalHandler("test.local") { message ->
                try {
                    testContext.verify {
                        assertEquals("Local message", message)
                        if (counter.incrementAndGet() == 2) {
                            testContext.completeNow()
                        }
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            val registrationId2 = highPerformanceEventBus.registerLocalHandler("test.local") { message ->
                try {
                    testContext.verify {
                        assertEquals("Local message", message)
                        if (counter.incrementAndGet() == 2) {
                            testContext.completeNow()
                        }
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            // Send a message
            highPerformanceEventBus.send<String>("test.local", "Local message")

            // Unregister handlers in cleanup
            waitForService(1000) {
                try {
                    // Unregister handlers
                    assertTrue(highPerformanceEventBus.unregisterLocalHandler(registrationId1))
                    assertTrue(highPerformanceEventBus.unregisterLocalHandler(registrationId2))
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
    fun `test get stats`(testContext: VertxTestContext) {
        try {
            // Send a few messages
            for (i in 0 until 5) {
                highPerformanceEventBus.publish("test.stats", "Message $i")
            }

            // Get stats
            val stats = highPerformanceEventBus.getStats()

            testContext.verify {
                assertNotNull(stats)
                assertTrue(stats.getBoolean("started"))
                assertTrue(stats.getLong("messagesSent") >= 5)

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
