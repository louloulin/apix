package com.louloulin.apix.core.streaming

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.streams.ReadStream
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(VertxExtension::class)
class StreamResponseOptimizerTest {
    private val logger = LoggerFactory.getLogger(StreamResponseOptimizerTest::class.java)

    private lateinit var vertx: Vertx
    private lateinit var streamResponseOptimizer: StreamResponseOptimizer
    private lateinit var server: HttpServer
    private lateinit var client: HttpClient

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建流式响应优化器
        streamResponseOptimizer = StreamResponseOptimizer(vertx)

        // 初始化流式响应优化器
        streamResponseOptimizer.initialize()
            .compose {
                // 创建HTTP服务器
                val serverOptions = HttpServerOptions()
                    .setPort(8888)
                    .setHost("localhost")

                server = vertx.createHttpServer(serverOptions)

                // 设置请求处理器
                server.requestHandler { req ->
                    if (req.path() == "/stream") {
                        // 创建模拟流
                        val mockStream = createMockStream()

                        // 使用流式响应优化器处理流
                        streamResponseOptimizer.handleStreamResponse(mockStream, req.response())
                    } else {
                        req.response()
                            .setStatusCode(404)
                            .end("Not Found")
                    }
                }

                // 启动服务器
                server.listen()
            }
            .compose {
                // 创建HTTP客户端
                val clientOptions = HttpClientOptions()

                client = vertx.createHttpClient(clientOptions)

                testContext.completeNow()
                io.vertx.core.Future.succeededFuture<Void>()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        // 关闭客户端
        client.close()
            .compose {
                // 关闭服务器
                server.close()
            }
            .compose {
                // 关闭流式响应优化器
                streamResponseOptimizer.close()
            }
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testStreamResponseOptimizer(testContext: VertxTestContext) {
        // 发送请求
        client.request(HttpMethod.GET, 8888, "localhost", "/stream")
            .compose { request ->
                request.send()
            }
            .compose { response ->
                // 验证响应
                testContext.verify {
                    assert(response.statusCode() == 200)
                }

                // 读取响应体
                val chunks = mutableListOf<Buffer>()
                val totalBytes = AtomicInteger(0)

                response.handler { chunk ->
                    chunks.add(chunk)
                    totalBytes.addAndGet(chunk.length())
                }

                response.endHandler {
                    testContext.verify {
                        // 验证接收到的数据
                        assert(chunks.size > 0)
                        assert(totalBytes.get() > 0)

                        logger.info("接收到 {} 个数据块，共 {} 字节", chunks.size, totalBytes.get())
                    }

                    testContext.completeNow()
                }

                io.vertx.core.Future.succeededFuture<Void>()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }

        // 等待测试完成
        assert(testContext.awaitCompletion(30, TimeUnit.SECONDS))
    }

    /**
     * 创建模拟流
     */
    private fun createMockStream(): ReadStream<Buffer> {
        // 创建模拟流
        val mockStream = MockReadStream()

        // 模拟数据
        val data = listOf(
            "这是第一块数据",
            "这是第二块数据",
            "这是第三块数据",
            "这是第四块数据",
            "这是第五块数据"
        )

        // 模拟发送数据
        var index = 0
        var timerId = 0L
        timerId = vertx.setPeriodic(100) { _ ->
            if (index < data.size) {
                mockStream.write(Buffer.buffer(data[index]))
                index++
            } else {
                vertx.cancelTimer(timerId)
                mockStream.end()
            }
        }

        return mockStream
    }
}
