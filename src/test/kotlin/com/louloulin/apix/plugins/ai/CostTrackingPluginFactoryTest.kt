package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostTrackingPluginFactoryTest {

    @Test
    fun testCreatePlugin() {
        // Create factory
        val factory = CostTrackingPluginFactory()

        // Create config
        val configJson = JsonObject()
            .put("enabled", true)
            .put("track_costs", true)

        val config = PluginConfig("test-cost-tracking", "cost-tracking", configJson)

        // Create plugin
        val plugin = factory.create(config)

        // Verify plugin
        assertTrue(plugin is CostTrackingPlugin)
        assertEquals("test-cost-tracking", plugin.id)
        assertEquals("cost-tracking", plugin.type)
    }
}
