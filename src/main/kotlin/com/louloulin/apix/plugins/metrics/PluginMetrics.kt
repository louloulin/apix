package com.louloulin.apix.plugins.metrics

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 插件指标收集类
 * 用于收集插件执行的性能指标
 */
class PluginMetrics(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginMetrics::class.java)

    // 使用 ConcurrentHashMap 保证线程安全
    private val executionTimes = ConcurrentHashMap<String, AtomicLong>()
    private val executionCounts = ConcurrentHashMap<String, AtomicLong>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    private val lastExecutionTimes = ConcurrentHashMap<String, Long>()
    private val maxExecutionTimes = ConcurrentHashMap<String, AtomicLong>()
    private val minExecutionTimes = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 记录执行时间
     *
     * @param pluginId 插件ID
     * @param executionTime 执行时间（毫秒）
     * @param success 是否成功
     */
    fun recordExecution(pluginId: String, executionTime: Long, success: Boolean) {
        // 记录执行时间
        executionTimes.computeIfAbsent(pluginId) { AtomicLong(0) }.addAndGet(executionTime)

        // 记录执行次数
        executionCounts.computeIfAbsent(pluginId) { AtomicLong(0) }.incrementAndGet()

        // 记录最后执行时间
        lastExecutionTimes[pluginId] = System.currentTimeMillis()

        // 记录最大执行时间
        maxExecutionTimes.computeIfAbsent(pluginId) { AtomicLong(0) }.updateAndGet { current ->
            if (executionTime > current) executionTime else current
        }

        // 记录最小执行时间
        minExecutionTimes.computeIfAbsent(pluginId) { AtomicLong(Long.MAX_VALUE) }.updateAndGet { current ->
            if (executionTime < current) executionTime else current
        }

        // 记录错误
        if (!success) {
            errorCounts.computeIfAbsent(pluginId) { AtomicLong(0) }.incrementAndGet()
        }

        // 在 Vert.x 中发布指标事件
        vertx.eventBus().publish(
            "metrics.plugin.execution",
            JsonObject()
                .put("pluginId", pluginId)
                .put("executionTime", executionTime)
                .put("success", success)
                .put("timestamp", System.currentTimeMillis())
        )
    }

    /**
     * 记录成功
     *
     * @param pluginId 插件ID
     * @param executionTime 执行时间（毫秒）
     */
    fun recordSuccess(pluginId: String, executionTime: Long) {
        recordExecution(pluginId, executionTime, true)
    }

    /**
     * 记录失败
     *
     * @param pluginId 插件ID
     * @param executionTime 执行时间（毫秒）
     * @param error 错误
     */
    fun recordFailure(pluginId: String, executionTime: Long, error: Throwable) {
        logger.warn("Plugin execution failed: {}", pluginId, error)
        recordExecution(pluginId, executionTime, false)
    }

    /**
     * 获取插件指标
     *
     * @param pluginId 插件ID
     * @return 插件指标
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

        // 最大执行时间
        val maxTime = maxExecutionTimes[pluginId]?.get() ?: 0
        if (maxTime > 0) {
            metrics.put("maxExecutionTime", maxTime)
        }

        // 最小执行时间
        val minTime = minExecutionTimes[pluginId]?.get() ?: Long.MAX_VALUE
        if (minTime < Long.MAX_VALUE) {
            metrics.put("minExecutionTime", minTime)
        }

        // 最后执行时间
        val lastTime = lastExecutionTimes[pluginId]
        if (lastTime != null) {
            metrics.put("lastExecutionTime", lastTime)
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
     *
     * @return 所有插件的指标
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
        lastExecutionTimes.clear()
        maxExecutionTimes.clear()
        minExecutionTimes.clear()
    }

    companion object {
        private var instance: PluginMetrics? = null

        /**
         * 获取单例实例
         *
         * @param vertx Vertx实例
         * @return PluginMetrics实例
         */
        @Synchronized
        fun getInstance(vertx: Vertx): PluginMetrics {
            if (instance == null) {
                instance = PluginMetrics(vertx)
            }
            return instance!!
        }
    }
}
