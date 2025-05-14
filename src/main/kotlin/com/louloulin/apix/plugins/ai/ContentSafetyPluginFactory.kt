package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import org.slf4j.LoggerFactory

/**
 * Factory for creating ContentSafetyPlugin instances.
 */
class ContentSafetyPluginFactory : PluginFactory {
    private val logger = LoggerFactory.getLogger(ContentSafetyPluginFactory::class.java)
    
    override fun create(config: PluginConfig): Plugin {
        logger.info("Creating ContentSafetyPlugin with ID: {}", config.id)
        return ContentSafetyPlugin(config.id, config)
    }
}
