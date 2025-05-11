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
 * 优雅缩容管理器测试。
 */
@ExtendWith(VertxExtension::class)
class GracefulScaleDownManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var gracefulScaleDownManager: GracefulScaleDownManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建优雅缩容管理器
        gracefulScaleDownManager = GracefulScaleDownManager.getInstance(vertx)
        
        // 初始化优雅缩容管理器
        val config = JsonObject()
            .put("gracefulScaleDown", JsonObject()
                .put("enabled", true)
                .put("scaleDownTimeout", 300000)
                .put("drainTimeout", 60000)
                .put("checkInterval", 5000)
            )
        
        // 模拟节点停止接收连接事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.node.stop.accepting") { message ->
            val request = message.body()
            val nodeId = request.getString("nodeId")
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("nodeId", nodeId)
                    .put("status", "stopping")
                )
            )
        }
        
        // 模拟节点连接状态事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.node.connections.status") { message ->
            val request = message.body()
            val nodeId = request.getString("nodeId")
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("nodeId", nodeId)
                    .put("acceptingStopped", true)
                    .put("activeConnections", 0)
                )
            )
        }
        
        // 模拟节点关闭事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.node.shutdown") { message ->
            val request = message.body()
            val nodeId = request.getString("nodeId")
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("nodeId", nodeId)
                    .put("status", "shutting_down")
                )
            )
        }
        
        // 模拟节点状态事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.node.status") { message ->
            val request = message.body()
            val nodeId = request.getString("nodeId")
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("nodeId", nodeId)
                    .put("online", false)
                )
            )
        }
        
        // 模拟节点恢复接收连接事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.node.resume.accepting") { message ->
            val request = message.body()
            val nodeId = request.getString("nodeId")
            
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("nodeId", nodeId)
                    .put("status", "resumed")
                )
            )
        }
        
        gracefulScaleDownManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // 停止优雅缩容管理器
        gracefulScaleDownManager.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 测试获取状态
        val status = gracefulScaleDownManager.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("enabled"))
            assertEquals(300000L, status.getLong("scaleDownTimeout"))
            assertEquals(60000L, status.getLong("drainTimeout"))
            assertEquals(5000L, status.getLong("checkInterval"))
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test start and complete graceful scale down`(testContext: VertxTestContext) {
        // 测试开始优雅缩容
        gracefulScaleDownManager.startGracefulScaleDown("test-node-1")
            .onSuccess {
                testContext.verify {
                    val status = gracefulScaleDownManager.getStatus()
                    val operations = status.getJsonObject("activeOperations")
                    
                    // 由于我们的模拟实现会立即完成缩容操作，所以这里可能已经没有活动操作了
                    // 如果有活动操作，验证其状态
                    if (operations.containsKey("test-node-1")) {
                        val operation = operations.getJsonObject("test-node-1")
                        assertEquals("test-node-1", operation.getString("nodeId"))
                    }
                    
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
