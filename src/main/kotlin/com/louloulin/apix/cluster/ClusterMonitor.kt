package com.louloulin.apix.cluster

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * Monitor for cluster health and performance.
 */
class ClusterMonitor(private val vertx: Vertx, private val clusterConfig: ClusterConfig) {
    private val logger = LoggerFactory.getLogger(ClusterMonitor::class.java)

    // Monitoring timer ID
    private var monitorTimerId: Long = -1

    // Node statistics
    private val nodeStats = mutableMapOf<String, NodeStats>()

    // Event bus metrics
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val messagesFailed = AtomicLong(0)

    /**
     * Start monitoring.
     */
    fun start(): Future<Void> {
        if (!clusterConfig.enabled || !vertx.isClustered()) {
            logger.info("Cluster monitoring is disabled (not in clustered mode)")
            return Future.succeededFuture()
        }

        logger.info("Starting cluster monitoring")

        // Start periodic monitoring
        val monitorInterval = 10000L // 10 seconds
        monitorTimerId = vertx.setPeriodic(monitorInterval) { _ ->
            collectMetrics()
        }

        // Register event bus interceptors for metrics
        registerEventBusMetricsInterceptors()

        return Future.succeededFuture()
    }

    /**
     * Stop monitoring.
     */
    fun stop(): Future<Void> {
        logger.info("Stopping cluster monitoring")

        if (monitorTimerId != -1L) {
            vertx.cancelTimer(monitorTimerId)
            monitorTimerId = -1L
        }

        return Future.succeededFuture()
    }

    /**
     * Register event bus metrics interceptors.
     */
    private fun registerEventBusMetricsInterceptors() {
        // Outbound messages
        vertx.eventBus().addOutboundInterceptor<Any> { context ->
            messagesSent.incrementAndGet()
            context.next()
        }

        // Inbound messages
        vertx.eventBus().addInboundInterceptor<Any> { context ->
            messagesReceived.incrementAndGet()
            context.next()
        }
    }

    /**
     * Collect metrics from the cluster.
     */
    private fun collectMetrics() {
        try {
            // Collect local node metrics
            val localNodeId = vertx.hashCode().toString()
            val localNodeStats = collectLocalNodeStats(localNodeId)
            nodeStats[localNodeId] = localNodeStats

            logger.debug("Collected metrics for local node: $localNodeId")
        } catch (e: Exception) {
            logger.error("Error collecting cluster metrics", e)
        }
    }

    /**
     * Collect local node statistics.
     */
    private fun collectLocalNodeStats(nodeId: String): NodeStats {
        val runtime = Runtime.getRuntime()
        val memoryMXBean = ManagementFactory.getMemoryMXBean()
        val threadMXBean = ManagementFactory.getThreadMXBean()

        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage

        return NodeStats(
            nodeId = nodeId,
            timestamp = System.currentTimeMillis(),
            cpuCount = runtime.availableProcessors(),
            heapMemoryUsed = heapMemoryUsage.used,
            heapMemoryMax = heapMemoryUsage.max,
            nonHeapMemoryUsed = nonHeapMemoryUsage.used,
            threadCount = threadMXBean.threadCount,
            messagesSent = messagesSent.get(),
            messagesReceived = messagesReceived.get(),
            messagesFailed = messagesFailed.get()
        )
    }

    /**
     * Get cluster metrics.
     */
    fun getMetrics(): JsonObject {
        val nodesArray = JsonArray()

        nodeStats.values.forEach { stats ->
            nodesArray.add(stats.toJson())
        }

        return JsonObject()
            .put("timestamp", System.currentTimeMillis())
            .put("clusterType", clusterConfig.type)
            .put("nodeCount", nodeStats.size)
            .put("nodes", nodesArray)
            .put("eventBus", JsonObject()
                .put("messagesSent", messagesSent.get())
                .put("messagesReceived", messagesReceived.get())
                .put("messagesFailed", messagesFailed.get())
            )
    }

    /**
     * Node statistics.
     */
    data class NodeStats(
        val nodeId: String,
        val timestamp: Long,
        val cpuCount: Int,
        val heapMemoryUsed: Long,
        val heapMemoryMax: Long,
        val nonHeapMemoryUsed: Long,
        val threadCount: Int,
        val messagesSent: Long,
        val messagesReceived: Long,
        val messagesFailed: Long
    ) {
        /**
         * Convert to JSON.
         */
        fun toJson(): JsonObject {
            return JsonObject()
                .put("nodeId", nodeId)
                .put("timestamp", timestamp)
                .put("cpuCount", cpuCount)
                .put("memory", JsonObject()
                    .put("heapUsed", heapMemoryUsed)
                    .put("heapMax", heapMemoryMax)
                    .put("heapUsedPercent", if (heapMemoryMax > 0) (heapMemoryUsed.toDouble() / heapMemoryMax * 100).toInt() else 0)
                    .put("nonHeapUsed", nonHeapMemoryUsed)
                )
                .put("threads", threadCount)
                .put("eventBus", JsonObject()
                    .put("messagesSent", messagesSent)
                    .put("messagesReceived", messagesReceived)
                    .put("messagesFailed", messagesFailed)
                )
        }
    }
}
