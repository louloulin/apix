package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.thread.ThreadModelOptimizer
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

/**
 * 线程模型优化器Verticle，负责管理线程模型优化器。
 */
class ThreadModelOptimizerVerticle : CoroutineVerticle() {
    private val logger = LoggerFactory.getLogger(ThreadModelOptimizerVerticle::class.java)
    private lateinit var threadModelOptimizer: ThreadModelOptimizer

    override suspend fun start() {
        logger.info("启动 ThreadModelOptimizerVerticle...")

        // 初始化线程模型优化器
        threadModelOptimizer = ThreadModelOptimizer.getInstance(vertx)

        // 获取配置
        val configMessage = vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_GET,
            JsonObject().put("section", "threadModel")
        ).await()

        val configResponse = configMessage.body()
        val config = if (configResponse.getBoolean("success", false)) {
            configResponse.getJsonObject("result", JsonObject())
        } else {
            JsonObject()
        }

        // 初始化线程模型优化器
        threadModelOptimizer.initialize(config)

        // 注册 EventBus 处理器
        registerEventBusHandlers()

        logger.info("ThreadModelOptimizerVerticle 启动完成")
    }

    override suspend fun stop() {
        logger.info("停止 ThreadModelOptimizerVerticle...")

        // 停止线程模型优化器
        threadModelOptimizer.stopMonitoring()

        logger.info("ThreadModelOptimizerVerticle 停止完成")
    }

    /**
     * 注册 EventBus 处理器
     */
    private fun registerEventBusHandlers() {
        // 获取线程模型统计信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.THREAD_MODEL_GET_STATS) { message ->
            threadModelOptimizer.getThreadModelStats()
                .onSuccess { stats ->
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("stats", stats)
                    )
                }
                .onFailure { err ->
                    logger.error("获取线程模型统计信息失败", err)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", err.message)
                    )
                }
        }

        // 启动线程模型监控
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.THREAD_MODEL_START_MONITORING) { message ->
            try {
                threadModelOptimizer.startMonitoring()
                message.reply(JsonObject()
                    .put("success", true)
                    .put("message", "线程模型监控已启动")
                )
            } catch (e: Exception) {
                logger.error("启动线程模型监控失败", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 停止线程模型监控
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.THREAD_MODEL_STOP_MONITORING) { message ->
            try {
                threadModelOptimizer.stopMonitoring()
                message.reply(JsonObject()
                    .put("success", true)
                    .put("message", "线程模型监控已停止")
                )
            } catch (e: Exception) {
                logger.error("停止线程模型监控失败", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }
    }
}
