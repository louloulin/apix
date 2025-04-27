package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import com.louloulin.apix.plugins.auth.ApiKeyPluginFactory
import com.louloulin.apix.plugins.security.RateLimitPluginFactory
import com.louloulin.apix.plugins.ai.PromptValidatorPluginFactory
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the lifecycle and execution of plugins in the gateway.
 */
class PluginManager(
    private val vertx: Vertx,
    private val configManager: ConfigManager
) {
    private val logger = LoggerFactory.getLogger(PluginManager::class.java)
    private val plugins = ConcurrentHashMap<String, Plugin>()
    private val pluginFactories = ConcurrentHashMap<String, PluginFactory>()

    init {
        // Register built-in plugin factories
        registerBuiltInPlugins()

        // Load plugins from configuration
        loadPluginsFromConfig()
    }

    /**
     * Registers all built-in plugin factories.
     */
    private fun registerBuiltInPlugins() {
        logger.info("Registering built-in plugins...")

        // Register authentication plugins
        pluginFactories["api-key"] = ApiKeyPluginFactory()

        // Register security plugins
        pluginFactories["rate-limiter"] = RateLimitPluginFactory()

        // Register AI-specific plugins
        pluginFactories["prompt-validator"] = PromptValidatorPluginFactory()

        logger.info("Registered {} built-in plugin factories", pluginFactories.size)
    }

    /**
     * Loads and initializes plugins from configuration.
     */
    private fun loadPluginsFromConfig() {
        logger.info("Loading plugins from configuration...")

        try {
            val pluginsConfig = configManager.getPluginsConfig()

            pluginsConfig.forEach { configObj ->
                val pluginConfig = configObj as JsonObject
                val pluginType = pluginConfig.getString("type")
                val pluginId = pluginConfig.getString("id")

                if (pluginType != null && pluginId != null) {
                    val factory = pluginFactories[pluginType]

                    if (factory != null) {
                        try {
                            val plugin = factory.create(PluginConfig(pluginId, pluginConfig))
                            plugins[pluginId] = plugin
                            logger.info("Loaded plugin: {}", pluginId)
                        } catch (e: Exception) {
                            logger.error("Failed to create plugin: {}", pluginId, e)
                        }
                    } else {
                        logger.warn("Unknown plugin type: {}", pluginType)
                    }
                } else {
                    logger.warn("Invalid plugin configuration: {}", pluginConfig.encode())
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading plugins from configuration", e)
        }
    }

    /**
     * Registers a custom plugin factory.
     */
    fun registerPluginFactory(type: String, factory: PluginFactory) {
        pluginFactories[type] = factory
        logger.info("Registered plugin factory for type: {}", type)
    }

    /**
     * Gets a plugin by its ID.
     */
    fun getPlugin(id: String): Plugin? {
        return plugins[id]
    }

    /**
     * Gets all registered plugins.
     */
    fun getAllPlugins(): Collection<Plugin> {
        return plugins.values
    }

    /**
     * Creates a plugin chain for the given plugin IDs.
     */
    fun createPluginChain(pluginIds: List<String>): PluginChain {
        val chainPlugins = pluginIds.mapNotNull { plugins[it] }
        return PluginChain(chainPlugins)
    }

    /**
     * Shuts down all plugins.
     */
    fun shutdown() {
        logger.info("Shutting down plugins...")
        plugins.values.forEach { plugin ->
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                logger.error("Error shutting down plugin: {}", plugin.id, e)
            }
        }
        plugins.clear()
    }
}
