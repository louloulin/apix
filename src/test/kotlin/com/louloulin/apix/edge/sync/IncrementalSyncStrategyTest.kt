package com.louloulin.apix.edge.sync

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.*
import io.vertx.core.Future
import io.vertx.core.Promise
import com.louloulin.apix.core.common.EventBusAddresses
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class IncrementalSyncStrategyTest {

    private lateinit var vertx: Vertx
    private lateinit var syncStrategy: IncrementalSyncStrategy

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建同步策略
        val config = JsonObject()
            .put("maxIncrementalVersionGap", 10)
        
        syncStrategy = IncrementalSyncStrategy(vertx, config)
        
        // 注册模拟的事件总线处理器
        setupMockEventBusHandlers()
        
        testContext.completeNow()
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        testContext.completeNow()
    }

    /**
     * 设置模拟的事件总线处理器。
     */
    private fun setupMockEventBusHandlers() {
        // 模拟获取最新版本
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_LATEST_VERSION) { message ->
            val dataType = message.body().getString("dataType")
            
            // 返回模拟的最新版本
            val response = JsonObject()
                .put("success", true)
                .put("version", 10L)
            
            message.reply(response)
        }
        
        // 模拟获取全量数据
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_FULL_DATA) { message ->
            val dataType = message.body().getString("dataType")
            val version = message.body().getLong("version")
            
            // 返回模拟的全量数据
            val data = JsonObject()
                .put("id", "test-$dataType")
                .put("version", version)
                .put("data", "This is test data for $dataType")
            
            val response = JsonObject()
                .put("success", true)
                .put("data", data)
            
            message.reply(response)
        }
        
        // 模拟获取差异数据
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_DIFF) { message ->
            val dataType = message.body().getString("dataType")
            val baseVersion = message.body().getLong("baseVersion")
            val targetVersion = message.body().getLong("targetVersion")
            
            // 创建模拟的差异数据
            val diff = JsonObject()
                .put("added", JsonObject()
                    .put("newKey", "newValue")
                )
                .put("modified", JsonObject()
                    .put("existingKey", "modifiedValue")
                )
                .put("deleted", JsonObject()
                    .put("oldKey", "oldValue")
                )
            
            val response = JsonObject()
                .put("success", true)
                .put("diff", diff)
            
            message.reply(response)
        }
        
        // 模拟本地存储获取
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.LOCAL_STORAGE_GET) { message ->
            val dataType = message.body().getString("dataType")
            
            // 返回模拟的本地数据
            val data = JsonObject()
                .put("id", "test-$dataType")
                .put("version", 5L)
                .put("existingKey", "existingValue")
                .put("oldKey", "oldValue")
            
            val response = JsonObject()
                .put("success", true)
                .put("data", data)
            
            message.reply(response)
        }
        
        // 模拟本地存储保存
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.LOCAL_STORAGE_SAVE) { message ->
            val response = JsonObject()
                .put("success", true)
            
            message.reply(response)
        }
    }

    @Test
    fun testSyncWithCurrentVersionUpToDate(testContext: VertxTestContext) {
        // 设置模拟的最新版本处理器，返回与当前版本相同的版本
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_LATEST_VERSION) { message ->
            val response = JsonObject()
                .put("success", true)
                .put("version", 5L) // 与当前版本相同
            
            message.reply(response)
        }
        
        // 执行同步
        syncStrategy.sync("routes", 5L)
            .onComplete(testContext.succeeding { result ->
                // 验证结果
                testContext.verify {
                    assertEquals("routes", result.getString("dataType"))
                    assertEquals(5L, result.getLong("version"))
                    assertEquals("UP_TO_DATE", result.getString("status"))
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testSyncWithFullSync(testContext: VertxTestContext) {
        // 执行同步，当前版本为0，应该执行全量同步
        syncStrategy.sync("routes", 0L)
            .onComplete(testContext.succeeding { result ->
                // 验证结果
                testContext.verify {
                    assertEquals("routes", result.getString("dataType"))
                    assertEquals(10L, result.getLong("version"))
                    assertEquals("SYNCED", result.getString("status"))
                    assertEquals("FULL", result.getString("syncType"))
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testSyncWithIncrementalSync(testContext: VertxTestContext) {
        // 执行同步，当前版本为5，最新版本为10，应该执行增量同步
        syncStrategy.sync("routes", 5L)
            .onComplete(testContext.succeeding { result ->
                // 验证结果
                testContext.verify {
                    assertEquals("routes", result.getString("dataType"))
                    assertEquals(10L, result.getLong("version"))
                    assertEquals("SYNCED", result.getString("status"))
                    assertEquals("INCREMENTAL", result.getString("syncType"))
                    assertTrue(result.getInteger("diffSize") > 0)
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testSyncWithLargeVersionGap(testContext: VertxTestContext) {
        // 设置模拟的最新版本处理器，返回与当前版本差距很大的版本
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_LATEST_VERSION) { message ->
            val response = JsonObject()
                .put("success", true)
                .put("version", 20L) // 与当前版本差距为15，大于maxIncrementalVersionGap
            
            message.reply(response)
        }
        
        // 执行同步，当前版本为5，最新版本为20，差距大于maxIncrementalVersionGap，应该执行全量同步
        syncStrategy.sync("routes", 5L)
            .onComplete(testContext.succeeding { result ->
                // 验证结果
                testContext.verify {
                    assertEquals("routes", result.getString("dataType"))
                    assertEquals(20L, result.getLong("version"))
                    assertEquals("SYNCED", result.getString("status"))
                    assertEquals("FULL", result.getString("syncType"))
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testSyncWithError(testContext: VertxTestContext) {
        // 设置模拟的最新版本处理器，返回错误
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_LATEST_VERSION) { message ->
            val response = JsonObject()
                .put("success", false)
                .put("error", "Test error")
            
            message.reply(response)
        }
        
        // 执行同步，应该失败
        syncStrategy.sync("routes", 5L)
            .onComplete(testContext.failing { throwable ->
                // 验证错误
                testContext.verify {
                    assertEquals("Test error", throwable.message)
                }
                
                testContext.completeNow()
            })
    }
}
