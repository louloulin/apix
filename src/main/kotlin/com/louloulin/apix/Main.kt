package com.louloulin.apix

import com.louloulin.apix.core.ApixVerticle
import com.louloulin.apix.core.verticle.AdminVerticle
import com.louloulin.apix.core.verticle.AuthVerticle
import com.louloulin.apix.core.verticle.CacheVerticle
import com.louloulin.apix.core.verticle.ConfigVerticle
import com.louloulin.apix.core.verticle.DeploymentVerticle
import com.louloulin.apix.core.verticle.HealthVerticle
import com.louloulin.apix.core.verticle.MonitorVerticle
import com.louloulin.apix.core.verticle.PluginVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.core.CompositeFuture
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.eventbus.EventBusOptions
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.louloulin.apix.Main")

fun main() {
    logger.info("Starting APIX - AI Agent Gateway")

    // Configure Vert.x with ultra-high concurrency settings (100K+ connections)
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    val vertxOptions = VertxOptions()
        // Event loop pool - critical for handling many concurrent connections
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

        // Increase event bus options for better internal communication
        .setEventBusOptions(
            EventBusOptions()
                .setConnectTimeout(30000) // 30 seconds
                .setReconnectAttempts(10)
                .setReconnectInterval(2000) // 2 seconds
        )

    // Create Vert.x instance
    val vertx = Vertx.vertx(vertxOptions)

    // Deploy verticles in the correct order
    deployVerticles(vertx, availableProcessors)
        .onSuccess {
            logger.info("APIX Gateway successfully deployed all verticles")
        }
        .onFailure { cause ->
            logger.error("Failed to deploy APIX Gateway verticles", cause)
            vertx.close()
        }

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down APIX Gateway...")
        vertx.close()
    })
}

/**
 * Deploy all verticles in the correct order
 */
private fun deployVerticles(vertx: Vertx, availableProcessors: Int): Future<Void> {
    // Standard deployment options
    val standardOptions = DeploymentOptions()
        .setInstances(1) // Single instance for service verticles

    // High concurrency deployment options for gateway verticle
    val gatewayOptions = DeploymentOptions()
        .setInstances(availableProcessors * 2) // Deploy two instances per core
        .setWorkerPoolSize(availableProcessors * 20) // 20 worker threads per core

    // Deploy ConfigVerticle first
    return deployVerticle(vertx, ConfigVerticle::class.java.name, standardOptions)
        .compose {
            // Then deploy MonitorVerticle
            deployVerticle(vertx, MonitorVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy AuthVerticle
            deployVerticle(vertx, AuthVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy CacheVerticle
            deployVerticle(vertx, CacheVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy PluginVerticle
            deployVerticle(vertx, PluginVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy AdminVerticle
            deployVerticle(vertx, AdminVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy HealthVerticle
            deployVerticle(vertx, HealthVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy DeploymentVerticle
            deployVerticle(vertx, DeploymentVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Finally deploy the main ApixVerticle
            deployVerticle(vertx, ApixVerticle::class.java.name, gatewayOptions)
        }
        .mapEmpty()
}

/**
 * Deploy a single verticle
 */
private fun deployVerticle(vertx: Vertx, verticleName: String, options: DeploymentOptions): Future<String> {
    logger.info("Deploying verticle: {}", verticleName)
    return Future.future { promise ->
        vertx.deployVerticle(verticleName, options)
            .onSuccess { deploymentId ->
                logger.info("Successfully deployed {}: {}", verticleName, deploymentId)
                promise.complete(deploymentId)
            }
            .onFailure { cause ->
                logger.error("Failed to deploy {}", verticleName, cause)
                promise.fail(cause)
            }
    }
}
