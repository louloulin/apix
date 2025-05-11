package com.louloulin.apix.edge.sync

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.common.EventBusAddresses
import java.util.concurrent.atomic.AtomicLong

/**
 * 带宽感知同步策略，根据网络条件动态调整同步行为。
 */
class BandwidthAwareSyncStrategy(
    private val vertx: Vertx,
    private val config: JsonObject,
    private val bandwidthMonitor: BandwidthMonitor,
    private val networkDetector: NetworkConditionDetector
) : SyncStrategy {
    private val logger = LoggerFactory.getLogger(BandwidthAwareSyncStrategy::class.java)

    // 基础同步策略
    private val baseStrategy = IncrementalSyncStrategy(vertx, config)

    // 当前传输速率限制（字节/秒）
    private val currentRateLimit = AtomicLong(config.getLong("defaultRateLimit", 1024 * 1024)) // 默认1MB/s

    /**
     * 执行数据同步。
     *
     * @param dataType 数据类型
     * @param currentVersion 当前版本
     * @return Future<JsonObject> 同步结果
     */
    override fun sync(dataType: String, currentVersion: Long): Future<JsonObject> {
        logger.info("执行带宽感知同步: $dataType, 当前版本: $currentVersion")

        // 获取当前网络条件
        val networkCondition = networkDetector.getCurrentCondition()
        logger.info("当前网络条件: $networkCondition")

        // 获取当前带宽使用情况
        val bandwidthUsage = bandwidthMonitor.getCurrentBandwidthUsage()
        logger.info("当前带宽使用情况: $bandwidthUsage")

        // 根据网络条件和带宽使用情况调整同步策略
        adjustSyncStrategy(networkCondition, bandwidthUsage)

        // 如果网络不可用，返回错误
        if (networkCondition.status == NetworkStatus.UNAVAILABLE) {
            logger.warn("网络不可用，同步失败")
            return Future.failedFuture("Network unavailable")
        }

        // 如果带宽使用率过高，延迟同步
        if (bandwidthUsage.usageRatio > config.getDouble("maxBandwidthUsage", 0.8)) {
            logger.warn("带宽使用率过高 (${bandwidthUsage.usageRatio}), 延迟同步")

            val delayMs = calculateDelay(bandwidthUsage.usageRatio)
            logger.info("延迟 ${delayMs}ms 后重试同步")

            val promise = Promise.promise<JsonObject>()

            vertx.setTimer(delayMs) { _ ->
                baseStrategy.sync(dataType, currentVersion)
                    .onSuccess { result -> promise.complete(result) }
                    .onFailure { cause -> promise.fail(cause) }
            }

            return promise.future()
        }

        // 正常执行同步，但应用速率限制
        return executeRateLimitedSync(dataType, currentVersion)
    }

    /**
     * 执行速率限制的同步。
     *
     * @param dataType 数据类型
     * @param currentVersion 当前版本
     * @return Future<JsonObject> 同步结果
     */
    private fun executeRateLimitedSync(dataType: String, currentVersion: Long): Future<JsonObject> {
        // 设置当前速率限制
        val rateLimit = currentRateLimit.get()
        logger.info("应用速率限制: $rateLimit 字节/秒")

        // 构建请求
        val request = JsonObject()
            .put("dataType", dataType)
            .put("currentVersion", currentVersion)
            .put("rateLimit", rateLimit)

        // 发送请求到控制平面，使用速率限制的同步
        val promise = Promise.promise<JsonObject>()

        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONTROL_PLANE_RATE_LIMITED_SYNC, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getJsonObject("result", JsonObject()))
                } else {
                    // 如果失败，尝试使用基础策略
                    logger.warn("速率限制同步失败，尝试使用基础策略: ${response.getString("error")}")

                    baseStrategy.sync(dataType, currentVersion)
                        .onSuccess { result -> promise.complete(result) }
                        .onFailure { cause -> promise.fail(cause) }
                }
            } else {
                // 如果失败，尝试使用基础策略
                logger.warn("速率限制同步请求失败，尝试使用基础策略", ar.cause())

                baseStrategy.sync(dataType, currentVersion)
                    .onSuccess { result -> promise.complete(result) }
                    .onFailure { cause -> promise.fail(cause) }
            }
        }

        return promise.future()
    }

    /**
     * 根据网络条件和带宽使用情况调整同步策略。
     *
     * @param networkCondition 网络条件
     * @param bandwidthUsage 带宽使用情况
     */
    private fun adjustSyncStrategy(networkCondition: NetworkCondition, bandwidthUsage: BandwidthUsage) {
        // 基础速率限制
        val baseRateLimit = config.getLong("defaultRateLimit", 1024 * 1024) // 默认1MB/s

        // 根据网络状况调整速率限制
        val networkFactor = when (networkCondition.status) {
            NetworkStatus.GOOD -> 1.0
            NetworkStatus.FAIR -> 0.7
            NetworkStatus.POOR -> 0.3
            NetworkStatus.UNAVAILABLE -> 0.1
            NetworkStatus.UNKNOWN -> 0.5
        }

        // 根据带宽使用情况调整速率限制
        val bandwidthFactor = 1.0 - bandwidthUsage.usageRatio

        // 计算新的速率限制
        val newRateLimit = (baseRateLimit * networkFactor * bandwidthFactor).toLong()

        // 确保速率限制不低于最小值
        val minRateLimit = config.getLong("minRateLimit", 10 * 1024) // 默认10KB/s
        val finalRateLimit = Math.max(newRateLimit, minRateLimit)

        // 更新当前速率限制
        currentRateLimit.set(finalRateLimit)

        logger.info("调整速率限制: $finalRateLimit 字节/秒 (网络因子: $networkFactor, 带宽因子: $bandwidthFactor)")
    }

    /**
     * 计算延迟时间。
     *
     * @param bandwidthUsage 带宽使用率
     * @return 延迟时间（毫秒）
     */
    private fun calculateDelay(bandwidthUsage: Double): Long {
        // 基础延迟
        val baseDelay = config.getLong("baseDelay", 5000) // 默认5秒

        // 根据带宽使用率计算额外延迟
        val extraDelay = if (bandwidthUsage > 0.9) {
            config.getLong("highUsageDelay", 30000) // 高使用率时额外延迟30秒
        } else if (bandwidthUsage > 0.8) {
            config.getLong("mediumUsageDelay", 15000) // 中等使用率时额外延迟15秒
        } else {
            0L
        }

        return baseDelay + extraDelay
    }

    /**
     * 获取策略名称。
     *
     * @return 策略名称
     */
    override fun getName(): String {
        return "bandwidth-aware"
    }

    /**
     * 获取策略配置。
     *
     * @return 策略配置
     */
    override fun getConfig(): JsonObject {
        return config.copy()
    }
}
