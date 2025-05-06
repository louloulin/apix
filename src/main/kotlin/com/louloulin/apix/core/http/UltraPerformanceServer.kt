package com.louloulin.apix.core.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.*
import io.netty.util.CharsetUtil
import io.netty.util.AsciiString
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 超高性能HTTP服务器，直接使用Netty实现，绕过Vert.x的所有抽象层
 * 仅用于性能测试，不用于生产环境
 */
class UltraPerformanceServer {
    private val logger = LoggerFactory.getLogger(UltraPerformanceServer::class.java)

    private val bossGroup = NioEventLoopGroup(1)
    private val workerGroup = NioEventLoopGroup()
    private val started = AtomicBoolean(false)
    private var channel: Channel? = null

    /**
     * 启动服务器
     *
     * @param port 端口号
     */
    fun start(port: Int = 8082) {
        if (started.compareAndSet(false, true)) {
            try {
                val bootstrap = ServerBootstrap()
                bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel::class.java)
                    .childHandler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(ch: SocketChannel) {
                            val pipeline = ch.pipeline()
                            pipeline.addLast(HttpServerCodec())
                            pipeline.addLast(HttpObjectAggregator(65536))
                            pipeline.addLast(UltraPerformanceHandler())
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_REUSEADDR, true)

                // 绑定端口并启动服务器
                channel = bootstrap.bind(port).sync().channel()
                logger.info("Ultra Performance Server started on port $port")
            } catch (e: Exception) {
                logger.error("Failed to start Ultra Performance Server", e)
                stop()
            }
        }
    }

    /**
     * 停止服务器
     */
    fun stop() {
        if (started.compareAndSet(true, false)) {
            try {
                channel?.close()
                bossGroup.shutdownGracefully()
                workerGroup.shutdownGracefully()
                logger.info("Ultra Performance Server stopped")
            } catch (e: Exception) {
                logger.error("Error stopping Ultra Performance Server", e)
            }
        }
    }

    /**
     * 超高性能HTTP处理器
     */
    private class UltraPerformanceHandler : SimpleChannelInboundHandler<FullHttpRequest>() {
        // 预先构建响应内容，避免每次请求都创建新对象
        private val responseContent = Unpooled.copiedBuffer("{\"message\":\"OK\"}", CharsetUtil.UTF_8)
        private val contentType = AsciiString("application/json")
        private val contentLength = AsciiString(responseContent.readableBytes().toString())

        override fun channelRead0(ctx: ChannelHandlerContext, request: FullHttpRequest) {
            if (HttpUtil.is100ContinueExpected(request)) {
                ctx.write(DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
                return
            }

            // 创建响应
            val response = DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.OK,
                Unpooled.copiedBuffer(responseContent)
            )

            // 设置响应头
            response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, contentType)
                .set(HttpHeaderNames.CONTENT_LENGTH, contentLength)

            // 如果是保持连接，设置相应的头
            if (HttpUtil.isKeepAlive(request)) {
                response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
            }

            // 写入响应并刷新
            ctx.writeAndFlush(response)
        }

        override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
            ctx.close()
        }
    }

    companion object {
        private var instance: UltraPerformanceServer? = null

        @Synchronized
        fun getInstance(): UltraPerformanceServer {
            if (instance == null) {
                instance = UltraPerformanceServer()
            }
            return instance!!
        }
    }
}
