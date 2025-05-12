package com.louloulin.apix.cache

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 多级缓存管理器测试。
 */
class MultiLevelCacheManagerTest : BaseVertxTest() {

    private lateinit var cacheManager: MultiLevelCacheManager

    override fun initialize(testContext: VertxTestContext) {
        try {
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
    fun `test put and get cache`(testContext: VertxTestContext) {
        try {
            // 生成唯一的测试键
            val testKey = "test-key-" + System.currentTimeMillis()
            val testValue = "test-value-" + System.currentTimeMillis()

            // 存储缓存
            cacheManager.put(testKey, testValue, 60000, "test")
                .compose { _ ->
                    // 获取缓存
                    cacheManager.get(testKey, "test")
                }
                .onSuccess { value ->
                    testContext.verify {
                        assertEquals(testValue, value, "缓存值应该与存储的值相同")

                        // 获取缓存统计信息
                        val stats = cacheManager.getStats("test")
                        assertNotNull(stats, "缓存统计信息不应为 null")
                        assertTrue(stats.getInteger("hits", 0) > 0, "应该有缓存命中")

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
    fun `test remove cache`(testContext: VertxTestContext) {
        try {
            // 生成唯一的测试键
            val testKey = "test-key-remove-" + System.currentTimeMillis()
            val testValue = "test-value-remove-" + System.currentTimeMillis()

            // 存储缓存
            cacheManager.put(testKey, testValue, 60000, "test")
                .compose { _ ->
                    // 移除缓存
                    cacheManager.remove(testKey, "test")
                }
                .compose { _ ->
                    // 尝试获取已移除的缓存
                    cacheManager.get(testKey, "test")
                        .otherwise { "Cache miss" }
                }
                .onSuccess { value ->
                    testContext.verify {
                        assertEquals("Cache miss", value, "应该无法获取已移除的缓存")

                        // 获取缓存统计信息
                        val stats = cacheManager.getStats("test")
                        assertNotNull(stats, "缓存统计信息不应为 null")

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
    fun `test clear cache`(testContext: VertxTestContext) {
        try {
            // 生成唯一的测试键
            val testKey1 = "test-key-clear-1-" + System.currentTimeMillis()
            val testValue1 = "test-value-clear-1-" + System.currentTimeMillis()
            val testKey2 = "test-key-clear-2-" + System.currentTimeMillis()
            val testValue2 = "test-value-clear-2-" + System.currentTimeMillis()

            // 存储多个缓存
            cacheManager.put(testKey1, testValue1, 60000, "test")
                .compose { _ ->
                    cacheManager.put(testKey2, testValue2, 60000, "test")
                }
                .compose { _ ->
                    // 清空缓存
                    cacheManager.clear("test")
                }
                .compose { _ ->
                    // 尝试获取已清空的缓存
                    cacheManager.get(testKey1, "test")
                        .otherwise { "Cache miss 1" }
                }
                .compose { value1 ->
                    testContext.verify {
                        assertEquals("Cache miss 1", value1, "应该无法获取已清空的缓存")
                    }

                    // 尝试获取另一个已清空的缓存
                    cacheManager.get(testKey2, "test")
                        .otherwise { "Cache miss 2" }
                }
                .onSuccess { value2 ->
                    testContext.verify {
                        assertEquals("Cache miss 2", value2, "应该无法获取已清空的缓存")

                        // 获取缓存统计信息
                        val stats = cacheManager.getStats("test")
                        assertNotNull(stats, "缓存统计信息不应为 null")

                        testContext.completeNow()
                    }
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
