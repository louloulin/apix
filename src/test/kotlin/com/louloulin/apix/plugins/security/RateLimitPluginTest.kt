package com.louloulin.apix.plugins.security

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.contains

@ExtendWith(VertxExtension::class)
class RateLimitPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: RateLimitPlugin

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()

        // Create plugin config with a low rate limit for testing
        val configJson = JsonObject()
            .put("limit", 2)
            .put("window", 60)

        val config = PluginConfig("test-rate-limit", "rate-limiter", configJson)
        plugin = RateLimitPlugin("test-rate-limit", config)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `should allow requests within rate limit`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val remoteAddress = mock(SocketAddress::class.java)

        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.remoteAddress()).thenReturn(remoteAddress)
        `when`(remoteAddress.hostAddress()).thenReturn("127.0.0.1")
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)

        // First request should be allowed
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that the response was not ended
                verify(response, never()).setStatusCode(anyInt())
                verify(response, never()).end()

                // Second request should also be allowed (limit is 2)
                plugin.execute(routingContext).onComplete { result2 ->
                    if (result2.succeeded()) {
                        // Verify that the response was not ended
                        verify(response, never()).setStatusCode(anyInt())
                        verify(response, never()).end()
                        testContext.completeNow()
                    } else {
                        testContext.failNow(result2.cause())
                    }
                }
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should reject requests exceeding rate limit`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val remoteAddress = mock(SocketAddress::class.java)

        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.remoteAddress()).thenReturn(remoteAddress)
        `when`(remoteAddress.hostAddress()).thenReturn("127.0.0.2") // Different IP to avoid interference with other tests
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)
        `when`(response.setStatusCode(anyInt())).thenReturn(response)

        // Make 3 requests (limit is 2)
        plugin.execute(routingContext).onComplete { _ ->
            plugin.execute(routingContext).onComplete { _ ->
                // Third request should be rejected
                plugin.execute(routingContext).onComplete { result ->
                    if (result.succeeded()) {
                        // Verify that the response was ended with 429
                        verify(response).setStatusCode(429)
                        verify(response).putHeader("Content-Type", "application/json")
                        verify(response).putHeader("Retry-After", "60")
                        verify(response).end(contains("Rate limit exceeded"))
                        testContext.completeNow()
                    } else {
                        testContext.failNow(result.cause())
                    }
                }
            }
        }
    }

    @Test
    fun `should add rate limit headers to response`(testContext: VertxTestContext) {
        // Mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val remoteAddress = mock(SocketAddress::class.java)

        // Set up mocks
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.remoteAddress()).thenReturn(remoteAddress)
        `when`(remoteAddress.hostAddress()).thenReturn("127.0.0.3") // Different IP to avoid interference with other tests
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)

        // Execute plugin
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that rate limit headers were added
                verify(response).putHeader("X-RateLimit-Limit", "2")
                verify(response).putHeader("X-RateLimit-Remaining", "1")
                verify(response).putHeader(eq("X-RateLimit-Reset"), anyString())
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
}
