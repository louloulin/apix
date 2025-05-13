package com.louloulin.apix.cluster

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.AsyncMap
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.common.EventBusAddresses

/**
 * Service for cluster operations and synchronization.
 */
class ClusterService(private val vertx: Vertx, private val clusterConfig: ClusterConfig) {
    private val logger = LoggerFactory.getLogger(ClusterService::class.java)

    // Shared data maps for configuration
    private var configMap: AsyncMap<String, String>? = null
    private var routesMap: AsyncMap<String, String>? = null
    private var servicesMap: AsyncMap<String, String>? = null
    private var pluginsMap: AsyncMap<String, String>? = null

    // Periodic sync timer ID
    private var syncTimerId: Long = -1

    /**
     * Initialize the cluster service.
     */
    fun initialize(): Future<Void> {
        if (!clusterConfig.enabled || !vertx.isClustered()) {
            logger.info("Cluster is not enabled or Vert.x is not clustered, skipping initialization")
            return Future.succeededFuture()
        }

        logger.info("Initializing cluster service")

        return initializeSharedMaps()
            .compose { setupPeriodicSync() }
    }

    /**
     * Set up periodic synchronization of data across cluster nodes.
     */
    private fun setupPeriodicSync(): Future<Void> {
        val syncInterval = clusterConfig.syncConfig.getInteger("syncInterval", 5000)
        logger.info("Setting up periodic sync with interval: $syncInterval ms")

        syncTimerId = vertx.setPeriodic(syncInterval.toLong()) { _ ->
            synchronizeData()
        }

        return Future.succeededFuture()
    }

    /**
     * Synchronize data across cluster nodes.
     */
    private fun synchronizeData() {
        if (!clusterConfig.enabled || !vertx.isClustered()) {
            return
        }

        logger.debug("Performing cluster data synchronization")

        // Get local configuration
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET_ALL, JsonObject()) { configAr ->
            if (configAr.succeeded()) {
                val configResponse = configAr.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // Synchronize configuration to cluster
                    putConfig(config) { putAr ->
                        if (putAr.succeeded()) {
                            logger.debug("Successfully synchronized configuration to cluster")
                        } else {
                            logger.warn("Failed to synchronize configuration to cluster", putAr.cause())
                        }
                    }
                }
            }
        }

        // Synchronize routes
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_GET_ALL, JsonObject()) { routesAr ->
            if (routesAr.succeeded()) {
                val routesResponse = routesAr.result().body()
                if (routesResponse.getBoolean("success", false)) {
                    val resultValue = routesResponse.getValue("result")
                    val routes = when (resultValue) {
                        is JsonArray -> resultValue
                        is JsonObject -> JsonArray().add(resultValue)
                        else -> JsonArray()
                    }

                    // Clear existing routes and add new ones
                    if (routesMap != null) {
                        routesMap!!.clear() { clearAr ->
                            if (clearAr.succeeded()) {
                                // Add each route to the cluster
                                for (i in 0 until routes.size()) {
                                    val route = routes.getJsonObject(i)
                                    val routeId = route.getString("id")
                                    if (routeId != null) {
                                        putRoute(routeId, route) { _ -> }
                                    }
                                }
                                logger.debug("Successfully synchronized routes to cluster")
                            } else {
                                logger.warn("Failed to clear routes in cluster", clearAr.cause())
                            }
                        }
                    }
                }
            }
        }

        // Synchronize services
        vertx.eventBus().request<JsonObject>(EventBusAddresses.SERVICE_GET_ALL, JsonObject()) { servicesAr ->
            if (servicesAr.succeeded()) {
                val servicesResponse = servicesAr.result().body()
                if (servicesResponse.getBoolean("success", false)) {
                    val resultValue = servicesResponse.getValue("result")
                    val services = when (resultValue) {
                        is JsonArray -> resultValue
                        is JsonObject -> JsonArray().add(resultValue)
                        else -> JsonArray()
                    }

                    // Clear existing services and add new ones
                    if (servicesMap != null) {
                        servicesMap!!.clear() { clearAr ->
                            if (clearAr.succeeded()) {
                                // Add each service to the cluster
                                for (i in 0 until services.size()) {
                                    val service = services.getJsonObject(i)
                                    val serviceId = service.getString("id")
                                    if (serviceId != null) {
                                        putService(serviceId, service) { _ -> }
                                    }
                                }
                                logger.debug("Successfully synchronized services to cluster")
                            } else {
                                logger.warn("Failed to clear services in cluster", clearAr.cause())
                            }
                        }
                    }
                }
            }
        }

        // Synchronize plugins
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_ALL, JsonObject()) { pluginsAr ->
            if (pluginsAr.succeeded()) {
                val pluginsResponse = pluginsAr.result().body()
                if (pluginsResponse.getBoolean("success", false)) {
                    val resultValue = pluginsResponse.getValue("result")
                    val plugins = when (resultValue) {
                        is JsonArray -> resultValue
                        is JsonObject -> JsonArray().add(resultValue)
                        else -> JsonArray()
                    }

                    // Clear existing plugins and add new ones
                    if (pluginsMap != null) {
                        pluginsMap!!.clear() { clearAr ->
                            if (clearAr.succeeded()) {
                                // Add each plugin to the cluster
                                for (i in 0 until plugins.size()) {
                                    val plugin = plugins.getJsonObject(i)
                                    val pluginId = plugin.getString("id")
                                    if (pluginId != null) {
                                        putPlugin(pluginId, plugin) { _ -> }
                                    }
                                }
                                logger.debug("Successfully synchronized plugins to cluster")
                            } else {
                                logger.warn("Failed to clear plugins in cluster", clearAr.cause())
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Initialize shared data maps.
     */
    private fun initializeSharedMaps(): Future<Void> {
        val configMapFuture = vertx.sharedData().getAsyncMap<String, String>(clusterConfig.syncConfig.getString("configPath", "/apix/config"))
        val routesMapFuture = vertx.sharedData().getAsyncMap<String, String>(clusterConfig.syncConfig.getString("routesPath", "/apix/routes"))
        val servicesMapFuture = vertx.sharedData().getAsyncMap<String, String>(clusterConfig.syncConfig.getString("servicesPath", "/apix/services"))
        val pluginsMapFuture = vertx.sharedData().getAsyncMap<String, String>(clusterConfig.syncConfig.getString("pluginsPath", "/apix/plugins"))

        return Future.all(configMapFuture, routesMapFuture, servicesMapFuture, pluginsMapFuture)
            .map { result ->
                configMap = result.resultAt(0)
                routesMap = result.resultAt(1)
                servicesMap = result.resultAt(2)
                pluginsMap = result.resultAt(3)
                null
            }
    }

    /**
     * Get configuration from the cluster.
     */
    fun getConfig(handler: Handler<AsyncResult<JsonObject>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || configMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        configMap!!.get("config") { ar ->
            if (ar.succeeded() && ar.result() != null) {
                try {
                    val config = JsonObject(ar.result())
                    handler.handle(Future.succeededFuture(config))
                } catch (e: Exception) {
                    logger.error("Failed to parse config from cluster", e)
                    handler.handle(Future.failedFuture(e))
                }
            } else {
                handler.handle(Future.failedFuture("Config not found in cluster or error: ${ar.cause()?.message}"))
            }
        }
    }

    /**
     * Put configuration to the cluster.
     */
    fun putConfig(config: JsonObject, handler: Handler<AsyncResult<Void>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || configMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        configMap!!.put("config", config.encode()) { ar ->
            if (ar.succeeded()) {
                logger.info("Config saved to cluster")
                handler.handle(Future.succeededFuture())
            } else {
                logger.error("Failed to save config to cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Get all routes from the cluster.
     */
    fun getRoutes(handler: Handler<AsyncResult<JsonArray>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || routesMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        routesMap!!.entries { ar ->
            if (ar.succeeded()) {
                val routes = JsonArray()
                ar.result().forEach { entry ->
                    try {
                        routes.add(JsonObject(entry.value))
                    } catch (e: Exception) {
                        logger.error("Failed to parse route from cluster: ${entry.key}", e)
                    }
                }
                handler.handle(Future.succeededFuture(routes))
            } else {
                logger.error("Failed to get routes from cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Put a route to the cluster.
     */
    fun putRoute(id: String, route: JsonObject, handler: Handler<AsyncResult<Void>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || routesMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        routesMap!!.put(id, route.encode()) { ar ->
            if (ar.succeeded()) {
                logger.info("Route $id saved to cluster")
                handler.handle(Future.succeededFuture())
            } else {
                logger.error("Failed to save route $id to cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Remove a route from the cluster.
     */
    fun removeRoute(id: String, handler: Handler<AsyncResult<Void>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || routesMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        routesMap!!.remove(id) { ar ->
            if (ar.succeeded()) {
                logger.info("Route $id removed from cluster")
                handler.handle(Future.succeededFuture())
            } else {
                logger.error("Failed to remove route $id from cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Get all services from the cluster.
     */
    fun getServices(handler: Handler<AsyncResult<JsonArray>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || servicesMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        servicesMap!!.entries { ar ->
            if (ar.succeeded()) {
                val services = JsonArray()
                ar.result().forEach { entry ->
                    try {
                        services.add(JsonObject(entry.value))
                    } catch (e: Exception) {
                        logger.error("Failed to parse service from cluster: ${entry.key}", e)
                    }
                }
                handler.handle(Future.succeededFuture(services))
            } else {
                logger.error("Failed to get services from cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Put a service to the cluster.
     */
    fun putService(id: String, service: JsonObject, handler: Handler<AsyncResult<Void>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || servicesMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        servicesMap!!.put(id, service.encode()) { ar ->
            if (ar.succeeded()) {
                logger.info("Service $id saved to cluster")
                handler.handle(Future.succeededFuture())
            } else {
                logger.error("Failed to save service $id to cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Remove a service from the cluster.
     */
    fun removeService(id: String, handler: Handler<AsyncResult<Void>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || servicesMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        servicesMap!!.remove(id) { ar ->
            if (ar.succeeded()) {
                logger.info("Service $id removed from cluster")
                handler.handle(Future.succeededFuture())
            } else {
                logger.error("Failed to remove service $id from cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Get all plugins from the cluster.
     */
    fun getPlugins(handler: Handler<AsyncResult<JsonArray>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || pluginsMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        pluginsMap!!.entries { ar ->
            if (ar.succeeded()) {
                val plugins = JsonArray()
                ar.result().forEach { entry ->
                    try {
                        plugins.add(JsonObject(entry.value))
                    } catch (e: Exception) {
                        logger.error("Failed to parse plugin from cluster: ${entry.key}", e)
                    }
                }
                handler.handle(Future.succeededFuture(plugins))
            } else {
                logger.error("Failed to get plugins from cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Put a plugin to the cluster.
     */
    fun putPlugin(id: String, plugin: JsonObject, handler: Handler<AsyncResult<Void>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || pluginsMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        pluginsMap!!.put(id, plugin.encode()) { ar ->
            if (ar.succeeded()) {
                logger.info("Plugin $id saved to cluster")
                handler.handle(Future.succeededFuture())
            } else {
                logger.error("Failed to save plugin $id to cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Remove a plugin from the cluster.
     */
    fun removePlugin(id: String, handler: Handler<AsyncResult<Void>>) {
        if (!clusterConfig.enabled || !vertx.isClustered() || pluginsMap == null) {
            handler.handle(Future.failedFuture("Cluster is not enabled or not initialized"))
            return
        }

        pluginsMap!!.remove(id) { ar ->
            if (ar.succeeded()) {
                logger.info("Plugin $id removed from cluster")
                handler.handle(Future.succeededFuture())
            } else {
                logger.error("Failed to remove plugin $id from cluster", ar.cause())
                handler.handle(Future.failedFuture(ar.cause()))
            }
        }
    }

    /**
     * Get cluster node information.
     */
    fun getNodeInfo(): JsonObject {
        val nodeId = vertx.isClustered() && vertx.isNativeTransportEnabled

        return JsonObject()
            .put("nodeId", vertx.hashCode().toString())
            .put("clustered", vertx.isClustered())
            .put("clusterType", clusterConfig.type)
    }

    /**
     * Stop the cluster service.
     */
    fun stop(): Future<Void> {
        logger.info("Stopping cluster service")

        if (syncTimerId != -1L) {
            vertx.cancelTimer(syncTimerId)
            syncTimerId = -1L
        }

        return Future.succeededFuture()
    }
}
