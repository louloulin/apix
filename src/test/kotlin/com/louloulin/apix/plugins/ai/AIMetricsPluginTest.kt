package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
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
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import io.vertx.core.Future
import io.vertx.core.MultiMap
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString

@ExtendWith(VertxExtension::class)
class AIMetricsPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: AIMetricsPlugin

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // Create plugin configuration
        val config = JsonObject()
            .put("enabled", true)
            .put("track_tokens", true)
            .put("track_latency", true)
            .put("track_throughput", true)
            .put("track_model_performance", true)
            .put("metrics_retention_hours", 24)

        // Create plugin
        plugin = AIMetricsPlugin("test-ai-metrics", PluginConfig("test-ai-metrics", "ai-metrics", config))

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
    fun testBasicFunctionality(testContext: VertxTestContext) {
        // Create a mock RoutingContext
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Mock the request
        val request = Mockito.mock(HttpServerRequest::class.java)
        `when`(routingContext.request()).thenReturn(request)

        // Mock the path (not a metrics endpoint)
        `when`(request.path()).thenReturn("/api/chat/completions")

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the context was used to store request start time
                verify(routingContext).put(Mockito.eq("ai_request_start_time"), Mockito.anyLong())

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testMetricsEndpoint(testContext: VertxTestContext) {
        // Create a mock RoutingContext for metrics endpoint
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Mock the request
        val request = Mockito.mock(HttpServerRequest::class.java)
        `when`(routingContext.request()).thenReturn(request)

        // Mock the path
        `when`(request.path()).thenReturn("/api/metrics")

        // Mock the response
        val response = Mockito.mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)

        // Mock the query parameters
        `when`(request.getParam("format")).thenReturn("json")
        `when`(request.getParam("model")).thenReturn(null)

        // Capture the response
        val responseCaptor = ArgumentCaptor.forClass(String::class.java)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)
        `when`(response.end(responseCaptor.capture())).thenReturn(Future.succeededFuture())

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the response was sent
                verify(response).putHeader("Content-Type", "application/json")
                verify(response).end(anyString())

                // Verify the response content
                val responseJson = JsonObject(responseCaptor.value)
                assertTrue(responseJson.containsKey("overall"))
                assertTrue(responseJson.containsKey("tokenUsage"))
                assertTrue(responseJson.containsKey("modelPerformance"))

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testPrometheusMetricsEndpoint(testContext: VertxTestContext) {
        // Create a mock RoutingContext for metrics endpoint
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Mock the request
        val request = Mockito.mock(HttpServerRequest::class.java)
        `when`(routingContext.request()).thenReturn(request)

        // Mock the path
        `when`(request.path()).thenReturn("/api/metrics")

        // Mock the response
        val response = Mockito.mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)

        // Mock the query parameters
        `when`(request.getParam("format")).thenReturn("prometheus")
        `when`(request.getParam("model")).thenReturn(null)

        // Capture the response
        val responseCaptor = ArgumentCaptor.forClass(String::class.java)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)
        `when`(response.end(responseCaptor.capture())).thenReturn(Future.succeededFuture())

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the response was sent
                verify(response).putHeader("Content-Type", "text/plain")
                verify(response).end(anyString())

                // Verify the response content
                val responseText = responseCaptor.value
                assertTrue(responseText.contains("# HELP apix_ai_request_count"))
                assertTrue(responseText.contains("# TYPE apix_ai_request_count counter"))

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testFilteredMetricsEndpoint(testContext: VertxTestContext) {
        // Create a mock RoutingContext for metrics endpoint
        val routingContext = Mockito.mock(RoutingContext::class.java)

        // Mock the request
        val request = Mockito.mock(HttpServerRequest::class.java)
        `when`(routingContext.request()).thenReturn(request)

        // Mock the path
        `when`(request.path()).thenReturn("/api/metrics")

        // Mock the response
        val response = Mockito.mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)

        // Mock the query parameters
        `when`(request.getParam("format")).thenReturn("json")
        `when`(request.getParam("model")).thenReturn("gpt-4")

        // Capture the response
        val responseCaptor = ArgumentCaptor.forClass(String::class.java)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)
        `when`(response.end(responseCaptor.capture())).thenReturn(Future.succeededFuture())

        // Execute the plugin
        plugin.execute(routingContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Verify that the response was sent
                verify(response).putHeader("Content-Type", "application/json")
                verify(response).end(anyString())

                // Verify the response content
                val responseJson = JsonObject(responseCaptor.value)
                assertTrue(responseJson.containsKey("overall"))
                assertTrue(responseJson.containsKey("tokenUsage"))
                assertTrue(responseJson.containsKey("modelPerformance"))

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
