package com.louloulin.apix.core.verticle

import io.vertx.core.Vertx
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DBlessVerticle.
 */
@ExtendWith(VertxExtension::class)
class DBlessVerticleTest {
    
    private lateinit var vertx: Vertx
    
    @TempDir
    lateinit var tempDir: Path
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // Create test config directory
        val configDir = tempDir.resolve("config")
        Files.createDirectories(configDir)
        
        // Create test backup directory
        val backupDir = tempDir.resolve("backups")
        Files.createDirectories(backupDir)
        
        // Create test config file
        val configFile = configDir.resolve("apix-test.json")
        val testConfig = JsonObject()
            .put("dbless", JsonObject()
                .put("enabled", true)
                .put("configPath", configFile.toString())
                .put("backupDir", backupDir.toString())
                .put("maxBackups", 5)
            )
            .put("test", "value")
            .put("number", 123)
            .put("nested", JsonObject()
                .put("key", "value")
            )
        
        Files.writeString(configFile, testConfig.encodePrettily())
        
        // Set system property for config path
        System.setProperty("apix.config.path", configFile.toString())
        
        // Deploy ConfigVerticle first
        vertx.deployVerticle(ConfigVerticle())
            .compose { _ -> 
                // Then deploy DBlessVerticle
                vertx.deployVerticle(DBlessVerticle())
            }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // Reset system property
        System.clearProperty("apix.config.path")
        
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get config`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>("apix.dbless.config.get", JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success", false))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("value", result.getString("test"))
                    assertEquals(123, result.getInteger("number"))
                    assertNotNull(result.getJsonObject("nested"))
                    assertEquals("value", result.getJsonObject("nested").getString("key"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get config section`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>("apix.dbless.config.get", JsonObject().put("section", "nested")) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success", false))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("value", result.getString("key"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test save config`(testContext: VertxTestContext) {
        val newConfig = JsonObject()
            .put("test", "new-value")
            .put("number", 456)
            .put("nested", JsonObject()
                .put("key", "new-value")
            )
        
        vertx.eventBus().request<JsonObject>(
            "apix.dbless.config.save", 
            JsonObject().put("config", newConfig)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success", false))
                    
                    // Now get the updated config
                    vertx.eventBus().request<JsonObject>("apix.dbless.config.get", JsonObject()) { getAr ->
                        if (getAr.succeeded()) {
                            val getResponse = getAr.result().body()
                            
                            assertTrue(getResponse.getBoolean("success", false))
                            val result = getResponse.getJsonObject("result")
                            assertNotNull(result)
                            assertEquals("new-value", result.getString("test"))
                            assertEquals(456, result.getInteger("number"))
                            assertNotNull(result.getJsonObject("nested"))
                            assertEquals("new-value", result.getJsonObject("nested").getString("key"))
                            
                            testContext.completeNow()
                        } else {
                            testContext.failNow(getAr.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test partial update`(testContext: VertxTestContext) {
        val update = JsonObject()
            .put("test", "updated-value")
            .put("new-key", "new-value")
        
        vertx.eventBus().request<JsonObject>(
            "apix.dbless.config.update", 
            JsonObject().put("update", update)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success", false))
                    
                    // Now get the updated config
                    vertx.eventBus().request<JsonObject>("apix.dbless.config.get", JsonObject()) { getAr ->
                        if (getAr.succeeded()) {
                            val getResponse = getAr.result().body()
                            
                            assertTrue(getResponse.getBoolean("success", false))
                            val result = getResponse.getJsonObject("result")
                            assertNotNull(result)
                            assertEquals("updated-value", result.getString("test"))
                            assertEquals("new-value", result.getString("new-key"))
                            
                            testContext.completeNow()
                        } else {
                            testContext.failNow(getAr.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
