package com.louloulin.apix.admin

import io.vertx.core.Vertx
import io.vertx.ext.web.Router
import org.slf4j.LoggerFactory

/**
 * Extension for AdminApiHandler to add AI-related endpoints.
 */
class AdminApiHandlerExtension(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(AdminApiHandlerExtension::class.java)
    
    // Create handlers
    private val aiModelHandler = AIModelHandler()
    private val aiRoutingHandler = AIRoutingHandler()
    private val systemMetricsHandler = SystemMetricsHandler(vertx)
    
    /**
     * Sets up the extended admin API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up extended admin API routes...")
        
        // Set up AI model management routes
        aiModelHandler.setupRoutes(router)
        
        // Set up AI routing rules routes
        aiRoutingHandler.setupRoutes(router)
        
        // Set up system metrics routes
        systemMetricsHandler.setupRoutes(router)
    }
}
