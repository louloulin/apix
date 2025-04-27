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
    
    // Configure Vert.x with metrics
    val vertxOptions = VertxOptions().apply {
        metricsOptions = MicrometerMetricsOptions()
            .setEnabled(true)
            .setPrometheusOptions(VertxPrometheusOptions().setEnabled(true))
    }
    
    // Create Vert.x instance
    val vertx = Vertx.create(vertxOptions)
    
    // Deploy the main verticle
    vertx.deployVerticle(ApixVerticle()) { result ->
        if (result.succeeded()) {
            logger.info("APIX Gateway successfully deployed: {}", result.result())
        } else {
            logger.error("Failed to deploy APIX Gateway", result.cause())
            vertx.close()
        }
    }
    
    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down APIX Gateway...")
        vertx.close()
    })
}
