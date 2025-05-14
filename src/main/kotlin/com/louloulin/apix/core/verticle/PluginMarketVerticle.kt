package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.plugins.PluginFactory
import com.louloulin.apix.plugins.ai.ModelSelectorPluginFactory
import com.louloulin.apix.plugins.ai.RequestTrackerPluginFactory
import com.louloulin.apix.plugins.market.PluginMarketManager
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 插件市场Verticle
 *
 * 该Verticle负责管理插件市场，包括插件的上传、下载、安装和卸载。
 */
class PluginMarketVerticle : BaseVerticle() {
    // 插件市场管理器
    private lateinit var pluginMarketManager: PluginMarketManager
    
    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动 PluginMarketVerticle...")
        
        // 创建插件市场管理器
        pluginMarketManager = PluginMarketManager(vertx)
        
        // 注册插件工厂
        registerPluginFactories()
        
        // 初始化插件市场管理器
        pluginMarketManager.initialize()
            .onSuccess {
                // 注册EventBus处理器
                registerEventBusHandlers()
                
                logger.info("PluginMarketVerticle 启动完成")
                startPromise.complete()
            }
            .onFailure { err ->
                logger.error("初始化插件市场管理器失败", err)
                startPromise.fail(err)
            }
    }
    
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止 PluginMarketVerticle...")
        
        // 关闭插件市场管理器
        pluginMarketManager.close()
            .onSuccess {
                logger.info("PluginMarketVerticle 停止完成")
                stopPromise.complete()
            }
            .onFailure { err ->
                logger.error("关闭插件市场管理器失败", err)
                stopPromise.fail(err)
            }
    }
    
    /**
     * 注册插件工厂
     */
    private fun registerPluginFactories() {
        // 注册AI特定插件工厂
        pluginMarketManager.registerPluginFactory("modelSelector", ModelSelectorPluginFactory())
        pluginMarketManager.registerPluginFactory("requestTracker", RequestTrackerPluginFactory())
        
        // 注册其他插件工厂
        // ...
        
        logger.info("注册了插件工厂")
    }
    
    override fun registerEventBusHandlers() {
        // 获取所有插件信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_GET_ALL) { message ->
            val plugins = pluginMarketManager.getAllPluginInfo()
            val pluginsArray = JsonArray()
            
            plugins.forEach { pluginInfo ->
                pluginsArray.add(pluginInfo.toJson())
            }
            
            sendSuccess(message, JsonObject()
                .put("plugins", pluginsArray)
            )
        }
        
        // 获取插件信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_GET) { message ->
            val pluginId = message.body().getString("id")
            if (pluginId == null) {
                sendError(message, 400, "缺少插件ID")
                return@consumer
            }
            
            val pluginInfo = pluginMarketManager.getPluginInfo(pluginId)
            if (pluginInfo == null) {
                sendError(message, 404, "插件不存在")
                return@consumer
            }
            
            sendSuccess(message, JsonObject()
                .put("plugin", pluginInfo.toJson())
            )
        }
        
        // 搜索插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_SEARCH) { message ->
            val query = message.body().getString("query", "")
            val tagsArray = message.body().getJsonArray("tags", JsonArray())
            val tags = (0 until tagsArray.size()).map { tagsArray.getString(it) }
            
            val plugins = pluginMarketManager.searchPlugins(query, tags)
            val pluginsArray = JsonArray()
            
            plugins.forEach { pluginInfo ->
                pluginsArray.add(pluginInfo.toJson())
            }
            
            sendSuccess(message, JsonObject()
                .put("plugins", pluginsArray)
            )
        }
        
        // 上传插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_UPLOAD) { message ->
            val pluginData = message.body().getBinary("data")
            val metadata = message.body().getJsonObject("metadata")
            
            if (pluginData == null || metadata == null) {
                sendError(message, 400, "缺少插件数据或元数据")
                return@consumer
            }
            
            pluginMarketManager.uploadPlugin(Buffer.buffer(pluginData), metadata)
                .onSuccess { pluginInfo ->
                    sendSuccess(message, JsonObject()
                        .put("plugin", pluginInfo.toJson())
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "上传插件失败")
                }
        }
        
        // 下载插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_DOWNLOAD) { message ->
            val pluginId = message.body().getString("id")
            if (pluginId == null) {
                sendError(message, 400, "缺少插件ID")
                return@consumer
            }
            
            pluginMarketManager.downloadPlugin(pluginId)
                .onSuccess { buffer ->
                    sendSuccess(message, JsonObject()
                        .put("data", buffer.bytes)
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "下载插件失败")
                }
        }
        
        // 安装插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_INSTALL) { message ->
            val pluginId = message.body().getString("id")
            if (pluginId == null) {
                sendError(message, 400, "缺少插件ID")
                return@consumer
            }
            
            pluginMarketManager.installPlugin(pluginId)
                .onSuccess { plugin ->
                    sendSuccess(message, JsonObject()
                        .put("id", plugin.id)
                        .put("type", plugin.type)
                        .put("message", "插件安装成功")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "安装插件失败")
                }
        }
        
        // 卸载插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_UNINSTALL) { message ->
            val pluginId = message.body().getString("id")
            if (pluginId == null) {
                sendError(message, 400, "缺少插件ID")
                return@consumer
            }
            
            pluginMarketManager.uninstallPlugin(pluginId)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "插件卸载成功")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "卸载插件失败")
                }
        }
        
        // 评价插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_RATE) { message ->
            val pluginId = message.body().getString("id")
            val rating = message.body().getDouble("rating")
            
            if (pluginId == null || rating == null) {
                sendError(message, 400, "缺少插件ID或评分")
                return@consumer
            }
            
            pluginMarketManager.ratePlugin(pluginId, rating)
                .onSuccess { pluginInfo ->
                    sendSuccess(message, JsonObject()
                        .put("plugin", pluginInfo.toJson())
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "评价插件失败")
                }
        }
        
        // 删除插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_DELETE) { message ->
            val pluginId = message.body().getString("id")
            if (pluginId == null) {
                sendError(message, 400, "缺少插件ID")
                return@consumer
            }
            
            pluginMarketManager.deletePlugin(pluginId)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "插件删除成功")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "删除插件失败")
                }
        }
        
        // 获取已安装的插件
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_MARKET_GET_INSTALLED) { message ->
            val plugins = pluginMarketManager.getAllInstalledPlugins()
            val pluginsArray = JsonArray()
            
            plugins.forEach { plugin ->
                pluginsArray.add(JsonObject()
                    .put("id", plugin.id)
                    .put("type", plugin.type)
                )
            }
            
            sendSuccess(message, JsonObject()
                .put("plugins", pluginsArray)
            )
        }
    }
}
