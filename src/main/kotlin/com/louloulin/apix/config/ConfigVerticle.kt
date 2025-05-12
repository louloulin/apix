package com.louloulin.apix.config

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * ConfigVerticle 负责管理系统配置，提供配置的加载、保存和更新功能。
 * 它使用 ConfigManager 来处理配置文件的读写操作。
 */
class ConfigVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(ConfigVerticle::class.java)
    private lateinit var configManager: ConfigManager
    
    override fun start(startPromise: Promise<Void>) {
        try {
            logger.info("Starting ConfigVerticle...")
            
            // 创建配置管理器
            configManager = ConfigManager(vertx)
            
            // 设置事件总线处理器
            setupEventBusHandlers()
            
            logger.info("ConfigVerticle started successfully")
            startPromise.complete()
        } catch (e: Exception) {
            logger.error("Error starting ConfigVerticle", e)
            startPromise.fail(e)
        }
    }
    
    /**
     * 设置事件总线处理器
     */
    private fun setupEventBusHandlers() {
        // 获取配置
        vertx.eventBus().consumer<JsonObject>("apix.config.get") { message ->
            try {
                val response = JsonObject()
                    .put("success", true)
                    .put("result", configManager.getConfig())
                
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
        vertx.eventBus().consumer<JsonObject>("apix.config.update") { message ->
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
                
                // 更新配置
                configManager.saveConfig(newConfig)
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
        
        // 重新加载配置
        vertx.eventBus().consumer<JsonObject>("apix.config.reload") { message ->
            try {
                configManager.loadConfig()
                    .onSuccess { reloadedConfig ->
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", reloadedConfig)
                        )
                    }
                    .onFailure { cause ->
                        logger.error("Failed to reload config", cause)
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", cause.message)
                        )
                    }
            } catch (e: Exception) {
                logger.error("Error handling config.reload request", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }
        
        // 手动触发配置重新加载
        vertx.eventBus().consumer<JsonObject>("apix.config.manual-reload") { message ->
            try {
                configManager.manualReload()
                    .onSuccess { reloadedConfig ->
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", reloadedConfig)
                        )
                    }
                    .onFailure { cause ->
                        logger.error("Failed to manually reload config", cause)
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", cause.message)
                        )
                    }
            } catch (e: Exception) {
                logger.error("Error handling config.manual-reload request", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }
    }
}
