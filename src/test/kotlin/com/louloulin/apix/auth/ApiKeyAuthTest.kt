package com.louloulin.apix.auth

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.AuthVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
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
 * API Key 认证测试
 */
@ExtendWith(VertxExtension::class)
class ApiKeyAuthTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private var apiKey: String = ""
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 部署 AuthVerticle
        vertx.deployVerticle(AuthVerticle())
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 创建 WebClient
                    webClient = WebClient.create(vertx, WebClientOptions()
                        .setDefaultHost("localhost")
                        .setDefaultPort(8080)
                    )
                    
                    // 创建测试 API Key
                    createTestApiKey(testContext)
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    /**
     * 创建测试 API Key
     */
    private fun createTestApiKey(testContext: VertxTestContext) {
        val message = JsonObject()
            .put("name", "Test API Key")
            .put("scopes", JsonArray().add("read").add("write"))
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AUTH_CREATE_API_KEY, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    val result = response.getJsonObject("result")
                    apiKey = result.getString("key")
                    testContext.completeNow()
                } else {
                    testContext.failNow(response.getString("error"))
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testValidateApiKey(testContext: VertxTestContext) {
        val message = JsonObject()
            .put("apiKey", apiKey)
            .put("requiredScopes", JsonArray().add("read"))
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AUTH_VALIDATE_API_KEY, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == true) { "Expected success to be true" }
                    val result = response.getJsonObject("result")
                    assert(result.getBoolean("valid") == true) { "Expected API Key to be valid" }
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testInvalidApiKey(testContext: VertxTestContext) {
        val message = JsonObject()
            .put("apiKey", "invalid-api-key")
            .put("requiredScopes", JsonArray().add("read"))
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AUTH_VALIDATE_API_KEY, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == false) { "Expected success to be false" }
                    assert(response.getInteger("errorCode") == 401) { "Expected error code to be 401" }
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testInsufficientScopes(testContext: VertxTestContext) {
        val message = JsonObject()
            .put("apiKey", apiKey)
            .put("requiredScopes", JsonArray().add("admin"))
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AUTH_VALIDATE_API_KEY, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == false) { "Expected success to be false" }
                    assert(response.getInteger("errorCode") == 403) { "Expected error code to be 403" }
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
