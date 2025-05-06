package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.eventbus.EventBusManager
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * EventBus管理Verticle，用于管理EventBus类型切换和监控。
 * 提供了EventBus类型切换、统计信息查询等功能。
 */
class EventBusManagerVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(EventBusManagerVerticle::class.java)

    // EventBus管理器
    private lateinit var eventBusManager: EventBusManager

    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting EventBusManagerVerticle")

        // 初始EventBus管理器
        eventBusManager = EventBusManager.getInstance(vertx)

        // 注册EventBus类型切换处理器
        vertx.eventBus().consumer<JsonObject>("eventbus.switch") { message ->
            val body = message.body()
            val typeStr = body.getString("type")

            try {
                val type = EventBusManager.EventBusType.valueOf(typeStr.uppercase())

                eventBusManager.switchType(type)
                    .onComplete { ar ->
                        if (ar.succeeded()) {
                            val success = ar.result()
                            message.reply(JsonObject()
                                .put("success", success)
                                .put("type", type.name)
                            )

                            logger.info("EventBus类型切换: {} (success={})", type, success)
                        } else {
                            logger.error("EventBus类型切换失败: {}", typeStr, ar.cause())
                            message.reply(JsonObject()
                                .put("success", false)
                                .put("error", ar.cause().message)
                            )
                        }
                    }
            } catch (e: Exception) {
                logger.error("EventBus类型切换失败: {}", typeStr, e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Invalid EventBus type: $typeStr")
                )
            }
        }

        // 注册EventBus统计信息查询处理器
        vertx.eventBus().consumer<JsonObject>("eventbus.stats") { message ->
            val stats = eventBusManager.getStats()
            message.reply(stats)
        }

        // 设置定期统计信息记录
        vertx.setPeriodic(60000) { // 每分钟记录一次
            val currentType = eventBusManager.getCurrentType()
            val stats = eventBusManager.getStats()

            logger.info("EventBus统计信息: type={}, stats={}", currentType, stats.encode())
        }

        startPromise.complete()
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping EventBusManagerVerticle")
        stopPromise.complete()
    }
}
