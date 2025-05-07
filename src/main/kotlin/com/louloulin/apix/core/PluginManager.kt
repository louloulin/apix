package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import com.louloulin.apix.plugins.auth.ApiKeyAuthPlugin
import com.louloulin.apix.plugins.auth.ApiKeyPluginFactory
import com.louloulin.apix.plugins.auth.BasicAuthPlugin
import com.louloulin.apix.plugins.auth.JwtAuthPlugin
import com.louloulin.apix.plugins.security.CsrfProtectionPlugin
import com.louloulin.apix.plugins.security.IpFilterPlugin
import com.louloulin.apix.plugins.security.RateLimitPluginFactory
import com.louloulin.apix.plugins.security.SignatureVerificationPlugin
import com.louloulin.apix.plugins.ai.PromptValidatorPluginFactory
import com.louloulin.apix.plugins.ai.ResponseCachePluginFactory
import com.louloulin.apix.plugins.ai.TokenUsagePluginFactory
import com.louloulin.apix.plugins.aggregation.RequestAggregationPlugin
import com.louloulin.apix.plugins.cache.RequestCachePlugin
import com.louloulin.apix.plugins.resilience.ResiliencePlugin
import com.louloulin.apix.plugins.transform.RequestTransformerPlugin
import com.louloulin.apix.plugins.transform.TransformPluginFactory
import com.louloulin.apix.plugins.logging.RequestLoggerPluginFactory
import com.louloulin.apix.plugins.logging.StructuredLoggerPlugin
import com.louloulin.apix.plugins.validation.RequestValidatorPlugin
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
        pluginFactories["apiKeyAuth"] = ApiKeyAuthPlugin.Factory()
        pluginFactories["jwtAuth"] = JwtAuthPlugin.Factory()
        pluginFactories["basicAuth"] = BasicAuthPlugin.Factory()

        // Register security plugins
        pluginFactories["rate-limiter"] = RateLimitPluginFactory()
        pluginFactories["ip-filter"] = IpFilterPlugin.Factory()
        pluginFactories["signatureVerification"] = SignatureVerificationPlugin.Factory()
        pluginFactories["csrfProtection"] = CsrfProtectionPlugin.Factory()

        // Register validation plugins
        pluginFactories["request-validator"] = RequestValidatorPlugin.Factory()
        pluginFactories["prompt-validator"] = PromptValidatorPluginFactory()

        // Register transformation plugins
        pluginFactories["transform"] = TransformPluginFactory()
        pluginFactories["requestTransformer"] = RequestTransformerPlugin.Factory()
        pluginFactories["requestAggregation"] = RequestAggregationPlugin.Factory()
        pluginFactories["requestCache"] = RequestCachePlugin.Factory()
        pluginFactories["resilience"] = ResiliencePlugin.Factory()

        // Register logging plugins
        pluginFactories["request-logger"] = RequestLoggerPluginFactory()
        pluginFactories["structured-logger"] = StructuredLoggerPlugin.Factory()

        // Register AI-specific plugins
        pluginFactories["prompt-validator"] = PromptValidatorPluginFactory()
        pluginFactories["response-cache"] = ResponseCachePluginFactory()
        pluginFactories["token-usage"] = TokenUsagePluginFactory()

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
                            val plugin = factory.create(PluginConfig(pluginId, pluginType, pluginConfig))
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
     * 创建给定插件ID的插件链。
     * 优化版本支持按优先级分组执行和并行执行。
     */
    fun createPluginChain(pluginIds: List<String>): PluginChain {
        val chainPlugins = pluginIds.mapNotNull { plugins[it] }
        return PluginChain(chainPlugins)
    }

    /**
     * 获取插件执行统计信息
     */
    fun getPluginExecutionStats(pluginChain: PluginChain): Map<String, Map<String, Long>> {
        return pluginChain.getExecutionStats()
    }

    /**
     * 清除插件执行结果缓存
     */
    fun clearPluginCache(pluginChain: PluginChain) {
        pluginChain.clearCache()
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
