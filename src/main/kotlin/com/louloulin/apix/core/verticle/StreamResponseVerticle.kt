package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.streaming.StreamResponseHandler
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.ReadStream
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * 流式响应Verticle
 *
 * 该Verticle负责管理流式响应处理，包括AI模型的流式输出处理。
 */
class StreamResponseVerticle : BaseVerticle() {
    // 流式响应处理器
    private lateinit var streamResponseHandler: StreamResponseHandler
    
    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动 StreamResponseVerticle...")
        
        // 创建流式响应处理器
        streamResponseHandler = StreamResponseHandler(vertx)
        
        // 初始化流式响应处理器
        streamResponseHandler.initialize()
            .onSuccess {
                // 注册EventBus处理器
                registerEventBusHandlers()
                
                logger.info("StreamResponseVerticle 启动完成")
                startPromise.complete()
            }
            .onFailure { err ->
                logger.error("初始化流式响应处理器失败", err)
                startPromise.fail(err)
            }
    }
    
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止 StreamResponseVerticle...")
        
        // 关闭流式响应处理器
        streamResponseHandler.close()
            .onSuccess {
                logger.info("StreamResponseVerticle 停止完成")
                stopPromise.complete()
            }
            .onFailure { err ->
                logger.error("关闭流式响应处理器失败", err)
                stopPromise.fail(err)
            }
    }
    
    override fun registerEventBusHandlers() {
        // 处理OpenAI流式响应
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.STREAM_HANDLE_OPENAI) { message ->
            val context = message.body().getValue("context") as? RoutingContext
            val stream = message.body().getValue("stream") as? ReadStream<Buffer>
            val options = message.body().getJsonObject("options", JsonObject())
            
            if (context == null || stream == null) {
                sendError(message, 400, "缺少必要参数")
                return@consumer
            }
            
            streamResponseHandler.handleOpenAIStream(context, stream, options)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "OpenAI流式响应处理完成")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "处理OpenAI流式响应失败")
                }
        }
        
        // 处理Anthropic流式响应
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.STREAM_HANDLE_ANTHROPIC) { message ->
            val context = message.body().getValue("context") as? RoutingContext
            val stream = message.body().getValue("stream") as? ReadStream<Buffer>
            val options = message.body().getJsonObject("options", JsonObject())
            
            if (context == null || stream == null) {
                sendError(message, 400, "缺少必要参数")
                return@consumer
            }
            
            streamResponseHandler.handleAnthropicStream(context, stream, options)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "Anthropic流式响应处理完成")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "处理Anthropic流式响应失败")
                }
        }
        
        // 处理通用流式响应
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.STREAM_HANDLE_GENERIC) { message ->
            val context = message.body().getValue("context") as? RoutingContext
            val stream = message.body().getValue("stream") as? ReadStream<Buffer>
            val options = message.body().getJsonObject("options", JsonObject())
            
            if (context == null || stream == null) {
                sendError(message, 400, "缺少必要参数")
                return@consumer
            }
            
            streamResponseHandler.handleGenericStream(context, stream, options)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "通用流式响应处理完成")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "处理通用流式响应失败")
                }
        }
        
        // 获取流式响应统计信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.STREAM_STATS_GET) { message ->
            val stats = streamResponseHandler.getStats()
            sendSuccess(message, stats)
        }
    }
}
