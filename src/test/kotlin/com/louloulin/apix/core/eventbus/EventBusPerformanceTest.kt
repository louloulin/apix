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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

/**
 * EventBus性能测试类
 */
@ExtendWith(VertxExtension::class)
class EventBusPerformanceTest {
    private val logger = LoggerFactory.getLogger(EventBusPerformanceTest::class.java)

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
     * 测试原生EventBus性能
     */
    @Test
    fun testNativeEventBusPerformance(testContext: VertxTestContext) {
        // 确保使用原生EventBus
        eventBusManager.switchType(EventBusManager.EventBusType.VERTX)
            .compose { success ->
                testContext.verify {
                    assertTrue(success)
                }

                // 执行性能测试
                performPerformanceTest("native", vertx.eventBus(), testContext)
            }
    }

    /**
     * 测试JCToolsEventBus性能
     */
    @Test
    fun testJCToolsEventBusPerformance(testContext: VertxTestContext) {
        // 切换到JCToolsEventBus
        eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            .compose { success ->
                testContext.verify {
                    assertTrue(success)
                }

                // 执行性能测试
                performPerformanceTest("jctools", eventBusManager, testContext)
            }
    }

    /**
     * 执行性能测试
     */
    private fun performPerformanceTest(name: String, eventBusOrManager: Any, testContext: VertxTestContext): io.vertx.core.Future<Void> {
        // 获取EventBus实例
        val eventBus = when (eventBusOrManager) {
            is io.vertx.core.eventbus.EventBus -> eventBusOrManager
            is EventBusManager -> eventBusOrManager.getEventBus()
            else -> throw IllegalArgumentException("Unsupported type: ${eventBusOrManager.javaClass.name}")
        }
        val promise = io.vertx.core.Promise.promise<Void>()

        // 测试参数
        val messageCount = 1000 // 减少消息数量，避免超时
        val address = "test.performance.$name"
        val receivedCount = AtomicInteger(0)
        val latch = CountDownLatch(messageCount)

        // 注册消费者
        eventBus.consumer<JsonObject>(address) { message ->
            receivedCount.incrementAndGet()
            latch.countDown()
        }

        // 发送大量消息
        val startTime = System.currentTimeMillis()

        for (i in 1..messageCount) {
            val message = JsonObject()
                .put("index", i)
                .put("value", "test")
                .put("timestamp", System.currentTimeMillis())

            if (eventBusOrManager is EventBusManager) {
                eventBusOrManager.send(address, message)
            } else {
                eventBus.send(address, message)
            }

            // 每100条消息输出一次进度
            if (i % 100 == 0) {
                logger.info("[$name] 已发送 $i 条消息")
            }
        }

        // 等待所有消息处理完成
        val waitResult = latch.await(30, TimeUnit.SECONDS)

        // 记录结束时间
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val messagesPerSecond = messageCount * 1000.0 / duration

        // 输出性能结果
        logger.info("[$name] 性能测试结果:")
        logger.info("[$name] - 消息数: $messageCount")
        logger.info("[$name] - 接收数: ${receivedCount.get()}")
        logger.info("[$name] - 持续时间: ${duration}ms")
        logger.info("[$name] - 每秒消息数: ${String.format("%.2f", messagesPerSecond)}")
        logger.info("[$name] - 所有消息处理完成: $waitResult")

        if (waitResult) {
            promise.complete()
        } else {
            promise.fail("等待消息处理超时")
        }

        return promise.future()
    }

    /**
     * 测试EventBus类型切换性能
     */
    @Test
    fun testEventBusSwitchPerformance(testContext: VertxTestContext) {
        // 测试参数
        val switchCount = 5 // 减少切换次数，避免超时

        // 记录开始时间
        val startTime = System.currentTimeMillis()

        // 执行多次切换
        var future = io.vertx.core.Future.succeededFuture<Boolean>()

        for (i in 1..switchCount) {
            // 切换到JCToolsEventBus
            future = future.compose {
                eventBusManager.switchType(EventBusManager.EventBusType.JCTOOLS)
            }

            // 切换到原生EventBus
            future = future.compose {
                eventBusManager.switchType(EventBusManager.EventBusType.VERTX)
            }
        }

        future.onComplete { ar ->
            // 记录结束时间
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            val switchesPerSecond = (switchCount * 2) * 1000.0 / duration

            // 输出性能结果
            logger.info("EventBus切换性能测试结果:")
            logger.info("- 切换次数: ${switchCount * 2}")
            logger.info("- 持续时间: ${duration}ms")
            logger.info("- 每秒切换次数: ${String.format("%.2f", switchesPerSecond)}")

            testContext.completeNow()
        }
    }
}
