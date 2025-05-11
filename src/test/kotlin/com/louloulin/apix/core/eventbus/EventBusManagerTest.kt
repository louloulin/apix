package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
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
 * Tests for EventBusManager.
 */
@ExtendWith(VertxExtension::class)
class EventBusManagerTest {
    
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        testContext.completeNow()
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun testEventBusManagerSwitchType(testContext: VertxTestContext) {
        val eventBusManager = EventBusManager.getInstance(vertx)
        
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
                if (ar.succeeded()) {
                    // Verify type is VERTX
                    testContext.verify {
                        assertEquals(EventBusManager.EventBusType.VERTX, eventBusManager.getCurrentType())
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun testEventBusManagerSendAndPublish(testContext: VertxTestContext) {
        val eventBusManager = EventBusManager.getInstance(vertx)
        
        // Register a consumer
        vertx.eventBus().consumer<String>("test.address") { message ->
            testContext.verify {
                assertEquals("Hello, World!", message.body())
                testContext.completeNow()
            }
        }
        
        // Send a message
        eventBusManager.send("test.address", "Hello, World!")
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun testEventBusManagerGetStats(testContext: VertxTestContext) {
        val eventBusManager = EventBusManager.getInstance(vertx)
        
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
    }
}
