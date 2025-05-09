package com.louloulin.apix.admin

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.ServiceManager

/**
 * Handler for service management API endpoints.
 */
class ServiceHandler(private val serviceManager: ServiceManager) {
    private val logger = LoggerFactory.getLogger(ServiceHandler::class.java)
    
    /**
     * Sets up the service management API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up service management API routes...")
        
        // Service management endpoints
        router.get("/services").handler(this::getServices)
        router.post("/services").handler(this::createService)
        router.get("/services/:id").handler(this::getService)
        router.put("/services/:id").handler(this::updateService)
        router.delete("/services/:id").handler(this::deleteService)
        router.get("/services/:id/health").handler(this::getServiceHealth)
    }
    
    /**
     * Gets all services.
     */
    private fun getServices(context: RoutingContext) {
        try {
            val services = serviceManager.getServices()
            val servicesArray = JsonArray()
            
            services.forEach { service ->
                servicesArray.add(service)
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("services", servicesArray)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting services", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get services: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Creates a new service.
     */
    private fun createService(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()
            
            // Validate required fields
            if (!body.containsKey("name") || !body.containsKey("url")) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing required fields: name, url")
                        .encode()
                    )
                return
            }
            
            val service = serviceManager.createService(body)
            
            context.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("service", service)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error creating service", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to create service: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Gets a service by ID.
     */
    private fun getService(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val service = serviceManager.getService(id)
            
            if (service == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Service not found: $id")
                        .encode()
                    )
                return
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("service", service)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting service", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get service: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Updates a service.
     */
    private fun updateService(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val body = context.body().asJsonObject()
            
            val service = serviceManager.getService(id)
            
            if (service == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Service not found: $id")
                        .encode()
                    )
                return
            }
            
            val updatedService = serviceManager.updateService(id, body)
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("service", updatedService)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error updating service", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to update service: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Deletes a service.
     */
    private fun deleteService(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            
            val service = serviceManager.getService(id)
            
            if (service == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Service not found: $id")
                        .encode()
                    )
                return
            }
            
            serviceManager.deleteService(id)
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error deleting service", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to delete service: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Gets the health status of a service.
     */
    private fun getServiceHealth(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val service = serviceManager.getService(id)
            
            if (service == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Service not found: $id")
                        .encode()
                    )
                return
            }
            
            val health = serviceManager.getServiceHealth(id)
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(health.encode())
        } catch (e: Exception) {
            logger.error("Error getting service health", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get service health: ${e.message}")
                    .encode()
                )
        }
    }
}
