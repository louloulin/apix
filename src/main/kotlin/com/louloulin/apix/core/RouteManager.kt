package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.models.Route
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the API routes and their associated plugin chains.
 */
class RouteManager(
    private val vertx: Vertx,
    private val configManager: ConfigManager,
    private val pluginManager: PluginManager
) {
    private val logger = LoggerFactory.getLogger(RouteManager::class.java)
    private val routes = ConcurrentHashMap<String, Route>()
    
    init {
        // Load routes from configuration
        loadRoutesFromConfig()
    }
    
    /**
     * Loads routes from configuration.
     */
    private fun loadRoutesFromConfig() {
        logger.info("Loading routes from configuration...")
        
        try {
            val routesConfig = configManager.getRoutesConfig()
            
            routesConfig.forEach { routeConfig ->
                val routeId = routeConfig.getString("id")
                
                if (routeId != null) {
                    try {
                        val route = Route.fromJson(routeConfig)
                        routes[routeId] = route
                        logger.info("Loaded route: {}", routeId)
                    } catch (e: Exception) {
                        logger.error("Failed to create route: {}", routeId, e)
                    }
                } else {
                    logger.warn("Invalid route configuration: {}", routeConfig.encode())
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading routes from configuration", e)
        }
    }
    
    /**
     * Sets up all routes on the provided router.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up routes...")
        
        routes.values.forEach { route ->
            try {
                setupRoute(router, route)
            } catch (e: Exception) {
                logger.error("Failed to set up route: {}", route.id, e)
            }
        }
    }
    
    /**
     * Sets up a single route on the provided router.
     */
    private fun setupRoute(router: Router, route: Route) {
        logger.info("Setting up route: {}", route.id)
        
        // Create a plugin chain for this route
        val pluginChain = pluginManager.createPluginChain(route.plugins)
        
        // Create a handler for this route
        val routeHandler = { context: RoutingContext ->
            // Store route information in the context
            context.put("route", route)
            
            // Execute the plugin chain
            pluginChain.execute(context).onComplete { result ->
                if (result.succeeded()) {
                    // If the response hasn't been ended by a plugin, forward the request
                    if (!context.response().ended()) {
                        forwardRequest(context, route)
                    }
                } else {
                    // Plugin chain execution failed
                    if (!context.response().ended()) {
                        context.fail(result.cause())
                    }
                }
            }
        }
        
        // Add the route to the router
        val methods = route.methods.map { HttpMethod.valueOf(it) }
        
        if (methods.isEmpty()) {
            // If no methods are specified, match all methods
            router.route(route.path).handler(routeHandler)
        } else {
            // Otherwise, match only the specified methods
            methods.forEach { method ->
                router.route(method, route.path).handler(routeHandler)
            }
        }
    }
    
    /**
     * Forwards the request to the target service.
     */
    private fun forwardRequest(context: RoutingContext, route: Route) {
        // This is a simplified implementation
        // In a real gateway, this would forward the request to the target service
        
        // For now, just return a success response
        context.response()
            .setStatusCode(200)
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("success", true)
                .put("message", "Request would be forwarded to ${route.targetUrl}")
                .encode()
            )
    }
    
    /**
     * Gets a route by its ID.
     */
    fun getRoute(id: String): Route? {
        return routes[id]
    }
    
    /**
     * Gets all registered routes.
     */
    fun getAllRoutes(): Collection<Route> {
        return routes.values
    }
    
    /**
     * Adds or updates a route.
     */
    fun updateRoute(route: Route) {
        routes[route.id] = route
        // In a real implementation, we would need to update the router
        // This would typically require restarting the server or using dynamic routing
    }
    
    /**
     * Removes a route.
     */
    fun removeRoute(id: String) {
        routes.remove(id)
        // In a real implementation, we would need to update the router
    }
}
