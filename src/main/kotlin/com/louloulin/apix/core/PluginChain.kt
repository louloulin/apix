package com.louloulin.apix.core

import com.louloulin.apix.plugins.Plugin
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * Represents a chain of plugins that will be executed in sequence.
 */
class PluginChain(private val plugins: List<Plugin>) {
    private val logger = LoggerFactory.getLogger(PluginChain::class.java)
    
    /**
     * Executes the plugin chain for the given routing context.
     * 
     * @param context The routing context to process
     * @return A future that completes when the chain has been executed
     */
    fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        if (plugins.isEmpty()) {
            // No plugins to execute, complete immediately
            promise.complete()
            return promise.future()
        }
        
        // Start executing the chain
        executeNext(context, 0, promise)
        
        return promise.future()
    }
    
    /**
     * Recursively executes the next plugin in the chain.
     */
    private fun executeNext(context: RoutingContext, index: Int, promise: Promise<Void>) {
        // Check if we've reached the end of the chain
        if (index >= plugins.size) {
            promise.complete()
            return
        }
        
        // Check if the response has already been ended
        if (context.response().ended()) {
            logger.debug("Response already ended, stopping plugin chain")
            promise.complete()
            return
        }
        
        // Get the current plugin
        val plugin = plugins[index]
        
        try {
            // Execute the plugin
            plugin.execute(context).onComplete { result ->
                if (result.succeeded()) {
                    // Continue to the next plugin if the response hasn't been ended
                    if (!context.response().ended()) {
                        executeNext(context, index + 1, promise)
                    } else {
                        // Response has been ended by the plugin
                        promise.complete()
                    }
                } else {
                    // Plugin execution failed
                    logger.error("Plugin execution failed: {}", plugin.id, result.cause())
                    
                    // Check if the response has already been ended
                    if (!context.response().ended()) {
                        context.fail(result.cause())
                    }
                    
                    promise.fail(result.cause())
                }
            }
        } catch (e: Exception) {
            // Exception during plugin execution
            logger.error("Exception during plugin execution: {}", plugin.id, e)
            
            // Check if the response has already been ended
            if (!context.response().ended()) {
                context.fail(e)
            }
            
            promise.fail(e)
        }
    }
}
