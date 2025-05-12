package com.louloulin.apix.admin

import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class AuthHandlerTest : BaseVertxTest() {

    private lateinit var authHandler: MockAuthHandler
    private lateinit var webClient: WebClient
    private var serverPort = 0

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建模拟的认证处理器
            authHandler = MockAuthHandler(vertx)
            webClient = WebClient.create(vertx)

            // 创建测试路由器
            val router = Router.router(vertx)
            authHandler.setupRoutes(router)

            // 创建测试服务器
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(0) // 随机端口
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        serverPort = ar.result().actualPort()
                        logger.info("Server started on port {}", serverPort)
                        testContext.completeNow()
                    } else {
                        testContext.failNow(ar.cause())
                    }
                }
        } catch (e: Exception) {
            logger.error("Error in initialize", e)
            testContext.failNow(e)
        }
    }

    override fun cleanup() {
        try {
            webClient.close()
            logger.info("Cleaning up resources in AuthHandlerTest")
        } catch (e: Exception) {
            logger.warn("Error during cleanup in AuthHandlerTest: {}", e.message)
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testLogin(testContext: VertxTestContext) {
        webClient.post(serverPort, "localhost", "/auth/login")
            .putHeader("Content-Type", "application/json")
            .sendJson(JsonObject()
                .put("username", "admin")
                .put("password", "admin123"))
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200)
                        val json = response.bodyAsJsonObject()
                        assert(json.containsKey("success"))
                        assert(json.getBoolean("success"))
                        assert(json.containsKey("token"))
                        assert(json.containsKey("user"))
                        assert(json.getJsonObject("user").getString("username") == "admin")
                        assert(json.getJsonObject("user").getString("role") == "admin")
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testLoginWithInvalidCredentials(testContext: VertxTestContext) {
        webClient.post(serverPort, "localhost", "/auth/login")
            .putHeader("Content-Type", "application/json")
            .sendJson(JsonObject()
                .put("username", "admin")
                .put("password", "wrong-password"))
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 401)
                        val json = response.bodyAsJsonObject()
                        assert(json.containsKey("success"))
                        assert(!json.getBoolean("success"))
                        assert(json.containsKey("error"))
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testRegister(testContext: VertxTestContext) {
        webClient.post(serverPort, "localhost", "/auth/register")
            .putHeader("Content-Type", "application/json")
            .sendJson(JsonObject()
                .put("username", "newuser")
                .put("password", "password123"))
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 201)
                        val json = response.bodyAsJsonObject()
                        assert(json.containsKey("success"))
                        assert(json.getBoolean("success"))
                        assert(json.containsKey("message"))

                        // 现在尝试使用新用户登录
                        webClient.post(serverPort, "localhost", "/auth/login")
                            .putHeader("Content-Type", "application/json")
                            .sendJson(JsonObject()
                                .put("username", "newuser")
                                .put("password", "password123"))
                            .onComplete { loginAr ->
                                if (loginAr.succeeded()) {
                                    val loginResponse = loginAr.result()
                                    assert(loginResponse.statusCode() == 200)
                                    val loginJson = loginResponse.bodyAsJsonObject()
                                    assert(loginJson.containsKey("success"))
                                    assert(loginJson.getBoolean("success"))
                                    assert(loginJson.containsKey("token"))
                                    assert(loginJson.containsKey("user"))
                                    assert(loginJson.getJsonObject("user").getString("username") == "newuser")
                                    testContext.completeNow()
                                } else {
                                    testContext.failNow(loginAr.cause())
                                }
                            }
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
}
