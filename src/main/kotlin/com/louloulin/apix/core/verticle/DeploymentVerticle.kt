package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.models.Route
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 负责 API 部署和卸载的 Verticle
 */
class DeploymentVerticle : BaseVerticle() {
    // 存储已部署的 API 路由信息
    private val deployedRoutes = ConcurrentHashMap<String, Route>()

    // 存储部署 ID 与路由 ID 的映射
    private val deploymentIdMap = ConcurrentHashMap<String, String>()

    // 存储所有路由信息
    private val routes = ConcurrentHashMap<String, Route>()

    override fun registerEventBusHandlers() {
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_DEPLOY_API, this::handleDeployApi)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_UNDEPLOY_API, this::handleUndeployApi)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_GET_STATUS, this::handleGetStatus)

        // 路由管理相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_GET_ALL, this::handleGetAllRoutes)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_GET_BY_ID, this::handleGetRouteById)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_CREATE, this::handleCreateRoute)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_UPDATE, this::handleUpdateRoute)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_DELETE, this::handleDeleteRoute)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_DEPLOY, this::handleDeployRoute)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_UNDEPLOY, this::handleUndeployRoute)
    }

    override fun onStart(startPromise: Promise<Void>) {
        // Add a default route for testing
        val defaultRoute = Route(
            id = "default-route",
            name = "Default Route",
            path = "/api",
            targetUrl = "http://localhost:8080",
            methods = listOf("GET", "POST", "PUT", "DELETE"),
            plugins = listOf(),
            enabled = true
        )

        routes[defaultRoute.id] = defaultRoute

        logger.info("DeploymentVerticle started successfully with default route")
        startPromise.complete()
    }

    /**
     * 处理部署 API 请求
     */
    private fun handleDeployApi(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeJson = message.body().getJsonObject("route")
        if (routeJson == null) {
            sendError(message, 400, "Route configuration is required")
            return
        }

        try {
            val route = Route(
                id = routeJson.getString("id"),
                name = routeJson.getString("name", routeJson.getString("id")),
                path = routeJson.getString("path"),
                targetUrl = routeJson.getString("targetUrl"),
                methods = routeJson.getJsonArray("methods")?.map { it.toString() } ?: listOf("GET", "POST", "PUT", "DELETE"),
                plugins = routeJson.getJsonArray("plugins")?.map { it.toString() } ?: emptyList(),
                enabled = routeJson.getBoolean("enabled", true)
            )

            // 检查路由是否已经部署
            if (deployedRoutes.containsKey(route.id)) {
                sendError(message, 409, "Route with ID ${route.id} is already deployed")
                return
            }

            // 部署路由
            deployRoute(route).onComplete { ar ->
                if (ar.succeeded()) {
                    val deploymentId = ar.result()
                    deployedRoutes[route.id] = route
                    deploymentIdMap[deploymentId] = route.id

                    val result = JsonObject()
                        .put("routeId", route.id)
                        .put("deploymentId", deploymentId)
                        .put("status", "deployed")

                    sendSuccess(message, result)
                } else {
                    sendError(message, ar.cause())
                }
            }
        } catch (e: Exception) {
            sendError(message, e)
        }
    }

    /**
     * 处理卸载 API 请求
     */
    private fun handleUndeployApi(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeId = message.body().getString("routeId")
        if (routeId == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        // 检查路由是否已部署
        val route = deployedRoutes[routeId]
        if (route == null) {
            sendError(message, 404, "Route with ID $routeId is not deployed")
            return
        }

        // 查找部署 ID
        val deploymentId = deploymentIdMap.entries.find { it.value == routeId }?.key
        if (deploymentId == null) {
            sendError(message, 500, "Deployment ID for route $routeId not found")
            return
        }

        // 卸载路由
        undeployRoute(deploymentId).onComplete { ar ->
            if (ar.succeeded()) {
                deployedRoutes.remove(routeId)
                deploymentIdMap.remove(deploymentId)

                val result = JsonObject()
                    .put("routeId", routeId)
                    .put("status", "undeployed")

                sendSuccess(message, result)
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取部署状态请求
     */
    private fun handleGetStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeId = message.body().getString("routeId")

        if (routeId != null) {
            // 获取特定路由的部署状态
            val route = deployedRoutes[routeId]
            if (route != null) {
                val result = JsonObject()
                    .put("routeId", routeId)
                    .put("status", "deployed")
                    .put("route", JsonObject.mapFrom(route))

                sendSuccess(message, result)
            } else {
                val result = JsonObject()
                    .put("routeId", routeId)
                    .put("status", "not_deployed")

                sendSuccess(message, result)
            }
        } else {
            // 获取所有路由的部署状态
            val routes = JsonArray()
            deployedRoutes.forEach { (id, route) ->
                routes.add(JsonObject()
                    .put("routeId", id)
                    .put("status", "deployed")
                    .put("route", JsonObject.mapFrom(route))
                )
            }

            sendSuccess(message, routes)
        }
    }

    /**
     * 部署路由
     */
    private fun deployRoute(route: Route): Future<String> {
        // 这里应该实际部署路由，例如创建一个新的 Verticle 或者向 RouteManager 发送消息
        // 为了简化示例，我们只是模拟部署过程
        return Future.future<String> { promise ->
            vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_DEPLOY, JsonObject.mapFrom(route)) { ar ->
                if (ar.succeeded()) {
                    val deploymentId = "deployment-" + route.id
                    promise.complete(deploymentId)
                } else {
                    promise.fail(ar.cause())
                }
            }
        }
    }

    /**
     * 卸载路由
     */
    private fun undeployRoute(deploymentId: String): Future<Void> {
        // 这里应该实际卸载路由，例如卸载 Verticle 或者向 RouteManager 发送消息
        // 为了简化示例，我们只是模拟卸载过程
        return Future.future<Void> { promise ->
            val routeId = deploymentIdMap[deploymentId]
            if (routeId != null) {
                vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_UNDEPLOY, JsonObject().put("routeId", routeId)) { ar ->
                    if (ar.succeeded()) {
                        promise.complete()
                    } else {
                        promise.fail(ar.cause())
                    }
                }
            } else {
                promise.fail("Deployment ID not found")
            }
        }
    }

    /**
     * 处理获取所有路由请求
     */
    private fun handleGetAllRoutes(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routesArray = JsonArray()
        routes.values.forEach { route ->
            routesArray.add(JsonObject.mapFrom(route))
        }

        val result = JsonObject()
            .put("routes", routesArray)

        sendSuccess(message, result)
    }

    /**
     * 处理获取路由请求
     */
    private fun handleGetRouteById(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeId = message.body().getString("routeId")
        if (routeId == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        val route = routes[routeId]
        if (route == null) {
            sendError(message, 404, "Route not found")
            return
        }

        val result = JsonObject()
            .put("route", JsonObject.mapFrom(route))

        sendSuccess(message, result)
    }

    /**
     * 处理创建路由请求
     */
    private fun handleCreateRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeJson = message.body().getJsonObject("route")
        if (routeJson == null) {
            sendError(message, 400, "Route configuration is required")
            return
        }

        try {
            // 生成路由 ID
            if (!routeJson.containsKey("id")) {
                routeJson.put("id", UUID.randomUUID().toString())
            }

            val route = Route.fromJson(routeJson)

            // 检查路由是否已存在
            if (routes.containsKey(route.id)) {
                sendError(message, 409, "Route with ID ${route.id} already exists")
                return
            }

            // 存储路由
            routes[route.id] = route

            // 如果路由启用，则部署路由
            if (route.enabled) {
                deployRoute(route).onComplete { ar ->
                    if (ar.succeeded()) {
                        val deploymentId = ar.result()
                        deployedRoutes[route.id] = route
                        deploymentIdMap[deploymentId] = route.id
                    } else {
                        logger.error("Failed to deploy route ${route.id}", ar.cause())
                    }
                }
            }

            val result = JsonObject()
                .put("route", JsonObject.mapFrom(route))

            sendSuccess(message, result, 201)
        } catch (e: Exception) {
            sendError(message, e)
        }
    }

    /**
     * 处理更新路由请求
     */
    private fun handleUpdateRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeId = message.body().getString("routeId")
        val routeJson = message.body().getJsonObject("route")

        if (routeId == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        if (routeJson == null) {
            sendError(message, 400, "Route configuration is required")
            return
        }

        // 检查路由是否存在
        if (!routes.containsKey(routeId)) {
            sendError(message, 404, "Route not found")
            return
        }

        try {
            // 确保路由 ID 不变
            routeJson.put("id", routeId)

            val updatedRoute = Route.fromJson(routeJson)

            // 检查路由是否已部署
            val isDeployed = deployedRoutes.containsKey(routeId)

            // 如果路由已部署，则先卸载
            if (isDeployed) {
                val deploymentId = deploymentIdMap.entries.find { it.value == routeId }?.key
                if (deploymentId != null) {
                    undeployRoute(deploymentId).onComplete { ar ->
                        if (ar.succeeded()) {
                            deployedRoutes.remove(routeId)
                            deploymentIdMap.remove(deploymentId)

                            // 更新路由
                            updateRouteAndDeploy(updatedRoute, message)
                        } else {
                            sendError(message, ar.cause())
                        }
                    }
                } else {
                    sendError(message, 500, "Deployment ID for route $routeId not found")
                }
            } else {
                // 直接更新路由
                updateRouteAndDeploy(updatedRoute, message)
            }
        } catch (e: Exception) {
            sendError(message, e)
        }
    }

    /**
     * 更新路由并部署
     */
    private fun updateRouteAndDeploy(route: Route, message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 更新路由
        routes[route.id] = route

        // 如果路由启用，则部署路由
        if (route.enabled) {
            deployRoute(route).onComplete { ar ->
                if (ar.succeeded()) {
                    val deploymentId = ar.result()
                    deployedRoutes[route.id] = route
                    deploymentIdMap[deploymentId] = route.id
                } else {
                    logger.error("Failed to deploy route ${route.id}", ar.cause())
                }
            }
        }

        val result = JsonObject()
            .put("route", JsonObject.mapFrom(route))

        sendSuccess(message, result)
    }

    /**
     * 处理删除路由请求
     */
    private fun handleDeleteRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeId = message.body().getString("routeId")
        if (routeId == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        // 检查路由是否存在
        if (!routes.containsKey(routeId)) {
            sendError(message, 404, "Route not found")
            return
        }

        // 检查路由是否已部署
        val isDeployed = deployedRoutes.containsKey(routeId)

        // 如果路由已部署，则先卸载
        if (isDeployed) {
            val deploymentId = deploymentIdMap.entries.find { it.value == routeId }?.key
            if (deploymentId != null) {
                undeployRoute(deploymentId).onComplete { ar ->
                    if (ar.succeeded()) {
                        deployedRoutes.remove(routeId)
                        deploymentIdMap.remove(deploymentId)

                        // 删除路由
                        routes.remove(routeId)

                        sendSuccess(message, null, 204)
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } else {
                sendError(message, 500, "Deployment ID for route $routeId not found")
            }
        } else {
            // 直接删除路由
            routes.remove(routeId)

            sendSuccess(message, null, 204)
        }
    }

    /**
     * 处理部署路由请求
     */
    private fun handleDeployRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeJson = message.body()

        try {
            val route = Route.fromJson(routeJson)

            // 模拟部署过程
            val deploymentId = "deployment-" + route.id

            sendSuccess(message, JsonObject().put("deploymentId", deploymentId))
        } catch (e: Exception) {
            sendError(message, e)
        }
    }

    /**
     * 处理卸载路由请求
     */
    private fun handleUndeployRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val routeId = message.body().getString("routeId")

        if (routeId == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        // 模拟卸载过程
        sendSuccess(message, null)
    }
}
