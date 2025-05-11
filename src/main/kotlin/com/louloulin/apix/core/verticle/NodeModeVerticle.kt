package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.mode.NodeMode
import com.louloulin.apix.core.mode.NodeModeManager
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Verticle responsible for managing node operation mode.
 */
class NodeModeVerticle : BaseVerticle() {
    // Use the logger from BaseVerticle

    // Node mode manager
    private lateinit var nodeModeManager: NodeModeManager

    override fun registerEventBusHandlers() {
        // Node mode management
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.NODE_MODE_GET, this::handleGetNodeMode)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.NODE_INFO_GET, this::handleGetNodeInfo)
    }

    override fun onStart(startPromise: Promise<Void>) {
        // Get configuration from ConfigVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // Initialize node mode manager
                    nodeModeManager = NodeModeManager.getInstance(vertx)

                    // Initialize with configuration
                    nodeModeManager.initialize(config)
                        .onSuccess {
                            logger.info("NodeModeVerticle started successfully in ${nodeModeManager.getMode()} mode")
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("Failed to initialize node mode manager", cause)
                            startPromise.fail(cause)
                        }
                } else {
                    val errorMsg = "Failed to get configuration: ${configResponse.getString("message", "Unknown error")}"
                    logger.error(errorMsg)
                    startPromise.fail(errorMsg)
                }
            } else {
                logger.error("Failed to get configuration", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }

    /**
     * Handle get node mode request.
     */
    private fun handleGetNodeMode(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!::nodeModeManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "Node mode manager is not initialized"))
            return
        }

        val mode = nodeModeManager.getMode()

        val response = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("mode", mode.name)
                .put("isControlPlane", nodeModeManager.isControlPlane())
                .put("isDataPlane", nodeModeManager.isDataPlane())
                .put("isStandalone", nodeModeManager.isStandalone())
            )

        message.reply(response)
    }

    /**
     * Handle get node info request.
     */
    private fun handleGetNodeInfo(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!::nodeModeManager.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "Node mode manager is not initialized"))
            return
        }

        val nodeInfo = nodeModeManager.getNodeInfo()

        // Add cluster information if available
        if (vertx.isClustered()) {
            vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_NODE_INFO, JsonObject()) { ar ->
                if (ar.succeeded()) {
                    val clusterResponse = ar.result().body()
                    if (clusterResponse.getBoolean("success", false)) {
                        val clusterInfo = clusterResponse.getJsonObject("result", JsonObject())
                        nodeInfo.mergeIn(clusterInfo)
                    }
                }

                val response = JsonObject()
                    .put("success", true)
                    .put("result", nodeInfo)

                message.reply(response)
            }
        } else {
            // Add non-clustered information
            nodeInfo.put("clustered", false)

            val response = JsonObject()
                .put("success", true)
                .put("result", nodeInfo)

            message.reply(response)
        }
    }
}
