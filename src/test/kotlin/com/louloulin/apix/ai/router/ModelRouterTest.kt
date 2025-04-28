package com.louloulin.apix.ai.router

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for ModelRouter.
 */
@ExtendWith(VertxExtension::class)
class ModelRouterTest {
    private lateinit var vertx: Vertx
    private lateinit var modelRouter: ModelRouter
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        modelRouter = ModelRouter(vertx)
        
        // Initialize with empty config
        modelRouter.initialize(JsonObject())
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
    fun `test initialize with rules`(testContext: VertxTestContext) {
        // Create config with rules
        val rule1 = JsonObject()
            .put("name", "Test Rule 1")
            .put("priority", 1)
            .put("condition", JsonObject()
                .put("type", "CONTAINS")
                .put("pattern", "test")
            )
            .put("targetModel", "gpt-4")
        
        val rule2 = JsonObject()
            .put("name", "Test Rule 2")
            .put("priority", 2)
            .put("condition", JsonObject()
                .put("type", "STARTS_WITH")
                .put("pattern", "hello")
            )
            .put("targetModel", "gpt-3.5-turbo")
        
        val rules = JsonArray().add(rule1).add(rule2)
        val config = JsonObject().put("rules", rules)
        
        // Initialize with config
        modelRouter.initialize(config)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.verify {
                        val loadedRules = modelRouter.getRules()
                        assertEquals(2, loadedRules.size)
                        assertEquals("Test Rule 1", loadedRules[0].name)
                        assertEquals("Test Rule 2", loadedRules[1].name)
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test add and remove rule`(testContext: VertxTestContext) {
        // Create a rule
        val condition = RuleCondition(ConditionType.CONTAINS, "test")
        val rule = RoutingRule(UUID.randomUUID().toString(), "Test Rule", 1, condition, "gpt-4")
        
        // Add rule
        modelRouter.addRule(rule)
            .compose { _ ->
                // Verify rule was added
                val rules = modelRouter.getRules()
                testContext.verify {
                    assertEquals(1, rules.size)
                    assertEquals("Test Rule", rules[0].name)
                }
                
                // Remove rule
                modelRouter.removeRule(rule.id)
            }
            .compose { removed ->
                testContext.verify {
                    assertTrue(removed)
                    assertEquals(0, modelRouter.getRules().size)
                }
                
                // Try to remove non-existent rule
                modelRouter.removeRule("non-existent-id")
            }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.verify {
                        assertNotNull(ar.result())
                        assertNotNull(!ar.result())
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test route request with no rules`(testContext: VertxTestContext) {
        // Create request
        val request = JsonObject()
            .put("content", "This is a test request")
            .put("contentType", "text")
            .put("requestType", "completion")
            .put("defaultModel", "gpt-3.5-turbo")
        
        // Route request
        modelRouter.routeRequest(request)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.verify {
                        assertEquals("gpt-3.5-turbo", ar.result())
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test route request with matching rule`(testContext: VertxTestContext) {
        // Add rules
        val condition1 = RuleCondition(ConditionType.CONTAINS, "test")
        val rule1 = RoutingRule(UUID.randomUUID().toString(), "Test Rule", 1, condition1, "gpt-4")
        
        val condition2 = RuleCondition(ConditionType.STARTS_WITH, "hello")
        val rule2 = RoutingRule(UUID.randomUUID().toString(), "Hello Rule", 2, condition2, "gpt-3.5-turbo-16k")
        
        modelRouter.addRule(rule1)
            .compose { _ -> modelRouter.addRule(rule2) }
            .compose { _ ->
                // Create request that matches rule1
                val request1 = JsonObject()
                    .put("content", "This is a test request")
                    .put("contentType", "text")
                    .put("requestType", "completion")
                    .put("defaultModel", "gpt-3.5-turbo")
                
                // Route request
                modelRouter.routeRequest(request1)
            }
            .compose { model ->
                testContext.verify {
                    assertEquals("gpt-4", model)
                }
                
                // Create request that matches rule2
                val request2 = JsonObject()
                    .put("content", "hello world")
                    .put("contentType", "text")
                    .put("requestType", "completion")
                    .put("defaultModel", "gpt-3.5-turbo")
                
                // Route request
                modelRouter.routeRequest(request2)
            }
            .compose { model ->
                testContext.verify {
                    assertEquals("gpt-3.5-turbo-16k", model)
                }
                
                // Create request that doesn't match any rule
                val request3 = JsonObject()
                    .put("content", "no match")
                    .put("contentType", "text")
                    .put("requestType", "completion")
                    .put("defaultModel", "gpt-3.5-turbo")
                
                // Route request
                modelRouter.routeRequest(request3)
            }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.verify {
                        assertEquals("gpt-3.5-turbo", ar.result())
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test clear rules`(testContext: VertxTestContext) {
        // Add rules
        val condition1 = RuleCondition(ConditionType.CONTAINS, "test")
        val rule1 = RoutingRule(UUID.randomUUID().toString(), "Test Rule", 1, condition1, "gpt-4")
        
        val condition2 = RuleCondition(ConditionType.STARTS_WITH, "hello")
        val rule2 = RoutingRule(UUID.randomUUID().toString(), "Hello Rule", 2, condition2, "gpt-3.5-turbo-16k")
        
        modelRouter.addRule(rule1)
            .compose { _ -> modelRouter.addRule(rule2) }
            .compose { _ ->
                testContext.verify {
                    assertEquals(2, modelRouter.getRules().size)
                }
                
                // Clear rules
                modelRouter.clearRules()
            }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.verify {
                        assertEquals(0, modelRouter.getRules().size)
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
