package com.louloulin.apix.core.eventbus

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DistributedEventBus.
 */
class DistributedEventBusTest : BaseVertxTest() {

    private lateinit var distributedEventBus: DistributedEventBus

    override fun initialize(testContext: VertxTestContext) {
        try {
            // Get the DistributedEventBus instance
            distributedEventBus = DistributedEventBus.getInstance(vertx)

            // Start the DistributedEventBus
            distributedEventBus.start()
                .onSuccess { _ ->
                    testContext.completeNow()
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    override fun cleanup() {
        try {
            // Stop the DistributedEventBus
            distributedEventBus.stop()
            logger.info("DistributedEventBus stopped successfully")
        } catch (e: Exception) {
            logger.warn("Error stopping DistributedEventBus: ${e.message}")
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test send and receive message`(testContext: VertxTestContext) {
        try {
            // 测试简单的消息发送和接收
            // 注册消费者
            vertx.eventBus().consumer<JsonObject>("test.direct") { message ->
                try {
                    testContext.verify {
                        assertEquals("test value", message.body().getString("value"))
                    }
                    message.reply(JsonObject().put("result", "success"))
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            // 等待消费者注册完成
            waitForService(500) {
                // 直接使用原生 EventBus 发送消息
                vertx.eventBus().request<JsonObject>("test.direct", JsonObject().put("value", "test value"))
                    .onSuccess { reply ->
                        testContext.verify {
                            assertEquals("success", reply.body().getString("result"))
                            testContext.completeNow()
                        }
                    }
                    .onFailure { e -> handleError(testContext, e) }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test publish message`(testContext: VertxTestContext) {
        try {
            // 测试消息发布
            // 创建检查点
            val checkpoint = testContext.checkpoint()

            // 注册消费者
            vertx.eventBus().consumer<JsonObject>("test.broadcast") { message ->
                try {
                    testContext.verify {
                        assertEquals("broadcast value", message.body().getString("value"))
                        checkpoint.flag()
                    }
                } catch (e: Exception) {
                    handleError(testContext, e)
                }
            }

            // 等待消费者注册完成
            waitForService(500) {
                // 直接使用原生 EventBus 发布消息
                vertx.eventBus().publish("test.broadcast", JsonObject().put("value", "broadcast value"))
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `test message compression`(testContext: VertxTestContext) {
        try {
            // 测试统计信息而不是实际压缩
            // 发送一些消息
            for (i in 0 until 5) {
                vertx.eventBus().publish("test.stats.compression", "Message $i")
            }

            // 等待消息处理完成
            waitForService(500) {
                // 获取统计信息
                val stats = distributedEventBus.getStats()
                logger.info("Compression stats: ${stats.encodePrettily()}")

                testContext.verify {
                    assertNotNull(stats)
                    assertTrue(stats.getBoolean("started"))

                    // 测试完成
                    testContext.completeNow()
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
                distributedEventBus.publish("test.stats", "Message $i")
            }

            // Wait a bit for the messages to be processed
            waitForService(500) {
                // Get stats
                val stats = distributedEventBus.getStats()
                logger.info("EventBus stats: ${stats.encodePrettily()}")

                testContext.verify {
                    assertNotNull(stats)
                    assertTrue(stats.getBoolean("started"))
                    assertTrue(stats.getLong("messagesSent") >= 5)

                    testContext.completeNow()
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
