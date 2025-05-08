package com.louloulin.apix.plugins.condition

import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.impl.HttpServerRequestWrapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.MultiMap

/**
 * 条件解析器测试
 */
class ConditionParserTest {
    
    private val parser = ConditionParser.getInstance()
    
    @Test
    fun testEmptyCondition() {
        val context = mockRoutingContext("/test", "GET")
        val condition = JsonObject()
        
        assertTrue(parser.evaluate(condition, context))
    }
    
    @Test
    fun testPathCondition() {
        val context = mockRoutingContext("/api/users", "GET")
        
        // 精确匹配
        var condition = JsonObject().put("path", "/api/users")
        assertTrue(parser.evaluate(condition, context))
        
        // 通配符匹配
        condition = JsonObject().put("path", "/api/*")
        assertTrue(parser.evaluate(condition, context))
        
        // 不匹配
        condition = JsonObject().put("path", "/admin/*")
        assertFalse(parser.evaluate(condition, context))
        
        // 数组匹配
        val paths = JsonArray().add("/api/users").add("/api/products")
        condition = JsonObject().put("path", paths)
        assertTrue(parser.evaluate(condition, context))
    }
    
    @Test
    fun testMethodCondition() {
        val context = mockRoutingContext("/api/users", "POST")
        
        // 精确匹配
        var condition = JsonObject().put("method", "POST")
        assertTrue(parser.evaluate(condition, context))
        
        // 不区分大小写
        condition = JsonObject().put("method", "post")
        assertTrue(parser.evaluate(condition, context))
        
        // 不匹配
        condition = JsonObject().put("method", "GET")
        assertFalse(parser.evaluate(condition, context))
        
        // 数组匹配
        val methods = JsonArray().add("POST").add("PUT")
        condition = JsonObject().put("method", methods)
        assertTrue(parser.evaluate(condition, context))
    }
    
    @Test
    fun testHeadersCondition() {
        val context = mockRoutingContext("/api/users", "GET", mapOf(
            "Content-Type" to "application/json",
            "X-API-Key" to "test-key"
        ))
        
        // 单个头匹配
        var condition = JsonObject().put("headers", JsonObject().put("Content-Type", "application/json"))
        assertTrue(parser.evaluate(condition, context))
        
        // 多个头匹配
        condition = JsonObject().put("headers", JsonObject()
            .put("Content-Type", "application/json")
            .put("X-API-Key", "test-key")
        )
        assertTrue(parser.evaluate(condition, context))
        
        // 通配符匹配
        condition = JsonObject().put("headers", JsonObject().put("X-API-Key", "*"))
        assertTrue(parser.evaluate(condition, context))
        
        // 不匹配
        condition = JsonObject().put("headers", JsonObject().put("Authorization", "Bearer token"))
        assertFalse(parser.evaluate(condition, context))
    }
    
    @Test
    fun testCombinedConditions() {
        val context = mockRoutingContext("/api/users", "POST", mapOf(
            "Content-Type" to "application/json"
        ))
        
        // 组合条件 - 全部匹配
        var condition = JsonObject()
            .put("path", "/api/users")
            .put("method", "POST")
            .put("headers", JsonObject().put("Content-Type", "application/json"))
        
        assertTrue(parser.evaluate(condition, context))
        
        // 组合条件 - 部分不匹配
        condition = JsonObject()
            .put("path", "/api/users")
            .put("method", "GET")
            .put("headers", JsonObject().put("Content-Type", "application/json"))
        
        assertFalse(parser.evaluate(condition, context))
    }
    
    /**
     * 创建模拟的RoutingContext
     */
    private fun mockRoutingContext(path: String, method: String, headers: Map<String, String> = emptyMap()): RoutingContext {
        val context = mock(RoutingContext::class.java)
        val request = mock(HttpServerRequest::class.java)
        
        `when`(context.request()).thenReturn(request)
        `when`(request.path()).thenReturn(path)
        `when`(request.method()).thenReturn(HttpMethod.valueOf(method))
        
        // 设置请求头
        val headerMap = MultiMap.caseInsensitiveMultiMap()
        headers.forEach { (key, value) -> headerMap.add(key, value) }
        `when`(request.headers()).thenReturn(headerMap)
        
        // 设置请求参数
        val paramMap = MultiMap.caseInsensitiveMultiMap()
        `when`(request.params()).thenReturn(paramMap)
        
        return context
    }
}
