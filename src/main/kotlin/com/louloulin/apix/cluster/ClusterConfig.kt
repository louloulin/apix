package com.louloulin.apix.cluster

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Configuration for cluster support.
 */
class ClusterConfig(config: JsonObject = JsonObject()) {
    private val logger = LoggerFactory.getLogger(ClusterConfig::class.java)

    // Cluster type: NONE, ZOOKEEPER, HAZELCAST, INFINISPAN, OPTIMIZED
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
            "OPTIMIZED" -> getDefaultOptimizedConfig()
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
     * Get default Optimized configuration.
     */
    private fun getDefaultOptimizedConfig(): JsonObject {
        return JsonObject()
            .put("gossip", JsonObject()
                .put("port", 8001)
                .put("gossipInterval", 1000)
                .put("cleanupInterval", 10000)
                .put("suspectTimeout", 5000)
                .put("deadTimeout", 60000)
                .put("fanout", 3)
            )
    }

    /**
     * Get default synchronization configuration.
     */
    private fun getDefaultSyncConfig(): JsonObject {
        return JsonObject()
            .put("syncInterval", 5000) // 5 seconds
            .put("maxChangeLogSize", 1000)
            .put("changeLogRetentionTime", 86400000) // 1 day
            .put("dataTypes", JsonObject()
                .put("config", JsonObject()
                    .put("syncPriority", 0)
                )
                .put("routes", JsonObject()
                    .put("syncPriority", 1)
                )
                .put("services", JsonObject()
                    .put("syncPriority", 2)
                )
                .put("plugins", JsonObject()
                    .put("syncPriority", 3)
                )
            )
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
        const val TYPE_OPTIMIZED = "OPTIMIZED"

        /**
         * Create from JSON.
         */
        fun fromJson(json: JsonObject): ClusterConfig {
            return ClusterConfig(json)
        }
    }
}
