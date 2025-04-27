package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory

/**
 * Factory for creating prompt validator plugins.
 */
class PromptValidatorPluginFactory : PluginFactory {
    override fun create(config: PluginConfig): Plugin {
        return PromptValidatorPlugin(config.id, config)
    }
}
