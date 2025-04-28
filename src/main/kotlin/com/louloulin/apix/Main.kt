package com.louloulin.apix

import com.louloulin.apix.core.ApixVerticle
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.louloulin.apix.Main")

fun main() {
    logger.info("Starting APIX - AI Agent Gateway")

    // Configure Vert.x with ultra-high concurrency settings (100K+ connections)
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    val vertxOptions = VertxOptions()
        // Event loop pool - critical for handling many concurrent connections
        // For 100K connections, we need more event loops than the default
        .setEventLoopPoolSize(availableProcessors * 4) // 4 event loop threads per core

        // Worker pool - for handling blocking operations
        .setWorkerPoolSize(availableProcessors * 16)   // 16 worker threads per core

        // Internal blocking pool - for internal Vert.x operations
        .setInternalBlockingPoolSize(availableProcessors * 8) // 8 internal blocking threads per core

        // Increase event loop execute time for high load scenarios
        .setMaxEventLoopExecuteTime(5000000000L)  // 5 seconds in nanoseconds

        // Increase worker execute time for complex operations
        .setMaxWorkerExecuteTime(120000000000L)   // 120 seconds in nanoseconds

        // Warning exception time
        .setWarningExceptionTime(10000000000L)    // 10 seconds in nanoseconds

        // Blocked thread check interval
        .setBlockedThreadCheckInterval(5000)     // 5 seconds

        // Use native transport for better performance
        .setPreferNativeTransport(true)          // Use native transport if available

        // Vert.x 4.x doesn't support setting acceptor and selector threads directly
        // We'll rely on the default configuration, which is optimized for most cases

        // Increase event bus options for better internal communication
        .setEventBusOptions(
            io.vertx.core.eventbus.EventBusOptions()
                .setConnectTimeout(30000) // 30 seconds
                .setReconnectAttempts(10)
                .setReconnectInterval(2000) // 2 seconds
        )

    // Disable metrics for native image compatibility
    // .apply {
    //     metricsOptions = MicrometerMetricsOptions()
    //         .setEnabled(true)
    //         .setPrometheusOptions(VertxPrometheusOptions().setEnabled(true))
    // }

    // Create Vert.x instance
    val vertx = Vertx.vertx(vertxOptions)

    // Deploy the main verticle with multiple instances for ultra-high concurrency
    val deploymentOptions = io.vertx.core.DeploymentOptions()
        // For 100K connections, we need more verticle instances
        .setInstances(availableProcessors * 2) // Deploy two instances per core
        // Increase the maximum worker pool size for this verticle
        .setWorkerPoolSize(availableProcessors * 20) // 20 worker threads per core
        // High concurrency is enabled by default in worker verticles

    vertx.deployVerticle(ApixVerticle(), deploymentOptions)
        .onSuccess { deploymentId ->
            logger.info("APIX Gateway successfully deployed: {}", deploymentId)
        }
        .onFailure { cause ->
            logger.error("Failed to deploy APIX Gateway", cause)
            vertx.close()
        }

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down APIX Gateway...")
        vertx.close()
    })
}
