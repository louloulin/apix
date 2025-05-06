package com.louloulin.apix.core.http

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpVersion
import io.vertx.core.http.Http2Settings
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * HTTP/2 优化器，用于配置和优化 HTTP/2 服务器和客户端。
 * 提供了一系列优化 HTTP/2 性能的方法和配置。
 */
class Http2Optimizer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(Http2Optimizer::class.java)

    // 默认的 HTTP/2 设置
    private val defaultHttp2Settings = Http2Settings()
        .setMaxConcurrentStreams(10000)         // 每个连接的最大并发流数
        .setInitialWindowSize(1048576)          // 初始窗口大小 (1MB)
        .setHeaderTableSize(8192)               // HPACK 头表大小
        .setMaxHeaderListSize(32768)            // 最大头列表大小
        .setMaxFrameSize(16384)                 // 最大帧大小
        .setPushEnabled(false)                  // 禁用服务器推送 (通常不需要)

    /**
     * 优化 HTTP 服务器选项，启用 HTTP/2 支持。
     *
     * @param options 现有的 HTTP 服务器选项
     * @return 优化后的 HTTP 服务器选项
     */
    fun optimizeServerOptions(options: HttpServerOptions): HttpServerOptions {
        return options
            // 启用 HTTP/2 支持
            .setUseAlpn(true)                       // 启用 ALPN 协议协商
            .setAlpnVersions(listOf(                // 支持的协议版本
                HttpVersion.HTTP_2,                 // 优先使用 HTTP/2
                HttpVersion.HTTP_1_1                // 回退到 HTTP/1.1
            ))
            // 配置 HTTP/2 设置
            .setInitialSettings(defaultHttp2Settings)
            // 启用压缩
            .setCompressionSupported(true)          // 支持响应压缩
            .setDecompressionSupported(true)        // 支持请求解压
            .setCompressionLevel(6)                 // 压缩级别 (1-9)，6是平衡点
            // 启用 100-continue 自动处理
            .setHandle100ContinueAutomatically(true)
    }

    /**
     * 从配置文件加载 HTTP/2 设置。
     *
     * @param configPath 配置文件路径
     * @return 包含 HTTP/2 设置的 Future
     */
    fun loadHttp2SettingsFromConfig(configPath: String): Future<Http2Settings> {
        val promise = Promise.promise<Http2Settings>()

        vertx.fileSystem().readFile(configPath) { ar ->
            if (ar.succeeded()) {
                try {
                    val config = JsonObject(ar.result())
                    val http2Config = config.getJsonObject("http2Settings", JsonObject())

                    val settings = Http2Settings()
                        .setMaxConcurrentStreams(http2Config.getLong("maxConcurrentStreams", 10000L))
                        .setInitialWindowSize(http2Config.getInteger("initialWindowSize", 1048576))
                        .setHeaderTableSize(http2Config.getLong("headerTableSize", 8192L))
                        .setMaxHeaderListSize(http2Config.getLong("maxHeaderListSize", 32768L))
                        .setMaxFrameSize(http2Config.getInteger("maxFrameSize", 16384))
                        .setPushEnabled(http2Config.getBoolean("pushEnabled", false))

                    logger.info("已从配置文件加载 HTTP/2 设置: {}", configPath)
                    promise.complete(settings)
                } catch (e: Exception) {
                    logger.warn("解析 HTTP/2 配置失败，使用默认设置: {}", e.message)
                    promise.complete(defaultHttp2Settings)
                }
            } else {
                logger.warn("读取 HTTP/2 配置文件失败，使用默认设置: {}", ar.cause().message)
                promise.complete(defaultHttp2Settings)
            }
        }

        return promise.future()
    }

    /**
     * 获取 HTTP/2 性能统计信息。
     *
     * @return 包含 HTTP/2 统计信息的 JsonObject
     */
    fun getHttp2Stats(): JsonObject {
        // 在实际实现中，这里应该从 Vert.x 的 HTTP/2 连接中收集统计信息
        // 由于 Vert.x 没有直接提供 HTTP/2 统计 API，这里只返回一个示例
        return JsonObject()
            .put("activeConnections", 0)
            .put("activeStreams", 0)
            .put("totalStreams", 0)
            .put("totalConnections", 0)
            .put("totalFramesSent", 0)
            .put("totalFramesReceived", 0)
            .put("totalDataSent", 0)
            .put("totalDataReceived", 0)
    }

    /**
     * 创建优化的 HTTP/2 服务器。
     *
     * @param port 服务器端口
     * @param host 服务器主机
     * @param handler 请求处理器
     * @return 包含服务器实例的 Future
     */
    fun createOptimizedHttp2Server(port: Int, host: String, handler: io.vertx.core.Handler<io.vertx.core.http.HttpServerRequest>): Future<io.vertx.core.http.HttpServer> {
        val options = HttpServerOptions()
            .setPort(port)
            .setHost(host)
            // TCP 优化
            .setTcpNoDelay(true)              // 禁用 Nagle 算法，降低延迟
            .setTcpFastOpen(true)             // 启用 TCP Fast Open，加快连接建立
            .setTcpQuickAck(true)             // 启用 TCP Quick ACK，提高响应性
            .setReusePort(true)               // 启用端口重用，提高负载分布
            .setReuseAddress(true)            // 启用地址重用，加快重启
            // 连接积压队列
            .setAcceptBacklog(10000)          // 增加连接积压队列大小
            // 超时设置
            .setIdleTimeout(300)              // 空闲超时时间 (秒)

        // 应用 HTTP/2 优化
        optimizeServerOptions(options)

        // 创建服务器
        return vertx.createHttpServer(options)
            .requestHandler(handler)
            .listen()
            .onSuccess { server ->
                logger.info("优化的 HTTP/2 服务器已启动: {}:{}", host, port)
            }
            .onFailure { err ->
                logger.error("启动优化的 HTTP/2 服务器失败: {}", err.message)
            }
    }

    companion object {
        // 单例实例
        private var INSTANCE: Http2Optimizer? = null

        /**
         * 获取 Http2Optimizer 的单例实例。
         *
         * @param vertx Vertx 实例
         * @return Http2Optimizer 实例
         */
        fun getInstance(vertx: Vertx): Http2Optimizer {
            if (INSTANCE == null) {
                synchronized(Http2Optimizer::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = Http2Optimizer(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
