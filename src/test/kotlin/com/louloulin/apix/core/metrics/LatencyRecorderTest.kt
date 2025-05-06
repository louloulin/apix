package com.louloulin.apix.core.metrics

import io.vertx.core.Vertx
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 延迟记录器测试类
 */
@ExtendWith(VertxExtension::class)
class LatencyRecorderTest {
    private lateinit var vertx: Vertx
    private lateinit var latencyRecorder: LatencyRecorder
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        latencyRecorder = LatencyRecorder.getInstance()
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    /**
     * 测试记录延迟
     */
    @Test
    fun testRecordLatency(testContext: VertxTestContext) {
        val name = "test.latency"
        
        // 记录一些延迟
        latencyRecorder.recordLatency(name, 10, TimeUnit.MILLISECONDS)
        latencyRecorder.recordLatency(name, 20, TimeUnit.MILLISECONDS)
        latencyRecorder.recordLatency(name, 30, TimeUnit.MILLISECONDS)
        latencyRecorder.recordLatency(name, 40, TimeUnit.MILLISECONDS)
        latencyRecorder.recordLatency(name, 50, TimeUnit.MILLISECONDS)
        
        // 获取延迟统计信息
        val stats = latencyRecorder.getLatencyStats(name)
        
        testContext.verify {
            assertNotNull(stats)
            assertEquals(name, stats.getString("name"))
            assertEquals(5, stats.getLong("count"))
            assertEquals("MILLISECONDS", stats.getString("unit"))
            
            val percentiles = stats.getJsonObject("percentiles")
            assertNotNull(percentiles)
            
            // 验证百分位数
            assertTrue(percentiles.getDouble("min") >= 10.0)
            assertTrue(percentiles.getDouble("max") <= 50.0)
            assertTrue(percentiles.getDouble("mean") >= 10.0 && percentiles.getDouble("mean") <= 50.0)
            assertTrue(percentiles.getDouble("p50") >= 10.0 && percentiles.getDouble("p50") <= 50.0)
            assertTrue(percentiles.getDouble("p99") >= 10.0 && percentiles.getDouble("p99") <= 50.0)
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试重置延迟统计信息
     */
    @Test
    fun testResetLatencyStats(testContext: VertxTestContext) {
        val name = "test.reset"
        
        // 记录一些延迟
        latencyRecorder.recordLatency(name, 10, TimeUnit.MILLISECONDS)
        latencyRecorder.recordLatency(name, 20, TimeUnit.MILLISECONDS)
        
        // 获取延迟统计信息
        var stats = latencyRecorder.getLatencyStats(name)
        
        testContext.verify {
            assertNotNull(stats)
            assertEquals(2, stats.getLong("count"))
            
            // 重置延迟统计信息
            latencyRecorder.resetLatencyStats(name)
            
            // 再次获取延迟统计信息
            stats = latencyRecorder.getLatencyStats(name)
            
            assertNotNull(stats)
            assertEquals(0, stats.getLong("count"))
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试获取所有延迟统计信息
     */
    @Test
    fun testGetAllLatencyStats(testContext: VertxTestContext) {
        // 记录一些延迟
        latencyRecorder.recordLatency("test.all.1", 10, TimeUnit.MILLISECONDS)
        latencyRecorder.recordLatency("test.all.2", 20, TimeUnit.MILLISECONDS)
        
        // 获取所有延迟统计信息
        val allStats = latencyRecorder.getAllLatencyStats()
        
        testContext.verify {
            assertNotNull(allStats)
            assertTrue(allStats.containsKey("test.all.1"))
            assertTrue(allStats.containsKey("test.all.2"))
            
            val stats1 = allStats.getJsonObject("test.all.1")
            val stats2 = allStats.getJsonObject("test.all.2")
            
            assertNotNull(stats1)
            assertNotNull(stats2)
            
            assertEquals(1, stats1.getLong("count"))
            assertEquals(1, stats2.getLong("count"))
            
            testContext.completeNow()
        }
    }
    
    /**
     * 测试获取直方图
     */
    @Test
    fun testGetHistogramChart(testContext: VertxTestContext) {
        val name = "test.histogram"
        
        // 记录一些延迟
        for (i in 1..100) {
            latencyRecorder.recordLatency(name, i.toLong(), TimeUnit.MILLISECONDS)
        }
        
        // 获取直方图
        val chart = latencyRecorder.getHistogramChart(name)
        
        testContext.verify {
            assertNotNull(chart)
            assertTrue(chart.isNotEmpty())
            assertTrue(chart.contains("Value"))
            assertTrue(chart.contains("Percentile"))
            
            testContext.completeNow()
        }
    }
}
