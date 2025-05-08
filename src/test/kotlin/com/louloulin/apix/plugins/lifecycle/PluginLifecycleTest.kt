package com.louloulin.apix.plugins.lifecycle

import com.louloulin.apix.core.PluginChain
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.RoutingContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 测试插件生命周期钩子
 */
@RunWith(VertxUnitRunner::class)
class PluginLifecycleTest {
    
    private lateinit var vertx: Vertx
    
    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        vertx.close(testContext.asyncAssertSuccess())
    }
    
    /**
     * 测试请求生命周期钩子
     */
    @Test
    fun testLifecycleHooks(testContext: TestContext) {
        val async = testContext.async()
        
        // 创建测试插件
        val plugin = LifecycleTestPlugin("test-plugin", "test", JsonObject())
        
        // 创建插件链
        val pluginChain = PluginChain(vertx, listOf(plugin))
        
        // 创建模拟的RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        val response = mock(io.vertx.core.http.HttpServerResponse::class.java)
        
        `when`(context.request()).thenReturn(request)
        `when`(context.response()).thenReturn(response)
        `when`(response.ended()).thenReturn(false)
        
        // 执行插件链
        pluginChain.execute(context).onComplete { ar ->
            if (ar.succeeded()) {
                // 验证onRequest钩子被调用
                testContext.assertTrue(plugin.onRequestCalled.get())
                testContext.assertEquals(1, plugin.onRequestCount.get())
                
                // 模拟响应结束
                `when`(response.ended()).thenReturn(true)
                
                // 触发bodyEndHandler
                plugin.triggerBodyEndHandler()
                
                // 验证onResponse钩子被调用
                testContext.assertTrue(plugin.onResponseCalled.get())
                testContext.assertEquals(1, plugin.onResponseCount.get())
                
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    /**
     * 测试错误处理钩子
     */
    @Test
    fun testErrorHook(testContext: TestContext) {
        val async = testContext.async()
        
        // 创建测试插件
        val plugin = LifecycleTestPlugin("test-plugin", "test", JsonObject())
        plugin.shouldFail = true
        
        // 创建插件链
        val pluginChain = PluginChain(vertx, listOf(plugin))
        
        // 创建模拟的RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        val response = mock(io.vertx.core.http.HttpServerResponse::class.java)
        
        `when`(context.request()).thenReturn(request)
        `when`(context.response()).thenReturn(response)
        `when`(response.ended()).thenReturn(false)
        
        // 执行插件链
        pluginChain.execute(context).onComplete { ar ->
            if (ar.failed()) {
                // 验证onRequest钩子被调用
                testContext.assertTrue(plugin.onRequestCalled.get())
                
                // 验证onError钩子被调用
                testContext.assertTrue(plugin.onErrorCalled.get())
                testContext.assertEquals(1, plugin.onErrorCount.get())
                
                async.complete()
            } else {
                testContext.fail("Expected plugin execution to fail")
            }
        }
    }
    
    /**
     * 测试插件类，用于测试生命周期钩子
     */
    class LifecycleTestPlugin(
        override val id: String,
        override val type: String,
        jsonConfig: JsonObject
    ) : Plugin {
        override val config: PluginConfig = PluginConfig(id, type, jsonConfig)
        
        // 跟踪钩子调用
        val onRequestCalled = AtomicBoolean(false)
        val onResponseCalled = AtomicBoolean(false)
        val onErrorCalled = AtomicBoolean(false)
        
        val onRequestCount = AtomicInteger(0)
        val onResponseCount = AtomicInteger(0)
        val onErrorCount = AtomicInteger(0)
        
        // 控制是否失败
        var shouldFail = false
        
        // 存储bodyEndHandler
        private var bodyEndHandler: (() -> Unit)? = null
        
        override fun onRequest(context: RoutingContext): Future<Void> {
            onRequestCalled.set(true)
            onRequestCount.incrementAndGet()
            
            // 存储bodyEndHandler
            context.addBodyEndHandler { 
                bodyEndHandler?.invoke()
            }
            
            return if (shouldFail) {
                Future.failedFuture("Test failure")
            } else {
                Future.succeededFuture()
            }
        }
        
        override fun onResponse(context: RoutingContext): Future<Void> {
            onResponseCalled.set(true)
            onResponseCount.incrementAndGet()
            return Future.succeededFuture()
        }
        
        override fun onError(context: RoutingContext, error: Throwable): Future<Void> {
            onErrorCalled.set(true)
            onErrorCount.incrementAndGet()
            return Future.succeededFuture()
        }
        
        override fun execute(context: RoutingContext): Future<Void> {
            return onRequest(context)
        }
        
        override fun initialize(vertx: Vertx): Future<Void> {
            return Future.succeededFuture()
        }
        
        override fun shutdown() {
            // 不需要实现
        }
        
        /**
         * 触发bodyEndHandler，用于测试
         */
        fun triggerBodyEndHandler() {
            bodyEndHandler?.invoke()
        }
    }
}
