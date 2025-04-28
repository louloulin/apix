package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import io.vertx.ext.web.RequestBody
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq

@ExtendWith(VertxExtension::class)
class ResponseCachePluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: ResponseCachePlugin

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()

        // 创建插件配置
        val configJson = JsonObject()
            .put("ttl_seconds", 300)
            .put("max_size", 1000)
            .put("methods", JsonArray().add("POST"))
            .put("status_codes", JsonArray().add(200))

        val config = PluginConfig("test-cache", "response-cache", configJson)
        plugin = ResponseCachePlugin("test-cache", config, vertx)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `should add cache headers to response`(testContext: VertxTestContext) {
        // 模拟路由上下文
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val requestBody = mock(RequestBody::class.java)

        // 设置模拟
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.method()).thenReturn(HttpMethod.POST)
        `when`(routingContext.body()).thenReturn(requestBody)
        `when`(requestBody.buffer()).thenReturn(Buffer.buffer("{\"test\":\"value\"}"))
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)

        // 执行插件
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // 验证缓存头被添加
                verify(response).putHeader("X-Cache", "MISS")

                // 验证主体结束处理器被添加
                verify(routingContext).addBodyEndHandler(any())

                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    // Note: ResponseCachePlugin doesn't have a clearCache method
    // This test has been removed
}
