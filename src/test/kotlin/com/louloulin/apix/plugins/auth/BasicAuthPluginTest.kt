package com.louloulin.apix.plugins.auth

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * 基本认证插件测试
 */
@ExtendWith(VertxExtension::class)
class BasicAuthPluginTest {
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
     * 测试基本认证（内存中用户）
     */
    @Test
    fun testBasicAuthWithInMemoryUsers(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("realm", "Test Realm")
            .put("users", JsonArray()
                .add(JsonObject()
                    .put("username", "testuser")
                    .put("password", "testpass")
                    .put("roles", JsonArray().add("user"))
                )
                .add(JsonObject()
                    .put("username", "admin")
                    .put("password", "adminpass")
                    .put("roles", JsonArray().add("admin"))
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-basic-auth", "basicAuth", config)
        val plugin = BasicAuthPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            val user = context.user()
            val principal = user.principal()
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("user", principal)
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求，不带认证头
                    webClient.get("/test")
                        .send()
                        .onComplete { missingAr ->
                            if (missingAr.succeeded()) {
                                val missingResponse = missingAr.result()
                                testContext.verify {
                                    assert(missingResponse.statusCode() == 401) { "Expected status code 401 but got ${missingResponse.statusCode()}" }
                                    val wwwAuthenticate = missingResponse.getHeader("WWW-Authenticate")
                                    assert(wwwAuthenticate == "Basic realm=\"Test Realm\"") { "Expected WWW-Authenticate header to be Basic realm=\"Test Realm\" but got $wwwAuthenticate" }
                                }
                                
                                // 发送请求，带有无效的认证头
                                val invalidCredentials = "invalid:credentials"
                                val invalidBase64 = Base64.getEncoder().encodeToString(invalidCredentials.toByteArray())
                                
                                webClient.get("/test")
                                    .putHeader("Authorization", "Basic $invalidBase64")
                                    .send()
                                    .onComplete { invalidAr ->
                                        if (invalidAr.succeeded()) {
                                            val invalidResponse = invalidAr.result()
                                            testContext.verify {
                                                assert(invalidResponse.statusCode() == 401) { "Expected status code 401 but got ${invalidResponse.statusCode()}" }
                                            }
                                            
                                            // 发送请求，带有有效的认证头
                                            val validCredentials = "testuser:testpass"
                                            val validBase64 = Base64.getEncoder().encodeToString(validCredentials.toByteArray())
                                            
                                            webClient.get("/test")
                                                .putHeader("Authorization", "Basic $validBase64")
                                                .send()
                                                .onComplete { validAr ->
                                                    if (validAr.succeeded()) {
                                                        val validResponse = validAr.result()
                                                        testContext.verify {
                                                            assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                                            val body = validResponse.bodyAsJsonObject()
                                                            assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                            val user = body.getJsonObject("user")
                                                            assert(user.getString("username") == "testuser") { "Expected username to be testuser but got ${user.getString("username")}" }
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
     * 测试基本认证（htpasswd 文件）
     */
    @Test
    fun testBasicAuthWithHtpasswdFile(@TempDir tempDir: Path, testContext: VertxTestContext) {
        // 创建 htpasswd 文件
        val htpasswdFile = tempDir.resolve("htpasswd").toFile()
        htpasswdFile.writeText("testuser:testpass\nadmin:adminpass\n")
        
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("realm", "Test Realm")
            .put("htpasswdFile", htpasswdFile.absolutePath)
            .put("allowPlainTextPassword", true)
        
        // 创建插件
        val pluginConfig = PluginConfig("test-basic-auth", "basicAuth", config)
        val plugin = BasicAuthPlugin(pluginConfig.id, pluginConfig, vertx)
        
        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }
        
        // 添加测试处理器
        router.route().handler { context ->
            val user = context.user()
            val principal = user.principal()
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("user", principal)
                    .encode()
                )
        }
        
        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求，带有有效的认证头
                    val validCredentials = "testuser:testpass"
                    val validBase64 = Base64.getEncoder().encodeToString(validCredentials.toByteArray())
                    
                    webClient.get("/test")
                        .putHeader("Authorization", "Basic $validBase64")
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
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
