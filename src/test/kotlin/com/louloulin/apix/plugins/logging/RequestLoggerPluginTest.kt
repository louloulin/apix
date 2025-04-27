package com.louloulin.apix.plugins.logging

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import io.vertx.core.net.SocketAddress
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any

@ExtendWith(VertxExtension::class)
class RequestLoggerPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: RequestLoggerPlugin

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()

        // 创建插件配置
        val configJson = JsonObject()
            .put("log_level", "info")
            .put("include_headers", true)
            .put("include_body", false)
            .put("mask_headers", JsonArray().add("authorization").add("x-api-key"))

        val config = PluginConfig("test-logger", configJson)
        plugin = RequestLoggerPlugin("test-logger", config)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    @Disabled("Needs further investigation")
    fun `should log request information`(testContext: VertxTestContext) {
        // 模拟路由上下文
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val remoteAddress = mock(SocketAddress::class.java)
        val headers = mock(io.vertx.core.MultiMap::class.java)

        // 设置模拟
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.method()).thenReturn(HttpMethod.GET)
        `when`(request.path()).thenReturn("/test")
        `when`(request.query()).thenReturn("param=value")
        `when`(request.remoteAddress()).thenReturn(remoteAddress)
        `when`(remoteAddress.hostAddress()).thenReturn("127.0.0.1")
        `when`(request.headers()).thenReturn(headers)

        // 执行插件
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // 验证主体结束处理器被添加
                verify(routingContext).addBodyEndHandler(any())

                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
}
