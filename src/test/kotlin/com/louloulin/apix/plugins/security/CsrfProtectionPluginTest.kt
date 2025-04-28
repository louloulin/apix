package com.louloulin.apix.plugins.security

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

/**
 * CSRF 保护插件测试
 */
@ExtendWith(VertxExtension::class)
class CsrfProtectionPluginTest {
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
     * 测试 CSRF 保护（Cookie 存储方式）
     */
    @Test
    fun testCsrfProtectionWithCookieStorage(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("tokenName", "X-CSRF-TOKEN")
            .put("headerName", "X-CSRF-TOKEN")
            .put("paramName", "_csrf")
            .put("cookieName", "XSRF-TOKEN")
            .put("storage", "cookie")
            .put("validationMode", "all")
            .put("excludedMethods", JsonArray()
                .add("GET")
                .add("HEAD")
                .add("OPTIONS")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-csrf-protection", "csrfProtection", config)
        val plugin = CsrfProtectionPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.get("/csrf-token").handler { context ->
            // 获取 CSRF 令牌
            val csrfToken = context.response().headers().get("X-CSRF-TOKEN")
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("csrfToken", csrfToken)
                    .encode()
                )
        }
        
        router.post("/test").handler { context ->
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
                    // 发送 GET 请求，获取 CSRF 令牌
                    webClient.get("/csrf-token")
                        .send()
                        .onComplete { getAr ->
                            if (getAr.succeeded()) {
                                val getResponse = getAr.result()
                                testContext.verify {
                                    assert(getResponse.statusCode() == 200) { "Expected status code 200 but got ${getResponse.statusCode()}" }
                                    
                                    // 获取 CSRF 令牌
                                    val csrfCookie = getResponse.cookies().find { it.startsWith("XSRF-TOKEN=") }
                                    assert(csrfCookie != null) { "Expected XSRF-TOKEN cookie but got none" }
                                    
                                    val csrfToken = csrfCookie!!.substringAfter("XSRF-TOKEN=").substringBefore(";")
                                    
                                    // 发送 POST 请求，不带 CSRF 令牌
                                    webClient.post("/test")
                                        .putHeader("Cookie", csrfCookie)
                                        .send()
                                        .onComplete { invalidAr ->
                                            if (invalidAr.succeeded()) {
                                                val invalidResponse = invalidAr.result()
                                                testContext.verify {
                                                    assert(invalidResponse.statusCode() == 403) { "Expected status code 403 but got ${invalidResponse.statusCode()}" }
                                                    val body = invalidResponse.bodyAsJsonObject()
                                                    assert(body.getString("error") == "CSRF token validation failed") { "Expected error to be CSRF token validation failed but got ${body.getString("error")}" }
                                                }
                                                
                                                // 发送 POST 请求，带有 CSRF 令牌（请求头）
                                                webClient.post("/test")
                                                    .putHeader("Cookie", csrfCookie)
                                                    .putHeader("X-CSRF-TOKEN", csrfToken)
                                                    .send()
                                                    .onComplete { validHeaderAr ->
                                                        if (validHeaderAr.succeeded()) {
                                                            val validHeaderResponse = validHeaderAr.result()
                                                            testContext.verify {
                                                                assert(validHeaderResponse.statusCode() == 200) { "Expected status code 200 but got ${validHeaderResponse.statusCode()}" }
                                                                val body = validHeaderResponse.bodyAsJsonObject()
                                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                            }
                                                            
                                                            // 发送 POST 请求，带有 CSRF 令牌（表单参数）
                                                            webClient.post("/test")
                                                                .putHeader("Cookie", csrfCookie)
                                                                .putHeader("Content-Type", "application/x-www-form-urlencoded")
                                                                .sendForm(io.vertx.core.MultiMap.caseInsensitiveMultiMap().add("_csrf", csrfToken))
                                                                .onComplete { validFormAr ->
                                                                    if (validFormAr.succeeded()) {
                                                                        val validFormResponse = validFormAr.result()
                                                                        testContext.verify {
                                                                            assert(validFormResponse.statusCode() == 200) { "Expected status code 200 but got ${validFormResponse.statusCode()}" }
                                                                            val body = validFormResponse.bodyAsJsonObject()
                                                                            assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                                            testContext.completeNow()
                                                                        }
                                                                    } else {
                                                                        testContext.failNow(validFormAr.cause())
                                                                    }
                                                                }
                                                        } else {
                                                            testContext.failNow(validHeaderAr.cause())
                                                        }
                                                    }
                                            } else {
                                                testContext.failNow(invalidAr.cause())
                                            }
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
     * 测试 CSRF 保护（Session 存储方式）
     */
    @Test
    fun testCsrfProtectionWithSessionStorage(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("tokenName", "X-CSRF-TOKEN")
            .put("headerName", "X-CSRF-TOKEN")
            .put("paramName", "_csrf")
            .put("storage", "session")
            .put("validationMode", "header")
            .put("excludedMethods", JsonArray()
                .add("GET")
                .add("HEAD")
                .add("OPTIONS")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-csrf-protection", "csrfProtection", config)
        val plugin = CsrfProtectionPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.get("/csrf-token").handler { context ->
            // 获取 CSRF 令牌
            val csrfToken = context.response().headers().get("X-CSRF-TOKEN")
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("csrfToken", csrfToken)
                    .encode()
                )
        }
        
        router.post("/test").handler { context ->
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
                    // 发送 GET 请求，获取 CSRF 令牌
                    webClient.get("/csrf-token")
                        .send()
                        .onComplete { getAr ->
                            if (getAr.succeeded()) {
                                val getResponse = getAr.result()
                                testContext.verify {
                                    assert(getResponse.statusCode() == 200) { "Expected status code 200 but got ${getResponse.statusCode()}" }
                                    
                                    // 获取 CSRF 令牌
                                    val csrfToken = getResponse.headers().get("X-CSRF-TOKEN")
                                    assert(csrfToken != null) { "Expected X-CSRF-TOKEN header but got none" }
                                    
                                    // 发送 POST 请求，不带 CSRF 令牌
                                    webClient.post("/test")
                                        .send()
                                        .onComplete { invalidAr ->
                                            if (invalidAr.succeeded()) {
                                                val invalidResponse = invalidAr.result()
                                                testContext.verify {
                                                    assert(invalidResponse.statusCode() == 403) { "Expected status code 403 but got ${invalidResponse.statusCode()}" }
                                                    val body = invalidResponse.bodyAsJsonObject()
                                                    assert(body.getString("error") == "CSRF token validation failed") { "Expected error to be CSRF token validation failed but got ${body.getString("error")}" }
                                                }
                                                
                                                // 发送 POST 请求，带有 CSRF 令牌
                                                webClient.post("/test")
                                                    .putHeader("X-CSRF-TOKEN", csrfToken!!)
                                                    .send()
                                                    .onComplete { validAr ->
                                                        if (validAr.succeeded()) {
                                                            val validResponse = validAr.result()
                                                            testContext.verify {
                                                                assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                                                val body = validResponse.bodyAsJsonObject()
                                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                                testContext.completeNow()
                                                            }
                                                        } else {
                                                            testContext.failNow(validAr.cause())
                                                        }
                                                    }
                                            } else {
                                                testContext.failNow(invalidAr.cause())
                                            }
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
     * 测试 CSRF 保护（排除路径）
     */
    @Test
    fun testCsrfProtectionWithExcludedPaths(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("tokenName", "X-CSRF-TOKEN")
            .put("headerName", "X-CSRF-TOKEN")
            .put("paramName", "_csrf")
            .put("cookieName", "XSRF-TOKEN")
            .put("storage", "cookie")
            .put("validationMode", "all")
            .put("excludedPaths", JsonArray()
                .add("/api/webhook")
            )
            .put("excludedMethods", JsonArray()
                .add("GET")
                .add("HEAD")
                .add("OPTIONS")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-csrf-protection", "csrfProtection", config)
        val plugin = CsrfProtectionPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.post("/test").handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        }
        
        router.post("/api/webhook").handler { context ->
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
                    // 发送 POST 请求到排除的路径
                    webClient.post("/api/webhook")
                        .send()
                        .onComplete { excludedAr ->
                            if (excludedAr.succeeded()) {
                                val excludedResponse = excludedAr.result()
                                testContext.verify {
                                    assert(excludedResponse.statusCode() == 200) { "Expected status code 200 but got ${excludedResponse.statusCode()}" }
                                    val body = excludedResponse.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                }
                                
                                // 发送 POST 请求到非排除的路径
                                webClient.post("/test")
                                    .send()
                                    .onComplete { notExcludedAr ->
                                        if (notExcludedAr.succeeded()) {
                                            val notExcludedResponse = notExcludedAr.result()
                                            testContext.verify {
                                                assert(notExcludedResponse.statusCode() == 403) { "Expected status code 403 but got ${notExcludedResponse.statusCode()}" }
                                                val body = notExcludedResponse.bodyAsJsonObject()
                                                assert(body.getString("error") == "CSRF token validation failed") { "Expected error to be CSRF token validation failed but got ${body.getString("error")}" }
                                                testContext.completeNow()
                                            }
                                        } else {
                                            testContext.failNow(notExcludedAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(excludedAr.cause())
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
