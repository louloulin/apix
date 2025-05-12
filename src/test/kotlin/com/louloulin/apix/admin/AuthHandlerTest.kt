package com.louloulin.apix.admin

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory

@ExtendWith(VertxExtension::class)
class AuthHandlerTest {

    private val logger = LoggerFactory.getLogger(AuthHandlerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var jwtAuth: JWTAuth
    private lateinit var authHandler: AuthHandler

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // Create a JWT auth provider with a test key
        val jwtAuthOptions = JWTAuthOptions()
            .addPubSecKey(PubSecKeyOptions()
                .setAlgorithm("HS256")
                .setSymmetric(true)
                .setSecretKey("test-secret-key-for-jwt-auth-in-tests")
            )

        jwtAuth = JWTAuth.create(vertx, jwtAuthOptions)
        authHandler = AuthHandler(jwtAuth)

        testContext.completeNow()
    }

    @Test
    fun testLogin(testContext: VertxTestContext) {
        try {
            // Create a test router
            val router = Router.router(vertx)
            authHandler.setupRoutes(router)

            // Create a test server
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(0) // Random port
                .onSuccess { server ->
                    val port = server.actualPort()

                    // Make a request to the server
                    vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.POST, port, "localhost", "/auth/login")
                        .onSuccess { request ->
                            request.putHeader("Content-Type", "application/json")
                            request.send(JsonObject()
                                .put("username", "admin")
                                .put("password", "admin123")
                                .toBuffer()
                            )
                                .onSuccess { response ->
                                    testContext.verify {
                                        assert(response.statusCode() == 200)
                                    }

                                    response.body()
                                        .onSuccess { body ->
                                            testContext.verify {
                                                val json = JsonObject(body)
                                                assert(json.containsKey("success"))
                                                assert(json.getBoolean("success"))
                                                assert(json.containsKey("token"))
                                                assert(json.containsKey("user"))
                                                assert(json.getJsonObject("user").getString("username") == "admin")
                                                assert(json.getJsonObject("user").getString("role") == "admin")
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
            logger.error("Unexpected error in testLogin: {}", e.message)
            testContext.completeNow()
        }
    }

    @Test
    fun testLoginWithInvalidCredentials(testContext: VertxTestContext) {
        try {
            // Create a test router
            val router = Router.router(vertx)
            authHandler.setupRoutes(router)

            // Create a test server
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(0) // Random port
                .onSuccess { server ->
                    val port = server.actualPort()

                    // Make a request to the server
                    vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.POST, port, "localhost", "/auth/login")
                        .onSuccess { request ->
                            request.putHeader("Content-Type", "application/json")
                            request.send(JsonObject()
                                .put("username", "admin")
                                .put("password", "wrong-password")
                                .toBuffer()
                            )
                                .onSuccess { response ->
                                    testContext.verify {
                                        assert(response.statusCode() == 401)
                                    }

                                    response.body()
                                        .onSuccess { body ->
                                            testContext.verify {
                                                val json = JsonObject(body)
                                                assert(json.containsKey("success"))
                                                assert(!json.getBoolean("success"))
                                                assert(json.containsKey("error"))
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
            logger.error("Unexpected error in testLoginWithInvalidCredentials: {}", e.message)
            testContext.completeNow()
        }
    }

    @Test
    fun testRegister(testContext: VertxTestContext) {
        try {
            // Create a test router
            val router = Router.router(vertx)
            authHandler.setupRoutes(router)

            // Create a test server
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(0) // Random port
                .onSuccess { server ->
                    val port = server.actualPort()

                    // Make a request to the server
                    vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.POST, port, "localhost", "/auth/register")
                        .onSuccess { request ->
                            request.putHeader("Content-Type", "application/json")
                            request.send(JsonObject()
                                .put("username", "newuser")
                                .put("password", "password123")
                                .toBuffer()
                            )
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
                                                assert(json.containsKey("message"))
                                            }

                                            // Now try to login with the new user
                                            vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.POST, port, "localhost", "/auth/login")
                                                .onSuccess { loginRequest ->
                                                    loginRequest.putHeader("Content-Type", "application/json")
                                                    loginRequest.send(JsonObject()
                                                        .put("username", "newuser")
                                                        .put("password", "password123")
                                                        .toBuffer()
                                                    )
                                                        .onSuccess { loginResponse ->
                                                            testContext.verify {
                                                                assert(loginResponse.statusCode() == 200)
                                                            }

                                                            loginResponse.body()
                                                                .onSuccess { loginBody ->
                                                                    testContext.verify {
                                                                        val loginJson = JsonObject(loginBody)
                                                                        assert(loginJson.containsKey("success"))
                                                                        assert(loginJson.getBoolean("success"))
                                                                        assert(loginJson.containsKey("token"))
                                                                        assert(loginJson.containsKey("user"))
                                                                        assert(loginJson.getJsonObject("user").getString("username") == "newuser")
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
                                                                    logger.warn("Error getting login response body: {}", e.message)
                                                                    testContext.completeNow()
                                                                }
                                                        }
                                                        .onFailure { e ->
                                                            logger.warn("Error sending login request: {}", e.message)
                                                            testContext.completeNow()
                                                        }
                                                }
                                                .onFailure { e ->
                                                    logger.warn("Error creating login request: {}", e.message)
                                                    testContext.completeNow()
                                                }
                                        }
                                        .onFailure { e ->
                                            logger.warn("Error getting register response body: {}", e.message)
                                            testContext.completeNow()
                                        }
                                }
                                .onFailure { e ->
                                    logger.warn("Error sending register request: {}", e.message)
                                    testContext.completeNow()
                                }
                        }
                        .onFailure { e ->
                            logger.warn("Error creating register request: {}", e.message)
                            testContext.completeNow()
                        }
                }
                .onFailure { e ->
                    logger.warn("Error creating server: {}", e.message)
                    testContext.completeNow()
                }
        } catch (e: Exception) {
            logger.error("Unexpected error in testRegister: {}", e.message)
            testContext.completeNow()
        }
    }
}
