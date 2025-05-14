package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import org.slf4j.LoggerFactory

/**
 * Factory for creating PIIFilterPlugin instances.
 */
class PIIFilterPluginFactory : PluginFactory {
    private val logger = LoggerFactory.getLogger(PIIFilterPluginFactory::class.java)
    
    override fun create(config: PluginConfig): Plugin {
        logger.info("Creating PIIFilterPlugin with ID: {}", config.id)
        return PIIFilterPlugin(config.id, config)
    }
}
