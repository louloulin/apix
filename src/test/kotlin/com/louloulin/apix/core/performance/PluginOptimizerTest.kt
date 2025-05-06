package com.louloulin.apix.core.performance

import com.louloulin.apix.core.plugin.Plugin
import com.louloulin.apix.core.plugin.PluginContext
import com.louloulin.apix.core.plugin.PluginOptimizer
import com.louloulin.apix.core.plugin.PluginType
import io.vertx.core.Future
import io.vertx.core.Vertx
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

/**
 * 插件优化器测试
 */
@ExtendWith(VertxExtension::class)
class PluginOptimizerTest {
    private lateinit var vertx: Vertx
    private lateinit var pluginOptimizer: PluginOptimizer
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        pluginOptimizer = PluginOptimizer.getInstance(vertx)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testGetPluginStats(testContext: VertxTestContext) {
        // 获取插件统计信息
        val stats = pluginOptimizer.getPluginStats()
        
        testContext.verify {
            // 验证统计信息是否为JsonObject
            assert(stats is JsonObject) { "应该返回JsonObject" }
            
            // 验证统计信息是否包含预期的字段
            assert(stats.containsKey("executionCounts")) { "应该包含executionCounts字段" }
            assert(stats.containsKey("averageExecutionTimes")) { "应该包含averageExecutionTimes字段" }
            assert(stats.containsKey("cacheSize")) { "应该包含cacheSize字段" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testPluginCache(testContext: VertxTestContext) {
        // 添加到缓存
        val testValue = "test value"
        pluginOptimizer.putInCache("testKey", testValue)
        
        // 从缓存获取
        val cachedValue = pluginOptimizer.getFromCache("testKey", String::class.java)
        
        testContext.verify {
            // 验证缓存值是否正确
            assert(cachedValue == testValue) { "应该返回正确的缓存值" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testPluginDependencies(testContext: VertxTestContext) {
        // 添加依赖关系
        pluginOptimizer.addDependency("pluginA", "pluginB")
        pluginOptimizer.addDependency("pluginA", "pluginC")
        
        // 获取依赖
        val dependencies = pluginOptimizer.getDependencies("pluginA")
        
        testContext.verify {
            // 验证依赖关系是否正确
            assert(dependencies.size == 2) { "应该有2个依赖" }
            assert(dependencies.contains("pluginB")) { "应该包含pluginB" }
            assert(dependencies.contains("pluginC")) { "应该包含pluginC" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testExecutePluginConditionally(testContext: VertxTestContext) {
        // 创建模拟插件
        val plugin = mock(Plugin::class.java)
        `when`(plugin.name).thenReturn("testPlugin")
        `when`(plugin.type).thenReturn(PluginType.SECURITY)
        `when`(plugin.handleRequest(Mockito.any())).thenReturn(Future.succeededFuture())
        
        // 创建模拟路由上下文
        val context = mock(RoutingContext::class.java)
        
        // 条件为true的情况
        pluginOptimizer.executePluginConditionally(plugin, context) { true }.onComplete { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    // 验证插件是否被执行
                    verify(plugin).handleRequest(context)
                    
                    // 条件为false的情况
                    pluginOptimizer.executePluginConditionally(plugin, context) { false }.onComplete { ar2 ->
                        if (ar2.succeeded()) {
                            // 验证插件没有被再次执行（仍然是1次）
                            verify(plugin, Mockito.times(1)).handleRequest(context)
                            
                            testContext.completeNow()
                        } else {
                            testContext.failNow(ar2.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
}
