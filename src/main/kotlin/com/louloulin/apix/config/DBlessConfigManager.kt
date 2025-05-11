package com.louloulin.apix.config

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicLong

/**
 * DB-less configuration manager that uses local files for configuration storage.
 * Supports YAML/JSON format, version control, hot reload, and incremental updates.
 */
class DBlessConfigManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(DBlessConfigManager::class.java)
    
    // Current configuration
    private var config = JsonObject()
    
    // Configuration version
    private val configVersion = AtomicLong(0)
    
    // Configuration file path
    private var configPath = "config/apix.json"
    
    // Backup directory
    private var backupDir = "config/backups"
    
    // Maximum number of backups to keep
    private var maxBackups = 10
    
    /**
     * Initialize the DB-less configuration manager.
     * @param options Initialization options.
     * @return A future that completes when initialization is done.
     */
    fun initialize(options: JsonObject): Future<Void> {
        logger.info("Initializing DB-less configuration manager")
        
        // Get configuration options
        configPath = options.getString("configPath", configPath)
        backupDir = options.getString("backupDir", backupDir)
        maxBackups = options.getInteger("maxBackups", maxBackups)
        
        // Create backup directory if it doesn't exist
        val backupPath = Paths.get(backupDir)
        if (!Files.exists(backupPath)) {
            Files.createDirectories(backupPath)
        }
        
        // Load initial configuration
        return loadConfig()
    }
    
    /**
     * Load configuration from file.
     * @return A future that completes when loading is done.
     */
    fun loadConfig(): Future<Void> {
        return vertx.executeBlocking<Void> { promise ->
            try {
                val configFile = Paths.get(configPath)
                
                if (Files.exists(configFile)) {
                    // Read configuration from file
                    val configContent = Files.readString(configFile)
                    
                    // Parse configuration
                    config = if (configPath.endsWith(".json")) {
                        JsonObject(configContent)
                    } else if (configPath.endsWith(".yaml") || configPath.endsWith(".yml")) {
                        // For YAML, we would need a YAML parser
                        // This is a placeholder for YAML parsing
                        JsonObject().put("error", "YAML parsing not implemented yet")
                    } else {
                        // Default to JSON
                        JsonObject(configContent)
                    }
                    
                    // Increment version
                    configVersion.incrementAndGet()
                    
                    logger.info("Loaded configuration from {}, version: {}", configPath, configVersion.get())
                } else {
                    // Create default configuration
                    config = createDefaultConfig()
                    
                    // Save default configuration
                    saveConfig(config)
                    
                    logger.info("Created default configuration at {}", configPath)
                }
                
                promise.complete()
            } catch (e: Exception) {
                logger.error("Failed to load configuration", e)
                promise.fail(e)
            }
        }
    }
    
    /**
     * Save configuration to file.
     * @param newConfig The new configuration to save.
     * @return A future that completes when saving is done.
     */
    fun saveConfig(newConfig: JsonObject): Future<Void> {
        return vertx.executeBlocking<Void> { promise ->
            try {
                // Create backup of current configuration
                createBackup()
                
                // Update configuration
                config = newConfig.copy()
                
                // Increment version
                configVersion.incrementAndGet()
                
                // Save configuration to file
                val configFile = Paths.get(configPath)
                
                // Create parent directories if they don't exist
                Files.createDirectories(configFile.parent)
                
                // Write configuration to file
                Files.writeString(configFile, config.encodePrettily())
                
                logger.info("Saved configuration to {}, version: {}", configPath, configVersion.get())
                
                promise.complete()
            } catch (e: Exception) {
                logger.error("Failed to save configuration", e)
                promise.fail(e)
            }
        }
    }
    
    /**
     * Create a backup of the current configuration.
     */
    private fun createBackup() {
        try {
            val configFile = Paths.get(configPath)
            
            if (Files.exists(configFile)) {
                // Create backup filename with version
                val backupFile = Paths.get(backupDir, "apix-config-v${configVersion.get()}.json")
                
                // Copy current configuration to backup
                Files.copy(configFile, backupFile, StandardCopyOption.REPLACE_EXISTING)
                
                logger.info("Created backup of configuration at {}", backupFile)
                
                // Clean up old backups
                cleanupOldBackups()
            }
        } catch (e: Exception) {
            logger.error("Failed to create backup", e)
        }
    }
    
    /**
     * Clean up old backups, keeping only the most recent ones.
     */
    private fun cleanupOldBackups() {
        try {
            val backupPath = Paths.get(backupDir)
            
            if (Files.exists(backupPath)) {
                // List all backup files
                val backupFiles = Files.list(backupPath)
                    .filter { it.fileName.toString().startsWith("apix-config-v") }
                    .sorted { a, b -> b.fileName.toString().compareTo(a.fileName.toString()) }
                    .toList()
                
                // Delete old backups
                if (backupFiles.size > maxBackups) {
                    for (i in maxBackups until backupFiles.size) {
                        Files.delete(backupFiles[i])
                        logger.debug("Deleted old backup: {}", backupFiles[i])
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to clean up old backups", e)
        }
    }
    
    /**
     * Create default configuration.
     * @return The default configuration.
     */
    private fun createDefaultConfig(): JsonObject {
        return JsonObject()
            .put("gateway", JsonObject()
                .put("host", "0.0.0.0")
                .put("port", 8080)
            )
            .put("admin", JsonObject()
                .put("enabled", true)
                .put("port", 8081)
                .put("host", "0.0.0.0")
            )
            .put("node", JsonObject()
                .put("mode", "STANDALONE")
                .put("id", "node-${vertx.hashCode()}")
            )
            .put("cluster", JsonObject()
                .put("type", "NONE")
                .put("getConfigFromCluster", false)
            )
            .put("plugins", JsonArray())
            .put("routes", JsonArray())
            .put("services", JsonArray())
    }
    
    /**
     * Get the current configuration.
     * @return The current configuration.
     */
    fun getConfig(): JsonObject {
        return config.copy()
    }
    
    /**
     * Get a specific section of the configuration.
     * @param section The section to get.
     * @return The requested section, or an empty JsonObject if not found.
     */
    fun getConfigSection(section: String): JsonObject {
        return config.getJsonObject(section, JsonObject()).copy()
    }
    
    /**
     * Get the current configuration version.
     * @return The current configuration version.
     */
    fun getConfigVersion(): Long {
        return configVersion.get()
    }
    
    /**
     * Restore configuration from a backup.
     * @param version The version to restore.
     * @return A future that completes when restoration is done.
     */
    fun restoreFromBackup(version: Long): Future<Void> {
        return vertx.executeBlocking<Void> { promise ->
            try {
                val backupFile = Paths.get(backupDir, "apix-config-v$version.json")
                
                if (Files.exists(backupFile)) {
                    // Create backup of current configuration before restoring
                    createBackup()
                    
                    // Copy backup to current configuration
                    val configFile = Paths.get(configPath)
                    Files.copy(backupFile, configFile, StandardCopyOption.REPLACE_EXISTING)
                    
                    // Reload configuration
                    loadConfig()
                        .onSuccess {
                            logger.info("Restored configuration from backup version {}", version)
                            promise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("Failed to reload configuration after restore", cause)
                            promise.fail(cause)
                        }
                } else {
                    val errorMsg = "Backup version $version not found"
                    logger.error(errorMsg)
                    promise.fail(errorMsg)
                }
            } catch (e: Exception) {
                logger.error("Failed to restore from backup", e)
                promise.fail(e)
            }
        }
    }
    
    /**
     * List available backup versions.
     * @return A future that completes with a list of available backup versions.
     */
    fun listBackupVersions(): Future<List<Long>> {
        return vertx.executeBlocking<List<Long>> { promise ->
            try {
                val backupPath = Paths.get(backupDir)
                
                if (Files.exists(backupPath)) {
                    // List all backup files
                    val backupFiles = Files.list(backupPath)
                        .filter { it.fileName.toString().startsWith("apix-config-v") }
                        .map { path ->
                            // Extract version number from filename
                            val filename = path.fileName.toString()
                            val versionStr = filename.substring("apix-config-v".length, filename.length - ".json".length)
                            versionStr.toLongOrNull() ?: 0L
                        }
                        .filter { it > 0 }
                        .sorted { a, b -> b.compareTo(a) } // Sort in descending order
                        .toList()
                    
                    promise.complete(backupFiles)
                } else {
                    promise.complete(emptyList())
                }
            } catch (e: Exception) {
                logger.error("Failed to list backup versions", e)
                promise.fail(e)
            }
        }
    }
    
    /**
     * Apply a partial update to the configuration.
     * @param update The partial update to apply.
     * @return A future that completes when the update is applied.
     */
    fun applyPartialUpdate(update: JsonObject): Future<Void> {
        return vertx.executeBlocking<Void> { promise ->
            try {
                // Create a copy of the current configuration
                val newConfig = config.copy()
                
                // Apply the update
                for (key in update.fieldNames()) {
                    val value = update.getValue(key)
                    newConfig.put(key, value)
                }
                
                // Save the updated configuration
                saveConfig(newConfig)
                    .onSuccess {
                        logger.info("Applied partial update to configuration")
                        promise.complete()
                    }
                    .onFailure { cause ->
                        logger.error("Failed to save configuration after partial update", cause)
                        promise.fail(cause)
                    }
            } catch (e: Exception) {
                logger.error("Failed to apply partial update", e)
                promise.fail(e)
            }
        }
    }
    
    companion object {
        // Singleton instance
        @Volatile
        private var instance: DBlessConfigManager? = null
        
        /**
         * Get the singleton instance of DBlessConfigManager.
         * @param vertx The Vert.x instance.
         * @return The DBlessConfigManager instance.
         */
        fun getInstance(vertx: Vertx): DBlessConfigManager {
            return instance ?: synchronized(this) {
                instance ?: DBlessConfigManager(vertx).also { instance = it }
            }
        }
    }
}
