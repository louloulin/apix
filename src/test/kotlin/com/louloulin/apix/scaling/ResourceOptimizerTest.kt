package com.louloulin.apix.scaling

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
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
 * 资源优化器测试。
 */
@ExtendWith(VertxExtension::class)
class ResourceOptimizerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var resourceOptimizer: ResourceOptimizer
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建资源优化器
        resourceOptimizer = ResourceOptimizer.getInstance(vertx)
        
        // 初始化资源优化器
        val config = JsonObject()
            .put("resourceOptimization", JsonObject()
                .put("enabled", true)
                .put("monitorInterval", 30000)
                .put("optimizeInterval", 300000)
                .put("thresholds", JsonObject()
                    .put("cpuHigh", 80.0)
                    .put("memoryHigh", 80.0)
                    .put("diskHigh", 80.0)
                    .put("networkHigh", 80.0)
                )
            )
        
        // 模拟系统指标事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.metrics.system.get") { message ->
            // 返回模拟的系统指标
            val metrics = JsonObject()
                .put("cpu", JsonObject()
                    .put("processCpuLoad", 0.3) // 30% CPU 使用率
                )
                .put("memory", JsonObject()
                    .put("heapUsed", 500 * 1024 * 1024) // 500MB 内存使用
                    .put("heapMax", 1024 * 1024 * 1024) // 1GB 最大内存
                )
                .put("disk", JsonObject()
                    .put("usagePercent", 50.0) // 50% 磁盘使用率
                )
                .put("network", JsonObject()
                    .put("usagePercent", 30.0) // 30% 网络使用率
                )
                .put("jvm", JsonObject()
                    .put("gcPauseTime", 50.0) // 50ms GC 暂停时间
                )
                .put("threads", JsonObject()
                    .put("threadCount", 100) // 100 线程
                )
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", metrics)
            )
        }
        
        // 设置优化建议回调
        var suggestionReceived = false
        resourceOptimizer.setOptimizationSuggestionCallback { suggestions ->
            suggestionReceived = true
            println("收到优化建议: $suggestions")
        }
        
        resourceOptimizer.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // 停止资源优化器
        resourceOptimizer.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 测试获取状态
        val status = resourceOptimizer.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("enabled"))
            assertEquals(30000L, status.getLong("monitorInterval"))
            assertEquals(300000L, status.getLong("optimizeInterval"))
            
            val thresholds = status.getJsonObject("thresholds")
            assertNotNull(thresholds)
            assertEquals(80.0, thresholds.getDouble("cpuHigh"))
            assertEquals(80.0, thresholds.getDouble("memoryHigh"))
            
            testContext.completeNow()
        }
    }
}
