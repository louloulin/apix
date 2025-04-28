package com.louloulin.apix.core.verticle

import com.louloulin.apix.cluster.ClusterConfig
import com.louloulin.apix.cluster.ClusterMonitor
import com.louloulin.apix.cluster.ClusterService
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.Counter
import java.util.concurrent.atomic.AtomicReference

/**
 * Verticle responsible for cluster management and synchronization.
 */
class ClusterVerticle : BaseVerticle() {
    private lateinit var clusterConfig: ClusterConfig
    private lateinit var clusterService: ClusterService
    private lateinit var clusterMonitor: ClusterMonitor

    // Cluster node counter
    private val nodeCounter = AtomicReference<Counter>()

    // Node ID
    private var nodeId: String = ""

    override fun registerEventBusHandlers() {
        // Cluster configuration
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_CONFIG_GET, this::handleGetClusterConfig)

        // Cluster node management
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_NODE_INFO, this::handleGetNodeInfo)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_NODES_GET, this::handleGetClusterNodes)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_METRICS_GET, this::handleGetClusterMetrics)

        // Cluster data synchronization
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_CONFIG_SYNC, this::handleSyncConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_ROUTES_SYNC, this::handleSyncRoutes)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_SERVICES_SYNC, this::handleSyncServices)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_PLUGINS_SYNC, this::handleSyncPlugins)
    }

    override fun onStart(startPromise: Promise<Void>) {
        // Get configuration from ConfigVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // Initialize cluster configuration
                    clusterConfig = ClusterConfig(config)

                    // Initialize cluster service and monitor
                    clusterService = ClusterService(vertx, clusterConfig)
                    clusterMonitor = ClusterMonitor(vertx, clusterConfig)

                    if (clusterConfig.enabled && vertx.isClustered()) {
                        // Initialize cluster service
                        clusterService.initialize().onComplete { initResult ->
                            if (initResult.succeeded()) {
                                // Initialize node counter
                                initializeNodeCounter().onComplete { counterResult ->
                                    if (counterResult.succeeded()) {
                                        // Start cluster monitor
                                        clusterMonitor.start().onComplete { monitorResult ->
                                            if (monitorResult.succeeded()) {
                                                logger.info("ClusterVerticle started successfully")
                                                startPromise.complete()
                                            } else {
                                                logger.error("Failed to start cluster monitor", monitorResult.cause())
                                                startPromise.fail(monitorResult.cause())
                                            }
                                        }
                                    } else {
                                        logger.error("Failed to initialize node counter", counterResult.cause())
                                        startPromise.fail(counterResult.cause())
                                    }
                                }
                            } else {
                                logger.error("Failed to initialize cluster service", initResult.cause())
                                startPromise.fail(initResult.cause())
                            }
                        }
                    } else {
                        logger.info("Clustering is disabled or Vert.x is not clustered")
                        startPromise.complete()
                    }
                } else {
                    val error = "Failed to get configuration: ${configResponse.getString("message", "Unknown error")}"
                    logger.error(error)
                    startPromise.fail(error)
                }
            } else {
                logger.error("Failed to get configuration", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }

    /**
     * Initialize the node counter.
     */
    private fun initializeNodeCounter(): Future<Void> {
        if (!vertx.isClustered()) {
            return Future.succeededFuture()
        }

        // Create a promise that will be completed when the counter is initialized
        val promise = Promise.promise<Void>()

        // Get or create the node counter
        vertx.sharedData().getCounter("apix.cluster.nodes") { ar ->
            if (ar.succeeded()) {
                val counter = ar.result()
                nodeCounter.set(counter)

                // Increment the counter to register this node
                counter.incrementAndGet { incAr ->
                    if (incAr.succeeded()) {
                        val nodeCount = incAr.result()
                        nodeId = "node-${vertx.hashCode()}-$nodeCount"
                        logger.info("Node registered with ID: $nodeId, total nodes: $nodeCount")

                        // Set up node departure handler
                        setupNodeDepartureHandler()

                        promise.complete()
                    } else {
                        logger.error("Failed to increment node counter", incAr.cause())
                        promise.fail(incAr.cause())
                    }
                }
            } else {
                logger.error("Failed to get node counter", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * Set up handler for node departure.
     */
    private fun setupNodeDepartureHandler() {
        vertx.exceptionHandler { err ->
            logger.error("Uncaught exception in Vert.x", err)
        }

        // We'll handle node departure in the stop method
    }

    /**
     * Handle get cluster configuration request.
     */
    private fun handleGetClusterConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val response = JsonObject()
            .put("success", true)
            .put("result", clusterConfig.toJson())

        message.reply(response)
    }

    /**
     * Handle get node info request.
     */
    private fun handleGetNodeInfo(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val nodeInfo = clusterService.getNodeInfo()
            .put("nodeId", nodeId)

        val response = JsonObject()
            .put("success", true)
            .put("result", nodeInfo)

        message.reply(response)
    }

    /**
     * Handle get cluster nodes request.
     */
    private fun handleGetClusterNodes(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!vertx.isClustered() || nodeCounter.get() == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "Clustering is not enabled or node counter is not initialized"))
            return
        }

        nodeCounter.get().get { ar ->
            if (ar.succeeded()) {
                val nodeCount = ar.result()

                val response = JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("nodeCount", nodeCount)
                        .put("currentNodeId", nodeId)
                    )

                message.reply(response)
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Failed to get node count: ${ar.cause().message}"))
            }
        }
    }

    /**
     * Handle get cluster metrics request.
     */
    private fun handleGetClusterMetrics(message: io.vertx.core.eventbus.Message<JsonObject>) {
        if (!vertx.isClustered() || !::clusterMonitor.isInitialized) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "Clustering is not enabled or cluster monitor is not initialized"))
            return
        }

        val metrics = clusterMonitor.getMetrics()

        val response = JsonObject()
            .put("success", true)
            .put("result", metrics)

        message.reply(response)
    }

    /**
     * Handle sync configuration request.
     */
    private fun handleSyncConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val action = request.getString("action", "get")

        if (action == "get") {
            clusterService.getConfig { ar ->
                if (ar.succeeded()) {
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", ar.result()))
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Failed to get config from cluster: ${ar.cause().message}"))
                }
            }
        } else if (action == "put") {
            val config = request.getJsonObject("config", JsonObject())
            clusterService.putConfig(config) { ar ->
                if (ar.succeeded()) {
                    message.reply(JsonObject()
                        .put("success", true))
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Failed to put config to cluster: ${ar.cause().message}"))
                }
            }
        } else {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "Unknown action: $action"))
        }
    }

    /**
     * Handle sync routes request.
     */
    private fun handleSyncRoutes(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val action = request.getString("action", "get")

        when (action) {
            "get" -> {
                clusterService.getRoutes { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", ar.result()))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to get routes from cluster: ${ar.cause().message}"))
                    }
                }
            }
            "put" -> {
                val id = request.getString("id")
                val route = request.getJsonObject("route", JsonObject())

                if (id == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Route ID is required"))
                    return
                }

                clusterService.putRoute(id, route) { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to put route to cluster: ${ar.cause().message}"))
                    }
                }
            }
            "remove" -> {
                val id = request.getString("id")

                if (id == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Route ID is required"))
                    return
                }

                clusterService.removeRoute(id) { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to remove route from cluster: ${ar.cause().message}"))
                    }
                }
            }
            else -> {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Unknown action: $action"))
            }
        }
    }

    /**
     * Handle sync services request.
     */
    private fun handleSyncServices(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val action = request.getString("action", "get")

        when (action) {
            "get" -> {
                clusterService.getServices { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", ar.result()))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to get services from cluster: ${ar.cause().message}"))
                    }
                }
            }
            "put" -> {
                val id = request.getString("id")
                val service = request.getJsonObject("service", JsonObject())

                if (id == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Service ID is required"))
                    return
                }

                clusterService.putService(id, service) { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to put service to cluster: ${ar.cause().message}"))
                    }
                }
            }
            "remove" -> {
                val id = request.getString("id")

                if (id == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Service ID is required"))
                    return
                }

                clusterService.removeService(id) { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to remove service from cluster: ${ar.cause().message}"))
                    }
                }
            }
            else -> {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Unknown action: $action"))
            }
        }
    }

    /**
     * Handle sync plugins request.
     */
    private fun handleSyncPlugins(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val action = request.getString("action", "get")

        when (action) {
            "get" -> {
                clusterService.getPlugins { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("result", ar.result()))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to get plugins from cluster: ${ar.cause().message}"))
                    }
                }
            }
            "put" -> {
                val id = request.getString("id")
                val plugin = request.getJsonObject("plugin", JsonObject())

                if (id == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Plugin ID is required"))
                    return
                }

                clusterService.putPlugin(id, plugin) { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to put plugin to cluster: ${ar.cause().message}"))
                    }
                }
            }
            "remove" -> {
                val id = request.getString("id")

                if (id == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Plugin ID is required"))
                    return
                }

                clusterService.removePlugin(id) { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("message", "Failed to remove plugin from cluster: ${ar.cause().message}"))
                    }
                }
            }
            else -> {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Unknown action: $action"))
            }
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping ClusterVerticle...")

        // Stop cluster monitor
        val monitorStopFuture = if (::clusterMonitor.isInitialized) {
            clusterMonitor.stop()
        } else {
            io.vertx.core.Future.succeededFuture<Void>()
        }

        // Stop cluster service
        val serviceStopFuture = if (::clusterService.isInitialized) {
            clusterService.stop()
        } else {
            io.vertx.core.Future.succeededFuture<Void>()
        }

        // Wait for both to stop
        val compositeFuture = io.vertx.core.CompositeFuture.all(
            monitorStopFuture as io.vertx.core.Future<Any>,
            serviceStopFuture as io.vertx.core.Future<Any>
        )

        compositeFuture.onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("ClusterVerticle stopped successfully")
                stopPromise.complete()
            } else {
                logger.error("Failed to stop ClusterVerticle", ar.cause())
                stopPromise.fail(ar.cause())
            }
        }
    }
}
