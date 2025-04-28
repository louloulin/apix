package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import io.vertx.core.Vertx

/**
 * 响应缓存插件的工厂类。
 */
class ResponseCachePluginFactory : PluginFactory {
    override fun create(config: PluginConfig): Plugin {
        return ResponseCachePlugin(config.id, config, Vertx.currentContext().owner())
    }
}
