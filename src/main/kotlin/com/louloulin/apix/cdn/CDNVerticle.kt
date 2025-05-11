package com.louloulin.apix.cdn

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.BaseVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * CDN Verticle
 * 处理CDN相关的操作，提供EventBus接口
 */
class CDNVerticle : BaseVerticle() {
    // 使用BaseVerticle中的logger

    // CDN管理器
    private lateinit var cdnManager: CDNManager

    /**
     * 注册EventBus处理器
     */
    override fun registerEventBusHandlers() {
        // 获取CDN状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CDN_STATUS_GET) { message ->
            val status = cdnManager.getStatus()
            sendSuccess(message, status)
        }

        // 刷新CDN缓存
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CDN_CACHE_PURGE) { message ->
            val urls = message.body().getJsonArray("urls", JsonArray()).map { it.toString() }

            if (urls.isEmpty()) {
                sendError(message, 400, "URLs parameter is required")
                return@consumer
            }

            cdnManager.purgeCache(urls)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 预热CDN缓存
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CDN_CACHE_PREWARM) { message ->
            val urls = message.body().getJsonArray("urls", JsonArray()).map { it.toString() }

            if (urls.isEmpty()) {
                sendError(message, 400, "URLs parameter is required")
                return@consumer
            }

            cdnManager.prewarmCache(urls)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 更新CDN配置
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CDN_CONFIG_UPDATE) { message ->
            val config = message.body()

            cdnManager.updateConfig(config)
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
        logger.info("启动CDN Verticle")

        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // 获取CDN配置
                    val cdnConfig = config.getJsonObject("cdn", JsonObject())

                    // 初始化CDN管理器
                    cdnManager = CDNManager.getInstance(vertx)

                    cdnManager.initialize(cdnConfig)
                        .onSuccess {
                            logger.info("CDN管理器初始化成功")

                            // 注册CDN组件状态
                            registerComponentStatus()

                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("CDN管理器初始化失败", cause)
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
     * 注册CDN组件状态
     */
    private fun registerComponentStatus() {
        val status = cdnManager.getStatus()
        val enabled = status.getBoolean("enabled", false)

        vertx.eventBus().send(EventBusAddresses.HEALTH_COMPONENT_STATUS, JsonObject()
            .put("component", "cdn")
            .put("status", enabled)
        )
    }

    /**
     * 停止Verticle
     */
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止CDN Verticle")

        if (::cdnManager.isInitialized) {
            cdnManager.close()
                .onSuccess {
                    logger.info("CDN管理器关闭成功")
                    stopPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("CDN管理器关闭失败", cause)
                    stopPromise.fail(cause)
                }
        } else {
            stopPromise.complete()
        }
    }
}
