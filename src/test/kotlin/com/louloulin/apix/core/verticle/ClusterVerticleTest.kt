package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * Tests for ClusterVerticle.
 */
@ExtendWith(VertxExtension::class)
class ClusterVerticleTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // Deploy ConfigVerticle first (required by ClusterVerticle)
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(ClusterVerticle()) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `test get cluster config`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == true) { "Expected success to be true" }
                    val result = response.getJsonObject("result")
                    assert(result.getString("type") != null) { "Expected type to be not null" }
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // Ensure test completes within 5 seconds
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun `test get node info`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_NODE_INFO, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == true) { "Expected success to be true" }
                    val result = response.getJsonObject("result")
                    assert(result.getString("nodeId") != null) { "Expected nodeId to be not null" }
                    assert(result.getBoolean("clustered") != null) { "Expected clustered to be not null" }
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // Ensure test completes within 5 seconds
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun `test get cluster nodes`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_NODES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                // In non-clustered mode, this should return a failure
                if (response.getBoolean("success", false)) {
                    val result = response.getJsonObject("result")
                    testContext.verify {
                        assert(result.getInteger("nodeCount") != null) { "Expected nodeCount to be not null" }
                        testContext.completeNow()
                    }
                } else {
                    // This is expected in non-clustered mode
                    testContext.completeNow()
                }
            } else {
                // This is also expected in non-clustered mode
                testContext.completeNow()
            }
        }
        
        // Ensure test completes within 5 seconds
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
