package com.louloulin.apix.core.memory

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryPoolMXBean
import java.lang.management.MemoryType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 内存管理器，负责监控和优化系统内存使用
 */
class MemoryManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(MemoryManager::class.java)

    // 内存监控相关
    private val memoryMXBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
    private val memoryPoolMXBeans: List<MemoryPoolMXBean> = ManagementFactory.getMemoryPoolMXBeans()

    // 内存使用统计
    private val heapUsage = AtomicLong(0)
    private val nonHeapUsage = AtomicLong(0)
    private val maxHeapMemory = AtomicLong(0)
    private val committedHeapMemory = AtomicLong(0)

    // 内存优化配置
    private var lowMemoryThreshold = 0.8 // 低内存阈值（使用率）
    private var criticalMemoryThreshold = 0.95 // 严重内存不足阈值（使用率）
    private var gcThreshold = 0.7 // 触发 GC 的阈值（使用率）
    private var monitorInterval = 5000L // 监控间隔（毫秒）

    // 内存优化状态
    private val isLowMemory = AtomicBoolean(false)
    private val isCriticalMemory = AtomicBoolean(false)
    private val isOptimizing = AtomicBoolean(false)
    private val lastGCTime = AtomicLong(0)
    private val gcCount = AtomicLong(0)

    // 内存优化回调
    private val lowMemoryHandlers = mutableListOf<() -> Unit>()
    private val criticalMemoryHandlers = mutableListOf<() -> Unit>()
    private val memoryRestoredHandlers = mutableListOf<() -> Unit>()

    // 对象池管理
    private val objectPools = ConcurrentHashMap<String, ObjectPool<*>>()

    /**
     * 初始化内存管理器
     */
    fun initialize(config: JsonObject) {
        // 加载配置
        lowMemoryThreshold = config.getDouble("lowMemoryThreshold", lowMemoryThreshold)
        criticalMemoryThreshold = config.getDouble("criticalMemoryThreshold", criticalMemoryThreshold)
        gcThreshold = config.getDouble("gcThreshold", gcThreshold)
        monitorInterval = config.getLong("monitorInterval", monitorInterval)

        // 启动内存监控
        startMemoryMonitoring()

        logger.info("内存管理器初始化完成，低内存阈值: ${lowMemoryThreshold * 100}%，严重内存不足阈值: ${criticalMemoryThreshold * 100}%")
    }

    /**
     * 启动内存监控
     */
    private fun startMemoryMonitoring() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    updateMemoryStats()
                    checkMemoryStatus()
                    kotlinx.coroutines.delay(monitorInterval)
                } catch (e: Exception) {
                    logger.error("内存监控异常", e)
                    kotlinx.coroutines.delay(monitorInterval)
                }
            }
        }
    }

    /**
     * 更新内存统计信息
     */
    private fun updateMemoryStats() {
        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage

        heapUsage.set(heapMemoryUsage.used)
        nonHeapUsage.set(nonHeapMemoryUsage.used)
        maxHeapMemory.set(heapMemoryUsage.max)
        committedHeapMemory.set(heapMemoryUsage.committed)

        logger.debug("内存使用情况 - 堆内存: ${formatSize(heapUsage.get())}/${formatSize(maxHeapMemory.get())}, " +
                "非堆内存: ${formatSize(nonHeapUsage.get())}")
    }

    /**
     * 检查内存状态
     */
    private fun checkMemoryStatus() {
        val heapUsageRatio = if (maxHeapMemory.get() > 0) heapUsage.get().toDouble() / maxHeapMemory.get() else 0.0

        // 检查是否需要触发 GC
        if (heapUsageRatio > gcThreshold && System.currentTimeMillis() - lastGCTime.get() > 60000) {
            triggerGC()
        }

        // 检查是否处于低内存状态
        val wasLowMemory = isLowMemory.get()
        val wasCriticalMemory = isCriticalMemory.get()

        if (heapUsageRatio > criticalMemoryThreshold) {
            // 严重内存不足
            if (!wasCriticalMemory) {
                isCriticalMemory.set(true)
                isLowMemory.set(true)
                logger.warn("严重内存不足！堆内存使用率: ${String.format("%.2f", heapUsageRatio * 100)}%")
                notifyCriticalMemory()
            }
        } else if (heapUsageRatio > lowMemoryThreshold) {
            // 低内存
            if (!wasLowMemory) {
                isLowMemory.set(true)
                isCriticalMemory.set(false)
                logger.warn("低内存警告！堆内存使用率: ${String.format("%.2f", heapUsageRatio * 100)}%")
                notifyLowMemory()
            } else if (wasCriticalMemory) {
                // 从严重内存不足恢复到低内存
                isCriticalMemory.set(false)
            }
        } else {
            // 内存正常
            if (wasLowMemory || wasCriticalMemory) {
                isLowMemory.set(false)
                isCriticalMemory.set(false)
                logger.info("内存已恢复正常，堆内存使用率: ${String.format("%.2f", heapUsageRatio * 100)}%")
                notifyMemoryRestored()
            }
        }
    }

    /**
     * 触发垃圾回收
     */
    fun triggerGC() {
        if (isOptimizing.compareAndSet(false, true)) {
            try {
                logger.info("触发垃圾回收...")
                System.gc()
                lastGCTime.set(System.currentTimeMillis())
                gcCount.incrementAndGet()
                logger.info("垃圾回收完成")
            } finally {
                isOptimizing.set(false)
            }
        }
    }

    /**
     * 清理内存缓存
     */
    fun clearCaches() {
        if (isOptimizing.compareAndSet(false, true)) {
            try {
                logger.info("清理内存缓存...")

                // 清理对象池
                for (pool in objectPools.values) {
                    pool.clear()
                }

                // 触发垃圾回收
                System.gc()
                lastGCTime.set(System.currentTimeMillis())
                logger.info("内存缓存清理完成")
            } finally {
                isOptimizing.set(false)
            }
        }
    }

    /**
     * 获取内存使用情况
     */
    fun getMemoryUsage(): JsonObject {
        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage

        val heapUsageRatio = if (heapMemoryUsage.max > 0) heapMemoryUsage.used.toDouble() / heapMemoryUsage.max else 0.0
        val committedUsageRatio = if (heapMemoryUsage.committed > 0) heapMemoryUsage.used.toDouble() / heapMemoryUsage.committed else 0.0

        val memoryPools = JsonObject()
        for (pool in memoryPoolMXBeans) {
            val usage = pool.usage
            val peakUsage = pool.peakUsage
            val poolInfo = JsonObject()
                .put("name", pool.name)
                .put("type", pool.type.toString())
                .put("used", usage.used)
                .put("max", usage.max)
                .put("committed", usage.committed)
                .put("init", usage.init)
                .put("peakUsed", peakUsage.used)
                .put("usageRatio", if (usage.max > 0) usage.used.toDouble() / usage.max else 0.0)

            memoryPools.put(pool.name, poolInfo)
        }

        // 对象池统计
        val poolStats = JsonObject()
        for ((name, pool) in objectPools) {
            val stats = pool.getStats()
            poolStats.put(name, stats)
        }

        return JsonObject()
            .put("heap", JsonObject()
                .put("used", heapMemoryUsage.used)
                .put("max", heapMemoryUsage.max)
                .put("committed", heapMemoryUsage.committed)
                .put("init", heapMemoryUsage.init)
                .put("usageRatio", heapUsageRatio)
                .put("committedUsageRatio", committedUsageRatio)
            )
            .put("nonHeap", JsonObject()
                .put("used", nonHeapMemoryUsage.used)
                .put("max", nonHeapMemoryUsage.max)
                .put("committed", nonHeapMemoryUsage.committed)
                .put("init", nonHeapMemoryUsage.init)
            )
            .put("memoryPools", memoryPools)
            .put("objectPools", poolStats)
            .put("isLowMemory", isLowMemory.get())
            .put("isCriticalMemory", isCriticalMemory.get())
            .put("gcCount", gcCount.get())
            .put("lastGCTime", lastGCTime.get())
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 添加低内存处理器
     */
    fun addLowMemoryHandler(handler: () -> Unit) {
        lowMemoryHandlers.add(handler)
    }

    /**
     * 添加严重内存不足处理器
     */
    fun addCriticalMemoryHandler(handler: () -> Unit) {
        criticalMemoryHandlers.add(handler)
    }

    /**
     * 添加内存恢复处理器
     */
    fun addMemoryRestoredHandler(handler: () -> Unit) {
        memoryRestoredHandlers.add(handler)
    }

    /**
     * 通知低内存状态
     */
    private fun notifyLowMemory() {
        for (handler in lowMemoryHandlers) {
            try {
                handler()
            } catch (e: Exception) {
                logger.error("执行低内存处理器异常", e)
            }
        }
    }

    /**
     * 通知严重内存不足状态
     */
    private fun notifyCriticalMemory() {
        for (handler in criticalMemoryHandlers) {
            try {
                handler()
            } catch (e: Exception) {
                logger.error("执行严重内存不足处理器异常", e)
            }
        }
    }

    /**
     * 通知内存恢复状态
     */
    private fun notifyMemoryRestored() {
        for (handler in memoryRestoredHandlers) {
            try {
                handler()
            } catch (e: Exception) {
                logger.error("执行内存恢复处理器异常", e)
            }
        }
    }

    /**
     * 格式化内存大小
     */
    private fun formatSize(size: Long): String {
        val kb = 1024L
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            size >= gb -> String.format("%.2f GB", size.toDouble() / gb)
            size >= mb -> String.format("%.2f MB", size.toDouble() / mb)
            size >= kb -> String.format("%.2f KB", size.toDouble() / kb)
            else -> "$size bytes"
        }
    }

    /**
     * 创建或获取对象池
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getOrCreateObjectPool(name: String, factory: () -> T, initialSize: Int = 10, maxSize: Int = 100): ObjectPool<T> {
        return objectPools.computeIfAbsent(name) {
            ObjectPool(name, factory, initialSize, maxSize)
        } as ObjectPool<T>
    }

    /**
     * 移除对象池
     */
    fun removeObjectPool(name: String) {
        objectPools.remove(name)?.clear()
    }
}
