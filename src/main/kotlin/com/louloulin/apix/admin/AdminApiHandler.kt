package com.louloulin.apix.admin

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.PluginManager
import com.louloulin.apix.core.RouteManager
import com.louloulin.apix.core.ServiceManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.models.Route
import com.louloulin.apix.models.Service
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
        router.get("/plugins/:id").handler(this::getPluginById)
        router.post("/plugins").handler(this::createPlugin)
        router.put("/plugins/:id").handler(this::updatePlugin)
        router.delete("/plugins/:id").handler(this::deletePlugin)
        router.post("/plugins/:id/enable").handler(this::enablePlugin)
        router.post("/plugins/:id/disable").handler(this::disablePlugin)
        router.post("/plugins/:id/reload").handler(this::reloadPlugin)
        router.post("/plugins/load-jar").handler(this::loadPluginJar)
        router.post("/plugins/scan-dir").handler(this::scanPluginDir)

        // API Key endpoints
        router.get("/auth/api-keys").handler(this::getApiKeys)
        router.post("/auth/api-keys").handler(this::createApiKey)
        router.delete("/auth/api-keys/:id").handler(this::deleteApiKey)

        // AI-specific endpoints
        router.get("/ai/models").handler(this::getAiModels)
        router.get("/ai/usage").handler(this::getAiUsage)
        router.post("/ai/cache/clear").handler(this::clearAiCache)
        router.get("/ai/cache/stats").handler(this::getAiCacheStats)
        router.get("/ai/routing/rules").handler(this::getAiRoutingRules)

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

            routeManager.addRoute(route)

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

            routeManager.addRoute(route)

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
        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("plugins", response.getValue("result")).encode())
                } else {
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get plugins: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Gets a plugin by ID.
     */
    private fun getPluginById(context: RoutingContext) {
        val id = context.pathParam("id")

        if (id == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Plugin ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("plugin", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Creates a new plugin.
     */
    private fun createPlugin(context: RoutingContext) {
        val body = context.body().asJsonObject()

        if (body == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Request body is required")
                    .encode()
                )
            return
        }

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_CREATE, body) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("plugin", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to create plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Updates a plugin.
     */
    private fun updatePlugin(context: RoutingContext) {
        val id = context.pathParam("id")
        val body = context.body().asJsonObject()

        if (id == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Plugin ID is required")
                    .encode()
                )
            return
        }

        if (body == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Request body is required")
                    .encode()
                )
            return
        }

        // 添加 ID 到请求体
        body.put("id", id)

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_UPDATE, body) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("plugin", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to update plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Deletes a plugin.
     */
    private fun deletePlugin(context: RoutingContext) {
        val id = context.pathParam("id")

        if (id == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Plugin ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_DELETE, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .setStatusCode(204)
                        .end()
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to delete plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Enables a plugin.
     */
    private fun enablePlugin(context: RoutingContext) {
        val id = context.pathParam("id")

        if (id == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Plugin ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_ENABLE, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .put("message", "Plugin enabled: $id")
                            .encode()
                        )
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to enable plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Disables a plugin.
     */
    private fun disablePlugin(context: RoutingContext) {
        val id = context.pathParam("id")

        if (id == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Plugin ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_DISABLE, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .put("message", "Plugin disabled: $id")
                            .encode()
                        )
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to disable plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Reloads a plugin.
     */
    private fun reloadPlugin(context: RoutingContext) {
        val id = context.pathParam("id")

        if (id == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Plugin ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_RELOAD, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .put("message", "Plugin reloaded: $id")
                            .put("plugin", response.getValue("result"))
                            .encode()
                        )
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to reload plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Loads a plugin JAR file.
     */
    private fun loadPluginJar(context: RoutingContext) {
        val body = context.body().asJsonObject()

        if (body == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Request body is required")
                    .encode()
                )
            return
        }

        val jarPath = body.getString("jarPath")

        if (jarPath == null) {
            context.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "JAR path is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("jarPath", jarPath)

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_LOAD_JAR, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .put("result", response.getValue("result"))
                            .encode()
                        )
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to load plugin JAR: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Scans a directory for plugin JAR files.
     */
    private fun scanPluginDir(context: RoutingContext) {
        val body = context.body().asJsonObject()
        val dirPath = body?.getString("dirPath")

        val message = JsonObject()
        if (dirPath != null) {
            message.put("dirPath", dirPath)
        }

        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_SCAN_DIR, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .put("result", response.getValue("result"))
                            .encode()
                        )
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    context.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to scan plugin directory: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Gets all API Keys.
     */
    private fun getApiKeys(ctx: RoutingContext) {
        logger.info("Getting all API Keys")

        ctx.vertx().eventBus().request<JsonObject>(EventBusAddresses.AUTH_GET_API_KEYS, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("apiKeys", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get API Keys: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Creates a new API Key.
     */
    private fun createApiKey(ctx: RoutingContext) {
        logger.info("Creating new API Key")

        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Request body is required")
                    .encode()
                )
            return
        }

        val name = body.getString("name")
        val scopes = body.getJsonArray("scopes", JsonArray())
        val expiresAt = body.getLong("expiresAt", 0L)

        if (name.isNullOrBlank()) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Name is required")
                    .encode()
                )
            return
        }

        val message = JsonObject()
            .put("name", name)
            .put("scopes", scopes)
            .put("expiresAt", expiresAt)

        ctx.vertx().eventBus().request<JsonObject>(EventBusAddresses.AUTH_CREATE_API_KEY, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("apiKey", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to create API Key: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Deletes an API Key.
     */
    private fun deleteApiKey(ctx: RoutingContext) {
        val id = ctx.pathParam("id")

        if (id.isNullOrBlank()) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "API Key ID is required")
                    .encode()
                )
            return
        }

        logger.info("Deleting API Key: {}", id)

        val message = JsonObject().put("id", id)

        ctx.vertx().eventBus().request<JsonObject>(EventBusAddresses.AUTH_DELETE_API_KEY, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .setStatusCode(204)
                        .end()
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to delete API Key: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
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
        val modelId = context.request().getParam("modelId")

        // 创建清除缓存的消息
        val message = JsonObject()
        if (modelId != null) {
            message.put("modelId", modelId)
        }

        // 通过 EventBus 清除缓存
        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.CACHE_CLEAR, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    val clearedCount = response.getInteger("result", 0)
                    val responseMessage = if (modelId != null) {
                        "Cleared $clearedCount cache entries for model: $modelId"
                    } else {
                        "Cleared $clearedCount cache entries"
                    }

                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .put("message", responseMessage)
                            .put("count", clearedCount)
                            .encode()
                        )
                } else {
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", false)
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Failed to clear cache: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Gets AI cache statistics.
     */
    private fun getAiCacheStats(context: RoutingContext) {
        val modelId = context.request().getParam("modelId")

        // 创建获取缓存统计信息的消息
        val message = JsonObject()
        if (modelId != null) {
            message.put("modelId", modelId)
        }

        // 通过 EventBus 获取缓存统计信息
        context.vertx().eventBus().request<JsonObject>(EventBusAddresses.CACHE_STATS, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .put("stats", response.getValue("result"))
                            .encode()
                        )
                } else {
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("success", false)
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Failed to get cache stats: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
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

    /**
     * Gets AI routing rules.
     */
    private fun getAiRoutingRules(context: RoutingContext) {
        // This is a placeholder implementation
        // In a real gateway, this would return the actual routing rules from a model router

        val rulesArray = JsonArray()
            .add(JsonObject()
                .put("id", "rule1")
                .put("name", "Technical Content Rule")
                .put("priority", 100)
                .put("condition", JsonObject()
                    .put("type", "CONTAINS")
                    .put("pattern", "code")
                    .put("contentTypes", JsonArray().add("text/plain").add("application/json"))
                    .put("requestTypes", JsonArray().add("chat").add("completion"))
                )
                .put("targetModel", "gpt-4")
            )
            .add(JsonObject()
                .put("id", "rule2")
                .put("name", "Creative Content Rule")
                .put("priority", 90)
                .put("condition", JsonObject()
                    .put("type", "CONTAINS")
                    .put("pattern", "story")
                    .put("contentTypes", JsonArray().add("text/plain"))
                    .put("requestTypes", JsonArray().add("chat").add("completion"))
                )
                .put("targetModel", "claude-3-opus")
            )
            .add(JsonObject()
                .put("id", "rule3")
                .put("name", "Default Rule")
                .put("priority", 0)
                .put("condition", JsonObject()
                    .put("type", "DEFAULT")
                    .put("pattern", "*")
                    .put("contentTypes", JsonArray().add("*"))
                    .put("requestTypes", JsonArray().add("*"))
                )
                .put("targetModel", "gpt-3.5-turbo")
            )

        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject().put("rules", rulesArray).encode())
    }
}
