package com.louloulin.apix.resilience

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
 * 故障注入管理器测试。
 */
@ExtendWith(VertxExtension::class)
class FaultInjectionManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var faultInjectionManager: FaultInjectionManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建故障注入管理器
        faultInjectionManager = FaultInjectionManager.getInstance(vertx)
        
        // 初始化故障注入管理器
        val config = JsonObject()
            .put("resilience", JsonObject()
                .put("faultInjection", JsonObject()
                    .put("enabled", true)
                    .put("globalFaultProbability", 0)
                    .put("services", JsonObject()
                        .put("testService", JsonObject()
                            .put("enabled", true)
                            .put("probability", 100) // 100% 概率注入故障
                            .put("faultTypes", JsonObject().put("0", "LATENCY").put("1", "ERROR").put("2", "TIMEOUT"))
                            .put("latencyMs", 100)
                            .put("errorCode", 500)
                            .put("errorMessage", "Injected fault")
                        )
                    )
                )
            )
        
        faultInjectionManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test set fault injection mode`(testContext: VertxTestContext) {
        // 设置故障注入模式
        faultInjectionManager.setFaultInjectionMode(true, 5000)
        
        testContext.verify {
            val stats = faultInjectionManager.getStats()
            assertNotNull(stats)
            assertTrue(stats.getBoolean("faultInjectionMode"))
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test should inject fault`(testContext: VertxTestContext) {
        // 设置故障注入模式
        faultInjectionManager.setFaultInjectionMode(true, 5000)
        
        // 检查是否应该注入故障
        val shouldInject = faultInjectionManager.shouldInjectFault("testService")
        
        testContext.verify {
            assertTrue(shouldInject)
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test inject fault`(testContext: VertxTestContext) {
        // 设置故障注入模式
        faultInjectionManager.setFaultInjectionMode(true, 5000)
        
        // 注入故障
        faultInjectionManager.injectFault<String>("testService")
            .onSuccess { result ->
                testContext.failNow("应该失败，但成功了")
            }
            .onFailure { cause ->
                testContext.verify {
                    assertNotNull(cause)
                    
                    // 获取故障注入统计信息
                    val stats = faultInjectionManager.getStats()
                    assertNotNull(stats)
                    
                    testContext.completeNow()
                }
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
