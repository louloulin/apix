package com.louloulin.apix.core.mode

import io.vertx.core.json.JsonObject

/**
 * Enum representing the different operation modes of an APIX node.
 */
enum class NodeMode {
    /**
     * Control Plane mode - responsible for configuration management, management API and cluster coordination.
     */
    CONTROL_PLANE,

    /**
     * Data Plane mode - responsible for request processing, routing forwarding and plugin execution.
     */
    DATA_PLANE,

    /**
     * Standalone mode - combines both control plane and data plane functionalities in a single node.
     * This is the default mode for non-clustered deployments.
     */
    STANDALONE;

    companion object {
        /**
         * Parse node mode from string.
         * @param mode The mode string to parse.
         * @return The corresponding NodeMode enum value.
         */
        fun fromString(mode: String): NodeMode {
            return when (mode.uppercase()) {
                "CONTROL_PLANE", "CONTROL", "CP" -> CONTROL_PLANE
                "DATA_PLANE", "DATA", "DP" -> DATA_PLANE
                else -> STANDALONE
            }
        }

        /**
         * Get node mode from configuration.
         * @param config The configuration object.
         * @return The node mode specified in the configuration, or STANDALONE if not specified.
         */
        fun fromConfig(config: JsonObject): NodeMode {
            val modeStr = config.getJsonObject("node", JsonObject())
                .getString("mode", "STANDALONE")
            return fromString(modeStr)
        }
    }
}
