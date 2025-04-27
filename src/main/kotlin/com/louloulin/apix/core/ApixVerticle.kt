package com.louloulin.apix.core

import com.louloulin.apix.admin.AdminApiHandler
import com.louloulin.apix.config.ConfigManager
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
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

            // Initialize route manager
            routeManager = RouteManager(vertx, configManager, pluginManager)

            // Create main router
            val mainRouter = Router.router(vertx)

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

            // Set up gateway routes
            routeManager.setupRoutes(mainRouter)

            // Create HTTP server
            val serverOptions = HttpServerOptions()
                .setPort(configManager.getGatewayPort())
                .setHost(configManager.getGatewayHost())

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
