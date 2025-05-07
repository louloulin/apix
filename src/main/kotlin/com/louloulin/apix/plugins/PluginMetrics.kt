package com.louloulin.apix.plugins

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 插件性能指标收集类
 * 用于收集和展示插件执行的性能指标
 */
class PluginMetrics(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginMetrics::class.java)

    // 使用 ConcurrentHashMap 保证线程安全
    private val executionTimes = ConcurrentHashMap<String, AtomicLong>()
    private val executionCounts = ConcurrentHashMap<String, AtomicLong>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()

    init {
        // 注册 EventBus 处理器，接收插件执行事件
        vertx.eventBus().consumer<JsonObject>("metrics.plugin.execution") { message ->
            val event = message.body()
            val pluginId = event.getString("plugin_id")
            val executionTime = event.getLong("execution_time", 0)
            val success = event.getBoolean("success", true)

            recordExecution(pluginId, executionTime, success)
        }
    }

    /**
     * 记录执行时间和结果
     */
    fun recordExecution(pluginId: String, executionTime: Long, success: Boolean) {
        // 记录执行时间
        executionTimes.computeIfAbsent(pluginId) { AtomicLong(0) }.addAndGet(executionTime)

        // 记录执行次数
        executionCounts.computeIfAbsent(pluginId) { AtomicLong(0) }.incrementAndGet()

        // 记录错误
        if (!success) {
            errorCounts.computeIfAbsent(pluginId) { AtomicLong(0) }.incrementAndGet()
        }

        // 在 Vert.x 中发布指标事件
        vertx.eventBus().publish(
            "metrics.plugin.execution.recorded",
            JsonObject()
                .put("pluginId", pluginId)
                .put("executionTime", executionTime)
                .put("success", success)
        )
    }

    /**
     * 记录成功
     */
    fun recordSuccess(pluginId: String) {
        recordExecution(pluginId, 0, true)
    }

    /**
     * 记录失败
     */
    fun recordFailure(pluginId: String, error: Throwable) {
        logger.warn("Plugin execution failed: {}", pluginId, error)
        recordExecution(pluginId, 0, false)
    }

    /**
     * 获取插件指标
     */
    fun getMetrics(pluginId: String): JsonObject {
        val metrics = JsonObject()

        // 执行次数
        val executions = executionCounts[pluginId]?.get() ?: 0
        metrics.put("executions", executions)

        // 平均执行时间
        val totalTime = executionTimes[pluginId]?.get() ?: 0
        if (executions > 0) {
            metrics.put("avgExecutionTime", totalTime.toDouble() / executions)
        }

        // 错误率
        val errors = errorCounts[pluginId]?.get() ?: 0
        metrics.put("errors", errors)
        if (executions > 0) {
            metrics.put("errorRate", errors.toDouble() / executions)
        }

        return metrics
    }

    /**
     * 获取所有插件的指标
     */
    fun getAllMetrics(): JsonObject {
        val result = JsonObject()

        // 收集所有插件的指标
        executionCounts.keys.forEach { pluginId ->
            result.put(pluginId, getMetrics(pluginId))
        }

        return result
    }

    /**
     * 重置指标
     */
    fun resetMetrics() {
        executionTimes.clear()
        executionCounts.clear()
        errorCounts.clear()
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PluginMetrics? = null

        /**
         * 获取 PluginMetrics 的单例实例
         */
        fun getInstance(vertx: Vertx): PluginMetrics {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PluginMetrics(vertx).also { INSTANCE = it }
            }
        }
    }
}
