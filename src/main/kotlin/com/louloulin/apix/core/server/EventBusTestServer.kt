package com.louloulin.apix.core.server

import com.louloulin.apix.core.eventbus.EventBusManager
import com.louloulin.apix.core.eventbus.JCToolsEventBus
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * EventBus测试服务器，用于测试EventBus的性能
 */
class EventBusTestServer : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(EventBusTestServer::class.java)
    
    // 请求计数器
    private val requestCounter = AtomicInteger(0)
    
    // EventBus管理器
    private lateinit var eventBusManager: EventBusManager
    
    // JCToolsEventBus
    private lateinit var jcToolsEventBus: JCToolsEventBus
    
    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting EventBusTestServer")
        
        // 初始化EventBus
        jcToolsEventBus = JCToolsEventBus.getInstance(vertx)
        jcToolsEventBus.start()
        
        eventBusManager = EventBusManager.getInstance(vertx)
        
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加Body处理器
        router.route().handler(BodyHandler.create())
        
        // 添加请求计数中间件
        router.route().handler { ctx ->
            requestCounter.incrementAndGet()
            ctx.next()
        }
        
        // 添加Hello路由
        router.get("/api/hello").handler { ctx ->
            val response = JsonObject()
                .put("message", "Hello, World!")
                .put("timestamp", System.currentTimeMillis())
                .put("counter", requestCounter.get())
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode())
        }
        
        // 添加EventBus路由
        router.post("/api/eventbus").handler { ctx ->
            val body = ctx.body().asJsonObject()
            val address = body.getString("address")
            val messageBody = body.getJsonObject("body")
            
            if (address == null || messageBody == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing address or body")
                        .encode())
                return@handler
            }
            
            // 使用EventBusManager发送消息
            eventBusManager.send(address, messageBody)
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("message", "Message sent to $address")
                    .encode())
        }
        
        // 添加数据处理路由
        router.post("/api/data").handler { ctx ->
            val body = ctx.body().asJsonObject()
            
            // 处理数据
            val dataSize = body.size()
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("message", "Processed $dataSize data items")
                    .encode())
        }
        
        // 添加统计信息路由
        router.get("/api/stats").handler { ctx ->
            val stats = JsonObject()
                .put("requests", requestCounter.get())
                .put("eventbus_type", eventBusManager.getCurrentType().name)
                .put("timestamp", System.currentTimeMillis())
            
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(stats.encode())
        }
        
        // 添加EventBus类型切换路由
        router.post("/api/eventbus/switch").handler { ctx ->
            val body = ctx.body().asJsonObject()
            val type = body.getString("type")
            
            if (type == null) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing type")
                        .encode())
                return@handler
            }
            
            val eventBusType = try {
                EventBusManager.EventBusType.valueOf(type.uppercase())
            } catch (e: IllegalArgumentException) {
                ctx.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Invalid type: $type")
                        .encode())
                return@handler
            }
            
            // 切换EventBus类型
            eventBusManager.switchType(eventBusType)
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("success", true)
                                .put("message", "Switched to $type")
                                .put("current_type", eventBusManager.getCurrentType().name)
                                .encode())
                    } else {
                        ctx.response()
                            .setStatusCode(500)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("success", false)
                                .put("error", ar.cause().message)
                                .encode())
                    }
                }
        }
        
        // 注册EventBus消息处理器
        vertx.eventBus().consumer<JsonObject>("test.eventbus") { message ->
            logger.debug("Received message on test.eventbus: {}", message.body().encode())
            
            // 回复消息
            message.reply(JsonObject()
                .put("success", true)
                .put("message", "Message received")
                .put("timestamp", System.currentTimeMillis()))
        }
        
        // 创建HTTP服务器
        vertx.createHttpServer(HttpServerOptions()
                .setPort(8080)
                .setHost("localhost"))
            .requestHandler(router)
            .listen()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    logger.info("EventBusTestServer started on port 8080")
                    startPromise.complete()
                } else {
                    logger.error("Failed to start EventBusTestServer", ar.cause())
                    startPromise.fail(ar.cause())
                }
            }
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping EventBusTestServer")
        stopPromise.complete()
    }
    
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val vertx = Vertx.vertx()
            
            vertx.deployVerticle(EventBusTestServer())
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        println("EventBusTestServer deployed successfully")
                    } else {
                        println("Failed to deploy EventBusTestServer: ${ar.cause().message}")
                        ar.cause().printStackTrace()
                        vertx.close()
                    }
                }
        }
    }
}
