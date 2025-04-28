package com.louloulin.apix.cluster

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for ClusterConfig class.
 */
class ClusterConfigTest {
    
    @Test
    fun `test default configuration`() {
        val config = ClusterConfig()
        
        assertEquals("NONE", config.type)
        assertFalse(config.enabled)
        assertFalse(config.getConfigFromCluster)
        assertTrue(config.clusterConfig.isEmpty)
        assertTrue(config.syncConfig.getString("configPath").isNotEmpty())
    }
    
    @Test
    fun `test zookeeper configuration`() {
        val json = JsonObject()
            .put("cluster", JsonObject()
                .put("type", "ZOOKEEPER")
                .put("getConfigFromCluster", true)
            )
        
        val config = ClusterConfig(json)
        
        assertEquals("ZOOKEEPER", config.type)
        assertTrue(config.enabled)
        assertTrue(config.getConfigFromCluster)
        assertTrue(config.clusterConfig.getString("zookeeperHosts").isNotEmpty())
        assertTrue(config.clusterConfig.getJsonObject("retry").getInteger("maxTimes") > 0)
    }
    
    @Test
    fun `test hazelcast configuration`() {
        val json = JsonObject()
            .put("cluster", JsonObject()
                .put("type", "HAZELCAST")
                .put("getConfigFromCluster", false)
            )
        
        val config = ClusterConfig(json)
        
        assertEquals("HAZELCAST", config.type)
        assertTrue(config.enabled)
        assertFalse(config.getConfigFromCluster)
        assertTrue(config.clusterConfig.getString("hazelcastConfigPath").isNotEmpty())
    }
    
    @Test
    fun `test infinispan configuration`() {
        val json = JsonObject()
            .put("cluster", JsonObject()
                .put("type", "INFINISPAN")
                .put("getConfigFromCluster", false)
            )
        
        val config = ClusterConfig(json)
        
        assertEquals("INFINISPAN", config.type)
        assertTrue(config.enabled)
        assertFalse(config.getConfigFromCluster)
        assertTrue(config.clusterConfig.getString("infinispanConfigPath").isNotEmpty())
    }
    
    @Test
    fun `test custom configuration`() {
        val customConfig = JsonObject()
            .put("zookeeperHosts", "192.168.1.1,192.168.1.2")
            .put("sessionTimeout", 30000)
        
        val json = JsonObject()
            .put("cluster", JsonObject()
                .put("type", "ZOOKEEPER")
                .put("config", customConfig)
            )
        
        val config = ClusterConfig(json)
        
        assertEquals("ZOOKEEPER", config.type)
        assertTrue(config.enabled)
        assertEquals("192.168.1.1,192.168.1.2", config.clusterConfig.getString("zookeeperHosts"))
        assertEquals(30000, config.clusterConfig.getInteger("sessionTimeout"))
    }
    
    @Test
    fun `test toJson method`() {
        val json = JsonObject()
            .put("cluster", JsonObject()
                .put("type", "ZOOKEEPER")
                .put("getConfigFromCluster", true)
            )
        
        val config = ClusterConfig(json)
        val result = config.toJson()
        
        assertEquals("ZOOKEEPER", result.getString("type"))
        assertTrue(result.getBoolean("enabled"))
        assertTrue(result.getBoolean("getConfigFromCluster"))
        assertTrue(result.getJsonObject("config").getString("zookeeperHosts").isNotEmpty())
    }
}
