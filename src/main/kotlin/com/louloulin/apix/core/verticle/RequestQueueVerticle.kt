package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.queue.RequestQueueManager
import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 请求队列 Verticle，负责管理请求队列和优先级处理
 */
class RequestQueueVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(RequestQueueVerticle::class.java)
    private lateinit var queueManager: RequestQueueManager

    override fun start(startPromise: Promise<Void>) {
        logger.info("启动 RequestQueueVerticle")

        try {
            // 创建请求队列管理器
            queueManager = RequestQueueManager(vertx)

            // 初始化请求队列管理器
            val config = config().getJsonObject("queue", JsonObject())
            queueManager.initialize(config)

            // 注册事件总线处理器
            registerEventBusHandlers()

            logger.info("RequestQueueVerticle 启动成功")
            startPromise.complete()
        } catch (e: Exception) {
            logger.error("RequestQueueVerticle 启动失败", e)
            startPromise.fail(e)
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("停止 RequestQueueVerticle")

        try {
            // 停止请求队列管理器
            queueManager.stop()

            logger.info("RequestQueueVerticle 停止成功")
            stopPromise.complete()
        } catch (e: Exception) {
            logger.error("RequestQueueVerticle 停止失败", e)
            stopPromise.fail(e)
        }
    }

    /**
     * 注册事件总线处理器
     */
    private fun registerEventBusHandlers() {
        // 注册请求入队处理器
        vertx.eventBus().consumer<JsonObject>("apix.queue.enqueue") { message ->
            val body = message.body()
            val serviceId = body.getString("serviceId")
            val request = body.getJsonObject("request")
            val priority = body.getInteger("priority", 5)
            val timeout = body.getLong("timeout", 30000L)

            if (serviceId == null || request == null) {
                message.fail(400, "Missing required fields: serviceId, request")
                return@consumer
            }

            // 入队请求
            queueManager.enqueue(
                serviceId = serviceId,
                request = request,
                priority = priority,
                timeout = timeout
            ) { req ->
                // 处理请求
                val requestHandler = body.getString("handler")
                if (requestHandler != null) {
                    // 发送请求到指定处理器
                    val future = vertx.eventBus().request<Any>(requestHandler, req)
                    return@enqueue Future.future { promise ->
                        future.onComplete { ar ->
                            if (ar.succeeded()) {
                                promise.complete(ar.result().body())
                            } else {
                                promise.fail(ar.cause())
                            }
                        }
                    }
                } else {
                    // 默认处理逻辑
                    val future = vertx.eventBus().request<Any>("apix.request.process", req)
                    return@enqueue Future.future { promise ->
                        future.onComplete { ar ->
                            if (ar.succeeded()) {
                                promise.complete(ar.result().body())
                            } else {
                                promise.fail(ar.cause())
                            }
                        }
                    }
                }
            }.onComplete { ar ->
                if (ar.succeeded()) {
                    message.reply(ar.result())
                } else {
                    message.fail(500, ar.cause().message)
                }
            }
        }

        // 注册队列状态查询处理器
        vertx.eventBus().consumer<JsonObject>("apix.queue.status") { message ->
            val serviceId = message.body().getString("serviceId")

            val stats = if (serviceId != null) {
                queueManager.getQueueStats(serviceId)
            } else {
                queueManager.getAllQueueStats()
            }

            message.reply(stats)
        }
    }
}
