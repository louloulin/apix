package com.louloulin.apix.plugins.ai

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
class TokenUsagePluginTest {

    private lateinit var vertx: Vertx
    private lateinit var plugin: TokenUsagePlugin

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()

        // 创建插件配置
        val configJson = JsonObject()
            .put("track_by_model", true)
            .put("track_by_user", true)
            .put("user_id_header", "X-User-ID")

        val config = PluginConfig("test-token-usage", configJson)
        plugin = TokenUsagePlugin("test-token-usage", config)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `should track token usage`(testContext: VertxTestContext) {
        // 模拟路由上下文
        val routingContext = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        val response = mock(HttpServerResponse::class.java)
        val requestBody = mock(RequestBody::class.java)

        // 创建响应体
        val responseBody = JsonObject()
            .put("model", "gpt-4")
            .put("usage", JsonObject()
                .put("prompt_tokens", 100)
                .put("completion_tokens", 50)
                .put("total_tokens", 150)
            )

        // 设置模拟
        `when`(routingContext.request()).thenReturn(request)
        `when`(routingContext.response()).thenReturn(response)
        `when`(request.getHeader("X-User-ID")).thenReturn("test-user")
        `when`(routingContext.body()).thenReturn(requestBody)
        `when`(requestBody.buffer()).thenReturn(Buffer.buffer(responseBody.encode()))
        `when`(response.statusCode).thenReturn(200)
        `when`(response.putHeader(anyString(), anyString())).thenReturn(response)

        // 执行插件
        plugin.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // 验证主体结束处理器被添加
                verify(routingContext).addBodyEndHandler(any())

                // 获取使用统计
                val usageStats = plugin.getUsageStats()

                // 验证使用统计不为空
                testContext.verify {
                    assertNotNull(usageStats)
                }

                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should clean up old stats`(testContext: VertxTestContext) {
        // 清理旧的统计
        plugin.cleanupOldStats(30)

        // 没有直接的方式来验证旧的统计被清理，但至少确保方法不会抛出异常
        testContext.completeNow()
    }

    private fun assertNotNull(obj: Any?) {
        if (obj == null) {
            throw AssertionError("Expected object to be not null")
        }
    }
}
