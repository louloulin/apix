package com.louloulin.apix.dbless

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * DBlessVerticle 提供无数据库模式的配置管理功能。
 * 它使用文件系统来存储和管理配置，支持配置备份和恢复。
 */
class DBlessVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(DBlessVerticle::class.java)

    // 配置文件路径
    private lateinit var configPath: Path

    // 备份目录
    private lateinit var backupDir: Path

    // 最大备份数量
    private var maxBackups: Int = 5

    // 当前配置
    private var config: JsonObject = JsonObject()

    override fun start(startPromise: Promise<Void>) {
        try {
            logger.info("Starting DBlessVerticle...")

            // 从系统配置中获取 DBless 配置
            val dblessConfig = config().getJsonObject("dbless", JsonObject())

            // 检查是否启用了 DBless 模式
            val enabled = dblessConfig.getBoolean("enabled", true) // 默认启用
            if (!enabled) {
                logger.info("DBless mode is disabled, not starting DBlessVerticle")
                startPromise.complete()
                return
            }

            logger.info("DBless mode is enabled, starting DBlessVerticle")

            // 获取配置文件路径
            val configPathStr = dblessConfig.getString("configPath") ?: System.getProperty("apix.config.path")
            if (configPathStr == null) {
                val errorMsg = "DBless mode is enabled but configPath is not specified"
                logger.error(errorMsg)
                startPromise.fail(errorMsg)
                return
            }

            logger.info("Using configPath: {}", configPathStr)

            configPath = Paths.get(configPathStr)

            // 获取备份目录
            val backupDirStr = dblessConfig.getString("backupDir", "backups")
            backupDir = Paths.get(backupDirStr)

            // 获取最大备份数量
            maxBackups = dblessConfig.getInteger("maxBackups", 5)

            // 创建备份目录（如果不存在）
            if (!Files.exists(backupDir)) {
                Files.createDirectories(backupDir)
                logger.info("Created backup directory: {}", backupDir)
            }

            // 加载配置
            loadConfig()
                .onSuccess { loadedConfig ->
                    // 设置事件总线处理器
                    setupEventBusHandlers()

                    logger.info("DBlessVerticle started successfully")
                    startPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("Failed to start DBlessVerticle", cause)
                    startPromise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("Error starting DBlessVerticle", e)
            startPromise.fail(e)
        }
    }

    /**
     * 设置事件总线处理器
     */
    private fun setupEventBusHandlers() {
        // 获取配置
        vertx.eventBus().consumer<JsonObject>("apix.dbless.config.get") { message ->
            try {
                val response = JsonObject()
                    .put("success", true)
                    .put("result", config)

                message.reply(response)
            } catch (e: Exception) {
                logger.error("Error handling config.get request", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 更新配置
        vertx.eventBus().consumer<JsonObject>("apix.dbless.config.update") { message ->
            try {
                val body = message.body()
                val newConfig = body.getJsonObject("config")

                if (newConfig == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "No config provided")
                    )
                    return@consumer
                }

                // 创建备份
                createBackup()
                    .compose { _ ->
                        // 更新配置
                        config = newConfig

                        // 保存配置
                        saveConfig()
                    }
                    .onSuccess { _ ->
                        message.reply(JsonObject()
                            .put("success", true)
                        )
                    }
                    .onFailure { cause ->
                        logger.error("Failed to update config", cause)
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", cause.message)
                        )
                    }
            } catch (e: Exception) {
                logger.error("Error handling config.update request", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 获取备份列表
        vertx.eventBus().consumer<JsonObject>("apix.dbless.backup.list") { message ->
            try {
                listBackups()
                    .onSuccess { backups ->
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", backups)
                        )
                    }
                    .onFailure { cause ->
                        logger.error("Failed to list backups", cause)
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", cause.message)
                        )
                    }
            } catch (e: Exception) {
                logger.error("Error handling backup.list request", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 恢复备份
        vertx.eventBus().consumer<JsonObject>("apix.dbless.backup.restore") { message ->
            try {
                val body = message.body()
                val backupName = body.getString("name")

                if (backupName == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "No backup name provided")
                    )
                    return@consumer
                }

                restoreBackup(backupName)
                    .onSuccess { restoredConfig ->
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", restoredConfig)
                        )
                    }
                    .onFailure { cause ->
                        logger.error("Failed to restore backup", cause)
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", cause.message)
                        )
                    }
            } catch (e: Exception) {
                logger.error("Error handling backup.restore request", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 创建备份
        vertx.eventBus().consumer<JsonObject>("apix.dbless.backup.create") { message ->
            try {
                createBackup()
                    .onSuccess { backupName ->
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", JsonObject().put("name", backupName))
                        )
                    }
                    .onFailure { cause ->
                        logger.error("Failed to create backup", cause)
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", cause.message)
                        )
                    }
            } catch (e: Exception) {
                logger.error("Error handling backup.create request", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }
    }

    /**
     * 加载配置
     */
    private fun loadConfig(): Future<JsonObject> {
        return vertx.executeBlocking<JsonObject> { promise ->
            try {
                if (Files.exists(configPath)) {
                    val configContent = Files.readString(configPath)
                    config = JsonObject(configContent)
                    logger.info("Loaded configuration from: {}", configPath)
                    promise.complete(config)
                } else {
                    logger.warn("Configuration file does not exist: {}, using empty configuration", configPath)
                    config = JsonObject()
                    promise.complete(config)
                }
            } catch (e: Exception) {
                logger.error("Failed to load configuration", e)
                promise.fail(e)
            }
        }
    }

    /**
     * 保存配置
     */
    private fun saveConfig(): Future<Void> {
        return vertx.executeBlocking<Void> { promise ->
            try {
                // 创建父目录（如果不存在）
                Files.createDirectories(configPath.parent)

                // 写入配置文件
                Files.writeString(configPath, config.encodePrettily())

                logger.info("Saved configuration to: {}", configPath)
                promise.complete()
            } catch (e: Exception) {
                logger.error("Failed to save configuration", e)
                promise.fail(e)
            }
        }
    }

    /**
     * 创建备份
     */
    private fun createBackup(): Future<String> {
        return vertx.executeBlocking<String> { promise ->
            try {
                // 创建备份目录（如果不存在）
                Files.createDirectories(backupDir)

                // 生成备份文件名
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                val backupName = "backup_$timestamp.json"
                val backupPath = backupDir.resolve(backupName)

                // 写入备份文件
                Files.writeString(backupPath, config.encodePrettily())

                logger.info("Created backup: {}", backupPath)

                // 清理旧备份
                cleanupOldBackups()

                promise.complete(backupName)
            } catch (e: Exception) {
                logger.error("Failed to create backup", e)
                promise.fail(e)
            }
        }
    }

    /**
     * 清理旧备份
     */
    private fun cleanupOldBackups() {
        try {
            // 获取所有备份文件
            val backupFiles = Files.list(backupDir)
                .filter { it.fileName.toString().startsWith("backup_") && it.fileName.toString().endsWith(".json") }
                .sorted { a, b -> Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)) }
                .toList()

            // 如果备份文件数量超过最大备份数量，删除最旧的备份
            if (backupFiles.size > maxBackups) {
                for (i in maxBackups until backupFiles.size) {
                    Files.delete(backupFiles[i])
                    logger.info("Deleted old backup: {}", backupFiles[i])
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to cleanup old backups", e)
        }
    }

    /**
     * 列出所有备份
     */
    private fun listBackups(): Future<JsonObject> {
        return vertx.executeBlocking<JsonObject> { promise ->
            try {
                // 获取所有备份文件
                val backupFiles = Files.list(backupDir)
                    .filter { it.fileName.toString().startsWith("backup_") && it.fileName.toString().endsWith(".json") }
                    .sorted { a, b -> Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)) }
                    .toList()

                // 构建备份列表
                val backups = JsonObject()
                val backupList = backupFiles.map { backupFile ->
                    val fileName = backupFile.fileName.toString()
                    val lastModified = Files.getLastModifiedTime(backupFile).toInstant()
                    val size = Files.size(backupFile)

                    JsonObject()
                        .put("name", fileName)
                        .put("lastModified", lastModified.toString())
                        .put("size", size)
                }

                backups.put("backups", backupList)
                promise.complete(backups)
            } catch (e: Exception) {
                logger.error("Failed to list backups", e)
                promise.fail(e)
            }
        }
    }

    /**
     * 恢复备份
     */
    private fun restoreBackup(backupName: String): Future<JsonObject> {
        return vertx.executeBlocking<JsonObject> { promise ->
            try {
                val backupPath = backupDir.resolve(backupName)

                if (!Files.exists(backupPath)) {
                    promise.fail("Backup does not exist: $backupName")
                    return@executeBlocking
                }

                // 读取备份文件
                val backupContent = Files.readString(backupPath)
                val backupConfig = JsonObject(backupContent)

                // 创建当前配置的备份
                createBackup()
                    .compose { _ ->
                        // 更新配置
                        config = backupConfig.copy()

                        // 保存配置
                        saveConfig()
                            .map { backupConfig }
                    }
                    .onSuccess { restoredConfig ->
                        logger.info("Restored backup: {}", backupName)
                        promise.complete(restoredConfig)
                    }
                    .onFailure { cause ->
                        logger.error("Failed to restore backup", cause)
                        promise.fail(cause)
                    }
            } catch (e: Exception) {
                logger.error("Failed to restore backup", e)
                promise.fail(e)
            }
        }
    }
}
