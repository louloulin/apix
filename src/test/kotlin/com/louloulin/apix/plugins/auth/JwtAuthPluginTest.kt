package com.louloulin.apix.plugins.auth

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * JWT 认证插件测试
 */
@ExtendWith(VertxExtension::class)
class JwtAuthPluginTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private lateinit var jwtAuth: JWTAuth
    private val testPort = 8888
    private val secret = "supersecretkeysupersecretkeysupersecretkey"
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(testPort)
        )
        
        // 创建 JWT 认证提供者
        val jwtAuthOptions = JWTAuthOptions()
            .addPubSecKey(PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setSymmetric(true)
                .setPublicKey(secret)
                .setSecretKey(secret)
            )
        
        jwtAuth = JWTAuth.create(vertx, jwtAuthOptions)
        
        testContext.completeNow()
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    /**
     * 测试 JWT 认证（从请求头获取令牌）
     */
    @Test
    fun testJwtAuthFromHeader(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("tokenLocation", "header")
            .put("tokenName", "Authorization")
            .put("tokenPrefix", "Bearer ")
            .put("secret", secret)
            .put("algorithm", "HS256")
        
        // 创建插件
        val pluginConfig = PluginConfig("test-jwt-auth", "jwtAuth", config)
        val plugin = JwtAuthPlugin(pluginConfig.id, pluginConfig, vertx)
        
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
                    // 生成有效的 JWT 令牌
                    val now = Instant.now()
                    val claims = JsonObject()
                        .put("sub", "user123")
                        .put("name", "Test User")
                        .put("iat", now.epochSecond)
                    
                    val options = JWTOptions()
                        .setExpiresInMinutes(60)
                    
                    val token = jwtAuth.generateToken(claims, options)
                    
                    // 发送请求，带有有效的 JWT 令牌
                    webClient.get("/test")
                        .putHeader("Authorization", "Bearer $token")
                        .send()
                        .onComplete { validAr ->
                            if (validAr.succeeded()) {
                                val validResponse = validAr.result()
                                testContext.verify {
                                    assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                    val body = validResponse.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    val user = body.getJsonObject("user")
                                    assert(user.getString("sub") == "user123") { "Expected sub to be user123 but got ${user.getString("sub")}" }
                                    assert(user.getString("name") == "Test User") { "Expected name to be Test User but got ${user.getString("name")}" }
                                }
                                
                                // 发送请求，不带 JWT 令牌
                                webClient.get("/test")
                                    .send()
                                    .onComplete { missingAr ->
                                        if (missingAr.succeeded()) {
                                            val missingResponse = missingAr.result()
                                            testContext.verify {
                                                assert(missingResponse.statusCode() == 401) { "Expected status code 401 but got ${missingResponse.statusCode()}" }
                                                val body = missingResponse.bodyAsJsonObject()
                                                assert(body.getString("error") == "Unauthorized") { "Expected error to be Unauthorized but got ${body.getString("error")}" }
                                            }
                                            
                                            // 发送请求，带有无效的 JWT 令牌
                                            webClient.get("/test")
                                                .putHeader("Authorization", "Bearer invalid-token")
                                                .send()
                                                .onComplete { invalidAr ->
                                                    if (invalidAr.succeeded()) {
                                                        val invalidResponse = invalidAr.result()
                                                        testContext.verify {
                                                            assert(invalidResponse.statusCode() == 401) { "Expected status code 401 but got ${invalidResponse.statusCode()}" }
                                                            val body = invalidResponse.bodyAsJsonObject()
                                                            assert(body.getString("error") == "Unauthorized") { "Expected error to be Unauthorized but got ${body.getString("error")}" }
                                                            testContext.completeNow()
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
    
    /**
     * 测试 JWT 认证（从查询参数获取令牌）
     */
    @Test
    fun testJwtAuthFromQuery(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("tokenLocation", "query")
            .put("tokenName", "token")
            .put("secret", secret)
            .put("algorithm", "HS256")
        
        // 创建插件
        val pluginConfig = PluginConfig("test-jwt-auth", "jwtAuth", config)
        val plugin = JwtAuthPlugin(pluginConfig.id, pluginConfig, vertx)
        
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
                    // 生成有效的 JWT 令牌
                    val now = Instant.now()
                    val claims = JsonObject()
                        .put("sub", "user123")
                        .put("name", "Test User")
                        .put("iat", now.epochSecond)
                    
                    val options = JWTOptions()
                        .setExpiresInMinutes(60)
                    
                    val token = jwtAuth.generateToken(claims, options)
                    
                    // 发送请求，带有有效的 JWT 令牌
                    webClient.get("/test?token=$token")
                        .send()
                        .onComplete { validAr ->
                            if (validAr.succeeded()) {
                                val validResponse = validAr.result()
                                testContext.verify {
                                    assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                    val body = validResponse.bodyAsJsonObject()
                                    assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                    val user = body.getJsonObject("user")
                                    assert(user.getString("sub") == "user123") { "Expected sub to be user123 but got ${user.getString("sub")}" }
                                    assert(user.getString("name") == "Test User") { "Expected name to be Test User but got ${user.getString("name")}" }
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
    
    /**
     * 测试 JWT 认证（验证必需的声明）
     */
    @Test
    fun testJwtAuthRequiredClaims(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("tokenLocation", "header")
            .put("tokenName", "Authorization")
            .put("tokenPrefix", "Bearer ")
            .put("secret", secret)
            .put("algorithm", "HS256")
            .put("requiredClaims", JsonObject()
                .put("role", "admin")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-jwt-auth", "jwtAuth", config)
        val plugin = JwtAuthPlugin(pluginConfig.id, pluginConfig, vertx)
        
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
                    // 生成有效的 JWT 令牌，但缺少必需的声明
                    val invalidClaims = JsonObject()
                        .put("sub", "user123")
                        .put("name", "Test User")
                    
                    val invalidToken = jwtAuth.generateToken(invalidClaims, JWTOptions())
                    
                    // 发送请求，带有缺少必需声明的 JWT 令牌
                    webClient.get("/test")
                        .putHeader("Authorization", "Bearer $invalidToken")
                        .send()
                        .onComplete { invalidAr ->
                            if (invalidAr.succeeded()) {
                                val invalidResponse = invalidAr.result()
                                testContext.verify {
                                    assert(invalidResponse.statusCode() == 403) { "Expected status code 403 but got ${invalidResponse.statusCode()}" }
                                    val body = invalidResponse.bodyAsJsonObject()
                                    assert(body.getString("error") == "Forbidden") { "Expected error to be Forbidden but got ${body.getString("error")}" }
                                }
                                
                                // 生成有效的 JWT 令牌，包含必需的声明
                                val validClaims = JsonObject()
                                    .put("sub", "user123")
                                    .put("name", "Test User")
                                    .put("role", "admin")
                                
                                val validToken = jwtAuth.generateToken(validClaims, JWTOptions())
                                
                                // 发送请求，带有包含必需声明的 JWT 令牌
                                webClient.get("/test")
                                    .putHeader("Authorization", "Bearer $validToken")
                                    .send()
                                    .onComplete { validAr ->
                                        if (validAr.succeeded()) {
                                            val validResponse = validAr.result()
                                            testContext.verify {
                                                assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                                val body = validResponse.bodyAsJsonObject()
                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                val user = body.getJsonObject("user")
                                                assert(user.getString("role") == "admin") { "Expected role to be admin but got ${user.getString("role")}" }
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
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试 JWT 认证（验证必需的作用域）
     */
    @Test
    fun testJwtAuthRequiredScopes(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 创建插件配置
        val config = JsonObject()
            .put("tokenLocation", "header")
            .put("tokenName", "Authorization")
            .put("tokenPrefix", "Bearer ")
            .put("secret", secret)
            .put("algorithm", "HS256")
            .put("requiredScopes", JsonArray()
                .add("read")
                .add("write")
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-jwt-auth", "jwtAuth", config)
        val plugin = JwtAuthPlugin(pluginConfig.id, pluginConfig, vertx)
        
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
                    // 生成有效的 JWT 令牌，但缺少必需的作用域
                    val invalidClaims = JsonObject()
                        .put("sub", "user123")
                        .put("name", "Test User")
                        .put("scope", "read")
                    
                    val invalidToken = jwtAuth.generateToken(invalidClaims, JWTOptions())
                    
                    // 发送请求，带有缺少必需作用域的 JWT 令牌
                    webClient.get("/test")
                        .putHeader("Authorization", "Bearer $invalidToken")
                        .send()
                        .onComplete { invalidAr ->
                            if (invalidAr.succeeded()) {
                                val invalidResponse = invalidAr.result()
                                testContext.verify {
                                    assert(invalidResponse.statusCode() == 403) { "Expected status code 403 but got ${invalidResponse.statusCode()}" }
                                    val body = invalidResponse.bodyAsJsonObject()
                                    assert(body.getString("error") == "Forbidden") { "Expected error to be Forbidden but got ${body.getString("error")}" }
                                }
                                
                                // 生成有效的 JWT 令牌，包含必需的作用域
                                val validClaims = JsonObject()
                                    .put("sub", "user123")
                                    .put("name", "Test User")
                                    .put("scope", "read write")
                                
                                val validToken = jwtAuth.generateToken(validClaims, JWTOptions())
                                
                                // 发送请求，带有包含必需作用域的 JWT 令牌
                                webClient.get("/test")
                                    .putHeader("Authorization", "Bearer $validToken")
                                    .send()
                                    .onComplete { validAr ->
                                        if (validAr.succeeded()) {
                                            val validResponse = validAr.result()
                                            testContext.verify {
                                                assert(validResponse.statusCode() == 200) { "Expected status code 200 but got ${validResponse.statusCode()}" }
                                                val body = validResponse.bodyAsJsonObject()
                                                assert(body.getBoolean("success") == true) { "Expected success to be true" }
                                                val user = body.getJsonObject("user")
                                                assert(user.getString("scope") == "read write") { "Expected scope to be read write but got ${user.getString("scope")}" }
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
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
