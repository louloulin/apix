package com.louloulin.apix.core.verticle

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import org.slf4j.LoggerFactory

/**
 * 基准测试Verticle，提供简单的测试端点
 */
class BenchmarkVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(BenchmarkVerticle::class.java)

    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting BenchmarkVerticle")

        // 创建路由
        val router = Router.router(vertx)

        // 添加ping端点 - 最轻量级的端点
        router.get("/ping").handler { ctx ->
            ctx.response()
                .putHeader("Content-Type", "text/plain")
                .end("pong")
        }

        // 添加hello端点 - 返回JSON
        router.get("/api/hello").handler { ctx ->
            val response = JsonObject()
                .put("message", "Hello, World!")
                .put("timestamp", System.currentTimeMillis())

            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode())
        }

        // 添加echo端点 - 回显请求参数
        router.get("/api/echo").handler { ctx ->
            val params = JsonObject()
            ctx.queryParams().forEach { (key, value) ->
                params.put(key, value)
            }

            val response = JsonObject()
                .put("echo", params)
                .put("timestamp", System.currentTimeMillis())

            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(response.encode())
        }

        // 从配置中获取端口和主机
        val port = config().getInteger("server.port", 10080) // 使用高端口避免冲突
        val host = config().getString("server.host", "0.0.0.0")

        // 创建HTTP服务器
        val serverOptions = HttpServerOptions()
            .setPort(port)
            .setHost(host)
            .setTcpNoDelay(true)
            .setTcpFastOpen(true)
            .setTcpQuickAck(true)
            .setReuseAddress(true)
            .setReusePort(true)

        // 启动HTTP服务器
        vertx.createHttpServer(serverOptions)
            .requestHandler(router)
            .listen()
            .onSuccess { server ->
                logger.info("BenchmarkVerticle started on port {}", server.actualPort())
                startPromise.complete()
            }
            .onFailure { err ->
                logger.error("Failed to start BenchmarkVerticle", err)
                startPromise.fail(err)
            }
    }
}
