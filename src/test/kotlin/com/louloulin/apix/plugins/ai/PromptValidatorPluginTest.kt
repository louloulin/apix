package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import io.vertx.ext.web.RequestBody
import org.mockito.ArgumentMatchers.contains

@ExtendWith(VertxExtension::class)
class PromptValidatorPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: PromptValidatorPlugin
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        
        // Create plugin config
        val configJson = JsonObject()
            .put("max_length", 20) // Small limit for testing
            .put("validate_json", true)
        
        val config = PluginConfig("test-prompt-validator", configJson)
        plugin = PromptValidatorPlugin("test-prompt-validator", config)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `should allow valid JSON with short prompt`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val requestBody = mock(RequestBody::class.java)
        
        // Create a valid OpenAI-style request with a short prompt
        val jsonBody = JsonObject()
            .put("messages", JsonArray()
                .add(JsonObject()
                    .put("role", "user")
                    .put("content", "Hello")
                )
            )
        
        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.body()).thenReturn(requestBody)
        `when`(requestBody.buffer()).thenReturn(Buffer.buffer(jsonBody.encode()))
        
        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was not ended
                verify(response, never()).setStatusCode(anyInt())
                verify(response, never()).end()
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
    
    @Test
    fun `should reject prompt exceeding maximum length`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val requestBody = mock(RequestBody::class.java)
        
        // Create a valid OpenAI-style request with a long prompt
        val jsonBody = JsonObject()
            .put("messages", JsonArray()
                .add(JsonObject()
                    .put("role", "user")
                    .put("content", "This is a very long prompt that exceeds the maximum length")
                )
            )
        
        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.body()).thenReturn(requestBody)
        `when`(requestBody.buffer()).thenReturn(Buffer.buffer(jsonBody.encode()))
        `when`(response.setStatusCode(anyInt())).thenReturn(response)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)
        
        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was ended with 400
                verify(response).setStatusCode(400)
                verify(response).putHeader("Content-Type", "application/json")
                verify(response).end(contains("Prompt exceeds maximum length"))
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
    
    @Test
    fun `should reject invalid JSON`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val requestBody = mock(RequestBody::class.java)
        
        // Create an invalid JSON
        val invalidJson = Buffer.buffer("{invalid json")
        
        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.body()).thenReturn(requestBody)
        `when`(requestBody.buffer()).thenReturn(invalidJson)
        `when`(response.setStatusCode(anyInt())).thenReturn(response)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)
        
        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was ended with 400
                verify(response).setStatusCode(400)
                verify(response).putHeader("Content-Type", "application/json")
                verify(response).end(contains("Invalid JSON"))
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
    
    @Test
    fun `should handle empty body`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val requestBody = mock(RequestBody::class.java)
        
        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.body()).thenReturn(requestBody)
        `when`(requestBody.buffer()).thenReturn(null)
        
        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was not ended
                verify(response, never()).setStatusCode(anyInt())
                verify(response, never()).end()
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
}
