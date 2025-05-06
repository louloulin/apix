package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.concurrency.ConcurrencyController
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * 并发控制 Verticle，负责管理系统的并发处理能力
 */
class ConcurrencyControlVerticle : CoroutineVerticle() {
    private val logger = LoggerFactory.getLogger(ConcurrencyControlVerticle::class.java)
    private lateinit var concurrencyController: ConcurrencyController

    override suspend fun start() {
        logger.info("启动 ConcurrencyControlVerticle...")

        // 初始化并发控制器
        concurrencyController = ConcurrencyController(vertx)

        // 获取配置
        val configMessage = vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_GET,
            JsonObject().put("section", "concurrency")
        ).await()

        val configResponse = configMessage.body()
        val config = if (configResponse.getBoolean("success", false)) {
            configResponse.getJsonObject("result", JsonObject())
        } else {
            JsonObject()
        }

        // 初始化并发控制器
        concurrencyController.init(config)

        // 注册 EventBus 处理器
        registerEventBusHandlers()

        logger.info("ConcurrencyControlVerticle 启动完成")
    }

    /**
     * 注册 EventBus 处理器
     */
    private fun registerEventBusHandlers() {
        // 尝试获取并发许可
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONCURRENCY_TRY_ACQUIRE) { message ->
            launch {
                try {
                    val request = message.body()
                    val serviceId = request.getString("serviceId", "default")

                    val acquired = concurrencyController.tryAcquire(serviceId)

                    val response = JsonObject()
                        .put("success", true)
                        .put("acquired", acquired)
                        .put("serviceId", serviceId)
                        .put("activeRequests", concurrencyController.getActiveCount(serviceId))
                        .put("limit", concurrencyController.getCurrentLimit(serviceId))
                        .put("concurrencyLimit", concurrencyController.getCurrentLimit(serviceId))

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("处理并发获取请求失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 释放并发许可
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONCURRENCY_RELEASE) { message ->
            launch {
                try {
                    val request = message.body()
                    val serviceId = request.getString("serviceId", "default")
                    val responseTime = request.getLong("responseTime", 0)
                    val isError = request.getBoolean("isError", false)

                    concurrencyController.release(serviceId, !isError)
                    concurrencyController.recordRequestCompletion(serviceId, responseTime, !isError)

                    val response = JsonObject()
                        .put("success", true)
                        .put("serviceId", serviceId)
                        .put("activeRequests", concurrencyController.getActiveCount(serviceId))

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("处理并发释放请求失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 获取服务指标
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONCURRENCY_GET_METRICS) { message ->
            launch {
                try {
                    val request = message.body()
                    val serviceId = request.getString("serviceId")

                    val metrics = if (serviceId != null) {
                        concurrencyController.getServiceMetrics(serviceId)
                    } else {
                        concurrencyController.getAllServiceMetrics()
                    }

                    val response = JsonObject()
                        .put("success", true)
                        .put("metrics", metrics)

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("获取服务指标失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 设置并发限制
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONCURRENCY_SET_LIMIT) { message ->
            launch {
                try {
                    val request = message.body()
                    val serviceId = request.getString("serviceId")
                    val limit = request.getInteger("limit")

                    if (serviceId == null || limit == null) {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", "Missing required parameters: serviceId and limit")
                        )
                        return@launch
                    }

                    concurrencyController.setCurrentLimit(serviceId, limit)

                    val response = JsonObject()
                        .put("success", true)
                        .put("serviceId", serviceId)
                        .put("limit", concurrencyController.getCurrentLimit(serviceId))

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("设置并发限制失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 重置服务指标
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONCURRENCY_RESET_METRICS) { message ->
            launch {
                try {
                    val request = message.body()
                    val serviceId = request.getString("serviceId")

                    if (serviceId != null) {
                        concurrencyController.resetServiceMetrics(serviceId)
                    } else {
                        concurrencyController.resetAllMetrics()
                    }

                    val response = JsonObject()
                        .put("success", true)

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("重置服务指标失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }
    }

    override suspend fun stop() {
        logger.info("停止 ConcurrencyControlVerticle...")
    }
}
