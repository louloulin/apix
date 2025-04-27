package com.louloulin.apix.admin

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.PluginManager
import com.louloulin.apix.core.RouteManager
import com.louloulin.apix.core.ServiceManager
import com.louloulin.apix.models.Route
import com.louloulin.apix.models.Service
import com.louloulin.apix.plugins.ai.ResponseCachePlugin
import com.louloulin.apix.plugins.ai.TokenUsagePlugin
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory

/**
 * Handles the admin API endpoints.
 */
class AdminApiHandler(
    private val configManager: ConfigManager,
    private val pluginManager: PluginManager,
    private val routeManager: RouteManager,
    private val serviceManager: ServiceManager
) {
    private val logger = LoggerFactory.getLogger(AdminApiHandler::class.java)

    /**
     * Sets up the admin API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up admin API routes...")

        // Add body handler
        router.route().handler(BodyHandler.create())

        // Routes endpoints
        router.get("/routes").handler(this::getRoutes)
        router.post("/routes").handler(this::createRoute)
        router.get("/routes/:id").handler(this::getRoute)
        router.put("/routes/:id").handler(this::updateRoute)
        router.delete("/routes/:id").handler(this::deleteRoute)

        // Services endpoints
        router.get("/services").handler(this::getServices)
        router.post("/services").handler(this::createService)
        router.get("/services/:id").handler(this::getService)
        router.put("/services/:id").handler(this::updateService)
        router.delete("/services/:id").handler(this::deleteService)

        // Plugins endpoints
        router.get("/plugins").handler(this::getPlugins)

        // AI-specific endpoints
        router.get("/ai/models").handler(this::getAiModels)
        router.get("/ai/usage").handler(this::getAiUsage)
        router.post("/ai/cache/clear").handler(this::clearAiCache)

        // Config endpoints
        router.get("/config").handler(this::getConfig)
        router.put("/config").handler(this::updateConfig)
    }

    /**
     * Gets all routes.
     */
    private fun getRoutes(context: RoutingContext) {
        val routes = routeManager.getAllRoutes()
        val routesArray = JsonArray()

        routes.forEach { route ->
            routesArray.add(route.toJson())
        }

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject().put("routes", routesArray).encode())
    }

    /**
     * Creates a new route.
     */
    private fun createRoute(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()
            val route = Route.fromJson(body)

            routeManager.updateRoute(route)

            context.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("route", route.toJson())
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error creating route", e)

            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                    .encode()
                )
        }
    }

    /**
     * Gets a route by ID.
     */
    private fun getRoute(context: RoutingContext) {
        val id = context.pathParam("id")
        val route = routeManager.getRoute(id)

        if (route != null) {
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(route.toJson().encode())
        } else {
            context.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Route not found: $id")
                    .encode()
                )
        }
    }

    /**
     * Updates a route.
     */
    private fun updateRoute(context: RoutingContext) {
        val id = context.pathParam("id")

        try {
            val body = context.body().asJsonObject()

            // Ensure the ID in the path matches the ID in the body
            if (body.getString("id") != id) {
                throw IllegalArgumentException("Route ID in path does not match ID in body")
            }

            val route = Route.fromJson(body)

            routeManager.updateRoute(route)

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("route", route.toJson())
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error updating route", e)

            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                    .encode()
                )
        }
    }

    /**
     * Deletes a route.
     */
    private fun deleteRoute(context: RoutingContext) {
        val id = context.pathParam("id")

        routeManager.removeRoute(id)

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("success", true)
                .encode()
            )
    }

    /**
     * Gets all services.
     */
    private fun getServices(context: RoutingContext) {
        val services = serviceManager.getAllServices()
        val servicesArray = JsonArray()

        services.forEach { service ->
            servicesArray.add(service.toJson())
        }

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject().put("services", servicesArray).encode())
    }

    /**
     * Creates a new service.
     */
    private fun createService(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()
            val service = Service.fromJson(body)

            serviceManager.updateService(service)

            context.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("service", service.toJson())
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error creating service", e)

            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                    .encode()
                )
        }
    }

    /**
     * Gets a service by ID.
     */
    private fun getService(context: RoutingContext) {
        val id = context.pathParam("id")
        val service = serviceManager.getService(id)

        if (service != null) {
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(service.toJson().encode())
        } else {
            context.response()
                .setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Service not found: $id")
                    .encode()
                )
        }
    }

    /**
     * Updates a service.
     */
    private fun updateService(context: RoutingContext) {
        val id = context.pathParam("id")

        try {
            val body = context.body().asJsonObject()

            // Ensure the ID in the path matches the ID in the body
            if (body.getString("id") != id) {
                throw IllegalArgumentException("Service ID in path does not match ID in body")
            }

            val service = Service.fromJson(body)

            serviceManager.updateService(service)

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("service", service.toJson())
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error updating service", e)

            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                    .encode()
                )
        }
    }

    /**
     * Deletes a service.
     */
    private fun deleteService(context: RoutingContext) {
        val id = context.pathParam("id")

        serviceManager.removeService(id)

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("success", true)
                .encode()
            )
    }

    /**
     * Gets all plugins.
     */
    private fun getPlugins(context: RoutingContext) {
        val plugins = pluginManager.getAllPlugins()
        val pluginsArray = JsonArray()

        plugins.forEach { plugin ->
            pluginsArray.add(JsonObject()
                .put("id", plugin.id)
                .put("type", plugin.type)
            )
        }

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject().put("plugins", pluginsArray).encode())
    }

    /**
     * Gets available AI models.
     */
    private fun getAiModels(context: RoutingContext) {
        // This is a placeholder implementation
        // In a real gateway, this would return the available AI models

        val modelsArray = JsonArray()
            .add(JsonObject()
                .put("id", "gpt-4")
                .put("name", "GPT-4")
                .put("provider", "OpenAI")
            )
            .add(JsonObject()
                .put("id", "gpt-3.5-turbo")
                .put("name", "GPT-3.5 Turbo")
                .put("provider", "OpenAI")
            )
            .add(JsonObject()
                .put("id", "claude-3-opus")
                .put("name", "Claude 3 Opus")
                .put("provider", "Anthropic")
            )

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject().put("models", modelsArray).encode())
    }

    /**
     * Gets AI usage statistics.
     */
    private fun getAiUsage(context: RoutingContext) {
        // 查找令牌使用跟踪插件并获取统计信息
        var usageStats: JsonObject? = null

        pluginManager.getAllPlugins().forEach { plugin ->
            if (plugin.type == "token-usage" && plugin is TokenUsagePlugin) {
                usageStats = plugin.getUsageStats()
            }
        }

        // 如果没有找到令牌使用跟踪插件，返回默认统计信息
        if (usageStats == null) {
            usageStats = JsonObject()
                .put("total_tokens", 0)
                .put("prompt_tokens", 0)
                .put("completion_tokens", 0)
                .put("total_requests", 0)
                .put("models", JsonObject())
                .put("daily", JsonObject())
        }

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject().put("usage", usageStats).encode())
    }

    /**
     * Clears the AI response cache.
     */
    private fun clearAiCache(context: RoutingContext) {
        // 查找所有响应缓存插件并清除缓存
        var cacheCleared = false

        pluginManager.getAllPlugins().forEach { plugin ->
            if (plugin.type == "response-cache" && plugin is ResponseCachePlugin) {
                plugin.clearCache()
                cacheCleared = true
            }
        }

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("success", true)
                .put("cache_cleared", cacheCleared)
                .encode()
            )
    }

    /**
     * Gets the current configuration.
     */
    private fun getConfig(context: RoutingContext) {
        val config = configManager.getConfig()

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(config.encode())
    }

    /**
     * Updates the configuration.
     */
    private fun updateConfig(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()

            configManager.updateConfig(body)

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error updating configuration", e)

            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                    .encode()
                )
        }
    }
}
