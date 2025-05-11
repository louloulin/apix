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
 * 弹性伸缩管理器测试。
 */
@ExtendWith(VertxExtension::class)
class ElasticScalingManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var elasticScalingManager: ElasticScalingManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建弹性伸缩管理器
        elasticScalingManager = ElasticScalingManager.getInstance(vertx)
        
        // 初始化弹性伸缩管理器
        val config = JsonObject()
            .put("scaling", JsonObject()
                .put("enabled", true)
                .put("autoScalingEnabled", true)
                .put("predictiveScalingEnabled", true)
                .put("minNodeCount", 1)
                .put("maxNodeCount", 5)
                .put("thresholds", JsonObject()
                    .put("cpuHigh", 80.0)
                    .put("cpuLow", 20.0)
                    .put("memoryHigh", 80.0)
                    .put("memoryLow", 20.0)
                    .put("requestsPerSecondHigh", 1000.0)
                    .put("requestsPerSecondLow", 100.0)
                )
            )
        
        // 设置扩容回调
        elasticScalingManager.setScaleUpCallback { nodeCount ->
            // 模拟扩容操作
            println("模拟扩容到 $nodeCount 个节点")
            io.vertx.core.Future.succeededFuture()
        }
        
        // 设置缩容回调
        elasticScalingManager.setScaleDownCallback { nodeCount ->
            // 模拟缩容操作
            println("模拟缩容到 $nodeCount 个节点")
            io.vertx.core.Future.succeededFuture()
        }
        
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
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", metrics)
            )
        }
        
        // 模拟请求指标事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.metrics.requests.get") { message ->
            // 返回模拟的请求指标
            val metrics = JsonObject()
                .put("requestsPerSecond", 500.0) // 500 请求/秒
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", metrics)
            )
        }
        
        elasticScalingManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // 停止弹性伸缩管理器
        elasticScalingManager.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 测试获取状态
        val status = elasticScalingManager.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("enabled"))
            assertTrue(status.getBoolean("autoScalingEnabled"))
            assertTrue(status.getBoolean("predictiveScalingEnabled"))
            assertEquals(1, status.getInteger("minNodeCount"))
            assertEquals(5, status.getInteger("maxNodeCount"))
            
            val thresholds = status.getJsonObject("thresholds")
            assertNotNull(thresholds)
            assertEquals(80.0, thresholds.getDouble("cpuHigh"))
            assertEquals(20.0, thresholds.getDouble("cpuLow"))
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test set node count`(testContext: VertxTestContext) {
        // 测试设置节点数量
        elasticScalingManager.setNodeCount(3)
            .onSuccess {
                testContext.verify {
                    val status = elasticScalingManager.getStatus()
                    assertEquals(3, status.getInteger("targetNodeCount"))
                    
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
