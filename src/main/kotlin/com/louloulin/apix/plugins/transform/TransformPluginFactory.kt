package com.louloulin.apix.plugins.transform

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory

/**
 * 转换插件的工厂类。
 */
class TransformPluginFactory : PluginFactory {
    override fun create(config: PluginConfig): Plugin {
        return TransformPlugin(config.id, config)
    }
}
