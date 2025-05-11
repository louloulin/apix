package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DistributedEventBus.
 */
@ExtendWith(VertxExtension::class)
class DistributedEventBusTest {
    
    private lateinit var vertx: Vertx
    private lateinit var distributedEventBus: DistributedEventBus
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // Get the DistributedEventBus instance
        distributedEventBus = DistributedEventBus.getInstance(vertx)
        
        // Start the DistributedEventBus
        distributedEventBus.start()
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // Stop the DistributedEventBus
        distributedEventBus.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test send and receive message`(testContext: VertxTestContext) {
        // Register a consumer
        vertx.eventBus().consumer<String>("test.address") { message ->
            testContext.verify {
                assertEquals("Hello, World!", message.body())
                message.reply("Reply")
            }
        }
        
        // Send a message
        distributedEventBus.send<String>("test.address", "Hello, World!")
            .onSuccess { reply ->
                testContext.verify {
                    assertEquals("Reply", reply.body())
                    testContext.completeNow()
                }
            }
            .onFailure(testContext::failNow)
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test publish message`(testContext: VertxTestContext) {
        // Counter for received messages
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        
        // Register multiple consumers
        vertx.eventBus().consumer<String>("test.publish") { message ->
            testContext.verify {
                assertEquals("Broadcast", message.body())
                if (counter.incrementAndGet() == 2) {
                    testContext.completeNow()
                }
            }
        }
        
        vertx.eventBus().consumer<String>("test.publish") { message ->
            testContext.verify {
                assertEquals("Broadcast", message.body())
                if (counter.incrementAndGet() == 2) {
                    testContext.completeNow()
                }
            }
        }
        
        // Publish a message
        distributedEventBus.publish("test.publish", "Broadcast")
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test message compression`(testContext: VertxTestContext) {
        // Create a large message
        val largeMessage = StringBuilder()
        for (i in 0 until 2000) {
            largeMessage.append("This is a large message that should be compressed. ")
        }
        
        // Register a consumer
        vertx.eventBus().consumer<String>("test.compression") { message ->
            testContext.verify {
                assertEquals(largeMessage.toString(), message.body())
                message.reply("Received compressed message")
            }
        }
        
        // Send the large message
        distributedEventBus.send<String>("test.compression", largeMessage.toString())
            .onSuccess { reply ->
                testContext.verify {
                    assertEquals("Received compressed message", reply.body())
                    
                    // Check stats to verify compression was used
                    val stats = distributedEventBus.getStats()
                    assertTrue(stats.getLong("messagesCompressed") > 0)
                    
                    testContext.completeNow()
                }
            }
            .onFailure(testContext::failNow)
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get stats`(testContext: VertxTestContext) {
        // Send a few messages
        for (i in 0 until 5) {
            distributedEventBus.publish("test.stats", "Message $i")
        }
        
        // Get stats
        val stats = distributedEventBus.getStats()
        
        testContext.verify {
            assertNotNull(stats)
            assertTrue(stats.getBoolean("started"))
            assertTrue(stats.getLong("messagesSent") >= 5)
            
            testContext.completeNow()
        }
    }
}
