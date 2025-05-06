package com.louloulin.apix.core

import com.louloulin.apix.admin.AdminApiHandler
import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.monitoring.PerformanceMonitor
import com.louloulin.apix.core.network.NetworkOptimizer
import com.louloulin.apix.core.tracing.TracingManager
import com.louloulin.apix.core.verticle.BaseVerticle
import com.louloulin.apix.plugins.PluginManager
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerHandler
import org.slf4j.LoggerFactory

/**
 * 主要的 API 网关 Verticle，负责处理 HTTP 请求和路由
 */
class ApixVerticle : BaseVerticle() {
    private lateinit var configManager: ConfigManager
    private lateinit var routeManager: RouteManager

    override fun registerEventBusHandlers() {
        // 不需要注册 EventBus 处理器，因为这个 Verticle 主要处理 HTTP 请求
    }

    override fun onStart(startPromise: Promise<Void>) {
        logger.info("Initializing APIX Gateway...")

        try {
            // 通过 EventBus 获取配置管理器
            vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
                if (ar.succeeded()) {
                    val configResponse = ar.result().body()
                    if (configResponse.getBoolean("success", false)) {
                        // 初始化配置管理器
                        configManager = ConfigManager(vertx)

                        // 创建主路由器
                        val mainRouter = Router.router(vertx)

                        // 初始化插件管理器
                        val pluginManager = PluginManager(vertx, configManager)

                        // 初始化服务管理器
                        val serviceManager = ServiceManager(vertx, configManager)

                        // 初始化路由管理器
                        routeManager = RouteManager(vertx, mainRouter, configManager, pluginManager)

                        // 初始化追踪管理器
                        val tracingManager = TracingManager.getInstance(vertx)

                        // 初始化性能监控器
                        val performanceMonitor = PerformanceMonitor.getInstance(vertx)

                        // 初始化网络优化器
                        val networkOptimizer = NetworkOptimizer.getInstance(vertx)

                        // Add common handlers
                        mainRouter.route().handler(tracingManager.createTracingHandler()) // 添加追踪中间件
                        mainRouter.route().handler(performanceMonitor.createPerformanceMonitorHandler()) // 添加性能监控中间件
                        mainRouter.route().handler(LoggerHandler.create())
                        mainRouter.route().handler(BodyHandler.create())
                        mainRouter.route().handler(CorsHandler.create("*")
                            .allowedHeaders(setOf("Content-Type", "Authorization", "X-Trace-ID", "X-Response-Time")) // 添加追踪ID和响应时间头
                            .allowedMethods(setOf(
                                io.vertx.core.http.HttpMethod.GET,
                                io.vertx.core.http.HttpMethod.POST,
                                io.vertx.core.http.HttpMethod.PUT,
                                io.vertx.core.http.HttpMethod.DELETE,
                                io.vertx.core.http.HttpMethod.OPTIONS
                            ))
                        )

                        // 设置管理 API 路由
                        val adminRouter = Router.router(vertx)
                        val adminApiHandler = AdminApiHandler(configManager, pluginManager, routeManager, serviceManager)
                        adminApiHandler.setupRoutes(adminRouter)
                        mainRouter.mountSubRouter("/admin", adminRouter)

                        // Gateway routes are set up automatically by the RouteManager

                        // Add Hello World endpoints for performance testing
                        mainRouter.get("/hello").handler { ctx ->
                            ctx.response()
                                .putHeader("content-type", "application/json")
                                .end(JsonObject().put("message", "Hello, World!").encode())
                        }

                        // Add API Hello endpoint for k6 testing
                        mainRouter.get("/api/hello").handler { ctx ->
                            ctx.response()
                                .putHeader("content-type", "application/json")
                                .end(JsonObject().put("message", "Hello from API!").encode())
                        }

                        // Add a super lightweight endpoint for maximum performance testing
                        mainRouter.get("/ping").handler { ctx ->
                            ctx.response().end("pong")
                        }

                        // Create HTTP server with ultra-high concurrency settings (200K+ connections)
                        val baseOptions = HttpServerOptions()
                            // Basic settings
                            .setPort(configManager.getGatewayPort())
                            .setHost(configManager.getGatewayHost())
                            .setCompressionSupported(true)
                            .setDecompressionSupported(true)
                            .setIdleTimeout(configManager.getGatewayIdleTimeout())

                        // 使用网络优化器创建优化的服务器选项
                        val serverOptions = networkOptimizer.createOptimizedHttpServerOptions(baseOptions)

                        // 启动服务器
                        vertx.createHttpServer(serverOptions)
                            .requestHandler(mainRouter)
                            .listen { result ->
                                if (result.succeeded()) {
                                    logger.info("APIX Gateway listening on {}:{}",
                                        configManager.getGatewayHost(),
                                        configManager.getGatewayPort())
                                    startPromise.complete()
                                } else {
                                    logger.error("Failed to start APIX Gateway", result.cause())
                                    startPromise.fail(result.cause())
                                }
                            }
                    } else {
                        logger.error("Failed to get configuration: {}", configResponse.getString("error"))
                        startPromise.fail(configResponse.getString("error"))
                    }
                } else {
                    logger.error("Failed to get configuration", ar.cause())
                    startPromise.fail(ar.cause())
                }
            }

        } catch (e: Exception) {
            logger.error("Error initializing APIX Gateway", e)
            startPromise.fail(e)
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping APIX Gateway...")

        // 通过 EventBus 通知其他 Verticle 关闭
        vertx.eventBus().publish("apix.system.shutdown", JsonObject())

        stopPromise.complete()
    }
}
