package com.louloulin.apix.plugins

import io.vertx.core.json.JsonObject

/**
 * Configuration for a plugin.
 */
data class PluginConfig(
    val id: String,
    val type: String,
    val config: JsonObject
) {
    /**
     * Gets a string value from the configuration.
     */
    fun getString(key: String, defaultValue: String? = null): String? {
        return config.getString(key, defaultValue)
    }

    /**
     * Gets an integer value from the configuration.
     */
    fun getInteger(key: String, defaultValue: Int? = null): Int? {
        return if (config.containsKey(key)) config.getInteger(key, defaultValue) else defaultValue
    }

    /**
     * Gets a boolean value from the configuration.
     */
    fun getBoolean(key: String, defaultValue: Boolean? = null): Boolean? {
        return if (config.containsKey(key)) config.getBoolean(key, defaultValue) else defaultValue
    }

    /**
     * Gets a JSON object from the configuration.
     */
    fun getJsonObject(key: String, defaultValue: JsonObject? = null): JsonObject? {
        return config.getJsonObject(key, defaultValue)
    }

    /**
     * Gets a JSON array from the configuration.
     */
    fun getJsonArray(key: String): io.vertx.core.json.JsonArray? {
        return config.getJsonArray(key)
    }

    /**
     * Converts the configuration to a JSON object.
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("type", type)
            .put("config", config)
    }

    companion object {
        /**
         * Creates a plugin configuration from a JSON object.
         */
        fun fromJson(json: JsonObject): PluginConfig {
            return PluginConfig(
                id = json.getString("id"),
                type = json.getString("type"),
                config = json.getJsonObject("config", JsonObject())
            )
        }
    }
}
