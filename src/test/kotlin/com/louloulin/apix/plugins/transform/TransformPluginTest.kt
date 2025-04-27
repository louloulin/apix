package com.louloulin.apix.plugins.transform

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
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
class TransformPluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: TransformPlugin
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        
        // 创建插件配置
        val configJson = JsonObject()
            .put("request", JsonObject()
                .put("headers", JsonObject()
                    .put("X-Test-Header", "test-value")
                )
            )
            .put("response", JsonObject()
                .put("headers", JsonObject()
                    .put("X-Response-Header", "response-value")
                )
            )
        
        val config = PluginConfig("test-transform", configJson)
        plugin = TransformPlugin("test-transform", config)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `should add request headers`(testContext: VertxTestContext) {
        // 模拟路由上下文
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val headers = mock(io.vertx.core.MultiMap::class.java)
        
        // 设置模拟
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.headers()).thenReturn(headers)
        
        // 执行插件
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // 验证请求头被添加
                verify(headers).set("X-Test-Header", "test-value")
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
    
    @Test
    fun `should add response headers`(testContext: VertxTestContext) {
        // 模拟路由上下文
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val headers = mock(io.vertx.core.MultiMap::class.java)
        
        // 设置模拟
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.headers()).thenReturn(headers)
        
        // 执行插件
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // 验证头部结束处理器被添加
                verify(routingContext).addHeadersEndHandler(any())
                
                // 模拟响应头
                `when`(response.putHeader(anyString(), anyString())).thenReturn(response)
                
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
}
