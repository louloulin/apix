package com.louloulin.apix.resilience

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
 * 降级策略管理器测试。
 */
@ExtendWith(VertxExtension::class)
class FallbackManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var fallbackManager: FallbackManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建降级策略管理器
        fallbackManager = FallbackManager.getInstance(vertx)
        
        // 初始化降级策略管理器
        val config = JsonObject()
            .put("resilience", JsonObject()
                .put("fallback", JsonObject()
                    .put("enabled", true)
                    .put("systemDegradationLevel", 0)
                    .put("services", JsonObject()
                        .put("testService", JsonObject()
                            .put("enabled", true)
                            .put("strategies", JsonObject()
                                .put("0", JsonObject()
                                    .put("level", 1)
                                    .put("type", "STATIC")
                                    .put("value", JsonObject().put("message", "Service is temporarily unavailable"))
                                    .put("statusCode", 503)
                                )
                                .put("1", JsonObject()
                                    .put("level", 2)
                                    .put("type", "CACHE")
                                    .put("ttl", 300000)
                                )
                                .put("2", JsonObject()
                                    .put("level", 3)
                                    .put("type", "SIMPLIFIED")
                                    .put("fields", JsonObject().put("0", "id").put("1", "name").put("2", "status"))
                                )
                            )
                        )
                    )
                )
            )
        
        fallbackManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test set system degradation level`(testContext: VertxTestContext) {
        // 设置系统降级级别
        fallbackManager.setSystemDegradationLevel(2)
        
        testContext.verify {
            assertEquals(2, fallbackManager.getSystemDegradationLevel())
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun `test apply fallback`(testContext: VertxTestContext) {
        // 设置系统降级级别
        fallbackManager.setSystemDegradationLevel(1)
        
        // 原始数据
        val originalData = JsonObject()
            .put("id", 1)
            .put("name", "Test")
            .put("status", "Active")
            .put("description", "This is a test")
            .put("createdAt", "2023-01-01")
        
        // 应用降级策略
        val fallbackData = fallbackManager.applyFallback("testService", originalData, "FAILURE")
        
        testContext.verify {
            assertNotNull(fallbackData)
            assertEquals("Service is temporarily unavailable", fallbackData.getString("message"))
            
            // 获取降级策略统计信息
            val stats = fallbackManager.getStats()
            assertNotNull(stats)
            
            testContext.completeNow()
        }
    }
}
