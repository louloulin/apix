package com.louloulin.apix.core.verticle

import com.louloulin.apix.cluster.ClusterConfig
import com.louloulin.apix.cluster.ClusterManagerFactory
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * Tests for cluster communication.
 * 
 * Note: This test requires running with -Dtest.cluster=true system property.
 */
@ExtendWith(VertxExtension::class)
@EnabledIfSystemProperty(named = "test.cluster", matches = "true")
class ClusterCommunicationTest {
    private lateinit var vertx1: Vertx
    private lateinit var vertx2: Vertx
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        // Create cluster configuration
        val config = JsonObject()
            .put("cluster", JsonObject()
                .put("type", "HAZELCAST")
            )
        
        val clusterConfig = ClusterConfig(config)
        val clusterManager = ClusterManagerFactory.createClusterManager(clusterConfig)
        
        if (clusterManager == null) {
            testContext.failNow(IllegalStateException("Failed to create cluster manager"))
            return
        }
        
        // Create first clustered Vertx instance
        val options1 = VertxOptions()
            .setClusterManager(clusterManager)
        
        Vertx.clusteredVertx(options1)
            .compose { v1 ->
                vertx1 = v1
                
                // Create second clustered Vertx instance
                val options2 = VertxOptions()
                    .setClusterManager(ClusterManagerFactory.createClusterManager(clusterConfig))
                
                Vertx.clusteredVertx(options2)
            }
            .onSuccess { v2 ->
                vertx2 = v2
                testContext.completeNow()
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx1.close()
            .compose { vertx2.close() }
            .onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `test event bus communication between cluster nodes`(testContext: VertxTestContext) {
        // Set up a consumer on the first node
        vertx1.eventBus().consumer<String>("test.address") { message ->
            testContext.verify {
                assert(message.body() == "Hello from node 2") { "Unexpected message: ${message.body()}" }
                message.reply("Hello from node 1")
            }
        }
        
        // Wait a bit for the consumer to be registered across the cluster
        vertx2.setTimer(1000) { _ ->
            // Send a message from the second node
            vertx2.eventBus().request<String>("test.address", "Hello from node 2") { ar ->
                if (ar.succeeded()) {
                    testContext.verify {
                        assert(ar.result().body() == "Hello from node 1") { "Unexpected reply: ${ar.result().body()}" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        }
        
        // Ensure test completes within 10 seconds
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun `test shared data between cluster nodes`(testContext: VertxTestContext) {
        // Get a distributed map on the first node
        vertx1.sharedData().getAsyncMap<String, String>("test-map") { ar1 ->
            if (ar1.succeeded()) {
                val map1 = ar1.result()
                
                // Put a value in the map
                map1.put("key1", "value1") { putAr ->
                    if (putAr.succeeded()) {
                        // Get the map on the second node
                        vertx2.sharedData().getAsyncMap<String, String>("test-map") { ar2 ->
                            if (ar2.succeeded()) {
                                val map2 = ar2.result()
                                
                                // Get the value from the second node
                                map2.get("key1") { getAr ->
                                    if (getAr.succeeded()) {
                                        testContext.verify {
                                            assert(getAr.result() == "value1") { "Unexpected value: ${getAr.result()}" }
                                            testContext.completeNow()
                                        }
                                    } else {
                                        testContext.failNow(getAr.cause())
                                    }
                                }
                            } else {
                                testContext.failNow(ar2.cause())
                            }
                        }
                    } else {
                        testContext.failNow(putAr.cause())
                    }
                }
            } else {
                testContext.failNow(ar1.cause())
            }
        }
        
        // Ensure test completes within 10 seconds
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
