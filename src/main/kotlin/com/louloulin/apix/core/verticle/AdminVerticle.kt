package com.louloulin.apix.core.verticle

import com.louloulin.apix.admin.DashboardHandler
import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.metrics.MetricsCollector
import com.louloulin.apix.models.Route
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import com.louloulin.apix.plugins.UnifiedPluginManager
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerHandler
import java.lang.management.ManagementFactory
import java.util.UUID

/**
 * 负责管理 API 接口的 Verticle
 */
class AdminVerticle : BaseVerticle() {
    private lateinit var httpServer: HttpServer
    private lateinit var router: Router
    private lateinit var configManager: ConfigManager
    private lateinit var pluginManager: UnifiedPluginManager
    private lateinit var metricsCollector: MetricsCollector
    private lateinit var dashboardHandler: DashboardHandler

    override fun registerEventBusHandlers() {
        // 不需要注册 EventBus 处理器
    }

    override fun onStart(startPromise: Promise<Void>) {
        logger.info("Starting AdminVerticle...")

        // 初始化配置管理器
        configManager = ConfigManager(vertx)

        // 初始化插件管理器
        pluginManager = UnifiedPluginManager.getInstance(vertx)

        // 初始化指标收集器
        metricsCollector = MetricsCollector(vertx)

        // 初始化仪表盘处理器
        dashboardHandler = DashboardHandler(metricsCollector)

        // 创建 HTTP 服务器与高并发优化设置
        val serverOptions = HttpServerOptions()
            // TCP 优化
            .setTcpNoDelay(true)              // 禁用 Nagle 算法以降低延迟
            .setTcpFastOpen(true)             // 启用 TCP Fast Open 以加快连接
            .setTcpQuickAck(true)             // 启用 TCP Quick ACK 以提高响应性

            // 套接字重用
            .setReusePort(true)               // 启用端口重用以提高负载分配
            .setReuseAddress(true)            // 启用地址重用以加快重启

            // 连接处理
            .setAcceptBacklog(10000)          // 增加接受队列大小
            .setIdleTimeout(0)                // 禁用空闲超时

            // 性能优化
            .setHandle100ContinueAutomatically(true) // 自动处理 100-Continue
            .setCompressionSupported(true)     // 启用压缩

        httpServer = vertx.createHttpServer(serverOptions)

        // 创建路由器
        router = Router.router(vertx)

        // 添加 CORS 处理器
        val corsHandler = CorsHandler.create("*")
            .allowedMethods(setOf(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS
            ))
            .allowedHeaders(setOf(
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "X-API-Key"
            ))

        router.route().handler(corsHandler)

        // 添加日志处理器
        router.route().handler(LoggerHandler.create())

        // 添加请求体处理器
        router.route().handler(BodyHandler.create())

        // 设置 API 路由
        setupRoutes()

        // 启动 HTTP 服务器
        val adminConfig = configManager.getConfig().getJsonObject("admin", JsonObject())
        val port = adminConfig.getInteger("port", 8071)
        val host = adminConfig.getString("host", "0.0.0.0")

        logger.info("AdminVerticle 将使用端口: {}, 配置: {}", port, adminConfig.encodePrettily())

        // 设置端口和主机
        httpServer.requestHandler(router).listen(port, host) { ar ->
            if (ar.succeeded()) {
                logger.info("AdminVerticle HTTP server started on {}:{}", host, port)
                startPromise.complete()
            } else {
                logger.error("Failed to start AdminVerticle HTTP server", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }

    /**
     * 设置 API 路由
     */
    private fun setupRoutes() {
        // 并发控制 API
        router.get("/api/concurrency/metrics").handler(this::getConcurrencyMetrics)
        router.get("/api/concurrency/metrics/:serviceId").handler(this::getConcurrencyMetrics)
        router.post("/api/concurrency/limits").handler(this::setConcurrencyLimit)
        router.post("/api/concurrency/reset").handler(this::resetConcurrencyMetrics)

        // 内存管理 API
        router.get("/api/memory/usage").handler(this::getMemoryUsage)
        router.post("/api/memory/gc").handler(this::triggerGC)
        router.post("/api/memory/cache/clear").handler(this::clearMemoryCache)

        // 对象池管理 API
        router.get("/api/memory/object-pools").handler(this::getObjectPoolStats)
        router.post("/api/memory/object-pools").handler(this::createObjectPool)
        router.delete("/api/memory/object-pools/:name").handler(this::removeObjectPool)

        // 压测用端点
        router.get("/api/hello").handler(this::helloWorld)
        router.get("/api/hello/:name").handler(this::helloName)

        // 健康检查端点
        router.get("/health").handler { ctx ->
            ctx.response()
                .putHeader("content-type", "application/json")
                .end(JsonObject().put("status", "UP").encode())
        }

        // 插件管理 API
        setupPluginRoutes()

        // 系统监控 API
        setupMonitoringRoutes()

        // 仪表盘 API
        setupDashboardRoutes()
    }

    /**
     * 获取并发控制指标
     */
    private fun getConcurrencyMetrics(ctx: RoutingContext) {
        val serviceId = ctx.pathParam("serviceId")

        val request = JsonObject()
        if (serviceId != null) {
            request.put("serviceId", serviceId)
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONCURRENCY_GET_METRICS, request) { ar ->
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
                        .put("error", "Failed to get concurrency metrics: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 设置并发限制
     */
    private fun setConcurrencyLimit(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Request body is required").encode())
            return
        }

        val serviceId = body.getString("serviceId")
        val limit = body.getInteger("limit")

        if (serviceId == null || limit == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Missing required parameters: serviceId and limit")
                    .encode()
                )
            return
        }

        val request = JsonObject()
            .put("serviceId", serviceId)
            .put("limit", limit)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONCURRENCY_SET_LIMIT, request) { ar ->
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
                        .put("error", "Failed to set concurrency limit: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 重置并发控制指标
     */
    private fun resetConcurrencyMetrics(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        val serviceId = if (body != null) body.getString("serviceId") else null

        val request = JsonObject()
        if (serviceId != null) {
            request.put("serviceId", serviceId)
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONCURRENCY_RESET_METRICS, request) { ar ->
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
                        .put("error", "Failed to reset concurrency metrics: ${ar.cause().message}")
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

    /**
     * 获取内存使用情况
     */
    private fun getMemoryUsage(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_USAGE_GET, JsonObject()) { ar ->
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
                        .put("error", "Failed to get memory usage: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 触发垃圾回收
     */
    private fun triggerGC(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_GC_TRIGGER, JsonObject()) { ar ->
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
                        .put("error", "Failed to trigger garbage collection: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 清理内存缓存
     */
    private fun clearMemoryCache(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_CACHE_CLEAR, JsonObject()) { ar ->
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
                        .put("error", "Failed to clear memory cache: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }
    /**
     * 获取对象池统计信息
     */
    private fun getObjectPoolStats(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_GET_STATS, JsonObject()) { ar ->
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
                        .put("error", "Failed to get object pool stats: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 创建对象池
     */
    private fun createObjectPool(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Request body is required").encode())
            return
        }

        val name = body.getString("name")
        val initialSize = body.getInteger("initialSize", 10)
        val maxSize = body.getInteger("maxSize", 100)

        if (name == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Pool name is required")
                    .encode()
                )
            return
        }

        val request = JsonObject()
            .put("name", name)
            .put("initialSize", initialSize)
            .put("maxSize", maxSize)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_CREATE, request) { ar ->
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
                        .put("error", "Failed to create object pool: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 移除对象池
     */
    private fun removeObjectPool(ctx: RoutingContext) {
        val poolName = ctx.pathParam("name")

        if (poolName == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Pool name is required").encode())
            return
        }

        val request = JsonObject().put("name", poolName)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_REMOVE, request) { ar ->
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
                        .put("error", "Failed to remove object pool: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Hello World 端点
     */
    private fun helloWorld(ctx: RoutingContext) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("message", "Hello, World!")
                .put("timestamp", System.currentTimeMillis())
                .encode()
            )
    }

    /**
     * Hello Name 端点
     */
    private fun helloName(ctx: RoutingContext) {
        val name = ctx.pathParam("name") ?: "World"

        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("message", "Hello, $name!")
                .put("timestamp", System.currentTimeMillis())
                .encode()
            )
    }

    /**
     * 设置插件管理路由
     */
    private fun setupPluginRoutes() {
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

        // 获取可用的插件类型
        router.get("/api/plugins/types").handler { ctx ->
            // 添加一些常见的插件类型
            val types = JsonArray()

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
    private fun setupMonitoringRoutes() {
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

    /**
     * 设置仪表盘路由
     */
    private fun setupDashboardRoutes() {
        // 获取仪表盘统计数据
        router.get("/api/admin/dashboard/stats").handler { ctx ->
            dashboardHandler.handleGetDashboardStats(ctx)
        }

        // 获取流量数据
        router.get("/api/admin/dashboard/traffic").handler { ctx ->
            dashboardHandler.handleGetTrafficData(ctx)
        }

        // 获取 LLM 使用数据
        router.get("/api/admin/dashboard/llm-usage").handler { ctx ->
            dashboardHandler.handleGetLlmUsageData(ctx)
        }

        // 获取最近事件
        router.get("/api/admin/dashboard/events").handler { ctx ->
            dashboardHandler.handleGetRecentEvents(ctx)
        }
    }
}