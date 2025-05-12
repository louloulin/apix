package com.louloulin.apix.admin

import com.louloulin.apix.core.ServiceManager
import com.louloulin.apix.models.Service
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.ArgumentMatchers.any
import java.util.UUID

@ExtendWith(VertxExtension::class)
class ServiceHandlerTest {

    private lateinit var vertx: Vertx
    private lateinit var serviceManager: ServiceManager
    private lateinit var serviceHandler: ServiceHandler

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        serviceManager = Mockito.mock(ServiceManager::class.java)
        serviceHandler = ServiceHandler(serviceManager)

        testContext.completeNow()
    }

    @Test
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
                                    assert(response.statusCode() == 200)
                                }

                                response.body()
                                    .onSuccess { body ->
                                        testContext.verify {
                                            val json = JsonObject(body)
                                            assert(json.containsKey("services"))
                                            assert(json.getJsonArray("services").size() == 2)
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
        `when`(serviceManager.createService(any())).thenReturn(createdService)

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
                vertx.createHttpClient().request(io.vertx.core.http.HttpMethod.POST, port, "localhost", "/services")
                    .onSuccess { request ->
                        request.putHeader("Content-Type", "application/json")
                        request.send(serviceData.toBuffer())
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
                                            assert(json.containsKey("service"))
                                            assert(json.getJsonObject("service").getString("id") == serviceId)
                                        }

                                        // Verify that the service manager was called
                                        verify(serviceManager).createService(serviceData)

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
                                    assert(response.statusCode() == 200)
                                }

                                response.body()
                                    .onSuccess { body ->
                                        testContext.verify {
                                            val json = JsonObject(body)
                                            assert(json.containsKey("status"))
                                            assert(json.getString("status") == "UP")
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
