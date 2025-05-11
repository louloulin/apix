package com.louloulin.apix.cache

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
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
 * 缓存预热管理器测试。
 */
@ExtendWith(VertxExtension::class)
class CacheWarmupManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var warmupManager: CacheWarmupManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建缓存预热管理器
        warmupManager = CacheWarmupManager.getInstance(vertx)
        
        // 初始化缓存预热管理器
        val config = JsonObject()
            .put("cache", JsonObject()
                .put("namespace", "test")
                .put("warmup", JsonObject()
                    .put("enabled", true)
                    .put("mode", "async")
                    .put("timeout", 60000)
                )
            )
        
        // 注册测试预热处理器
        vertx.eventBus().consumer<JsonObject>("test.warmup.handler") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("message", "Warmup handler executed")
            )
        }
        
        warmupManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test warmup cache async`(testContext: VertxTestContext) {
        // 预热缓存
        warmupManager.warmupCache(
            keys = listOf("test-key-1", "test-key-2"),
            handlers = listOf("test.warmup.handler"),
            namespace = "test",
            mode = "async"
        ).onSuccess { result ->
            testContext.verify {
                assertNotNull(result)
                assertEquals("warming", result.getString("status"))
                
                // 等待一段时间，让预热完成
                vertx.setTimer(1000) {
                    // 获取预热状态
                    val status = warmupManager.getWarmupStatus()
                    
                    assertNotNull(status)
                    assertEquals("completed", status.getString("status"))
                    assertEquals(3, status.getInteger("total")) // 2 keys + 1 handler
                    assertEquals(3, status.getInteger("progress"))
                    assertEquals(100, status.getInteger("percentage"))
                    
                    testContext.completeNow()
                }
            }
        }.onFailure { cause ->
            testContext.failNow(cause)
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test warmup cache sync`(testContext: VertxTestContext) {
        // 预热缓存
        warmupManager.warmupCache(
            keys = listOf("test-key-1", "test-key-2"),
            handlers = listOf("test.warmup.handler"),
            namespace = "test",
            mode = "sync"
        ).onSuccess { result ->
            testContext.verify {
                assertNotNull(result)
                assertEquals("completed", result.getString("status"))
                assertEquals(3, result.getInteger("total")) // 2 keys + 1 handler
                assertEquals(3, result.getInteger("progress"))
                assertEquals(100, result.getInteger("percentage"))
                
                testContext.completeNow()
            }
        }.onFailure { cause ->
            testContext.failNow(cause)
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get warmup status`(testContext: VertxTestContext) {
        // 获取预热状态
        val status = warmupManager.getWarmupStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertEquals("idle", status.getString("status"))
            assertTrue(status.getBoolean("enabled"))
            assertEquals("async", status.getString("mode"))
            
            testContext.completeNow()
        }
    }
}
