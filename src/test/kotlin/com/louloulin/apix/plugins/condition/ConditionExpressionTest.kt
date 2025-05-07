package com.louloulin.apix.plugins.condition

import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * 条件表达式测试
 */
class ConditionExpressionTest {
    
    @Test
    fun testPathCondition() {
        // 创建模拟的 RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        
        // 测试精确匹配
        `when`(request.path()).thenReturn("/api/users")
        val exactPathCondition = PathCondition("/api/users")
        assertTrue(exactPathCondition.evaluate(context))
        
        // 测试不匹配
        `when`(request.path()).thenReturn("/api/products")
        assertFalse(exactPathCondition.evaluate(context))
        
        // 测试通配符匹配
        val wildcardPathCondition = PathCondition("/api/*")
        `when`(request.path()).thenReturn("/api/users")
        assertTrue(wildcardPathCondition.evaluate(context))
        `when`(request.path()).thenReturn("/api/products")
        assertTrue(wildcardPathCondition.evaluate(context))
        `when`(request.path()).thenReturn("/other/path")
        assertFalse(wildcardPathCondition.evaluate(context))
    }
    
    @Test
    fun testMethodCondition() {
        // 创建模拟的 RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        
        // 测试单个方法匹配
        `when`(request.method()).thenReturn(HttpMethod.GET)
        val getMethodCondition = MethodCondition(listOf(HttpMethod.GET))
        assertTrue(getMethodCondition.evaluate(context))
        
        // 测试不匹配
        `when`(request.method()).thenReturn(HttpMethod.POST)
        assertFalse(getMethodCondition.evaluate(context))
        
        // 测试多个方法匹配
        val multiMethodCondition = MethodCondition(listOf(HttpMethod.GET, HttpMethod.POST))
        `when`(request.method()).thenReturn(HttpMethod.GET)
        assertTrue(multiMethodCondition.evaluate(context))
        `when`(request.method()).thenReturn(HttpMethod.POST)
        assertTrue(multiMethodCondition.evaluate(context))
        `when`(request.method()).thenReturn(HttpMethod.DELETE)
        assertFalse(multiMethodCondition.evaluate(context))
    }
    
    @Test
    fun testHeaderCondition() {
        // 创建模拟的 RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        
        // 测试头信息匹配
        `when`(request.getHeader("Content-Type")).thenReturn("application/json")
        val headerCondition = HeaderCondition("Content-Type", "application/json")
        assertTrue(headerCondition.evaluate(context))
        
        // 测试不匹配
        `when`(request.getHeader("Content-Type")).thenReturn("text/plain")
        assertFalse(headerCondition.evaluate(context))
        
        // 测试头信息不存在
        `when`(request.getHeader("Content-Type")).thenReturn(null)
        assertFalse(headerCondition.evaluate(context))
    }
    
    @Test
    fun testParamCondition() {
        // 创建模拟的 RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        
        // 测试参数匹配
        `when`(request.getParam("id")).thenReturn("123")
        val paramCondition = ParamCondition("id", "123")
        assertTrue(paramCondition.evaluate(context))
        
        // 测试不匹配
        `when`(request.getParam("id")).thenReturn("456")
        assertFalse(paramCondition.evaluate(context))
        
        // 测试参数不存在
        `when`(request.getParam("id")).thenReturn(null)
        assertFalse(paramCondition.evaluate(context))
    }
    
    @Test
    fun testCompositeConditionAnd() {
        // 创建模拟的 RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        
        // 设置请求属性
        `when`(request.path()).thenReturn("/api/users")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        
        // 创建条件
        val pathCondition = PathCondition("/api/users")
        val methodCondition = MethodCondition(listOf(HttpMethod.GET))
        
        // 创建 AND 复合条件
        val andCondition = CompositeCondition(
            listOf(pathCondition, methodCondition),
            CompositeCondition.Operator.AND
        )
        
        // 测试全部匹配
        assertTrue(andCondition.evaluate(context))
        
        // 测试部分匹配
        `when`(request.method()).thenReturn(HttpMethod.POST)
        assertFalse(andCondition.evaluate(context))
    }
    
    @Test
    fun testCompositeConditionOr() {
        // 创建模拟的 RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        
        // 设置请求属性
        `when`(request.path()).thenReturn("/api/users")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        
        // 创建条件
        val pathCondition = PathCondition("/api/users")
        val methodCondition = MethodCondition(listOf(HttpMethod.POST))
        
        // 创建 OR 复合条件
        val orCondition = CompositeCondition(
            listOf(pathCondition, methodCondition),
            CompositeCondition.Operator.OR
        )
        
        // 测试部分匹配
        assertTrue(orCondition.evaluate(context))
        
        // 测试全部不匹配
        `when`(request.path()).thenReturn("/api/products")
        assertFalse(orCondition.evaluate(context))
    }
    
    @Test
    fun testConditionExpressionParser() {
        // 创建模拟的 RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        
        // 设置请求属性
        `when`(request.path()).thenReturn("/api/users")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        `when`(request.getHeader("Content-Type")).thenReturn("application/json")
        
        // 创建配置
        val config = JsonObject()
            .put("path", "/api/users")
            .put("method", JsonArray().add("GET").add("POST"))
            .put("headers", JsonObject().put("Content-Type", "application/json"))
            .put("operator", "AND")
        
        // 解析条件表达式
        val condition = ConditionExpressionParser.parse(config)
        
        // 测试匹配
        assertTrue(condition.evaluate(context))
        
        // 测试不匹配
        `when`(request.path()).thenReturn("/api/products")
        assertFalse(condition.evaluate(context))
    }
}
