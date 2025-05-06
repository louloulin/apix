package com.louloulin.apix.core.http

import io.vertx.core.AbstractVerticle
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.Http2Settings
import io.vertx.core.http.HttpServerOptions
import org.slf4j.LoggerFactory

/**
 * 专用高性能HTTP服务器，用于性能测试
 * 这个服务器绕过所有中间件和常规处理流程，直接返回响应
 */
class HighPerformanceServer : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(HighPerformanceServer::class.java)
    
    // 默认端口
    private val DEFAULT_PORT = 8081
    
    override fun start(startPromise: Promise<Void>) {
        // 创建超高性能HTTP服务器配置
        val serverOptions = HttpServerOptions()
            // 基本设置
            .setPort(DEFAULT_PORT)
            .setHost("0.0.0.0")
            // TCP优化
            .setTcpNoDelay(true)
            .setTcpFastOpen(true)
            .setTcpQuickAck(true)
            .setReusePort(true)
            // 连接设置
            .setAcceptBacklog(65535)
            .setIdleTimeout(300)
            // 禁用压缩以减少CPU开销
            .setCompressionSupported(false)
            .setDecompressionSupported(false)
            // 缓冲区设置
            .setReceiveBufferSize(262144)  // 256KB
            .setSendBufferSize(262144)     // 256KB
            // HTTP/2设置
            .setUseAlpn(true)
            .setInitialSettings(Http2Settings()
                .setMaxConcurrentStreams(100000)
                .setInitialWindowSize(2097152)  // 2MB
                .setHeaderTableSize(16384)      // 16KB
                .setMaxHeaderListSize(65536)    // 64KB
                .setMaxFrameSize(24576)         // 24KB
                .setPushEnabled(false)
            )
        
        // 创建HTTP服务器
        vertx.createHttpServer(serverOptions)
            .requestHandler { req ->
                // 直接返回响应，不做任何处理
                req.response()
                    .putHeader("content-type", "text/plain")
                    .end("OK")
            }
            .listen()
            .onSuccess { server ->
                logger.info("High Performance Server started on port {}", server.actualPort())
                startPromise.complete()
            }
            .onFailure { err ->
                logger.error("Failed to start High Performance Server", err)
                startPromise.fail(err)
            }
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping High Performance Server")
        stopPromise.complete()
    }
}
