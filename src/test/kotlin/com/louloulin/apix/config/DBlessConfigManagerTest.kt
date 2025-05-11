package com.louloulin.apix.config

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
 * Tests for DBlessConfigManager.
 */
@ExtendWith(VertxExtension::class)
class DBlessConfigManagerTest {

    private lateinit var vertx: Vertx
    private lateinit var dblessConfigManager: DBlessConfigManager

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
            .put("test", "value")
            .put("number", 123)
            .put("nested", JsonObject()
                .put("key", "value")
            )

        Files.writeString(configFile, testConfig.encodePrettily())

        // Create DBlessConfigManager
        dblessConfigManager = DBlessConfigManager.getInstance(vertx)

        // Initialize with test options
        val options = JsonObject()
            .put("configPath", configFile.toString())
            .put("backupDir", backupDir.toString())
            .put("maxBackups", 5)

        dblessConfigManager.initialize(options)
            .onComplete(testContext.succeedingThenComplete())
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }

    @Test
    fun `test get config`(testContext: VertxTestContext) {
        // Get the config synchronously
        val config = dblessConfigManager.getConfig()

        testContext.verify {
            assertNotNull(config)
            assertEquals("value", config.getString("test"))
            assertEquals(123, config.getInteger("number"))
            assertNotNull(config.getJsonObject("nested"))
            assertEquals("value", config.getJsonObject("nested").getString("key"))

            testContext.completeNow()
        }
    }

    @Test
    fun `test get config section`(testContext: VertxTestContext) {
        val nested = dblessConfigManager.getConfigSection("nested")

        testContext.verify {
            assertNotNull(nested)
            assertEquals("value", nested.getString("key"))

            testContext.completeNow()
        }
    }

    @Test
    fun `test save config`(testContext: VertxTestContext) {
        val newConfig = JsonObject()
            .put("test", "new-value")
            .put("number", 456)
            .put("nested", JsonObject()
                .put("key", "new-value")
            )

        // Save the config and then verify it was saved correctly
        dblessConfigManager.saveConfig(newConfig)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // Get the updated config synchronously
                    val config = dblessConfigManager.getConfig()

                    testContext.verify {
                        assertNotNull(config)
                        assertEquals("new-value", config.getString("test"))
                        assertEquals(456, config.getInteger("number"))
                        assertNotNull(config.getJsonObject("nested"))
                        assertEquals("new-value", config.getJsonObject("nested").getString("key"))

                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test apply partial update`(testContext: VertxTestContext) {
        val update = JsonObject()
            .put("test", "updated-value")
            .put("new-key", "new-value")

        // Apply the partial update and then verify it was applied correctly
        dblessConfigManager.applyPartialUpdate(update)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // Get the updated config synchronously
                    val config = dblessConfigManager.getConfig()

                    testContext.verify {
                        assertNotNull(config)
                        assertEquals("updated-value", config.getString("test"))
                        assertEquals("new-value", config.getString("new-key"))
                        assertEquals(123, config.getInteger("number"))
                        assertNotNull(config.getJsonObject("nested"))
                        assertEquals("value", config.getJsonObject("nested").getString("key"))

                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test create and list backups`(testContext: VertxTestContext) {
        // Save config multiple times to create backups
        val config1 = JsonObject().put("version", 1)
        val config2 = JsonObject().put("version", 2)
        val config3 = JsonObject().put("version", 3)

        // Chain the operations to create backups and then list them
        dblessConfigManager.saveConfig(config1)
            .compose { _ -> dblessConfigManager.saveConfig(config2) }
            .compose { _ -> dblessConfigManager.saveConfig(config3) }
            .compose { _ -> dblessConfigManager.listBackupVersions() }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val versions = ar.result()

                    testContext.verify {
                        assertNotNull(versions)
                        assertTrue(versions.isNotEmpty())
                        assertTrue(versions.size >= 3)

                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
