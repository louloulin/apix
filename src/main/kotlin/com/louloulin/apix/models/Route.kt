package com.louloulin.apix.models

import io.vertx.core.json.JsonObject

/**
 * Represents a route in the gateway.
 */
data class Route(
    val id: String,
    val name: String,
    val path: String,
    val methods: List<String>,
    val targetUrl: String,
    val plugins: List<String>,
    val enabled: Boolean
) {
    /**
     * Converts the route to a JSON object.
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("name", name)
            .put("path", path)
            .put("methods", methods)
            .put("targetUrl", targetUrl)
            .put("plugins", plugins)
            .put("enabled", enabled)
    }
    
    companion object {
        /**
         * Creates a route from a JSON object.
         */
        fun fromJson(json: JsonObject): Route {
            val id = json.getString("id")
                ?: throw IllegalArgumentException("Route ID is required")
            
            val name = json.getString("name", id)
            
            val path = json.getString("path")
                ?: throw IllegalArgumentException("Route path is required")
            
            val methods = json.getJsonArray("methods", io.vertx.core.json.JsonArray())
                .map { it.toString() }
            
            val targetUrl = json.getString("targetUrl")
                ?: throw IllegalArgumentException("Route target URL is required")
            
            val plugins = json.getJsonArray("plugins", io.vertx.core.json.JsonArray())
                .map { it.toString() }
            
            val enabled = json.getBoolean("enabled", true)
            
            return Route(
                id = id,
                name = name,
                path = path,
                methods = methods,
                targetUrl = targetUrl,
                plugins = plugins,
                enabled = enabled
            )
        }
    }
}
