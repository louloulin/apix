package com.louloulin.apix.metrics

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.OperatingSystemMXBean
import java.lang.management.ThreadMXBean

/**
 * 收集系统指标的工具类
 */
class MetricsCollector(private val vertx: Vertx) {
    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
    private val threadMXBean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val osMXBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
    
    /**
     * 获取 CPU 指标
     */
    fun getCpuMetrics(): JsonObject {
        val cpuMetrics = JsonObject()
        
        // 获取 CPU 核心数
        cpuMetrics.put("cores", Runtime.getRuntime().availableProcessors())
        
        // 获取系统负载
        if (osMXBean is com.sun.management.OperatingSystemMXBean) {
            val sunOsMXBean = osMXBean as com.sun.management.OperatingSystemMXBean
            cpuMetrics.put("systemLoadAverage", osMXBean.systemLoadAverage)
            cpuMetrics.put("processCpuLoad", sunOsMXBean.processCpuLoad)
            cpuMetrics.put("systemCpuLoad", sunOsMXBean.systemCpuLoad)
        } else {
            cpuMetrics.put("systemLoadAverage", osMXBean.systemLoadAverage)
        }
        
        return cpuMetrics
    }
    
    /**
     * 获取内存指标
     */
    fun getMemoryMetrics(): JsonObject {
        val memoryMetrics = JsonObject()
        
        // 获取堆内存使用情况
        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        memoryMetrics.put("heap", JsonObject()
            .put("init", heapMemoryUsage.init)
            .put("used", heapMemoryUsage.used)
            .put("committed", heapMemoryUsage.committed)
            .put("max", heapMemoryUsage.max)
            .put("usage", heapMemoryUsage.used.toDouble() / heapMemoryUsage.max)
        )
        
        // 获取非堆内存使用情况
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage
        memoryMetrics.put("nonHeap", JsonObject()
            .put("init", nonHeapMemoryUsage.init)
            .put("used", nonHeapMemoryUsage.used)
            .put("committed", nonHeapMemoryUsage.committed)
            .put("max", nonHeapMemoryUsage.max)
        )
        
        // 获取系统内存使用情况
        if (osMXBean is com.sun.management.OperatingSystemMXBean) {
            val sunOsMXBean = osMXBean as com.sun.management.OperatingSystemMXBean
            memoryMetrics.put("system", JsonObject()
                .put("totalPhysicalMemory", sunOsMXBean.totalPhysicalMemorySize)
                .put("freePhysicalMemory", sunOsMXBean.freePhysicalMemorySize)
                .put("totalSwapSpace", sunOsMXBean.totalSwapSpaceSize)
                .put("freeSwapSpace", sunOsMXBean.freeSwapSpaceSize)
            )
        }
        
        return memoryMetrics
    }
    
    /**
     * 获取线程指标
     */
    fun getThreadMetrics(): JsonObject {
        val threadMetrics = JsonObject()
        
        // 获取线程数量
        threadMetrics.put("threadCount", threadMXBean.threadCount)
        threadMetrics.put("daemonThreadCount", threadMXBean.daemonThreadCount)
        threadMetrics.put("peakThreadCount", threadMXBean.peakThreadCount)
        threadMetrics.put("totalStartedThreadCount", threadMXBean.totalStartedThreadCount)
        
        return threadMetrics
    }
    
    /**
     * 获取 JVM 指标
     */
    fun getJvmMetrics(): JsonObject {
        val jvmMetrics = JsonObject()
        
        // 获取 JVM 运行时间
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        jvmMetrics.put("uptime", runtimeMXBean.uptime)
        jvmMetrics.put("startTime", runtimeMXBean.startTime)
        
        // 获取 JVM 版本信息
        jvmMetrics.put("vmName", runtimeMXBean.vmName)
        jvmMetrics.put("vmVendor", runtimeMXBean.vmVendor)
        jvmMetrics.put("vmVersion", runtimeMXBean.vmVersion)
        
        return jvmMetrics
    }
    
    /**
     * 获取操作系统指标
     */
    fun getOsMetrics(): JsonObject {
        val osMetrics = JsonObject()
        
        // 获取操作系统信息
        osMetrics.put("name", osMXBean.name)
        osMetrics.put("arch", osMXBean.arch)
        osMetrics.put("version", osMXBean.version)
        osMetrics.put("availableProcessors", osMXBean.availableProcessors)
        
        return osMetrics
    }
    
    /**
     * 获取所有指标
     */
    fun getAllMetrics(): JsonObject {
        val allMetrics = JsonObject()
        
        allMetrics.put("cpu", getCpuMetrics())
        allMetrics.put("memory", getMemoryMetrics())
        allMetrics.put("thread", getThreadMetrics())
        allMetrics.put("jvm", getJvmMetrics())
        allMetrics.put("os", getOsMetrics())
        
        return allMetrics
    }
}
