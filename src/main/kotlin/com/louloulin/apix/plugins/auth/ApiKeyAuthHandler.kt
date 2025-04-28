package com.louloulin.apix.plugins.auth

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * API Key 认证处理器
 */
class ApiKeyAuthHandler(
    private val vertx: Vertx,
    private val requiredScopes: List<String> = emptyList(),
    private val headerName: String = "X-API-Key"
) : Handler<RoutingContext> {
    private val logger = LoggerFactory.getLogger(ApiKeyAuthHandler::class.java)
    
    override fun handle(ctx: RoutingContext) {
        // 从请求头中获取 API Key
        val apiKey = ctx.request().getHeader(headerName)
        
        if (apiKey.isNullOrBlank()) {
            // 从查询参数中获取 API Key
            val apiKeyParam = ctx.request().getParam("apiKey")
            
            if (apiKeyParam.isNullOrBlank()) {
                // API Key 不存在
                ctx.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "API Key is required")
                        .put("status", 401)
                        .encode()
                    )
                return
            }
            
            validateApiKey(ctx, apiKeyParam)
        } else {
            validateApiKey(ctx, apiKey)
        }
    }
    
    /**
     * 验证 API Key
     */
    private fun validateApiKey(ctx: RoutingContext, apiKey: String) {
        // 通过 EventBus 验证 API Key
        val message = JsonObject()
            .put("apiKey", apiKey)
            .put("requiredScopes", JsonArray(requiredScopes))
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AUTH_VALIDATE_API_KEY, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                if (response.getBoolean("success", false)) {
                    // API Key 验证成功
                    val result = response.getJsonObject("result")
                    
                    // 将 API Key 信息添加到上下文中
                    ctx.put("apiKey", result.getJsonObject("apiKey"))
                    
                    // 继续处理请求
                    ctx.next()
                } else {
                    // API Key 验证失败
                    val errorCode = response.getInteger("errorCode", 401)
                    val errorMessage = response.getString("error", "Invalid API Key")
                    
                    ctx.response()
                        .setStatusCode(errorCode)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", errorMessage)
                            .put("status", errorCode)
                            .encode()
                        )
                }
            } else {
                // 验证过程中出错
                logger.error("Error validating API Key", ar.cause())
                
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Internal server error")
                        .put("status", 500)
                        .encode()
                    )
            }
        }
    }
    
    companion object {
        /**
         * 创建 API Key 认证处理器
         */
        fun create(vertx: Vertx, requiredScopes: List<String> = emptyList(), headerName: String = "X-API-Key"): ApiKeyAuthHandler {
            return ApiKeyAuthHandler(vertx, requiredScopes, headerName)
        }
    }
}
