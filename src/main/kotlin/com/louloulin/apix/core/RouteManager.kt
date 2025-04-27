package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.models.Route
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages API routes and handles request forwarding.
 */
class RouteManager(
    private val vertx: Vertx,
    private val router: Router,
    private val configManager: ConfigManager,
    private val pluginManager: PluginManager
) {
    private val logger = LoggerFactory.getLogger(RouteManager::class.java)
    private val routes = ConcurrentHashMap<String, Route>()
    private val httpClient: HttpClient

    init {
        // Create HTTP client for forwarding requests
        val options = HttpClientOptions()
            .setKeepAlive(true)
            .setMaxPoolSize(50)
            .setConnectTimeout(5000) // 5 seconds
            .setIdleTimeout(60) // 60 seconds

        httpClient = vertx.createHttpClient(options)

        // Load routes from configuration
        loadRoutesFromConfig()
    }

    /**
     * Loads routes from configuration.
     */
    private fun loadRoutesFromConfig() {
        val config = configManager.getConfig()
        val routesConfig = config.getJsonArray("routes") ?: return

        for (i in 0 until routesConfig.size()) {
            val routeJson = routesConfig.getJsonObject(i)
            val route = Route(
                id = routeJson.getString("id"),
                name = routeJson.getString("name", routeJson.getString("id")),
                path = routeJson.getString("path"),
                targetUrl = routeJson.getString("targetUrl"),
                methods = routeJson.getJsonArray("methods")?.map { it.toString() }
                    ?: listOf("GET", "POST", "PUT", "DELETE"),
                plugins = routeJson.getJsonArray("plugins")?.map { it.toString() } ?: emptyList(),
                enabled = routeJson.getBoolean("enabled", true)
            )
            addRoute(route)
        }
    }

    /**
     * Adds a route to the router.
     */
    fun addRoute(route: Route): Boolean {
        if (routes.containsKey(route.id)) {
            // Route already exists, update it
            routes[route.id] = route
            return true
        }

        // Add the route to the map
        routes[route.id] = route

        // Create a handler for the route
        val handler = Handler<RoutingContext> { context ->
            handleRequest(context, route)
        }

        // Register the route with the router
        val routeBuilder = router.route(route.path)

        // Set allowed methods
        for (methodStr in route.methods) {
            try {
                val method = HttpMethod.valueOf(methodStr)
                routeBuilder.method(method)
            } catch (e: Exception) {
                logger.warn("Invalid HTTP method: {}", methodStr)
            }
        }

        // Add body handler and the route handler
        routeBuilder.handler(BodyHandler.create())
        routeBuilder.handler(handler)

        logger.info("Added route: {} -> {}", route.path, route.targetUrl)
        return true
    }

    /**
     * Removes a route from the router.
     */
    fun removeRoute(id: String): Boolean {
        val route = routes.remove(id) ?: return false

        // We can't directly remove routes from the router in Vert.x
        // Instead, we'll need to recreate the router or use a workaround
        // For now, we'll just mark it as removed in our map

        logger.info("Removed route: {}", route.path)
        return true
    }

    /**
     * Handles an incoming request and forwards it to the target service.
     */
    private fun handleRequest(context: RoutingContext, route: Route) {
        // Apply pre-processing plugins
        // In a real implementation, we would apply pre-processing plugins here
        // For now, we'll just log the request
        logger.debug("Processing request for route: {}", route.id)

        try {
            // Parse the target URL
            val url = URL(route.targetUrl)
            val isSecure = url.protocol == "https"
            val port = if (url.port == -1) (if (isSecure) 443 else 80) else url.port

            // Prepare request options
            val requestOptions = io.vertx.core.http.RequestOptions()
                .setHost(url.host)
                .setPort(port)
                .setURI(url.path + (if (url.query != null) "?${url.query}" else ""))
                .setSsl(isSecure)
                .setMethod(context.request().method())

            // Prepare headers
            val headers = io.vertx.core.MultiMap.caseInsensitiveMultiMap()

            // Copy headers from the original request
            context.request().headers().forEach { header ->
                // Skip host header as it will be set automatically
                if (header.key.lowercase() != "host") {
                    headers.add(header.key, header.value)
                }
            }

            // Add gateway headers
            headers.add("X-Forwarded-By", "APIX-Gateway")
            headers.add("X-Forwarded-Proto", context.request().scheme())
            headers.add("X-Forwarded-Host", context.request().host())

            requestOptions.setHeaders(headers)

            // Send the request with the body
            val body = context.body().buffer()

            // Create the request
            httpClient.request(requestOptions).onComplete { requestResult ->
                if (requestResult.succeeded()) {
                    val request = requestResult.result()

                    // Set up response handler
                    request.response().onComplete { responseResult ->
                        if (responseResult.succeeded()) {
                            val response = responseResult.result()

                            // Copy the response status and headers
                            val clientResponse = context.response()
                                .setStatusCode(response.statusCode())

                            // Copy headers from the target response
                            response.headers().forEach { header ->
                                clientResponse.putHeader(header.key, header.value)
                            }

                            // Add gateway headers
                            clientResponse.putHeader("X-Gateway-Route", route.id)

                            // Apply post-processing plugins
                            // In a real implementation, we would apply post-processing plugins here
                            // For now, we'll just log the response
                            logger.debug("Received response for route: {}, status: {}", route.id, response.statusCode())

                            // Handle response body
                            response.body().onComplete { bodyResult ->
                                if (bodyResult.succeeded()) {
                                    val responseBody = bodyResult.result()
                                    if (responseBody != null) {
                                        clientResponse.end(responseBody)
                                    } else {
                                        clientResponse.end()
                                    }
                                } else {
                                    clientResponse.end()
                                }
                            }
                        } else {
                            logger.error("Error getting response from {}", route.targetUrl, responseResult.cause())

                            // Return an error response
                            context.response()
                                .setStatusCode(502)
                                .putHeader("Content-Type", "application/json")
                                .end(JsonObject()
                                    .put("error", "Error getting response: ${responseResult.cause().message}")
                                    .encode()
                                )
                        }
                    }

                    // Send the request with body if present
                    if (body != null && body.length() > 0) {
                        request.end(body)
                    } else {
                        request.end()
                    }
                } else {
                    logger.error("Error creating request to {}", route.targetUrl, requestResult.cause())

                    // Return an error response
                    context.response()
                        .setStatusCode(502)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", "Error creating request: ${requestResult.cause().message}")
                            .encode()
                        )
                }
            }
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
     * Gets a route by its ID.
     */
    fun getRoute(id: String): Route? {
        return routes[id]
    }

    /**
     * Gets all routes.
     */
    fun getAllRoutes(): List<Route> {
        return routes.values.toList()
    }

    /**
     * Updates the configuration with the current routes.
     */
    fun saveRoutesToConfig() {
        val config = configManager.getConfig()
        val routesArray = io.vertx.core.json.JsonArray()

        for (route in routes.values) {
            val routeJson = JsonObject()
                .put("id", route.id)
                .put("name", route.name)
                .put("path", route.path)
                .put("targetUrl", route.targetUrl)
                .put("enabled", route.enabled)

            val methodsArray = io.vertx.core.json.JsonArray()
            for (method in route.methods) {
                methodsArray.add(method)
            }
            routeJson.put("methods", methodsArray)

            val pluginsArray = io.vertx.core.json.JsonArray()
            for (plugin in route.plugins) {
                pluginsArray.add(plugin)
            }
            routeJson.put("plugins", pluginsArray)

            routesArray.add(routeJson)
        }

        config.put("routes", routesArray)
        // In a real implementation, we would save the configuration to file
        // For now, we'll just log the updated configuration
        logger.info("Updated routes configuration")
    }
}
