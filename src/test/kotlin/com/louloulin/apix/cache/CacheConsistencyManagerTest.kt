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
 * 缓存一致性管理器测试。
 */
@ExtendWith(VertxExtension::class)
class CacheConsistencyManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var consistencyManager: CacheConsistencyManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建缓存一致性管理器
        consistencyManager = CacheConsistencyManager.getInstance(vertx)
        
        // 初始化缓存一致性管理器
        val config = JsonObject()
            .put("cache", JsonObject()
                .put("namespace", "test")
                .put("consistency", JsonObject()
                    .put("enabled", true)
                    .put("strategy", "eventual")
                )
            )
        
        consistencyManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test acquire and release lock`(testContext: VertxTestContext) {
        // 获取锁
        val acquired = consistencyManager.acquireLock("test-key", "test")
        
        testContext.verify {
            assertTrue(acquired)
            
            // 释放锁
            consistencyManager.releaseLock("test-key", "test")
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test publish update event`(testContext: VertxTestContext) {
        // 监听缓存更新事件
        vertx.eventBus().consumer<JsonObject>("test.cache.update") { message ->
            val key = message.body().getString("key")
            val namespace = message.body().getString("namespace")
            val version = message.body().getLong("version")
            
            testContext.verify {
                assertEquals("test-key", key)
                assertEquals("test", namespace)
                assertTrue(version > 0)
                
                testContext.completeNow()
            }
        }
        
        // 发布缓存更新事件
        val version = consistencyManager.publishUpdateEvent("test-key", "test")
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test publish invalidate event`(testContext: VertxTestContext) {
        // 监听缓存失效事件
        vertx.eventBus().consumer<JsonObject>("test.cache.invalidate") { message ->
            val key = message.body().getString("key")
            val namespace = message.body().getString("namespace")
            val version = message.body().getLong("version")
            
            testContext.verify {
                assertEquals("test-key", key)
                assertEquals("test", namespace)
                assertTrue(version > 0)
                
                testContext.completeNow()
            }
        }
        
        // 发布缓存失效事件
        val version = consistencyManager.publishInvalidateEvent("test-key", "test")
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 获取缓存一致性状态
        val status = consistencyManager.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("enabled"))
            assertEquals("eventual", status.getString("strategy"))
            assertEquals("test", status.getString("namespace"))
            
            testContext.completeNow()
        }
    }
}
