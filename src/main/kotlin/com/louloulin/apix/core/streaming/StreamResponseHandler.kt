package com.louloulin.apix.core.streaming

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 流式响应处理器
 *
 * 该类用于处理AI模型的流式响应，支持不同AI提供商的流式输出格式。
 * 它提供了以下功能：
 * 1. 统一的流式响应处理接口
 * 2. 支持不同AI提供商的流式输出格式
 * 3. 支持SSE（Server-Sent Events）格式
 * 4. 支持JSON流格式
 * 5. 支持自定义格式
 * 6. 支持流式响应的转换和过滤
 * 7. 支持流式响应的监控和统计
 */
class StreamResponseHandler(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(StreamResponseHandler::class.java)

    // 流式响应优化器
    private val streamResponseOptimizer = StreamResponseOptimizer(vertx)

    // 活跃的流式响应
    private val activeResponses = ConcurrentHashMap<String, StreamResponseContext>()

    // 统计信息
    private val totalResponses = AtomicLong(0)
    private val successfulResponses = AtomicLong(0)
    private val failedResponses = AtomicLong(0)

    /**
     * 初始化流式响应处理器
     */
    fun initialize(): Future<Void> {
        logger.info("初始化流式响应处理器")

        // 初始化流式响应优化器
        return streamResponseOptimizer.initialize()
    }

    /**
     * 处理OpenAI流式响应
     *
     * @param context 路由上下文
     * @param responseStream 响应流
     * @param options 选项
     * @return 包含操作结果的 Future
     */
    fun handleOpenAIStream(
        context: RoutingContext,
        responseStream: io.vertx.core.streams.ReadStream<Buffer>,
        options: JsonObject = JsonObject()
    ): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 生成响应ID
            val responseId = options.getString("responseId") ?: generateResponseId()

            // 获取选项
            val format = options.getString("format", "sse") // sse 或 json
            val includeRaw = options.getBoolean("includeRaw", false)
            val filterFunction = options.getString("filterFunction")

            // 创建响应上下文
            val responseContext = StreamResponseContext(
                id = responseId,
                format = format,
                includeRaw = includeRaw,
                filterFunction = filterFunction
            )

            // 保存响应上下文
            activeResponses[responseId] = responseContext

            // 增加总响应数
            totalResponses.incrementAndGet()

            // 设置响应头
            val response = context.response()
            response.putHeader("Content-Type", if (format == "sse") "text/event-stream" else "application/json")
            response.putHeader("Cache-Control", "no-cache")
            response.putHeader("Connection", "keep-alive")
            response.putHeader("X-Accel-Buffering", "no") // 禁用Nginx缓冲
            response.putHeader("X-Response-ID", responseId)

            // 创建转换流
            val transformStream = createOpenAITransformStream(responseContext)

            // 设置管道：responseStream -> transformStream -> response
            // 使用自定义处理器来处理流之间的传输
            responseStream.handler { buffer ->
                if (transformStream is BufferReadStream) {
                    (transformStream as BufferReadStream).handle(buffer)
                }
            }

            // 使用流式响应优化器处理转换后的流
            streamResponseOptimizer.handleStreamResponse(transformStream, response, options)
                .onSuccess {
                    logger.debug("OpenAI流式响应处理完成: {}", responseId)
                    activeResponses.remove(responseId)
                    successfulResponses.incrementAndGet()
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("OpenAI流式响应处理失败: {}", responseId, err)
                    activeResponses.remove(responseId)
                    failedResponses.incrementAndGet()
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("设置OpenAI流式响应处理时出错", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 处理Anthropic流式响应
     *
     * @param context 路由上下文
     * @param responseStream 响应流
     * @param options 选项
     * @return 包含操作结果的 Future
     */
    fun handleAnthropicStream(
        context: RoutingContext,
        responseStream: io.vertx.core.streams.ReadStream<Buffer>,
        options: JsonObject = JsonObject()
    ): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 生成响应ID
            val responseId = options.getString("responseId") ?: generateResponseId()

            // 获取选项
            val format = options.getString("format", "sse") // sse 或 json
            val includeRaw = options.getBoolean("includeRaw", false)
            val filterFunction = options.getString("filterFunction")

            // 创建响应上下文
            val responseContext = StreamResponseContext(
                id = responseId,
                format = format,
                includeRaw = includeRaw,
                filterFunction = filterFunction
            )

            // 保存响应上下文
            activeResponses[responseId] = responseContext

            // 增加总响应数
            totalResponses.incrementAndGet()

            // 设置响应头
            val response = context.response()
            response.putHeader("Content-Type", if (format == "sse") "text/event-stream" else "application/json")
            response.putHeader("Cache-Control", "no-cache")
            response.putHeader("Connection", "keep-alive")
            response.putHeader("X-Accel-Buffering", "no") // 禁用Nginx缓冲
            response.putHeader("X-Response-ID", responseId)

            // 创建转换流
            val transformStream = createAnthropicTransformStream(responseContext)

            // 设置管道：responseStream -> transformStream -> response
            // 使用自定义处理器来处理流之间的传输
            responseStream.handler { buffer ->
                if (transformStream is BufferReadStream) {
                    (transformStream as BufferReadStream).handle(buffer)
                }
            }

            // 使用流式响应优化器处理转换后的流
            streamResponseOptimizer.handleStreamResponse(transformStream, response, options)
                .onSuccess {
                    logger.debug("Anthropic流式响应处理完成: {}", responseId)
                    activeResponses.remove(responseId)
                    successfulResponses.incrementAndGet()
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("Anthropic流式响应处理失败: {}", responseId, err)
                    activeResponses.remove(responseId)
                    failedResponses.incrementAndGet()
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("设置Anthropic流式响应处理时出错", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 处理通用流式响应
     *
     * @param context 路由上下文
     * @param responseStream 响应流
     * @param options 选项
     * @return 包含操作结果的 Future
     */
    fun handleGenericStream(
        context: RoutingContext,
        responseStream: io.vertx.core.streams.ReadStream<Buffer>,
        options: JsonObject = JsonObject()
    ): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 生成响应ID
            val responseId = options.getString("responseId") ?: generateResponseId()

            // 获取选项
            val format = options.getString("format", "raw") // raw, sse 或 json
            val contentType = options.getString("contentType", "application/octet-stream")
            val transformFunction = options.getString("transformFunction")

            // 创建响应上下文
            val responseContext = StreamResponseContext(
                id = responseId,
                format = format,
                includeRaw = false,
                filterFunction = null,
                transformFunction = transformFunction
            )

            // 保存响应上下文
            activeResponses[responseId] = responseContext

            // 增加总响应数
            totalResponses.incrementAndGet()

            // 设置响应头
            val response = context.response()
            response.putHeader("Content-Type", when (format) {
                "sse" -> "text/event-stream"
                "json" -> "application/json"
                else -> contentType
            })
            response.putHeader("Cache-Control", "no-cache")
            response.putHeader("Connection", "keep-alive")
            response.putHeader("X-Accel-Buffering", "no") // 禁用Nginx缓冲
            response.putHeader("X-Response-ID", responseId)

            // 创建转换流
            val transformStream = createGenericTransformStream(responseContext)

            // 设置管道：responseStream -> transformStream -> response
            // 使用自定义处理器来处理流之间的传输
            responseStream.handler { buffer ->
                if (transformStream is BufferReadStream) {
                    (transformStream as BufferReadStream).handle(buffer)
                }
            }

            // 使用流式响应优化器处理转换后的流
            streamResponseOptimizer.handleStreamResponse(transformStream, response, options)
                .onSuccess {
                    logger.debug("通用流式响应处理完成: {}", responseId)
                    activeResponses.remove(responseId)
                    successfulResponses.incrementAndGet()
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("通用流式响应处理失败: {}", responseId, err)
                    activeResponses.remove(responseId)
                    failedResponses.incrementAndGet()
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("设置通用流式响应处理时出错", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 创建OpenAI转换流
     */
    private fun createOpenAITransformStream(context: StreamResponseContext): io.vertx.core.streams.ReadStream<Buffer> {
        // 创建转换流
        val transformStream = BufferReadStream()

        // 累积的数据
        val dataBuffer = StringBuilder()

        // 设置处理器
        transformStream.handler { buffer ->
            try {
                // 添加到累积的数据
                dataBuffer.append(buffer.toString())

                // 处理累积的数据
                val lines = dataBuffer.toString().split("\n")

                // 重置累积的数据，保留最后一行（可能不完整）
                dataBuffer.clear()
                if (lines.isNotEmpty() && lines.last().isNotEmpty()) {
                    dataBuffer.append(lines.last())
                }

                // 处理完整的行
                val completeLines = if (lines.isNotEmpty() && lines.last().isNotEmpty()) {
                    lines.dropLast(1)
                } else {
                    lines.filter { it.isNotEmpty() }
                }

                // 处理每一行
                completeLines.forEach { line ->
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)

                        if (data == "[DONE]") {
                            // 流结束
                            if (context.format == "sse") {
                                if (transformStream is BufferReadStream) {
                                    (transformStream as BufferReadStream).handle(Buffer.buffer("data: [DONE]\n\n"))
                                }
                            } else {
                                if (transformStream is BufferReadStream) {
                                    (transformStream as BufferReadStream).handle(Buffer.buffer("{\"done\": true}\n"))
                                }
                            }
                        } else {
                            try {
                                // 解析JSON数据
                                val json = JsonObject(data)

                                // 应用过滤器
                                val filteredJson = if (context.filterFunction != null) {
                                    applyFilter(json, context.filterFunction)
                                } else {
                                    json
                                }

                                // 提取内容
                                val choices = filteredJson.getJsonArray("choices", JsonArray())
                                if (choices.size() > 0) {
                                    val choice = choices.getJsonObject(0)
                                    val delta = choice.getJsonObject("delta")

                                    if (delta != null) {
                                        val content = delta.getString("content", "")

                                        if (content.isNotEmpty()) {
                                            // 根据格式输出
                                            if (context.format == "sse") {
                                                val outputJson = JsonObject()
                                                    .put("content", content)

                                                if (context.includeRaw) {
                                                    outputJson.put("raw", filteredJson)
                                                }

                                                if (transformStream is BufferReadStream) {
                                                    (transformStream as BufferReadStream).handle(Buffer.buffer("data: ${outputJson.encode()}\n\n"))
                                                }
                                            } else {
                                                val outputJson = JsonObject()
                                                    .put("content", content)

                                                if (context.includeRaw) {
                                                    outputJson.put("raw", filteredJson)
                                                }

                                                if (transformStream is BufferReadStream) {
                                                    (transformStream as BufferReadStream).handle(Buffer.buffer(outputJson.encode() + "\n"))
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.warn("解析OpenAI流式响应数据时出错: {}", e.message)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("处理OpenAI流式响应数据时出错", e)
                transformStream.close()
            }
        }

        return transformStream
    }

    /**
     * 创建Anthropic转换流
     */
    private fun createAnthropicTransformStream(context: StreamResponseContext): io.vertx.core.streams.ReadStream<Buffer> {
        // 创建转换流
        val transformStream = BufferReadStream()

        // 累积的数据
        val dataBuffer = StringBuilder()

        // 设置处理器
        transformStream.handler { buffer ->
            try {
                // 添加到累积的数据
                dataBuffer.append(buffer.toString())

                // 处理累积的数据
                val lines = dataBuffer.toString().split("\n")

                // 重置累积的数据，保留最后一行（可能不完整）
                dataBuffer.clear()
                if (lines.isNotEmpty() && lines.last().isNotEmpty()) {
                    dataBuffer.append(lines.last())
                }

                // 处理完整的行
                val completeLines = if (lines.isNotEmpty() && lines.last().isNotEmpty()) {
                    lines.dropLast(1)
                } else {
                    lines.filter { it.isNotEmpty() }
                }

                // 处理每一行
                completeLines.forEach { line ->
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6)

                        try {
                            // 解析JSON数据
                            val json = JsonObject(data)

                            // 应用过滤器
                            val filteredJson = if (context.filterFunction != null) {
                                applyFilter(json, context.filterFunction)
                            } else {
                                json
                            }

                            // 检查类型
                            val type = filteredJson.getString("type")

                            if (type == "content_block_delta") {
                                // 内容块增量
                                val delta = filteredJson.getJsonObject("delta")

                                if (delta != null) {
                                    val text = delta.getString("text", "")

                                    if (text.isNotEmpty()) {
                                        // 根据格式输出
                                        if (context.format == "sse") {
                                            val outputJson = JsonObject()
                                                .put("content", text)

                                            if (context.includeRaw) {
                                                outputJson.put("raw", filteredJson)
                                            }

                                            if (transformStream is BufferReadStream) {
                                                (transformStream as BufferReadStream).handle(Buffer.buffer("data: ${outputJson.encode()}\n\n"))
                                            }
                                        } else {
                                            val outputJson = JsonObject()
                                                .put("content", text)

                                            if (context.includeRaw) {
                                                outputJson.put("raw", filteredJson)
                                            }

                                            if (transformStream is BufferReadStream) {
                                                (transformStream as BufferReadStream).handle(Buffer.buffer(outputJson.encode() + "\n"))
                                            }
                                        }
                                    }
                                }
                            } else if (type == "message_stop") {
                                // 消息结束
                                if (context.format == "sse") {
                                    if (transformStream is BufferReadStream) {
                                        (transformStream as BufferReadStream).handle(Buffer.buffer("data: [DONE]\n\n"))
                                    }
                                } else {
                                    if (transformStream is BufferReadStream) {
                                        (transformStream as BufferReadStream).handle(Buffer.buffer("{\"done\": true}\n"))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn("解析Anthropic流式响应数据时出错: {}", e.message)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.error("处理Anthropic流式响应数据时出错", e)
                transformStream.close()
            }
        }

        return transformStream
    }

    /**
     * 创建通用转换流
     */
    private fun createGenericTransformStream(context: StreamResponseContext): io.vertx.core.streams.ReadStream<Buffer> {
        // 创建转换流
        val transformStream = BufferReadStream()

        // 设置处理器
        transformStream.handler { buffer ->
            try {
                // 根据格式处理数据
                when (context.format) {
                    "sse" -> {
                        // 转换为SSE格式
                        val data = if (context.transformFunction != null) {
                            applyTransform(buffer.toString(), context.transformFunction)
                        } else {
                            buffer.toString()
                        }

                        transformStream.handle(Buffer.buffer("data: $data\n\n"))
                    }
                    "json" -> {
                        // 转换为JSON格式
                        try {
                            val data = if (context.transformFunction != null) {
                                applyTransform(buffer.toString(), context.transformFunction)
                            } else {
                                buffer.toString()
                            }

                            // 尝试解析为JSON
                            val json = try {
                                JsonObject(data)
                            } catch (e: Exception) {
                                JsonObject().put("content", data)
                            }

                            transformStream.handle(Buffer.buffer(json.encode() + "\n"))
                        } catch (e: Exception) {
                            logger.warn("转换为JSON格式时出错: {}", e.message)
                            transformStream.handle(Buffer.buffer(JsonObject().put("content", buffer.toString()).encode() + "\n"))
                        }
                    }
                    else -> {
                        // 原始格式
                        val data = if (context.transformFunction != null) {
                            Buffer.buffer(applyTransform(buffer.toString(), context.transformFunction))
                        } else {
                            buffer
                        }

                        transformStream.handle(data)
                    }
                }
            } catch (e: Exception) {
                logger.error("处理通用流式响应数据时出错", e)
                transformStream.close()
            }
        }

        return transformStream
    }

    /**
     * 应用过滤器
     */
    private fun applyFilter(json: JsonObject, filterFunction: String): JsonObject {
        // 这里可以实现自定义过滤逻辑
        // 例如，可以使用JavaScript引擎执行过滤函数

        // 简单实现：保留原始JSON
        return json
    }

    /**
     * 应用转换
     */
    private fun applyTransform(data: String, transformFunction: String): String {
        // 这里可以实现自定义转换逻辑
        // 例如，可以使用JavaScript引擎执行转换函数

        // 简单实现：保留原始数据
        return data
    }

    /**
     * 生成响应ID
     */
    private fun generateResponseId(): String {
        return "response-${System.currentTimeMillis()}-${(Math.random() * 1000000).toInt()}"
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("totalResponses", totalResponses.get())
            .put("activeResponses", activeResponses.size)
            .put("successfulResponses", successfulResponses.get())
            .put("failedResponses", failedResponses.get())
            .put("streamStats", streamResponseOptimizer.getStats())
    }

    /**
     * 关闭流式响应处理器
     */
    fun close(): Future<Void> {
        logger.info("关闭流式响应处理器")

        // 关闭流式响应优化器
        return streamResponseOptimizer.close()
    }

    /**
     * 流式响应上下文
     */
    private data class StreamResponseContext(
        val id: String,
        val format: String,
        val includeRaw: Boolean,
        val filterFunction: String? = null,
        val transformFunction: String? = null
    )
}
