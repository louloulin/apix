package com.louloulin.apix.dbless

import com.louloulin.apix.config.ConfigVerticle
import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * 测试 DBlessVerticle 的功能
 */
class DBlessVerticleTest : BaseVertxTest() {
    private lateinit var tempDir: Path
    private lateinit var configDir: Path
    private lateinit var backupDir: Path
    private lateinit var configFile: Path

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建临时目录
            tempDir = Files.createTempDirectory("dbless-test")

            // 创建测试配置目录
            configDir = tempDir.resolve("config")
            Files.createDirectories(configDir)

            // 创建测试备份目录
            backupDir = tempDir.resolve("backups")
            Files.createDirectories(backupDir)

            // 创建测试配置文件
            configFile = configDir.resolve("apix-test.json")
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

            // 设置系统属性
            System.setProperty("apix.config.path", configFile.toString())

            // 部署 ConfigVerticle 和 DBlessVerticle
            vertx.deployVerticle(ConfigVerticle())
                .compose { _ ->
                    vertx.deployVerticle(DBlessVerticle())
                }
                .onSuccess { _ ->
                    // 等待一段时间，确保服务已启动
                    waitForService(5000) {
                        testContext.completeNow()
                    }
                }
                .onFailure { cause ->
                    handleError(testContext, cause)
                }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    override fun cleanup() {
        try {
            // 删除临时目录
            if (Files.exists(tempDir)) {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach { Files.delete(it) }
            }

            // 清除系统属性
            System.clearProperty("apix.config.path")

            logger.info("Cleaned up resources in DBlessVerticleTest")
        } catch (e: Exception) {
            logger.warn("Error during cleanup in DBlessVerticleTest: {}", e.message)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testGetConfig(testContext: VertxTestContext) {
        try {
            // 等待一段时间，确保服务已启动
            waitForService(2000) {
                vertx.eventBus().request<JsonObject>("apix.dbless.config.get", JsonObject()) { ar ->
                    handleAsyncResult(testContext, ar) { message ->
                        val response = message.body()

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
                    }
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testUpdateConfig(testContext: VertxTestContext) {
        try {
            // 等待一段时间，确保服务已启动
            waitForService(2000) {
                // 更新配置
                val newConfig = JsonObject()
                    .put("dbless", JsonObject()
                        .put("enabled", true)
                        .put("configPath", configFile.toString())
                        .put("backupDir", backupDir.toString())
                        .put("maxBackups", 5)
                    )
                    .put("test", "updated-value")
                    .put("number", 456)
                    .put("nested", JsonObject()
                        .put("key", "updated-value")
                    )
                    .put("newKey", "new-value")

                vertx.eventBus().request<JsonObject>("apix.dbless.config.update", JsonObject().put("config", newConfig)) { updateAr ->
                    handleAsyncResult(testContext, updateAr) { updateMessage ->
                        val updateResponse = updateMessage.body()

                        testContext.verify {
                            assertTrue(updateResponse.getBoolean("success", false))

                            // 获取更新后的配置
                            vertx.eventBus().request<JsonObject>("apix.dbless.config.get", JsonObject()) { getAr ->
                                handleAsyncResult(testContext, getAr) { getMessage ->
                                    val getResponse = getMessage.body()

                                    testContext.verify {
                                        assertTrue(getResponse.getBoolean("success", false))
                                        val result = getResponse.getJsonObject("result")
                                        assertNotNull(result)
                                        assertEquals("updated-value", result.getString("test"))
                                        assertEquals(456, result.getInteger("number"))
                                        assertNotNull(result.getJsonObject("nested"))
                                        assertEquals("updated-value", result.getJsonObject("nested").getString("key"))
                                        assertEquals("new-value", result.getString("newKey"))

                                        testContext.completeNow()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testCreateAndListBackups(testContext: VertxTestContext) {
        try {
            // 等待一段时间，确保服务已启动
            waitForService(2000) {
                // 创建备份
                vertx.eventBus().request<JsonObject>("apix.dbless.backup.create", JsonObject()) { createAr ->
                    handleAsyncResult(testContext, createAr) { createMessage ->
                        val createResponse = createMessage.body()

                        testContext.verify {
                            assertTrue(createResponse.getBoolean("success", false))
                            assertNotNull(createResponse.getJsonObject("result"))
                            assertNotNull(createResponse.getJsonObject("result").getString("name"))

                            // 列出备份
                            vertx.eventBus().request<JsonObject>("apix.dbless.backup.list", JsonObject()) { listAr ->
                                handleAsyncResult(testContext, listAr) { listMessage ->
                                    val listResponse = listMessage.body()

                                    testContext.verify {
                                        assertTrue(listResponse.getBoolean("success", false))
                                        val result = listResponse.getJsonObject("result")
                                        assertNotNull(result)
                                        val backups = result.getJsonArray("backups")
                                        assertNotNull(backups)
                                        assertTrue(backups.size() > 0)

                                        testContext.completeNow()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testRestoreBackup(testContext: VertxTestContext) {
        try {
            // 等待一段时间，确保服务已启动
            waitForService(2000) {
                // 更新配置
                val newConfig = JsonObject()
                    .put("dbless", JsonObject()
                        .put("enabled", true)
                        .put("configPath", configFile.toString())
                        .put("backupDir", backupDir.toString())
                        .put("maxBackups", 5)
                    )
                    .put("test", "before-backup")
                    .put("number", 789)

                vertx.eventBus().request<JsonObject>("apix.dbless.config.update", JsonObject().put("config", newConfig)) { updateAr ->
                    handleAsyncResult(testContext, updateAr) { updateMessage ->
                        // 创建备份
                        vertx.eventBus().request<JsonObject>("apix.dbless.backup.create", JsonObject()) { createAr ->
                            handleAsyncResult(testContext, createAr) { createMessage ->
                                val backupName = createMessage.body().getJsonObject("result").getString("name")

                                // 再次更新配置
                                val newerConfig = JsonObject()
                                    .put("dbless", JsonObject()
                                        .put("enabled", true)
                                        .put("configPath", configFile.toString())
                                        .put("backupDir", backupDir.toString())
                                        .put("maxBackups", 5)
                                    )
                                    .put("test", "after-backup")
                                    .put("number", 999)

                                vertx.eventBus().request<JsonObject>("apix.dbless.config.update", JsonObject().put("config", newerConfig)) { updateAgainAr ->
                                    handleAsyncResult(testContext, updateAgainAr) { _ ->
                                        // 恢复备份
                                        vertx.eventBus().request<JsonObject>("apix.dbless.backup.restore", JsonObject().put("name", backupName)) { restoreAr ->
                                            handleAsyncResult(testContext, restoreAr) { restoreMessage ->
                                                val restoreResponse = restoreMessage.body()

                                                testContext.verify {
                                                    assertTrue(restoreResponse.getBoolean("success", false))

                                                    // 获取恢复后的配置
                                                    vertx.eventBus().request<JsonObject>("apix.dbless.config.get", JsonObject()) { getAr ->
                                                        handleAsyncResult(testContext, getAr) { getMessage ->
                                                            val getResponse = getMessage.body()

                                                            testContext.verify {
                                                                assertTrue(getResponse.getBoolean("success", false))
                                                                val result = getResponse.getJsonObject("result")
                                                                assertNotNull(result)
                                                                assertEquals("before-backup", result.getString("test"))
                                                                assertEquals(789, result.getInteger("number"))

                                                                testContext.completeNow()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
