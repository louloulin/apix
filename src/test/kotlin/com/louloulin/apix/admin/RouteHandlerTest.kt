package com.louloulin.apix.admin

import com.louloulin.apix.core.BaseVertxTest
import com.louloulin.apix.core.RouteManager
import com.louloulin.apix.models.Route
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*
import io.vertx.ext.web.RoutingContext

@ExtendWith(VertxExtension::class)
class RouteHandlerTest : BaseVertxTest() {

    private lateinit var routeManager: RouteManager
    private lateinit var routeHandler: RouteHandler

    @BeforeEach
    override fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        super.setUp(vertx, testContext)
        routeManager = Mockito.mock(RouteManager::class.java)
        routeHandler = RouteHandler(routeManager)

        testContext.completeNow()
    }

    @Test
    fun testGetRoutes(testContext: VertxTestContext) {
        // Mock data
        val route1Id = UUID.randomUUID().toString()
        val route2Id = UUID.randomUUID().toString()

        val route1 = Route(
            id = route1Id,
            name = "Test Route 1",
            path = "/api/test",
            methods = listOf("GET"),
            targetUrl = "http://localhost:8080",
            plugins = emptyList(),
            enabled = true
        )

        val route2 = Route(
            id = route2Id,
            name = "Test Route 2",
            path = "/api/test2",
            methods = listOf("GET"),
            targetUrl = "http://localhost:8081",
            plugins = emptyList(),
            enabled = false
        )

        val routes = listOf(route1, route2)

        // Mock the route manager
        `when`(routeManager.getRoutes()).thenReturn(routes)

        // Create a test router
        val router = Router.router(vertx)
        routeHandler.setupRoutes(router)

        // Create a test server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(0) // Random port
            .onSuccess { server ->
                val port = server.actualPort()

                // Make a request to the server
                vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.GET, port, "localhost", "/routes")
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
                                            assert(json.containsKey("routes"))
                                            assert(json.getJsonArray("routes").size() == 2)
                                        }

                                        // Verify that the route manager was called
                                        verify(routeManager).getRoutes()

                                        // Close the server
                                        server.close()
                                            .onSuccess { testContext.completeNow() }
                                            .onFailure { testContext.failNow(it) }
                                    }
                                    .onFailure { testContext.failNow(it) }
                            }
                            .onFailure { testContext.failNow(it) }
                    }
                    .onFailure { testContext.failNow(it) }
            }
            .onFailure { testContext.failNow(it) }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testCreateRoute(testContext: VertxTestContext) {
        // Mock data
        val routeId = UUID.randomUUID().toString()
        val routeData = JsonObject()
            .put("path", "/api/test")
            .put("target", "http://localhost:8080") // 注意这里是 target 而不是 targetUrl

        val createdRoute = Route(
            id = routeId,
            name = "Test Route",
            path = "/api/test",
            methods = listOf("GET"),
            targetUrl = "http://localhost:8080",
            plugins = emptyList(),
            enabled = true
        )

        // Mock the route manager
        `when`(routeManager.createRoute(routeData)).thenReturn(createdRoute)

        // 直接测试 RouteHandler 的逻辑
        val mockContext = Mockito.mock(RoutingContext::class.java)
        val mockResponse = Mockito.mock(io.vertx.core.http.HttpServerResponse::class.java)
        val mockRequest = Mockito.mock(io.vertx.core.http.HttpServerRequest::class.java)
        val mockBody = Mockito.mock(io.vertx.ext.web.RequestBody::class.java)

        // 设置 mock 对象的行为
        `when`(mockContext.response()).thenReturn(mockResponse)
        `when`(mockContext.request()).thenReturn(mockRequest)
        `when`(mockContext.body()).thenReturn(mockBody)
        `when`(mockBody.asJsonObject()).thenReturn(routeData)
        `when`(mockResponse.setStatusCode(Mockito.anyInt())).thenReturn(mockResponse)
        `when`(mockResponse.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(mockResponse)

        // 捕获 response.end() 调用
        `when`(mockResponse.end(Mockito.anyString())).thenAnswer { invocation ->
            val responseJson = JsonObject(invocation.getArgument<String>(0))
            testContext.verify {
                assertTrue(responseJson.containsKey("success"), "Response should contain 'success' field")
                assertTrue(responseJson.getBoolean("success"), "Success should be true")
                assertTrue(responseJson.containsKey("route"), "Response should contain 'route' field")
                assertEquals(routeId, responseJson.getJsonObject("route").getString("id"), "Route ID should match")
            }

            // 验证 routeManager.createRoute 被调用
            verify(routeManager).createRoute(routeData)

            testContext.completeNow()
            null
        }

        // 调用被测试的方法
        routeHandler.createRouteForTest(mockContext)
    }
}
