package com.louloulin.apix.core

import com.louloulin.apix.admin.AdminApiHandler
import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
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

                        // Add common handlers
                        mainRouter.route().handler(LoggerHandler.create())
                        mainRouter.route().handler(BodyHandler.create())
                        mainRouter.route().handler(CorsHandler.create("*")
                            .allowedHeaders(setOf("Content-Type", "Authorization"))
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

                        // Add a super lightweight endpoint for maximum performance testing
                        mainRouter.get("/ping").handler { ctx ->
                            ctx.response().end("pong")
                        }

                        // Create HTTP server with ultra-high concurrency settings (100K+ connections)
                        val serverOptions = HttpServerOptions()
                            // Basic settings
                            .setPort(configManager.getGatewayPort())
                            .setHost(configManager.getGatewayHost())

                            // TCP optimizations
                            .setTcpNoDelay(true)              // Disable Nagle's algorithm for lower latency
                            .setTcpFastOpen(true)             // Enable TCP Fast Open for faster connections
                            .setTcpQuickAck(true)             // Enable TCP Quick ACK for better responsiveness
                            .setTcpCork(true)                 // Enable TCP Cork for better throughput

                            // Socket reuse
                            .setReusePort(true)               // Enable port reuse for better load distribution
                            .setReuseAddress(true)            // Enable address reuse for faster restarts

                            // Connection handling
                            .setAcceptBacklog(65536)          // Increase accept backlog to handle more pending connections
                            .setIdleTimeout(300)              // 5 minutes idle timeout

                            // Performance optimizations
                            .setHandle100ContinueAutomatically(true) // Handle 100-Continue automatically
                            .setCompressionLevel(1)           // Set compression level to 1 (fastest)
                            .setCompressionSupported(true)    // Enable compression
                            .setDecompressionSupported(true)  // Enable decompression

                            // HTTP/2 settings
                            .setUseAlpn(true)                 // Enable ALPN for HTTP/2 support
                            .setInitialSettings(
                                io.vertx.core.http.Http2Settings()
                                    .setMaxConcurrentStreams(10000) // Allow 10K concurrent streams per connection
                                    .setInitialWindowSize(65535 * 2) // Increase initial window size
                                    .setHeaderTableSize(4096 * 2)   // Increase header table size
                            )

                            // Keep-alive settings are enabled by default in HTTP server
                            // We'll use the default timeout settings

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
