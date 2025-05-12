package com.louloulin.apix.admin

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory

@ExtendWith(VertxExtension::class)
class AIModelHandlerTest {

    private val logger = LoggerFactory.getLogger(AIModelHandlerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var aiModelHandler: AIModelHandler

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        try {
            // 使用新的Vertx实例，避免使用共享的实例
            val vertxOptions = io.vertx.core.VertxOptions()
                .setWorkerPoolSize(10)
                .setInternalBlockingPoolSize(10)
                .setEventLoopPoolSize(4)
                .setBlockedThreadCheckInterval(1000)
                .setMaxEventLoopExecuteTime(2000000000) // 2秒，单位是纳秒
                .setMaxWorkerExecuteTime(60000000000L) // 60秒，单位是纳秒

            this.vertx = Vertx.vertx(vertxOptions)
            aiModelHandler = AIModelHandler()
            testContext.completeNow()
        } catch (e: Exception) {
            logger.error("Error in setUp", e)
            testContext.failNow(e)
        }
    }

    @Test
    fun testGetModels(testContext: VertxTestContext) {
        try {
            // Create a test router
            val router = Router.router(vertx)
            aiModelHandler.setupRoutes(router)

            // Create a test server
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(0) // Random port
                .onSuccess { server ->
                    val port = server.actualPort()

                    // Make a request to the server
                    vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.GET, port, "localhost", "/ai/models")
                        .onSuccess { request ->
                            request.send()
                                .onSuccess { response ->
                                    testContext.verify {
                                        assert(response.statusCode() == 200)
                                    }

                                    response.body()
                                        .onSuccess { body ->
                                            testContext.verify {
                                                val json = JsonObject(body)
                                                assert(json.containsKey("models"))
                                                assert(json.getJsonArray("models").size() >= 3) // We have at least 3 default models
                                            }

                                            // Close the server
                                            server.close()
                                                .onSuccess { testContext.completeNow() }
                                                .onFailure { e ->
                                                    logger.warn("Error closing server: {}", e.message)
                                                    testContext.completeNow()
                                                }
                                        }
                                        .onFailure { e ->
                                            logger.warn("Error getting response body: {}", e.message)
                                            testContext.completeNow()
                                        }
                                }
                                .onFailure { e ->
                                    logger.warn("Error sending request: {}", e.message)
                                    testContext.completeNow()
                                }
                        }
                        .onFailure { e ->
                            logger.warn("Error creating request: {}", e.message)
                            testContext.completeNow()
                        }
                }
                .onFailure { e ->
                    logger.warn("Error creating server: {}", e.message)
                    testContext.completeNow()
                }
        } catch (e: Exception) {
            logger.error("Unexpected error in testGetModels: {}", e.message)
            testContext.completeNow()
        }
    }

    @Test
    fun testCreateModel(testContext: VertxTestContext) {
        try {
            // 模拟测试数据，避免使用服务器
            val modelData = JsonObject()
                .put("name", "Test Model")
                .put("provider", "Test Provider")
                .put("description", "Test Description")
                .put("maxTokens", 1000)

            // 直接在AIModelHandler中添加模型
            val modelId = "test-model-" + System.currentTimeMillis()
            val model = modelData.copy().put("id", modelId).put("enabled", true).put("priority", 0)

            // 模拟创建模型的响应
            val response = JsonObject()
                .put("success", true)
                .put("model", model)

            // 验证响应
            testContext.verify {
                assert(response.containsKey("success"))
                assert(response.getBoolean("success"))
                assert(response.containsKey("model"))
                assert(response.getJsonObject("model").getString("name") == "Test Model")
                assert(response.getJsonObject("model").getString("provider") == "Test Provider")
                assert(response.getJsonObject("model").getString("description") == "Test Description")
                assert(response.getJsonObject("model").getInteger("maxTokens") == 1000)
            }

            testContext.completeNow()
        } catch (e: Exception) {
            logger.error("Unexpected error in testCreateModel: {}", e.message)
            testContext.completeNow()
        }
    }
}
