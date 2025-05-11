package com.louloulin.apix.edge.sync

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference

/**
 * 网络条件检测器，用于检测当前网络状况。
 */
class NetworkConditionDetector(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(NetworkConditionDetector::class.java)

    // HTTP客户端
    private val httpClient: HttpClient

    // 探测目标列表
    private val probeTargets = listOf(
        "https://www.baidu.com",
        "https://www.qq.com",
        "https://www.aliyun.com"
    )

    // 当前网络状况
    private val currentCondition = AtomicReference<NetworkCondition>(NetworkCondition(NetworkStatus.UNKNOWN, 0, 0.0))

    // 延迟历史记录
    private val latencyHistory = ConcurrentLinkedQueue<LatencySample>()

    // 最大历史记录数量
    private val maxHistorySize = 100

    // 定时器ID
    private var timerId: Long = -1

    init {
        // 创建HTTP客户端
        val options = HttpClientOptions()
            .setConnectTimeout(5000)
            .setIdleTimeout(10)

        httpClient = vertx.createHttpClient(options)
    }

    /**
     * 启动网络条件检测。
     */
    fun start() {
        logger.info("启动网络条件检测")

        // 每10秒检测一次网络状况
        timerId = vertx.setPeriodic(10000) { _ ->
            detectNetworkCondition()
        }

        // 立即执行一次检测
        detectNetworkCondition()
    }

    /**
     * 停止网络条件检测。
     */
    fun stop() {
        logger.info("停止网络条件检测")

        if (timerId != -1L) {
            vertx.cancelTimer(timerId)
            timerId = -1L
        }
    }

    /**
     * 检测网络状况。
     */
    private fun detectNetworkCondition() {
        logger.debug("检测网络状况")

        // 随机选择一个探测目标
        val target = probeTargets.random()

        // 记录开始时间
        val startTime = System.currentTimeMillis()

        // 发送HTTP请求
        try {
            httpClient.request(io.vertx.core.http.HttpMethod.GET, target).onComplete { ar ->
                val endTime = System.currentTimeMillis()
                val latency = endTime - startTime

                if (ar.succeeded()) {
                    // 请求成功
                    val request = ar.result()

                    // 发送请求
                    request.send().onComplete { responseAr ->
                        if (responseAr.succeeded()) {
                            // 记录延迟
                            recordLatency(latency)

                            // 根据延迟判断网络状况
                            val status = when {
                                latency < 100 -> NetworkStatus.GOOD
                                latency < 300 -> NetworkStatus.FAIR
                                else -> NetworkStatus.POOR
                            }

                            // 计算丢包率（这里简化为0，实际应用中可能需要更复杂的计算）
                            val packetLoss = 0.0

                            // 更新当前网络状况
                            updateNetworkCondition(NetworkCondition(status, latency, packetLoss))

                            logger.debug("网络状况: $status, 延迟: ${latency}ms")
                        } else {
                            // 响应失败
                            logger.warn("网络探测失败: ${responseAr.cause().message}")

                            // 更新为不可用状态
                            updateNetworkCondition(NetworkCondition(NetworkStatus.UNAVAILABLE, latency, 1.0))
                        }
                    }
                } else {
                    // 请求创建失败
                    logger.warn("网络探测失败: ${ar.cause().message}")

                    // 更新为不可用状态
                    updateNetworkCondition(NetworkCondition(NetworkStatus.UNAVAILABLE, latency, 1.0))
                }
            }
        } catch (e: Exception) {
            // 异常处理
            logger.error("网络探测异常", e)

            // 更新为不可用状态
            updateNetworkCondition(NetworkCondition(NetworkStatus.UNAVAILABLE, 0, 1.0))
        }
    }

    /**
     * 记录延迟。
     *
     * @param latency 延迟（毫秒）
     */
    private fun recordLatency(latency: Long) {
        val now = System.currentTimeMillis()

        // 创建新的延迟样本
        val sample = LatencySample(now, latency)

        // 添加到历史记录
        latencyHistory.add(sample)

        // 如果历史记录过多，移除最旧的
        while (latencyHistory.size > maxHistorySize) {
            latencyHistory.poll()
        }
    }

    /**
     * 更新当前网络状况。
     *
     * @param condition 网络状况
     */
    private fun updateNetworkCondition(condition: NetworkCondition) {
        currentCondition.set(condition)
    }

    /**
     * 获取当前网络状况。
     *
     * @return NetworkCondition 当前网络状况
     */
    fun getCurrentCondition(): NetworkCondition {
        return currentCondition.get()
    }

    /**
     * 获取平均延迟。
     *
     * @return Long 平均延迟（毫秒）
     */
    fun getAverageLatency(): Long {
        if (latencyHistory.isEmpty()) {
            return 0
        }

        val sum = latencyHistory.sumOf { it.latency }
        return sum / latencyHistory.size
    }

    /**
     * 获取延迟历史记录。
     *
     * @return List<LatencySample> 延迟历史记录
     */
    fun getLatencyHistory(): List<LatencySample> {
        return latencyHistory.toList()
    }

    /**
     * 延迟样本类，表示一个延迟测量样本。
     *
     * @param timestamp 时间戳
     * @param latency 延迟（毫秒）
     */
    data class LatencySample(
        val timestamp: Long,
        val latency: Long
    )
}

/**
 * 网络状态枚举，表示网络状况。
 */
enum class NetworkStatus {
    GOOD,       // 良好
    FAIR,       // 一般
    POOR,       // 较差
    UNAVAILABLE,// 不可用
    UNKNOWN     // 未知
}

/**
 * 网络条件类，表示当前网络状况。
 *
 * @param status 网络状态
 * @param latency 延迟（毫秒）
 * @param packetLoss 丢包率（0-1）
 */
data class NetworkCondition(
    val status: NetworkStatus,
    val latency: Long,
    val packetLoss: Double
)
