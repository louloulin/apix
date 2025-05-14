package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(VertxExtension::class)
class CostTrackingPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: CostTrackingPlugin
    private lateinit var config: PluginConfig

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()

        // Create plugin configuration
        val configJson = JsonObject()
            .put("enabled", true)
            .put("track_costs", true)
            .put("budget_enabled", true)
            .put("monthly_budget", 100.0)
            .put("daily_budget", 10.0)
            .put("alert_threshold_percent", 80)
            .put("model_pricing", JsonObject()
                .put("gpt-4", JsonObject()
                    .put("input_price", 0.03)
                    .put("output_price", 0.06)
                    .put("provider", "openai")
                    .put("unit", "1K tokens")
                )
                .put("gpt-3.5-turbo", JsonObject()
                    .put("input_price", 0.0015)
                    .put("output_price", 0.002)
                    .put("provider", "openai")
                    .put("unit", "1K tokens")
                )
            )

        config = PluginConfig("test-cost-tracking", "cost-tracking", configJson)

        // Create plugin instance
        plugin = CostTrackingPlugin("test-cost-tracking", config)

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
    fun tearDown(testContext: VertxTestContext) {
        plugin.shutdown()
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun testCalculateCost(testContext: VertxTestContext) {
        // Create a mock request with OpenAI response
        val router = Router.router(vertx)
        router.route().handler(BodyHandler.create())

        router.route(HttpMethod.POST, "/v1/chat/completions").handler { ctx ->
            // Mock OpenAI response
            val responseJson = JsonObject()
                .put("id", "chatcmpl-123")
                .put("object", "chat.completion")
                .put("model", "gpt-4")
                .put("usage", JsonObject()
                    .put("prompt_tokens", 10)
                    .put("completion_tokens", 20)
                    .put("total_tokens", 30)
                )

            ctx.response()
                .putHeader("Content-Type", "application/json")
                .putHeader("X-Response-Body", responseJson.encode())
                .end(responseJson.encode())
        }

        // Create a mock routing context
        val mockContext = mock(RoutingContext::class.java)
        val mockRequest = mock(io.vertx.core.http.HttpServerRequest::class.java)
        val mockResponse = mock(io.vertx.core.http.HttpServerResponse::class.java)
        val mockHeaders = mock(io.vertx.core.MultiMap::class.java)

        `when`(mockContext.request()).thenReturn(mockRequest)
        `when`(mockRequest.path()).thenReturn("/v1/chat/completions")
        `when`(mockContext.response()).thenReturn(mockResponse)
        `when`(mockResponse.headers()).thenReturn(mockHeaders)

        // Set up model info in context
        val modelInfo = AIMetricsPlugin.ModelInfo("gpt-4", "openai")
        `when`(mockContext.get<AIMetricsPlugin.ModelInfo>("ai_model_info")).thenReturn(modelInfo)

        // Set up response body
        val responseJson = JsonObject()
            .put("id", "chatcmpl-123")
            .put("object", "chat.completion")
            .put("model", "gpt-4")
            .put("usage", JsonObject()
                .put("prompt_tokens", 10)
                .put("completion_tokens", 20)
                .put("total_tokens", 30)
            )

        `when`(mockHeaders.get("X-Response-Body")).thenReturn(responseJson.encode())

        // Add headers end handler
        `when`(mockContext.addHeadersEndHandler(Mockito.any())).thenAnswer { invocation ->
            val handler = invocation.getArgument<io.vertx.core.Handler<Void>>(0)
            handler.handle(null)
            null
        }

        // Execute plugin
        plugin.execute(mockContext).onComplete { ar ->
            if (ar.succeeded()) {
                // Use reflection to access private fields for testing
                val modelCostsField = CostTrackingPlugin::class.java.getDeclaredField("modelCosts")
                modelCostsField.isAccessible = true
                val modelCosts = modelCostsField.get(plugin) as ConcurrentHashMap<String, Double>

                // Check if cost was tracked
                assertTrue(modelCosts.containsKey("gpt-4"))

                // Expected cost: (10 tokens * $0.03/1K) + (20 tokens * $0.06/1K) = $0.0003 + $0.0012 = $0.0015
                val expectedCost = 0.0015
                val actualCost = modelCosts["gpt-4"] ?: 0.0

                // Allow for small floating point differences
                assertTrue(Math.abs(expectedCost - actualCost) < 0.0001,
                    "Expected cost $expectedCost, but got $actualCost")

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @Test
    fun testCostQueryEndpoint(testContext: VertxTestContext) {
        // Create a mock routing context for cost query
        val mockContext = mock(RoutingContext::class.java)
        val mockRequest = mock(io.vertx.core.http.HttpServerRequest::class.java)
        val mockResponse = mock(io.vertx.core.http.HttpServerResponse::class.java)

        `when`(mockContext.request()).thenReturn(mockRequest)
        `when`(mockRequest.path()).thenReturn("/costs")
        `when`(mockRequest.getParam("format")).thenReturn("json")
        `when`(mockRequest.getParam("period")).thenReturn("all")
        `when`(mockContext.response()).thenReturn(mockResponse)

        // Set up response chaining
        `when`(mockResponse.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(mockResponse)

        // Capture the response
        `when`(mockResponse.end(Mockito.any<String>())).thenAnswer { invocation ->
            val responseJson = JsonObject(invocation.getArgument<String>(0))

            // Verify response structure
            assertTrue(responseJson.containsKey("overall"))
            assertTrue(responseJson.containsKey("modelCosts"))
            assertTrue(responseJson.containsKey("dailyCosts"))

            testContext.completeNow()
            null
        }

        // Execute plugin
        plugin.execute(mockContext)
    }

    @Test
    fun testOptimizationSuggestions(testContext: VertxTestContext) {
        // Use reflection to access and populate private fields for testing
        val modelUsagePatternsField = CostTrackingPlugin::class.java.getDeclaredField("modelUsagePatterns")
        modelUsagePatternsField.isAccessible = true

        val modelUsagePatterns = modelUsagePatternsField.get(plugin) as ConcurrentHashMap<String, CostTrackingPlugin.ModelUsagePattern>

        // Add a test usage pattern that should trigger optimization suggestions
        modelUsagePatterns["gpt-4"] = CostTrackingPlugin.ModelUsagePattern(
            requestCount = 100,
            totalInputTokens = 30000,
            totalOutputTokens = 10000,
            avgInputTokens = 300.0,
            avgOutputTokens = 100.0,
            totalCost = 5.0,
            lastUpdated = java.time.Instant.now()
        )

        // Create a mock routing context for cost query
        val mockContext = mock(RoutingContext::class.java)
        val mockRequest = mock(io.vertx.core.http.HttpServerRequest::class.java)
        val mockResponse = mock(io.vertx.core.http.HttpServerResponse::class.java)

        `when`(mockContext.request()).thenReturn(mockRequest)
        `when`(mockRequest.path()).thenReturn("/costs")
        `when`(mockRequest.getParam("format")).thenReturn("json")
        `when`(mockRequest.getParam("period")).thenReturn("all")
        `when`(mockContext.response()).thenReturn(mockResponse)

        // Set up response chaining
        `when`(mockResponse.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(mockResponse)

        // Capture the response
        `when`(mockResponse.end(Mockito.any<String>())).thenAnswer { invocation ->
            val responseJson = JsonObject(invocation.getArgument<String>(0))

            // Verify optimization suggestions
            assertTrue(responseJson.containsKey("optimizationSuggestions"))
            val suggestions = responseJson.getJsonArray("optimizationSuggestions")
            assertTrue(suggestions.size() > 0)

            // Check suggestion content
            val suggestion = suggestions.getJsonObject(0)
            assertTrue(suggestion.containsKey("type"))
            assertTrue(suggestion.containsKey("model"))
            assertTrue(suggestion.containsKey("suggestion"))
            assertTrue(suggestion.containsKey("potential_savings"))

            testContext.completeNow()
            null
        }

        // Execute plugin
        plugin.execute(mockContext)
    }
}
