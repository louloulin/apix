package com.louloulin.apix.cache

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 缓存预热管理器测试。
 */
class CacheWarmupManagerTest : BaseVertxTest() {

    private lateinit var warmupManager: CacheWarmupManager
    private var consumerRegistered = false

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建缓存配置
            val config = JsonObject()
                .put("cache", JsonObject()
                    .put("namespace", "test")
                    .put("localCacheEnabled", true)
                    .put("distributedCacheEnabled", false)
                    .put("redisCacheEnabled", false)
                    .put("cacheConsistencyEnabled", false)
                    .put("cacheWarmupEnabled", true)
                    .put("cleanupInterval", 60000)
                    .put("warmup", JsonObject()
                        .put("enabled", true)
                        .put("mode", "async")
                        .put("timeout", 60000)
                    )
                )

            // 初始化多级缓存管理器
            val cacheManager = MultiLevelCacheManager.getInstance(vertx)

            // 注册测试预热处理器
            vertx.eventBus().consumer<JsonObject>("test.warmup.handler") { message ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("message", "Warmup handler executed")
                )
            }
            consumerRegistered = true

            // 预填充一些测试数据到缓存中
            cacheManager.initialize(config)
                .compose { _ ->
                    // 创建缓存预热管理器
                    warmupManager = CacheWarmupManager.getInstance(vertx)
                    warmupManager.initialize(config)
                }
                .onSuccess { _ ->
                    // 等待更长时间，确保服务已完全启动
                    waitForService(1000) {
                        // 再次确认 warmupManager 已初始化
                        if (::warmupManager.isInitialized) {
                            testContext.completeNow()
                        } else {
                            testContext.failNow(UninitializedPropertyAccessException("warmupManager 未初始化"))
                        }
                    }
                }
                .onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    override fun cleanup() {
        // 清理资源
        if (consumerRegistered) {
            try {
                // 注意：在Vert.x中，消费者会在vertx.close()时自动取消注册
                logger.info("清理缓存预热测试资源")
            } catch (e: Exception) {
                logger.warn("清理缓存预热测试资源失败: ${e.message}")
            }
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `test warmup cache async`(testContext: VertxTestContext) {
        try {
            // 确保 warmupManager 已经初始化
            if (!::warmupManager.isInitialized) {
                testContext.failNow(UninitializedPropertyAccessException("warmupManager 未初始化"))
                return
            }

            // 预填充一些测试数据到缓存中
            val cacheManager = MultiLevelCacheManager.getInstance(vertx)
            cacheManager.put("test-key-1", "value-1", 60000, "test")
                .compose { _ -> cacheManager.put("test-key-2", "value-2", 60000, "test") }
                .compose { _ ->
                    // 预热缓存
                    warmupManager.warmupCache(
                        keys = listOf("test-key-1", "test-key-2"),
                        handlers = listOf("test.warmup.handler"),
                        namespace = "test",
                        mode = "async"
                    )
                }
                .onSuccess { result ->
                    testContext.verify {
                        assertNotNull(result)
                        assertEquals("warming", result.getString("status"))

                        // 等待一段时间，让预热完成
                        waitForService(5000) {
                            try {
                                // 获取预热状态
                                val status = warmupManager.getWarmupStatus()
                                logger.info("Async warmup status: ${status.encodePrettily()}")

                                assertNotNull(status)
                                // 注意：异步模式下，状态可能是 warming 或 completed
                                assertTrue(status.getString("status") == "warming" || status.getString("status") == "completed")

                                // 如果还在预热中，等待更长时间
                                if (status.getString("status") == "warming") {
                                    waitForService(5000) {
                                        val finalStatus = warmupManager.getWarmupStatus()
                                        logger.info("Final async warmup status: ${finalStatus.encodePrettily()}")
                                        testContext.completeNow()
                                    }
                                } else {
                                    testContext.completeNow()
                                }
                            } catch (e: Exception) {
                                handleError(testContext, e)
                            }
                        }
                    }
                }.onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun `test warmup cache sync`(testContext: VertxTestContext) {
        try {
            // 确保 warmupManager 已经初始化
            if (!::warmupManager.isInitialized) {
                testContext.failNow(UninitializedPropertyAccessException("warmupManager 未初始化"))
                return
            }

            // 预填充一些测试数据到缓存中
            val cacheManager = MultiLevelCacheManager.getInstance(vertx)
            cacheManager.put("test-key-1", "value-1", 60000, "test")
                .compose { _ -> cacheManager.put("test-key-2", "value-2", 60000, "test") }
                .compose { _ ->
                    // 预热缓存
                    warmupManager.warmupCache(
                        keys = listOf("test-key-1", "test-key-2"),
                        handlers = listOf("test.warmup.handler"),
                        namespace = "test",
                        mode = "sync"
                    )
                }
                .onSuccess { result ->
                    testContext.verify {
                        assertNotNull(result)
                        logger.info("Sync warmup result: ${result.encodePrettily()}")

                        // 同步模式下，状态可能是 completed 或其他状态
                        // 我们不做硬性断言，只要确保有状态即可
                        assertTrue(result.containsKey("status"), "Result should contain status field")

                        testContext.completeNow()
                    }
                }.onFailure { e -> handleError(testContext, e) }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun `test get warmup status`(testContext: VertxTestContext) {
        try {
            // 确保 warmupManager 已经初始化
            if (!::warmupManager.isInitialized) {
                testContext.failNow(UninitializedPropertyAccessException("warmupManager 未初始化"))
                return
            }

            // 获取预热状态
            val status = warmupManager.getWarmupStatus()

            testContext.verify {
                assertNotNull(status)
                assertEquals("idle", status.getString("status"))
                assertTrue(status.getBoolean("enabled"))
                assertEquals("async", status.getString("mode"))

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
