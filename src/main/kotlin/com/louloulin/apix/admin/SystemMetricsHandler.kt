package com.louloulin.apix.admin

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.OperatingSystemMXBean
import java.lang.management.ThreadMXBean
import java.util.concurrent.TimeUnit

/**
 * Handler for system metrics API endpoints.
 */
class SystemMetricsHandler(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SystemMetricsHandler::class.java)
    private val startTime = System.currentTimeMillis()

    // MX Beans for system metrics
    private val osMXBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
    private val threadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean()

    // Metrics cache to avoid frequent calculations
    private var metricsCache: JsonObject? = null
    private var lastMetricsUpdate: Long = 0
    private val METRICS_CACHE_TTL = 5000 // 5 seconds

    /**
     * Sets up the system metrics API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up system metrics API routes...")

        // System metrics endpoints
        router.get("/metrics").handler(this::getSystemMetrics)
        router.get("/metrics/cpu").handler(this::getCpuMetrics)
        router.get("/metrics/memory").handler(this::getMemoryMetrics)
        router.get("/metrics/threads").handler(this::getThreadMetrics)
        router.get("/metrics/jvm").handler(this::getJvmMetrics)
        router.get("/metrics/os").handler(this::getOsMetrics)

        // Health check endpoint
        router.get("/health").handler(this::getHealthCheck)

        // Start periodic metrics collection
        startPeriodicMetricsCollection()
    }

    /**
     * Gets all system metrics.
     */
    private fun getSystemMetrics(context: RoutingContext) {
        try {
            val metrics = getOrUpdateMetricsCache()

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(metrics.encode())
        } catch (e: Exception) {
            logger.error("Error getting system metrics", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get system metrics: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets CPU metrics.
     */
    private fun getCpuMetrics(context: RoutingContext) {
        try {
            val metrics = getOrUpdateMetricsCache()
            val cpuMetrics = metrics.getJsonObject("cpu", JsonObject())

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(cpuMetrics.encode())
        } catch (e: Exception) {
            logger.error("Error getting CPU metrics", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get CPU metrics: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets memory metrics.
     */
    private fun getMemoryMetrics(context: RoutingContext) {
        try {
            val metrics = getOrUpdateMetricsCache()
            val memoryMetrics = metrics.getJsonObject("memory", JsonObject())

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(memoryMetrics.encode())
        } catch (e: Exception) {
            logger.error("Error getting memory metrics", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get memory metrics: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets thread metrics.
     */
    private fun getThreadMetrics(context: RoutingContext) {
        try {
            val metrics = getOrUpdateMetricsCache()
            val threadMetrics = metrics.getJsonObject("threads", JsonObject())

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(threadMetrics.encode())
        } catch (e: Exception) {
            logger.error("Error getting thread metrics", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get thread metrics: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets JVM metrics.
     */
    private fun getJvmMetrics(context: RoutingContext) {
        try {
            val metrics = getOrUpdateMetricsCache()
            val jvmMetrics = metrics.getJsonObject("jvm", JsonObject())

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(jvmMetrics.encode())
        } catch (e: Exception) {
            logger.error("Error getting JVM metrics", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get JVM metrics: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets OS metrics.
     */
    private fun getOsMetrics(context: RoutingContext) {
        try {
            val metrics = getOrUpdateMetricsCache()
            val osMetrics = metrics.getJsonObject("os", JsonObject())

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(osMetrics.encode())
        } catch (e: Exception) {
            logger.error("Error getting OS metrics", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get OS metrics: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets health check.
     */
    private fun getHealthCheck(context: RoutingContext) {
        try {
            val health = JsonObject()
                .put("status", "UP")
                .put("timestamp", System.currentTimeMillis())
                .put("uptime", System.currentTimeMillis() - startTime)
                .put("checks", JsonArray()
                    .add(JsonObject()
                        .put("name", "system")
                        .put("status", "UP")
                    )
                    .add(JsonObject()
                        .put("name", "memory")
                        .put("status", if (getMemoryUsagePercent() < 90) "UP" else "WARNING")
                    )
                )

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(health.encode())
        } catch (e: Exception) {
            logger.error("Error getting health check", e)

            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("status", "DOWN")
                    .put("error", "Failed to get health check: ${e.message}")
                    .encode()
                )
        }
    }

    /**
     * Gets or updates the metrics cache.
     */
    private fun getOrUpdateMetricsCache(): JsonObject {
        val now = System.currentTimeMillis()

        // If cache is valid, return it
        if (metricsCache != null && now - lastMetricsUpdate < METRICS_CACHE_TTL) {
            return metricsCache!!
        }

        // Otherwise, update the cache
        val metrics = collectSystemMetrics()
        metricsCache = metrics
        lastMetricsUpdate = now

        return metrics
    }

    /**
     * Collects system metrics.
     */
    private fun collectSystemMetrics(): JsonObject {
        val metrics = JsonObject()

        // CPU metrics
        val cpuMetrics = JsonObject()
            .put("cores", Runtime.getRuntime().availableProcessors())
            .put("systemLoad", osMXBean.systemLoadAverage)
            .put("processCpuLoad", getCpuLoad())
            .put("processCpuTime", getProcessCpuTime())

        // Memory metrics
        val heapMemory = memoryMXBean.heapMemoryUsage
        val nonHeapMemory = memoryMXBean.nonHeapMemoryUsage

        val memoryMetrics = JsonObject()
            .put("heap", JsonObject()
                .put("init", heapMemory.init)
                .put("used", heapMemory.used)
                .put("committed", heapMemory.committed)
                .put("max", heapMemory.max)
                .put("usagePercent", (heapMemory.used.toDouble() / heapMemory.max) * 100)
            )
            .put("nonHeap", JsonObject()
                .put("init", nonHeapMemory.init)
                .put("used", nonHeapMemory.used)
                .put("committed", nonHeapMemory.committed)
                .put("max", nonHeapMemory.max)
            )
            .put("total", JsonObject()
                .put("init", heapMemory.init + nonHeapMemory.init)
                .put("used", heapMemory.used + nonHeapMemory.used)
                .put("committed", heapMemory.committed + nonHeapMemory.committed)
                .put("max", if (nonHeapMemory.max > 0) heapMemory.max + nonHeapMemory.max else heapMemory.max)
            )

        // Thread metrics
        val threadMetrics = JsonObject()
            .put("count", threadMXBean.threadCount)
            .put("peakCount", threadMXBean.peakThreadCount)
            .put("daemonCount", threadMXBean.daemonThreadCount)
            .put("totalStarted", threadMXBean.totalStartedThreadCount)

        // JVM metrics
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        val jvmMetrics = JsonObject()
            .put("name", runtimeMXBean.vmName)
            .put("vendor", runtimeMXBean.vmVendor)
            .put("version", runtimeMXBean.vmVersion)
            .put("startTime", runtimeMXBean.startTime)
            .put("uptime", runtimeMXBean.uptime)
            .put("inputArguments", JsonArray(runtimeMXBean.inputArguments))

        // OS metrics
        val osMetrics = JsonObject()
            .put("name", osMXBean.name)
            .put("version", osMXBean.version)
            .put("arch", osMXBean.arch)
            .put("availableProcessors", osMXBean.availableProcessors)

        // Add all metrics to the main object
        metrics
            .put("timestamp", System.currentTimeMillis())
            .put("cpu", cpuMetrics)
            .put("memory", memoryMetrics)
            .put("threads", threadMetrics)
            .put("jvm", jvmMetrics)
            .put("os", osMetrics)

        return metrics
    }

    /**
     * Gets the CPU load.
     */
    private fun getCpuLoad(): Double {
        try {
            // Try to get the CPU load using reflection (for Oracle JDK)
            val method = osMXBean.javaClass.getMethod("getProcessCpuLoad")
            method.isAccessible = true
            return method.invoke(osMXBean) as Double
        } catch (e: Exception) {
            // Fallback to system load average
            return osMXBean.systemLoadAverage / Runtime.getRuntime().availableProcessors()
        }
    }

    /**
     * Gets the process CPU time.
     */
    private fun getProcessCpuTime(): Long {
        try {
            // Try to get the CPU time using reflection (for Oracle JDK)
            val method = osMXBean.javaClass.getMethod("getProcessCpuTime")
            method.isAccessible = true
            return method.invoke(osMXBean) as Long
        } catch (e: Exception) {
            // Fallback to 0
            return 0
        }
    }

    /**
     * Gets the memory usage percent.
     */
    private fun getMemoryUsagePercent(): Double {
        val heapMemory = memoryMXBean.heapMemoryUsage
        return (heapMemory.used.toDouble() / heapMemory.max) * 100
    }

    /**
     * Starts periodic metrics collection.
     */
    private fun startPeriodicMetricsCollection() {
        vertx.setPeriodic(METRICS_CACHE_TTL.toLong()) {
            try {
                // Update metrics cache
                metricsCache = collectSystemMetrics()
                lastMetricsUpdate = System.currentTimeMillis()

                // Log memory usage if it's high
                val memoryUsagePercent = getMemoryUsagePercent()
                if (memoryUsagePercent > 80) {
                    logger.warn("High memory usage: ${String.format("%.2f", memoryUsagePercent)}%")
                }
            } catch (e: Exception) {
                logger.error("Error collecting system metrics", e)
            }
        }
    }
}
