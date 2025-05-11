package com.louloulin.apix.cache

import io.vertx.core.Future
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
 * 缓存穿透防护管理器测试。
 */
@ExtendWith(VertxExtension::class)
class CacheProtectionManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var cacheProtectionManager: CacheProtectionManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建缓存穿透防护管理器
        cacheProtectionManager = CacheProtectionManager.getInstance(vertx)
        
        // 初始化缓存穿透防护管理器
        val config = JsonObject()
            .put("cache", JsonObject()
                .put("protection", JsonObject()
                    .put("penetrationEnabled", true)
                    .put("breakdownEnabled", true)
                    .put("avalancheEnabled", true)
                    .put("nullValueTTL", 60000)
                    .put("mutexLockTimeout", 5000)
                    .put("randomExpiryOffset", 300000)
                    .put("avalancheThreshold", 1000)
                    .put("avalancheTimeWindow", 1000)
                )
                .put("cacheNamespace", "test")
            )
        
        // 创建多级缓存管理器
        val cacheManager = MultiLevelCacheManager.getInstance(vertx)
        
        // 创建布隆过滤器管理器
        val bloomFilterManager = BloomFilterManager.getInstance(vertx)
        
        cacheManager.initialize(config)
            .compose { _ -> bloomFilterManager.initialize(config) }
            .compose { _ -> cacheProtectionManager.initialize(config) }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test prevent cache penetration`(testContext: VertxTestContext) {
        // 创建数据加载函数
        val loader = {
            Future.succeededFuture<String>("test-value")
        }
        
        // 防止缓存穿透
        cacheProtectionManager.preventCachePenetration("test-key", "test", loader)
            .onSuccess { result ->
                testContext.verify {
                    assertEquals("test-value", result)
                    
                    // 再次调用，应该从缓存获取
                    cacheProtectionManager.preventCachePenetration("test-key", "test", loader)
                        .onSuccess { result2 ->
                            assertEquals("test-value", result2)
                            
                            // 获取状态
                            val status = cacheProtectionManager.getStatus()
                            assertNotNull(status)
                            
                            testContext.completeNow()
                        }
                        .onFailure { cause ->
                            testContext.failNow(cause)
                        }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test prevent cache breakdown`(testContext: VertxTestContext) {
        // 创建数据加载函数
        val loader = {
            Future.succeededFuture<String>("test-value")
        }
        
        // 防止缓存击穿
        cacheProtectionManager.preventCacheBreakdown("test-key", "test", loader)
            .onSuccess { result ->
                testContext.verify {
                    assertEquals("test-value", result)
                    
                    // 再次调用，应该从缓存获取
                    cacheProtectionManager.preventCacheBreakdown("test-key", "test", loader)
                        .onSuccess { result2 ->
                            assertEquals("test-value", result2)
                            
                            // 获取状态
                            val status = cacheProtectionManager.getStatus()
                            assertNotNull(status)
                            
                            testContext.completeNow()
                        }
                        .onFailure { cause ->
                            testContext.failNow(cause)
                        }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test prevent cache avalanche`(testContext: VertxTestContext) {
        // 创建数据加载函数
        val loader = {
            Future.succeededFuture<String>("test-value")
        }
        
        // 防止缓存雪崩
        cacheProtectionManager.preventCacheAvalanche("test-key", "test", 60000, loader)
            .onSuccess { result ->
                testContext.verify {
                    assertEquals("test-value", result)
                    
                    // 再次调用，应该从缓存获取
                    cacheProtectionManager.preventCacheAvalanche("test-key", "test", 60000, loader)
                        .onSuccess { result2 ->
                            assertEquals("test-value", result2)
                            
                            // 获取状态
                            val status = cacheProtectionManager.getStatus()
                            assertNotNull(status)
                            
                            testContext.completeNow()
                        }
                        .onFailure { cause ->
                            testContext.failNow(cause)
                        }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
