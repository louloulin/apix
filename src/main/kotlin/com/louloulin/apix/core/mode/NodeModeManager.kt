package com.louloulin.apix.core.mode

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Manager for node operation mode.
 * Handles mode-specific configuration and provides utilities for mode-specific operations.
 */
class NodeModeManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(NodeModeManager::class.java)
    
    // Current node mode
    private val currentMode = AtomicReference(NodeMode.STANDALONE)
    
    // Node configuration
    private var nodeConfig = JsonObject()
    
    /**
     * Initialize the node mode manager with the given configuration.
     * @param config The configuration object.
     * @return A future that completes when initialization is done.
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("Initializing NodeModeManager")
        
        // Get node configuration
        nodeConfig = config.getJsonObject("node", JsonObject())
        
        // Determine node mode
        val mode = NodeMode.fromConfig(config)
        currentMode.set(mode)
        
        logger.info("Node initialized in ${mode.name} mode")
        
        return Future.succeededFuture()
    }
    
    /**
     * Get the current node mode.
     * @return The current node mode.
     */
    fun getMode(): NodeMode {
        return currentMode.get()
    }
    
    /**
     * Check if the node is running in control plane mode.
     * @return True if the node is in control plane mode, false otherwise.
     */
    fun isControlPlane(): Boolean {
        val mode = currentMode.get()
        return mode == NodeMode.CONTROL_PLANE || mode == NodeMode.STANDALONE
    }
    
    /**
     * Check if the node is running in data plane mode.
     * @return True if the node is in data plane mode, false otherwise.
     */
    fun isDataPlane(): Boolean {
        val mode = currentMode.get()
        return mode == NodeMode.DATA_PLANE || mode == NodeMode.STANDALONE
    }
    
    /**
     * Check if the node is running in standalone mode.
     * @return True if the node is in standalone mode, false otherwise.
     */
    fun isStandalone(): Boolean {
        return currentMode.get() == NodeMode.STANDALONE
    }
    
    /**
     * Get the node configuration.
     * @return The node configuration.
     */
    fun getNodeConfig(): JsonObject {
        return nodeConfig.copy()
    }
    
    /**
     * Get node information as JSON.
     * @return A JSON object containing node information.
     */
    fun getNodeInfo(): JsonObject {
        return JsonObject()
            .put("mode", currentMode.get().name)
            .put("isControlPlane", isControlPlane())
            .put("isDataPlane", isDataPlane())
            .put("isStandalone", isStandalone())
    }
    
    companion object {
        // Singleton instance
        @Volatile
        private var instance: NodeModeManager? = null
        
        /**
         * Get the singleton instance of NodeModeManager.
         * @param vertx The Vert.x instance.
         * @return The NodeModeManager instance.
         */
        fun getInstance(vertx: Vertx): NodeModeManager {
            return instance ?: synchronized(this) {
                instance ?: NodeModeManager(vertx).also { instance = it }
            }
        }
    }
}
