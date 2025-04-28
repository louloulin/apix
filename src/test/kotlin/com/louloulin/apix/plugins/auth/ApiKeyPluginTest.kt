package com.louloulin.apix.plugins.auth

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.http.HttpHeaders
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import io.vertx.ext.web.impl.RoutingContextImpl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.MultiMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.contains

@ExtendWith(VertxExtension::class)
class ApiKeyPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: ApiKeyPlugin

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()

        // Create plugin config with test API keys
        val configJson = JsonObject()
            .put("header", "X-API-Key")
            .put("keys", JsonArray().add("valid-key"))

        val config = PluginConfig("test-api-key", "api-key", configJson)
        plugin = ApiKeyPlugin("test-api-key", config)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `should allow request with valid API key`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)

        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.getHeader("X-API-Key")).thenReturn("valid-key")

        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was not ended
                verify(response, never()).end()
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should reject request with invalid API key`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)

        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.getHeader("X-API-Key")).thenReturn("invalid-key")
        `when`(response.setStatusCode(anyInt())).thenReturn(response)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)

        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was ended with 403
                verify(response).setStatusCode(403)
                verify(response).putHeader("Content-Type", "application/json")
                verify(response).end(contains("Invalid API key"))
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should reject request with missing API key`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)

        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.getHeader("X-API-Key")).thenReturn(null)
        `when`(response.setStatusCode(anyInt())).thenReturn(response)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)

        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was ended with 401
                verify(response).setStatusCode(401)
                verify(response).putHeader("Content-Type", "application/json")
                verify(response).end(contains("API key is missing"))
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
}
