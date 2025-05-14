package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import org.slf4j.LoggerFactory

/**
 * Factory for creating CostTrackingPlugin instances.
 */
class CostTrackingPluginFactory : PluginFactory {
    private val logger = LoggerFactory.getLogger(CostTrackingPluginFactory::class.java)

    override fun create(config: PluginConfig): Plugin {
        logger.info("Creating CostTrackingPlugin with ID: {}", config.id)
        return CostTrackingPlugin(config.id, config)
    }
}
