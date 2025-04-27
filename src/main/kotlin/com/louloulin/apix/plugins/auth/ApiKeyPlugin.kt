package com.louloulin.apix.plugins.auth

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * Plugin that authenticates requests using API keys.
 */
class ApiKeyPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(ApiKeyPlugin::class.java)
    
    override val type: String = "api-key"
    
    // Configuration values
    private val headerName: String = config.getString("header", "X-API-Key") ?: "X-API-Key"
    private val apiKeys: List<String> = config.getJsonArray("keys")?.map { it.toString() } ?: emptyList()
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // Get the API key from the request header
            val apiKey = context.request().getHeader(headerName)
            
            if (apiKey == null) {
                // API key is missing
                logger.debug("API key is missing")
                context.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end("""{"error": "API key is missing"}""")
                promise.complete() // Complete the promise to stop the chain
                return promise.future()
            }
            
            if (apiKeys.isEmpty() || apiKeys.contains(apiKey)) {
                // API key is valid or no keys are configured (allow all)
                logger.debug("API key is valid")
                promise.complete()
            } else {
                // API key is invalid
                logger.debug("API key is invalid")
                context.response()
                    .setStatusCode(403)
                    .putHeader("Content-Type", "application/json")
                    .end("""{"error": "Invalid API key"}""")
                promise.complete() // Complete the promise to stop the chain
            }
        } catch (e: Exception) {
            logger.error("Error executing API key plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    override fun shutdown() {
        // No resources to clean up
    }
}
