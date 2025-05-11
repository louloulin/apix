package com.louloulin.apix.ha

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
 * 区域多活管理器测试。
 */
@ExtendWith(VertxExtension::class)
class MultiRegionManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var multiRegionManager: MultiRegionManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建区域多活管理器
        multiRegionManager = MultiRegionManager.getInstance(vertx)
        
        // 初始化区域多活管理器
        val config = JsonObject()
            .put("ha", JsonObject()
                .put("multiRegion", JsonObject()
                    .put("enabled", true)
                    .put("currentRegion", "region1")
                    .put("regions", JsonObject()
                        .put("region1", JsonObject()
                            .put("url", "http://region1.example.com")
                            .put("priority", 10)
                        )
                        .put("region2", JsonObject()
                            .put("url", "http://region2.example.com")
                            .put("priority", 5)
                        )
                    )
                )
            )
        
        multiRegionManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // 停止区域多活管理器
        multiRegionManager.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 测试获取状态
        val status = multiRegionManager.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("enabled"))
            assertEquals("region1", status.getString("currentRegion"))
            assertEquals(2, status.getInteger("regionsCount"))
            
            val regions = status.getJsonArray("regions")
            assertNotNull(regions)
            assertEquals(2, regions.size())
            
            testContext.completeNow()
        }
    }
}
