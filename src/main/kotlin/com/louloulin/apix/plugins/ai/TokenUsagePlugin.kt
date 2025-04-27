package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 令牌使用跟踪插件，用于跟踪AI服务的令牌使用情况。
 */
class TokenUsagePlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(TokenUsagePlugin::class.java)
    
    override val type: String = "token-usage"
    
    // 配置值
    private val trackByModel: Boolean = config.getBoolean("track_by_model", true) ?: true
    private val trackByUser: Boolean = config.getBoolean("track_by_user", true) ?: true
    private val userIdHeader: String = config.getString("user_id_header", "X-User-ID") ?: "X-User-ID"
    
    // 使用统计
    private val dailyUsage = ConcurrentHashMap<String, UsageStats>()
    
    /**
     * 使用统计。
     */
    data class UsageStats(
        val promptTokens: AtomicLong = AtomicLong(0),
        val completionTokens: AtomicLong = AtomicLong(0),
        val totalTokens: AtomicLong = AtomicLong(0),
        val requests: AtomicLong = AtomicLong(0),
        val modelUsage: ConcurrentHashMap<String, ModelUsage> = ConcurrentHashMap(),
        val userUsage: ConcurrentHashMap<String, UserUsage> = ConcurrentHashMap()
    )
    
    /**
     * 模型使用统计。
     */
    data class ModelUsage(
        val model: String,
        val promptTokens: AtomicLong = AtomicLong(0),
        val completionTokens: AtomicLong = AtomicLong(0),
        val totalTokens: AtomicLong = AtomicLong(0),
        val requests: AtomicLong = AtomicLong(0)
    )
    
    /**
     * 用户使用统计。
     */
    data class UserUsage(
        val userId: String,
        val promptTokens: AtomicLong = AtomicLong(0),
        val completionTokens: AtomicLong = AtomicLong(0),
        val totalTokens: AtomicLong = AtomicLong(0),
        val requests: AtomicLong = AtomicLong(0)
    )
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 添加响应处理器来跟踪令牌使用情况
            context.addBodyEndHandler {
                try {
                    trackTokenUsage(context)
                } catch (e: Exception) {
                    logger.error("Error tracking token usage", e)
                }
            }
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing token usage plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 跟踪令牌使用情况。
     */
    private fun trackTokenUsage(context: RoutingContext) {
        val response = context.response()
        val statusCode = response.statusCode
        
        // 只跟踪成功的响应
        if (statusCode != 200) {
            return
        }
        
        // 获取响应体
        val body = context.body().buffer()
        if (body == null) {
            return
        }
        
        try {
            // 解析响应体
            val jsonBody = JsonObject(body.toString())
            
            // 提取令牌使用信息
            val usage = jsonBody.getJsonObject("usage")
            if (usage == null) {
                return
            }
            
            val promptTokens = usage.getLong("prompt_tokens", 0)
            val completionTokens = usage.getLong("completion_tokens", 0)
            val totalTokens = usage.getLong("total_tokens", 0)
            
            // 获取模型信息
            val model = jsonBody.getString("model", "unknown")
            
            // 获取用户ID
            val userId = context.request().getHeader(userIdHeader) ?: "anonymous"
            
            // 更新使用统计
            updateUsageStats(promptTokens, completionTokens, totalTokens, model, userId)
            
            // 添加使用统计头部
            response.putHeader("X-Prompt-Tokens", promptTokens.toString())
            response.putHeader("X-Completion-Tokens", completionTokens.toString())
            response.putHeader("X-Total-Tokens", totalTokens.toString())
        } catch (e: Exception) {
            logger.debug("Error parsing response body for token usage", e)
        }
    }
    
    /**
     * 更新使用统计。
     */
    private fun updateUsageStats(
        promptTokens: Long,
        completionTokens: Long,
        totalTokens: Long,
        model: String,
        userId: String
    ) {
        val today = LocalDate.now().toString()
        
        // 获取或创建今天的使用统计
        val stats = dailyUsage.computeIfAbsent(today) { UsageStats() }
        
        // 更新总体统计
        stats.promptTokens.addAndGet(promptTokens)
        stats.completionTokens.addAndGet(completionTokens)
        stats.totalTokens.addAndGet(totalTokens)
        stats.requests.incrementAndGet()
        
        // 更新模型统计
        if (trackByModel) {
            val modelStats = stats.modelUsage.computeIfAbsent(model) { ModelUsage(model) }
            modelStats.promptTokens.addAndGet(promptTokens)
            modelStats.completionTokens.addAndGet(completionTokens)
            modelStats.totalTokens.addAndGet(totalTokens)
            modelStats.requests.incrementAndGet()
        }
        
        // 更新用户统计
        if (trackByUser) {
            val userStats = stats.userUsage.computeIfAbsent(userId) { UserUsage(userId) }
            userStats.promptTokens.addAndGet(promptTokens)
            userStats.completionTokens.addAndGet(completionTokens)
            userStats.totalTokens.addAndGet(totalTokens)
            userStats.requests.incrementAndGet()
        }
    }
    
    /**
     * 获取使用统计。
     */
    fun getUsageStats(): JsonObject {
        val result = JsonObject()
        
        // 添加总体统计
        var totalPromptTokens = 0L
        var totalCompletionTokens = 0L
        var totalTokens = 0L
        var totalRequests = 0L
        
        // 按日期统计
        val dailyStats = JsonObject()
        dailyUsage.forEach { (date, stats) ->
            val dateStats = JsonObject()
                .put("prompt_tokens", stats.promptTokens.get())
                .put("completion_tokens", stats.completionTokens.get())
                .put("total_tokens", stats.totalTokens.get())
                .put("requests", stats.requests.get())
            
            // 添加模型统计
            if (trackByModel) {
                val modelStats = JsonObject()
                stats.modelUsage.forEach { (modelName, usage) ->
                    modelStats.put(modelName, JsonObject()
                        .put("prompt_tokens", usage.promptTokens.get())
                        .put("completion_tokens", usage.completionTokens.get())
                        .put("total_tokens", usage.totalTokens.get())
                        .put("requests", usage.requests.get())
                    )
                }
                dateStats.put("models", modelStats)
            }
            
            // 添加用户统计
            if (trackByUser) {
                val userStats = JsonObject()
                stats.userUsage.forEach { (user, usage) ->
                    userStats.put(user, JsonObject()
                        .put("prompt_tokens", usage.promptTokens.get())
                        .put("completion_tokens", usage.completionTokens.get())
                        .put("total_tokens", usage.totalTokens.get())
                        .put("requests", usage.requests.get())
                    )
                }
                dateStats.put("users", userStats)
            }
            
            dailyStats.put(date, dateStats)
            
            // 更新总体统计
            totalPromptTokens += stats.promptTokens.get()
            totalCompletionTokens += stats.completionTokens.get()
            totalTokens += stats.totalTokens.get()
            totalRequests += stats.requests.get()
        }
        
        // 添加总体统计
        result.put("total_prompt_tokens", totalPromptTokens)
            .put("total_completion_tokens", totalCompletionTokens)
            .put("total_tokens", totalTokens)
            .put("total_requests", totalRequests)
            .put("daily", dailyStats)
        
        return result
    }
    
    /**
     * 清除过期的使用统计。
     */
    fun cleanupOldStats(daysToKeep: Int = 30) {
        val cutoffDate = LocalDate.now().minusDays(daysToKeep.toLong())
        
        // 移除旧的统计
        val keysToRemove = dailyUsage.keys
            .filter { LocalDate.parse(it).isBefore(cutoffDate) }
        
        keysToRemove.forEach { dailyUsage.remove(it) }
        
        logger.info("Cleaned up {} old usage stats entries", keysToRemove.size)
    }
    
    override fun shutdown() {
        // 没有资源需要清理
    }
}
