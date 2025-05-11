package com.louloulin.apix.core.mode

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests for NodeMode enum.
 */
class NodeModeTest {
    
    @Test
    fun `test fromString with valid values`() {
        assertEquals(NodeMode.CONTROL_PLANE, NodeMode.fromString("CONTROL_PLANE"))
        assertEquals(NodeMode.CONTROL_PLANE, NodeMode.fromString("CONTROL"))
        assertEquals(NodeMode.CONTROL_PLANE, NodeMode.fromString("CP"))
        assertEquals(NodeMode.CONTROL_PLANE, NodeMode.fromString("control_plane"))
        
        assertEquals(NodeMode.DATA_PLANE, NodeMode.fromString("DATA_PLANE"))
        assertEquals(NodeMode.DATA_PLANE, NodeMode.fromString("DATA"))
        assertEquals(NodeMode.DATA_PLANE, NodeMode.fromString("DP"))
        assertEquals(NodeMode.DATA_PLANE, NodeMode.fromString("data_plane"))
        
        assertEquals(NodeMode.STANDALONE, NodeMode.fromString("STANDALONE"))
        assertEquals(NodeMode.STANDALONE, NodeMode.fromString("standalone"))
    }
    
    @Test
    fun `test fromString with invalid values`() {
        assertEquals(NodeMode.STANDALONE, NodeMode.fromString("INVALID"))
        assertEquals(NodeMode.STANDALONE, NodeMode.fromString(""))
    }
    
    @Test
    fun `test fromConfig with valid config`() {
        val config = JsonObject()
            .put("node", JsonObject()
                .put("mode", "CONTROL_PLANE")
            )
        
        assertEquals(NodeMode.CONTROL_PLANE, NodeMode.fromConfig(config))
    }
    
    @Test
    fun `test fromConfig with missing mode`() {
        val config = JsonObject()
            .put("node", JsonObject())
        
        assertEquals(NodeMode.STANDALONE, NodeMode.fromConfig(config))
    }
    
    @Test
    fun `test fromConfig with missing node section`() {
        val config = JsonObject()
        
        assertEquals(NodeMode.STANDALONE, NodeMode.fromConfig(config))
    }
}
