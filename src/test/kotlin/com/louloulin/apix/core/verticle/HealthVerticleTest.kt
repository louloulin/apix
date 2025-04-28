package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
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
 * 健康检查 Verticle 测试
 */
@ExtendWith(VertxExtension::class)
class HealthVerticleTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private val healthPort = 8086
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(healthPort)
        )
        
        // 部署 ConfigVerticle 和 HealthVerticle
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(HealthVerticle()) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
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
     * 测试健康检查端点
     */
    @Test
    fun testHealthEndpoint(testContext: VertxTestContext) {
        webClient.get("/health")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.getString("status") == "UP") { "Expected status to be UP but got ${body.getString("status")}" }
                        assert(body.containsKey("checks")) { "Expected body to contain checks" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试就绪探针端点
     */
    @Test
    fun testReadyEndpoint(testContext: VertxTestContext) {
        // 设置组件状态
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.HEALTH_COMPONENT_STATUS,
            JsonObject()
                .put("component", "test-component")
                .put("status", true)
        ) { ar ->
            if (ar.succeeded()) {
                // 检查就绪探针
                webClient.get("/ready")
                    .send()
                    .onComplete { readyAr ->
                        if (readyAr.succeeded()) {
                            val response = readyAr.result()
                            testContext.verify {
                                assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                                val body = response.bodyAsJsonObject()
                                assert(body.getString("status") == "UP") { "Expected status to be UP but got ${body.getString("status")}" }
                                assert(body.containsKey("checks")) { "Expected body to contain checks" }
                                
                                // 设置组件状态为 DOWN
                                vertx.eventBus().request<JsonObject>(
                                    EventBusAddresses.HEALTH_COMPONENT_STATUS,
                                    JsonObject()
                                        .put("component", "test-component")
                                        .put("status", false)
                                ) { downAr ->
                                    if (downAr.succeeded()) {
                                        // 再次检查就绪探针
                                        webClient.get("/ready")
                                            .send()
                                            .onComplete { downReadyAr ->
                                                if (downReadyAr.succeeded()) {
                                                    val downResponse = downReadyAr.result()
                                                    testContext.verify {
                                                        assert(downResponse.statusCode() == 503) { "Expected status code 503 but got ${downResponse.statusCode()}" }
                                                        val downBody = downResponse.bodyAsJsonObject()
                                                        assert(downBody.getString("status") == "DOWN") { "Expected status to be DOWN but got ${downBody.getString("status")}" }
                                                        testContext.completeNow()
                                                    }
                                                } else {
                                                    testContext.failNow(downReadyAr.cause())
                                                }
                                            }
                                    } else {
                                        testContext.failNow(downAr.cause())
                                    }
                                }
                            }
                        } else {
                            testContext.failNow(readyAr.cause())
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
     * 测试存活探针端点
     */
    @Test
    fun testLiveEndpoint(testContext: VertxTestContext) {
        webClient.get("/live")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.getString("status") == "UP") { "Expected status to be UP but got ${body.getString("status")}" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试系统信息端点
     */
    @Test
    fun testInfoEndpoint(testContext: VertxTestContext) {
        webClient.get("/info")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("jvm")) { "Expected body to contain jvm" }
                        assert(body.containsKey("os")) { "Expected body to contain os" }
                        assert(body.containsKey("vertx")) { "Expected body to contain vertx" }
                        assert(body.containsKey("application")) { "Expected body to contain application" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试 EventBus 健康检查
     */
    @Test
    fun testEventBusHealthCheck(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.HEALTH_CHECK, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == true) { "Expected success to be true" }
                    val result = response.getJsonObject("result")
                    assert(result.getString("status") == "UP") { "Expected status to be UP but got ${result.getString("status")}" }
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试 EventBus 组件状态
     */
    @Test
    fun testEventBusComponentStatus(testContext: VertxTestContext) {
        val message = JsonObject()
            .put("component", "test-component")
            .put("status", true)
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.HEALTH_COMPONENT_STATUS, message) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == true) { "Expected success to be true" }
                    val result = response.getJsonObject("result")
                    assert(result.getString("component") == "test-component") { "Expected component to be test-component but got ${result.getString("component")}" }
                    assert(result.getBoolean("status") == true) { "Expected status to be true but got ${result.getBoolean("status")}" }
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    /**
     * 测试 EventBus 系统信息
     */
    @Test
    fun testEventBusSystemInfo(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.HEALTH_SYSTEM_INFO, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == true) { "Expected success to be true" }
                    val result = response.getJsonObject("result")
                    assert(result.containsKey("jvm")) { "Expected result to contain jvm" }
                    assert(result.containsKey("os")) { "Expected result to contain os" }
                    assert(result.containsKey("vertx")) { "Expected result to contain vertx" }
                    assert(result.containsKey("application")) { "Expected result to contain application" }
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
