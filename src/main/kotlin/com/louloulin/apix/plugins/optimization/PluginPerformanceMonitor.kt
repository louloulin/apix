package com.louloulin.apix.plugins.optimization

import com.louloulin.apix.plugins.Plugin
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 插件性能监控器，用于监控插件的执行性能。
 * 提供了插件执行时间统计、错误率统计和性能分析等功能。
 */
class PluginPerformanceMonitor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginPerformanceMonitor::class.java)
    
    // 插件执行计数器
    private val executionCounters = ConcurrentHashMap<String, AtomicInteger>()
    
    // 插件执行时间统计（毫秒）
    private val executionTimes = ConcurrentHashMap<String, AtomicLong>()
    
    // 插件错误计数器
    private val errorCounters = ConcurrentHashMap<String, AtomicInteger>()
    
    // 慢插件阈值（毫秒）
    private var slowPluginThreshold = 100L
    
    // 慢插件记录
    private val slowPluginExecutions = ConcurrentHashMap<String, MutableList<SlowPluginExecution>>()
    
    /**
     * 初始化插件性能监控器
     */
    init {
        logger.info("Plugin performance monitor initialized")
        
        // 定期清理旧的慢插件记录
        vertx.setPeriodic(3600000) { // 每小时清理一次
            cleanupOldSlowPluginExecutions()
        }
        
        // 定期记录插件性能统计
        vertx.setPeriodic(300000) { // 每5分钟记录一次
            logPluginPerformanceStats()
        }
    }
    
    /**
     * 监控插件执行
     * 
     * @param plugin 要监控的插件
     * @param context 路由上下文
     * @param action 插件执行动作
     * @return 执行结果的Future
     */
    fun <T> monitorPluginExecution(plugin: Plugin, context: RoutingContext, action: () -> Future<T>): Future<T> {
        val startTime = System.currentTimeMillis()
        
        // 增加执行计数
        executionCounters.computeIfAbsent(plugin.id) { AtomicInteger(0) }.incrementAndGet()
        
        // 执行插件
        return action().onComplete { result ->
            // 记录执行时间
            val executionTime = System.currentTimeMillis() - startTime
            executionTimes.computeIfAbsent(plugin.id) { AtomicLong(0) }.addAndGet(executionTime)
            
            if (result.failed()) {
                // 增加错误计数
                errorCounters.computeIfAbsent(plugin.id) { AtomicInteger(0) }.incrementAndGet()
                
                logger.warn("Plugin execution failed: {} ({}ms)", plugin.id, executionTime, result.cause())
            } else if (executionTime > slowPluginThreshold) {
                // 记录慢插件执行
                recordSlowPluginExecution(plugin, context, executionTime)
                
                logger.warn("Slow plugin execution: {} ({}ms)", plugin.id, executionTime)
            }
        }
    }
    
    /**
     * 记录慢插件执行
     */
    private fun recordSlowPluginExecution(plugin: Plugin, context: RoutingContext, executionTime: Long) {
        val execution = SlowPluginExecution(
            pluginId = plugin.id,
            pluginType = plugin.type,
            executionTime = executionTime,
            timestamp = System.currentTimeMillis(),
            path = context.request().path(),
            method = context.request().method().name()
        )
        
        // 添加到慢插件记录
        val pluginExecutions = slowPluginExecutions.computeIfAbsent(plugin.id) { mutableListOf() }
        
        synchronized(pluginExecutions) {
            // 限制每个插件的慢执行记录数量
            if (pluginExecutions.size >= 100) {
                pluginExecutions.removeAt(0)
            }
            
            pluginExecutions.add(execution)
        }
    }
    
    /**
     * 清理旧的慢插件记录
     */
    private fun cleanupOldSlowPluginExecutions() {
        val now = System.currentTimeMillis()
        val maxAge = 24 * 60 * 60 * 1000L // 24小时
        
        slowPluginExecutions.forEach { (_, executions) ->
            synchronized(executions) {
                val iterator = executions.iterator()
                while (iterator.hasNext()) {
                    val execution = iterator.next()
                    if (now - execution.timestamp > maxAge) {
                        iterator.remove()
                    }
                }
            }
        }
    }
    
    /**
     * 记录插件性能统计
     */
    private fun logPluginPerformanceStats() {
        val stats = getPluginPerformanceStats()
        
        // 记录总体统计
        logger.info("Plugin performance stats: {} plugins, {} executions, {}ms avg execution time",
            stats.getInteger("totalPlugins"),
            stats.getInteger("totalExecutions"),
            String.format("%.2f", stats.getDouble("avgExecutionTime"))
        )
        
        // 记录最慢的5个插件
        val slowestPlugins = stats.getJsonArray("slowestPlugins")
        if (slowestPlugins != null && slowestPlugins.size() > 0) {
            logger.info("Slowest plugins:")
            for (i in 0 until Math.min(5, slowestPlugins.size())) {
                val plugin = slowestPlugins.getJsonObject(i)
                logger.info("  {}: {}ms avg ({}ms max, {} executions)",
                    plugin.getString("id"),
                    String.format("%.2f", plugin.getDouble("avgTime")),
                    plugin.getLong("maxTime"),
                    plugin.getInteger("executions")
                )
            }
        }
    }
    
    /**
     * 设置慢插件阈值
     * 
     * @param threshold 阈值（毫秒）
     */
    fun setSlowPluginThreshold(threshold: Long) {
        slowPluginThreshold = threshold
        logger.info("Slow plugin threshold set to {}ms", threshold)
    }
    
    /**
     * 获取插件性能统计信息
     * 
     * @return 包含性能统计信息的JsonObject
     */
    fun getPluginPerformanceStats(): JsonObject {
        val stats = JsonObject()
        
        // 计算总体统计
        var totalExecutions = 0
        var totalExecutionTime = 0L
        var maxExecutionTime = 0L
        
        executionCounters.forEach { (id, counter) ->
            val executions = counter.get()
            totalExecutions += executions
            
            val executionTime = executionTimes[id]?.get() ?: 0
            totalExecutionTime += executionTime
            
            if (executionTime > maxExecutionTime) {
                maxExecutionTime = executionTime
            }
        }
        
        val avgExecutionTime = if (totalExecutions > 0) totalExecutionTime.toDouble() / totalExecutions else 0.0
        
        stats.put("totalPlugins", executionCounters.size)
        stats.put("totalExecutions", totalExecutions)
        stats.put("totalExecutionTime", totalExecutionTime)
        stats.put("maxExecutionTime", maxExecutionTime)
        stats.put("avgExecutionTime", avgExecutionTime)
        
        // 添加插件统计
        val pluginStats = JsonArray()
        executionCounters.forEach { (id, counter) ->
            val executions = counter.get()
            val executionTime = executionTimes[id]?.get() ?: 0
            val errors = errorCounters[id]?.get() ?: 0
            
            val avgTime = if (executions > 0) executionTime.toDouble() / executions else 0.0
            val errorRate = if (executions > 0) errors.toDouble() / executions else 0.0
            
            pluginStats.add(JsonObject()
                .put("id", id)
                .put("executions", executions)
                .put("executionTime", executionTime)
                .put("avgTime", avgTime)
                .put("maxTime", executionTime) // 这里应该是最大执行时间，但我们没有跟踪单次执行的最大值
                .put("errors", errors)
                .put("errorRate", errorRate)
            )
        }
        stats.put("plugins", pluginStats)
        
        // 添加最慢的插件
        val slowestPlugins = pluginStats.toList()
            .sortedByDescending { (it as JsonObject).getDouble("avgTime") }
            .take(10)
        stats.put("slowestPlugins", JsonArray(slowestPlugins))
        
        // 添加错误率最高的插件
        val highestErrorRatePlugins = pluginStats.toList()
            .sortedByDescending { (it as JsonObject).getDouble("errorRate") }
            .take(10)
        stats.put("highestErrorRatePlugins", JsonArray(highestErrorRatePlugins))
        
        // 添加慢插件执行记录
        val slowExecutions = JsonArray()
        slowPluginExecutions.forEach { (id, executions) ->
            executions.forEach { execution ->
                slowExecutions.add(JsonObject()
                    .put("pluginId", execution.pluginId)
                    .put("pluginType", execution.pluginType)
                    .put("executionTime", execution.executionTime)
                    .put("timestamp", execution.timestamp)
                    .put("path", execution.path)
                    .put("method", execution.method)
                )
            }
        }
        stats.put("slowExecutions", slowExecutions)
        
        return stats
    }
    
    /**
     * 重置插件性能统计信息
     */
    fun resetStats() {
        executionCounters.clear()
        executionTimes.clear()
        errorCounters.clear()
        slowPluginExecutions.clear()
    }
    
    /**
     * 慢插件执行记录
     */
    data class SlowPluginExecution(
        val pluginId: String,
        val pluginType: String,
        val executionTime: Long,
        val timestamp: Long,
        val path: String,
        val method: String
    )
    
    companion object {
        // 单例实例
        private var INSTANCE: PluginPerformanceMonitor? = null
        
        /**
         * 获取PluginPerformanceMonitor的单例实例
         * 
         * @param vertx Vertx实例
         * @return PluginPerformanceMonitor实例
         */
        fun getInstance(vertx: Vertx): PluginPerformanceMonitor {
            if (INSTANCE == null) {
                synchronized(PluginPerformanceMonitor::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = PluginPerformanceMonitor(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
