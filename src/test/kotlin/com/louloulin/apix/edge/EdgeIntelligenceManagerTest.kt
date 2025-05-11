package com.louloulin.apix.edge

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
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
class EdgeIntelligenceManagerTest {
    private val logger = LoggerFactory.getLogger(EdgeIntelligenceManagerTest::class.java)
    
    private lateinit var vertx: Vertx
    private lateinit var edgeIntelligenceManager: EdgeIntelligenceManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建测试配置
        val config = JsonObject()
            .put("node", JsonObject()
                .put("edge", JsonObject()
                    .put("intelligence", JsonObject()
                        .put("enabled", true)
                        .put("modelDir", "test-models")
                        .put("dataDir", "test-data")
                        .put("models", JsonArray()
                            .add(JsonObject()
                                .put("id", "test-model-1")
                                .put("path", "test-models/model1.bin")
                                .put("type", "classification")
                            )
                            .add(JsonObject()
                                .put("id", "test-model-2")
                                .put("path", "test-models/model2.bin")
                                .put("type", "regression")
                            )
                        )
                        .put("dataPipelines", JsonArray()
                            .add(JsonObject()
                                .put("id", "test-pipeline-1")
                                .put("steps", JsonArray()
                                    .add("normalize")
                                    .add("filter")
                                )
                            )
                        )
                        .put("analyticsTasks", JsonArray()
                            .add(JsonObject()
                                .put("id", "test-task-1")
                                .put("type", "trend-analysis")
                            )
                        )
                        .put("federatedLearning", JsonObject()
                            .put("enabled", true)
                            .put("aggregationMethod", "fedAvg")
                        )
                    )
                )
            )
        
        // 初始化边缘智能管理器
        edgeIntelligenceManager = EdgeIntelligenceManager.getInstance(vertx)
        edgeIntelligenceManager.initialize(config)
            .onSuccess {
                logger.info("边缘智能管理器初始化成功")
                testContext.completeNow()
            }
            .onFailure { cause ->
                logger.error("边缘智能管理器初始化失败", cause)
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
        // 获取边缘智能状态
        val status = edgeIntelligenceManager.getStatus()
        
        testContext.verify {
            // 验证状态
            assertTrue(status.getBoolean("enabled"))
            assertNotNull(status.getJsonObject("config"))
            assertNotNull(status.getJsonArray("models"))
            assertNotNull(status.getJsonArray("dataPipelines"))
            assertNotNull(status.getJsonArray("analyticsTasks"))
            assertNotNull(status.getJsonObject("federatedLearning"))
            assertNotNull(status.getLong("timestamp"))
            
            // 验证模型列表
            val models = status.getJsonArray("models")
            assertEquals(2, models.size())
            assertTrue(models.contains("test-model-1"))
            assertTrue(models.contains("test-model-2"))
            
            // 验证数据处理管道列表
            val pipelines = status.getJsonArray("dataPipelines")
            assertEquals(1, pipelines.size())
            assertTrue(pipelines.contains("test-pipeline-1"))
            
            // 验证分析任务列表
            val tasks = status.getJsonArray("analyticsTasks")
            assertEquals(1, tasks.size())
            assertTrue(tasks.contains("test-task-1"))
            
            testContext.completeNow()
        }
    }
    
    @Test
    fun testPerformInference(testContext: VertxTestContext) {
        // 创建推理输入
        val input = JsonObject()
            .put("feature1", 0.5)
            .put("feature2", 0.7)
            .put("feature3", 0.2)
        
        // 执行AI推理
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_INTELLIGENCE_INFERENCE, JsonObject()
            .put("modelId", "test-model-1")
            .put("input", input)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("模拟推理结果", result.getString("prediction"))
                    assertEquals(0.95, result.getDouble("confidence"))
                    assertTrue(result.getInteger("processingTime") > 0)
                    assertEquals("test-model-1", result.getString("modelId"))
                    assertNotNull(result.getLong("timestamp"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testProcessData(testContext: VertxTestContext) {
        // 创建数据
        val data = JsonObject()
            .put("value1", 10)
            .put("value2", 20)
            .put("value3", 30)
        
        // 处理数据
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_INTELLIGENCE_DATA_PROCESS, JsonObject()
            .put("pipelineId", "test-pipeline-1")
            .put("data", data)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertNotNull(result.getJsonObject("processedData"))
                    assertTrue(result.getInteger("processingTime") > 0)
                    assertEquals("test-pipeline-1", result.getString("pipelineId"))
                    assertNotNull(result.getLong("timestamp"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testPerformAnalytics(testContext: VertxTestContext) {
        // 创建数据
        val data = JsonObject()
            .put("series", JsonArray()
                .add(10)
                .add(15)
                .add(20)
                .add(25)
                .add(30)
            )
            .put("timeframe", "daily")
        
        // 执行边缘分析
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_INTELLIGENCE_ANALYTICS, JsonObject()
            .put("taskId", "test-task-1")
            .put("data", data)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertNotNull(result.getJsonObject("analytics"))
                    assertTrue(result.getInteger("processingTime") > 0)
                    assertEquals("test-task-1", result.getString("taskId"))
                    assertNotNull(result.getLong("timestamp"))
                    
                    // 验证分析结果
                    val analytics = result.getJsonObject("analytics")
                    assertNotNull(analytics.getDouble("metric1"))
                    assertNotNull(analytics.getDouble("metric2"))
                    assertEquals("上升", analytics.getString("trend"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testUpdateFederatedModel(testContext: VertxTestContext) {
        // 创建模型更新
        val updates = JsonObject()
            .put("weights", JsonArray()
                .add(0.1)
                .add(0.2)
                .add(0.3)
            )
            .put("iteration", 5)
        
        // 更新联邦学习模型
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_INTELLIGENCE_FEDERATED_UPDATE, JsonObject()
            .put("modelId", "test-model-1")
            .put("updates", updates)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("updated"))
                    assertNotNull(result.getLong("modelVersion"))
                    assertEquals("test-model-1", result.getString("modelId"))
                    assertNotNull(result.getLong("timestamp"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testTrainFederatedModel(testContext: VertxTestContext) {
        // 创建训练数据
        val data = JsonObject()
            .put("samples", JsonArray()
                .add(JsonObject()
                    .put("features", JsonArray().add(0.1).add(0.2).add(0.3))
                    .put("label", 1)
                )
                .add(JsonObject()
                    .put("features", JsonArray().add(0.4).add(0.5).add(0.6))
                    .put("label", 0)
                )
            )
            .put("epochs", 10)
            .put("batchSize", 2)
        
        // 训练联邦学习模型
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_INTELLIGENCE_FEDERATED_TRAIN, JsonObject()
            .put("modelId", "test-model-1")
            .put("data", data)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertTrue(result.getBoolean("trained"))
                    assertEquals(10, result.getInteger("iterations"))
                    assertNotNull(result.getDouble("loss"))
                    assertNotNull(result.getDouble("accuracy"))
                    assertEquals("test-model-1", result.getString("modelId"))
                    assertNotNull(result.getLong("timestamp"))
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
