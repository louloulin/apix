package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JCToolsEventBus测试类
 */
@ExtendWith(VertxExtension::class)
class JCToolsEventBusTest {
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
     * 测试基本的消息发送和接收
     */
    @Test
    fun testBasicSendReceive(testContext: VertxTestContext) {
        val address = "test.basic"
        val message = JsonObject().put("value", "hello")
        
        // 注册消费者
        jcToolsEventBus.consumer<JsonObject>(address) { msg ->
            testContext.verify {
                assertEquals("hello", msg.body().getString("value"))
                testContext.completeNow()
            }
        }
        
        // 发送消息
        jcToolsEventBus.send(address, message)
    }
    
    /**
     * 测试请求-响应模式
     */
    @Test
    fun testRequestResponse(testContext: VertxTestContext) {
        val address = "test.request"
        val message = JsonObject().put("value", "hello")
        
        // 注册消费者
        jcToolsEventBus.consumer<JsonObject>(address) { msg ->
            // 回复消息
            msg.reply(JsonObject().put("response", "world"))
        }
        
        // 发送请求
        jcToolsEventBus.request<JsonObject>(address, message)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    assertEquals("world", ar.result().body().getString("response"))
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试高优先级消息
     */
    @Test
    fun testHighPriorityMessage(testContext: VertxTestContext) {
        val address = "test.priority"
        val normalCount = AtomicInteger(0)
        val highPriorityCount = AtomicInteger(0)
        val latch = CountDownLatch(1100) // 等待1100条消息处理完成
        
        // 注册消费者
        jcToolsEventBus.consumer<JsonObject>(address) { msg ->
            val priority = msg.body().getString("priority")
            if (priority == "high") {
                highPriorityCount.incrementAndGet()
            } else {
                normalCount.incrementAndGet()
            }
            latch.countDown()
        }
        
        // 发送1000条普通消息
        for (i in 1..1000) {
            jcToolsEventBus.send(address, JsonObject().put("priority", "normal").put("index", i))
        }
        
        // 发送100条高优先级消息
        for (i in 1..100) {
            val options = io.vertx.core.eventbus.DeliveryOptions()
                .addHeader("priority", "high")
            jcToolsEventBus.send(address, JsonObject().put("priority", "high").put("index", i), options)
        }
        
        // 等待所有消息处理完成
        assertTrue(latch.await(10, TimeUnit.SECONDS))
        
        // 验证结果
        testContext.verify {
            assertEquals(1000, normalCount.get())
            assertEquals(100, highPriorityCount.get())
            testContext.completeNow()
        }
    }
    
    /**
     * 测试EventBus类型切换
     */
    @Test
    fun testEventBusTypeSwitch(testContext: VertxTestContext) {
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
                    assertTrue(ar.result())
                    assertEquals(EventBusManager.EventBusType.VERTX, eventBusManager.getCurrentType())
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试性能比较
     */
    @Test
    fun testPerformanceComparison(testContext: VertxTestContext) {
        val messageCount = 100000
        val address = "test.performance"
        val latch = CountDownLatch(messageCount * 2) // 等待所有消息处理完成
        
        // 注册消费者（原生EventBus）
        vertx.eventBus().consumer<JsonObject>(address + ".vertx") { msg ->
            latch.countDown()
        }
        
        // 注册消费者（JCToolsEventBus）
        jcToolsEventBus.consumer<JsonObject>(address + ".jctools") { msg ->
            latch.countDown()
        }
        
        // 测试原生EventBus性能
        val vertxStartTime = System.currentTimeMillis()
        for (i in 1..messageCount) {
            vertx.eventBus().send(address + ".vertx", JsonObject().put("index", i))
        }
        val vertxEndTime = System.currentTimeMillis()
        val vertxTime = vertxEndTime - vertxStartTime
        
        // 测试JCToolsEventBus性能
        val jcToolsStartTime = System.currentTimeMillis()
        for (i in 1..messageCount) {
            jcToolsEventBus.send(address + ".jctools", JsonObject().put("index", i))
        }
        val jcToolsEndTime = System.currentTimeMillis()
        val jcToolsTime = jcToolsEndTime - jcToolsStartTime
        
        // 等待所有消息处理完成
        assertTrue(latch.await(30, TimeUnit.SECONDS))
        
        // 输出性能比较结果
        println("Performance comparison:")
        println("- Vert.x EventBus: $vertxTime ms for $messageCount messages (${messageCount * 1000 / vertxTime} msgs/sec)")
        println("- JCTools EventBus: $jcToolsTime ms for $messageCount messages (${messageCount * 1000 / jcToolsTime} msgs/sec)")
        println("- Improvement: ${(vertxTime - jcToolsTime) * 100.0 / vertxTime}%")
        
        testContext.completeNow()
    }
}
