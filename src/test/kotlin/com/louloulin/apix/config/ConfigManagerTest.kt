package com.louloulin.apix.config

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(VertxExtension::class)
class ConfigManagerTest {

    private lateinit var vertx: Vertx
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        
        // Set the config path to a temporary location
        System.setProperty("apix.config.path", tempDir.resolve("apix-test.json").toString())
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        // Clear the system property
        System.clearProperty("apix.config.path")
        
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `should load default configuration when no file exists`(testContext: VertxTestContext) {
        // Create config manager
        val configManager = ConfigManager(vertx)
        
        // Verify default values
        assertEquals("0.0.0.0", configManager.getGatewayHost())
        assertEquals(8080, configManager.getGatewayPort())
        assertEquals("0.0.0.0", configManager.getAdminHost())
        assertEquals(8081, configManager.getAdminPort())
        assertTrue(configManager.getPluginsConfig().isEmpty)
        assertTrue(configManager.getRoutesConfig().isEmpty)
        
        // Verify that the config file was created
        val configFile = tempDir.resolve("apix-test.json")
        assertTrue(Files.exists(configFile))
        
        testContext.completeNow()
    }
    
    @Test
    fun `should load configuration from file`(testContext: VertxTestContext) {
        // Create a test configuration file
        val configFile = tempDir.resolve("apix-test.json")
        val configJson = JsonObject()
            .put("gateway", JsonObject()
                .put("host", "127.0.0.1")
                .put("port", 9090)
            )
            .put("admin", JsonObject()
                .put("host", "127.0.0.1")
                .put("port", 9091)
            )
            .put("plugins", JsonArray()
                .add(JsonObject()
                    .put("id", "test-plugin")
                    .put("type", "test")
                )
            )
            .put("routes", JsonArray()
                .add(JsonObject()
                    .put("id", "test-route")
                    .put("path", "/test")
                    .put("targetUrl", "http://example.com")
                )
            )
        
        Files.writeString(configFile, configJson.encodePrettily())
        
        // Create config manager
        val configManager = ConfigManager(vertx)
        
        // Verify loaded values
        assertEquals("127.0.0.1", configManager.getGatewayHost())
        assertEquals(9090, configManager.getGatewayPort())
        assertEquals("127.0.0.1", configManager.getAdminHost())
        assertEquals(9091, configManager.getAdminPort())
        
        val plugins = configManager.getPluginsConfig()
        assertEquals(1, plugins.size())
        assertEquals("test-plugin", plugins.getJsonObject(0).getString("id"))
        
        val routes = configManager.getRoutesConfig()
        assertEquals(1, routes.size())
        assertEquals("test-route", routes.getJsonObject(0).getString("id"))
        
        testContext.completeNow()
    }
    
    @Test
    fun `should update configuration`(testContext: VertxTestContext) {
        // Create config manager with default configuration
        val configManager = ConfigManager(vertx)
        
        // Update configuration
        val newConfig = JsonObject()
            .put("gateway", JsonObject()
                .put("port", 9999)
            )
        
        configManager.updateConfig(newConfig)
        
        // Verify updated values
        assertEquals("0.0.0.0", configManager.getGatewayHost()) // Unchanged
        assertEquals(9999, configManager.getGatewayPort()) // Updated
        
        // Verify that the config file was updated
        val configFile = tempDir.resolve("apix-test.json")
        val savedConfig = JsonObject(Files.readString(configFile))
        assertEquals(9999, savedConfig.getJsonObject("gateway").getInteger("port"))
        
        testContext.completeNow()
    }
}
