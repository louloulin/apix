package com.louloulin.apix.plugins.auth

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory

/**
 * Factory for creating API key authentication plugins.
 */
class ApiKeyPluginFactory : PluginFactory {
    override fun create(config: PluginConfig): Plugin {
        return ApiKeyPlugin(config.id, config)
    }
}
