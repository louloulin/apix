package com.louloulin.apix.plugins.logging

import com.louloulin.apix.core.logging.StructuredLogger
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * 结构化日志插件测试
 */
@ExtendWith(VertxExtension::class)
class StructuredLoggerPluginTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private val testPort = 8888
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(testPort)
        )
        
        testContext.completeNow()
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    /**
     * 测试基本日志记录
     */
    @Test
    fun testBasicLogging(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("logRequest", true)
            .put("logResponse", true)
            .put("logHeaders", true)
            .put("captureRequestBody", true)
            .put("captureResponseBody", true)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-structured-logger", "structured-logger", config)
        val plugin = StructuredLoggerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            // 获取请求 ID
            val requestId = context.get<String>("requestId")
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("requestId", requestId)
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送 GET 请求
                    webClient.get("/test")
                        .putHeader("X-Test-Header", "test-value")
                        .send()
                        .onComplete { getAr ->
                            if (getAr.succeeded()) {
                                val getResponse = getAr.result()
                                testContext.verify {
                                    assert(getResponse.statusCode() == 200) { "Expected status code 200 but got ${getResponse.statusCode()}" }
                                    val body = getResponse.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    assert(body.getString("requestId") != null) { "Expected requestId to be not null" }
                                }
                                
                                // 发送 POST 请求
                                webClient.post("/test")
                                    .putHeader("Content-Type", "application/json")
                                    .sendJsonObject(JsonObject()
                                        .put("name", "test")
                                        .put("value", 123)
                                    )
                                    .onComplete { postAr ->
                                        if (postAr.succeeded()) {
                                            val postResponse = postAr.result()
                                            testContext.verify {
                                                assert(postResponse.statusCode() == 200) { "Expected status code 200 but got ${postResponse.statusCode()}" }
                                                val body = postResponse.bodyAsJsonObject()
                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                assert(body.getString("requestId") != null) { "Expected requestId to be not null" }
                                                testContext.completeNow()
                                            }
                                        } else {
                                            testContext.failNow(postAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(getAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试敏感头过滤
     */
    @Test
    fun testSensitiveHeadersFiltering(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("logRequest", true)
            .put("logResponse", true)
            .put("logHeaders", true)
            .put("sensitiveHeaders", JsonArray()
                .add("authorization")
                .add("x-api-key")
                .add("custom-sensitive-header")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-structured-logger", "structured-logger", config)
        val plugin = StructuredLoggerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .putHeader("X-Response-Header", "response-value")
                .putHeader("Set-Cookie", "session=123; Path=/; HttpOnly")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/test")
                        .putHeader("Authorization", "Bearer token123")
                        .putHeader("X-API-Key", "api-key-123")
                        .putHeader("Custom-Sensitive-Header", "sensitive-value")
                        .putHeader("X-Test-Header", "test-value")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(responseAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试 MDC 上下文
     */
    @Test
    fun testMdcContext(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("logRequest", true)
            .put("logResponse", true)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-structured-logger", "structured-logger", config)
        val plugin = StructuredLoggerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            // 设置 MDC 上下文
            StructuredLogger.setContext("userId", "user123")
            StructuredLogger.setContext("sessionId", "session456")
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/test")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(responseAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
