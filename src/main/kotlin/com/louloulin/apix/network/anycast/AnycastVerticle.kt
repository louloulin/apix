package com.louloulin.apix.network.anycast

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.BaseVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Anycast Verticle
 * 处理Anycast相关的操作，提供EventBus接口
 */
class AnycastVerticle : BaseVerticle() {
    // 使用BaseVerticle中的logger

    // Anycast管理器
    private lateinit var anycastManager: AnycastManager

    /**
     * 注册EventBus处理器
     */
    override fun registerEventBusHandlers() {
        // 获取Anycast状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ANYCAST_STATUS_GET) { message ->
            val status = anycastManager.getStatus()
            sendSuccess(message, status)
        }

        // 添加Anycast IP地址
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ANYCAST_IP_ADD) { message ->
            val ip = message.body().getString("ip", "")
            val cidr = message.body().getInteger("cidr", 32)
            val interface_ = message.body().getString("interface", "")

            if (ip.isEmpty()) {
                sendError(message, 400, "ip parameter is required")
                return@consumer
            }

            if (interface_.isEmpty()) {
                sendError(message, 400, "interface parameter is required")
                return@consumer
            }

            anycastManager.addAnycastIP(ip, cidr, interface_)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 删除Anycast IP地址
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ANYCAST_IP_REMOVE) { message ->
            val ip = message.body().getString("ip", "")

            if (ip.isEmpty()) {
                sendError(message, 400, "ip parameter is required")
                return@consumer
            }

            anycastManager.removeAnycastIP(ip)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 添加BGP会话
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ANYCAST_BGP_SESSION_ADD) { message ->
            val name = message.body().getString("name", "")
            val peerAddress = message.body().getString("peerAddress", "")
            val peerASN = message.body().getInteger("peerASN", 0)
            val localASN = message.body().getInteger("localASN", 0)

            if (name.isEmpty()) {
                sendError(message, 400, "name parameter is required")
                return@consumer
            }

            if (peerAddress.isEmpty()) {
                sendError(message, 400, "peerAddress parameter is required")
                return@consumer
            }

            if (peerASN == 0) {
                sendError(message, 400, "peerASN parameter is required")
                return@consumer
            }

            if (localASN == 0) {
                sendError(message, 400, "localASN parameter is required")
                return@consumer
            }

            anycastManager.addBGPSession(name, peerAddress, peerASN, localASN)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 删除BGP会话
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ANYCAST_BGP_SESSION_REMOVE) { message ->
            val name = message.body().getString("name", "")

            if (name.isEmpty()) {
                sendError(message, 400, "name parameter is required")
                return@consumer
            }

            anycastManager.removeBGPSession(name)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 更新Anycast配置
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ANYCAST_CONFIG_UPDATE) { message ->
            val config = message.body()

            anycastManager.updateConfig(config)
                .onSuccess {
                    sendSuccess(message, JsonObject().put("updated", true))
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
    }

    /**
     * 启动Verticle
     */
    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动Anycast Verticle")

        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // 获取Anycast配置
                    val anycastConfig = config.getJsonObject("anycast", JsonObject())

                    // 初始化Anycast管理器
                    anycastManager = AnycastManager.getInstance(vertx)

                    anycastManager.initialize(anycastConfig)
                        .onSuccess {
                            logger.info("Anycast管理器初始化成功")

                            // 注册Anycast组件状态
                            registerComponentStatus()

                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("Anycast管理器初始化失败", cause)
                            startPromise.fail(cause)
                        }
                } else {
                    val error = "获取配置失败: ${configResponse.getString("error", "未知错误")}"
                    logger.error(error)
                    startPromise.fail(error)
                }
            } else {
                logger.error("获取配置失败", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }

    /**
     * 注册Anycast组件状态
     */
    private fun registerComponentStatus() {
        val status = anycastManager.getStatus()
        val enabled = status.getBoolean("enabled", false)

        vertx.eventBus().send(EventBusAddresses.HEALTH_COMPONENT_STATUS, JsonObject()
            .put("component", "anycast")
            .put("status", enabled)
        )
    }

    /**
     * 停止Verticle
     */
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止Anycast Verticle")

        if (::anycastManager.isInitialized) {
            anycastManager.close()
                .onSuccess {
                    logger.info("Anycast管理器关闭成功")
                    stopPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("Anycast管理器关闭失败", cause)
                    stopPromise.fail(cause)
                }
        } else {
            stopPromise.complete()
        }
    }
}
