package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.async.AsyncProcessorEnhancer
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import org.slf4j.LoggerFactory

/**
 * 异步处理增强器Verticle，负责管理异步处理增强器。
 */
class AsyncProcessorVerticle : CoroutineVerticle() {
    private val logger = LoggerFactory.getLogger(AsyncProcessorVerticle::class.java)
    private lateinit var asyncProcessorEnhancer: AsyncProcessorEnhancer

    override suspend fun start() {
        logger.info("启动 AsyncProcessorVerticle...")

        // 初始化异步处理增强器
        asyncProcessorEnhancer = AsyncProcessorEnhancer.getInstance(vertx)

        // 获取配置
        val configMessage = vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_GET,
            JsonObject().put("section", "asyncProcessor")
        ).await()

        val configResponse = configMessage.body()
        val config = if (configResponse.getBoolean("success", false)) {
            configResponse.getJsonObject("result", JsonObject())
        } else {
            JsonObject()
        }

        // 初始化异步处理增强器
        asyncProcessorEnhancer.initialize(config)

        // 注册 EventBus 处理器
        registerEventBusHandlers()

        logger.info("AsyncProcessorVerticle 启动完成")
    }

    override suspend fun stop() {
        logger.info("停止 AsyncProcessorVerticle...")
        logger.info("AsyncProcessorVerticle 停止完成")
    }

    /**
     * 注册 EventBus 处理器
     */
    private fun registerEventBusHandlers() {
        // 获取统计信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ASYNC_GET_STATS) { message ->
            val stats = asyncProcessorEnhancer.getStats()
            message.reply(JsonObject()
                .put("success", true)
                .put("stats", stats)
            )
        }

        // 重置统计信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ASYNC_RESET_STATS) { message ->
            asyncProcessorEnhancer.resetStats()
            message.reply(JsonObject()
                .put("success", true)
                .put("message", "统计信息已重置")
            )
        }
    }
}
