package com.louloulin.apix.integration

import com.louloulin.apix.core.ApixVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

@ExtendWith(VertxExtension::class)
@Disabled("Integration tests require external services")
class GatewayIntegrationTest {

    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // Create web client
        val webClientOptions = WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(8080)
        
        webClient = WebClient.create(vertx, webClientOptions)
        
        // Create test configuration
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        
        val configFile = configDir.resolve("apix.json")
        val configJson = JsonObject()
            .put("gateway", JsonObject()
                .put("host", "localhost")
                .put("port", 8080)
            )
            .put("admin", JsonObject()
                .put("host", "localhost")
                .put("port", 8081)
            )
            .put("plugins", JsonArray()
                .add(JsonObject()
                    .put("id", "api-key")
                    .put("type", "api-key")
                    .put("config", JsonObject()
                        .put("header", "X-API-Key")
                        .put("keys", JsonArray().add("test-key"))
                    )
                )
            )
            .put("routes", JsonArray()
                .add(JsonObject()
                    .put("id", "echo")
                    .put("name", "Echo Service")
                    .put("path", "/echo")
                    .put("methods", JsonArray().add("POST"))
                    .put("targetUrl", "https://postman-echo.com/post")
                    .put("plugins", JsonArray().add("api-key"))
                    .put("enabled", true)
                )
            )
        
        Files.writeString(configFile, configJson.encodePrettily())
        
        // Set the config path
        System.setProperty("apix.config.path", configFile.toString())
        
        // Deploy the verticle
        vertx.deployVerticle(ApixVerticle())
            .onSuccess { _ -> testContext.completeNow() }
            .onFailure { testContext.failNow(it) }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        // Clear the system property
        System.clearProperty("apix.config.path")
        
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `should reject request without API key`(testContext: VertxTestContext) {
        // Send a request without API key
        webClient.post("/echo")
            .sendJsonObject(JsonObject().put("message", "Hello, World!"))
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    
                    // Verify that the request was rejected
                    assertEquals(401, response.statusCode())
                    assertEquals("application/json", response.getHeader("Content-Type"))
                    
                    val body = response.bodyAsJsonObject()
                    assertEquals("API key is missing", body.getString("error"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @Test
    fun `should accept request with valid API key`(testContext: VertxTestContext) {
        // Send a request with valid API key
        webClient.post("/echo")
            .putHeader("X-API-Key", "test-key")
            .sendJsonObject(JsonObject().put("message", "Hello, World!"))
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    
                    // Verify that the request was accepted
                    assertEquals(200, response.statusCode())
                    
                    // The response should be from the echo service
                    val body = response.bodyAsJsonObject()
                    val data = body.getJsonObject("data")
                    assertEquals("Hello, World!", data.getString("message"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @Test
    fun `should reject request with invalid API key`(testContext: VertxTestContext) {
        // Send a request with invalid API key
        webClient.post("/echo")
            .putHeader("X-API-Key", "invalid-key")
            .sendJsonObject(JsonObject().put("message", "Hello, World!"))
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    
                    // Verify that the request was rejected
                    assertEquals(403, response.statusCode())
                    assertEquals("application/json", response.getHeader("Content-Type"))
                    
                    val body = response.bodyAsJsonObject()
                    assertEquals("Invalid API key", body.getString("error"))
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
}
