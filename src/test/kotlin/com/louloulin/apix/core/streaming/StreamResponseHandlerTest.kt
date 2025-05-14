package com.louloulin.apix.core.streaming

import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
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
class StreamResponseHandlerTest {
    private val logger = LoggerFactory.getLogger(StreamResponseHandlerTest::class.java)

    private lateinit var vertx: Vertx
    private lateinit var streamResponseHandler: StreamResponseHandler
    private lateinit var server: HttpServer
    private lateinit var client: HttpClient

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建流式响应处理器
        streamResponseHandler = StreamResponseHandler(vertx)

        // 初始化流式响应处理器
        streamResponseHandler.initialize()
            .compose {
                // 创建HTTP服务器
                val serverOptions = HttpServerOptions()
                    .setPort(8889)
                    .setHost("localhost")

                server = vertx.createHttpServer(serverOptions)

                // 创建路由器
                val router = Router.router(vertx)

                // 设置OpenAI流式响应路由
                router.get("/openai-stream").handler { context ->
                    // 创建模拟OpenAI流
                    val mockStream = createMockOpenAIStream()

                    // 使用流式响应处理器处理流
                    streamResponseHandler.handleOpenAIStream(
                        context,
                        mockStream,
                        JsonObject().put("format", "sse")
                    )
                }

                // 设置Anthropic流式响应路由
                router.get("/anthropic-stream").handler { context ->
                    // 创建模拟Anthropic流
                    val mockStream = createMockAnthropicStream()

                    // 使用流式响应处理器处理流
                    streamResponseHandler.handleAnthropicStream(
                        context,
                        mockStream,
                        JsonObject().put("format", "sse")
                    )
                }

                // 设置通用流式响应路由
                router.get("/generic-stream").handler { context ->
                    // 创建模拟通用流
                    val mockStream = createMockGenericStream()

                    // 使用流式响应处理器处理流
                    streamResponseHandler.handleGenericStream(
                        context,
                        mockStream,
                        JsonObject().put("format", "sse")
                    )
                }

                // 设置请求处理器
                server.requestHandler(router)

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
                // 关闭流式响应处理器
                streamResponseHandler.close()
            }
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testOpenAIStreamResponseHandler(testContext: VertxTestContext) {
        // 发送请求
        client.request(HttpMethod.GET, 8889, "localhost", "/openai-stream")
            .compose { request ->
                request.send()
            }
            .compose { response ->
                // 验证响应
                testContext.verify {
                    assert(response.statusCode() == 200)
                    assert(response.getHeader("Content-Type") == "text/event-stream")
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

                        // 验证SSE格式
                        val content = chunks.joinToString("") { it.toString() }
                        assert(content.contains("data:"))
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

    @Test
    fun testAnthropicStreamResponseHandler(testContext: VertxTestContext) {
        // 发送请求
        client.request(HttpMethod.GET, 8889, "localhost", "/anthropic-stream")
            .compose { request ->
                request.send()
            }
            .compose { response ->
                // 验证响应
                testContext.verify {
                    assert(response.statusCode() == 200)
                    assert(response.getHeader("Content-Type") == "text/event-stream")
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

                        // 验证SSE格式
                        val content = chunks.joinToString("") { it.toString() }
                        assert(content.contains("data:"))
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

    @Test
    fun testGenericStreamResponseHandler(testContext: VertxTestContext) {
        // 发送请求
        client.request(HttpMethod.GET, 8889, "localhost", "/generic-stream")
            .compose { request ->
                request.send()
            }
            .compose { response ->
                // 验证响应
                testContext.verify {
                    assert(response.statusCode() == 200)
                    assert(response.getHeader("Content-Type") == "text/event-stream")
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

                        // 验证SSE格式
                        val content = chunks.joinToString("") { it.toString() }
                        assert(content.contains("data:"))
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
     * 创建模拟OpenAI流
     */
    private fun createMockOpenAIStream(): ReadStream<Buffer> {
        // 创建模拟流
        val mockStream = MockReadStream()

        // 模拟数据
        val data = listOf(
            """data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1694268190,"model":"gpt-3.5-turbo-0613","choices":[{"index":0,"delta":{"role":"assistant","content":"Hello"},"finish_reason":null}]}""",
            """data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1694268190,"model":"gpt-3.5-turbo-0613","choices":[{"index":0,"delta":{"content":" world"},"finish_reason":null}]}""",
            """data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1694268190,"model":"gpt-3.5-turbo-0613","choices":[{"index":0,"delta":{"content":"!"},"finish_reason":null}]}""",
            """data: {"id":"chatcmpl-123","object":"chat.completion.chunk","created":1694268190,"model":"gpt-3.5-turbo-0613","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
            """data: [DONE]"""
        )

        // 模拟发送数据
        var index = 0
        var timerId = 0L
        timerId = vertx.setPeriodic(100) { _ ->
            if (index < data.size) {
                mockStream.write(Buffer.buffer(data[index] + "\n\n"))
                index++
            } else {
                vertx.cancelTimer(timerId)
                mockStream.end()
            }
        }

        return mockStream
    }

    /**
     * 创建模拟Anthropic流
     */
    private fun createMockAnthropicStream(): ReadStream<Buffer> {
        // 创建模拟流
        val mockStream = MockReadStream()

        // 模拟数据
        val data = listOf(
            """data: {"type":"content_block_delta","delta":{"type":"text","text":"Hello"},"index":0}""",
            """data: {"type":"content_block_delta","delta":{"type":"text","text":" world"},"index":0}""",
            """data: {"type":"content_block_delta","delta":{"type":"text","text":"!"},"index":0}""",
            """data: {"type":"message_stop"}"""
        )

        // 模拟发送数据
        var index = 0
        var timerId = 0L
        timerId = vertx.setPeriodic(100) { _ ->
            if (index < data.size) {
                mockStream.write(Buffer.buffer(data[index] + "\n\n"))
                index++
            } else {
                vertx.cancelTimer(timerId)
                mockStream.end()
            }
        }

        return mockStream
    }

    /**
     * 创建模拟通用流
     */
    private fun createMockGenericStream(): ReadStream<Buffer> {
        // 创建模拟流
        val mockStream = MockReadStream()

        // 模拟数据
        val data = listOf(
            "Hello",
            " world",
            "!",
            "This is a test."
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
