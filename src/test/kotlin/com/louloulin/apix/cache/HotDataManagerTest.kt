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
 * 热点数据管理器测试。
 */
@ExtendWith(VertxExtension::class)
class HotDataManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var hotDataManager: HotDataManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建热点数据管理器
        hotDataManager = HotDataManager.getInstance(vertx)
        
        // 初始化热点数据管理器
        val config = JsonObject()
            .put("cache", JsonObject()
                .put("hotData", JsonObject()
                    .put("enabled", true)
                    .put("algorithm", "frequency")
                    .put("threshold", 10)
                    .put("timeWindow", 60000)
                    .put("maxSize", 1000)
                    .put("strategy", "local")
                )
                .put("cacheNamespace", "test")
            )
        
        // 创建多级缓存管理器
        val cacheManager = MultiLevelCacheManager.getInstance(vertx)
        cacheManager.initialize(config)
            .compose { _ -> hotDataManager.initialize(config) }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test record access and check hot data`(testContext: VertxTestContext) {
        // 记录多次访问
        for (i in 0 until 20) {
            hotDataManager.recordAccess("test-key", "test")
        }
        
        // 手动触发热点数据检测
        vertx.eventBus().publish("test.cache.access", JsonObject()
            .put("key", "test-key")
            .put("namespace", "test")
        )
        
        // 等待一段时间，让热点数据检测完成
        vertx.setTimer(2000) {
            // 检查是否是热点数据
            val isHotData = hotDataManager.isHotData("test-key", "test")
            val accessCount = hotDataManager.getHotDataAccessCount("test-key", "test")
            
            testContext.verify {
                // 由于访问次数超过阈值，应该被识别为热点数据
                assertTrue(isHotData)
                assertTrue(accessCount > 0)
                
                // 获取所有热点数据
                val allHotData = hotDataManager.getAllHotData()
                assertNotNull(allHotData)
                assertTrue(allHotData.isNotEmpty())
                
                // 获取状态
                val status = hotDataManager.getStatus()
                assertNotNull(status)
                
                testContext.completeNow()
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
