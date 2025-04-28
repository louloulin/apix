package com.louloulin.apix.plugins.security

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * IP 黑白名单插件测试
 */
@ExtendWith(VertxExtension::class)
class IpFilterPluginTest {
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
     * 测试黑名单模式
     */
    @Test
    fun testBlacklistMode(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("mode", "blacklist")
            .put("ips", JsonArray()
                .add("127.0.0.1")
                .add("192.168.1.0/24")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-ip-filter", "ip-filter", config)
        val plugin = IpFilterPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            // 设置 X-Real-IP 头，模拟不同的 IP
            val testIp = context.request().getParam("ip") ?: "127.0.0.1"
            context.request().headers().set("X-Real-IP", testIp)
            
            // 继续处理
            context.next()
        }
        
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("ip", context.request().getHeader("X-Real-IP"))
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 测试被黑名单阻止的 IP
                    webClient.get("/?ip=127.0.0.1")
                        .send()
                        .onComplete { blacklistAr ->
                            if (blacklistAr.succeeded()) {
                                val blacklistResponse = blacklistAr.result()
                                testContext.verify {
                                    assert(blacklistResponse.statusCode() == 403) { "Expected status code 403 but got ${blacklistResponse.statusCode()}" }
                                    val body = blacklistResponse.bodyAsJsonObject()
                                    assert(body.getString("error") == "IP address not allowed") { "Expected error message 'IP address not allowed' but got '${body.getString("error")}'" }
                                    assert(body.getString("ip") == "127.0.0.1") { "Expected IP 127.0.0.1 but got ${body.getString("ip")}" }
                                }
                                
                                // 测试被 CIDR 黑名单阻止的 IP
                                webClient.get("/?ip=192.168.1.100")
                                    .send()
                                    .onComplete { cidrAr ->
                                        if (cidrAr.succeeded()) {
                                            val cidrResponse = cidrAr.result()
                                            testContext.verify {
                                                assert(cidrResponse.statusCode() == 403) { "Expected status code 403 but got ${cidrResponse.statusCode()}" }
                                                val body = cidrResponse.bodyAsJsonObject()
                                                assert(body.getString("ip") == "192.168.1.100") { "Expected IP 192.168.1.100 but got ${body.getString("ip")}" }
                                            }
                                            
                                            // 测试允许的 IP
                                            webClient.get("/?ip=10.0.0.1")
                                                .send()
                                                .onComplete { allowedAr ->
                                                    if (allowedAr.succeeded()) {
                                                        val allowedResponse = allowedAr.result()
                                                        testContext.verify {
                                                            assert(allowedResponse.statusCode() == 200) { "Expected status code 200 but got ${allowedResponse.statusCode()}" }
                                                            val body = allowedResponse.bodyAsJsonObject()
                                                            assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                            assert(body.getString("ip") == "10.0.0.1") { "Expected IP 10.0.0.1 but got ${body.getString("ip")}" }
                                                            testContext.completeNow()
                                                        }
                                                    } else {
                                                        testContext.failNow(allowedAr.cause())
                                                    }
                                                }
                                        } else {
                                            testContext.failNow(cidrAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(blacklistAr.cause())
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
     * 测试白名单模式
     */
    @Test
    fun testWhitelistMode(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("mode", "whitelist")
            .put("ips", JsonArray()
                .add("127.0.0.1")
                .add("192.168.1.0/24")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-ip-filter", "ip-filter", config)
        val plugin = IpFilterPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            // 设置 X-Real-IP 头，模拟不同的 IP
            val testIp = context.request().getParam("ip") ?: "127.0.0.1"
            context.request().headers().set("X-Real-IP", testIp)
            
            // 继续处理
            context.next()
        }
        
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("ip", context.request().getHeader("X-Real-IP"))
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 测试白名单允许的 IP
                    webClient.get("/?ip=127.0.0.1")
                        .send()
                        .onComplete { whitelistAr ->
                            if (whitelistAr.succeeded()) {
                                val whitelistResponse = whitelistAr.result()
                                testContext.verify {
                                    assert(whitelistResponse.statusCode() == 200) { "Expected status code 200 but got ${whitelistResponse.statusCode()}" }
                                    val body = whitelistResponse.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    assert(body.getString("ip") == "127.0.0.1") { "Expected IP 127.0.0.1 but got ${body.getString("ip")}" }
                                }
                                
                                // 测试 CIDR 白名单允许的 IP
                                webClient.get("/?ip=192.168.1.100")
                                    .send()
                                    .onComplete { cidrAr ->
                                        if (cidrAr.succeeded()) {
                                            val cidrResponse = cidrAr.result()
                                            testContext.verify {
                                                assert(cidrResponse.statusCode() == 200) { "Expected status code 200 but got ${cidrResponse.statusCode()}" }
                                                val body = cidrResponse.bodyAsJsonObject()
                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                assert(body.getString("ip") == "192.168.1.100") { "Expected IP 192.168.1.100 but got ${body.getString("ip")}" }
                                            }
                                            
                                            // 测试被白名单阻止的 IP
                                            webClient.get("/?ip=10.0.0.1")
                                                .send()
                                                .onComplete { blockedAr ->
                                                    if (blockedAr.succeeded()) {
                                                        val blockedResponse = blockedAr.result()
                                                        testContext.verify {
                                                            assert(blockedResponse.statusCode() == 403) { "Expected status code 403 but got ${blockedResponse.statusCode()}" }
                                                            val body = blockedResponse.bodyAsJsonObject()
                                                            assert(body.getString("error") == "IP address not allowed") { "Expected error message 'IP address not allowed' but got '${body.getString("error")}'" }
                                                            assert(body.getString("ip") == "10.0.0.1") { "Expected IP 10.0.0.1 but got ${body.getString("ip")}" }
                                                            testContext.completeNow()
                                                        }
                                                    } else {
                                                        testContext.failNow(blockedAr.cause())
                                                    }
                                                }
                                        } else {
                                            testContext.failNow(cidrAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(whitelistAr.cause())
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
     * 测试自定义状态码和消息
     */
    @Test
    fun testCustomStatusCodeAndMessage(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("mode", "blacklist")
            .put("ips", JsonArray().add("127.0.0.1"))
            .put("statusCode", 401)
            .put("message", "Custom error message")
        
        // 创建插件
        val pluginConfig = PluginConfig("test-ip-filter", "ip-filter", config)
        val plugin = IpFilterPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            // 设置 X-Real-IP 头
            context.request().headers().set("X-Real-IP", "127.0.0.1")
            
            // 继续处理
            context.next()
        }
        
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
                    // 测试自定义状态码和消息
                    webClient.get("/")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 401) { "Expected status code 401 but got ${response.statusCode()}" }
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getString("error") == "Custom error message") { "Expected error message 'Custom error message' but got '${body.getString("error")}'" }
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
     * 测试 X-Forwarded-For 头
     */
    @Test
    fun testXForwardedForHeader(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("mode", "blacklist")
            .put("ips", JsonArray().add("192.168.1.1"))
        
        // 创建插件
        val pluginConfig = PluginConfig("test-ip-filter", "ip-filter", config)
        val plugin = IpFilterPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            // 设置 X-Forwarded-For 头
            context.request().headers().set("X-Forwarded-For", "192.168.1.1, 10.0.0.1")
            
            // 继续处理
            context.next()
        }
        
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
                    // 测试 X-Forwarded-For 头
                    webClient.get("/")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 403) { "Expected status code 403 but got ${response.statusCode()}" }
                                    val body = response.bodyAsJsonObject()
                                    assert(body.getString("ip") == "192.168.1.1") { "Expected IP 192.168.1.1 but got ${body.getString("ip")}" }
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
