package com.louloulin.apix.core.cost

import io.vertx.core.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 模型成本配置
 */
data class ModelCostConfig(
    val modelId: String,
    val inputCostPer1kTokens: Double,
    val outputCostPer1kTokens: Double,
    val provider: String = "",
    val description: String = ""
) {
    companion object {
        fun fromJson(json: JsonObject): ModelCostConfig {
            return ModelCostConfig(
                modelId = json.getString("modelId"),
                inputCostPer1kTokens = json.getDouble("inputCostPer1kTokens", 0.0),
                outputCostPer1kTokens = json.getDouble("outputCostPer1kTokens", 0.0),
                provider = json.getString("provider", ""),
                description = json.getString("description", "")
            )
        }
    }

    fun toJson(): JsonObject {
        return JsonObject()
            .put("modelId", modelId)
            .put("inputCostPer1kTokens", inputCostPer1kTokens)
            .put("outputCostPer1kTokens", outputCostPer1kTokens)
            .put("provider", provider)
            .put("description", description)
    }
}

/**
 * 成本预算配置
 */
data class CostBudgetConfig(
    val id: String,
    val name: String,
    val dailyLimit: Double? = null,
    val monthlyLimit: Double? = null,
    val alertThresholdPercent: Int = 80,
    val alertEmails: List<String> = emptyList(),
    val alertWebhook: String? = null,
    val enabled: Boolean = true,
    val models: List<String> = emptyList(),
    val users: List<String> = emptyList(),
    val applications: List<String> = emptyList()
) {
    companion object {
        fun fromJson(json: JsonObject): CostBudgetConfig {
            return CostBudgetConfig(
                id = json.getString("id"),
                name = json.getString("name"),
                dailyLimit = if (json.containsKey("dailyLimit")) json.getDouble("dailyLimit") else null,
                monthlyLimit = if (json.containsKey("monthlyLimit")) json.getDouble("monthlyLimit") else null,
                alertThresholdPercent = json.getInteger("alertThresholdPercent", 80),
                alertEmails = json.getJsonArray("alertEmails")?.map { it as String } ?: emptyList(),
                alertWebhook = json.getString("alertWebhook"),
                enabled = json.getBoolean("enabled", true),
                models = json.getJsonArray("models")?.map { it as String } ?: emptyList(),
                users = json.getJsonArray("users")?.map { it as String } ?: emptyList(),
                applications = json.getJsonArray("applications")?.map { it as String } ?: emptyList()
            )
        }
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
            .put("id", id)
            .put("name", name)
            .put("alertThresholdPercent", alertThresholdPercent)
            .put("enabled", enabled)
        
        dailyLimit?.let { json.put("dailyLimit", it) }
        monthlyLimit?.let { json.put("monthlyLimit", it) }
        alertWebhook?.let { json.put("alertWebhook", it) }
        
        if (alertEmails.isNotEmpty()) {
            json.put("alertEmails", alertEmails)
        }
        if (models.isNotEmpty()) {
            json.put("models", models)
        }
        if (users.isNotEmpty()) {
            json.put("users", users)
        }
        if (applications.isNotEmpty()) {
            json.put("applications", applications)
        }
        
        return json
    }
}

/**
 * 成本使用记录
 */
data class CostUsage(
    val inputTokens: AtomicLong = AtomicLong(0),
    val outputTokens: AtomicLong = AtomicLong(0),
    val inputCost: AtomicLong = AtomicLong(0), // 以微美元为单位 (1/1,000,000 美元)
    val outputCost: AtomicLong = AtomicLong(0), // 以微美元为单位
    val totalCost: AtomicLong = AtomicLong(0), // 以微美元为单位
    val requestCount: AtomicLong = AtomicLong(0),
    var lastUpdated: Instant = Instant.now()
) {
    fun toJson(): JsonObject {
        return JsonObject()
            .put("inputTokens", inputTokens.get())
            .put("outputTokens", outputTokens.get())
            .put("inputCost", inputCost.get() / 1_000_000.0) // 转换为美元
            .put("outputCost", outputCost.get() / 1_000_000.0) // 转换为美元
            .put("totalCost", totalCost.get() / 1_000_000.0) // 转换为美元
            .put("requestCount", requestCount.get())
            .put("lastUpdated", lastUpdated.toString())
    }
}

/**
 * 每日成本使用统计
 */
data class DailyCostStats(
    val date: LocalDate,
    val modelUsage: ConcurrentHashMap<String, CostUsage> = ConcurrentHashMap(),
    val userUsage: ConcurrentHashMap<String, CostUsage> = ConcurrentHashMap(),
    val appUsage: ConcurrentHashMap<String, CostUsage> = ConcurrentHashMap(),
    val totalUsage: CostUsage = CostUsage()
) {
    fun toJson(): JsonObject {
        val modelUsageJson = JsonObject()
        modelUsage.forEach { (model, usage) ->
            modelUsageJson.put(model, usage.toJson())
        }

        val userUsageJson = JsonObject()
        userUsage.forEach { (user, usage) ->
            userUsageJson.put(user, usage.toJson())
        }

        val appUsageJson = JsonObject()
        appUsage.forEach { (app, usage) ->
            appUsageJson.put(app, usage.toJson())
        }

        return JsonObject()
            .put("date", date.toString())
            .put("modelUsage", modelUsageJson)
            .put("userUsage", userUsageJson)
            .put("appUsage", appUsageJson)
            .put("totalUsage", totalUsage.toJson())
    }
}

/**
 * 成本优化建议
 */
data class CostOptimizationSuggestion(
    val id: String,
    val type: SuggestionType,
    val description: String,
    val potentialSavings: Double,
    val confidence: Int, // 1-100
    val createdAt: Instant = Instant.now()
) {
    enum class SuggestionType {
        MODEL_SWITCH,
        CACHING,
        TOKEN_REDUCTION,
        BATCH_REQUESTS,
        QUOTA_MANAGEMENT,
        OTHER
    }

    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("type", type.name)
            .put("description", description)
            .put("potentialSavings", potentialSavings)
            .put("confidence", confidence)
            .put("createdAt", createdAt.toString())
    }
}

/**
 * 预算警报
 */
data class BudgetAlert(
    val id: String,
    val budgetId: String,
    val budgetName: String,
    val type: AlertType,
    val threshold: Double,
    val currentUsage: Double,
    val percentUsed: Int,
    val createdAt: Instant = Instant.now(),
    val resolved: Boolean = false,
    val resolvedAt: Instant? = null
) {
    enum class AlertType {
        DAILY_THRESHOLD,
        MONTHLY_THRESHOLD,
        DAILY_LIMIT,
        MONTHLY_LIMIT
    }

    fun toJson(): JsonObject {
        val json = JsonObject()
            .put("id", id)
            .put("budgetId", budgetId)
            .put("budgetName", budgetName)
            .put("type", type.name)
            .put("threshold", threshold)
            .put("currentUsage", currentUsage)
            .put("percentUsed", percentUsed)
            .put("createdAt", createdAt.toString())
            .put("resolved", resolved)
        
        resolvedAt?.let { json.put("resolvedAt", it.toString()) }
        
        return json
    }
}
