package com.louloulin.apix.plugins.security

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
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

/**
 * 请求签名验证插件测试
 */
@ExtendWith(VertxExtension::class)
class SignatureVerificationPluginTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private val testPort = 8888
    private val apiKeyId = "test-key-id"
    private val apiKeySecret = "test-key-secret"
    
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
     * 测试请求签名验证（从请求头获取签名）
     */
    @Test
    fun testSignatureVerificationFromHeader(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("signatureLocation", "header")
            .put("signatureName", "X-Signature")
            .put("timestampName", "X-Timestamp")
            .put("keyIdName", "X-API-Key")
            .put("algorithm", "HMAC-SHA256")
            .put("maxTimestampAge", 300)
            .put("includeBody", true)
            .put("includeHeaders", JsonArray().add("content-type"))
            .put("apiKeys", JsonArray()
                .add(JsonObject()
                    .put("id", apiKeyId)
                    .put("secret", apiKeySecret)
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-signature-verification", "signatureVerification", config)
        val plugin = SignatureVerificationPlugin(pluginConfig.id, pluginConfig, vertx)
        
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
                    // 发送请求，不带签名
                    webClient.get("/test")
                        .send()
                        .onComplete { missingAr ->
                            if (missingAr.succeeded()) {
                                val missingResponse = missingAr.result()
                                testContext.verify {
                                    assert(missingResponse.statusCode() == 401) { "Expected status code 401 but got ${missingResponse.statusCode()}" }
                                    val body = missingResponse.bodyAsJsonObject()
                                    assert(body.getString("error") == "Signature verification failed") { "Expected error to be Signature verification failed but got ${body.getString("error")}" }
                                }
                                
                                // 发送请求，带有签名但不带时间戳
                                webClient.get("/test")
                                    .putHeader("X-Signature", "invalid-signature")
                                    .putHeader("X-API-Key", apiKeyId)
                                    .send()
                                    .onComplete { noTimestampAr ->
                                        if (noTimestampAr.succeeded()) {
                                            val noTimestampResponse = noTimestampAr.result()
                                            testContext.verify {
                                                assert(noTimestampResponse.statusCode() == 401) { "Expected status code 401 but got ${noTimestampResponse.statusCode()}" }
                                                val body = noTimestampResponse.bodyAsJsonObject()
                                                assert(body.getString("message") == "Missing timestamp") { "Expected message to be Missing timestamp but got ${body.getString("message")}" }
                                            }
                                            
                                            // 发送请求，带有签名和时间戳但签名无效
                                            val timestamp = Instant.now().epochSecond
                                            
                                            webClient.get("/test")
                                                .putHeader("X-Signature", "invalid-signature")
                                                .putHeader("X-Timestamp", timestamp.toString())
                                                .putHeader("X-API-Key", apiKeyId)
                                                .send()
                                                .onComplete { invalidAr ->
                                                    if (invalidAr.succeeded()) {
                                                        val invalidResponse = invalidAr.result()
                                                        testContext.verify {
                                                            assert(invalidResponse.statusCode() == 401) { "Expected status code 401 but got ${invalidResponse.statusCode()}" }
                                                            val body = invalidResponse.bodyAsJsonObject()
                                                            assert(body.getString("message") == "Invalid signature") { "Expected message to be Invalid signature but got ${body.getString("message")}" }
                                                        }
                                                        
                                                        // 发送请求，带有有效的签名
                                                        val validTimestamp = Instant.now().epochSecond
                                                        val stringToSign = buildStringToSign("GET", "/test", validTimestamp)
                                                        val validSignature = hmacSha256(stringToSign, apiKeySecret)
                                                        
                                                        webClient.get("/test")
                                                            .putHeader("X-Signature", validSignature)
                                                            .putHeader("X-Timestamp", validTimestamp.toString())
                                                            .putHeader("X-API-Key", apiKeyId)
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
                                        } else {
                                            testContext.failNow(noTimestampAr.cause())
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
     * 测试请求签名验证（从查询参数获取签名）
     */
    @Test
    fun testSignatureVerificationFromQuery(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("signatureLocation", "query")
            .put("signatureName", "signature")
            .put("timestampName", "timestamp")
            .put("keyIdName", "apiKey")
            .put("algorithm", "HMAC-SHA256")
            .put("maxTimestampAge", 300)
            .put("includeBody", true)
            .put("excludeQueryParams", JsonArray().add("signature"))
            .put("apiKeys", JsonArray()
                .add(JsonObject()
                    .put("id", apiKeyId)
                    .put("secret", apiKeySecret)
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-signature-verification", "signatureVerification", config)
        val plugin = SignatureVerificationPlugin(pluginConfig.id, pluginConfig, vertx)
        
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
                    // 发送请求，带有有效的签名
                    val validTimestamp = Instant.now().epochSecond
                    val stringToSign = buildStringToSign("GET", "/test", validTimestamp, "param1=value1")
                    val validSignature = hmacSha256(stringToSign, apiKeySecret)
                    
                    webClient.get("/test?apiKey=${apiKeyId}&timestamp=${validTimestamp}&signature=${validSignature}&param1=value1")
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
    
    /**
     * 测试请求签名验证（包含请求体）
     */
    @Test
    fun testSignatureVerificationWithRequestBody(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())
        
        // 创建插件配置
        val config = JsonObject()
            .put("signatureLocation", "header")
            .put("signatureName", "X-Signature")
            .put("timestampName", "X-Timestamp")
            .put("keyIdName", "X-API-Key")
            .put("algorithm", "HMAC-SHA256")
            .put("maxTimestampAge", 300)
            .put("includeBody", true)
            .put("includeHeaders", JsonArray().add("content-type"))
            .put("apiKeys", JsonArray()
                .add(JsonObject()
                    .put("id", apiKeyId)
                    .put("secret", apiKeySecret)
                )
            )
        
        // 创建插件
        val pluginConfig = PluginConfig("test-signature-verification", "signatureVerification", config)
        val plugin = SignatureVerificationPlugin(pluginConfig.id, pluginConfig, vertx)
        
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
                    // 发送 POST 请求，带有请求体和有效的签名
                    val validTimestamp = Instant.now().epochSecond
                    val requestBody = JsonObject()
                        .put("name", "Test")
                        .put("value", 123)
                        .encode()
                    
                    val stringToSign = buildStringToSign("POST", "/test", validTimestamp) + requestBody
                    val validSignature = hmacSha256(stringToSign, apiKeySecret)
                    
                    webClient.post("/test")
                        .putHeader("X-Signature", validSignature)
                        .putHeader("X-Timestamp", validTimestamp.toString())
                        .putHeader("X-API-Key", apiKeyId)
                        .putHeader("Content-Type", "application/json")
                        .sendBuffer(Buffer.buffer(requestBody))
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
    
    /**
     * 构建签名字符串
     */
    private fun buildStringToSign(method: String, path: String, timestamp: Long, queryString: String = ""): String {
        val stringBuilder = StringBuilder()
        
        // 添加请求方法
        stringBuilder.append(method).append("\n")
        
        // 添加请求路径
        stringBuilder.append(path).append("\n")
        
        // 添加时间戳
        stringBuilder.append(timestamp).append("\n")
        
        // 添加查询参数
        if (queryString.isNotEmpty()) {
            stringBuilder.append(queryString).append("\n")
        }
        
        return stringBuilder.toString()
    }
    
    /**
     * HMAC-SHA256 签名
     */
    private fun hmacSha256(data: String, key: String): String {
        val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(hash)
    }
}
