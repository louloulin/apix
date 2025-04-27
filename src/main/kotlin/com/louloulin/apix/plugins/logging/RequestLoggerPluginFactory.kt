package com.louloulin.apix.plugins.logging

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory

/**
 * 请求日志插件的工厂类。
 */
class RequestLoggerPluginFactory : PluginFactory {
    override fun create(config: PluginConfig): Plugin {
        return RequestLoggerPlugin(config.id, config)
    }
}
