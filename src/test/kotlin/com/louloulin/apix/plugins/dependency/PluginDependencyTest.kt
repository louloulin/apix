package com.louloulin.apix.plugins.dependency

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginRegistry
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.RoutingContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 插件依赖管理测试
 */
@RunWith(VertxUnitRunner::class)
class PluginDependencyTest {
    
    private lateinit var vertx: Vertx
    private lateinit var pluginRegistry: PluginRegistry
    private lateinit var dependencyManager: PluginDependencyManager
    
    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        pluginRegistry = PluginRegistry.getInstance(vertx)
        dependencyManager = PluginDependencyManager.getInstance(vertx, pluginRegistry)
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        vertx.close(testContext.asyncAssertSuccess())
    }
    
    /**
     * 测试添加依赖关系
     */
    @Test
    fun testAddDependencies(testContext: TestContext) {
        val async = testContext.async()
        
        // 添加依赖关系
        val result = dependencyManager.addDependencies("plugin1", setOf("plugin2", "plugin3"))
        
        // 验证添加成功
        testContext.assertTrue(result)
        
        // 验证依赖关系
        val dependencies = dependencyManager.getDependencies("plugin1")
        testContext.assertEquals(2, dependencies.size)
        testContext.assertTrue(dependencies.contains("plugin2"))
        testContext.assertTrue(dependencies.contains("plugin3"))
        
        async.complete()
    }
    
    /**
     * 测试循环依赖检测
     */
    @Test
    fun testCyclicDependencyDetection(testContext: TestContext) {
        val async = testContext.async()
        
        // 添加依赖关系
        dependencyManager.addDependencies("plugin1", setOf("plugin2"))
        dependencyManager.addDependencies("plugin2", setOf("plugin3"))
        
        // 尝试添加循环依赖
        val result = dependencyManager.addDependencies("plugin3", setOf("plugin1"))
        
        // 验证添加失败
        testContext.assertFalse(result)
        
        async.complete()
    }
    
    /**
     * 测试获取依赖项
     */
    @Test
    fun testGetDependents(testContext: TestContext) {
        val async = testContext.async()
        
        // 添加依赖关系
        dependencyManager.addDependencies("plugin1", setOf("plugin2"))
        dependencyManager.addDependencies("plugin3", setOf("plugin2"))
        
        // 获取依赖于plugin2的插件
        val dependents = dependencyManager.getDependents("plugin2")
        
        // 验证依赖项
        testContext.assertEquals(2, dependents.size)
        testContext.assertTrue(dependents.contains("plugin1"))
        testContext.assertTrue(dependents.contains("plugin3"))
        
        async.complete()
    }
    
    /**
     * 测试拓扑排序
     */
    @Test
    fun testTopologicalOrder(testContext: TestContext) {
        val async = testContext.async()
        
        // 添加依赖关系
        dependencyManager.addDependencies("plugin1", setOf("plugin2", "plugin3"))
        dependencyManager.addDependencies("plugin2", setOf("plugin3"))
        
        // 获取拓扑排序
        val order = dependencyManager.getTopologicalOrder()
        
        // 验证排序结果
        testContext.assertEquals(3, order.size)
        
        // plugin3应该在plugin2之前
        val plugin3Index = order.indexOf("plugin3")
        val plugin2Index = order.indexOf("plugin2")
        testContext.assertTrue(plugin3Index < plugin2Index)
        
        // plugin2应该在plugin1之前
        val plugin1Index = order.indexOf("plugin1")
        testContext.assertTrue(plugin2Index < plugin1Index)
        
        async.complete()
    }
    
    /**
     * 测试移除依赖关系
     */
    @Test
    fun testRemoveDependencies(testContext: TestContext) {
        val async = testContext.async()
        
        // 添加依赖关系
        dependencyManager.addDependencies("plugin1", setOf("plugin2", "plugin3"))
        
        // 移除依赖关系
        dependencyManager.removeDependencies("plugin1")
        
        // 验证依赖关系已移除
        val dependencies = dependencyManager.getDependencies("plugin1")
        testContext.assertTrue(dependencies.isEmpty())
        
        async.complete()
    }
    
    /**
     * 测试验证依赖关系
     */
    @Test
    fun testValidateDependencies(testContext: TestContext) {
        val async = testContext.async()
        
        // 添加依赖关系
        dependencyManager.addDependencies("plugin1", setOf("plugin2", "plugin3"))
        
        // 验证依赖关系
        val missingDependencies = dependencyManager.validateDependencies()
        
        // 验证结果
        testContext.assertEquals(1, missingDependencies.size)
        testContext.assertTrue(missingDependencies.containsKey("plugin1"))
        testContext.assertEquals(2, missingDependencies["plugin1"]?.size)
        
        async.complete()
    }
    
    /**
     * 测试插件类，用于测试
     */
    class TestPlugin(
        override val id: String,
        override val type: String,
        jsonConfig: JsonObject
    ) : Plugin {
        override val config: PluginConfig = PluginConfig(id, type, jsonConfig)
        private val initialized = AtomicBoolean(false)
        
        override fun initialize(vertx: Vertx): Future<Void> {
            val promise = Promise.promise<Void>()
            initialized.set(true)
            promise.complete()
            return promise.future()
        }
        
        override fun execute(context: RoutingContext): Future<Void> {
            val promise = Promise.promise<Void>()
            promise.complete()
            return promise.future()
        }
        
        override fun shutdown() {
            initialized.set(false)
        }
        
        override fun shouldExecute(context: RoutingContext): Boolean {
            return true
        }
        
        override fun canExecuteInParallel(): Boolean {
            return true
        }
        
        override fun getEventBusAddress(): String? {
            return null
        }
        
        override fun getPriority(): Int {
            return 0
        }
    }
}
