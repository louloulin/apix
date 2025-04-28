package com.louloulin.apix.eventbus

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.ConfigVerticle
import com.louloulin.apix.core.verticle.MonitorVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * 测试 EventBus 通信
 */
@ExtendWith(VertxExtension::class)
class EventBusTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 部署测试 Verticle
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(MonitorVerticle()) }
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
    
    @Test
    fun testMetricsEventBus(testContext: VertxTestContext) {
        // 测试获取指标
        vertx.eventBus().request<JsonObject>(EventBusAddresses.METRICS_GET, JsonObject().put("type", "system")) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assert(response.getBoolean("success") == true) { "Expected success to be true" }
                    val result = response.getJsonObject("result")
                    assert(result != null) { "Expected result to be non-null" }
                    assert(result.getJsonObject("jvm") != null) { "Expected JVM metrics to be present" }
                    assert(result.getJsonObject("system") != null) { "Expected system metrics to be present" }
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
    fun testConfigEventBus(testContext: VertxTestContext) {
        // 测试设置配置
        val testKey = "test.key"
        val testValue = "test.value"
        
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_SET, 
            JsonObject().put("key", testKey).put("value", testValue)
        ) { setAr ->
            if (setAr.succeeded()) {
                val setResponse = setAr.result().body()
                testContext.verify {
                    assert(setResponse.getBoolean("success") == true) { "Expected set success to be true" }
                }
                
                // 获取配置
                vertx.eventBus().request<JsonObject>(
                    EventBusAddresses.CONFIG_GET,
                    JsonObject().put("key", testKey)
                ) { getAr ->
                    if (getAr.succeeded()) {
                        val getResponse = getAr.result().body()
                        testContext.verify {
                            assert(getResponse.getBoolean("success") == true) { "Expected get success to be true" }
                            assert(getResponse.getString("result") == testValue) { 
                                "Expected result to be $testValue but was ${getResponse.getString("result")}" 
                            }
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(getAr.cause())
                    }
                }
            } else {
                testContext.failNow(setAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
