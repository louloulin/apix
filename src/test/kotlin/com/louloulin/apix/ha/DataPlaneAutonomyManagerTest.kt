package com.louloulin.apix.ha

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
 * 数据平面自治管理器测试。
 */
@ExtendWith(VertxExtension::class)
class DataPlaneAutonomyManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var autonomyManager: DataPlaneAutonomyManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建数据平面自治管理器
        autonomyManager = DataPlaneAutonomyManager.getInstance(vertx)
        
        // 初始化数据平面自治管理器
        val config = JsonObject()
            .put("ha", JsonObject()
                .put("dataPlaneAutonomy", JsonObject()
                    .put("enabled", true)
                )
            )
            .put("node", JsonObject()
                .put("mode", "DATA_PLANE")
            )
        
        // 模拟路由、服务和插件的事件处理器
        vertx.eventBus().consumer<JsonObject>("apix.route.get.all") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("routes", JsonObject()
                        .put("test-route", JsonObject()
                            .put("id", "test-route")
                            .put("path", "/test")
                        )
                    )
                )
            )
        }
        
        vertx.eventBus().consumer<JsonObject>("apix.service.get.all") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("services", JsonObject()
                        .put("test-service", JsonObject()
                            .put("id", "test-service")
                            .put("url", "http://test-service")
                        )
                    )
                )
            )
        }
        
        vertx.eventBus().consumer<JsonObject>("apix.plugin.get.all") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("plugins", JsonObject()
                        .put("test-plugin", JsonObject()
                            .put("id", "test-plugin")
                            .put("enabled", true)
                        )
                    )
                )
            )
        }
        
        autonomyManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        // 停止数据平面自治管理器
        autonomyManager.stop()
            .compose { _ -> vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test get status`(testContext: VertxTestContext) {
        // 测试获取状态
        val status = autonomyManager.getStatus()
        
        testContext.verify {
            assertNotNull(status)
            assertTrue(status.getBoolean("enabled"))
            assertEquals(false, status.getBoolean("inAutonomyMode"))
            
            val localCacheSize = status.getJsonObject("localCacheSize")
            assertNotNull(localCacheSize)
            
            testContext.completeNow()
        }
    }
}
