package com.louloulin.apix.admin

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.RouteManager

/**
 * Handler for route management API endpoints.
 */
class RouteHandler(private val routeManager: RouteManager) {
    private val logger = LoggerFactory.getLogger(RouteHandler::class.java)

    /**
     * Sets up the route management API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up route management API routes...")

        // Route management endpoints
        router.get("/routes").handler(this::getRoutes)
        router.post("/routes").handler(this::createRoute)
        router.get("/routes/:id").handler(this::getRoute)
        router.put("/routes/:id").handler(this::updateRoute)
        router.delete("/routes/:id").handler(this::deleteRoute)
    }

    /**
     * Gets all routes.
     */
    private fun getRoutes(context: RoutingContext) {
        try {
            val routes = routeManager.getRoutes()
            val routesArray = JsonArray()

            routes.forEach { route: com.louloulin.apix.models.Route ->
                routesArray.add(route.toJson())
            }

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("routes", routesArray)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting routes", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get routes: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Creates a new route.
     */
    private fun createRoute(context: RoutingContext) {
        createRouteForTest(context)
    }

    /**
     * Creates a new route (for testing).
     */
    fun createRouteForTest(context: RoutingContext) {
        try {
            // 安全地获取请求体
            val body = try {
                context.body().asJsonObject()
            } catch (e: Exception) {
                logger.warn("Failed to parse request body as JSON: ${e.message}")
                null
            }

            logger.info("Received create route request with body: $body")

            if (body == null) {
                logger.warn("Request body is null")
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Invalid or missing request body")
                        .encode()
                    )
                return
            }

            // Validate required fields
            if (!body.containsKey("path") || !body.containsKey("target")) {
                logger.warn("Missing required fields in request body: path=${body.containsKey("path")}, target=${body.containsKey("target")}")
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing required fields: path, target")
                        .encode()
                    )
                return
            }

            // 将 target 字段复制到 targetUrl 字段，以兼容 Route.fromJson 方法
            if (body.containsKey("target") && !body.containsKey("targetUrl")) {
                body.put("targetUrl", body.getString("target"))
            }

            val route = routeManager.createRoute(body)

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
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to create route: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets a route by ID.
     */
    private fun getRoute(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val route = routeManager.getRoute(id)

            if (route == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Route not found: $id")
                        .encode()
                    )
                return
            }

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("route", route.toJson())
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting route", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get route: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Updates a route.
     */
    private fun updateRoute(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val body = context.body().asJsonObject()

            // 将 target 字段复制到 targetUrl 字段，以兼容 Route.fromJson 方法
            if (body.containsKey("target") && !body.containsKey("targetUrl")) {
                body.put("targetUrl", body.getString("target"))
            }

            val route = routeManager.getRoute(id)

            if (route == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Route not found: $id")
                        .encode()
                    )
                return
            }

            val updatedRoute = routeManager.updateRoute(id, body)

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("route", updatedRoute?.toJson())
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error updating route", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to update route: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Deletes a route.
     */
    private fun deleteRoute(context: RoutingContext) {
        try {
            val id = context.pathParam("id")

            val route = routeManager.getRoute(id)

            if (route == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Route not found: $id")
                        .encode()
                    )
                return
            }

            routeManager.deleteRoute(id)

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error deleting route", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to delete route: ${e.message}")
                    .encode()
                )
        }
    }
}
