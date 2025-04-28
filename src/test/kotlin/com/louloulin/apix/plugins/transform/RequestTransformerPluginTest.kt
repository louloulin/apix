package com.louloulin.apix.plugins.transform

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
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
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * 请求转换插件测试
 */
@ExtendWith(VertxExtension::class)
class RequestTransformerPluginTest {
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
     * 测试请求头转换
     */
    @Test
    fun testHeaderTransformation(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("headers", JsonObject()
                .put("add", JsonObject()
                    .put("X-Added-Header", "added-value")
                )
                .put("remove", JsonArray()
                    .add("X-Remove-Header")
                )
                .put("rename", JsonObject()
                    .put("X-Old-Header", "X-New-Header")
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-transformer", "requestTransformer", config)
        val plugin = RequestTransformerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            val headers = JsonObject()
            context.request().headers().forEach { header ->
                headers.put(header.key, header.value)
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("headers", headers)
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
                        .putHeader("X-Remove-Header", "remove-value")
                        .putHeader("X-Old-Header", "old-value")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    
                                    val headers = body.getJsonObject("headers")
                                    assert(headers.getString("X-Added-Header") == "added-value") { "Expected X-Added-Header to be added-value" }
                                    assert(!headers.containsKey("X-Remove-Header")) { "Expected X-Remove-Header to be removed" }
                                    assert(!headers.containsKey("X-Old-Header")) { "Expected X-Old-Header to be removed" }
                                    assert(headers.getString("X-New-Header") == "old-value") { "Expected X-New-Header to be old-value" }
                                    
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
     * 测试查询参数转换
     */
    @Test
    fun testQueryParamTransformation(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("queryParams", JsonObject()
                .put("add", JsonObject()
                    .put("added", "added-value")
                )
                .put("remove", JsonArray()
                    .add("remove")
                )
                .put("rename", JsonObject()
                    .put("old", "new")
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-transformer", "requestTransformer", config)
        val plugin = RequestTransformerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            val params = JsonObject()
            context.queryParams().forEach { param ->
                params.put(param.key, param.value)
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("params", params)
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
                    webClient.get("/test?remove=remove-value&old=old-value")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    
                                    val params = body.getJsonObject("params")
                                    assert(params.getString("added") == "added-value") { "Expected added to be added-value" }
                                    assert(!params.containsKey("remove")) { "Expected remove to be removed" }
                                    assert(!params.containsKey("old")) { "Expected old to be removed" }
                                    assert(params.getString("new") == "old-value") { "Expected new to be old-value" }
                                    
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
     * 测试请求体转换（JSON 转表单）
     */
    @Test
    fun testBodyTransformationJsonToForm(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("body", JsonObject()
                .put("type", "json_to_form")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-transformer", "requestTransformer", config)
        val plugin = RequestTransformerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            val contentType = context.request().getHeader("Content-Type")
            val body = context.body().asString()
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("contentType", contentType)
                    .put("body", body)
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
                    val jsonBody = JsonObject()
                        .put("name", "test")
                        .put("value", 123)
                        .put("nested", JsonObject()
                            .put("key", "value")
                        )
                    
                    webClient.post("/test")
                        .putHeader("Content-Type", "application/json")
                        .sendBuffer(Buffer.buffer(jsonBody.encode()))
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    
                                    val contentType = body.getString("contentType")
                                    assert(contentType == "application/x-www-form-urlencoded") { "Expected Content-Type to be application/x-www-form-urlencoded but got $contentType" }
                                    
                                    val formBody = body.getString("body")
                                    assert(formBody.contains("name=test")) { "Expected form body to contain name=test" }
                                    assert(formBody.contains("value=123")) { "Expected form body to contain value=123" }
                                    assert(formBody.contains("nested[key]=value")) { "Expected form body to contain nested[key]=value" }
                                    
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
     * 测试请求体转换（表单转 JSON）
     */
    @Test
    fun testBodyTransformationFormToJson(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("body", JsonObject()
                .put("type", "form_to_json")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-transformer", "requestTransformer", config)
        val plugin = RequestTransformerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            val contentType = context.request().getHeader("Content-Type")
            val body = context.body().asJsonObject()
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("contentType", contentType)
                    .put("body", body)
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
                    val formParams = listOf(
                        "name=test",
                        "value=123",
                        "nested[key]=value"
                    ).joinToString("&")
                    
                    webClient.post("/test")
                        .putHeader("Content-Type", "application/x-www-form-urlencoded")
                        .sendBuffer(Buffer.buffer(formParams))
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    
                                    val contentType = body.getString("contentType")
                                    assert(contentType == "application/json") { "Expected Content-Type to be application/json but got $contentType" }
                                    
                                    val jsonBody = body.getJsonObject("body")
                                    assert(jsonBody.getString("name") == "test") { "Expected name to be test but got ${jsonBody.getString("name")}" }
                                    assert(jsonBody.getString("value") == "123") { "Expected value to be 123 but got ${jsonBody.getString("value")}" }
                                    
                                    val nested = jsonBody.getJsonObject("nested")
                                    assert(nested != null) { "Expected nested to be not null" }
                                    assert(nested.getString("key") == "value") { "Expected nested.key to be value but got ${nested.getString("key")}" }
                                    
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
     * 测试条件转换
     */
    @Test
    fun testConditionalTransformation(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("headers", JsonObject()
                .put("add", JsonObject()
                    .put("X-Conditional-Header", "conditional-value")
                )
            )
            .put("conditions", JsonArray()
                .add(JsonObject()
                    .put("method", "POST")
                )
                .add(JsonObject()
                    .put("header", "X-Test-Header")
                    .put("value", "test-value")
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-transformer", "requestTransformer", config)
        val plugin = RequestTransformerPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            val headers = JsonObject()
            context.request().headers().forEach { header ->
                headers.put(header.key, header.value)
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("headers", headers)
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送不满足条件的请求
                    webClient.get("/test")
                        .putHeader("X-Test-Header", "test-value")
                        .send()
                        .onComplete { notMatchAr ->
                            if (notMatchAr.succeeded()) {
                                val notMatchResponse = notMatchAr.result()
                                testContext.verify {
                                    assert(notMatchResponse.statusCode() == 200) { "Expected status code 200 but got ${notMatchResponse.statusCode()}" }
                                    val body = notMatchResponse.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    
                                    val headers = body.getJsonObject("headers")
                                    assert(!headers.containsKey("X-Conditional-Header")) { "Expected X-Conditional-Header to not be added" }
                                }
                                
                                // 发送满足条件的请求
                                webClient.post("/test")
                                    .putHeader("X-Test-Header", "test-value")
                                    .send()
                                    .onComplete { matchAr ->
                                        if (matchAr.succeeded()) {
                                            val matchResponse = matchAr.result()
                                            testContext.verify {
                                                assert(matchResponse.statusCode() == 200) { "Expected status code 200 but got ${matchResponse.statusCode()}" }
                                                val body = matchResponse.bodyAsJsonObject()
                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                
                                                val headers = body.getJsonObject("headers")
                                                assert(headers.getString("X-Conditional-Header") == "conditional-value") { "Expected X-Conditional-Header to be conditional-value" }
                                                
                                                testContext.completeNow()
                                            }
                                        } else {
                                            testContext.failNow(matchAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(notMatchAr.cause())
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
