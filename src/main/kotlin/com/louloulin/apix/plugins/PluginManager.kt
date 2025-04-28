package com.louloulin.apix.plugins

import com.louloulin.apix.core.PluginChain
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 管理插件的类
 */
class PluginManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    private val plugins = mutableMapOf<String, Plugin>()

    /**
     * 注册插件
     */
    fun registerPlugin(plugin: Plugin) {
        logger.info("Registering plugin: {}", plugin.id)
        plugins[plugin.id] = plugin
    }

    /**
     * 获取插件
     */
    fun getPlugin(id: String): Plugin? {
        return plugins[id]
    }

    /**
     * 获取所有插件
     */
    fun getAllPlugins(): List<Plugin> {
        return plugins.values.toList()
    }

    /**
     * 创建插件链
     */
    fun createPluginChain(pluginIds: List<String>): PluginChain {
        val chainPlugins = pluginIds.mapNotNull { plugins[it] }
        return PluginChain(chainPlugins)
    }
}




