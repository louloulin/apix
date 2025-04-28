package com.louloulin.apix.core

import com.louloulin.apix.admin.AdminApiHandler
import com.louloulin.apix.config.ConfigManager
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerHandler
import org.slf4j.LoggerFactory

class ApixVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(ApixVerticle::class.java)
    private lateinit var configManager: ConfigManager
    private lateinit var pluginManager: PluginManager
    private lateinit var routeManager: RouteManager
    private lateinit var serviceManager: ServiceManager

    override fun start(startPromise: Promise<Void>) {
        logger.info("Initializing APIX Gateway...")

        try {
            // Initialize configuration
            configManager = ConfigManager(vertx)

            // Initialize plugin system
            pluginManager = PluginManager(vertx, configManager)

            // Initialize service manager
            serviceManager = ServiceManager(vertx, configManager)

            // Create main router
            val mainRouter = Router.router(vertx)

            // Initialize route manager
            routeManager = RouteManager(vertx, mainRouter, configManager, pluginManager)

            // Main router already created above

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

            // Set up admin API routes
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

            // Start the server
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

        } catch (e: Exception) {
            logger.error("Error initializing APIX Gateway", e)
            startPromise.fail(e)
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping APIX Gateway...")

        // Cleanup resources
        pluginManager.shutdown()

        stopPromise.complete()
    }
}
