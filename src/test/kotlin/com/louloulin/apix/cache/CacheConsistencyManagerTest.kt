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
 * 缓存一致性管理器测试。
 */
class CacheConsistencyManagerTest : BaseVertxTest() {

    private lateinit var consistencyManager: CacheConsistencyManager

    override fun initialize(testContext: VertxTestContext) {
        try {
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
    fun `test acquire and release lock`(testContext: VertxTestContext) {
        try {
            val key = "test-lock-" + System.currentTimeMillis()

            // 获取锁
            val acquired = consistencyManager.acquireLock(key, "test")

            testContext.verify {
                assertTrue(acquired, "应该成功获取锁")

                // 释放锁
                consistencyManager.releaseLock(key, "test")

                // 再次获取锁，确保锁已释放
                val acquiredAgain = consistencyManager.acquireLock(key, "test")
                assertTrue(acquiredAgain, "应该能再次获取锁")

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test publish update event`(testContext: VertxTestContext) {
        try {
            // 直接测试发布事件的功能，而不依赖事件总线消费者
            val key = "test-update-" + System.currentTimeMillis()

            // 发布缓存更新事件
            val version = consistencyManager.publishUpdateEvent(key, "test")

            testContext.verify {
                assertTrue(version > 0, "版本号应该大于 0")
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test publish invalidate event`(testContext: VertxTestContext) {
        try {
            // 直接测试发布事件的功能，而不依赖事件总线消费者
            val key = "test-invalidate-" + System.currentTimeMillis()

            // 发布缓存失效事件
            val version = consistencyManager.publishInvalidateEvent(key, "test")

            testContext.verify {
                assertTrue(version > 0, "版本号应该大于 0")
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test get status`(testContext: VertxTestContext) {
        try {
            // 获取缓存一致性状态
            val status = consistencyManager.getStatus()

            testContext.verify {
                assertNotNull(status, "状态对象不应为 null")
                assertTrue(status.getBoolean("enabled"), "缓存一致性应该已启用")
                assertEquals("eventual", status.getString("strategy"), "策略应该为 eventual")
                assertEquals("test", status.getString("namespace"), "命名空间应该为 test")

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
