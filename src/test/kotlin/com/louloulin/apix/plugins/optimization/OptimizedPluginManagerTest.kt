package com.louloulin.apix.plugins.optimization

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginChain
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
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
 * 优化的插件管理器测试
 */
@ExtendWith(VertxExtension::class)
class OptimizedPluginManagerTest {
    private lateinit var vertx: Vertx
    private lateinit var pluginManager: OptimizedPluginManager
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        pluginManager = OptimizedPluginManager.getInstance(vertx)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testRegisterPlugin(testContext: VertxTestContext) {
        // 创建模拟插件
        val plugin = mock(Plugin::class.java)
        `when`(plugin.id).thenReturn("testPlugin")
        `when`(plugin.type).thenReturn("test")
        
        // 注册插件
        pluginManager.registerPlugin(plugin).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    // 验证插件是否已注册
                    val registeredPlugin = pluginManager.getPlugin("testPlugin")
                    assert(registeredPlugin != null) { "插件应该已注册" }
                    assert(registeredPlugin === plugin) { "应该返回相同的插件实例" }
                    
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
    fun testRegisterPluginChain(testContext: VertxTestContext) {
        // 创建模拟插件链
        val chain = mock(PluginChain::class.java)
        `when`(chain.id).thenReturn("testChain")
        `when`(chain.pluginIds).thenReturn(listOf("plugin1", "plugin2"))
        
        // 注册插件链
        pluginManager.registerPluginChain(chain).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    // 验证插件链是否已注册
                    val registeredChain = pluginManager.getPluginChain("testChain")
                    assert(registeredChain != null) { "插件链应该已注册" }
                    assert(registeredChain === chain) { "应该返回相同的插件链实例" }
                    
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
    fun testGetPluginStats(testContext: VertxTestContext) {
        // 获取插件统计信息
        val stats = pluginManager.getPluginStats()
        
        testContext.verify {
            // 验证统计信息是否为JsonObject
            assert(stats is JsonObject) { "应该返回JsonObject" }
            
            // 验证统计信息是否包含预期的字段
            assert(stats.containsKey("totalPlugins")) { "应该包含totalPlugins字段" }
            assert(stats.containsKey("totalPluginChains")) { "应该包含totalPluginChains字段" }
            assert(stats.containsKey("totalPathMappings")) { "应该包含totalPathMappings字段" }
            assert(stats.containsKey("optimizerStats")) { "应该包含optimizerStats字段" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testResetStats(testContext: VertxTestContext) {
        // 重置统计信息
        pluginManager.resetStats()
        
        // 获取统计信息
        val stats = pluginManager.getPluginStats()
        
        testContext.verify {
            // 验证统计信息是否已重置
            val optimizerStats = stats.getJsonObject("optimizerStats")
            assert(optimizerStats.getJsonObject("executionCounts").size() == 0) { "执行计数应该已重置" }
            assert(optimizerStats.getJsonObject("averageExecutionTimes").size() == 0) { "执行时间应该已重置" }
            
            testContext.completeNow()
        }
    }
}
