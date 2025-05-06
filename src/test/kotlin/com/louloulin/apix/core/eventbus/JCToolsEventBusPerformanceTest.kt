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
import kotlin.test.assertTrue

/**
 * JCToolsEventBus性能测试类
 */
@ExtendWith(VertxExtension::class)
class JCToolsEventBusPerformanceTest {
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
     * 测试高并发场景下的性能
     */
    @Test
    fun testHighConcurrencyPerformance(testContext: VertxTestContext) {
        val messageCount = 1000000 // 100万条消息
        val address = "test.high.concurrency"
        val receivedCount = AtomicInteger(0)
        val latch = CountDownLatch(messageCount) // 等待所有消息处理完成
        
        // 注册消费者
        jcToolsEventBus.consumer<JsonObject>(address) { msg ->
            receivedCount.incrementAndGet()
            latch.countDown()
        }
        
        // 发送消息
        val startTime = System.currentTimeMillis()
        for (i in 1..messageCount) {
            jcToolsEventBus.send(address, JsonObject().put("index", i))
            
            // 每10万条消息输出一次进度
            if (i % 100000 == 0) {
                println("Sent $i messages")
            }
        }
        val endTime = System.currentTimeMillis()
        val sendTime = endTime - startTime
        
        // 等待所有消息处理完成
        val waitResult = latch.await(60, TimeUnit.SECONDS)
        val totalTime = System.currentTimeMillis() - startTime
        
        // 输出性能结果
        println("High concurrency performance:")
        println("- Messages sent: $messageCount")
        println("- Messages received: ${receivedCount.get()}")
        println("- Send time: $sendTime ms (${messageCount * 1000 / sendTime} msgs/sec)")
        println("- Total time: $totalTime ms (${messageCount * 1000 / totalTime} msgs/sec)")
        println("- All messages processed: $waitResult")
        
        // 获取JCToolsEventBus统计信息
        val stats = jcToolsEventBus.getStats()
        println("JCToolsEventBus stats: ${stats.encode()}")
        
        testContext.completeNow()
    }
    
    /**
     * 测试多地址并发场景下的性能
     */
    @Test
    fun testMultiAddressConcurrencyPerformance(testContext: VertxTestContext) {
        val addressCount = 100 // 100个不同的地址
        val messageCount = 10000 // 每个地址10000条消息
        val totalMessageCount = addressCount * messageCount
        val receivedCount = AtomicInteger(0)
        val latch = CountDownLatch(totalMessageCount) // 等待所有消息处理完成
        
        // 注册消费者
        for (a in 1..addressCount) {
            val address = "test.multi.address.$a"
            jcToolsEventBus.consumer<JsonObject>(address) { msg ->
                receivedCount.incrementAndGet()
                latch.countDown()
            }
        }
        
        // 发送消息
        val startTime = System.currentTimeMillis()
        for (a in 1..addressCount) {
            val address = "test.multi.address.$a"
            for (i in 1..messageCount) {
                jcToolsEventBus.send(address, JsonObject().put("address", a).put("index", i))
            }
            
            // 每10个地址输出一次进度
            if (a % 10 == 0) {
                println("Sent messages to $a addresses")
            }
        }
        val endTime = System.currentTimeMillis()
        val sendTime = endTime - startTime
        
        // 等待所有消息处理完成
        val waitResult = latch.await(60, TimeUnit.SECONDS)
        val totalTime = System.currentTimeMillis() - startTime
        
        // 输出性能结果
        println("Multi-address concurrency performance:")
        println("- Addresses: $addressCount")
        println("- Messages per address: $messageCount")
        println("- Total messages: $totalMessageCount")
        println("- Messages received: ${receivedCount.get()}")
        println("- Send time: $sendTime ms (${totalMessageCount * 1000 / sendTime} msgs/sec)")
        println("- Total time: $totalTime ms (${totalMessageCount * 1000 / totalTime} msgs/sec)")
        println("- All messages processed: $waitResult")
        
        // 获取JCToolsEventBus统计信息
        val stats = jcToolsEventBus.getStats()
        println("JCToolsEventBus stats: ${stats.encode()}")
        
        testContext.completeNow()
    }
    
    /**
     * 测试请求-响应模式的性能
     */
    @Test
    fun testRequestResponsePerformance(testContext: VertxTestContext) {
        val messageCount = 10000 // 1万条请求
        val address = "test.request.response.performance"
        val latch = CountDownLatch(messageCount) // 等待所有请求处理完成
        
        // 注册消费者
        jcToolsEventBus.consumer<JsonObject>(address) { msg ->
            // 回复消息
            msg.reply(JsonObject().put("response", "success").put("index", msg.body().getInteger("index")))
        }
        
        // 发送请求
        val startTime = System.currentTimeMillis()
        for (i in 1..messageCount) {
            jcToolsEventBus.request<JsonObject>(address, JsonObject().put("index", i))
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        latch.countDown()
                    }
                }
            
            // 每1000条请求输出一次进度
            if (i % 1000 == 0) {
                println("Sent $i requests")
            }
        }
        val endTime = System.currentTimeMillis()
        val sendTime = endTime - startTime
        
        // 等待所有请求处理完成
        val waitResult = latch.await(60, TimeUnit.SECONDS)
        val totalTime = System.currentTimeMillis() - startTime
        
        // 输出性能结果
        println("Request-response performance:")
        println("- Requests sent: $messageCount")
        println("- Send time: $sendTime ms (${messageCount * 1000 / sendTime} reqs/sec)")
        println("- Total time: $totalTime ms (${messageCount * 1000 / totalTime} reqs/sec)")
        println("- All requests processed: $waitResult")
        
        // 获取JCToolsEventBus统计信息
        val stats = jcToolsEventBus.getStats()
        println("JCToolsEventBus stats: ${stats.encode()}")
        
        testContext.completeNow()
    }
    
    /**
     * 测试原生EventBus与JCToolsEventBus的性能对比
     */
    @Test
    fun testPerformanceComparison(testContext: VertxTestContext) {
        val messageCount = 1000000 // 100万条消息
        val address1 = "test.performance.vertx"
        val address2 = "test.performance.jctools"
        val receivedCount1 = AtomicInteger(0)
        val receivedCount2 = AtomicInteger(0)
        val latch1 = CountDownLatch(messageCount) // 等待原生EventBus消息处理完成
        val latch2 = CountDownLatch(messageCount) // 等待JCToolsEventBus消息处理完成
        
        // 注册原生EventBus消费者
        vertx.eventBus().consumer<JsonObject>(address1) { msg ->
            receivedCount1.incrementAndGet()
            latch1.countDown()
        }
        
        // 注册JCToolsEventBus消费者
        jcToolsEventBus.consumer<JsonObject>(address2) { msg ->
            receivedCount2.incrementAndGet()
            latch2.countDown()
        }
        
        // 测试原生EventBus性能
        println("Testing Vert.x EventBus performance...")
        val startTime1 = System.currentTimeMillis()
        for (i in 1..messageCount) {
            vertx.eventBus().send(address1, JsonObject().put("index", i))
            
            // 每10万条消息输出一次进度
            if (i % 100000 == 0) {
                println("Sent $i messages to Vert.x EventBus")
            }
        }
        val endTime1 = System.currentTimeMillis()
        val sendTime1 = endTime1 - startTime1
        
        // 测试JCToolsEventBus性能
        println("Testing JCTools EventBus performance...")
        val startTime2 = System.currentTimeMillis()
        for (i in 1..messageCount) {
            jcToolsEventBus.send(address2, JsonObject().put("index", i))
            
            // 每10万条消息输出一次进度
            if (i % 100000 == 0) {
                println("Sent $i messages to JCTools EventBus")
            }
        }
        val endTime2 = System.currentTimeMillis()
        val sendTime2 = endTime2 - startTime2
        
        // 等待所有消息处理完成
        val waitResult1 = latch1.await(60, TimeUnit.SECONDS)
        val totalTime1 = endTime1 - startTime1 + (if (waitResult1) 0 else System.currentTimeMillis() - endTime1)
        
        val waitResult2 = latch2.await(60, TimeUnit.SECONDS)
        val totalTime2 = endTime2 - startTime2 + (if (waitResult2) 0 else System.currentTimeMillis() - endTime2)
        
        // 输出性能比较结果
        println("Performance comparison:")
        println("- Vert.x EventBus:")
        println("  - Messages sent: $messageCount")
        println("  - Messages received: ${receivedCount1.get()}")
        println("  - Send time: $sendTime1 ms (${messageCount * 1000 / sendTime1} msgs/sec)")
        println("  - Total time: $totalTime1 ms (${messageCount * 1000 / totalTime1} msgs/sec)")
        println("  - All messages processed: $waitResult1")
        
        println("- JCTools EventBus:")
        println("  - Messages sent: $messageCount")
        println("  - Messages received: ${receivedCount2.get()}")
        println("  - Send time: $sendTime2 ms (${messageCount * 1000 / sendTime2} msgs/sec)")
        println("  - Total time: $totalTime2 ms (${messageCount * 1000 / totalTime2} msgs/sec)")
        println("  - All messages processed: $waitResult2")
        
        println("- Improvement:")
        println("  - Send time: ${(sendTime1 - sendTime2) * 100.0 / sendTime1}%")
        println("  - Total time: ${(totalTime1 - totalTime2) * 100.0 / totalTime1}%")
        
        // 获取JCToolsEventBus统计信息
        val stats = jcToolsEventBus.getStats()
        println("JCToolsEventBus stats: ${stats.encode()}")
        
        testContext.completeNow()
    }
}
