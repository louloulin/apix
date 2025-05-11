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
 * 高可用性管理器测试。
 */
@ExtendWith(VertxExtension::class)
class HighAvailabilityManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var haManager: HighAvailabilityManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建高可用性管理器
        haManager = HighAvailabilityManager.getInstance(vertx)
        
        // 初始化高可用性管理器
        val config = JsonObject()
            .put("ha", JsonObject()
                .put("enabled", true)
                .put("nodeId", "test-node-1")
            )
            .put("node", JsonObject()
                .put("mode", "STANDALONE")
            )
        
        haManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // 停止高可用性管理器
        haManager.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 测试获取状态
        val status = haManager.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("enabled"))
            assertEquals("STANDALONE", status.getString("nodeMode"))
            assertTrue(status.getBoolean("isLeader"))
            
            testContext.completeNow()
        }
    }
}
