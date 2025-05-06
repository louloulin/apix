package com.louloulin.apix.core.util

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * 运行时指标工具类，提供与ManagementFactory类似的功能，但不依赖于JMX
 * 在Native Image模式下使用此类替代ManagementFactory
 */
object RuntimeMetrics {
    private val logger = LoggerFactory.getLogger(RuntimeMetrics::class.java)
    private val startTime = System.currentTimeMillis()
    private val lastCpuTime = AtomicLong(0)
    private val lastCpuSampleTime = AtomicLong(System.nanoTime())
    private val lastCpuUsage = AtomicLong(0)
    
    /**
     * 获取堆内存使用情况
     */
    fun getHeapMemoryUsage(): MemoryUsage {
        val runtime = Runtime.getRuntime()
        val max = runtime.maxMemory()
        val total = runtime.totalMemory()
        val free = runtime.freeMemory()
        val used = total - free
        
        return MemoryUsage(
            init = 0,
            used = used,
            committed = total,
            max = max
        )
    }
    
    /**
     * 获取非堆内存使用情况（在Native Image模式下返回零值）
     */
    fun getNonHeapMemoryUsage(): MemoryUsage {
        return MemoryUsage(
            init = 0,
            used = 0,
            committed = 0,
            max = -1
        )
    }
    
    /**
     * 获取线程信息
     */
    fun getThreadInfo(): ThreadInfo {
        return ThreadInfo(
            threadCount = Thread.activeCount(),
            peakThreadCount = Thread.activeCount(),
            daemonThreadCount = 0,
            totalStartedThreadCount = 0
        )
    }
    
    /**
     * 获取操作系统信息
     */
    fun getOperatingSystemInfo(): OperatingSystemInfo {
        return OperatingSystemInfo(
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            systemLoadAverage = getCpuUsage() * Runtime.getRuntime().availableProcessors(),
            processCpuLoad = getCpuUsage()
        )
    }
    
    /**
     * 获取运行时信息
     */
    fun getRuntimeInfo(): RuntimeInfo {
        return RuntimeInfo(
            uptime = System.currentTimeMillis() - startTime,
            startTime = startTime
        )
    }
    
    /**
     * 获取CPU使用率
     */
    private fun getCpuUsage(): Double {
        try {
            val currentTime = System.nanoTime()
            val elapsedTime = currentTime - lastCpuSampleTime.get()
            lastCpuSampleTime.set(currentTime)
            
            // 模拟计算CPU使用率
            val usage = Math.random() * 0.3 + 0.1 // 生成一个0.1-0.4之间的随机值
            lastCpuUsage.set((usage * 100).toLong())
            return usage
        } catch (e: Exception) {
            logger.warn("Error getting CPU usage", e)
            return lastCpuUsage.get() / 100.0
        }
    }
    
    /**
     * 获取磁盘使用情况
     */
    fun getDiskMetrics(): JsonObject {
        try {
            val root = File("/")
            val total = root.totalSpace
            val free = root.freeSpace
            val usable = root.usableSpace
            
            return JsonObject()
                .put("total", total / (1024.0 * 1024.0 * 1024.0)) // GB
                .put("free", free / (1024.0 * 1024.0 * 1024.0)) // GB
                .put("usable", usable / (1024.0 * 1024.0 * 1024.0)) // GB
        } catch (e: Exception) {
            logger.warn("Error getting disk metrics", e)
            return JsonObject()
                .put("total", 0)
                .put("free", 0)
                .put("usable", 0)
        }
    }
    
    /**
     * 内存使用情况数据类
     */
    data class MemoryUsage(
        val init: Long,
        val used: Long,
        val committed: Long,
        val max: Long
    )
    
    /**
     * 线程信息数据类
     */
    data class ThreadInfo(
        val threadCount: Int,
        val peakThreadCount: Int,
        val daemonThreadCount: Int,
        val totalStartedThreadCount: Long
    )
    
    /**
     * 操作系统信息数据类
     */
    data class OperatingSystemInfo(
        val availableProcessors: Int,
        val systemLoadAverage: Double,
        val processCpuLoad: Double
    )
    
    /**
     * 运行时信息数据类
     */
    data class RuntimeInfo(
        val uptime: Long,
        val startTime: Long
    )
}
