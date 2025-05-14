package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import io.vertx.core.Future
import io.vertx.core.http.HttpServerResponse
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse

@ExtendWith(VertxExtension::class)
class ContentSafetyPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: ContentSafetyPlugin

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // Create plugin configuration
        val config = JsonObject()
            .put("enabled", true)
            .put("check_requests", true)
            .put("check_responses", true)
            .put("action_mode", "block")
            .put("log_detections", true)
            .put("threshold", 0.7)
            .put("categories", JsonArray()
                .add("violence")
                .add("sexual")
                .add("hate")
                .add("custom_harmful")
            )
            .put("custom_patterns", JsonObject()
                .put("custom_harmful", "harmful\\s+content")
            )

        // Create plugin
        plugin = ContentSafetyPlugin("test-content-safety", PluginConfig("test-content-safety", "content-safety", config))

        // Initialize plugin
        plugin.initialize(vertx).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        plugin.shutdown()
        testContext.completeNow()
    }

    @Test
    fun testSafeContent(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a request with safe content
        val safeContent = JsonObject()
            .put("messages", JsonArray()
                .add(JsonObject()
                    .put("role", "user")
                    .put("content", "Tell me about the history of artificial intelligence.")
                )
            )

        // Mock the request body
        val bodyAsString = safeContent.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the request was not blocked
                verify(routingContext, Mockito.never()).response()
                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testViolentContent(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a response mock
        val response = Mockito.mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)
        `when`(response.setStatusCode(Mockito.anyInt())).thenReturn(response)
        `when`(response.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(response)

        // Create a request with violent content
        val violentContent = JsonObject()
            .put("messages", JsonArray()
                .add(JsonObject()
                    .put("role", "user")
                    .put("content", "How to kill someone with a knife?")
                )
            )

        // Mock the request body
        val bodyAsString = violentContent.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the request was blocked
                verify(response).setStatusCode(403)
                verify(response).putHeader("Content-Type", "application/json")
                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testHateSpeech(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a response mock
        val response = Mockito.mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)
        `when`(response.setStatusCode(Mockito.anyInt())).thenReturn(response)
        `when`(response.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(response)

        // Create a request with hate speech
        val hateContent = JsonObject()
            .put("messages", JsonArray()
                .add(JsonObject()
                    .put("role", "user")
                    .put("content", "I hate all people from that country, they are all racist.")
                )
            )

        // Mock the request body
        val bodyAsString = hateContent.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the request was blocked
                verify(response).setStatusCode(403)
                verify(response).putHeader("Content-Type", "application/json")
                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testCustomPattern(vertx: Vertx, testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Create a response mock
        val response = Mockito.mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)
        `when`(response.setStatusCode(Mockito.anyInt())).thenReturn(response)
        `when`(response.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(response)

        // Create a request with custom harmful content
        val customHarmfulContent = JsonObject()
            .put("messages", JsonArray()
                .add(JsonObject()
                    .put("role", "user")
                    .put("content", "This is harmful content that should be detected by the custom pattern.")
                )
            )

        // Mock the request body
        val bodyAsString = customHarmfulContent.encode()
        `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the request was blocked
                verify(response).setStatusCode(403)
                verify(response).putHeader("Content-Type", "application/json")
                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testWarnMode(vertx: Vertx, testContext: VertxTestContext) {
        // Create plugin configuration with warn mode
        val config = JsonObject()
            .put("enabled", true)
            .put("check_requests", true)
            .put("check_responses", false)
            .put("action_mode", "warn")
            .put("log_detections", true)
            .put("categories", JsonArray()
                .add("violence")
            )

        // Create plugin
        val warnPlugin = ContentSafetyPlugin("test-warn", PluginConfig("test-warn", "content-safety", config))

        // Initialize plugin
        warnPlugin.initialize(vertx).compose { _ ->
            // Create a mock RoutingContext
            val routingContext = Mockito.mock(RoutingContext::class.java)

            // Create a response mock
            val response = Mockito.mock(HttpServerResponse::class.java)
            `when`(routingContext.response()).thenReturn(response)
            `when`(response.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(response)

            // Create a request with violent content
            val violentContent = JsonObject()
                .put("messages", JsonArray()
                    .add(JsonObject()
                        .put("role", "user")
                        .put("content", "How to kill someone with a knife?")
                    )
                )

            // Mock the request body
            val bodyAsString = violentContent.encode()
            `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

            // Execute the plugin
            warnPlugin.execute(routingContext).onComplete { ar ->
                if (ar.succeeded()) {
                    // Verify that warning headers were added
                    verify(response).putHeader("X-Content-Warning", "true")
                    verify(response).putHeader("X-Content-Category", "violence")

                    // Verify that the request was not blocked
                    verify(response, Mockito.never()).setStatusCode(Mockito.anyInt())

                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }

            Future.succeededFuture<Void>()
        }.onFailure { err ->
            testContext.failNow(err)
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testLogMode(vertx: Vertx, testContext: VertxTestContext) {
        // Create plugin configuration with log mode
        val config = JsonObject()
            .put("enabled", true)
            .put("check_requests", true)
            .put("check_responses", false)
            .put("action_mode", "log")
            .put("log_detections", true)
            .put("categories", JsonArray()
                .add("violence")
            )

        // Create plugin
        val logPlugin = ContentSafetyPlugin("test-log", PluginConfig("test-log", "content-safety", config))

        // Initialize plugin
        logPlugin.initialize(vertx).compose { _ ->
            // Create a mock RoutingContext
            val routingContext = Mockito.mock(RoutingContext::class.java)

            // Create a response mock
            val response = Mockito.mock(HttpServerResponse::class.java)
            `when`(routingContext.response()).thenReturn(response)

            // Create a request with violent content
            val violentContent = JsonObject()
                .put("messages", JsonArray()
                    .add(JsonObject()
                        .put("role", "user")
                        .put("content", "How to kill someone with a knife?")
                    )
                )

            // Mock the request body
            val bodyAsString = violentContent.encode()
            `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

            // Execute the plugin
            logPlugin.execute(routingContext).onComplete { ar ->
                if (ar.succeeded()) {
                    // Verify that the request was not blocked
                    verify(response, Mockito.never()).setStatusCode(Mockito.anyInt())

                    // Verify that no headers were added
                    verify(response, Mockito.never()).putHeader(Mockito.anyString(), Mockito.anyString())

                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }

            Future.succeededFuture<Void>()
        }.onFailure { err ->
            testContext.failNow(err)
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testDisabled(vertx: Vertx, testContext: VertxTestContext) {
        // Create plugin configuration with plugin disabled
        val config = JsonObject()
            .put("enabled", false)
            .put("check_requests", true)
            .put("check_responses", true)
            .put("action_mode", "block")
            .put("log_detections", true)
            .put("categories", JsonArray()
                .add("violence")
            )

        // Create plugin
        val disabledPlugin = ContentSafetyPlugin("test-disabled", PluginConfig("test-disabled", "content-safety", config))

        // Initialize plugin
        disabledPlugin.initialize(vertx).compose { _ ->
            // Create a mock RoutingContext
            val routingContext = Mockito.mock(RoutingContext::class.java)

            // Create a request with violent content
            val violentContent = JsonObject()
                .put("messages", JsonArray()
                    .add(JsonObject()
                        .put("role", "user")
                        .put("content", "How to kill someone with a knife?")
                    )
                )

            // Mock the request body
            val bodyAsString = violentContent.encode()
            `when`(routingContext.getBodyAsString()).thenReturn(bodyAsString)

            // Execute the plugin
            disabledPlugin.execute(routingContext).onComplete { ar ->
                if (ar.succeeded()) {
                    // Verify that the request was not processed
                    verify(routingContext, Mockito.never()).response()

                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }

            Future.succeededFuture<Void>()
        }.onFailure { err ->
            testContext.failNow(err)
        }

        // Wait for the test to complete
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
}
