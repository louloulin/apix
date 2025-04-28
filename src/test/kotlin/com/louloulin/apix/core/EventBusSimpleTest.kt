package com.louloulin.apix.core

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

/**
 * 简单的 EventBus 通信测试
 */
@ExtendWith(VertxExtension::class)
class EventBusSimpleTest {
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
    }
}
