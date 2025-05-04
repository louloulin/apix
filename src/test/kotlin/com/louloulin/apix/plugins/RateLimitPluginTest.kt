package com.louloulin.apix.plugins

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.plugin.PluginContext
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 限流插件测试
 */
@ExtendWith(VertxExtension::class)
class RateLimitPluginTest {
    private lateinit var vertx: Vertx
    private lateinit var plugin: RateLimitPlugin
    private lateinit var context: PluginContext
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        plugin = RateLimitPlugin()
        context = mock(PluginContext::class.java)
        
        // 模拟 PluginContext
        `when`(context.vertx()).thenReturn(vertx)
        
        // 注册事件总线处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RATE_LIMIT_CREATE) { message ->
            message.reply(JsonObject().put("created", true))
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RATE_LIMIT_CHECK) { message ->
            val key = message.body().getString("key")
            val type = message.body().getString("type")
            
            // 模拟限流检查结果
            if (key.contains("allowed")) {
                message.reply(JsonObject()
                    .put("allowed", true)
                    .put("key", key)
                    .put("type", type)
                    .put("tokens", 1)
                    .put("remaining", 99)
                    .put("capacity", 100)
                    .put("reset", System.currentTimeMillis() + 1000)
                )
            } else {
                message.reply(JsonObject()
                    .put("allowed", false)
                    .put("key", key)
                    .put("type", type)
                    .put("tokens", 1)
                    .put("remaining", 0)
                    .put("capacity", 100)
                    .put("reset", System.currentTimeMillis() + 1000)
                )
            }
        }
        
        // 初始化插件
        val config = JsonObject()
            .put("enabled", true)
            .put("algorithm", "token_bucket")
            .put("keyPrefix", "test:")
            .put("keyResolver", "ip")
            .put("limit", 100)
            .put("capacity", 100)
            .put("refillRate", 1.0)
            .put("refillInterval", 1000L)
            .put("statusCode", 429)
            .put("errorMessage", "Too Many Requests")
            .put("headers", true)
        
        plugin.init(context, config).onComplete { ar ->
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
    fun `test handle request allowed`(testContext: VertxTestContext) {
        // 模拟 RoutingContext
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        val response = mock(io.vertx.core.http.HttpServerResponse::class.java)
        val remoteAddress = mock(io.vertx.core.net.SocketAddress::class.java)
        
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.remoteAddress()).thenReturn(remoteAddress)
        `when`(remoteAddress.host()).thenReturn("allowed-ip")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        `when`(request.path()).thenReturn("/api/test")
        `when`(request.getHeader("X-API-Key")).thenReturn("test-key")
        `when`(response.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(response)
        
        // 处理请求
        plugin.handleRequest(routingContext).onComplete { ar ->
            testContext.verify {
                assertTrue(ar.succeeded())
                
                // 验证响应头
                verify(response).putHeader("X-RateLimit-Limit", "100")
                verify(response).putHeader("X-RateLimit-Remaining", "99")
                verify(response).putHeader(Mockito.eq("X-RateLimit-Reset"), Mockito.anyString())
            }
            testContext.completeNow()
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test handle request limited`(testContext: VertxTestContext) {
        // 模拟 RoutingContext
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        val response = mock(io.vertx.core.http.HttpServerResponse::class.java)
        val remoteAddress = mock(io.vertx.core.net.SocketAddress::class.java)
        
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.remoteAddress()).thenReturn(remoteAddress)
        `when`(remoteAddress.host()).thenReturn("limited-ip")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        `when`(request.path()).thenReturn("/api/test")
        `when`(request.getHeader("X-API-Key")).thenReturn("test-key")
        `when`(response.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(response)
        `when`(response.setStatusCode(Mockito.anyInt())).thenReturn(response)
        `when`(response.end(Mockito.anyString())).thenReturn(Future.succeededFuture())
        
        // 处理请求
        plugin.handleRequest(routingContext).onComplete { ar ->
            testContext.verify {
                assertTrue(ar.succeeded())
                
                // 验证响应
                verify(response).setStatusCode(429)
                verify(response).putHeader("Content-Type", "application/json")
                verify(response).end(Mockito.argThat<String> { json ->
                    val jsonObject = JsonObject(json)
                    jsonObject.getString("error") == "Too Many Requests" && jsonObject.getInteger("status") == 429
                })
                
                // 验证响应头
                verify(response).putHeader("X-RateLimit-Limit", "100")
                verify(response).putHeader("X-RateLimit-Remaining", "0")
                verify(response).putHeader(Mockito.eq("X-RateLimit-Reset"), Mockito.anyString())
            }
            testContext.completeNow()
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test plugin properties`(testContext: VertxTestContext) {
        testContext.verify {
            assertEquals("rate-limit", plugin.name)
            assertEquals("1.0.0", plugin.version)
            assertEquals("限流插件，支持多种限流算法", plugin.description)
        }
        testContext.completeNow()
    }
}
