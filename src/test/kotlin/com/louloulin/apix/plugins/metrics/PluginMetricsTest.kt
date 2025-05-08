package com.louloulin.apix.plugins.metrics

import io.vertx.core.Vertx
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 插件指标测试
 */
@RunWith(VertxUnitRunner::class)
class PluginMetricsTest {
    
    private lateinit var vertx: Vertx
    private lateinit var metrics: PluginMetrics
    
    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        metrics = PluginMetrics.getInstance(vertx)
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        metrics.resetMetrics()
        vertx.close(testContext.asyncAssertSuccess())
    }
    
    @Test
    fun testRecordExecution(testContext: TestContext) {
        val async = testContext.async()
        
        // 记录成功执行
        metrics.recordSuccess("test-plugin", 100)
        
        // 获取指标
        val pluginMetrics = metrics.getMetrics("test-plugin")
        
        // 验证指标
        testContext.assertEquals(1, pluginMetrics.getLong("executions"))
        testContext.assertEquals(100.0, pluginMetrics.getDouble("avgExecutionTime"))
        testContext.assertEquals(0, pluginMetrics.getLong("errors"))
        testContext.assertEquals(100, pluginMetrics.getLong("maxExecutionTime"))
        testContext.assertEquals(100, pluginMetrics.getLong("minExecutionTime"))
        
        async.complete()
    }
    
    @Test
    fun testRecordMultipleExecutions(testContext: TestContext) {
        val async = testContext.async()
        
        // 记录多次执行
        metrics.recordSuccess("test-plugin", 100)
        metrics.recordSuccess("test-plugin", 200)
        metrics.recordFailure("test-plugin", 300, RuntimeException("Test error"))
        
        // 获取指标
        val pluginMetrics = metrics.getMetrics("test-plugin")
        
        // 验证指标
        testContext.assertEquals(3, pluginMetrics.getLong("executions"))
        testContext.assertEquals(200.0, pluginMetrics.getDouble("avgExecutionTime"))
        testContext.assertEquals(1, pluginMetrics.getLong("errors"))
        testContext.assertEquals(1.0/3.0, pluginMetrics.getDouble("errorRate"))
        testContext.assertEquals(300, pluginMetrics.getLong("maxExecutionTime"))
        testContext.assertEquals(100, pluginMetrics.getLong("minExecutionTime"))
        
        async.complete()
    }
    
    @Test
    fun testGetAllMetrics(testContext: TestContext) {
        val async = testContext.async()
        
        // 记录多个插件的执行
        metrics.recordSuccess("plugin1", 100)
        metrics.recordSuccess("plugin2", 200)
        
        // 获取所有指标
        val allMetrics = metrics.getAllMetrics()
        
        // 验证指标
        testContext.assertTrue(allMetrics.containsKey("plugin1"))
        testContext.assertTrue(allMetrics.containsKey("plugin2"))
        testContext.assertEquals(100.0, allMetrics.getJsonObject("plugin1").getDouble("avgExecutionTime"))
        testContext.assertEquals(200.0, allMetrics.getJsonObject("plugin2").getDouble("avgExecutionTime"))
        
        async.complete()
    }
    
    @Test
    fun testResetMetrics(testContext: TestContext) {
        val async = testContext.async()
        
        // 记录执行
        metrics.recordSuccess("test-plugin", 100)
        
        // 重置指标
        metrics.resetMetrics()
        
        // 获取所有指标
        val allMetrics = metrics.getAllMetrics()
        
        // 验证指标已重置
        testContext.assertTrue(allMetrics.isEmpty)
        
        async.complete()
    }
}
