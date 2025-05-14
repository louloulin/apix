package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import org.slf4j.LoggerFactory

/**
 * Factory for creating AIMetricsPlugin instances.
 */
class AIMetricsPluginFactory : PluginFactory {
    private val logger = LoggerFactory.getLogger(AIMetricsPluginFactory::class.java)
    
    override fun create(config: PluginConfig): Plugin {
        logger.info("Creating AIMetricsPlugin with ID: {}", config.id)
        return AIMetricsPlugin(config.id, config)
    }
}
