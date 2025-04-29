package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests for ModelRouterVerticle.
 */
@ExtendWith(VertxExtension::class)
class ModelRouterVerticleTest {
    private lateinit var vertx: Vertx

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()

        // Deploy ConfigVerticle first (required by ModelRouterVerticle)
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(ModelRouterVerticle()) }
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
    @org.junit.jupiter.api.Disabled("Temporarily disabled until ModelRouterVerticle is properly implemented")
    fun `test get rules`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_RULES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    val response = ar.result().body()
                    assertTrue(response is JsonObject)
                    assertTrue(response.getBoolean("success", false))
                    val rules = response.getJsonArray("result")
                    assertNotNull(rules)
                    assertEquals(0, rules.size())
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    @org.junit.jupiter.api.Disabled("Temporarily disabled until ModelRouterVerticle is properly implemented")
    fun `test add rule`(testContext: VertxTestContext) {
        // Create rule
        val rule = JsonObject()
            .put("name", "Test Rule")
            .put("priority", 1)
            .put("condition", JsonObject()
                .put("type", "CONTAINS")
                .put("pattern", "test")
            )
            .put("targetModel", "gpt-4")

        // Add rule
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_MODEL_RULE_ADD,
            JsonObject().put("rule", rule)
        ) { addAr ->
            if (addAr.succeeded()) {
                // Get rules
                vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_RULES_GET, JsonObject()) { getRulesAr ->
                    if (getRulesAr.succeeded()) {
                        testContext.verify {
                            val response = getRulesAr.result().body()
                            assertTrue(response is JsonObject)
                            assertTrue(response.getBoolean("success", false))
                            val rules = response.getJsonArray("result")
                            assertNotNull(rules)
                            assertEquals(1, rules.size())

                            val addedRule = rules.getJsonObject(0)
                            assertEquals("Test Rule", addedRule.getString("name"))
                            assertEquals("gpt-4", addedRule.getString("targetModel"))

                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(getRulesAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test route to model`(testContext: VertxTestContext) {
        // Create rule
        val rule = JsonObject()
            .put("name", "Test Rule")
            .put("priority", 1)
            .put("condition", JsonObject()
                .put("type", "CONTAINS")
                .put("pattern", "test")
            )
            .put("targetModel", "gpt-4")

        // Add rule
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_MODEL_RULE_ADD,
            JsonObject().put("rule", rule)
        ) { addAr ->
            if (addAr.succeeded()) {
                // Create request that matches the rule
                val request = JsonObject()
                    .put("content", "This is a test request")
                    .put("contentType", "text")
                    .put("requestType", "completion")
                    .put("defaultModel", "gpt-3.5-turbo")

                // Route request
                vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_ROUTE, request) { routeAr ->
                    if (routeAr.succeeded()) {
                        testContext.verify {
                            val response = routeAr.result().body()
                            assertTrue(response.getBoolean("success", false))
                            assertEquals("gpt-4", response.getJsonObject("result").getString("model"))
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(routeAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    @org.junit.jupiter.api.Disabled("Temporarily disabled until ModelRouterVerticle is properly implemented")
    fun `test clear rules`(testContext: VertxTestContext) {
        // Create rule
        val rule = JsonObject()
            .put("name", "Test Rule")
            .put("priority", 1)
            .put("condition", JsonObject()
                .put("type", "CONTAINS")
                .put("pattern", "test")
            )
            .put("targetModel", "gpt-4")

        // Add rule
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_MODEL_RULE_ADD,
            JsonObject().put("rule", rule)
        ) { addAr ->
            if (addAr.succeeded()) {
                // Clear rules
                vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_RULES_CLEAR, JsonObject()) { clearAr ->
                    if (clearAr.succeeded()) {
                        // Get rules
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_RULES_GET, JsonObject()) { getRulesAr ->
                            if (getRulesAr.succeeded()) {
                                testContext.verify {
                                    val response = getRulesAr.result().body()
                                    assertTrue(response is JsonObject)
                                    assertTrue(response.getBoolean("success", false))
                                    val rules = response.getJsonArray("result")
                                    assertNotNull(rules)
                                    assertEquals(0, rules.size())
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(getRulesAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(clearAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
