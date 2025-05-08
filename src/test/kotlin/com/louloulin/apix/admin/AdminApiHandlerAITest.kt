package com.louloulin.apix.admin

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.PluginManager
import com.louloulin.apix.core.RouteManager
import com.louloulin.apix.core.ServiceManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.plugins.ai.TokenUsagePlugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class AdminApiHandlerAITest {

    private lateinit var vertx: Vertx
    private lateinit var client: WebClient
    private lateinit var configManager: ConfigManager
    private lateinit var pluginManager: PluginManager
    private lateinit var routeManager: RouteManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var adminApiHandler: AdminApiHandler

    private var port = 0

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        client = WebClient.create(vertx)

        // Mock dependencies
        configManager = mock(ConfigManager::class.java)
        pluginManager = mock(PluginManager::class.java)
        routeManager = mock(RouteManager::class.java)
        serviceManager = mock(ServiceManager::class.java)

        // Create AdminApiHandler with mocked dependencies
        adminApiHandler = AdminApiHandler(configManager, pluginManager, routeManager, serviceManager)

        // Create router and set up routes
        val router = Router.router(vertx)
        adminApiHandler.setupRoutes(router)

        // Start HTTP server on a random port
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(0) { ar ->
                if (ar.succeeded()) {
                    port = ar.result().actualPort()
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    fun testGetAiModels(testContext: VertxTestContext) {
        client.get(port, "localhost", "/ai/models")
            .send(testContext.succeeding { response ->
                testContext.verify {
                    assert(response.statusCode() == 200)
                    val body = response.bodyAsJsonObject()
                    assert(body.getJsonArray("models") != null)
                    assert(body.getJsonArray("models").size() >= 3) // Should have at least 3 models
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun testGetAiUsage(testContext: VertxTestContext) {
        // Mock TokenUsagePlugin
        val tokenUsagePlugin = mock(TokenUsagePlugin::class.java)
        val usageStats = JsonObject()
            .put("total_tokens", 1000)
            .put("prompt_tokens", 400)
            .put("completion_tokens", 600)
            .put("total_requests", 50)
            .put("models", JsonObject())
            .put("daily", JsonObject())

        `when`(tokenUsagePlugin.getUsageStats()).thenReturn(usageStats)
        `when`(tokenUsagePlugin.type).thenReturn("token-usage")

        // Mock PluginManager to return our mock TokenUsagePlugin
        `when`(pluginManager.getAllPlugins()).thenReturn(listOf(tokenUsagePlugin))

        client.get(port, "localhost", "/ai/usage")
            .send(testContext.succeeding { response ->
                testContext.verify {
                    assert(response.statusCode() == 200)
                    val body = response.bodyAsJsonObject()
                    assert(body.getJsonObject("usage") != null)
                    assert(body.getJsonObject("usage").getInteger("total_tokens") == 1000)
                    assert(body.getJsonObject("usage").getInteger("prompt_tokens") == 400)
                    assert(body.getJsonObject("usage").getInteger("completion_tokens") == 600)
                    assert(body.getJsonObject("usage").getInteger("total_requests") == 50)
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun testGetAiCacheStats(testContext: VertxTestContext) {
        // Set up mock response for cache stats
        val statsResponse = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("total", JsonObject()
                    .put("modelId", "total")
                    .put("hits", 100)
                    .put("misses", 50)
                    .put("hitRate", 0.67)
                    .put("size", 150)
                )
                .put("models", JsonArray()
                    .add(JsonObject()
                        .put("modelId", "gpt-4")
                        .put("hits", 60)
                        .put("misses", 20)
                        .put("hitRate", 0.75)
                    )
                    .add(JsonObject()
                        .put("modelId", "gpt-3.5-turbo")
                        .put("hits", 40)
                        .put("misses", 30)
                        .put("hitRate", 0.57)
                    )
                )
            )

        // Set up EventBus mock
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_STATS) { message ->
            message.reply(statsResponse)
        }

        client.get(port, "localhost", "/ai/cache/stats")
            .send(testContext.succeeding { response ->
                testContext.verify {
                    assert(response.statusCode() == 200)
                    val body = response.bodyAsJsonObject()
                    assert(body.getJsonObject("stats") != null)
                    assert(body.getJsonObject("stats").getJsonObject("total").getInteger("hits") == 100)
                    assert(body.getJsonObject("stats").getJsonArray("models").size() == 2)
                    testContext.completeNow()
                }
            })
    }

    @Test
    fun testClearAiCache(testContext: VertxTestContext) {
        // Set up mock response for cache clear
        val clearResponse = JsonObject()
            .put("success", true)
            .put("result", 25) // 25 entries cleared

        // Set up EventBus mock
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_CLEAR) { message ->
            message.reply(clearResponse)
        }

        client.post(port, "localhost", "/ai/cache/clear")
            .send(testContext.succeeding { response ->
                testContext.verify {
                    assert(response.statusCode() == 200)
                    val body = response.bodyAsJsonObject()
                    assert(body.getBoolean("success") == true)
                    assert(body.getInteger("count") == 25)
                    testContext.completeNow()
                }
            })
    }
}
