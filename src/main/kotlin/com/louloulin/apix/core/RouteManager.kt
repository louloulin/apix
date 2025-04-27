package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.models.Route
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.HttpRequest
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory
import java.net.URL
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
    private val webClient: WebClient

    init {
        // Create web client for forwarding requests
        val webClientOptions = WebClientOptions()
            .setKeepAlive(true)
            .setMaxPoolSize(50)
            .setConnectTimeout(5000) // 5 seconds
            .setIdleTimeout(60) // 60 seconds

        webClient = WebClient.create(vertx, webClientOptions)

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

            routesConfig.forEach { configObj ->
                val routeConfig = configObj as JsonObject
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
        val routeHandler = Handler<RoutingContext> { context ->
            // Store route information in the context
            context.put("route", route)

            // Execute the plugin chain
            pluginChain.execute(context)
                .onSuccess {
                    // If the response hasn't been ended by a plugin, forward the request
                    if (!context.response().ended()) {
                        forwardRequest(context, route)
                    }
                }
                .onFailure { cause ->
                    // Plugin chain execution failed
                    if (!context.response().ended()) {
                        context.fail(cause)
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
        if (!route.enabled) {
            // Route is disabled
            context.response()
                .setStatusCode(503)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Route is disabled")
                    .encode()
                )
            return
        }

        try {
            // Parse the target URL
            val url = URL(route.targetUrl)
            val isSecure = url.protocol == "https"

            // Create the request
            val request = webClient.request(
                context.request().method(),
                if (isSecure) 443 else 80,
                url.host,
                url.path + (if (url.query != null) "?${url.query}" else "")
            )

            // Set SSL if needed
            if (isSecure) {
                request.ssl(true)
            }

            // Copy headers from the original request
            context.request().headers().forEach { header ->
                // Skip host header as it will be set automatically
                if (header.key.lowercase() != "host") {
                    request.putHeader(header.key, header.value)
                }
            }

            // Add gateway headers
            request.putHeader("X-Forwarded-By", "APIX-Gateway")
            request.putHeader("X-Forwarded-Proto", context.request().scheme())
            request.putHeader("X-Forwarded-Host", context.request().host())

            // Send the request with the body
            val body = context.body().buffer()
            sendRequest(request, body, context, route)
        } catch (e: Exception) {
            logger.error("Error forwarding request to {}", route.targetUrl, e)

            // Return an error response
            context.response()
                .setStatusCode(502)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Error forwarding request: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Sends the request to the target service.
     */
    private fun sendRequest(
        request: HttpRequest<Buffer>,
        body: Buffer?,
        context: RoutingContext,
        route: Route
    ) {
        val future: Future<HttpResponse<Buffer>> = if (body != null) {
            request.sendBuffer(body)
        } else {
            request.send()
        }

        future.onComplete { result ->
            if (result.succeeded()) {
                val response = result.result()

                // Copy the response status and headers
                val clientResponse = context.response()
                    .setStatusCode(response.statusCode())

                // Copy headers from the target response
                response.headers().forEach { header ->
                    clientResponse.putHeader(header.key, header.value)
                }

                // Add gateway headers
                clientResponse.putHeader("X-Gateway-Route", route.id)

                // Send the response body
                if (response.body() != null) {
                    clientResponse.end(response.body())
                } else {
                    clientResponse.end()
                }
            } else {
                logger.error("Error sending request to {}", route.targetUrl, result.cause())

                // Return an error response
                context.response()
                    .setStatusCode(502)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Error sending request: ${result.cause().message}")
                        .encode()
                    )
            }
        }
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
