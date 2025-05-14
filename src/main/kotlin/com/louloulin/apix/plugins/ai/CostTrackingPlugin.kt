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
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Plugin that tracks and manages AI model usage costs.
 *
 * This plugin:
 * 1. Calculates costs based on model usage and token consumption
 * 2. Provides budget management and alerts
 * 3. Offers cost optimization suggestions
 */
class CostTrackingPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(CostTrackingPlugin::class.java)

    override val type: String = "cost-tracking"

    // Configuration values with defaults
    private val enabled: Boolean = config.config.getBoolean("enabled", true)
    private val trackCosts: Boolean = config.config.getBoolean("track_costs", true)
    private val budgetEnabled: Boolean = config.config.getBoolean("budget_enabled", false)
    private val alertThresholdPercent: Int = config.config.getInteger("alert_threshold_percent", 80)
    private val optimizationSuggestionsEnabled: Boolean = config.config.getBoolean("optimization_suggestions_enabled", true)
    private val retentionDays: Int = config.config.getInteger("retention_days", 90)

    // Budget configuration
    private val monthlyBudget: Double = config.config.getDouble("monthly_budget", 0.0)
    private val dailyBudget: Double = config.config.getDouble("daily_budget", 0.0)

    // Model pricing configuration
    private val modelPricing: Map<String, ModelPricing> = parseModelPricing(config.config.getJsonObject("model_pricing"))

    // Default pricing for common models
    private val defaultPricing = mapOf(
        // OpenAI models
        "gpt-4" to ModelPricing(0.03, 0.06, "openai", "1K tokens"),
        "gpt-4-32k" to ModelPricing(0.06, 0.12, "openai", "1K tokens"),
        "gpt-4-turbo" to ModelPricing(0.01, 0.03, "openai", "1K tokens"),
        "gpt-3.5-turbo" to ModelPricing(0.0015, 0.002, "openai", "1K tokens"),
        "gpt-3.5-turbo-16k" to ModelPricing(0.003, 0.004, "openai", "1K tokens"),
        "text-embedding-ada-002" to ModelPricing(0.0001, 0.0, "openai", "1K tokens"),
        
        // Anthropic models
        "claude-2" to ModelPricing(0.008, 0.024, "anthropic", "1K tokens"),
        "claude-instant-1" to ModelPricing(0.0008, 0.0024, "anthropic", "1K tokens"),
        
        // Cohere models
        "command" to ModelPricing(0.0015, 0.0020, "cohere", "1K tokens"),
        "command-light" to ModelPricing(0.0003, 0.0006, "cohere", "1K tokens"),
        "embed-english-v3.0" to ModelPricing(0.0001, 0.0, "cohere", "1K tokens")
    )

    // Cost tracking storage
    private val dailyCosts = ConcurrentHashMap<String, ConcurrentHashMap<String, Double>>() // date -> model -> cost
    private val modelCosts = ConcurrentHashMap<String, Double>() // model -> total cost
    private val userCosts = ConcurrentHashMap<String, Double>() // user -> total cost
    private val appCosts = ConcurrentHashMap<String, Double>() // app -> total cost
    
    // Budget tracking
    private val dailyUsage = AtomicLong(0) // In cents
    private val monthlyUsage = AtomicLong(0) // In cents
    private val budgetAlertsSent = ConcurrentHashMap<String, Boolean>() // date -> alert sent

    // Optimization suggestions
    private val modelUsagePatterns = ConcurrentHashMap<String, ModelUsagePattern>() // model -> usage pattern

    /**
     * Initialize the plugin
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            logger.info("Initializing CostTrackingPlugin")
            
            // Schedule daily and monthly reset
            setupPeriodicTasks(vertx)
            
            // Load existing cost data if available
            loadCostData()
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to initialize CostTrackingPlugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Execute the plugin
     */
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!enabled || !trackCosts) {
            // Plugin is disabled, skip processing
            promise.complete()
            return promise.future()
        }

        try {
            // Check if this is a cost API request
            val path = context.request().path()
            if (path.endsWith("/costs") || path.endsWith("/costs/")) {
                handleCostQuery(context)
                promise.complete()
                return promise.future()
            }

            // Extract user and app information
            val userId = extractUserId(context)
            val appId = extractAppId(context)
            
            // Add response handler to track costs
            context.addHeadersEndHandler { _ ->
                try {
                    // Get model info and token usage from context
                    val modelInfo = context.get<AIMetricsPlugin.ModelInfo>("ai_model_info")
                    val responseBody = context.response().headers().get("X-Response-Body")
                    
                    if (modelInfo != null && responseBody != null) {
                        try {
                            val responseJson = JsonObject(responseBody)
                            val tokenInfo = extractTokenInfo(responseJson, modelInfo.provider)
                            
                            if (tokenInfo != null) {
                                // Calculate and track cost
                                val cost = calculateCost(modelInfo.modelId, tokenInfo.inputTokens, tokenInfo.outputTokens)
                                if (cost > 0) {
                                    trackCost(modelInfo.modelId, cost, userId, appId)
                                    
                                    // Check budget if enabled
                                    if (budgetEnabled) {
                                        checkBudget()
                                    }
                                    
                                    // Update usage patterns for optimization suggestions
                                    if (optimizationSuggestionsEnabled) {
                                        updateUsagePattern(modelInfo.modelId, tokenInfo.inputTokens, tokenInfo.outputTokens)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logger.debug("Failed to parse response body for cost tracking", e)
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error tracking costs", e)
                }
            }

            // Continue with the request
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing cost tracking plugin", e)
            promise.fail(e)
        }

        return promise.future()
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
     * Calculate cost based on model and token usage
     */
    private fun calculateCost(modelId: String, inputTokens: Int, outputTokens: Int): Double {
        // Get pricing for the model
        val pricing = modelPricing[modelId] ?: defaultPricing[modelId] ?: ModelPricing(0.0, 0.0, "unknown", "1K tokens")
        
        // Calculate cost based on token usage
        val inputCost = (inputTokens.toDouble() / 1000.0) * pricing.inputPricePerUnit
        val outputCost = (outputTokens.toDouble() / 1000.0) * pricing.outputPricePerUnit
        
        return inputCost + outputCost
    }

    /**
     * Track cost for a model
     */
    private fun trackCost(modelId: String, cost: Double, userId: String?, appId: String?) {
        // Get today's date
        val today = LocalDate.now().toString()
        
        // Update daily costs
        dailyCosts.computeIfAbsent(today) { ConcurrentHashMap() }
            .compute(modelId) { _, current -> (current ?: 0.0) + cost }
        
        // Update model costs
        modelCosts.compute(modelId) { _, current -> (current ?: 0.0) + cost }
        
        // Update user costs if user ID is available
        if (userId != null && userId.isNotEmpty()) {
            userCosts.compute(userId) { _, current -> (current ?: 0.0) + cost }
        }
        
        // Update app costs if app ID is available
        if (appId != null && appId.isNotEmpty()) {
            appCosts.compute(appId) { _, current -> (current ?: 0.0) + cost }
        }
        
        // Update budget tracking
        val costInCents = (cost * 100).toLong()
        dailyUsage.addAndGet(costInCents)
        monthlyUsage.addAndGet(costInCents)
        
        // Save cost data periodically (in a real implementation)
        // saveCostData()
    }

    /**
     * Check budget and send alerts if necessary
     */
    private fun checkBudget() {
        val today = LocalDate.now().toString()
        
        // Check daily budget
        if (dailyBudget > 0) {
            val dailyUsageAmount = dailyUsage.get() / 100.0
            val dailyPercentage = (dailyUsageAmount / dailyBudget) * 100
            
            if (dailyPercentage >= alertThresholdPercent && !budgetAlertsSent.getOrDefault("daily_$today", false)) {
                // Send daily budget alert
                logger.warn("Daily budget alert: Used ${String.format("%.2f", dailyPercentage)}% of daily budget ($${String.format("%.2f", dailyUsageAmount)} of $${String.format("%.2f", dailyBudget)})")
                budgetAlertsSent["daily_$today"] = true
                
                // In a real implementation, we would send an alert to the admin
            }
        }
        
        // Check monthly budget
        if (monthlyBudget > 0) {
            val monthlyUsageAmount = monthlyUsage.get() / 100.0
            val monthlyPercentage = (monthlyUsageAmount / monthlyBudget) * 100
            
            val month = LocalDate.now().month.toString()
            if (monthlyPercentage >= alertThresholdPercent && !budgetAlertsSent.getOrDefault("monthly_$month", false)) {
                // Send monthly budget alert
                logger.warn("Monthly budget alert: Used ${String.format("%.2f", monthlyPercentage)}% of monthly budget ($${String.format("%.2f", monthlyUsageAmount)} of $${String.format("%.2f", monthlyBudget)})")
                budgetAlertsSent["monthly_$month"] = true
                
                // In a real implementation, we would send an alert to the admin
            }
        }
    }

    /**
     * Update usage pattern for a model
     */
    private fun updateUsagePattern(modelId: String, inputTokens: Int, outputTokens: Int) {
        modelUsagePatterns.compute(modelId) { _, pattern ->
            val current = pattern ?: ModelUsagePattern(
                requestCount = 0,
                totalInputTokens = 0,
                totalOutputTokens = 0,
                avgInputTokens = 0.0,
                avgOutputTokens = 0.0,
                totalCost = 0.0,
                lastUpdated = Instant.now()
            )
            
            current.requestCount++
            current.totalInputTokens += inputTokens
            current.totalOutputTokens += outputTokens
            current.avgInputTokens = current.totalInputTokens.toDouble() / current.requestCount
            current.avgOutputTokens = current.totalOutputTokens.toDouble() / current.requestCount
            current.totalCost += calculateCost(modelId, inputTokens, outputTokens)
            current.lastUpdated = Instant.now()
            
            current
        }
    }

    /**
     * Generate cost optimization suggestions
     */
    private fun generateOptimizationSuggestions(): JsonArray {
        val suggestions = JsonArray()
        
        // Analyze model usage patterns
        modelUsagePatterns.forEach { (modelId, pattern) ->
            // Check if a cheaper model could be used
            val suggestion = JsonObject()
            
            when {
                // For GPT-4 models, suggest GPT-3.5 for simpler tasks
                modelId.startsWith("gpt-4") && pattern.avgInputTokens < 500 && pattern.avgOutputTokens < 200 -> {
                    val potentialSavings = pattern.totalCost * 0.8 // Approx 80% savings
                    suggestion.put("type", "model_downgrade")
                        .put("model", modelId)
                        .put("suggestion", "Consider using gpt-3.5-turbo for simpler tasks")
                        .put("potential_savings", String.format("$%.2f", potentialSavings))
                        .put("reasoning", "Your average input (${pattern.avgInputTokens.toInt()} tokens) and output (${pattern.avgOutputTokens.toInt()} tokens) are relatively small, suggesting simpler tasks that might work well with gpt-3.5-turbo at ~20% of the cost.")
                }
                
                // For 16k/32k context models with small actual usage
                (modelId.contains("-16k") || modelId.contains("-32k")) && pattern.avgInputTokens < 2000 -> {
                    val potentialSavings = pattern.totalCost * 0.5 // Approx 50% savings
                    suggestion.put("type", "context_size")
                        .put("model", modelId)
                        .put("suggestion", "Consider using standard context model")
                        .put("potential_savings", String.format("$%.2f", potentialSavings))
                        .put("reasoning", "Your average input (${pattern.avgInputTokens.toInt()} tokens) is much smaller than the context size you're paying for.")
                }
                
                // For high token usage, suggest prompt optimization
                pattern.avgInputTokens > 1000 -> {
                    val potentialSavings = pattern.totalCost * 0.3 // Approx 30% savings
                    suggestion.put("type", "prompt_optimization")
                        .put("model", modelId)
                        .put("suggestion", "Consider optimizing prompts to reduce token usage")
                        .put("potential_savings", String.format("$%.2f", potentialSavings))
                        .put("reasoning", "Your average input (${pattern.avgInputTokens.toInt()} tokens) is relatively high. Optimizing prompts could reduce costs.")
                }
            }
            
            if (suggestion.size() > 0) {
                suggestions.add(suggestion)
            }
        }
        
        return suggestions
    }

    /**
     * Handle cost query request
     */
    private fun handleCostQuery(context: RoutingContext) {
        val format = context.request().getParam("format") ?: "json"
        val model = context.request().getParam("model")
        val user = context.request().getParam("user")
        val app = context.request().getParam("app")
        val period = context.request().getParam("period") ?: "all"
        
        when (format.lowercase()) {
            "csv" -> {
                val csv = generateCsvReport(model, user, app, period)
                context.response()
                    .putHeader("Content-Type", "text/csv")
                    .putHeader("Content-Disposition", "attachment; filename=\"cost_report.csv\"")
                    .end(csv)
            }
            else -> {
                val costs = generateJsonCosts(model, user, app, period)
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(costs.encode())
            }
        }
    }

    /**
     * Generate costs in JSON format
     */
    private fun generateJsonCosts(modelFilter: String?, userFilter: String?, appFilter: String?, period: String): JsonObject {
        val result = JsonObject()
        
        // Overall costs
        val overall = JsonObject()
            .put("totalCost", String.format("$%.2f", calculateTotalCost()))
            .put("dailyBudget", if (dailyBudget > 0) String.format("$%.2f", dailyBudget) else "Not set")
            .put("monthlyBudget", if (monthlyBudget > 0) String.format("$%.2f", monthlyBudget) else "Not set")
            .put("dailyUsage", String.format("$%.2f", dailyUsage.get() / 100.0))
            .put("monthlyUsage", String.format("$%.2f", monthlyUsage.get() / 100.0))
        
        result.put("overall", overall)
        
        // Model costs
        val filteredModelCosts = if (modelFilter != null) {
            modelCosts.filterKeys { it == modelFilter }
        } else {
            modelCosts
        }
        
        val modelCostsJson = JsonObject()
        filteredModelCosts.forEach { (model, cost) ->
            modelCostsJson.put(model, String.format("$%.4f", cost))
        }
        
        result.put("modelCosts", modelCostsJson)
        
        // User costs
        if (userFilter == null) {
            val userCostsJson = JsonObject()
            userCosts.forEach { (user, cost) ->
                userCostsJson.put(user, String.format("$%.4f", cost))
            }
            result.put("userCosts", userCostsJson)
        } else if (userCosts.containsKey(userFilter)) {
            result.put("userCosts", JsonObject().put(userFilter, String.format("$%.4f", userCosts[userFilter])))
        }
        
        // App costs
        if (appFilter == null) {
            val appCostsJson = JsonObject()
            appCosts.forEach { (app, cost) ->
                appCostsJson.put(app, String.format("$%.4f", cost))
            }
            result.put("appCosts", appCostsJson)
        } else if (appCosts.containsKey(appFilter)) {
            result.put("appCosts", JsonObject().put(appFilter, String.format("$%.4f", appCosts[appFilter])))
        }
        
        // Daily costs
        val dailyCostsJson = JsonObject()
        val filteredDailyCosts = when (period) {
            "today" -> {
                val today = LocalDate.now().toString()
                dailyCosts.filterKeys { it == today }
            }
            "week" -> {
                val weekAgo = LocalDate.now().minusDays(7).toString()
                dailyCosts.filterKeys { it >= weekAgo }
            }
            "month" -> {
                val monthAgo = LocalDate.now().minusDays(30).toString()
                dailyCosts.filterKeys { it >= monthAgo }
            }
            else -> dailyCosts
        }
        
        filteredDailyCosts.forEach { (date, models) ->
            val dateTotal = models.values.sum()
            dailyCostsJson.put(date, String.format("$%.4f", dateTotal))
        }
        
        result.put("dailyCosts", dailyCostsJson)
        
        // Add optimization suggestions
        if (optimizationSuggestionsEnabled) {
            result.put("optimizationSuggestions", generateOptimizationSuggestions())
        }
        
        return result
    }

    /**
     * Generate CSV cost report
     */
    private fun generateCsvReport(modelFilter: String?, userFilter: String?, appFilter: String?, period: String): String {
        val sb = StringBuilder()
        
        // Header
        sb.appendLine("Date,Model,Cost")
        
        // Filter daily costs
        val filteredDailyCosts = when (period) {
            "today" -> {
                val today = LocalDate.now().toString()
                dailyCosts.filterKeys { it == today }
            }
            "week" -> {
                val weekAgo = LocalDate.now().minusDays(7).toString()
                dailyCosts.filterKeys { it >= weekAgo }
            }
            "month" -> {
                val monthAgo = LocalDate.now().minusDays(30).toString()
                dailyCosts.filterKeys { it >= monthAgo }
            }
            else -> dailyCosts
        }
        
        // Generate report rows
        filteredDailyCosts.forEach { (date, models) ->
            val filteredModels = if (modelFilter != null) {
                models.filterKeys { it == modelFilter }
            } else {
                models
            }
            
            filteredModels.forEach { (model, cost) ->
                sb.appendLine("$date,$model,$${String.format("%.4f", cost)}")
            }
        }
        
        return sb.toString()
    }

    /**
     * Calculate total cost across all models
     */
    private fun calculateTotalCost(): Double {
        return modelCosts.values.sum()
    }

    /**
     * Extract user ID from request
     */
    private fun extractUserId(context: RoutingContext): String? {
        // Try to get user ID from various sources
        return context.request().getHeader("X-User-ID")
            ?: context.request().getParam("user_id")
            ?: context.user()?.principal()?.getString("sub")
    }

    /**
     * Extract app ID from request
     */
    private fun extractAppId(context: RoutingContext): String? {
        // Try to get app ID from various sources
        return context.request().getHeader("X-App-ID")
            ?: context.request().getParam("app_id")
    }

    /**
     * Parse model pricing configuration
     */
    private fun parseModelPricing(pricingConfig: JsonObject?): Map<String, ModelPricing> {
        if (pricingConfig == null) {
            return emptyMap()
        }
        
        val result = mutableMapOf<String, ModelPricing>()
        
        pricingConfig.forEach { entry ->
            val modelId = entry.key
            val config = entry.value as? JsonObject
            
            if (config != null) {
                val inputPrice = config.getDouble("input_price", 0.0)
                val outputPrice = config.getDouble("output_price", 0.0)
                val provider = config.getString("provider", "unknown")
                val unit = config.getString("unit", "1K tokens")
                
                result[modelId] = ModelPricing(inputPrice, outputPrice, provider, unit)
            }
        }
        
        return result
    }

    /**
     * Set up periodic tasks
     */
    private fun setupPeriodicTasks(vertx: Vertx) {
        // Reset daily usage at midnight
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val now = Instant.now().toEpochMilli()
        val initialDelay = midnight - now
        
        vertx.setTimer(initialDelay) { _ ->
            // Reset daily usage
            dailyUsage.set(0)
            
            // Clear daily budget alerts
            val today = LocalDate.now().toString()
            budgetAlertsSent.remove("daily_$today")
            
            // Schedule next reset
            vertx.setPeriodic(24 * 60 * 60 * 1000) { _ ->
                dailyUsage.set(0)
                val newDay = LocalDate.now().toString()
                budgetAlertsSent.remove("daily_$newDay")
            }
        }
        
        // Reset monthly usage on the 1st of each month
        val firstOfNextMonth = LocalDate.now()
            .plusMonths(1)
            .withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val initialMonthlyDelay = firstOfNextMonth - now
        
        vertx.setTimer(initialMonthlyDelay) { _ ->
            // Reset monthly usage
            monthlyUsage.set(0)
            
            // Clear monthly budget alerts
            val month = LocalDate.now().month.toString()
            budgetAlertsSent.remove("monthly_$month")
            
            // Schedule next reset
            vertx.setPeriodic(30 * 24 * 60 * 60 * 1000) { _ -> // Approximate month length
                monthlyUsage.set(0)
                val newMonth = LocalDate.now().month.toString()
                budgetAlertsSent.remove("monthly_$newMonth")
            }
        }
        
        // Clean up old data periodically
        vertx.setPeriodic(24 * 60 * 60 * 1000) { _ -> // Daily cleanup
            cleanupOldData()
        }
    }

    /**
     * Clean up old data
     */
    private fun cleanupOldData() {
        if (retentionDays <= 0) {
            return
        }
        
        val cutoffDate = LocalDate.now().minusDays(retentionDays.toLong()).toString()
        
        // Remove old daily costs
        dailyCosts.keys.removeIf { it < cutoffDate }
    }

    /**
     * Load cost data from storage
     */
    private fun loadCostData() {
        // In a real implementation, we would load cost data from a database or file
        logger.info("Would load cost data from storage in a real implementation")
    }

    /**
     * Save cost data to storage
     */
    private fun saveCostData() {
        // In a real implementation, we would save cost data to a database or file
        logger.info("Would save cost data to storage in a real implementation")
    }

    override fun shutdown() {
        // Save cost data before shutdown
        saveCostData()
    }

    /**
     * Model pricing information
     */
    data class ModelPricing(
        val inputPricePerUnit: Double,
        val outputPricePerUnit: Double,
        val provider: String,
        val unit: String
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
     * Model usage pattern for optimization suggestions
     */
    data class ModelUsagePattern(
        var requestCount: Int,
        var totalInputTokens: Int,
        var totalOutputTokens: Int,
        var avgInputTokens: Double,
        var avgOutputTokens: Double,
        var totalCost: Double,
        var lastUpdated: Instant
    )
}
