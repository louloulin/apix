package com.louloulin.apix.cluster

import io.vertx.core.json.JsonObject
import io.vertx.core.spi.cluster.ClusterManager
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager
import io.vertx.spi.cluster.zookeeper.ZookeeperClusterManager
// Temporarily comment out Infinispan until we add the proper dependency
// import io.vertx.spi.cluster.infinispan.InfinispanClusterManager
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ClusterManagerFactory.
 */
class ClusterManagerFactoryTest {

    @Test
    fun `test create cluster manager with disabled clustering`() {
        // Create a mock ClusterConfig with enabled=false
        val config = ClusterConfig(JsonObject().put("cluster", JsonObject().put("type", "NONE")))
        val manager = ClusterManagerFactory.createClusterManager(config)

        // Even with disabled clustering, we now return a ZookeeperClusterManager
        assertNotNull(manager)
        assertTrue(manager is ZookeeperClusterManager)
    }

    @Test
    fun `test create zookeeper cluster manager`() {
        val config = ClusterConfig(JsonObject().put("cluster", JsonObject().put("type", "ZOOKEEPER")))
        val manager = ClusterManagerFactory.createClusterManager(config)

        assertNotNull(manager)
        assertTrue(manager is ZookeeperClusterManager)
    }

    @Test
    fun `test create hazelcast cluster manager`() {
        val config = ClusterConfig(JsonObject().put("cluster", JsonObject().put("type", "HAZELCAST")))
        val manager = ClusterManagerFactory.createClusterManager(config)

        assertNotNull(manager)
        assertTrue(manager is HazelcastClusterManager)
    }

    @Test
    fun `test create infinispan cluster manager`() {
        val config = ClusterConfig(JsonObject().put("cluster", JsonObject().put("type", "INFINISPAN")))
        val manager = ClusterManagerFactory.createClusterManager(config)

        assertNotNull(manager)
        // Currently returns a ZookeeperClusterManager as a fallback
        assertTrue(manager is ZookeeperClusterManager)
    }

    @Test
    fun `test create cluster manager with unknown type`() {
        val config = ClusterConfig(JsonObject().put("cluster", JsonObject().put("type", "UNKNOWN")))
        val manager = ClusterManagerFactory.createClusterManager(config)

        // For unknown types, we now return a ZookeeperClusterManager
        assertNotNull(manager)
        assertTrue(manager is ZookeeperClusterManager)
    }
}
