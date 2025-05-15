package com.louloulin.apix.plugins.verticle

import com.louloulin.apix.core.verticle.BaseVerticle
import com.louloulin.apix.plugins.optimization.PluginSystemOptimization
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 插件系统Verticle
 * 负责管理插件系统优化
 */
class PluginSystemVerticle : BaseVerticle() {
    private val pluginLogger = LoggerFactory.getLogger(PluginSystemVerticle::class.java)
    
    // 插件系统优化
    private lateinit var pluginSystemOptimization: PluginSystemOptimization
    
    // 事件总线地址
    object EventBusAddresses {
        // 插件系统优化
        const val PLUGIN_SYSTEM_CONFIG_GET = "apix.plugins.system.config.get"
        const val PLUGIN_SYSTEM_STATUS_GET = "apix.plugins.system.status.get"
    }
    
    override fun registerEventBusHandlers() {
        // 插件系统优化
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_SYSTEM_CONFIG_GET, this::handleGetConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_SYSTEM_STATUS_GET, this::handleGetStatus)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        try {
            // 获取配置
            val config = config().getJsonObject("pluginSystem", JsonObject())
            
            // 创建插件系统优化
            pluginSystemOptimization = PluginSystemOptimization.getInstance(vertx, config)
            
            // 初始化插件系统优化
            pluginSystemOptimization.initialize()
            
            pluginLogger.info("PluginSystemVerticle started successfully")
            startPromise.complete()
        } catch (e: Exception) {
            pluginLogger.error("Error starting PluginSystemVerticle", e)
            startPromise.fail(e)
        }
    }
    
    override fun onStop(stopPromise: Promise<Void>) {
        try {
            pluginLogger.info("PluginSystemVerticle stopped successfully")
            stopPromise.complete()
        } catch (e: Exception) {
            pluginLogger.error("Error stopping PluginSystemVerticle", e)
            stopPromise.fail(e)
        }
    }
    
    /**
     * 处理获取配置请求
     */
    private fun handleGetConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            // 获取配置
            val config = pluginSystemOptimization.getConfig()
            
            replyWithSuccess(message, config)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }
    
    /**
     * 处理获取状态请求
     */
    private fun handleGetStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            // 获取状态
            val status = pluginSystemOptimization.getStatus()
            
            replyWithSuccess(message, status)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }
    
    /**
     * 发送成功响应
     */
    private fun replyWithSuccess(message: io.vertx.core.eventbus.Message<JsonObject>, result: Any?, statusCode: Int = 200) {
        val response = JsonObject()
            .put("success", true)
            .put("statusCode", statusCode)
        
        if (result != null) {
            response.put("result", result)
        }
        
        message.reply(response)
    }
    
    /**
     * 发送错误响应
     */
    private fun replyWithError(message: io.vertx.core.eventbus.Message<JsonObject>, cause: Throwable) {
        val response = JsonObject()
            .put("success", false)
            .put("statusCode", 500)
            .put("message", cause.message)
        
        message.reply(response)
    }
    
    /**
     * 发送错误响应
     */
    private fun replyWithError(message: io.vertx.core.eventbus.Message<JsonObject>, statusCode: Int, errorMessage: String) {
        val response = JsonObject()
            .put("success", false)
            .put("statusCode", statusCode)
            .put("message", errorMessage)
        
        message.reply(response)
    }
}
