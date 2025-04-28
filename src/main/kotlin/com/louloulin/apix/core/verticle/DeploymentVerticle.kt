package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.models.Route
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * 负责 API 部署和卸载的 Verticle
 */
class DeploymentVerticle : BaseVerticle() {
    // 存储已部署的 API 路由信息
    private val deployedRoutes = ConcurrentHashMap<String, Route>()

    // 存储部署 ID 与路由 ID 的映射
    private val deploymentIdMap = ConcurrentHashMap<String, String>()

    override fun registerEventBusHandlers() {
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_DEPLOY_API, this::handleDeployApi)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_UNDEPLOY_API, this::handleUndeployApi)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_GET_STATUS, this::handleGetStatus)
    }

    override fun onStart(startPromise: Promise<Void>) {
        logger.info("DeploymentVerticle started successfully")
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
}
