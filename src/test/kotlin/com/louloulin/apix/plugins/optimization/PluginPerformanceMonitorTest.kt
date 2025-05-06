package com.louloulin.apix.plugins.optimization

import com.louloulin.apix.plugins.Plugin
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
import java.util.concurrent.TimeUnit

/**
 * 插件性能监控器测试
 */
@ExtendWith(VertxExtension::class)
class PluginPerformanceMonitorTest {
    private lateinit var vertx: Vertx
    private lateinit var performanceMonitor: PluginPerformanceMonitor
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        performanceMonitor = PluginPerformanceMonitor.getInstance(vertx)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testMonitorPluginExecution(testContext: VertxTestContext) {
        // 创建模拟插件
        val plugin = mock(Plugin::class.java)
        `when`(plugin.id).thenReturn("testPlugin")
        `when`(plugin.type).thenReturn("test")
        
        // 创建模拟路由上下文
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        
        // 监控插件执行
        performanceMonitor.monitorPluginExecution(plugin, context) {
            // 模拟插件执行
            Future.succeededFuture("result")
        }.onComplete { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    // 验证执行结果
                    assert(ar.result() == "result") { "应该返回正确的执行结果" }
                    
                    // 获取性能统计信息
                    val stats = performanceMonitor.getPluginPerformanceStats()
                    
                    // 验证统计信息是否包含插件执行记录
                    val plugins = stats.getJsonArray("plugins")
                    assert(plugins.size() > 0) { "应该有插件执行记录" }
                    
                    val pluginStat = plugins.getJsonObject(0)
                    assert(pluginStat.getString("id") == "testPlugin") { "应该记录正确的插件ID" }
                    assert(pluginStat.getInteger("executions") == 1) { "应该记录1次执行" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
    
    @Test
    fun testMonitorPluginExecutionWithError(testContext: VertxTestContext) {
        // 创建模拟插件
        val plugin = mock(Plugin::class.java)
        `when`(plugin.id).thenReturn("testPlugin")
        `when`(plugin.type).thenReturn("test")
        
        // 创建模拟路由上下文
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        
        // 监控插件执行（带错误）
        performanceMonitor.monitorPluginExecution(plugin, context) {
            // 模拟插件执行失败
            Future.failedFuture<String>("Test error")
        }.onComplete { ar ->
            testContext.verify {
                // 验证执行结果
                assert(ar.failed()) { "应该返回失败的执行结果" }
                assert(ar.cause().message == "Test error") { "应该返回正确的错误消息" }
                
                // 获取性能统计信息
                val stats = performanceMonitor.getPluginPerformanceStats()
                
                // 验证统计信息是否包含插件执行记录
                val plugins = stats.getJsonArray("plugins")
                assert(plugins.size() > 0) { "应该有插件执行记录" }
                
                val pluginStat = plugins.getJsonObject(0)
                assert(pluginStat.getString("id") == "testPlugin") { "应该记录正确的插件ID" }
                assert(pluginStat.getInteger("executions") == 1) { "应该记录1次执行" }
                assert(pluginStat.getInteger("errors") == 1) { "应该记录1次错误" }
                
                testContext.completeNow()
            }
        }
        
        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
    
    @Test
    fun testGetPluginPerformanceStats(testContext: VertxTestContext) {
        // 获取插件性能统计信息
        val stats = performanceMonitor.getPluginPerformanceStats()
        
        testContext.verify {
            // 验证统计信息是否为JsonObject
            assert(stats is JsonObject) { "应该返回JsonObject" }
            
            // 验证统计信息是否包含预期的字段
            assert(stats.containsKey("totalPlugins")) { "应该包含totalPlugins字段" }
            assert(stats.containsKey("totalExecutions")) { "应该包含totalExecutions字段" }
            assert(stats.containsKey("totalExecutionTime")) { "应该包含totalExecutionTime字段" }
            assert(stats.containsKey("maxExecutionTime")) { "应该包含maxExecutionTime字段" }
            assert(stats.containsKey("avgExecutionTime")) { "应该包含avgExecutionTime字段" }
            assert(stats.containsKey("plugins")) { "应该包含plugins字段" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testSetSlowPluginThreshold(testContext: VertxTestContext) {
        // 设置慢插件阈值
        performanceMonitor.setSlowPluginThreshold(200)
        
        testContext.completeNow()
    }
    
    @Test
    fun testResetStats(testContext: VertxTestContext) {
        // 创建模拟插件
        val plugin = mock(Plugin::class.java)
        `when`(plugin.id).thenReturn("testPlugin")
        `when`(plugin.type).thenReturn("test")
        
        // 创建模拟路由上下文
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(HttpMethod.GET)
        
        // 监控插件执行
        performanceMonitor.monitorPluginExecution(plugin, context) {
            // 模拟插件执行
            Future.succeededFuture("result")
        }.onComplete { ar ->
            if (ar.succeeded()) {
                // 重置统计信息
                performanceMonitor.resetStats()
                
                // 获取统计信息
                val stats = performanceMonitor.getPluginPerformanceStats()
                
                testContext.verify {
                    // 验证统计信息是否已重置
                    assert(stats.getInteger("totalExecutions") == 0) { "总执行次数应该已重置" }
                    assert(stats.getJsonArray("plugins").size() == 0) { "插件统计应该已重置" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
}
