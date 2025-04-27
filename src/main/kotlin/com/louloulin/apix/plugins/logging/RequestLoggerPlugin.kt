package com.louloulin.apix.plugins.logging

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 请求日志插件，用于记录请求和响应的详细信息。
 */
class RequestLoggerPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(RequestLoggerPlugin::class.java)
    
    override val type: String = "request-logger"
    
    // 配置值
    private val logLevel: String = config.getString("log_level", "info") ?: "info"
    private val includeHeaders: Boolean = config.getBoolean("include_headers", false) ?: false
    private val includeBody: Boolean = config.getBoolean("include_body", false) ?: false
    private val maskSensitiveHeaders: List<String> = config.getJsonArray("mask_headers")
        ?.map { it.toString().lowercase() } ?: listOf("authorization", "x-api-key")
    
    // 日期格式化器
    private val dateFormatter = DateTimeFormatter
        .ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            val startTime = System.currentTimeMillis()
            
            // 记录请求信息
            logRequest(context)
            
            // 添加响应处理器来记录响应信息
            context.addBodyEndHandler {
                val duration = System.currentTimeMillis() - startTime
                logResponse(context, duration)
            }
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing request logger plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 记录请求信息。
     */
    private fun logRequest(context: RoutingContext) {
        val request = context.request()
        val method = request.method()
        val path = request.path()
        val timestamp = dateFormatter.format(Instant.now())
        
        val logData = JsonObject()
            .put("timestamp", timestamp)
            .put("type", "request")
            .put("method", method.name())
            .put("path", path)
            .put("query", request.query() ?: "")
            .put("client_ip", request.remoteAddress().hostAddress())
        
        // 添加请求头
        if (includeHeaders) {
            val headers = JsonObject()
            request.headers().forEach { header ->
                val headerName = header.key.lowercase()
                val headerValue = if (maskSensitiveHeaders.contains(headerName)) {
                    "********"
                } else {
                    header.value
                }
                headers.put(header.key, headerValue)
            }
            logData.put("headers", headers)
        }
        
        // 添加请求体
        if (includeBody && method != HttpMethod.GET) {
            val body = context.body().buffer()
            if (body != null) {
                try {
                    // 尝试解析为JSON
                    val jsonBody = JsonObject(body.toString())
                    logData.put("body", jsonBody)
                } catch (e: Exception) {
                    // 不是有效的JSON，使用字符串
                    logData.put("body", body.toString())
                }
            }
        }
        
        // 根据配置的日志级别记录
        when (logLevel.lowercase()) {
            "debug" -> logger.debug(logData.encode())
            "info" -> logger.info(logData.encode())
            "warn" -> logger.warn(logData.encode())
            "error" -> logger.error(logData.encode())
            else -> logger.info(logData.encode())
        }
    }
    
    /**
     * 记录响应信息。
     */
    private fun logResponse(context: RoutingContext, duration: Long) {
        val response = context.response()
        val statusCode = response.statusCode
        val timestamp = dateFormatter.format(Instant.now())
        
        val logData = JsonObject()
            .put("timestamp", timestamp)
            .put("type", "response")
            .put("method", context.request().method().name())
            .put("path", context.request().path())
            .put("status", statusCode)
            .put("duration_ms", duration)
        
        // 添加响应头
        if (includeHeaders) {
            val headers = JsonObject()
            response.headers().forEach { header ->
                headers.put(header.key, header.value)
            }
            logData.put("headers", headers)
        }
        
        // 根据配置的日志级别记录
        when (logLevel.lowercase()) {
            "debug" -> logger.debug(logData.encode())
            "info" -> logger.info(logData.encode())
            "warn" -> logger.warn(logData.encode())
            "error" -> logger.error(logData.encode())
            else -> logger.info(logData.encode())
        }
    }
    
    override fun shutdown() {
        // 没有资源需要清理
    }
}
