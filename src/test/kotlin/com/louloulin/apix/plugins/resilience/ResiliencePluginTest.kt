package com.louloulin.apix.plugins.resilience

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * 请求重试和熔断插件测试
 */
@ExtendWith(VertxExtension::class)
class ResiliencePluginTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private val testPort = 8888
    private val mockPort = 8889
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(testPort)
        )
        
        // 启动模拟服务器
        startMockServer(testContext)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    /**
     * 启动模拟服务器
     */
    private fun startMockServer(testContext: VertxTestContext) {
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 请求计数器
        val requestCount = AtomicInteger(0)
        
        // 添加成功接口
        router.get("/success").handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("count", requestCount.incrementAndGet())
                    .encode()
                )
        }
        
        // 添加失败接口
        router.get("/failure").handler { context ->
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Internal Server Error")
                    .put("count", requestCount.incrementAndGet())
                    .encode()
                )
        }
        
        // 添加间歇性失败接口
        router.get("/intermittent").handler { context ->
            val count = requestCount.incrementAndGet()
            
            if (count % 2 == 0) {
                // 偶数请求成功
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", true)
                        .put("count", count)
                        .encode()
                    )
            } else {
                // 奇数请求失败
                context.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Internal Server Error")
                        .put("count", count)
                        .encode()
                    )
            }
        }
        
        // 添加超时接口
        router.get("/timeout").handler { context ->
            // 延迟 2 秒后响应
            vertx.setTimer(2000) {
                context.response()
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", true)
                        .put("count", requestCount.incrementAndGet())
                        .encode()
                    )
            }
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(mockPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    /**
     * 测试请求重试
     */
    @Test
    fun testRetry(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("retry", JsonObject()
                .put("enabled", true)
                .put("maxAttempts", 3)
                .put("delay", 100)
                .put("retryOn", JsonArray().add("5xx"))
            )
            .put("circuitBreaker", JsonObject()
                .put("enabled", false)
            )
            .put("timeout", JsonObject()
                .put("enabled", false)
            )
            .put("fallback", JsonObject()
                .put("enabled", true)
                .put("statusCode", 503)
                .put("body", JsonObject()
                    .put("error", "Service Unavailable")
                    .put("message", "Fallback response")
                    .encode()
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-resilience", "resilience", config)
        val plugin = ResiliencePlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加测试处理器
        router.get("/test-retry").handler { context ->
            // 设置目标 URL
            context.put("targetUrl", "http://localhost:$mockPort/intermittent")
            
            // 执行插件
            plugin.execute(context).onComplete { ar ->
                if (ar.failed()) {
                    testContext.failNow(ar.cause())
                }
            }
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/test-retry")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    
                                    // 验证请求计数为 2（第一次失败，第二次成功）
                                    val count = body.getInteger("count")
                                    assert(count == 2) { "Expected count to be 2 but got $count" }
                                    
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
     * 测试熔断器
     */
    @Test
    fun testCircuitBreaker(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("retry", JsonObject()
                .put("enabled", false)
            )
            .put("circuitBreaker", JsonObject()
                .put("enabled", true)
                .put("failureThreshold", 50)
                .put("requestVolumeThreshold", 2)
                .put("windowSizeInMillis", 10000)
                .put("sleepWindowInMillis", 1000)
            )
            .put("timeout", JsonObject()
                .put("enabled", false)
            )
            .put("fallback", JsonObject()
                .put("enabled", true)
                .put("statusCode", 503)
                .put("body", JsonObject()
                    .put("error", "Service Unavailable")
                    .put("message", "Circuit breaker is open")
                    .encode()
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-resilience", "resilience", config)
        val plugin = ResiliencePlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加测试处理器
        router.get("/test-circuit-breaker").handler { context ->
            // 设置目标 URL
            context.put("targetUrl", "http://localhost:$mockPort/failure")
            
            // 执行插件
            plugin.execute(context).onComplete { ar ->
                if (ar.failed()) {
                    testContext.failNow(ar.cause())
                }
            }
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送第一个请求（失败）
                    webClient.get("/test-circuit-breaker")
                        .send()
                        .onComplete { firstAr ->
                            if (firstAr.succeeded()) {
                                val firstResponse = firstAr.result()
                                testContext.verify {
                                    assert(firstResponse.statusCode() == 503) { "Expected status code 503 but got ${firstResponse.statusCode()}" }
                                }
                                
                                // 发送第二个请求（失败）
                                webClient.get("/test-circuit-breaker")
                                    .send()
                                    .onComplete { secondAr ->
                                        if (secondAr.succeeded()) {
                                            val secondResponse = secondAr.result()
                                            testContext.verify {
                                                assert(secondResponse.statusCode() == 503) { "Expected status code 503 but got ${secondResponse.statusCode()}" }
                                            }
                                            
                                            // 发送第三个请求（熔断器应该打开）
                                            webClient.get("/test-circuit-breaker")
                                                .send()
                                                .onComplete { thirdAr ->
                                                    if (thirdAr.succeeded()) {
                                                        val thirdResponse = thirdAr.result()
                                                        testContext.verify {
                                                            assert(thirdResponse.statusCode() == 503) { "Expected status code 503 but got ${thirdResponse.statusCode()}" }
                                                            
                                                            val body = thirdResponse.bodyAsJsonObject()
                                                            assert(body.getString("error") == "Service Unavailable") { "Expected error to be Service Unavailable" }
                                                            assert(body.getString("message") == "Circuit breaker is open") { "Expected message to be Circuit breaker is open" }
                                                            
                                                            testContext.completeNow()
                                                        }
                                                    } else {
                                                        testContext.failNow(thirdAr.cause())
                                                    }
                                                }
                                        } else {
                                            testContext.failNow(secondAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(firstAr.cause())
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
     * 测试超时
     */
    @Test
    fun testTimeout(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("retry", JsonObject()
                .put("enabled", false)
            )
            .put("circuitBreaker", JsonObject()
                .put("enabled", false)
            )
            .put("timeout", JsonObject()
                .put("enabled", true)
                .put("timeoutInMillis", 1000)
            )
            .put("fallback", JsonObject()
                .put("enabled", true)
                .put("statusCode", 504)
                .put("body", JsonObject()
                    .put("error", "Gateway Timeout")
                    .put("message", "Request timed out")
                    .encode()
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-resilience", "resilience", config)
        val plugin = ResiliencePlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加测试处理器
        router.get("/test-timeout").handler { context ->
            // 设置目标 URL
            context.put("targetUrl", "http://localhost:$mockPort/timeout")
            
            // 执行插件
            plugin.execute(context).onComplete { ar ->
                if (ar.failed()) {
                    testContext.failNow(ar.cause())
                }
            }
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/test-timeout")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 504) { "Expected status code 504 but got ${response.statusCode()}" }
                                    
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getString("error") == "Gateway Timeout") { "Expected error to be Gateway Timeout" }
                                    assert(body.getString("message") == "Request timed out") { "Expected message to be Request timed out" }
                                    
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
     * 测试后备响应
     */
    @Test
    fun testFallback(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("retry", JsonObject()
                .put("enabled", false)
            )
            .put("circuitBreaker", JsonObject()
                .put("enabled", false)
            )
            .put("timeout", JsonObject()
                .put("enabled", false)
            )
            .put("fallback", JsonObject()
                .put("enabled", true)
                .put("statusCode", 503)
                .put("body", JsonObject()
                    .put("error", "Service Unavailable")
                    .put("message", "Custom fallback response")
                    .encode()
                )
                .put("headers", JsonObject()
                    .put("X-Fallback", "true")
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-resilience", "resilience", config)
        val plugin = ResiliencePlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加测试处理器
        router.get("/test-fallback").handler { context ->
            // 设置目标 URL
            context.put("targetUrl", "http://localhost:$mockPort/failure")
            
            // 执行插件
            plugin.execute(context).onComplete { ar ->
                if (ar.failed()) {
                    testContext.failNow(ar.cause())
                }
            }
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/test-fallback")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 503) { "Expected status code 503 but got ${response.statusCode()}" }
                                    
                                    // 验证响应头
                                    val fallbackHeader = response.getHeader("X-Fallback")
                                    assert(fallbackHeader == "true") { "Expected X-Fallback header to be true" }
                                    
                                    // 验证响应体
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getString("error") == "Service Unavailable") { "Expected error to be Service Unavailable" }
                                    assert(body.getString("message") == "Custom fallback response") { "Expected message to be Custom fallback response" }
                                    
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
