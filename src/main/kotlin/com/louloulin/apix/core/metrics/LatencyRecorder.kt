package com.louloulin.apix.core.metrics

import io.vertx.core.json.JsonObject
import org.HdrHistogram.ConcurrentHistogram
import org.HdrHistogram.Histogram
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于HdrHistogram的延迟记录器，用于记录和分析系统性能指标。
 * HdrHistogram提供了高动态范围的直方图，可以精确记录延迟分布。
 */
class LatencyRecorder {
    private val logger = LoggerFactory.getLogger(LatencyRecorder::class.java)
    
    // 延迟直方图，按名称分组
    private val histograms = ConcurrentHashMap<String, ConcurrentHistogram>()
    
    // 记录总数，按名称分组
    private val counts = ConcurrentHashMap<String, AtomicLong>()
    
    // 最后重置时间，按名称分组
    private val lastResetTime = ConcurrentHashMap<String, AtomicLong>()
    
    /**
     * 记录延迟
     * 
     * @param name 指标名称
     * @param latency 延迟值
     * @param unit 时间单位
     */
    fun recordLatency(name: String, latency: Long, unit: TimeUnit = TimeUnit.MILLISECONDS) {
        // 获取或创建直方图
        val histogram = getOrCreateHistogram(name)
        
        // 转换为纳秒
        val latencyNanos = unit.toNanos(latency)
        
        try {
            // 记录延迟
            histogram.recordValue(latencyNanos)
            
            // 增加计数
            counts.computeIfAbsent(name) { AtomicLong(0) }.incrementAndGet()
        } catch (e: Exception) {
            logger.warn("记录延迟时发生错误: name={}, latency={}, unit={}", name, latency, unit, e)
        }
    }
    
    /**
     * 获取或创建直方图
     */
    private fun getOrCreateHistogram(name: String): ConcurrentHistogram {
        return histograms.computeIfAbsent(name) {
            // 创建新的直方图，最大值为1小时（纳秒），精度为2位有效数字
            val histogram = ConcurrentHistogram(TimeUnit.HOURS.toNanos(1), 2)
            
            // 设置最后重置时间
            lastResetTime.computeIfAbsent(name) { AtomicLong(System.currentTimeMillis()) }
            
            histogram
        }
    }
    
    /**
     * 获取延迟统计信息
     * 
     * @param name 指标名称
     * @param unit 时间单位
     * @return 延迟统计信息的JsonObject
     */
    fun getLatencyStats(name: String, unit: TimeUnit = TimeUnit.MILLISECONDS): JsonObject? {
        val histogram = histograms[name] ?: return null
        val count = counts[name]?.get() ?: 0
        val resetTime = lastResetTime[name]?.get() ?: 0
        
        // 创建统计信息
        val stats = JsonObject()
            .put("name", name)
            .put("count", count)
            .put("unit", unit.name)
            .put("reset_time", resetTime)
            .put("elapsed_time_ms", System.currentTimeMillis() - resetTime)
        
        // 添加百分位数
        val percentiles = JsonObject()
        percentiles.put("min", convertFromNanos(histogram.minValue, unit))
        percentiles.put("max", convertFromNanos(histogram.maxValue, unit))
        percentiles.put("mean", convertFromNanos(histogram.mean, unit))
        percentiles.put("p50", convertFromNanos(histogram.getValueAtPercentile(50.0), unit))
        percentiles.put("p75", convertFromNanos(histogram.getValueAtPercentile(75.0), unit))
        percentiles.put("p90", convertFromNanos(histogram.getValueAtPercentile(90.0), unit))
        percentiles.put("p95", convertFromNanos(histogram.getValueAtPercentile(95.0), unit))
        percentiles.put("p99", convertFromNanos(histogram.getValueAtPercentile(99.0), unit))
        percentiles.put("p999", convertFromNanos(histogram.getValueAtPercentile(99.9), unit))
        percentiles.put("p9999", convertFromNanos(histogram.getValueAtPercentile(99.99), unit))
        
        stats.put("percentiles", percentiles)
        
        return stats
    }
    
    /**
     * 获取所有延迟统计信息
     * 
     * @param unit 时间单位
     * @return 所有延迟统计信息的JsonObject
     */
    fun getAllLatencyStats(unit: TimeUnit = TimeUnit.MILLISECONDS): JsonObject {
        val allStats = JsonObject()
        
        for (name in histograms.keys) {
            val stats = getLatencyStats(name, unit)
            if (stats != null) {
                allStats.put(name, stats)
            }
        }
        
        return allStats
    }
    
    /**
     * 重置延迟统计信息
     * 
     * @param name 指标名称
     */
    fun resetLatencyStats(name: String) {
        val histogram = histograms[name]
        if (histogram != null) {
            histogram.reset()
            counts[name]?.set(0)
            lastResetTime[name]?.set(System.currentTimeMillis())
            logger.info("重置延迟统计信息: name={}", name)
        }
    }
    
    /**
     * 重置所有延迟统计信息
     */
    fun resetAllLatencyStats() {
        for (name in histograms.keys) {
            resetLatencyStats(name)
        }
        logger.info("重置所有延迟统计信息")
    }
    
    /**
     * 获取直方图的ASCII图表
     * 
     * @param name 指标名称
     * @param unit 时间单位
     * @return ASCII图表字符串
     */
    fun getHistogramChart(name: String, unit: TimeUnit = TimeUnit.MILLISECONDS): String {
        val histogram = histograms[name] ?: return "No histogram found for $name"
        
        val outputStream = ByteArrayOutputStream()
        val printStream = PrintStream(outputStream)
        
        // 打印直方图
        histogram.outputPercentileDistribution(printStream, 1.0)
        
        return outputStream.toString()
    }
    
    /**
     * 将纳秒转换为指定单位
     */
    private fun convertFromNanos(valueNanos: Double, unit: TimeUnit): Double {
        return when (unit) {
            TimeUnit.NANOSECONDS -> valueNanos
            TimeUnit.MICROSECONDS -> valueNanos / 1000.0
            TimeUnit.MILLISECONDS -> valueNanos / 1000000.0
            TimeUnit.SECONDS -> valueNanos / 1000000000.0
            else -> valueNanos / 1000000.0 // 默认毫秒
        }
    }
    
    /**
     * 将纳秒转换为指定单位
     */
    private fun convertFromNanos(valueNanos: Long, unit: TimeUnit): Double {
        return convertFromNanos(valueNanos.toDouble(), unit)
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: LatencyRecorder? = null
        
        /**
         * 获取LatencyRecorder的单例实例
         */
        fun getInstance(): LatencyRecorder {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LatencyRecorder().also { INSTANCE = it }
            }
        }
    }
}
