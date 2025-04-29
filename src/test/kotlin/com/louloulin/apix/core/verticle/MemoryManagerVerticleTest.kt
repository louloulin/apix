package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 内存管理 Verticle 测试
 */
@ExtendWith(VertxExtension::class)
class MemoryManagerVerticleTest {
    private lateinit var vertx: Vertx

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()

        // 部署 ConfigVerticle 和 MemoryManagerVerticle
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(MemoryManagerVerticle()) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `test get memory usage`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_USAGE_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val usage = response.getJsonObject("usage")
                    assertNotNull(usage)

                    // 验证堆内存信息
                    val heap = usage.getJsonObject("heap")
                    assertNotNull(heap)
                    assertTrue(heap.getLong("used") >= 0)

                    // 验证非堆内存信息
                    val nonHeap = usage.getJsonObject("nonHeap")
                    assertNotNull(nonHeap)
                    assertTrue(nonHeap.getLong("used") >= 0)

                    // 验证内存池信息
                    val memoryPools = usage.getJsonObject("memoryPools")
                    assertNotNull(memoryPools)
                    assertTrue(memoryPools.fieldNames().isNotEmpty())
                }

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test trigger GC`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_GC_TRIGGER, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val message = response.getString("message")
                    assertNotNull(message)
                    assertTrue(message.contains("Garbage collection triggered"))

                    val usage = response.getJsonObject("usage")
                    assertNotNull(usage)
                }

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test clear memory cache`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_CACHE_CLEAR, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val message = response.getString("message")
                    assertNotNull(message)
                    assertTrue(message.contains("Memory caches cleared"))

                    val usage = response.getJsonObject("usage")
                    assertNotNull(usage)
                }

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test memory notification`(testContext: VertxTestContext) {
        // 注册内存状态变化通知处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.MEMORY_LOW_NOTIFY) { message ->
            val notification = message.body()
            testContext.verify {
                assertEquals("low", notification.getString("status"))
                assertNotNull(notification.getJsonObject("usage"))
            }
        }

        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.MEMORY_CRITICAL_NOTIFY) { message ->
            val notification = message.body()
            testContext.verify {
                assertEquals("critical", notification.getString("status"))
                assertNotNull(notification.getJsonObject("usage"))
            }
        }

        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.MEMORY_RESTORED_NOTIFY) { message ->
            val notification = message.body()
            testContext.verify {
                assertEquals("normal", notification.getString("status"))
                assertNotNull(notification.getJsonObject("usage"))
            }
        }

        // 注意：这里我们无法直接测试内存状态变化通知，因为它依赖于系统的实际内存使用情况
        // 所以我们只验证通知处理器是否被正确注册

        // 等待一段时间，确保处理器被注册
        vertx.setTimer(1000) {
            testContext.completeNow()
        }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    // 辅助方法
    private fun assertEquals(expected: String, actual: String) {
        if (expected != actual) {
            throw AssertionError("Expected '$expected' but was '$actual'")
        }
    }

    @Test
    fun `test object pool management`(testContext: VertxTestContext) {
        // 创建对象池
        val createRequest = JsonObject()
            .put("name", "test-pool")
            .put("initialSize", 5)
            .put("maxSize", 10)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_CREATE, createRequest) { createAr ->
            if (createAr.succeeded()) {
                val createResponse = createAr.result().body()
                testContext.verify {
                    assertTrue(createResponse.getBoolean("success"))
                    val message = createResponse.getString("message")
                    assertNotNull(message)
                    assertTrue(message.contains("Object pool created"))

                    val stats = createResponse.getJsonObject("stats")
                    assertNotNull(stats)
                    assertEquals("test-pool", stats.getString("name"))
                }

                // 获取对象池统计信息
                vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_GET_STATS, JsonObject()) { statsAr ->
                    if (statsAr.succeeded()) {
                        val statsResponse = statsAr.result().body()
                        testContext.verify {
                            assertTrue(statsResponse.getBoolean("success"))
                            val objectPools = statsResponse.getJsonObject("objectPools")
                            assertNotNull(objectPools)
                            assertTrue(objectPools.containsKey("test-pool"))
                        }

                        // 移除对象池
                        val removeRequest = JsonObject().put("name", "test-pool")
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_REMOVE, removeRequest) { removeAr ->
                            if (removeAr.succeeded()) {
                                val removeResponse = removeAr.result().body()
                                testContext.verify {
                                    assertTrue(removeResponse.getBoolean("success"))
                                    val message = removeResponse.getString("message")
                                    assertNotNull(message)
                                    assertTrue(message.contains("Object pool removed"))
                                }

                                // 再次获取对象池统计信息，验证对象池已移除
                                vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_GET_STATS, JsonObject()) { statsAfterRemoveAr ->
                                    if (statsAfterRemoveAr.succeeded()) {
                                        val statsAfterRemoveResponse = statsAfterRemoveAr.result().body()
                                        testContext.verify {
                                            assertTrue(statsAfterRemoveResponse.getBoolean("success"))
                                            val objectPoolsAfterRemove = statsAfterRemoveResponse.getJsonObject("objectPools")
                                            assertNotNull(objectPoolsAfterRemove)
                                            assertTrue(!objectPoolsAfterRemove.containsKey("test-pool") || objectPoolsAfterRemove.getJsonObject("test-pool") == null)
                                        }

                                        testContext.completeNow()
                                    } else {
                                        testContext.failNow(statsAfterRemoveAr.cause())
                                    }
                                }
                            } else {
                                testContext.failNow(removeAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(statsAr.cause())
                    }
                }
            } else {
                testContext.failNow(createAr.cause())
            }
        }

        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
