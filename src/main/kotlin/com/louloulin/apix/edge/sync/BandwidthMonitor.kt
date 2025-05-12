package com.louloulin.apix.edge.sync

import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong

/**
 * 带宽监控器，用于监控网络带宽使用情况。
 */
class BandwidthMonitor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(BandwidthMonitor::class.java)

    // 带宽使用历史记录
    private val bandwidthHistory = ConcurrentLinkedQueue<BandwidthSample>()

    // 最大历史记录数量
    private val maxHistorySize = 100

    // 总发送字节数
    private val totalBytesSent = AtomicLong(0)

    // 总接收字节数
    private val totalBytesReceived = AtomicLong(0)

    // 当前带宽使用率
    private val currentBandwidthUsage = AtomicLong(0)

    // 最大带宽（字节/秒）
    private val maxBandwidth = AtomicLong(10 * 1024 * 1024) // 默认10MB/s

    // 定时器ID
    private var timerId: Long = -1

    /**
     * 启动带宽监控。
     */
    fun start() {
        logger.info("启动带宽监控")

        // 初始化带宽历史记录
        val now = System.currentTimeMillis()
        val totalBytes = totalBytesSent.get() + totalBytesReceived.get()
        bandwidthHistory.add(BandwidthSample(now, totalBytes))

        // 每秒更新一次带宽使用情况
        timerId = vertx.setPeriodic(1000) { _ ->
            updateBandwidthUsage()
        }
    }

    /**
     * 停止带宽监控。
     */
    fun stop() {
        logger.info("停止带宽监控")

        if (timerId != -1L) {
            vertx.cancelTimer(timerId)
            timerId = -1L
        }
    }

    /**
     * 记录发送的字节数。
     *
     * @param bytes 字节数
     */
    fun recordBytesSent(bytes: Long) {
        totalBytesSent.addAndGet(bytes)
    }

    /**
     * 记录接收的字节数。
     *
     * @param bytes 字节数
     */
    fun recordBytesReceived(bytes: Long) {
        totalBytesReceived.addAndGet(bytes)
    }

    /**
     * 更新带宽使用情况。
     */
    private fun updateBandwidthUsage() {
        val now = System.currentTimeMillis()
        val totalBytes = totalBytesSent.get() + totalBytesReceived.get()

        // 创建新的带宽样本
        val sample = BandwidthSample(now, totalBytes)

        // 添加到历史记录
        bandwidthHistory.add(sample)

        // 如果历史记录过多，移除最旧的
        while (bandwidthHistory.size > maxHistorySize) {
            bandwidthHistory.poll()
        }

        // 计算当前带宽使用率
        calculateCurrentBandwidthUsage()
    }

    /**
     * 计算当前带宽使用率。
     */
    private fun calculateCurrentBandwidthUsage() {
        if (bandwidthHistory.size < 2) {
            return
        }

        // 获取最新和最旧的样本
        val newest = bandwidthHistory.last()
        val oldest = bandwidthHistory.first()

        // 计算时间差（秒）
        val timeDiffSeconds = (newest.timestamp - oldest.timestamp) / 1000.0

        if (timeDiffSeconds <= 0) {
            return
        }

        // 计算字节差
        val bytesDiff = newest.totalBytes - oldest.totalBytes

        // 计算带宽（字节/秒）
        val bandwidthBytesPerSecond = (bytesDiff / timeDiffSeconds).toLong()

        // 更新当前带宽使用率
        currentBandwidthUsage.set(bandwidthBytesPerSecond)

        logger.debug("当前带宽使用: $bandwidthBytesPerSecond 字节/秒")
    }

    /**
     * 获取当前带宽使用情况。
     *
     * @return BandwidthUsage 带宽使用情况
     */
    fun getCurrentBandwidthUsage(): BandwidthUsage {
        val bytesPerSecond = currentBandwidthUsage.get()
        val maxBandwidthValue = maxBandwidth.get()
        val usageRatio = if (maxBandwidthValue > 0) bytesPerSecond.toDouble() / maxBandwidthValue else 0.0

        return BandwidthUsage(bytesPerSecond, maxBandwidthValue, usageRatio)
    }

    /**
     * 设置最大带宽。
     *
     * @param bytesPerSecond 最大带宽（字节/秒）
     */
    fun setMaxBandwidth(bytesPerSecond: Long) {
        maxBandwidth.set(bytesPerSecond)
    }

    /**
     * 获取带宽历史记录。
     *
     * @return List<BandwidthSample> 带宽历史记录
     */
    fun getBandwidthHistory(): List<BandwidthSample> {
        return bandwidthHistory.toList()
    }

    /**
     * 带宽样本类，表示一个带宽测量样本。
     *
     * @param timestamp 时间戳
     * @param totalBytes 总字节数
     */
    data class BandwidthSample(
        val timestamp: Long,
        val totalBytes: Long
    )
}

/**
 * 带宽使用情况类，表示当前带宽使用情况。
 *
 * @param bytesPerSecond 每秒字节数
 * @param maxBandwidth 最大带宽（字节/秒）
 * @param usageRatio 使用率（0-1）
 */
data class BandwidthUsage(
    val bytesPerSecond: Long,
    val maxBandwidth: Long,
    val usageRatio: Double
) : Comparable<BandwidthUsage> {
    /**
     * 比较带宽使用情况。
     *
     * @param other 其他带宽使用情况
     * @return 比较结果
     */
    override fun compareTo(other: BandwidthUsage): Int {
        return usageRatio.compareTo(other.usageRatio)
    }
}
