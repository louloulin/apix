package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.ha.DataPlaneAutonomyManager
import com.louloulin.apix.ha.HighAvailabilityManager
import com.louloulin.apix.ha.MultiRegionManager
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 高可用性 Verticle，负责管理高可用性功能。
 */
class HighAvailabilityVerticle : BaseVerticle() {
    // 使用 BaseVerticle 中的 logger

    // 高可用性管理器
    private lateinit var haManager: HighAvailabilityManager

    // 数据平面自治管理器
    private lateinit var autonomyManager: DataPlaneAutonomyManager

    // 区域多活管理器
    private lateinit var multiRegionManager: MultiRegionManager

    override fun registerEventBusHandlers() {
        // 高可用性状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_STATUS_GET, this::handleGetHaStatus)

        // 控制平面探测
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_CONTROL_PLANE_PROBE, this::handleControlPlaneProbe)

        // 控制平面心跳
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_CONTROL_PLANE_HEARTBEAT, this::handleControlPlaneHeartbeat)

        // 数据平面自治状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_AUTONOMY_STATUS_GET, this::handleGetAutonomyStatus)

        // 区域管理
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_REGIONS_GET, this::handleGetRegions)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_CURRENT_REGION_GET, this::handleGetCurrentRegion)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_REGION_SYNC, this::handleRegionSync)
    }

    override fun onStart(startPromise: Promise<Void>) {
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // 初始化高可用性管理器
                    haManager = HighAvailabilityManager.getInstance(vertx)

                    // 初始化数据平面自治管理器
                    autonomyManager = DataPlaneAutonomyManager.getInstance(vertx)

                    // 初始化区域多活管理器
                    multiRegionManager = MultiRegionManager.getInstance(vertx)

                    // 初始化所有管理器
                    haManager.initialize(config)
                        .compose { _ -> autonomyManager.initialize(config) }
                        .compose { _ -> multiRegionManager.initialize(config) }
                        .onSuccess { _ ->
                            logger.info("HighAvailabilityVerticle 启动成功")
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("HighAvailabilityVerticle 启动失败", cause)
                            startPromise.fail(cause)
                        }
                } else {
                    val errorMsg = "获取配置失败: ${configResponse.getString("message", "未知错误")}"
                    logger.error(errorMsg)
                    startPromise.fail(errorMsg)
                }
            } else {
                logger.error("获取配置失败", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        // 停止所有管理器
        haManager.stop()
            .compose { _ -> autonomyManager.stop() }
            .compose { _ -> multiRegionManager.stop() }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    logger.info("HighAvailabilityVerticle 停止成功")
                    stopPromise.complete()
                } else {
                    logger.error("HighAvailabilityVerticle 停止失败", ar.cause())
                    stopPromise.fail(ar.cause())
                }
            }
    }

    /**
     * 处理获取高可用性状态请求。
     */
    private fun handleGetHaStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val status = JsonObject()
            .put("ha", haManager.getStatus())
            .put("autonomy", autonomyManager.getStatus())
            .put("multiRegion", multiRegionManager.getStatus())

        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }

    /**
     * 处理控制平面探测请求。
     */
    private fun handleControlPlaneProbe(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 返回控制平面状态
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("status", "HEALTHY")
                .put("timestamp", System.currentTimeMillis())
            )
        )
    }

    /**
     * 处理控制平面心跳请求。
     */
    private fun handleControlPlaneHeartbeat(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 控制平面心跳不需要回复
    }

    /**
     * 处理获取数据平面自治状态请求。
     */
    private fun handleGetAutonomyStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        message.reply(JsonObject()
            .put("success", true)
            .put("result", autonomyManager.getStatus())
        )
    }

    /**
     * 处理获取区域列表请求。
     */
    private fun handleGetRegions(message: io.vertx.core.eventbus.Message<JsonObject>) {
        message.reply(JsonObject()
            .put("success", true)
            .put("result", multiRegionManager.getStatus().getJsonArray("regions"))
        )
    }

    /**
     * 处理获取当前区域请求。
     */
    private fun handleGetCurrentRegion(message: io.vertx.core.eventbus.Message<JsonObject>) {
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("currentRegion", multiRegionManager.getStatus().getString("currentRegion"))
            )
        )
    }

    /**
     * 处理区域同步请求。
     */
    private fun handleRegionSync(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 转发到区域多活管理器
        vertx.eventBus().request<JsonObject>(EventBusAddresses.HA_REGION_SYNC, JsonObject()) { ar ->
            if (ar.succeeded()) {
                message.reply(ar.result().body())
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "区域同步失败: ${ar.cause().message}")
                )
            }
        }
    }
}
