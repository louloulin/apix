package com.louloulin.apix.plugins.validation

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
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
 * 请求参数验证插件测试
 */
@ExtendWith(VertxExtension::class)
class RequestValidatorPluginTest {
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
     * 测试查询参数验证
     */
    @Test
    fun testQueryParameterValidation(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("parameters", JsonArray()
                .add(JsonObject()
                    .put("name", "id")
                    .put("in", "query")
                    .put("required", true)
                    .put("type", "integer")
                    .put("minimum", 1)
                    .put("maximum", 100)
                )
                .add(JsonObject()
                    .put("name", "name")
                    .put("in", "query")
                    .put("required", true)
                    .put("type", "string")
                    .put("minLength", 3)
                    .put("maxLength", 50)
                )
                .add(JsonObject()
                    .put("name", "status")
                    .put("in", "query")
                    .put("required", false)
                    .put("type", "string")
                    .put("enum", JsonArray()
                        .add("active")
                        .add("inactive")
                        .add("pending")
                    )
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-validator", "request-validator", config)
        val plugin = RequestValidatorPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("id", context.request().getParam("id"))
                    .put("name", context.request().getParam("name"))
                    .put("status", context.request().getParam("status"))
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 测试缺少必填参数
                    webClient.get("/?id=10")
                        .send()
                        .onComplete { missingAr ->
                            if (missingAr.succeeded()) {
                                val missingResponse = missingAr.result()
                                testContext.verify {
                                    assert(missingResponse.statusCode() == 400) { "Expected status code 400 but got ${missingResponse.statusCode()}" }
                                    val body = missingResponse.bodyAsJsonObject()
                                    assert(body.getString("error") == "Validation failed") { "Expected error message 'Validation failed' but got '${body.getString("error")}'" }
                                    assert(body.getString("parameter") == "name") { "Expected parameter 'name' but got '${body.getString("parameter")}'" }
                                }
                                
                                // 测试参数类型错误
                                webClient.get("/?id=abc&name=test")
                                    .send()
                                    .onComplete { typeAr ->
                                        if (typeAr.succeeded()) {
                                            val typeResponse = typeAr.result()
                                            testContext.verify {
                                                assert(typeResponse.statusCode() == 400) { "Expected status code 400 but got ${typeResponse.statusCode()}" }
                                                val body = typeResponse.bodyAsJsonObject()
                                                assert(body.getString("parameter") == "id") { "Expected parameter 'id' but got '${body.getString("parameter")}'" }
                                            }
                                            
                                            // 测试参数范围错误
                                            webClient.get("/?id=0&name=test")
                                                .send()
                                                .onComplete { rangeAr ->
                                                    if (rangeAr.succeeded()) {
                                                        val rangeResponse = rangeAr.result()
                                                        testContext.verify {
                                                            assert(rangeResponse.statusCode() == 400) { "Expected status code 400 but got ${rangeResponse.statusCode()}" }
                                                            val body = rangeResponse.bodyAsJsonObject()
                                                            assert(body.getString("parameter") == "id") { "Expected parameter 'id' but got '${body.getString("parameter")}'" }
                                                        }
                                                        
                                                        // 测试参数长度错误
                                                        webClient.get("/?id=10&name=ab")
                                                            .send()
                                                            .onComplete { lengthAr ->
                                                                if (lengthAr.succeeded()) {
                                                                    val lengthResponse = lengthAr.result()
                                                                    testContext.verify {
                                                                        assert(lengthResponse.statusCode() == 400) { "Expected status code 400 but got ${lengthResponse.statusCode()}" }
                                                                        val body = lengthResponse.bodyAsJsonObject()
                                                                        assert(body.getString("parameter") == "name") { "Expected parameter 'name' but got '${body.getString("parameter")}'" }
                                                                    }
                                                                    
                                                                    // 测试枚举值错误
                                                                    webClient.get("/?id=10&name=test&status=unknown")
                                                                        .send()
                                                                        .onComplete { enumAr ->
                                                                            if (enumAr.succeeded()) {
                                                                                val enumResponse = enumAr.result()
                                                                                testContext.verify {
                                                                                    assert(enumResponse.statusCode() == 400) { "Expected status code 400 but got ${enumResponse.statusCode()}" }
                                                                                    val body = enumResponse.bodyAsJsonObject()
                                                                                    assert(body.getString("parameter") == "status") { "Expected parameter 'status' but got '${body.getString("parameter")}'" }
                                                                                }
                                                                                
                                                                                // 测试有效请求
                                                                                webClient.get("/?id=10&name=test&status=active")
                                                                                    .send()
                                                                                    .onComplete { validAr ->
                                                                                        if (validAr.succeeded()) {
                                                                                            val validResponse = validAr.result()
                                                                                            testContext.verify {
                                                                                                assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                                                                                val body = validResponse.bodyAsJsonObject()
                                                                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                                                                assert(body.getString("id") == "10") { "Expected id to be 10 but got ${body.getString("id")}" }
                                                                                                assert(body.getString("name") == "test") { "Expected name to be test but got ${body.getString("name")}" }
                                                                                                assert(body.getString("status") == "active") { "Expected status to be active but got ${body.getString("status")}" }
                                                                                                testContext.completeNow()
                                                                                            }
                                                                                        } else {
                                                                                            testContext.failNow(validAr.cause())
                                                                                        }
                                                                                    }
                                                                            } else {
                                                                                testContext.failNow(enumAr.cause())
                                                                            }
                                                                        }
                                                                } else {
                                                                    testContext.failNow(lengthAr.cause())
                                                                }
                                                            }
                                                    } else {
                                                        testContext.failNow(rangeAr.cause())
                                                    }
                                                }
                                        } else {
                                            testContext.failNow(typeAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(missingAr.cause())
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
     * 测试请求体参数验证
     */
    @Test
    fun testBodyParameterValidation(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("parameters", JsonArray()
                .add(JsonObject()
                    .put("name", "user")
                    .put("in", "body")
                    .put("required", true)
                    .put("type", "object")
                    .put("properties", JsonObject()
                        .put("id", JsonObject()
                            .put("type", "integer")
                            .put("required", true)
                            .put("minimum", 1)
                        )
                        .put("name", JsonObject()
                            .put("type", "string")
                            .put("required", true)
                            .put("minLength", 3)
                        )
                        .put("email", JsonObject()
                            .put("type", "string")
                            .put("required", true)
                            .put("format", "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")
                        )
                        .put("roles", JsonObject()
                            .put("type", "array")
                            .put("required", false)
                            .put("items", JsonObject()
                                .put("type", "string")
                                .put("enum", JsonArray()
                                    .add("admin")
                                    .add("user")
                                    .add("guest")
                                )
                            )
                        )
                    )
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-validator", "request-validator", config)
        val plugin = RequestValidatorPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("user", context.body().asJsonObject().getJsonObject("user"))
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 测试缺少必填参数
                    webClient.post("/")
                        .sendJsonObject(JsonObject())
                        .onComplete { missingAr ->
                            if (missingAr.succeeded()) {
                                val missingResponse = missingAr.result()
                                testContext.verify {
                                    assert(missingResponse.statusCode() == 400) { "Expected status code 400 but got ${missingResponse.statusCode()}" }
                                    val body = missingResponse.bodyAsJsonObject()
                                    assert(body.getString("parameter") == "user") { "Expected parameter 'user' but got '${body.getString("parameter")}'" }
                                }
                                
                                // 测试缺少必填属性
                                webClient.post("/")
                                    .sendJsonObject(JsonObject()
                                        .put("user", JsonObject()
                                            .put("id", 1)
                                        )
                                    )
                                    .onComplete { propertyAr ->
                                        if (propertyAr.succeeded()) {
                                            val propertyResponse = propertyAr.result()
                                            testContext.verify {
                                                assert(propertyResponse.statusCode() == 400) { "Expected status code 400 but got ${propertyResponse.statusCode()}" }
                                                val body = propertyResponse.bodyAsJsonObject()
                                                assert(body.getString("parameter") == "user.name") { "Expected parameter 'user.name' but got '${body.getString("parameter")}'" }
                                            }
                                            
                                            // 测试属性格式错误
                                            webClient.post("/")
                                                .sendJsonObject(JsonObject()
                                                    .put("user", JsonObject()
                                                        .put("id", 1)
                                                        .put("name", "test")
                                                        .put("email", "invalid-email")
                                                    )
                                                )
                                                .onComplete { formatAr ->
                                                    if (formatAr.succeeded()) {
                                                        val formatResponse = formatAr.result()
                                                        testContext.verify {
                                                            assert(formatResponse.statusCode() == 400) { "Expected status code 400 but got ${formatResponse.statusCode()}" }
                                                            val body = formatResponse.bodyAsJsonObject()
                                                            assert(body.getString("parameter") == "user.email") { "Expected parameter 'user.email' but got '${body.getString("parameter")}'" }
                                                        }
                                                        
                                                        // 测试数组元素错误
                                                        webClient.post("/")
                                                            .sendJsonObject(JsonObject()
                                                                .put("user", JsonObject()
                                                                    .put("id", 1)
                                                                    .put("name", "test")
                                                                    .put("email", "test@example.com")
                                                                    .put("roles", JsonArray()
                                                                        .add("admin")
                                                                        .add("invalid-role")
                                                                    )
                                                                )
                                                            )
                                                            .onComplete { arrayAr ->
                                                                if (arrayAr.succeeded()) {
                                                                    val arrayResponse = arrayAr.result()
                                                                    testContext.verify {
                                                                        assert(arrayResponse.statusCode() == 400) { "Expected status code 400 but got ${arrayResponse.statusCode()}" }
                                                                        val body = arrayResponse.bodyAsJsonObject()
                                                                        assert(body.getString("parameter") == "user.roles[1]") { "Expected parameter 'user.roles[1]' but got '${body.getString("parameter")}'" }
                                                                    }
                                                                    
                                                                    // 测试有效请求
                                                                    webClient.post("/")
                                                                        .sendJsonObject(JsonObject()
                                                                            .put("user", JsonObject()
                                                                                .put("id", 1)
                                                                                .put("name", "test")
                                                                                .put("email", "test@example.com")
                                                                                .put("roles", JsonArray()
                                                                                    .add("admin")
                                                                                    .add("user")
                                                                                )
                                                                            )
                                                                        )
                                                                        .onComplete { validAr ->
                                                                            if (validAr.succeeded()) {
                                                                                val validResponse = validAr.result()
                                                                                testContext.verify {
                                                                                    assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                                                                    val body = validResponse.bodyAsJsonObject()
                                                                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                                                    val user = body.getJsonObject("user")
                                                                                    assert(user.getInteger("id") == 1) { "Expected id to be 1 but got ${user.getInteger("id")}" }
                                                                                    assert(user.getString("name") == "test") { "Expected name to be test but got ${user.getString("name")}" }
                                                                                    assert(user.getString("email") == "test@example.com") { "Expected email to be test@example.com but got ${user.getString("email")}" }
                                                                                    testContext.completeNow()
                                                                                }
                                                                            } else {
                                                                                testContext.failNow(validAr.cause())
                                                                            }
                                                                        }
                                                                } else {
                                                                    testContext.failNow(arrayAr.cause())
                                                                }
                                                            }
                                                    } else {
                                                        testContext.failNow(formatAr.cause())
                                                    }
                                                }
                                        } else {
                                            testContext.failNow(propertyAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(missingAr.cause())
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
     * 测试自定义状态码
     */
    @Test
    fun testCustomStatusCode(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("parameters", JsonArray()
                .add(JsonObject()
                    .put("name", "id")
                    .put("in", "query")
                    .put("required", true)
                    .put("type", "integer")
                )
            )
            .put("failureStatusCode", 422)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-validator", "request-validator", config)
        val plugin = RequestValidatorPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
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
                    // 测试自定义状态码
                    webClient.get("/")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 422) { "Expected status code 422 but got ${response.statusCode()}" }
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
