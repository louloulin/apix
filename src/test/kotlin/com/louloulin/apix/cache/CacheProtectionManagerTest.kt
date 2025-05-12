package com.louloulin.apix.cache

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 缓存穿透防护管理器测试。
 */
class CacheProtectionManagerTest : BaseVertxTest() {

    private lateinit var cacheProtectionManager: CacheProtectionManager

    override fun initialize(testContext: VertxTestContext) {
        try {
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

            // 确保初始化完成后再继续测试
            cacheManager.initialize(config)
                .compose { _ -> bloomFilterManager.initialize(config) }
                .compose { _ -> cacheProtectionManager.initialize(config) }
                .onSuccess { _ ->
                    // 等待一段时间，确保所有初始化完成
                    waitForService(500) {
                        testContext.completeNow()
                    }
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test prevent cache penetration`(testContext: VertxTestContext) {
        try {
            // 确保 cacheProtectionManager 已经初始化
            if (!::cacheProtectionManager.isInitialized) {
                testContext.failNow(UninitializedPropertyAccessException("cacheProtectionManager 未初始化"))
                return
            }

            // 生成唯一的测试键
            val testKey = "test-key-penetration-" + System.currentTimeMillis()
            val testValue = "test-value-penetration-" + System.currentTimeMillis()

            // 获取布隆过滤器管理器
            val bloomFilterManager = BloomFilterManager.getInstance(vertx)

            // 将测试键添加到布隆过滤器中
            bloomFilterManager.add(testKey, "test")
                .compose { _ ->
                    // 创建数据加载函数
                    val loader = {
                        Future.succeededFuture<String>(testValue)
                    }

                    // 防止缓存穿透
                    cacheProtectionManager.preventCachePenetration(testKey, "test", loader)
                }
                .compose { result ->
                    testContext.verify {
                        assertEquals(testValue, result, "第一次调用应该返回原始值")
                    }

                    // 再次调用，应该从缓存获取
                    cacheProtectionManager.preventCachePenetration(testKey, "test", {
                        Future.succeededFuture<String>("wrong-value")
                    })
                }
                .compose { result2 ->
                    testContext.verify {
                        assertEquals(testValue, result2, "第二次调用应该返回缓存的值")
                    }

                    // 获取状态
                    val status = cacheProtectionManager.getStatus()
                    testContext.verify {
                        assertNotNull(status, "状态对象不应为 null")
                        assertTrue(status.getBoolean("penetrationProtectionEnabled", false), "缓存穿透防护应该已启用")
                    }

                    Future.succeededFuture<Void>()
                }
                .onSuccess { _ ->
                    testContext.completeNow()
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test prevent cache breakdown`(testContext: VertxTestContext) {
        try {
            // 确保 cacheProtectionManager 已经初始化
            if (!::cacheProtectionManager.isInitialized) {
                testContext.failNow(UninitializedPropertyAccessException("cacheProtectionManager 未初始化"))
                return
            }

            // 生成唯一的测试键
            val testKey = "test-key-breakdown-" + System.currentTimeMillis()
            val testValue = "test-value-breakdown-" + System.currentTimeMillis()

            // 创建数据加载函数
            val loader = {
                Future.succeededFuture<String>(testValue)
            }

            // 防止缓存击穿
            cacheProtectionManager.preventCacheBreakdown(testKey, "test", loader)
                .compose { result ->
                    testContext.verify {
                        assertEquals(testValue, result, "第一次调用应该返回原始值")
                    }

                    // 再次调用，应该从缓存获取
                    cacheProtectionManager.preventCacheBreakdown(testKey, "test", {
                        Future.succeededFuture<String>("wrong-value")
                    })
                }
                .compose { result2 ->
                    testContext.verify {
                        assertEquals(testValue, result2, "第二次调用应该返回缓存的值")
                    }

                    // 获取状态
                    val status = cacheProtectionManager.getStatus()
                    testContext.verify {
                        assertNotNull(status, "状态对象不应为 null")
                        assertTrue(status.getBoolean("breakdownProtectionEnabled", false), "缓存击穿防护应该已启用")
                    }

                    Future.succeededFuture<Void>()
                }
                .onSuccess { _ ->
                    testContext.completeNow()
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test prevent cache avalanche`(testContext: VertxTestContext) {
        try {
            // 确保 cacheProtectionManager 已经初始化
            if (!::cacheProtectionManager.isInitialized) {
                testContext.failNow(UninitializedPropertyAccessException("cacheProtectionManager 未初始化"))
                return
            }

            // 生成唯一的测试键
            val testKey = "test-key-avalanche-" + System.currentTimeMillis()
            val testValue = "test-value-avalanche-" + System.currentTimeMillis()

            // 创建数据加载函数
            val loader = {
                Future.succeededFuture<String>(testValue)
            }

            // 防止缓存雪崩
            cacheProtectionManager.preventCacheAvalanche(testKey, "test", 60000, loader)
                .compose { result ->
                    testContext.verify {
                        assertEquals(testValue, result, "第一次调用应该返回原始值")
                    }

                    // 再次调用，应该从缓存获取
                    cacheProtectionManager.preventCacheAvalanche(testKey, "test", 60000, {
                        Future.succeededFuture<String>("wrong-value")
                    })
                }
                .compose { result2 ->
                    testContext.verify {
                        assertEquals(testValue, result2, "第二次调用应该返回缓存的值")
                    }

                    // 获取状态
                    val status = cacheProtectionManager.getStatus()
                    testContext.verify {
                        assertNotNull(status, "状态对象不应为 null")
                        assertTrue(status.getBoolean("avalancheProtectionEnabled", false), "缓存雪崩防护应该已启用")
                    }

                    Future.succeededFuture<Void>()
                }
                .onSuccess { _ ->
                    testContext.completeNow()
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
