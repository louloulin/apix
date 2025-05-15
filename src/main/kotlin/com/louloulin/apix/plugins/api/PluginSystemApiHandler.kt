package com.louloulin.apix.plugins.api

import com.louloulin.apix.plugins.verticle.PluginSystemVerticle
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory

/**
 * 插件系统API处理器
 * 提供插件系统优化的HTTP API
 */
class PluginSystemApiHandler(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginSystemApiHandler::class.java)

    /**
     * 注册路由
     *
     * @param router 路由器
     * @param basePath 基础路径
     */
    fun registerRoutes(router: Router, basePath: String = "/api") {
        logger.info("Registering plugin system API routes at {}/plugins/...", basePath)

        // 添加Body处理器
        router.route().handler(BodyHandler.create())

        // 获取配置
        router.get("$basePath/plugins/system/config").handler(this::handleGetConfig)

        // 获取状态
        router.get("$basePath/plugins/system/status").handler(this::handleGetStatus)

        // 获取统计信息
        router.get("$basePath/plugins/system/stats").handler(this::handleGetStats)

        // 执行插件
        router.post("$basePath/plugins/execute").handler(this::handleExecutePlugin)

        // 执行插件链
        router.post("$basePath/plugins/chain/execute").handler(this::handleExecutePluginChain)

        // 添加备用路径，以兼容测试脚本
        router.get("/plugins/system/config").handler(this::handleGetConfig)
        router.get("/plugins/system/status").handler(this::handleGetStatus)
        router.get("/plugins/system/stats").handler(this::handleGetStats)
        router.post("/plugins/execute").handler(this::handleExecutePlugin)
        router.post("/plugins/chain/execute").handler(this::handleExecutePluginChain)

        logger.info("Plugin system API routes registered successfully")
    }

    /**
     * 处理获取配置请求
     */
    private fun handleGetConfig(context: RoutingContext) {
        vertx.eventBus().request<JsonObject>(PluginSystemVerticle.EventBusAddresses.PLUGIN_SYSTEM_CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                context.response()
                    .putHeader("content-type", "application/json")
                    .end(ar.result().body().encode())
            } else {
                handleError(context, ar.cause())
            }
        }
    }

    /**
     * 处理获取状态请求
     */
    private fun handleGetStatus(context: RoutingContext) {
        vertx.eventBus().request<JsonObject>(PluginSystemVerticle.EventBusAddresses.PLUGIN_SYSTEM_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                context.response()
                    .putHeader("content-type", "application/json")
                    .end(ar.result().body().encode())
            } else {
                handleError(context, ar.cause())
            }
        }
    }

    /**
     * 处理获取统计信息请求
     */
    private fun handleGetStats(context: RoutingContext) {
        vertx.eventBus().request<JsonObject>(PluginSystemVerticle.EventBusAddresses.PLUGIN_SYSTEM_STATS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                context.response()
                    .putHeader("content-type", "application/json")
                    .end(ar.result().body().encode())
            } else {
                handleError(context, ar.cause())
            }
        }
    }

    /**
     * 处理执行插件请求
     */
    private fun handleExecutePlugin(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()

            if (body == null) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("message", "Request body is required")
                        .encode())
                return
            }

            val pluginId = body.getString("pluginId")
            val contextJson = body.getJsonObject("context")

            if (pluginId == null || contextJson == null) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("message", "pluginId and context are required")
                        .encode())
                return
            }

            val message = JsonObject()
                .put("pluginId", pluginId)
                .put("context", contextJson)

            vertx.eventBus().request<JsonObject>(PluginSystemVerticle.EventBusAddresses.PLUGIN_SYSTEM_EXECUTE, message) { ar ->
                if (ar.succeeded()) {
                    context.response()
                        .putHeader("content-type", "application/json")
                        .end(ar.result().body().encode())
                } else {
                    handleError(context, ar.cause())
                }
            }
        } catch (e: Exception) {
            handleError(context, e)
        }
    }

    /**
     * 处理执行插件链请求
     */
    private fun handleExecutePluginChain(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()

            if (body == null) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("message", "Request body is required")
                        .encode())
                return
            }

            val pluginIds = body.getJsonArray("pluginIds")
            val contextJson = body.getJsonObject("context")

            if (pluginIds == null || contextJson == null) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("message", "pluginIds and context are required")
                        .encode())
                return
            }

            val message = JsonObject()
                .put("pluginIds", pluginIds)
                .put("context", contextJson)

            vertx.eventBus().request<JsonObject>(PluginSystemVerticle.EventBusAddresses.PLUGIN_SYSTEM_CHAIN_EXECUTE, message) { ar ->
                if (ar.succeeded()) {
                    context.response()
                        .putHeader("content-type", "application/json")
                        .end(ar.result().body().encode())
                } else {
                    handleError(context, ar.cause())
                }
            }
        } catch (e: Exception) {
            handleError(context, e)
        }
    }

    /**
     * 处理错误
     */
    private fun handleError(context: RoutingContext, cause: Throwable) {
        logger.error("Error handling request", cause)

        context.response()
            .setStatusCode(500)
            .putHeader("content-type", "application/json")
            .end(JsonObject()
                .put("success", false)
                .put("message", cause.message)
                .encode())
    }
}
