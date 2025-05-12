package com.louloulin.apix.edge.cicd

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class PipelineManagerTest {
    private val logger = LoggerFactory.getLogger(PipelineManagerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var pipelineManager: PipelineManager

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        pipelineManager = PipelineManager.getInstance(vertx)

        // 创建测试配置
        val config = JsonObject()
            .put("templates", JsonArray()
                .add(JsonObject()
                    .put("id", "test-template-1")
                    .put("name", "Test Template 1")
                    .put("type", "JENKINS")
                    .put("stages", JsonArray()
                        .add(JsonObject()
                            .put("id", "build")
                            .put("name", "Build")
                            .put("order", 1)
                            .put("tasks", JsonArray()
                                .add(JsonObject()
                                    .put("id", "compile")
                                    .put("name", "Compile")
                                    .put("type", "shell")
                                    .put("script", "mvn clean compile")
                                    .put("order", 1)
                                )
                            )
                        )
                        .add(JsonObject()
                            .put("id", "test")
                            .put("name", "Test")
                            .put("order", 2)
                            .put("tasks", JsonArray()
                                .add(JsonObject()
                                    .put("id", "unit-test")
                                    .put("name", "Unit Test")
                                    .put("type", "shell")
                                    .put("script", "mvn test")
                                    .put("order", 1)
                                )
                            )
                        )
                    )
                )
            )
            .put("pipelines", JsonArray()
                .add(JsonObject()
                    .put("id", "test-pipeline-1")
                    .put("name", "Test Pipeline 1")
                    .put("type", "JENKINS")
                    .put("jenkinsUrl", "http://localhost:8080")
                    .put("username", "admin")
                    .put("apiToken", "test-token")
                    .put("stages", JsonArray()
                        .add(JsonObject()
                            .put("id", "build")
                            .put("name", "Build")
                            .put("order", 1)
                            .put("tasks", JsonArray()
                                .add(JsonObject()
                                    .put("id", "compile")
                                    .put("name", "Compile")
                                    .put("type", "shell")
                                    .put("script", "mvn clean compile")
                                    .put("order", 1)
                                )
                            )
                        )
                    )
                )
            )

        // 初始化流水线管理器
        pipelineManager.initialize(config)
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        pipelineManager.close()
            .onSuccess {
                vertx.close()
                    .onSuccess {
                        testContext.completeNow()
                    }
                    .onFailure { cause ->
                        testContext.failNow(cause)
                    }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetTemplates(testContext: VertxTestContext) {
        pipelineManager.getTemplates()
            .onSuccess { templates ->
                testContext.verify {
                    assert(templates.size() == 1)
                    assert(templates.getJsonObject(0).getString("id") == "test-template-1")
                    assert(templates.getJsonObject(0).getString("name") == "Test Template 1")
                    assert(templates.getJsonObject(0).getString("type") == "JENKINS")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetTemplateDetails(testContext: VertxTestContext) {
        pipelineManager.getTemplateDetails("test-template-1")
            .onSuccess { template ->
                testContext.verify {
                    assert(template.getString("id") == "test-template-1")
                    assert(template.getString("name") == "Test Template 1")
                    assert(template.getString("type") == "JENKINS")
                    assert(template.getJsonArray("stages").size() == 2)
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testCreateTemplate(testContext: VertxTestContext) {
        val template = JsonObject()
            .put("name", "New Template")
            .put("type", "JENKINS")
            .put("stages", JsonArray()
                .add(JsonObject()
                    .put("name", "Build")
                    .put("order", 1)
                )
            )

        pipelineManager.createTemplate(template)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("name") == "New Template")
                    assert(result.getString("type") == "JENKINS")
                    assert(result.getJsonArray("stages").size() == 1)
                    assert(result.containsKey("id"))
                    assert(result.containsKey("createdAt"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testUpdateTemplate(testContext: VertxTestContext) {
        val template = JsonObject()
            .put("name", "Updated Template")
            .put("stages", JsonArray()
                .add(JsonObject()
                    .put("id", "deploy")
                    .put("name", "Deploy")
                    .put("order", 3)
                )
            )

        pipelineManager.updateTemplate("test-template-1", template)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("id") == "test-template-1")
                    assert(result.getString("name") == "Updated Template")
                    assert(result.getString("type") == "JENKINS")
                    assert(result.getJsonArray("stages").size() == 3)
                    assert(result.containsKey("updatedAt"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testDeleteTemplate(testContext: VertxTestContext) {
        pipelineManager.deleteTemplate("test-template-1")
            .compose {
                // 获取模板列表
                pipelineManager.getTemplates()
            }
            .onSuccess { templates ->
                testContext.verify {
                    assert(templates.size() == 0)
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetPipelines(testContext: VertxTestContext) {
        pipelineManager.getPipelines()
            .onSuccess { pipelines ->
                testContext.verify {
                    assert(pipelines.size() == 1)
                    assert(pipelines.getJsonObject(0).getString("id") == "test-pipeline-1")
                    assert(pipelines.getJsonObject(0).getString("name") == "Test Pipeline 1")
                    assert(pipelines.getJsonObject(0).getString("type") == "JENKINS")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetPipelineDetails(testContext: VertxTestContext) {
        pipelineManager.getPipelineDetails("test-pipeline-1")
            .onSuccess { pipeline ->
                testContext.verify {
                    assert(pipeline.getString("id") == "test-pipeline-1")
                    assert(pipeline.getString("name") == "Test Pipeline 1")
                    assert(pipeline.getString("type") == "JENKINS")
                    assert(pipeline.getJsonArray("stages").size() == 1)
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testCreatePipeline(testContext: VertxTestContext) {
        val pipeline = JsonObject()
            .put("name", "New Pipeline")
            .put("type", "JENKINS")
            .put("jenkinsUrl", "http://localhost:8080")
            .put("username", "admin")
            .put("apiToken", "test-token")
            .put("stages", JsonArray()
                .add(JsonObject()
                    .put("name", "Build")
                    .put("order", 1)
                )
            )

        pipelineManager.createPipeline(pipeline)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("name") == "New Pipeline")
                    assert(result.getString("type") == "JENKINS")
                    assert(result.getJsonArray("stages").size() == 1)
                    assert(result.containsKey("id"))
                    assert(result.containsKey("createdAt"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testCreatePipelineFromTemplate(testContext: VertxTestContext) {
        val pipeline = JsonObject()
            .put("name", "Pipeline From Template")
            .put("type", "JENKINS")
            .put("jenkinsUrl", "http://localhost:8080")
            .put("username", "admin")
            .put("apiToken", "test-token")
            .put("templateId", "test-template-1")

        pipelineManager.createPipeline(pipeline)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("name") == "Pipeline From Template")
                    assert(result.getString("type") == "JENKINS")
                    assert(result.getJsonArray("stages").size() == 2)
                    assert(result.containsKey("id"))
                    assert(result.containsKey("createdAt"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testUpdatePipeline(testContext: VertxTestContext) {
        val pipeline = JsonObject()
            .put("name", "Updated Pipeline")
            .put("stages", JsonArray()
                .add(JsonObject()
                    .put("id", "deploy")
                    .put("name", "Deploy")
                    .put("order", 2)
                )
            )

        pipelineManager.updatePipeline("test-pipeline-1", pipeline)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("id") == "test-pipeline-1")
                    assert(result.getString("name") == "Updated Pipeline")
                    assert(result.getString("type") == "JENKINS")
                    assert(result.getJsonArray("stages").size() == 2)
                    assert(result.containsKey("updatedAt"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testDeletePipeline(testContext: VertxTestContext) {
        pipelineManager.deletePipeline("test-pipeline-1")
            .compose {
                // 获取流水线列表
                pipelineManager.getPipelines()
            }
            .onSuccess { pipelines ->
                testContext.verify {
                    assert(pipelines.size() == 0)
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testExecutePipeline(testContext: VertxTestContext) {
        val params = JsonObject()
            .put("branch", "main")
            .put("environment", "test")

        pipelineManager.executePipeline("test-pipeline-1", params)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("status") == "RUNNING")
                    assert(result.getString("message") == "流水线执行已启动")
                    assert(result.containsKey("id"))

                    // 获取执行历史
                    val executionId = result.getString("id")
                    vertx.setTimer(1000) {
                        pipelineManager.getPipelineExecutionHistory("test-pipeline-1", 10, 0)
                            .onSuccess { history ->
                                testContext.verify {
                                    assert(history.size() == 1)
                                    assert(history.getJsonObject(0).getString("id") == executionId)
                                    assert(history.getJsonObject(0).getString("status") == "RUNNING")
                                    testContext.completeNow()
                                }
                            }
                            .onFailure { cause ->
                                testContext.failNow(cause)
                            }
                    }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetStatus(testContext: VertxTestContext) {
        val status = pipelineManager.getStatus()
        testContext.verify {
            assert(status.getInteger("templateCount") == 1)
            assert(status.getInteger("pipelineCount") == 1)
            assert(status.containsKey("activePipelineCount"))
            assert(status.containsKey("timestamp"))
            testContext.completeNow()
        }
    }
}
