package com.louloulin.apix.core.performance

import com.louloulin.apix.core.monitoring.SystemMonitor
import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

/**
 * 系统监控器测试
 */
@ExtendWith(VertxExtension::class)
class SystemMonitorTest {
    private lateinit var vertx: Vertx
    private lateinit var systemMonitor: SystemMonitor
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        systemMonitor = SystemMonitor.getInstance(vertx)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testGetSystemMetrics(testContext: VertxTestContext) {
        // 获取系统指标
        val metrics = systemMonitor.getSystemMetrics()
        
        testContext.verify {
            // 验证指标是否为JsonObject
            assert(metrics is io.vertx.core.json.JsonObject) { "应该返回JsonObject" }
            
            // 验证指标是否包含预期的字段
            assert(metrics.containsKey("cpu")) { "应该包含cpu字段" }
            assert(metrics.containsKey("memory")) { "应该包含memory字段" }
            assert(metrics.containsKey("threads")) { "应该包含threads字段" }
            assert(metrics.containsKey("classes")) { "应该包含classes字段" }
            assert(metrics.containsKey("gc")) { "应该包含gc字段" }
            assert(metrics.containsKey("disk")) { "应该包含disk字段" }
            assert(metrics.containsKey("counters")) { "应该包含counters字段" }
            
            // 验证CPU指标
            val cpu = metrics.getJsonObject("cpu")
            assert(cpu.containsKey("usage")) { "应该包含usage字段" }
            assert(cpu.containsKey("cores")) { "应该包含cores字段" }
            assert(cpu.containsKey("systemLoad")) { "应该包含systemLoad字段" }
            
            // 验证内存指标
            val memory = metrics.getJsonObject("memory")
            assert(memory.containsKey("heap")) { "应该包含heap字段" }
            assert(memory.containsKey("nonHeap")) { "应该包含nonHeap字段" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testIncrementCounter(testContext: VertxTestContext) {
        // 增加计数器
        systemMonitor.incrementCounter("test.counter", 5)
        
        // 获取计数器值
        val value = systemMonitor.getCounter("test.counter")
        
        testContext.verify {
            // 验证计数器值是否正确
            assert(value == 5L) { "计数器值应该是5" }
            
            // 再次增加计数器
            systemMonitor.incrementCounter("test.counter", 3)
            
            // 获取更新后的计数器值
            val updatedValue = systemMonitor.getCounter("test.counter")
            
            // 验证计数器值是否正确更新
            assert(updatedValue == 8L) { "计数器值应该是8" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testGetMetricHistory(testContext: VertxTestContext) {
        // 获取指标历史
        val history = systemMonitor.getMetricHistory("cpu.usage")
        
        testContext.verify {
            // 验证历史是否为List
            assert(history is List<*>) { "应该返回List" }
            
            // 获取所有指标历史
            val allHistory = systemMonitor.getAllMetricsHistory()
            
            // 验证所有历史是否为JsonObject
            assert(allHistory is io.vertx.core.json.JsonObject) { "应该返回JsonObject" }
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testResetCounter(testContext: VertxTestContext) {
        // 增加计数器
        systemMonitor.incrementCounter("test.counter1", 5)
        systemMonitor.incrementCounter("test.counter2", 10)
        
        // 重置特定计数器
        systemMonitor.resetCounter("test.counter1")
        
        testContext.verify {
            // 验证计数器是否被重置
            assert(systemMonitor.getCounter("test.counter1") == 0L) { "计数器1应该被重置为0" }
            assert(systemMonitor.getCounter("test.counter2") == 10L) { "计数器2应该保持不变" }
            
            // 重置所有计数器
            systemMonitor.resetCounter()
            
            // 验证所有计数器是否被重置
            assert(systemMonitor.getCounter("test.counter1") == 0L) { "计数器1应该被重置为0" }
            assert(systemMonitor.getCounter("test.counter2") == 0L) { "计数器2应该被重置为0" }
            
            testContext.completeNow()
        }
    }
}
