package com.louloulin.apix.cluster

import io.vertx.core.json.JsonObject
import io.vertx.core.spi.cluster.ClusterManager
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager
import com.hazelcast.config.Config
import com.hazelcast.core.HazelcastInstance
// Temporarily comment out Infinispan until we add the proper dependency
// import io.vertx.spi.cluster.infinispan.InfinispanClusterManager
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Factory for creating Vert.x cluster managers.
 */
object ClusterManagerFactory {
    private val logger = LoggerFactory.getLogger(ClusterManagerFactory::class.java)

    /**
     * Create a cluster manager based on the provided configuration.
     */
    fun createClusterManager(clusterConfig: ClusterConfig): ClusterManager {
        if (!clusterConfig.enabled) {
            logger.info("Clustering is disabled")
            // Return a default ZookeeperClusterManager with empty config when clustering is disabled
            return ZookeeperClusterManager(JsonObject())
        }

        return when (clusterConfig.type) {
            ClusterConfig.TYPE_ZOOKEEPER -> createZookeeperClusterManager(clusterConfig.clusterConfig)
            ClusterConfig.TYPE_HAZELCAST -> createHazelcastClusterManager(clusterConfig.clusterConfig)
            ClusterConfig.TYPE_INFINISPAN -> createInfinispanClusterManager(clusterConfig.clusterConfig)
            else -> {
                logger.warn("Unknown cluster type: ${clusterConfig.type}, defaulting to Zookeeper")
                createZookeeperClusterManager(JsonObject())
            }
        }
    }

    /**
     * Create a Zookeeper cluster manager.
     */
    private fun createZookeeperClusterManager(config: JsonObject): ZookeeperClusterManager {
        logger.info("Creating Zookeeper cluster manager with config: $config")
        return ZookeeperClusterManager(config)
    }

    /**
     * Create a Hazelcast cluster manager.
     */
    private fun createHazelcastClusterManager(config: JsonObject): HazelcastClusterManager {
        val configPath = config.getString("hazelcastConfigPath", "config/hazelcast.xml")
        logger.info("Creating Hazelcast cluster manager with config path: $configPath")

        val configFile = File(configPath)
        return if (configFile.exists()) {
            // Create a Config object from the URL
            val hazelcastConfig = Config()
            hazelcastConfig.setConfigurationUrl(configFile.toURI().toURL())
            HazelcastClusterManager(hazelcastConfig)
        } else {
            logger.warn("Hazelcast config file not found at $configPath, using default configuration")
            HazelcastClusterManager()
        }
    }

    /**
     * Create an Infinispan cluster manager.
     */
    private fun createInfinispanClusterManager(config: JsonObject): ClusterManager {
        val configPath = config.getString("infinispanConfigPath", "config/infinispan.xml")
        logger.info("Creating Infinispan cluster manager with config path: $configPath")

        // Temporarily return a ZookeeperClusterManager until we add proper Infinispan support
        logger.warn("Infinispan cluster manager is not currently supported, using Zookeeper instead")
        return ZookeeperClusterManager(JsonObject())

        /* Commented out until we add proper Infinispan support
        val configFile = File(configPath)
        return if (configFile.exists()) {
            InfinispanClusterManager(configFile.toURI().toURL())
        } else {
            logger.warn("Infinispan config file not found at $configPath, using default configuration")
            InfinispanClusterManager()
        }
        */
    }
}
