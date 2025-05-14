package com.louloulin.apix.scaling.stateless

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.common.Constants
import java.util.UUID

/**
 * 无状态请求处理器
 * 
 * 确保请求处理不依赖于本地状态，支持水平扩展
 */
class StatelessRequestProcessor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(StatelessRequestProcessor::class.java)
    
    // 分布式会话存储
    private val sessionStore: DistributedSessionStore = DistributedSessionStore(vertx)
    
    /**
     * 处理请求
     * 
     * @param context 路由上下文
     * @return 包含处理结果的Future
     */
    fun processRequest(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 创建请求上下文，包含所有必要信息
            val requestContext = createRequestContext(context)
            
            // 处理会话
            handleSession(requestContext)
                .compose { sessionId ->
                    // 添加会话ID到请求上下文
                    requestContext.put("sessionId", sessionId)
                    
                    // 处理请求
                    processRequestWithContext(requestContext)
                }
                .onSuccess { result ->
                    // 将结果写入响应
                    writeResponse(context, result)
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("处理请求失败", err)
                    context.fail(err)
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("处理请求失败", e)
            context.fail(e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 创建请求上下文
     * 
     * @param context 路由上下文
     * @return 包含所有必要信息的请求上下文
     */
    private fun createRequestContext(context: RoutingContext): JsonObject {
        val request = context.request()
        val requestContext = JsonObject()
        
        // 添加请求信息
        requestContext.put("method", request.method().name())
        requestContext.put("path", request.path())
        requestContext.put("uri", request.uri())
        requestContext.put("absoluteURI", request.absoluteURI())
        requestContext.put("remoteAddress", request.remoteAddress().toString())
        
        // 添加请求头
        val headers = JsonObject()
        for (header in request.headers()) {
            headers.put(header.key, header.value)
        }
        requestContext.put("headers", headers)
        
        // 添加请求参数
        val params = JsonObject()
        for (param in request.params()) {
            params.put(param.key, param.value)
        }
        requestContext.put("params", params)
        
        // 添加请求体
        if (context.body() != null) {
            requestContext.put("body", context.body().asString())
        }
        
        // 添加请求ID
        val requestId = UUID.randomUUID().toString()
        requestContext.put("requestId", requestId)
        
        // 添加时间戳
        requestContext.put("timestamp", System.currentTimeMillis())
        
        return requestContext
    }
    
    /**
     * 处理会话
     * 
     * @param requestContext 请求上下文
     * @return 包含会话ID的Future
     */
    private fun handleSession(requestContext: JsonObject): Future<String> {
        val promise = Promise.promise<String>()
        
        // 从请求头中获取会话ID
        val headers = requestContext.getJsonObject("headers", JsonObject())
        var sessionId = headers.getString(Constants.SESSION_ID_HEADER)
        
        if (sessionId == null || sessionId.isEmpty()) {
            // 如果没有会话ID，创建新会话
            sessionId = UUID.randomUUID().toString()
            
            // 创建新会话
            sessionStore.createSession(sessionId, JsonObject())
                .onSuccess {
                    promise.complete(sessionId)
                }
                .onFailure { err ->
                    logger.error("创建会话失败", err)
                    promise.fail(err)
                }
        } else {
            // 如果有会话ID，获取会话
            sessionStore.getSession(sessionId)
                .onSuccess { session ->
                    if (session != null) {
                        // 会话存在，更新会话访问时间
                        sessionStore.updateSessionAccessTime(sessionId)
                            .onSuccess {
                                promise.complete(sessionId)
                            }
                            .onFailure { err ->
                                logger.error("更新会话访问时间失败", err)
                                promise.fail(err)
                            }
                    } else {
                        // 会话不存在，创建新会话
                        sessionId = UUID.randomUUID().toString()
                        sessionStore.createSession(sessionId, JsonObject())
                            .onSuccess {
                                promise.complete(sessionId)
                            }
                            .onFailure { err ->
                                logger.error("创建会话失败", err)
                                promise.fail(err)
                            }
                    }
                }
                .onFailure { err ->
                    logger.error("获取会话失败", err)
                    promise.fail(err)
                }
        }
        
        return promise.future()
    }
    
    /**
     * 使用上下文处理请求
     * 
     * @param requestContext 请求上下文
     * @return 包含处理结果的Future
     */
    private fun processRequestWithContext(requestContext: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 根据请求路径选择处理器
        val path = requestContext.getString("path")
        val method = requestContext.getString("method")
        
        // 构建路由键
        val routeKey = "$method:$path"
        
        // 发送请求到适当的处理器
        vertx.eventBus().request<JsonObject>(getHandlerAddress(routeKey), requestContext) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                promise.complete(response)
            } else {
                logger.error("处理请求失败", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取处理器地址
     * 
     * @param routeKey 路由键
     * @return 处理器地址
     */
    private fun getHandlerAddress(routeKey: String): String {
        // 这里可以实现路由映射逻辑
        // 简单示例：根据路由键确定处理器地址
        
        // 如果是AI相关请求
        if (routeKey.contains("/ai/") || routeKey.contains("/v1/")) {
            return EventBusAddresses.AI_REQUEST_PROCESSOR
        }
        
        // 如果是管理API请求
        if (routeKey.contains("/admin/")) {
            return EventBusAddresses.ADMIN_REQUEST_PROCESSOR
        }
        
        // 默认处理器
        return EventBusAddresses.DEFAULT_REQUEST_PROCESSOR
    }
    
    /**
     * 将结果写入响应
     * 
     * @param context 路由上下文
     * @param result 处理结果
     */
    private fun writeResponse(context: RoutingContext, result: JsonObject) {
        val response = context.response()
        
        // 设置状态码
        val statusCode = result.getInteger("statusCode", 200)
        response.setStatusCode(statusCode)
        
        // 设置响应头
        val headers = result.getJsonObject("headers", JsonObject())
        for (header in headers.map) {
            response.putHeader(header.key, header.value.toString())
        }
        
        // 设置会话ID
        val sessionId = result.getString("sessionId")
        if (sessionId != null && sessionId.isNotEmpty()) {
            response.putHeader(Constants.SESSION_ID_HEADER, sessionId)
        }
        
        // 设置响应体
        val body = result.getValue("body")
        if (body != null) {
            when (body) {
                is String -> response.end(body)
                is JsonObject -> response.end(body.encode())
                else -> response.end(body.toString())
            }
        } else {
            response.end()
        }
    }
}
