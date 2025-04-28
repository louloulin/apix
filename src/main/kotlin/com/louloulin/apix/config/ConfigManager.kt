package com.louloulin.apix.config

import io.vertx.config.ConfigRetriever
import io.vertx.config.ConfigRetrieverOptions
import io.vertx.config.ConfigStoreOptions
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Manages the configuration for the gateway.
 */
class ConfigManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ConfigManager::class.java)
    private var config: JsonObject = JsonObject()

    init {
        // Load default configuration
        loadDefaultConfig()

        // Set up config retriever for dynamic configuration updates
        setupConfigRetriever()
    }

    /**
     * Loads the default configuration.
     */
    private fun loadDefaultConfig() {
        logger.info("Loading default configuration...")

        // Default configuration values
        config = JsonObject()
            .put("gateway", JsonObject()
                .put("host", "0.0.0.0")
                .put("port", 8080)
            )
            .put("admin", JsonObject()
                .put("enabled", true)
                .put("port", 8081)
                .put("host", "0.0.0.0")
            )
            .put("plugins", JsonArray())
            .put("routes", JsonArray())
            .put("services", JsonArray())

        // Try to load configuration from file
        val configPath = System.getProperty("apix.config.path", "config/apix.json")
        val configFile = Paths.get(configPath)

        if (Files.exists(configFile)) {
            try {
                val fileContent = Files.readString(configFile)
                val fileConfig = JsonObject(fileContent)

                // Merge file configuration with default configuration
                config = config.mergeIn(fileConfig, true)

                logger.info("Loaded configuration from file: {}", configPath)
            } catch (e: Exception) {
                logger.error("Failed to load configuration from file: {}", configPath, e)
            }
        } else {
            logger.info("Configuration file not found: {}, using default configuration", configPath)

            try {
                // Create parent directories if they don't exist
                Files.createDirectories(configFile.parent)

                // Save default configuration to file
                Files.writeString(configFile, config.encodePrettily())

                logger.info("Created default configuration file: {}", configPath)
            } catch (e: Exception) {
                logger.error("Failed to create default configuration file: {}", configPath, e)
            }
        }
    }

    /**
     * Sets up the config retriever for dynamic configuration updates.
     */
    private fun setupConfigRetriever() {
        logger.info("Setting up configuration retriever...")

        // Create config store options
        val fileStore = ConfigStoreOptions()
            .setType("file")
            .setFormat("json")
            .setConfig(JsonObject().put("path", System.getProperty("apix.config.path", "config/apix.json")))

        // Create config retriever
        val retrieverOptions = ConfigRetrieverOptions()
            .addStore(fileStore)
            .setScanPeriod(5000) // Check for changes every 5 seconds

        val retriever = ConfigRetriever.create(vertx, retrieverOptions)

        // Set up config change listener
        retriever.listen { change ->
            logger.info("Configuration changed")

            // Update configuration
            config = change.newConfiguration

            // Notify listeners
            // In a real implementation, we would notify components that depend on configuration
        }
    }

    /**
     * Gets the gateway host.
     */
    fun getGatewayHost(): String {
        return config.getJsonObject("gateway", JsonObject()).getString("host", "0.0.0.0")
    }

    /**
     * Gets the gateway port.
     */
    fun getGatewayPort(): Int {
        return config.getJsonObject("gateway", JsonObject()).getInteger("port", 8080)
    }

    /**
     * Gets the admin API host.
     */
    fun getAdminHost(): String {
        return config.getJsonObject("admin", JsonObject()).getString("host", "0.0.0.0")
    }

    /**
     * Gets the admin API port.
     */
    fun getAdminPort(): Int {
        return config.getJsonObject("admin", JsonObject()).getInteger("port", 8081)
    }

    /**
     * Gets the plugins configuration.
     */
    fun getPluginsConfig(): JsonArray {
        return config.getJsonArray("plugins", JsonArray())
    }

    /**
     * Gets the routes configuration.
     */
    fun getRoutesConfig(): JsonArray {
        return config.getJsonArray("routes", JsonArray())
    }

    /**
     * Gets the services configuration.
     */
    fun getServicesConfig(): JsonArray {
        return config.getJsonArray("services", JsonArray())
    }

    /**
     * Gets the entire configuration.
     */
    fun getConfig(): JsonObject {
        return config.copy()
    }

    /**
     * Updates the configuration.
     */
    fun updateConfig(newConfig: JsonObject) {
        // Merge new configuration with existing configuration
        config = config.mergeIn(newConfig, true)

        // Save configuration to file
        saveConfig()
    }

    /**
     * Saves the configuration to file.
     */
    private fun saveConfig() {
        val configPath = System.getProperty("apix.config.path", "config/apix.json")
        val configFile = Paths.get(configPath)

        try {
            // Create parent directories if they don't exist
            Files.createDirectories(configFile.parent)

            // Write configuration to file
            Files.writeString(configFile, config.encodePrettily())

            logger.info("Saved configuration to file: {}", configPath)
        } catch (e: Exception) {
            logger.error("Failed to save configuration to file: {}", configPath, e)
        }
    }

    /**
     * Saves the configuration to file asynchronously.
     */
    fun saveConfig(configToSave: JsonObject): io.vertx.core.Future<Void> {
        return vertx.executeBlocking<Void> { promise ->
            try {
                // Update the configuration
                config = configToSave.copy()

                // Save to file
                val configPath = System.getProperty("apix.config.path", "config/apix.json")
                val configFile = Paths.get(configPath)

                // Create parent directories if they don't exist
                Files.createDirectories(configFile.parent)

                // Write configuration to file
                Files.writeString(configFile, config.encodePrettily())

                logger.info("Saved configuration to file: {}", configPath)
                promise.complete()
            } catch (e: Exception) {
                logger.error("Failed to save configuration to file", e)
                promise.fail(e)
            }
        }
    }

    /**
     * Loads the configuration from file asynchronously.
     */
    fun loadConfig(): io.vertx.core.Future<JsonObject> {
        return vertx.executeBlocking { promise ->
            try {
                loadDefaultConfig()
                promise.complete(config)
            } catch (e: Exception) {
                logger.error("Failed to load configuration", e)
                promise.fail(e)
            }
        }
    }
}
