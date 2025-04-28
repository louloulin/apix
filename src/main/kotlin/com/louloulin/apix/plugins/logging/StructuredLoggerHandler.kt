package com.louloulin.apix.plugins.logging

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.core.logging.StructuredLogger
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.ext.web.RoutingContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 结构化日志处理器
 *
 * 该处理器用于记录请求和响应的结构化日志。
 */
class StructuredLoggerHandler(
    private val options: Options = Options()
) : Handler<RoutingContext> {
    private val logger = LoggerFactory.getLogger(StructuredLoggerHandler::class.java)

    override fun handle(context: RoutingContext) {
        val startTime = System.currentTimeMillis()
        val requestId = UUID.randomUUID().toString()

        // 设置请求 ID
        context.put("requestId", requestId)
        StructuredLogger.setContext("requestId", requestId)

        // 记录请求日志
        if (options.logRequest) {
            logRequest(context, requestId)
        }

        // 记录响应日志
        if (options.logResponse) {
            val responseBodyCapture = options.captureResponseBody
            val requestEnded = AtomicBoolean(false)

            // 保存原始的 end 方法
            val originalEnd = context.response().endHandler(null)

            // 替换 end 方法，以便在响应结束时记录日志
            val bodyBuffer = Buffer.buffer()

            // 添加响应完成处理器
            context.response().bodyEndHandler {
                if (requestEnded.compareAndSet(false, true)) {
                    val duration = System.currentTimeMillis() - startTime
                    val responseBody = if (responseBodyCapture) {
                        // 获取响应体
                        try {
                            context.response().headers().get("Content-Type")?.let { contentType ->
                                if (contentType.startsWith("application/json") || contentType.startsWith("text/")) {
                                    // 对于 JSON 和文本响应，我们可以获取完整的响应体
                                    // 但这需要在应用中正确设置 Content-Type
                                    context.body().asString()
                                } else {
                                    "<binary data>"
                                }
                            } ?: "<unknown content type>"
                        } catch (e: Exception) {
                            "<error capturing response body: ${e.message}>"
                        }
                    } else null

                    logResponse(context, requestId, duration, responseBody)
                    StructuredLogger.clearContext("requestId")
                }
            }
        }

        // 继续处理请求
        context.next()
    }

    /**
     * 记录请求日志
     */
    private fun logRequest(context: RoutingContext, requestId: String) {
        val request = context.request()
        val method = request.method().name()
        val path = request.path()
        val query = request.query() ?: ""
        val remoteAddress = request.remoteAddress().host()
        val userAgent = request.getHeader("User-Agent") ?: ""

        val fields = mutableMapOf<String, Any>(
            "requestId" to requestId,
            "method" to method,
            "path" to path,
            "remoteAddress" to remoteAddress,
            "userAgent" to userAgent
        )

        // 添加查询参数
        if (query.isNotEmpty()) {
            fields["query"] = query
        }

        // 添加请求头
        if (options.logHeaders) {
            val headers = mutableMapOf<String, String>()
            request.headers().forEach { header ->
                // 过滤敏感头
                if (!options.sensitiveHeaders.contains(header.key.lowercase())) {
                    headers[header.key] = header.value
                }
            }
            fields["headers"] = headers
        }

        // 添加请求体
        if (options.captureRequestBody && shouldCaptureBody(request)) {
            val body = context.body().asString()
            if (!body.isNullOrEmpty()) {
                fields["body"] = body
            }
        }

        logger.info("Received request: {} {}", fields, method, path)
    }

    /**
     * 记录响应日志
     */
    private fun logResponse(context: RoutingContext, requestId: String, duration: Long, responseBody: String?) {
        val response = context.response()
        val statusCode = response.statusCode

        val fields = mutableMapOf<String, Any>(
            "requestId" to requestId,
            "statusCode" to statusCode,
            "duration" to duration
        )

        // 添加响应头
        if (options.logHeaders) {
            val headers = mutableMapOf<String, String>()
            response.headers().forEach { header ->
                // 过滤敏感头
                if (!options.sensitiveHeaders.contains(header.key.lowercase())) {
                    headers[header.key] = header.value
                }
            }
            fields["headers"] = headers
        }

        // 添加响应体
        if (responseBody != null && responseBody.isNotEmpty()) {
            fields["body"] = responseBody
        }

        logger.info("Completed request: {} {} in {}ms", fields, context.request().method().name(), context.request().path(), duration)
    }

    /**
     * 判断是否应该捕获请求体
     */
    private fun shouldCaptureBody(request: HttpServerRequest): Boolean {
        // 只捕获 POST、PUT、PATCH 请求的请求体
        return request.method() == HttpMethod.POST || request.method() == HttpMethod.PUT || request.method() == HttpMethod.PATCH
    }

    /**
     * 结构化日志处理器选项
     */
    data class Options(
        val logRequest: Boolean = true,
        val logResponse: Boolean = true,
        val logHeaders: Boolean = true,
        val captureRequestBody: Boolean = true,
        val captureResponseBody: Boolean = false,
        val sensitiveHeaders: Set<String> = setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
            "x-auth-token",
            "auth-token",
            "x-csrf-token",
            "csrf-token"
        )
    )

    companion object {
        /**
         * 创建结构化日志处理器
         */
        fun create(options: Options = Options()): StructuredLoggerHandler {
            return StructuredLoggerHandler(options)
        }
    }
}
