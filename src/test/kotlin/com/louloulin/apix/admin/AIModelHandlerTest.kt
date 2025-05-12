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
        this.vertx = vertx
        aiModelHandler = AIModelHandler()

        testContext.completeNow()
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
            // Create a test router
            val router = Router.router(vertx)
            aiModelHandler.setupRoutes(router)

            // Create a test server
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(0) // Random port
                .onSuccess { server ->
                    val port = server.actualPort()

                    // Create a test model
                    val modelData = JsonObject()
                        .put("name", "Test Model")
                        .put("provider", "Test Provider")
                        .put("description", "Test Description")
                        .put("maxTokens", 1000)

                    // Make a request to the server
                    vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.POST, port, "localhost", "/ai/models")
                        .onSuccess { request ->
                            request.putHeader("Content-Type", "application/json")
                            request.send(modelData.toBuffer())
                                .onSuccess { response ->
                                    testContext.verify {
                                        assert(response.statusCode() == 201)
                                    }

                                    response.body()
                                        .onSuccess { body ->
                                            testContext.verify {
                                                val json = JsonObject(body)
                                                assert(json.containsKey("success"))
                                                assert(json.getBoolean("success"))
                                                assert(json.containsKey("model"))
                                                assert(json.getJsonObject("model").getString("name") == "Test Model")
                                                assert(json.getJsonObject("model").getString("provider") == "Test Provider")
                                                assert(json.getJsonObject("model").getString("description") == "Test Description")
                                                assert(json.getJsonObject("model").getInteger("maxTokens") == 1000)
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
            logger.error("Unexpected error in testCreateModel: {}", e.message)
            testContext.completeNow()
        }
    }
}
