package com.louloulin.apix.admin

import com.louloulin.apix.core.PluginChain
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import com.louloulin.apix.plugins.UnifiedPluginManager
import com.louloulin.apix.plugins.version.PluginVersion
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory

/**
 * 管理 API 的 Verticle
 * 提供插件管理、系统监控等功能
 */
class AdminVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(AdminVerticle::class.java)
    private lateinit var server: HttpServer
    private lateinit var pluginManager: UnifiedPluginManager

    override fun start(startPromise: Promise<Void>) {
        pluginManager = UnifiedPluginManager.getInstance(vertx)

        val router = Router.router(vertx)

        // 配置 CORS
        router.route().handler(
            CorsHandler.create("*")
                .allowedMethod(HttpMethod.GET)
                .allowedMethod(HttpMethod.POST)
                .allowedMethod(HttpMethod.PUT)
                .allowedMethod(HttpMethod.DELETE)
                .allowedMethod(HttpMethod.OPTIONS)
                .allowedHeader("Content-Type")
                .allowedHeader("Authorization")
        )

        // 配置请求体处理
        router.route().handler(BodyHandler.create())

        // 健康检查端点
        router.get("/health").handler { ctx ->
            ctx.response()
                .putHeader("content-type", "application/json")
                .end(JsonObject().put("status", "UP").encode())
        }

        // 插件管理 API
        setupPluginRoutes(router)

        // 系统监控 API
        setupMonitoringRoutes(router)

        // 启动 HTTP 服务器
        server = vertx.createHttpServer()
        server.requestHandler(router)
            .listen(8081) { result ->
                if (result.succeeded()) {
                    logger.info("Admin API server started on port 8081")
                    startPromise.complete()
                } else {
                    logger.error("Failed to start Admin API server", result.cause())
                    startPromise.fail(result.cause())
                }
            }
    }

    override fun stop(stopPromise: Promise<Void>) {
        server.close { result ->
            if (result.succeeded()) {
                logger.info("Admin API server stopped")
                stopPromise.complete()
            } else {
                logger.error("Failed to stop Admin API server", result.cause())
                stopPromise.fail(result.cause())
            }
        }
    }

    /**
     * 设置插件管理路由
     */
    private fun setupPluginRoutes(router: Router) {
        // 获取所有插件
        router.get("/api/plugins").handler { ctx ->
            val plugins = pluginManager.getAllPlugins()
            val response = JsonObject()
                .put("plugins", JsonArray(plugins.map { plugin ->
                    JsonObject()
                        .put("id", plugin.id)
                        .put("type", plugin.type)
                        .put("config", plugin.config.config)
                        .put("status", if (pluginManager.isPluginEnabled(plugin.id)) "enabled" else "disabled")
                }))

            ctx.response()
                .putHeader("content-type", "application/json")
                .end(response.encode())
        }

        // 获取单个插件
        router.get("/api/plugins/:id").handler { ctx ->
            val id = ctx.pathParam("id")
            val plugin = pluginManager.getPlugin(id)

            if (plugin != null) {
                val response = JsonObject()
                    .put("plugin", JsonObject()
                        .put("id", plugin.id)
                        .put("type", plugin.type)
                        .put("config", plugin.config.config)
                        .put("status", if (pluginManager.isPluginEnabled(plugin.id)) "enabled" else "disabled")
                    )

                ctx.response()
                    .putHeader("content-type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin not found")
                        .put("id", id)
                        .encode()
                    )
            }
        }

        // 创建插件
        router.post("/api/plugins").handler { ctx ->
            val body = ctx.body().asJsonObject()

            if (body == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Invalid request body")
                        .encode()
                    )
                return@handler
            }

            val id = body.getString("id")
            val type = body.getString("type")
            val config = body.getJsonObject("config", JsonObject())

            if (id == null || type == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Missing required fields: id, type")
                        .encode()
                    )
                return@handler
            }

            // 检查插件是否已存在
            if (pluginManager.hasPlugin(id)) {
                ctx.response()
                    .setStatusCode(409)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin already exists")
                        .put("id", id)
                        .encode()
                    )
                return@handler
            }

            // 创建插件配置
            val pluginConfig = PluginConfig(id, type, config)

            // 创建插件
            pluginManager.createPlugin(pluginConfig)
                .onSuccess { pluginId ->
                    // 获取创建的插件
                    val plugin = pluginManager.getPlugin(pluginId)

                    if (plugin != null) {
                        // 如果请求中指定了启用状态，则设置插件状态
                        val enabled = body.getBoolean("enabled", true)
                        pluginManager.setPluginEnabled(pluginId, enabled)

                        val response = JsonObject()
                            .put("success", true)
                            .put("plugin", JsonObject()
                                .put("id", plugin.id)
                                .put("type", plugin.type)
                                .put("config", plugin.config.config)
                                .put("status", if (pluginManager.isPluginEnabled(plugin.id)) "enabled" else "disabled")
                            )

                        ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(response.encode())
                    } else {
                        ctx.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json")
                            .end(JsonObject()
                                .put("error", "Failed to retrieve created plugin")
                                .encode()
                            )
                    }
                }
                .onFailure { err ->
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("error", "Failed to create plugin: ${err.message}")
                            .encode()
                        )
                }
        }

        // 更新插件
        router.put("/api/plugins/:id").handler { ctx ->
            val id = ctx.pathParam("id")
            val body = ctx.body().asJsonObject()

            if (body == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Invalid request body")
                        .encode()
                    )
                return@handler
            }

            // 检查插件是否存在
            if (!pluginManager.hasPlugin(id)) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin not found")
                        .put("id", id)
                        .encode()
                    )
                return@handler
            }

            // 更新插件
            pluginManager.updatePlugin(body.put("id", id))
                .onSuccess {
                    // 如果请求中指定了启用状态，则设置插件状态
                    val status = body.getString("status")
                    if (status != null) {
                        pluginManager.setPluginEnabled(id, status == "enabled")
                    }

                    // 获取更新后的插件
                    val plugin = pluginManager.getPlugin(id)

                    if (plugin != null) {
                        val response = JsonObject()
                            .put("success", true)
                            .put("plugin", JsonObject()
                                .put("id", plugin.id)
                                .put("type", plugin.type)
                                .put("config", plugin.config.config)
                                .put("status", if (pluginManager.isPluginEnabled(plugin.id)) "enabled" else "disabled")
                            )

                        ctx.response()
                            .putHeader("content-type", "application/json")
                            .end(response.encode())
                    } else {
                        ctx.response()
                            .setStatusCode(500)
                            .putHeader("content-type", "application/json")
                            .end(JsonObject()
                                .put("error", "Failed to retrieve updated plugin")
                                .encode()
                            )
                    }
                }
                .onFailure { err ->
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("error", "Failed to update plugin: ${err.message}")
                            .encode()
                        )
                }
        }

        // 删除插件
        router.delete("/api/plugins/:id").handler { ctx ->
            val id = ctx.pathParam("id")

            // 检查插件是否存在
            if (!pluginManager.hasPlugin(id)) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin not found")
                        .put("id", id)
                        .encode()
                    )
                return@handler
            }

            // 删除插件
            pluginManager.unloadPlugin(id)
                .onSuccess {
                    ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .encode()
                        )
                }
                .onFailure { err ->
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("error", "Failed to delete plugin: ${err.message}")
                            .encode()
                        )
                }
        }

        // 启用插件
        router.post("/api/plugins/:id/enable").handler { ctx ->
            val id = ctx.pathParam("id")

            // 检查插件是否存在
            if (!pluginManager.hasPlugin(id)) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin not found")
                        .put("id", id)
                        .encode()
                    )
                return@handler
            }

            // 启用插件
            pluginManager.setPluginEnabled(id, true)
                .onSuccess {
                    ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .encode()
                        )
                }
                .onFailure { err ->
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("error", "Failed to enable plugin: ${err.message}")
                            .encode()
                        )
                }
        }

        // 禁用插件
        router.post("/api/plugins/:id/disable").handler { ctx ->
            val id = ctx.pathParam("id")

            // 检查插件是否存在
            if (!pluginManager.hasPlugin(id)) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin not found")
                        .put("id", id)
                        .encode()
                    )
                return@handler
            }

            // 禁用插件
            pluginManager.setPluginEnabled(id, false)
                .onSuccess {
                    ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .encode()
                        )
                }
                .onFailure { err ->
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("error", "Failed to disable plugin: ${err.message}")
                            .encode()
                        )
                }
        }

        // 重新加载插件
        router.post("/api/plugins/:id/reload").handler { ctx ->
            val id = ctx.pathParam("id")

            // 检查插件是否存在
            if (!pluginManager.hasPlugin(id)) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin not found")
                        .put("id", id)
                        .encode()
                    )
                return@handler
            }

            // 获取插件
            val plugin = pluginManager.getPlugin(id)

            if (plugin == null) {
                ctx.response()
                    .setStatusCode(404)
                    .putHeader("content-type", "application/json")
                    .end(JsonObject()
                        .put("error", "Plugin not found")
                        .put("id", id)
                        .encode()
                    )
                return@handler
            }

            // 获取插件状态
            val wasEnabled = pluginManager.isPluginEnabled(id)

            // 卸载插件
            pluginManager.unloadPlugin(id)
                .compose {
                    // 重新创建插件
                    pluginManager.createPlugin(plugin.config)
                }
                .compose { newId ->
                    // 恢复插件状态
                    pluginManager.setPluginEnabled(newId, wasEnabled)
                }
                .onSuccess {
                    ctx.response()
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("success", true)
                            .encode()
                        )
                }
                .onFailure { err ->
                    ctx.response()
                        .setStatusCode(500)
                        .putHeader("content-type", "application/json")
                        .end(JsonObject()
                            .put("error", "Failed to reload plugin: ${err.message}")
                            .encode()
                        )
                }
        }

        // 获取可用的插件类型
        router.get("/api/plugins/types").handler { ctx ->
            // 获取所有注册的插件工厂
            val factories = mutableMapOf<String, PluginFactory>()

            // 这里应该从插件管理器获取可用的插件类型
            // 由于我们没有直接的方法，这里模拟一些常见类型
            val types = JsonArray()

            // 添加一些常见的插件类型
            types.add(JsonObject()
                .put("id", "authentication")
                .put("name", "Authentication")
                .put("description", "Handles user authentication and authorization")
                .put("defaultConfig", JsonObject()
                    .put("provider", "jwt")
                    .put("secret", "your-secret-key")
                )
            )

            types.add(JsonObject()
                .put("id", "security")
                .put("name", "Security")
                .put("description", "Provides security features like rate limiting, IP filtering, etc.")
                .put("defaultConfig", JsonObject()
                    .put("rateLimit", 100)
                    .put("timeWindow", 60000)
                )
            )

            types.add(JsonObject()
                .put("id", "transformation")
                .put("name", "Transformation")
                .put("description", "Transforms request/response data")
                .put("defaultConfig", JsonObject()
                    .put("requestTransform", JsonObject())
                    .put("responseTransform", JsonObject())
                )
            )

            types.add(JsonObject()
                .put("id", "business-logic")
                .put("name", "Business Logic")
                .put("description", "Implements custom business logic")
                .put("defaultConfig", JsonObject()
                    .put("script", "function process(request, response) { return response; }")
                )
            )

            ctx.response()
                .putHeader("content-type", "application/json")
                .end(JsonObject()
                    .put("types", types)
                    .encode()
                )
        }
    }

    /**
     * 设置系统监控路由
     */
    private fun setupMonitoringRoutes(router: Router) {
        // 获取系统指标
        router.get("/api/metrics").handler { ctx ->
            val metrics = JsonObject()
                .put("cpu", JsonObject()
                    .put("usage", 0.5)
                    .put("cores", Runtime.getRuntime().availableProcessors())
                )
                .put("memory", JsonObject()
                    .put("total", Runtime.getRuntime().totalMemory())
                    .put("free", Runtime.getRuntime().freeMemory())
                    .put("max", Runtime.getRuntime().maxMemory())
                )
                .put("uptime", System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().startTime)

            ctx.response()
                .putHeader("content-type", "application/json")
                .end(metrics.encode())
        }

        // 获取路由信息
        router.get("/api/routes").handler { ctx ->
            val routes = JsonArray()

            // 这里应该从路由管理器获取所有路由
            // 由于我们没有直接的方法，这里模拟一些路由
            routes.add(JsonObject()
                .put("path", "/api/v1/users")
                .put("method", "GET")
                .put("plugins", JsonArray().add("authentication").add("rate-limiter"))
            )

            routes.add(JsonObject()
                .put("path", "/api/v1/users")
                .put("method", "POST")
                .put("plugins", JsonArray().add("authentication").add("validation"))
            )

            ctx.response()
                .putHeader("content-type", "application/json")
                .end(JsonObject()
                    .put("routes", routes)
                    .encode()
                )
        }
    }
}
