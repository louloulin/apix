package com.louloulin.apix.cluster

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Configuration for cluster support.
 */
class ClusterConfig(config: JsonObject = JsonObject()) {
    private val logger = LoggerFactory.getLogger(ClusterConfig::class.java)
    
    // Cluster type: NONE, ZOOKEEPER, HAZELCAST, INFINISPAN
    val type: String
    
    // Whether to enable cluster mode
    val enabled: Boolean
    
    // Whether to get configuration from cluster
    val getConfigFromCluster: Boolean
    
    // Cluster-specific configuration
    val clusterConfig: JsonObject
    
    // Synchronization configuration
    val syncConfig: JsonObject
    
    init {
        val clusterJson = config.getJsonObject("cluster", JsonObject())
        
        type = clusterJson.getString("type", "NONE").uppercase()
        enabled = type != "NONE"
        getConfigFromCluster = clusterJson.getBoolean("getConfigFromCluster", false)
        clusterConfig = clusterJson.getJsonObject("config", getDefaultClusterConfig(type))
        syncConfig = clusterJson.getJsonObject("sync", getDefaultSyncConfig())
        
        logger.info("Initialized cluster configuration: type=$type, enabled=$enabled")
    }
    
    /**
     * Get default cluster configuration based on type.
     */
    private fun getDefaultClusterConfig(type: String): JsonObject {
        return when (type.uppercase()) {
            "ZOOKEEPER" -> getDefaultZookeeperConfig()
            "HAZELCAST" -> getDefaultHazelcastConfig()
            "INFINISPAN" -> getDefaultInfinispanConfig()
            else -> JsonObject()
        }
    }
    
    /**
     * Get default Zookeeper configuration.
     */
    private fun getDefaultZookeeperConfig(): JsonObject {
        return JsonObject()
            .put("zookeeperHosts", "127.0.0.1")
            .put("sessionTimeout", 20000)
            .put("connectTimeout", 3000)
            .put("rootPath", "io.vertx")
            .put("retry", JsonObject()
                .put("initialSleepTime", 100)
                .put("intervalTimes", 10000)
                .put("maxTimes", 5)
            )
    }
    
    /**
     * Get default Hazelcast configuration.
     */
    private fun getDefaultHazelcastConfig(): JsonObject {
        return JsonObject()
            .put("hazelcastConfigPath", "config/hazelcast.xml")
    }
    
    /**
     * Get default Infinispan configuration.
     */
    private fun getDefaultInfinispanConfig(): JsonObject {
        return JsonObject()
            .put("infinispanConfigPath", "config/infinispan.xml")
    }
    
    /**
     * Get default synchronization configuration.
     */
    private fun getDefaultSyncConfig(): JsonObject {
        return JsonObject()
            .put("syncInterval", 5000) // 5 seconds
            .put("configPath", "/apix/config")
            .put("routesPath", "/apix/routes")
            .put("servicesPath", "/apix/services")
            .put("pluginsPath", "/apix/plugins")
    }
    
    /**
     * Convert to JSON.
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("type", type)
            .put("enabled", enabled)
            .put("getConfigFromCluster", getConfigFromCluster)
            .put("config", clusterConfig)
            .put("sync", syncConfig)
    }
    
    companion object {
        // Cluster types
        const val TYPE_NONE = "NONE"
        const val TYPE_ZOOKEEPER = "ZOOKEEPER"
        const val TYPE_HAZELCAST = "HAZELCAST"
        const val TYPE_INFINISPAN = "INFINISPAN"
        
        /**
         * Create from JSON.
         */
        fun fromJson(json: JsonObject): ClusterConfig {
            return ClusterConfig(json)
        }
    }
}
