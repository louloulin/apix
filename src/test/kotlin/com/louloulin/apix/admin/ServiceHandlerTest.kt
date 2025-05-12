package com.louloulin.apix.admin

import com.louloulin.apix.core.BaseVertxTest
import com.louloulin.apix.core.ServiceManager
import com.louloulin.apix.models.Service
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
class ServiceHandlerTest : BaseVertxTest() {

    private lateinit var serviceManager: ServiceManager
    private lateinit var serviceHandler: ServiceHandler

    @BeforeEach
    override fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        super.setUp(vertx, testContext)
        serviceManager = Mockito.mock(ServiceManager::class.java)
        serviceHandler = ServiceHandler(serviceManager)

        testContext.completeNow()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testGetServices(testContext: VertxTestContext) {
        // Mock data
        val service1Id = UUID.randomUUID().toString()
        val service2Id = UUID.randomUUID().toString()

        val service1 = Service(
            id = service1Id,
            name = "Test Service",
            url = "http://localhost:8080",
            protocol = "http",
            host = "localhost",
            port = 8080,
            enabled = true
        )

        val service2 = Service(
            id = service2Id,
            name = "Test Service 2",
            url = "http://localhost:8081",
            protocol = "http",
            host = "localhost",
            port = 8081,
            enabled = false
        )

        val services = listOf(service1, service2)

        // Mock the service manager
        `when`(serviceManager.getServices()).thenReturn(services)

        // Create a test router
        val router = Router.router(vertx)
        serviceHandler.setupRoutes(router)

        // Create a test server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(0) // Random port
            .onSuccess { server ->
                val port = server.actualPort()

                // Make a request to the server
                vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.GET, port, "localhost", "/services")
                    .onSuccess { request ->
                        request.send()
                            .onSuccess { response ->
                                testContext.verify {
                                    assertEquals(200, response.statusCode(), "Expected status code 200 but got ${response.statusCode()}")
                                }

                                response.body()
                                    .onSuccess { body ->
                                        testContext.verify {
                                            val json = JsonObject(body)
                                            assertTrue(json.containsKey("services"), "Response should contain 'services' field")
                                            assertEquals(2, json.getJsonArray("services").size(), "Services array should contain 2 items")
                                        }

                                        // Verify that the service manager was called
                                        verify(serviceManager).getServices()

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
    fun testCreateService(testContext: VertxTestContext) {
        // Mock data
        val serviceId = UUID.randomUUID().toString()
        val serviceData = JsonObject()
            .put("name", "Test Service")
            .put("url", "http://localhost:8080")

        val createdService = Service(
            id = serviceId,
            name = "Test Service",
            url = "http://localhost:8080",
            protocol = "http",
            host = "localhost",
            port = 8080,
            enabled = true
        )

        // Mock the service manager
        `when`(serviceManager.createService(serviceData)).thenReturn(createdService)

        // 直接测试 ServiceHandler 的逻辑
        val mockContext = Mockito.mock(RoutingContext::class.java)
        val mockResponse = Mockito.mock(io.vertx.core.http.HttpServerResponse::class.java)
        val mockRequest = Mockito.mock(io.vertx.core.http.HttpServerRequest::class.java)
        val mockBody = Mockito.mock(io.vertx.ext.web.RequestBody::class.java)

        // 设置 mock 对象的行为
        `when`(mockContext.response()).thenReturn(mockResponse)
        `when`(mockContext.request()).thenReturn(mockRequest)
        `when`(mockContext.body()).thenReturn(mockBody)
        `when`(mockBody.asJsonObject()).thenReturn(serviceData)
        `when`(mockResponse.setStatusCode(Mockito.anyInt())).thenReturn(mockResponse)
        `when`(mockResponse.putHeader(Mockito.anyString(), Mockito.anyString())).thenReturn(mockResponse)

        // 捕获 response.end() 调用
        `when`(mockResponse.end(Mockito.anyString())).thenAnswer { invocation ->
            val responseJson = JsonObject(invocation.getArgument<String>(0))
            testContext.verify {
                assertTrue(responseJson.containsKey("success"), "Response should contain 'success' field")
                assertTrue(responseJson.getBoolean("success"), "Success should be true")
                assertTrue(responseJson.containsKey("service"), "Response should contain 'service' field")
                assertEquals(serviceId, responseJson.getJsonObject("service").getString("id"), "Service ID should match")
            }

            // 验证 serviceManager.createService 被调用
            verify(serviceManager).createService(serviceData)

            testContext.completeNow()
            null
        }

        // 调用被测试的方法
        serviceHandler.createServiceForTest(mockContext)
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testGetServiceHealth(testContext: VertxTestContext) {
        // Mock data
        val serviceId = UUID.randomUUID().toString()
        val service = Service(
            id = serviceId,
            name = "Test Service",
            url = "http://localhost:8080",
            protocol = "http",
            host = "localhost",
            port = 8080,
            enabled = true
        )

        val health = JsonObject()
            .put("status", "UP")
            .put("timestamp", System.currentTimeMillis())

        // Mock the service manager
        `when`(serviceManager.getService(serviceId)).thenReturn(service)
        `when`(serviceManager.getServiceHealth(serviceId)).thenReturn(health)

        // Create a test router
        val router = Router.router(vertx)
        serviceHandler.setupRoutes(router)

        // Create a test server
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(0) // Random port
            .onSuccess { server ->
                val port = server.actualPort()

                // Make a request to the server
                vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.GET, port, "localhost", "/services/$serviceId/health")
                    .onSuccess { request ->
                        request.send()
                            .onSuccess { response ->
                                testContext.verify {
                                    assertEquals(200, response.statusCode(), "Expected status code 200 but got ${response.statusCode()}")
                                }

                                response.body()
                                    .onSuccess { body ->
                                        testContext.verify {
                                            val json = JsonObject(body)
                                            assertTrue(json.containsKey("status"), "Response should contain 'status' field")
                                            assertEquals("UP", json.getString("status"), "Status should be UP")
                                        }

                                        // Verify that the service manager was called
                                        verify(serviceManager).getService(serviceId)
                                        verify(serviceManager).getServiceHealth(serviceId)

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
}
