package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 简单的EventBus测试类
 */
@ExtendWith(VertxExtension::class)
class SimpleEventBusTest {
    private val logger = LoggerFactory.getLogger(SimpleEventBusTest::class.java)

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

        logger.info("JCToolsEventBus统计信息: {}", stats.encode())

        testContext.verify {
            assertNotNull(stats)
            assertTrue(stats.getBoolean("started"))

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

        logger.info("EventBusManager统计信息: {}", stats.encode())

        testContext.verify {
            assertNotNull(stats)
            // 类型可能是VERTX或JCTOOLS，取决于其他测试的运行情况
            assertTrue(stats.getString("type") in listOf(EventBusManager.EventBusType.VERTX.name, EventBusManager.EventBusType.JCTOOLS.name))

            testContext.completeNow()
        }
    }

    /**
     * 测试原生EventBus发送消息
     */
    @Test
    fun testNativeEventBusSend(testContext: VertxTestContext) {
        // 注册消费者
        vertx.eventBus().consumer<JsonObject>("test.native") { message ->
            testContext.verify {
                assertEquals("native test", message.body().getString("value"))
                testContext.completeNow()
            }
        }

        // 发送消息
        vertx.eventBus().send("test.native", JsonObject().put("value", "native test"))
    }

    /**
     * 测试EventBusManager切换类型
     */
    @Test
    fun testEventBusManagerSwitchType(testContext: VertxTestContext) {
        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    assertEquals(EventBusManager.EventBusType.JCTOOLS, eventBusManager.getCurrentType())

                    // 获取统计信息
                    val stats = eventBusManager.getStats()
                    logger.info("切换后的统计信息: {}", stats.encode())

                    testContext.completeNow()
                }
            }
    }
}
