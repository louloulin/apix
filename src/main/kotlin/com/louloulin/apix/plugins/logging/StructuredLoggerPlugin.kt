package com.louloulin.apix.plugins.logging

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext

/**
 * 结构化日志插件
 * 
 * 该插件用于记录请求和响应的结构化日志。
 */
class StructuredLoggerPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(StructuredLoggerPlugin::class.java)
    override val type: String = "structuredLogger"
    
    // 日志处理器
    private val handler: StructuredLoggerHandler
    
    init {
        // 解析配置
        val logRequest = config.config.getBoolean("logRequest", true)
        val logResponse = config.config.getBoolean("logResponse", true)
        val logHeaders = config.config.getBoolean("logHeaders", true)
        val captureRequestBody = config.config.getBoolean("captureRequestBody", true)
        val captureResponseBody = config.config.getBoolean("captureResponseBody", false)
        val sensitiveHeaders = config.config.getJsonArray("sensitiveHeaders")
            ?.map { it.toString().lowercase() }
            ?.toSet()
            ?: StructuredLoggerHandler.Options().sensitiveHeaders
        
        // 创建处理器
        val options = StructuredLoggerHandler.Options(
            logRequest = logRequest,
            logResponse = logResponse,
            logHeaders = logHeaders,
            captureRequestBody = captureRequestBody,
            captureResponseBody = captureResponseBody,
            sensitiveHeaders = sensitiveHeaders
        )
        
        handler = StructuredLoggerHandler.create(options)
        
        logger.info("Initialized structured logger plugin")
    }
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 使用处理器处理请求
            handler.handle(context)
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing structured logger plugin", e)
            // 发生错误时，继续处理请求
            context.next()
            promise.complete()
        }
        
        return promise.future()
    }
    
    override fun shutdown() {
        // 无需释放资源
    }
    
    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return StructuredLoggerPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
