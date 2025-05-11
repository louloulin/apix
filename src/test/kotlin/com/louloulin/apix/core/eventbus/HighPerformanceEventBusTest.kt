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
 * Tests for HighPerformanceEventBus.
 */
@ExtendWith(VertxExtension::class)
class HighPerformanceEventBusTest {
    
    private lateinit var vertx: Vertx
    private lateinit var highPerformanceEventBus: HighPerformanceEventBus
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // Get the HighPerformanceEventBus instance
        highPerformanceEventBus = HighPerformanceEventBus.getInstance(vertx)
        
        // Start the HighPerformanceEventBus
        highPerformanceEventBus.start()
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // Stop the HighPerformanceEventBus
        highPerformanceEventBus.stop()
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
        highPerformanceEventBus.send<String>("test.address", "Hello, World!")
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
        highPerformanceEventBus.publish("test.publish", "Broadcast")
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test local handlers`(testContext: VertxTestContext) {
        // Counter for received messages
        val counter = java.util.concurrent.atomic.AtomicInteger(0)
        
        // Register local handlers
        val registrationId1 = highPerformanceEventBus.registerLocalHandler("test.local") { message ->
            testContext.verify {
                assertEquals("Local message", message)
                if (counter.incrementAndGet() == 2) {
                    testContext.completeNow()
                }
            }
        }
        
        val registrationId2 = highPerformanceEventBus.registerLocalHandler("test.local") { message ->
            testContext.verify {
                assertEquals("Local message", message)
                if (counter.incrementAndGet() == 2) {
                    testContext.completeNow()
                }
            }
        }
        
        // Send a message
        highPerformanceEventBus.send<String>("test.local", "Local message")
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
        
        // Unregister handlers
        assertTrue(highPerformanceEventBus.unregisterLocalHandler(registrationId1))
        assertTrue(highPerformanceEventBus.unregisterLocalHandler(registrationId2))
    }
    
    @Test
    fun `test get stats`(testContext: VertxTestContext) {
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
    }
}
