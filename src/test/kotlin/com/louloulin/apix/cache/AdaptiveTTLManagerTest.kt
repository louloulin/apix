package com.louloulin.apix.cache

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
 * 自适应 TTL 管理器测试。
 */
@ExtendWith(VertxExtension::class)
class AdaptiveTTLManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var adaptiveTTLManager: AdaptiveTTLManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建自适应 TTL 管理器
        adaptiveTTLManager = AdaptiveTTLManager.getInstance(vertx)
        
        // 初始化自适应 TTL 管理器
        val config = JsonObject()
            .put("cache", JsonObject()
                .put("adaptiveTTL", JsonObject()
                    .put("enabled", true)
                    .put("strategy", "frequency")
                    .put("minTTL", 1000)
                    .put("maxTTL", 86400000)
                    .put("defaultTTL", 60000)
                    .put("adjustmentFactor", 1.5)
                    .put("frequencyThreshold", 10)
                    .put("intervalThreshold", 60000)
                )
                .put("cacheNamespace", "test")
            )
        
        adaptiveTTLManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test record access and get recommended TTL`(testContext: VertxTestContext) {
        // 记录多次访问
        for (i in 0 until 20) {
            adaptiveTTLManager.recordAccess("test-key", "test", true)
        }
        
        // 等待一段时间，让统计信息更新
        vertx.setTimer(1000) {
            // 获取推荐的 TTL
            val ttl = adaptiveTTLManager.getRecommendedTTL("test-key", "test")
            
            testContext.verify {
                // 由于访问频率高，TTL 应该增加
                assertTrue(ttl >= 60000)
                
                // 获取状态
                val status = adaptiveTTLManager.getStatus()
                assertNotNull(status)
                
                testContext.completeNow()
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
