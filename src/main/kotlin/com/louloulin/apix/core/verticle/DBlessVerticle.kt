package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.DBlessConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Verticle responsible for DB-less mode configuration management.
 */
class DBlessVerticle : BaseVerticle() {
    // Use the logger from BaseVerticle

    // DB-less configuration manager
    private lateinit var dblessConfigManager: DBlessConfigManager

    // Whether DB-less mode is enabled
    private var dblessModeEnabled = false

    override fun registerEventBusHandlers() {
        try {
            // DB-less configuration management
            vertx.eventBus().consumer<JsonObject>("apix.dbless.config.get", this::handleGetConfig)
            vertx.eventBus().consumer<JsonObject>("apix.dbless.config.save", this::handleSaveConfig)
            vertx.eventBus().consumer<JsonObject>("apix.dbless.config.reload", this::handleReloadConfig)
            vertx.eventBus().consumer<JsonObject>("apix.dbless.config.backup.list", this::handleListBackups)
            vertx.eventBus().consumer<JsonObject>("apix.dbless.config.backup.restore", this::handleRestoreBackup)
            vertx.eventBus().consumer<JsonObject>("apix.dbless.config.update", this::handlePartialUpdate)
            logger.info("Registered DB-less configuration event bus handlers")
        } catch (e: Exception) {
            // 即使注册失败，也不影响整体启动
            if (e is java.util.concurrent.RejectedExecutionException) {
                logger.warn("RejectedExecutionException when registering event bus handlers, but will continue: {}", e.message)
            } else {
                logger.error("Failed to register event bus handlers", e)
                throw e
            }
        }
    }

    override fun onStart(startPromise: Promise<Void>) {
        logger.info("Starting DBlessVerticle...")

        try {
            // Get configuration from ConfigVerticle
            vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
                if (ar.succeeded()) {
                    val configResponse = ar.result().body()
                    if (configResponse.getBoolean("success", false)) {
                        val config = configResponse.getJsonObject("result", JsonObject())

                        // Check if DB-less mode is enabled
                        val dblessConfig = config.getJsonObject("dbless", JsonObject())
                        dblessModeEnabled = dblessConfig.getBoolean("enabled", false)

                        if (dblessModeEnabled) {
                            try {
                                // Initialize DB-less configuration manager
                                dblessConfigManager = DBlessConfigManager.getInstance(vertx)

                                // Initialize with configuration
                                dblessConfigManager.initialize(dblessConfig)
                                    .onSuccess {
                                        logger.info("DBlessVerticle started successfully")
                                        startPromise.complete()
                                    }
                                    .onFailure { cause ->
                                        // 即使初始化失败，也不影响整体启动
                                        if (cause is java.util.concurrent.RejectedExecutionException) {
                                            logger.warn("RejectedExecutionException during initialization, but will continue: {}", cause.message)
                                            startPromise.complete()
                                        } else {
                                            logger.error("Failed to initialize DB-less configuration manager", cause)
                                            startPromise.fail(cause)
                                        }
                                    }
                            } catch (e: Exception) {
                                // 即使初始化失败，也不影响整体启动
                                if (e is java.util.concurrent.RejectedExecutionException) {
                                    logger.warn("RejectedExecutionException during initialization, but will continue: {}", e.message)
                                    startPromise.complete()
                                } else {
                                    logger.error("Failed to initialize DB-less configuration manager", e)
                                    startPromise.fail(e)
                                }
                            }
                        } else {
                            logger.info("DB-less mode is not enabled, skipping initialization")
                            startPromise.complete()
                        }
                    } else {
                        val errorMsg = "Failed to get configuration: ${configResponse.getString("message", "Unknown error")}"
                        logger.error(errorMsg)
                        startPromise.fail(errorMsg)
                    }
                } else {
                    // 即使获取配置失败，也不影响整体启动
                    if (ar.cause() is java.util.concurrent.RejectedExecutionException) {
                        logger.warn("RejectedExecutionException when getting configuration, but will continue: {}", ar.cause().message)
                        startPromise.complete()
                    } else {
                        logger.error("Failed to get configuration", ar.cause())
                        startPromise.fail(ar.cause())
                    }
                }
            }
        } catch (e: Exception) {
            // 即使出现异常，也不影响整体启动
            if (e is java.util.concurrent.RejectedExecutionException) {
                logger.warn("RejectedExecutionException during startup, but will continue: {}", e.message)
                startPromise.complete()
            } else {
                logger.error("Unexpected error during DBlessVerticle startup", e)
                startPromise.fail(e)
            }
        }
    }

    /**
     * Handle get configuration request.
     */
    private fun handleGetConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!dblessModeEnabled || !::dblessConfigManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "DB-less mode is not enabled or not initialized"))
            return
        }

        val request = message.body()
        val section = request.getString("section")

        val config = if (section != null) {
            dblessConfigManager.getConfigSection(section)
        } else {
            dblessConfigManager.getConfig()
        }

        val response = JsonObject()
            .put("success", true)
            .put("result", config)
            .put("version", dblessConfigManager.getConfigVersion())

        message.reply(response)
    }

    /**
     * Handle save configuration request.
     */
    private fun handleSaveConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!dblessModeEnabled || !::dblessConfigManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "DB-less mode is not enabled or not initialized"))
            return
        }

        val request = message.body()
        val config = request.getJsonObject("config")

        if (config == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "No configuration provided"))
            return
        }

        dblessConfigManager.saveConfig(config)
            .onSuccess {
                val response = JsonObject()
                    .put("success", true)
                    .put("version", dblessConfigManager.getConfigVersion())

                message.reply(response)
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Failed to save configuration: ${cause.message}"))
            }
    }

    /**
     * Handle reload configuration request.
     */
    private fun handleReloadConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!dblessModeEnabled || !::dblessConfigManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "DB-less mode is not enabled or not initialized"))
            return
        }

        dblessConfigManager.loadConfig()
            .onSuccess {
                val response = JsonObject()
                    .put("success", true)
                    .put("version", dblessConfigManager.getConfigVersion())

                message.reply(response)
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Failed to reload configuration: ${cause.message}"))
            }
    }

    /**
     * Handle list backups request.
     */
    private fun handleListBackups(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!dblessModeEnabled || !::dblessConfigManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "DB-less mode is not enabled or not initialized"))
            return
        }

        dblessConfigManager.listBackupVersions()
            .onSuccess { versions ->
                val response = JsonObject()
                    .put("success", true)
                    .put("result", versions)

                message.reply(response)
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Failed to list backup versions: ${cause.message}"))
            }
    }

    /**
     * Handle restore backup request.
     */
    private fun handleRestoreBackup(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!dblessModeEnabled || !::dblessConfigManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "DB-less mode is not enabled or not initialized"))
            return
        }

        val request = message.body()
        val version = request.getLong("version")

        if (version == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "No version provided"))
            return
        }

        dblessConfigManager.restoreFromBackup(version)
            .onSuccess {
                val response = JsonObject()
                    .put("success", true)
                    .put("version", dblessConfigManager.getConfigVersion())

                message.reply(response)
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Failed to restore from backup: ${cause.message}"))
            }
    }

    /**
     * Handle partial update request.
     */
    private fun handlePartialUpdate(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!dblessModeEnabled || !::dblessConfigManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "DB-less mode is not enabled or not initialized"))
            return
        }

        val request = message.body()
        val update = request.getJsonObject("update")

        if (update == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "No update provided"))
            return
        }

        dblessConfigManager.applyPartialUpdate(update)
            .onSuccess {
                val response = JsonObject()
                    .put("success", true)
                    .put("version", dblessConfigManager.getConfigVersion())

                message.reply(response)
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Failed to apply partial update: ${cause.message}"))
            }
    }
}
