package com.louloulin.apix.core

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.ConfigVerticle
import com.louloulin.apix.core.verticle.DeploymentVerticle
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
 * 测试 EventBus 通信的集成测试
 */
@ExtendWith(VertxExtension::class)
class EventBusIntegrationTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 部署测试 Verticle
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(MonitorVerticle()) }
            .compose { vertx.deployVerticle(DeploymentVerticle()) }
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
    fun testConfigAndMonitorIntegration(testContext: VertxTestContext) {
        // 测试设置配置
        val testKey = "test.integration.key"
        val testValue = "test.integration.value"
        
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_SET, 
            JsonObject().put("key", testKey).put("value", testValue)
        ) { setAr ->
            if (setAr.succeeded()) {
                val setResponse = setAr.result().body()
                testContext.verify {
                    assert(setResponse.getBoolean("success") == true) { "Expected set success to be true" }
                }
                
                // 获取指标
                vertx.eventBus().request<JsonObject>(EventBusAddresses.METRICS_GET, JsonObject().put("type", "system")) { metricsAr ->
                    if (metricsAr.succeeded()) {
                        val metricsResponse = metricsAr.result().body()
                        testContext.verify {
                            assert(metricsResponse.getBoolean("success") == true) { "Expected metrics success to be true" }
                            val result = metricsResponse.getJsonObject("result")
                            assert(result != null) { "Expected result to be non-null" }
                            assert(result.getJsonObject("jvm") != null) { "Expected JVM metrics to be present" }
                            
                            // 完成测试
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(metricsAr.cause())
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
