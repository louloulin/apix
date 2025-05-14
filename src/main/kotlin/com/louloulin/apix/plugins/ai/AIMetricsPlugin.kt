package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/**
 * Plugin that collects and analyzes AI-specific metrics.
 *
 * This plugin:
 * 1. Tracks token usage (input tokens, output tokens, total tokens)
 * 2. Measures request latency and throughput
 * 3. Collects model performance metrics
 * 4. Provides metrics query and export functionality
 */
class AIMetricsPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(AIMetricsPlugin::class.java)

    override val type: String = "ai-metrics"

    // Configuration values with defaults
    private val enabled: Boolean = config.config.getBoolean("enabled", true)
    private val trackTokens: Boolean = config.config.getBoolean("track_tokens", true)
    private val trackLatency: Boolean = config.config.getBoolean("track_latency", true)
    private val trackThroughput: Boolean = config.config.getBoolean("track_throughput", true)
    private val trackModelPerformance: Boolean = config.config.getBoolean("track_model_performance", true)
    private val metricsRetentionHours: Int = config.config.getInteger("metrics_retention_hours", 24)

    // Metrics storage
    private val tokenUsage = ConcurrentHashMap<String, TokenUsage>() // model -> usage
    private val requestLatency = ConcurrentHashMap<String, MutableList<RequestLatency>>() // model -> latencies
    private val modelPerformance = ConcurrentHashMap<String, ModelPerformance>() // model -> performance

    // Throughput tracking
    private val requestCount = AtomicLong(0)
    private val successCount = AtomicLong(0)
    private val failureCount = AtomicLong(0)
    private val startTime = Instant.now()

    // Token counting patterns
    private val openaiCompletionPattern = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"")
    private val anthropicCompletionPattern = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"")
    private val cohereCompletionPattern = Pattern.compile("\"model\"\\s*:\\s*\"([^\"]+)\"")

    /**
     * Initialize the plugin
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            logger.info("Initializing AIMetricsPlugin")

            // Schedule metrics cleanup
            if (metricsRetentionHours > 0) {
                val cleanupIntervalMs = 3600000L // 1 hour
                vertx.setPeriodic(cleanupIntervalMs) { _ ->
                    cleanupOldMetrics()
                }
            }

            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to initialize AIMetricsPlugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Execute the plugin
     */
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!enabled) {
            // Plugin is disabled, skip processing
            promise.complete()
            return promise.future()
        }

        try {
            // Check if this is a metrics query request
            val path = context.request().path()
            if (path.endsWith("/metrics") || path.endsWith("/metrics/")) {
                handleMetricsQuery(context)
                promise.complete()
                return promise.future()
            }

            // Track request start time
            val requestStartTime = System.currentTimeMillis()
            context.put("ai_request_start_time", requestStartTime)

            // Increment request count
            requestCount.incrementAndGet()

            // Extract model information from request
            val body = try {
                context.body().asJsonObject()
            } catch (e: Exception) {
                try {
                    JsonObject(context.getBodyAsString())
                } catch (e: Exception) {
                    null
                }
            }

            val modelInfo = extractModelInfo(body)
            if (modelInfo != null) {
                context.put("ai_model_info", modelInfo)
            }

            // Add response handler
            context.addHeadersEndHandler { _ ->
                try {
                    // Track request completion
                    val requestEndTime = System.currentTimeMillis()
                    val latency = requestEndTime - requestStartTime

                    // Get status code
                    val statusCode = context.response().statusCode
                    val success = statusCode >= 200 && statusCode < 300

                    if (success) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }

                    // Get model info
                    val storedModelInfo = context.get<ModelInfo>("ai_model_info")

                    // Track metrics
                    if (storedModelInfo != null) {
                        val modelId = storedModelInfo.modelId

                        // Track latency
                        if (trackLatency) {
                            trackRequestLatency(modelId, latency, success)
                        }

                        // Track model performance
                        if (trackModelPerformance) {
                            updateModelPerformance(modelId, latency, success)
                        }

                        // Track token usage
                        if (trackTokens) {
                            val responseBody = context.response().headers().get("X-Response-Body")
                            if (responseBody != null) {
                                try {
                                    val responseJson = JsonObject(responseBody)
                                    val tokenInfo = extractTokenInfo(responseJson, storedModelInfo.provider)
                                    if (tokenInfo != null) {
                                        updateTokenUsage(modelId, tokenInfo.inputTokens, tokenInfo.outputTokens)
                                    }
                                } catch (e: Exception) {
                                    logger.debug("Failed to parse response body for token tracking", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error tracking metrics", e)
                }
            }

            // Continue with the request
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing AI metrics plugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Extract model information from request body
     */
    private fun extractModelInfo(body: JsonObject?): ModelInfo? {
        if (body == null) {
            return null
        }

        // Try to extract model ID and provider

        // OpenAI format
        val openaiModel = body.getString("model")
        if (openaiModel != null) {
            return ModelInfo(openaiModel, "openai")
        }

        // Anthropic format
        val anthropicModel = body.getString("model")
        if (anthropicModel != null && body.containsKey("prompt")) {
            return ModelInfo(anthropicModel, "anthropic")
        }

        // Cohere format
        val cohereModel = body.getString("model")
        if (cohereModel != null && (
                body.containsKey("message") ||
                body.containsKey("texts") ||
                body.containsKey("prompt") ||
                body.containsKey("chat_history")
            )) {
            return ModelInfo(cohereModel, "cohere")
        }

        // Generic format
        val genericModel = body.getString("model_id") ?: body.getString("modelId")
        if (genericModel != null) {
            val provider = body.getString("provider") ?: "unknown"
            return ModelInfo(genericModel, provider)
        }

        return null
    }

    /**
     * Extract token information from response
     */
    private fun extractTokenInfo(response: JsonObject, provider: String): TokenInfo? {
        when (provider.lowercase()) {
            "openai" -> {
                val usage = response.getJsonObject("usage")
                if (usage != null) {
                    val promptTokens = usage.getInteger("prompt_tokens", 0)
                    val completionTokens = usage.getInteger("completion_tokens", 0)
                    val totalTokens = usage.getInteger("total_tokens", 0)
                    return TokenInfo(promptTokens, completionTokens, totalTokens)
                }
            }
            "anthropic" -> {
                val usage = response.getJsonObject("usage")
                if (usage != null) {
                    val inputTokens = usage.getInteger("input_tokens", 0)
                    val outputTokens = usage.getInteger("output_tokens", 0)
                    return TokenInfo(inputTokens, outputTokens, inputTokens + outputTokens)
                }
            }
            "cohere" -> {
                // For generate endpoint
                val meta = response.getJsonObject("meta")
                if (meta != null) {
                    val billableTokens = meta.getInteger("billed_units", 0)
                    val inputTokens = meta.getInteger("input_tokens", 0)
                    val outputTokens = billableTokens - inputTokens
                    return TokenInfo(inputTokens, outputTokens, billableTokens)
                }

                // For chat endpoint
                val usage = response.getJsonObject("usage")
                if (usage != null) {
                    val inputTokens = usage.getInteger("input_tokens", 0)
                    val outputTokens = usage.getInteger("output_tokens", 0)
                    return TokenInfo(inputTokens, outputTokens, inputTokens + outputTokens)
                }

                // For embed endpoint
                val tokens = response.getInteger("tokens", 0)
                if (tokens > 0) {
                    return TokenInfo(tokens, 0, tokens)
                }
            }
        }

        return null
    }

    /**
     * Track request latency
     */
    private fun trackRequestLatency(modelId: String, latency: Long, success: Boolean) {
        val latencyRecord = RequestLatency(
            timestamp = Instant.now(),
            latencyMs = latency,
            success = success
        )

        requestLatency.computeIfAbsent(modelId) { mutableListOf() }.add(latencyRecord)
    }

    /**
     * Update model performance metrics
     */
    private fun updateModelPerformance(modelId: String, latency: Long, success: Boolean) {
        modelPerformance.compute(modelId) { _, performance ->
            val current = performance ?: ModelPerformance(
                requestCount = 0,
                successCount = 0,
                failureCount = 0,
                totalLatency = 0,
                minLatency = Long.MAX_VALUE,
                maxLatency = 0,
                lastUpdated = Instant.now()
            )

            current.requestCount++
            if (success) {
                current.successCount++
            } else {
                current.failureCount++
            }

            current.totalLatency += latency
            current.minLatency = minOf(current.minLatency, latency)
            current.maxLatency = maxOf(current.maxLatency, latency)
            current.lastUpdated = Instant.now()

            current
        }
    }

    /**
     * Update token usage metrics
     */
    private fun updateTokenUsage(modelId: String, inputTokens: Int, outputTokens: Int) {
        tokenUsage.compute(modelId) { _, usage ->
            val current = usage ?: TokenUsage(
                inputTokens = 0,
                outputTokens = 0,
                totalTokens = 0,
                lastUpdated = Instant.now()
            )

            current.inputTokens += inputTokens
            current.outputTokens += outputTokens
            current.totalTokens += (inputTokens + outputTokens)
            current.lastUpdated = Instant.now()

            current
        }
    }

    /**
     * Handle metrics query request
     */
    private fun handleMetricsQuery(context: RoutingContext) {
        val format = context.request().getParam("format") ?: "json"
        val model = context.request().getParam("model")

        when (format.lowercase()) {
            "prometheus" -> {
                val metrics = generatePrometheusMetrics(model)
                context.response()
                    .putHeader("Content-Type", "text/plain")
                    .end(metrics)
            }
            else -> {
                val metrics = generateJsonMetrics(model)
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(metrics.encode())
            }
        }
    }

    /**
     * Generate metrics in JSON format
     */
    private fun generateJsonMetrics(modelFilter: String?): JsonObject {
        val result = JsonObject()

        // Overall metrics
        val overall = JsonObject()
            .put("requestCount", requestCount.get())
            .put("successCount", successCount.get())
            .put("failureCount", failureCount.get())
            .put("uptime", (Instant.now().toEpochMilli() - startTime.toEpochMilli()) / 1000)

        result.put("overall", overall)

        // Token usage metrics
        val filteredTokenUsage = if (modelFilter != null) {
            tokenUsage.filterKeys { it == modelFilter }
        } else {
            tokenUsage
        }

        val tokenUsageJson = JsonObject()
        filteredTokenUsage.forEach { (model, usage) ->
            tokenUsageJson.put(model, JsonObject()
                .put("inputTokens", usage.inputTokens)
                .put("outputTokens", usage.outputTokens)
                .put("totalTokens", usage.totalTokens)
                .put("lastUpdated", usage.lastUpdated.toString())
            )
        }

        result.put("tokenUsage", tokenUsageJson)

        // Model performance metrics
        val filteredModelPerformance = if (modelFilter != null) {
            modelPerformance.filterKeys { it == modelFilter }
        } else {
            modelPerformance
        }

        val modelPerformanceJson = JsonObject()
        filteredModelPerformance.forEach { (model, performance) ->
            val avgLatency = if (performance.requestCount > 0) {
                performance.totalLatency / performance.requestCount
            } else {
                0
            }

            val successRate = if (performance.requestCount > 0) {
                (performance.successCount.toDouble() / performance.requestCount) * 100
            } else {
                0.0
            }

            modelPerformanceJson.put(model, JsonObject()
                .put("requestCount", performance.requestCount)
                .put("successCount", performance.successCount)
                .put("failureCount", performance.failureCount)
                .put("avgLatency", avgLatency)
                .put("minLatency", performance.minLatency)
                .put("maxLatency", performance.maxLatency)
                .put("successRate", successRate)
                .put("lastUpdated", performance.lastUpdated.toString())
            )
        }

        result.put("modelPerformance", modelPerformanceJson)

        return result
    }

    /**
     * Generate metrics in Prometheus format
     */
    private fun generatePrometheusMetrics(modelFilter: String?): String {
        val sb = StringBuilder()

        // Overall metrics
        sb.appendLine("# HELP apix_ai_request_count Total number of AI requests")
        sb.appendLine("# TYPE apix_ai_request_count counter")
        sb.appendLine("apix_ai_request_count ${requestCount.get()}")

        sb.appendLine("# HELP apix_ai_success_count Total number of successful AI requests")
        sb.appendLine("# TYPE apix_ai_success_count counter")
        sb.appendLine("apix_ai_success_count ${successCount.get()}")

        sb.appendLine("# HELP apix_ai_failure_count Total number of failed AI requests")
        sb.appendLine("# TYPE apix_ai_failure_count counter")
        sb.appendLine("apix_ai_failure_count ${failureCount.get()}")

        sb.appendLine("# HELP apix_ai_uptime_seconds Uptime in seconds")
        sb.appendLine("# TYPE apix_ai_uptime_seconds gauge")
        sb.appendLine("apix_ai_uptime_seconds ${(Instant.now().toEpochMilli() - startTime.toEpochMilli()) / 1000}")

        // Token usage metrics
        val filteredTokenUsage = if (modelFilter != null) {
            tokenUsage.filterKeys { it == modelFilter }
        } else {
            tokenUsage
        }

        sb.appendLine("# HELP apix_ai_input_tokens_total Total number of input tokens")
        sb.appendLine("# TYPE apix_ai_input_tokens_total counter")
        filteredTokenUsage.forEach { (model, usage) ->
            sb.appendLine("apix_ai_input_tokens_total{model=\"$model\"} ${usage.inputTokens}")
        }

        sb.appendLine("# HELP apix_ai_output_tokens_total Total number of output tokens")
        sb.appendLine("# TYPE apix_ai_output_tokens_total counter")
        filteredTokenUsage.forEach { (model, usage) ->
            sb.appendLine("apix_ai_output_tokens_total{model=\"$model\"} ${usage.outputTokens}")
        }

        sb.appendLine("# HELP apix_ai_total_tokens_total Total number of tokens")
        sb.appendLine("# TYPE apix_ai_total_tokens_total counter")
        filteredTokenUsage.forEach { (model, usage) ->
            sb.appendLine("apix_ai_total_tokens_total{model=\"$model\"} ${usage.totalTokens}")
        }

        // Model performance metrics
        val filteredModelPerformance = if (modelFilter != null) {
            modelPerformance.filterKeys { it == modelFilter }
        } else {
            modelPerformance
        }

        sb.appendLine("# HELP apix_ai_model_request_count Total number of requests per model")
        sb.appendLine("# TYPE apix_ai_model_request_count counter")
        filteredModelPerformance.forEach { (model, performance) ->
            sb.appendLine("apix_ai_model_request_count{model=\"$model\"} ${performance.requestCount}")
        }

        sb.appendLine("# HELP apix_ai_model_success_count Total number of successful requests per model")
        sb.appendLine("# TYPE apix_ai_model_success_count counter")
        filteredModelPerformance.forEach { (model, performance) ->
            sb.appendLine("apix_ai_model_success_count{model=\"$model\"} ${performance.successCount}")
        }

        sb.appendLine("# HELP apix_ai_model_failure_count Total number of failed requests per model")
        sb.appendLine("# TYPE apix_ai_model_failure_count counter")
        filteredModelPerformance.forEach { (model, performance) ->
            sb.appendLine("apix_ai_model_failure_count{model=\"$model\"} ${performance.failureCount}")
        }

        sb.appendLine("# HELP apix_ai_model_avg_latency_ms Average latency in milliseconds per model")
        sb.appendLine("# TYPE apix_ai_model_avg_latency_ms gauge")
        filteredModelPerformance.forEach { (model, performance) ->
            val avgLatency = if (performance.requestCount > 0) {
                performance.totalLatency / performance.requestCount
            } else {
                0
            }
            sb.appendLine("apix_ai_model_avg_latency_ms{model=\"$model\"} $avgLatency")
        }

        sb.appendLine("# HELP apix_ai_model_min_latency_ms Minimum latency in milliseconds per model")
        sb.appendLine("# TYPE apix_ai_model_min_latency_ms gauge")
        filteredModelPerformance.forEach { (model, performance) ->
            sb.appendLine("apix_ai_model_min_latency_ms{model=\"$model\"} ${performance.minLatency}")
        }

        sb.appendLine("# HELP apix_ai_model_max_latency_ms Maximum latency in milliseconds per model")
        sb.appendLine("# TYPE apix_ai_model_max_latency_ms gauge")
        filteredModelPerformance.forEach { (model, performance) ->
            sb.appendLine("apix_ai_model_max_latency_ms{model=\"$model\"} ${performance.maxLatency}")
        }

        sb.appendLine("# HELP apix_ai_model_success_rate Success rate percentage per model")
        sb.appendLine("# TYPE apix_ai_model_success_rate gauge")
        filteredModelPerformance.forEach { (model, performance) ->
            val successRate = if (performance.requestCount > 0) {
                (performance.successCount.toDouble() / performance.requestCount) * 100
            } else {
                0.0
            }
            sb.appendLine("apix_ai_model_success_rate{model=\"$model\"} $successRate")
        }

        return sb.toString()
    }

    /**
     * Clean up old metrics
     */
    private fun cleanupOldMetrics() {
        val cutoffTime = Instant.now().minusSeconds(metricsRetentionHours * 3600L)

        // Clean up request latency records
        requestLatency.forEach { (model, latencies) ->
            val newLatencies = latencies.filter { it.timestamp.isAfter(cutoffTime) }
            if (newLatencies.isEmpty()) {
                requestLatency.remove(model)
            } else {
                requestLatency[model] = newLatencies.toMutableList()
            }
        }

        // Clean up token usage records
        tokenUsage.entries.removeIf { (_, usage) ->
            usage.lastUpdated.isBefore(cutoffTime)
        }

        // Clean up model performance records
        modelPerformance.entries.removeIf { (_, performance) ->
            performance.lastUpdated.isBefore(cutoffTime)
        }
    }

    override fun shutdown() {
        // No resources to clean up
    }

    /**
     * Model information
     */
    data class ModelInfo(
        val modelId: String,
        val provider: String
    )

    /**
     * Token usage information
     */
    data class TokenInfo(
        val inputTokens: Int,
        val outputTokens: Int,
        val totalTokens: Int
    )

    /**
     * Token usage metrics
     */
    data class TokenUsage(
        var inputTokens: Int,
        var outputTokens: Int,
        var totalTokens: Int,
        var lastUpdated: Instant
    )

    /**
     * Request latency record
     */
    data class RequestLatency(
        val timestamp: Instant,
        val latencyMs: Long,
        val success: Boolean
    )

    /**
     * Model performance metrics
     */
    data class ModelPerformance(
        var requestCount: Int,
        var successCount: Int,
        var failureCount: Int,
        var totalLatency: Long,
        var minLatency: Long,
        var maxLatency: Long,
        var lastUpdated: Instant
    )
}
