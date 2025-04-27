package com.louloulin.apix.plugins.security

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory

/**
 * Factory for creating rate limit plugins.
 */
class RateLimitPluginFactory : PluginFactory {
    override fun create(config: PluginConfig): Plugin {
        return RateLimitPlugin(config.id, config)
    }
}
