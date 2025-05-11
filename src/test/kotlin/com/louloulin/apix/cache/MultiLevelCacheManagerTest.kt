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
 * 多级缓存管理器测试。
 */
@ExtendWith(VertxExtension::class)
class MultiLevelCacheManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var cacheManager: MultiLevelCacheManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建多级缓存管理器
        cacheManager = MultiLevelCacheManager.getInstance(vertx)
        
        // 初始化多级缓存管理器
        val config = JsonObject()
            .put("cache", JsonObject()
                .put("localCacheEnabled", true)
                .put("distributedCacheEnabled", true)
                .put("redisCacheEnabled", false)
                .put("cacheConsistencyEnabled", true)
                .put("cacheWarmupEnabled", false)
                .put("cacheNamespace", "test")
                .put("cleanupInterval", 60000)
            )
        
        cacheManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test put and get cache`(testContext: VertxTestContext) {
        // 存储缓存
        cacheManager.put("test-key", "test-value", 60000, "test")
            .compose { _ ->
                // 获取缓存
                cacheManager.get("test-key", "test")
            }
            .onSuccess { value ->
                testContext.verify {
                    assertEquals("test-value", value)
                    
                    // 获取缓存统计信息
                    val stats = cacheManager.getStats("test")
                    assertNotNull(stats)
                    assertEquals(1, stats.getInteger("hits"))
                    assertEquals(0, stats.getInteger("misses"))
                    
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test remove cache`(testContext: VertxTestContext) {
        // 存储缓存
        cacheManager.put("test-key", "test-value", 60000, "test")
            .compose { _ ->
                // 移除缓存
                cacheManager.remove("test-key", "test")
            }
            .compose { _ ->
                // 尝试获取已移除的缓存
                cacheManager.get("test-key", "test")
                    .otherwise { "Cache miss" }
            }
            .onSuccess { value ->
                testContext.verify {
                    assertEquals("Cache miss", value)
                    
                    // 获取缓存统计信息
                    val stats = cacheManager.getStats("test")
                    assertNotNull(stats)
                    assertEquals(0, stats.getInteger("hits"))
                    assertEquals(1, stats.getInteger("misses"))
                    
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test clear cache`(testContext: VertxTestContext) {
        // 存储多个缓存
        cacheManager.put("test-key-1", "test-value-1", 60000, "test")
            .compose { _ ->
                cacheManager.put("test-key-2", "test-value-2", 60000, "test")
            }
            .compose { _ ->
                // 清空缓存
                cacheManager.clear("test")
            }
            .compose { _ ->
                // 尝试获取已清空的缓存
                cacheManager.get("test-key-1", "test")
                    .otherwise { "Cache miss 1" }
            }
            .compose { value1 ->
                testContext.verify {
                    assertEquals("Cache miss 1", value1)
                }
                
                // 尝试获取另一个已清空的缓存
                cacheManager.get("test-key-2", "test")
                    .otherwise { "Cache miss 2" }
            }
            .onSuccess { value2 ->
                testContext.verify {
                    assertEquals("Cache miss 2", value2)
                    
                    // 获取缓存统计信息
                    val stats = cacheManager.getStats("test")
                    assertNotNull(stats)
                    assertEquals(0, stats.getInteger("hits"))
                    assertEquals(2, stats.getInteger("misses"))
                    
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
