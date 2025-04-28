package com.louloulin.apix.plugins.aggregation

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
 * 请求聚合插件测试
 */
@ExtendWith(VertxExtension::class)
class RequestAggregationPluginTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private val testPort = 8888
    private val mockPort1 = 8889
    private val mockPort2 = 8890
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(testPort)
        )
        
        // 启动模拟服务器 1
        startMockServer1(testContext)
        
        // 启动模拟服务器 2
        startMockServer2(testContext)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    /**
     * 启动模拟服务器 1
     */
    private fun startMockServer1(testContext: VertxTestContext) {
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 添加用户信息接口
        router.get("/users/:id").handler { context ->
            val userId = context.pathParam("id")
            
            val user = JsonObject()
                .put("id", userId)
                .put("name", "User $userId")
                .put("email", "user$userId@example.com")
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(user.encode())
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(mockPort1)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    /**
     * 启动模拟服务器 2
     */
    private fun startMockServer2(testContext: VertxTestContext) {
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 添加订单信息接口
        router.get("/orders").handler { context ->
            val userId = context.request().getParam("userId")
            
            val orders = JsonArray()
            for (i in 1..3) {
                orders.add(JsonObject()
                    .put("id", "order$i")
                    .put("userId", userId)
                    .put("product", "Product $i")
                    .put("price", i * 10.0)
                )
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("orders", orders)
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(mockPort2)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    /**
     * 测试并行请求聚合（简单合并）
     */
    @Test
    fun testParallelAggregationWithSimpleMerge(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("services", JsonArray()
                .add(JsonObject()
                    .put("name", "user")
                    .put("url", "http://localhost:$mockPort1/users/123")
                    .put("method", "GET")
                )
                .add(JsonObject()
                    .put("name", "orders")
                    .put("url", "http://localhost:$mockPort2/orders?userId=123")
                    .put("method", "GET")
                )
            )
            .put("aggregation", JsonObject()
                .put("type", "merge")
                .put("mergeStrategy", "simple")
            )
            .put("parallel", true)
            .put("timeout", 5000)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-aggregation", "requestAggregation", config)
        val plugin = RequestAggregationPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/aggregate")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    
                                    val body = response.bodyAsJsonObject()
                                    
                                    // 验证用户信息
                                    val user = body.getJsonObject("user")
                                    assert(user != null) { "Expected user to be not null" }
                                    assert(user.getString("id") == "123") { "Expected user.id to be 123 but got ${user.getString("id")}" }
                                    assert(user.getString("name") == "User 123") { "Expected user.name to be User 123 but got ${user.getString("name")}" }
                                    
                                    // 验证订单信息
                                    val orders = body.getJsonObject("orders")
                                    assert(orders != null) { "Expected orders to be not null" }
                                    val ordersList = orders.getJsonArray("orders")
                                    assert(ordersList != null) { "Expected orders.orders to be not null" }
                                    assert(ordersList.size() == 3) { "Expected orders.orders.size to be 3 but got ${ordersList.size()}" }
                                    
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
     * 测试串行请求聚合（嵌套合并）
     */
    @Test
    fun testSerialAggregationWithNestedMerge(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("services", JsonArray()
                .add(JsonObject()
                    .put("name", "user")
                    .put("url", "http://localhost:$mockPort1/users/123")
                    .put("method", "GET")
                )
                .add(JsonObject()
                    .put("name", "orders")
                    .put("url", "http://localhost:$mockPort2/orders?userId={user.id}")
                    .put("method", "GET")
                    .put("dependsOn", JsonArray().add("user"))
                )
            )
            .put("aggregation", JsonObject()
                .put("type", "merge")
                .put("mergeStrategy", "nested")
            )
            .put("parallel", false)
            .put("timeout", 5000)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-aggregation", "requestAggregation", config)
        val plugin = RequestAggregationPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/aggregate")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    
                                    val body = response.bodyAsJsonObject()
                                    
                                    // 验证嵌套结构
                                    assert(body.containsKey("user.id")) { "Expected body to contain user.id" }
                                    assert(body.getString("user.id") == "123") { "Expected user.id to be 123 but got ${body.getString("user.id")}" }
                                    assert(body.containsKey("user.name")) { "Expected body to contain user.name" }
                                    assert(body.getString("user.name") == "User 123") { "Expected user.name to be User 123 but got ${body.getString("user.name")}" }
                                    
                                    assert(body.containsKey("orders.orders")) { "Expected body to contain orders.orders" }
                                    
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
     * 测试模板聚合
     */
    @Test
    fun testTemplateAggregation(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("services", JsonArray()
                .add(JsonObject()
                    .put("name", "user")
                    .put("url", "http://localhost:$mockPort1/users/123")
                    .put("method", "GET")
                )
                .add(JsonObject()
                    .put("name", "orders")
                    .put("url", "http://localhost:$mockPort2/orders?userId={user.id}")
                    .put("method", "GET")
                    .put("dependsOn", JsonArray().add("user"))
                )
            )
            .put("aggregation", JsonObject()
                .put("type", "template")
                .put("template", JsonObject()
                    .put("user", JsonObject()
                        .put("id", "{user.id}")
                        .put("name", "{user.name}")
                        .put("email", "{user.email}")
                    )
                    .put("orders", "{orders.orders}")
                    .put("summary", JsonObject()
                        .put("userId", "{user.id}")
                        .put("userName", "{user.name}")
                        .put("orderCount", 3)
                    )
                    .encode()
                )
            )
            .put("parallel", false)
            .put("timeout", 5000)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-aggregation", "requestAggregation", config)
        val plugin = RequestAggregationPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/aggregate")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    
                                    val body = response.bodyAsJsonObject()
                                    
                                    // 验证模板结构
                                    val user = body.getJsonObject("user")
                                    assert(user != null) { "Expected user to be not null" }
                                    assert(user.getString("id") == "123") { "Expected user.id to be 123 but got ${user.getString("id")}" }
                                    assert(user.getString("name") == "User 123") { "Expected user.name to be User 123 but got ${user.getString("name")}" }
                                    
                                    val orders = body.getJsonArray("orders")
                                    assert(orders != null) { "Expected orders to be not null" }
                                    assert(orders.size() == 3) { "Expected orders.size to be 3 but got ${orders.size()}" }
                                    
                                    val summary = body.getJsonObject("summary")
                                    assert(summary != null) { "Expected summary to be not null" }
                                    assert(summary.getString("userId") == "123") { "Expected summary.userId to be 123 but got ${summary.getString("userId")}" }
                                    assert(summary.getString("userName") == "User 123") { "Expected summary.userName to be User 123 but got ${summary.getString("userName")}" }
                                    assert(summary.getInteger("orderCount") == 3) { "Expected summary.orderCount to be 3 but got ${summary.getInteger("orderCount")}" }
                                    
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
     * 测试错误处理
     */
    @Test
    fun testErrorHandling(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("services", JsonArray()
                .add(JsonObject()
                    .put("name", "user")
                    .put("url", "http://localhost:$mockPort1/users/123")
                    .put("method", "GET")
                )
                .add(JsonObject()
                    .put("name", "nonexistent")
                    .put("url", "http://localhost:9999/nonexistent")
                    .put("method", "GET")
                    .put("timeout", 1000)
                )
            )
            .put("aggregation", JsonObject()
                .put("type", "merge")
                .put("mergeStrategy", "simple")
            )
            .put("parallel", true)
            .put("continueOnError", true)
            .put("timeout", 5000)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-request-aggregation", "requestAggregation", config)
        val plugin = RequestAggregationPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/aggregate")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                    
                                    val body = response.bodyAsJsonObject()
                                    
                                    // 验证用户信息
                                    val user = body.getJsonObject("user")
                                    assert(user != null) { "Expected user to be not null" }
                                    assert(user.getString("id") == "123") { "Expected user.id to be 123 but got ${user.getString("id")}" }
                                    
                                    // 验证错误信息
                                    val errors = body.getJsonObject("errors")
                                    assert(errors != null) { "Expected errors to be not null" }
                                    assert(errors.containsKey("nonexistent")) { "Expected errors to contain nonexistent" }
                                    
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
