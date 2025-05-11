package com.louloulin.apix.edge

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import com.louloulin.apix.core.common.EventBusAddresses

@ExtendWith(VertxExtension::class)
class EdgeAutonomyManagerTest {
    private val logger = LoggerFactory.getLogger(EdgeAutonomyManagerTest::class.java)
    
    private lateinit var vertx: Vertx
    private lateinit var edgeAutonomyManager: EdgeAutonomyManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建测试配置
        val config = JsonObject()
            .put("node", JsonObject()
                .put("edge", JsonObject()
                    .put("autonomy", JsonObject()
                        .put("enabled", true)
                        .put("heartbeatInterval", 1000)
                        .put("heartbeatTimeout", 3000)
                    )
                )
            )
        
        // 初始化边缘自治管理器
        edgeAutonomyManager = EdgeAutonomyManager.getInstance(vertx)
        edgeAutonomyManager.initialize(config)
            .onSuccess {
                logger.info("边缘自治管理器初始化成功")
                testContext.completeNow()
            }
            .onFailure { cause ->
                logger.error("边缘自治管理器初始化失败", cause)
                testContext.failNow(cause)
            }
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close()
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }
    
    @Test
    fun testGetStatus(testContext: VertxTestContext) {
        // 获取边缘自治状态
        val status = edgeAutonomyManager.getStatus()
        
        testContext.verify {
            // 验证状态
            assertTrue(status.getBoolean("enabled"))
            assertFalse(status.getBoolean("offlineMode"))
            assertNotNull(status.getLong("lastCommunicationTime"))
            assertNotNull(status.getJsonObject("config"))
            assertNotNull(status.getLong("timestamp"))
            
            // 验证配置
            val config = status.getJsonObject("config")
            assertEquals(1000L, config.getLong("heartbeatInterval"))
            assertEquals(3000L, config.getLong("heartbeatTimeout"))
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testEnterOfflineMode(testContext: VertxTestContext) {
        // 进入离线模式
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUTONOMY_OFFLINE_ENTER, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("offlineMode"))
                    
                    // 验证是否真的进入了离线模式
                    assertTrue(edgeAutonomyManager.isOfflineMode())
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testExitOfflineMode(testContext: VertxTestContext) {
        // 先进入离线模式
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUTONOMY_OFFLINE_ENTER, JsonObject()) { enterAr ->
            if (enterAr.succeeded()) {
                // 然后退出离线模式
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUTONOMY_OFFLINE_EXIT, JsonObject()) { exitAr ->
                    if (exitAr.succeeded()) {
                        val response = exitAr.result().body()
                        
                        testContext.verify {
                            assertTrue(response.getBoolean("success"))
                            val result = response.getJsonObject("result")
                            assertNotNull(result)
                            assertFalse(result.getBoolean("offlineMode"))
                            
                            // 验证是否真的退出了离线模式
                            assertFalse(edgeAutonomyManager.isOfflineMode())
                            
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(exitAr.cause())
                    }
                }
            } else {
                testContext.failNow(enterAr.cause())
            }
        }
    }
    
    @Test
    fun testMakeLocalDecision(testContext: VertxTestContext) {
        // 创建决策上下文
        val context = JsonObject()
            .put("service", "test-service")
            .put("operation", "test-operation")
            .put("parameters", JsonObject()
                .put("param1", "value1")
                .put("param2", "value2")
            )
        
        // 进行本地决策
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUTONOMY_LOCAL_DECISION, JsonObject().put("context", context)) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("allow", result.getString("action"))
                    assertEquals("本地决策", result.getString("reason"))
                    assertNotNull(result.getLong("timestamp"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testUpdateLocalCache(testContext: VertxTestContext) {
        // 创建缓存数据
        val cacheData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", JsonObject()
                .put("subkey1", "subvalue1")
                .put("subkey2", "subvalue2")
            )
        
        // 更新本地缓存
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUTONOMY_CACHE_UPDATE, JsonObject().put("data", cacheData)) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testCheckRateLimit(testContext: VertxTestContext) {
        // 检查限流
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUTONOMY_RATE_LIMIT, JsonObject()
            .put("key", "test-service")
            .put("limit", 100)
            .put("window", 60000)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    assertTrue(response.getBoolean("allowed"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testCheckCircuitBreaker(testContext: VertxTestContext) {
        // 检查熔断器
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUTONOMY_CIRCUIT_BREAK, JsonObject()
            .put("service", "test-service")
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    assertTrue(response.getBoolean("allowed"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
