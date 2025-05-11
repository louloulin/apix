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
 * 领导者选举服务测试。
 */
@ExtendWith(VertxExtension::class)
class LeaderElectionServiceTest {
    
    private lateinit var vertx: Vertx
    private lateinit var leaderElectionService: LeaderElectionService
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建领导者选举服务
        leaderElectionService = LeaderElectionService.getInstance(vertx)
        
        // 初始化领导者选举服务
        val config = JsonObject()
            .put("ha", JsonObject()
                .put("nodeId", "test-node-1")
            )
        
        leaderElectionService.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // 停止领导者选举服务
        leaderElectionService.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test leader election in non-clustered mode`(testContext: VertxTestContext) {
        // 在非集群模式下，节点应该自动成为领导者
        testContext.verify {
            assertTrue(leaderElectionService.isLeader())
            
            val leaderInfo = leaderElectionService.getCurrentLeader()
            assertNotNull(leaderInfo)
            assertEquals("test-node-1", leaderInfo.getString("nodeId"))
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test leader change listener`(testContext: VertxTestContext) {
        // 测试领导者变更监听器
        var listenerCalled = false
        
        leaderElectionService.addLeaderChangeListener { isLeader, leaderInfo ->
            testContext.verify {
                assertTrue(isLeader)
                assertNotNull(leaderInfo)
                assertEquals("test-node-1", leaderInfo.getString("nodeId"))
                
                listenerCalled = true
                
                if (listenerCalled) {
                    testContext.completeNow()
                }
            }
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
        assertTrue(listenerCalled)
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 测试获取状态
        val status = leaderElectionService.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("isLeader"))
            assertEquals("test-node-1", status.getString("nodeId"))
            
            testContext.completeNow()
        }
    }
}
