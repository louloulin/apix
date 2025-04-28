package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.models.Route
import io.vertx.core.Promise
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerHandler
import java.util.UUID

/**
 * 负责管理 API 接口的 Verticle
 */
class AdminVerticle : BaseVerticle() {
    private lateinit var configManager: ConfigManager
    private lateinit var httpServer: HttpServer
    private lateinit var router: Router

    // 管理 API 的端口和主机
    private var adminPort = 8081
    private var adminHost = "localhost"

    override fun registerEventBusHandlers() {
        // 路由管理相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_GET_ROUTES, this::handleGetRoutes)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_GET_ROUTE_BY_ID, this::handleGetRouteById)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_CREATE_ROUTE, this::handleCreateRoute)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_UPDATE_ROUTE, this::handleUpdateRoute)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_DELETE_ROUTE, this::handleDeleteRoute)

        // 插件管理相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_GET_PLUGINS, this::handleGetPlugins)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_GET_PLUGIN_BY_ID, this::handleGetPluginById)

        // 配置管理相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_GET_CONFIG, this::handleGetConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_UPDATE_CONFIG, this::handleUpdateConfig)

        // 系统管理相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_GET_SYSTEM_INFO, this::handleGetSystemInfo)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ADMIN_GET_METRICS, this::handleGetMetrics)

        // 集群管理相关处理器
        vertx.eventBus().consumer<JsonObject>("apix.admin.cluster.config.get", this::handleGetClusterConfig)
        vertx.eventBus().consumer<JsonObject>("apix.admin.cluster.node.info", this::handleGetClusterNodeInfo)
        vertx.eventBus().consumer<JsonObject>("apix.admin.cluster.nodes.get", this::handleGetClusterNodes)
    }

    override fun onStart(startPromise: Promise<Void>) {
        configManager = ConfigManager(vertx)

        // 从配置中获取管理 API 的端口和主机
        adminPort = configManager.getConfig().getInteger("admin.port", 8081)
        adminHost = configManager.getConfig().getString("admin.host", "localhost")

        // 创建路由器
        router = Router.router(vertx)

        // 设置路由处理器
        setupRoutes()

        // 创建 HTTP 服务器
        httpServer = vertx.createHttpServer()
            .requestHandler(router)
            .listen(adminPort, adminHost) { ar ->
                if (ar.succeeded()) {
                    logger.info("AdminVerticle started on {}:{}", adminHost, adminPort)
                    startPromise.complete()
                } else {
                    logger.error("Failed to start AdminVerticle", ar.cause())
                    startPromise.fail(ar.cause())
                }
            }
    }

    /**
     * 设置路由处理器
     */
    private fun setupRoutes() {
        // 添加通用处理器
        router.route().handler(LoggerHandler.create())
        router.route().handler(BodyHandler.create())
        router.route().handler(CorsHandler.create("*")
            .allowedHeaders(setOf("Content-Type", "Authorization"))
            .allowedMethods(setOf(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS
            ))
        )

        // 路由管理 API
        router.get("/api/routes").handler(this::getRoutes)
        router.get("/api/routes/:id").handler(this::getRouteById)
        router.post("/api/routes").handler(this::createRoute)
        router.put("/api/routes/:id").handler(this::updateRoute)
        router.delete("/api/routes/:id").handler(this::deleteRoute)

        // 插件管理 API
        router.get("/api/plugins").handler(this::getPlugins)
        router.get("/api/plugins/:id").handler(this::getPluginById)

        // 配置管理 API
        router.get("/api/config").handler(this::getConfig)
        router.put("/api/config").handler(this::updateConfig)

        // 系统管理 API
        router.get("/api/system/info").handler(this::getSystemInfo)
        router.get("/api/system/metrics").handler(this::getMetrics)

        // 集群管理 API
        router.get("/api/cluster/config").handler(this::getClusterConfig)
        router.get("/api/cluster/node").handler(this::getClusterNodeInfo)
        router.get("/api/cluster/nodes").handler(this::getClusterNodes)
        router.get("/api/cluster/metrics").handler(this::getClusterMetrics)

        // AI 管理 API
        router.get("/api/ai/models").handler(this::getAIModels)
        router.get("/api/ai/usage").handler(this::getAIUsage)
        router.post("/api/ai/cache/clear").handler(this::clearAICache)

        // AI 模型路由 API
        router.get("/api/ai/model/rules").handler(this::getModelRules)
        router.post("/api/ai/model/rules").handler(this::addModelRule)
        router.delete("/api/ai/model/rules/:id").handler(this::removeModelRule)
        router.post("/api/ai/model/rules/clear").handler(this::clearModelRules)
        router.post("/api/ai/model/route").handler(this::routeToModel)

        // 健康检查 API
        router.get("/health").handler { ctx ->
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("status", "UP").encode())
        }
    }

    /**
     * 获取所有路由
     */
    private fun getRoutes(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_GET_ROUTES, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("routes", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get routes: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取路由详情
     */
    private fun getRouteById(ctx: RoutingContext) {
        val id = ctx.pathParam("id")

        if (id == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Route ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_GET_ROUTE_BY_ID, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("route", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get route: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 创建路由
     */
    private fun createRoute(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Request body is required")
                    .encode()
                )
            return
        }

        // 生成路由 ID
        if (!body.containsKey("id")) {
            body.put("id", UUID.randomUUID().toString())
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_CREATE, JsonObject().put("route", body)) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .setStatusCode(201)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("route", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to create route: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 更新路由
     */
    private fun updateRoute(ctx: RoutingContext) {
        val id = ctx.pathParam("id")
        val body = ctx.body().asJsonObject()

        if (id == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Route ID is required")
                    .encode()
                )
            return
        }

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Request body is required")
                    .encode()
                )
            return
        }

        // 添加 ID 到请求体
        body.put("id", id)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_UPDATE_ROUTE, body) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("route", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to update route: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 删除路由
     */
    private fun deleteRoute(ctx: RoutingContext) {
        val id = ctx.pathParam("id")

        if (id == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Route ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_DELETE_ROUTE, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .setStatusCode(204)
                        .end()
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to delete route: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取所有插件
     */
    private fun getPlugins(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_GET_PLUGINS, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("plugins", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get plugins: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取插件详情
     */
    private fun getPluginById(ctx: RoutingContext) {
        val id = ctx.pathParam("id")

        if (id == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Plugin ID is required")
                    .encode()
                )
            return
        }

        val message = JsonObject().put("id", id)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_GET_PLUGIN_BY_ID, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("plugin", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get plugin: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取配置
     */
    private fun getConfig(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_GET_CONFIG, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("config", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get config: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 更新配置
     */
    private fun updateConfig(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Request body is required")
                    .encode()
                )
            return
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_UPDATE_CONFIG, body) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("config", response.getValue("result")).encode())
                } else {
                    val errorCode = response.getInteger("errorCode", 500)
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to update config: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取系统信息
     */
    private fun getSystemInfo(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_GET_SYSTEM_INFO, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("system", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get system info: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取指标
     */
    private fun getMetrics(ctx: RoutingContext) {
        val type = ctx.request().getParam("type")

        val message = JsonObject()
        if (type != null) {
            message.put("type", type)
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ADMIN_GET_METRICS, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("metrics", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get metrics: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 处理获取所有路由请求
     */
    private fun handleGetRoutes(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到 DeploymentVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取路由详情请求
     */
    private fun handleGetRouteById(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        // 转发到 DeploymentVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_GET_BY_ID, JsonObject().put("id", id)) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理创建路由请求
     */
    private fun handleCreateRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val body = message.body()

        // 转发到 DeploymentVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_CREATE, body) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理更新路由请求
     */
    private fun handleUpdateRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val body = message.body()
        val id = body.getString("id")

        if (id == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        // 转发到 DeploymentVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_UPDATE, body) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理删除路由请求
     */
    private fun handleDeleteRoute(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Route ID is required")
            return
        }

        // 转发到 DeploymentVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_DELETE, JsonObject().put("id", id)) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取所有插件请求
     */
    private fun handleGetPlugins(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到 PluginVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取插件详情请求
     */
    private fun handleGetPluginById(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Plugin ID is required")
            return
        }

        // 转发到 PluginVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, JsonObject().put("id", id)) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取配置请求
     */
    private fun handleGetConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到 ConfigVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理更新配置请求
     */
    private fun handleUpdateConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val body = message.body()

        // 转发到 ConfigVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_UPDATE, body) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取系统信息请求
     */
    private fun handleGetSystemInfo(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到 MonitorVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.SYSTEM_INFO, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取指标请求
     */
    private fun handleGetMetrics(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val type = message.body().getString("type")

        val metricsMessage = JsonObject()
        if (type != null) {
            metricsMessage.put("type", type)
        }

        // 转发到 MonitorVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.METRICS_GET, metricsMessage) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 获取集群配置
     */
    private fun getClusterConfig(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("config", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get cluster config: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取集群节点信息
     */
    private fun getClusterNodeInfo(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_NODE_INFO, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("node", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get cluster node info: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取集群节点列表
     */
    private fun getClusterNodes(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_NODES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("nodes", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get cluster nodes: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取集群指标
     */
    private fun getClusterMetrics(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_METRICS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("metrics", response.getValue("result")).encode())
                } else {
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", response.getString("error", "Unknown error"))
                            .encode()
                        )
                }
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get cluster metrics: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 处理获取集群配置请求
     */
    private fun handleGetClusterConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到 ClusterVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取集群节点信息请求
     */
    private fun handleGetClusterNodeInfo(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到 ClusterVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_NODE_INFO, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理获取集群节点列表请求
     */
    private fun handleGetClusterNodes(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到 ClusterVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_NODES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 获取模型路由规则
     */
    private fun getModelRules(ctx: RoutingContext) {
        vertx.eventBus().request<JsonArray>(EventBusAddresses.AI_MODEL_RULES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val rules = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("rules", rules).encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get model rules: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 添加模型路由规则
     */
    private fun addModelRule(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Request body is required").encode())
            return
        }

        val rule = body.getJsonObject("rule")

        if (rule == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Rule is required").encode())
            return
        }

        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_MODEL_RULE_ADD,
            JsonObject().put("rule", rule)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject().put("rule", response).encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to add model rule: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 删除模型路由规则
     */
    private fun removeModelRule(ctx: RoutingContext) {
        val ruleId = ctx.pathParam("id")

        if (ruleId == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Rule ID is required").encode())
            return
        }

        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_MODEL_RULE_REMOVE,
            JsonObject().put("id", ruleId)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to remove model rule: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 清空模型路由规则
     */
    private fun clearModelRules(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_RULES_CLEAR, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to clear model rules: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 路由到模型
     */
    private fun routeToModel(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Request body is required").encode())
            return
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_ROUTE, body) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to route to model: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取 AI 模型列表
     */
    private fun getAIModels(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_MODEL_LIST, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get AI models: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 获取 AI 使用情况
     */
    private fun getAIUsage(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_USAGE_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get AI usage: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 清空 AI 缓存
     */
    private fun clearAICache(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject() ?: JsonObject()

        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_CACHE_CLEAR, body) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to clear AI cache: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping AdminVerticle...")

        // 关闭 HTTP 服务器
        httpServer.close { ar ->
            if (ar.succeeded()) {
                logger.info("AdminVerticle HTTP server closed")
                stopPromise.complete()
            } else {
                logger.error("Failed to close AdminVerticle HTTP server", ar.cause())
                stopPromise.fail(ar.cause())
            }
        }
    }
}
