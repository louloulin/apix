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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 基本EventBus测试类
 */
@ExtendWith(VertxExtension::class)
class BasicEventBusTest {
    private val logger = LoggerFactory.getLogger(BasicEventBusTest::class.java)

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
     * 测试EventBusManager发送消息
     */
    @Test
    fun testEventBusManagerSend(testContext: VertxTestContext) {
        // 注册消费者
        vertx.eventBus().consumer<JsonObject>("test.manager") { message ->
            testContext.verify {
                assertEquals("manager test", message.body().getString("value"))
                testContext.completeNow()
            }
        }

        // 发送消息
        vertx.eventBus().send("test.manager", JsonObject().put("value", "manager test"))
    }

    /**
     * 测试EventBusManager切换类型
     */
    @Test
    fun testEventBusManagerSwitchType(testContext: VertxTestContext) {
        // 获取当前类型
        val initialType = eventBusManager.getCurrentType()

        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .compose { success ->
                testContext.verify {
                    assertTrue(success)
                    assertEquals(EventBusManager.EventBusType.JCTOOLS, eventBusManager.getCurrentType())
                }

                // 切换回原生EventBus
                eventBusManager.switchType(EventBusManager.EventBusType.VERTX)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    assertEquals(EventBusManager.EventBusType.VERTX, eventBusManager.getCurrentType())
                    testContext.completeNow()
                }
            }
    }
}
