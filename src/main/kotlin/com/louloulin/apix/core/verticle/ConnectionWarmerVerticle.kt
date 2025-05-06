package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.connection.ConnectionWarmer
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 连接预热Verticle，用于管理连接预热。
 * 提供了连接预热、获取预热连接、返回预热连接等功能。
 */
class ConnectionWarmerVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(ConnectionWarmerVerticle::class.java)
    
    // 连接预热器
    private lateinit var connectionWarmer: ConnectionWarmer
    
    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting ConnectionWarmerVerticle")
        
        // 初始化连接预热器
        connectionWarmer = ConnectionWarmer.getInstance(vertx)
        
        // 注册连接预热处理器
        vertx.eventBus().consumer<JsonObject>("connection.warm") { message ->
            val body = message.body()
            val host = body.getString("host")
            val port = body.getInteger("port")
            val count = body.getInteger("count", 10)
            val ssl = body.getBoolean("ssl", false)
            
            connectionWarmer.warmConnections(host, port, count, ssl)
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        message.reply(ar.result())
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", ar.cause().message)
                        )
                    }
                }
        }
        
        // 注册批量连接预热处理器
        vertx.eventBus().consumer<JsonObject>("connection.warm.batch") { message ->
            val body = message.body()
            val endpoints = body.getJsonArray("endpoints", JsonArray())
            
            connectionWarmer.warmConnectionsBatch(endpoints)
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        message.reply(ar.result())
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", ar.cause().message)
                        )
                    }
                }
        }
        
        // 注册获取预热连接处理器
        vertx.eventBus().consumer<JsonObject>("connection.get") { message ->
            val body = message.body()
            val host = body.getString("host")
            val port = body.getInteger("port")
            
            connectionWarmer.getWarmedConnection(host, port)
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        val client = ar.result()
                        if (client != null) {
                            message.reply(JsonObject()
                                .put("success", true)
                                .put("available", true)
                            )
                        } else {
                            message.reply(JsonObject()
                                .put("success", true)
                                .put("available", false)
                            )
                        }
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", ar.cause().message)
                        )
                    }
                }
        }
        
        // 注册返回预热连接处理器
        vertx.eventBus().consumer<JsonObject>("connection.return") { message ->
            val body = message.body()
            val host = body.getString("host")
            val port = body.getInteger("port")
            val clientId = body.getString("clientId")
            
            // 注意：这里只是一个示例，实际上我们无法通过EventBus传递HttpClient对象
            // 在实际使用中，应该在获取连接的地方保存连接，并在使用完毕后返回
            message.reply(JsonObject()
                .put("success", true)
                .put("message", "Connection return is handled directly, not through EventBus")
            )
        }
        
        // 注册获取统计信息处理器
        vertx.eventBus().consumer<JsonObject>("connection.stats") { message ->
            val stats = connectionWarmer.getStats()
            message.reply(stats)
        }
        
        // 注册清理处理器
        vertx.eventBus().consumer<JsonObject>("connection.cleanup") { message ->
            connectionWarmer.cleanup()
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true)
                        )
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", ar.cause().message)
                        )
                    }
                }
        }
        
        // 设置定期统计信息记录
        vertx.setPeriodic(60000) { // 每分钟记录一次
            val stats = connectionWarmer.getStats()
            logger.info("连接预热统计信息: {}", stats.encode())
        }
        
        startPromise.complete()
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping ConnectionWarmerVerticle")
        
        // 清理所有预热连接
        connectionWarmer.cleanup()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    logger.info("连接预热器清理完成")
                } else {
                    logger.warn("连接预热器清理失败", ar.cause())
                }
                
                stopPromise.complete()
            }
    }
}
