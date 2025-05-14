package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import org.slf4j.LoggerFactory

/**
 * Factory for creating PromptGuardPlugin instances.
 */
class PromptGuardPluginFactory : PluginFactory {
    private val logger = LoggerFactory.getLogger(PromptGuardPluginFactory::class.java)
    
    override fun create(config: PluginConfig): Plugin {
        logger.info("Creating PromptGuardPlugin with ID: {}", config.id)
        return PromptGuardPlugin(config.id, config)
    }
}
