package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.mode.NodeMode
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
 * Tests for NodeModeVerticle.
 */
@ExtendWith(VertxExtension::class)
class NodeModeVerticleTest {
    
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // Deploy ConfigVerticle first
        vertx.deployVerticle(ConfigVerticle())
            .compose { _ -> 
                // Then deploy NodeModeVerticle
                vertx.deployVerticle(NodeModeVerticle())
            }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get node mode`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.NODE_MODE_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals(NodeMode.STANDALONE.name, result.getString("mode"))
                    assertTrue(result.getBoolean("isStandalone"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get node info`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.NODE_INFO_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals(NodeMode.STANDALONE.name, result.getString("mode"))
                    assertTrue(result.getBoolean("isStandalone"))
                    assertNotNull(result.getBoolean("clustered"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
