package com.louloulin.apix.plugins

import io.vertx.core.Future
import io.vertx.ext.web.RoutingContext

/**
 * Interface for all plugins in the gateway.
 */
interface Plugin {
    /**
     * The unique identifier of the plugin.
     */
    val id: String
    
    /**
     * The type of the plugin.
     */
    val type: String
    
    /**
     * The configuration of the plugin.
     */
    val config: PluginConfig
    
    /**
     * Executes the plugin for the given routing context.
     * 
     * @param context The routing context to process
     * @return A future that completes when the plugin has been executed
     */
    fun execute(context: RoutingContext): Future<Void>
    
    /**
     * Shuts down the plugin and releases any resources.
     */
    fun shutdown()
}
