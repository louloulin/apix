package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory

/**
 * 令牌使用跟踪插件的工厂类。
 */
class TokenUsagePluginFactory : PluginFactory {
    override fun create(config: PluginConfig): Plugin {
        return TokenUsagePlugin(config.id, config)
    }
}
