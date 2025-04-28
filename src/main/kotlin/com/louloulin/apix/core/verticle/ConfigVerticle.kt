package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject

/**
 * 负责配置管理的 Verticle
 */
class ConfigVerticle : BaseVerticle() {
    private lateinit var configManager: ConfigManager
    
    override fun registerEventBusHandlers() {
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET, this::handleGetConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_SET, this::handleSetConfig)
        vertx.eventBus().consumer<Void>(EventBusAddresses.CONFIG_RELOAD, this::handleReloadConfig)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        configManager = ConfigManager(vertx)
        configManager.loadConfig().onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("ConfigVerticle started successfully")
                startPromise.complete()
            } else {
                logger.error("Failed to load configuration", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }
    
    /**
     * 处理获取配置请求
     */
    private fun handleGetConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val key = message.body().getString("key")
        if (key != null) {
            // 获取特定配置项
            val value = configManager.getConfig().getValue(key)
            if (value != null) {
                sendSuccess(message, value)
            } else {
                sendError(message, 404, "Configuration key not found: $key")
            }
        } else {
            // 获取全部配置
            sendSuccess(message, configManager.getConfig())
        }
    }
    
    /**
     * 处理设置配置请求
     */
    private fun handleSetConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val key = message.body().getString("key")
        val value = message.body().getValue("value")
        
        if (key == null || value == null) {
            sendError(message, 400, "Both key and value are required")
            return
        }
        
        try {
            val config = configManager.getConfig()
            config.put(key, value)
            configManager.saveConfig(config).onComplete { ar ->
                if (ar.succeeded()) {
                    sendSuccess(message, true)
                } else {
                    sendError(message, ar.cause())
                }
            }
        } catch (e: Exception) {
            sendError(message, e)
        }
    }
    
    /**
     * 处理重新加载配置请求
     */
    private fun handleReloadConfig(message: io.vertx.core.eventbus.Message<Void>) {
        configManager.loadConfig().onComplete { ar ->
            if (ar.succeeded()) {
                sendSuccess(message, true)
            } else {
                sendError(message, ar.cause())
            }
        }
    }
}
