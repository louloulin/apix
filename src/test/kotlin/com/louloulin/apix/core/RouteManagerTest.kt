package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.models.Route
import com.louloulin.apix.core.PluginChain
import com.louloulin.apix.core.PluginManager
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(VertxExtension::class)
class RouteManagerTest {

    private lateinit var vertx: Vertx
    private lateinit var configManager: ConfigManager
    private lateinit var pluginManager: PluginManager
    private lateinit var routeManager: RouteManager

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()

        // Mock config manager
        configManager = mock(ConfigManager::class.java)
        `when`(configManager.getRoutesConfig()).thenReturn(JsonArray()
            .add(JsonObject()
                .put("id", "test-route")
                .put("name", "Test Route")
                .put("path", "/test")
                .put("methods", JsonArray().add("GET"))
                .put("targetUrl", "http://example.com")
                .put("plugins", JsonArray().add("test-plugin"))
                .put("enabled", true)
            )
        )

        // Mock plugin manager
        pluginManager = mock(PluginManager::class.java)
        `when`(pluginManager.createPluginChain(any())).thenReturn(PluginChain(emptyList()))

        // Create route manager
        routeManager = RouteManager(vertx, Router.router(vertx), configManager, pluginManager)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `should load routes from configuration`() {
        // Verify that the route was loaded
        val route = routeManager.getRoute("test-route")
        assertNotNull(route)
        assertEquals("test-route", route.id)
        assertEquals("/test", route.path)
        assertEquals(listOf("GET"), route.methods)
        assertEquals("http://example.com", route.targetUrl)
        assertEquals(listOf("test-plugin"), route.plugins)
        assertEquals(true, route.enabled)
    }

    @Test
    fun `should set up routes on router`() {
        // Create mock router
        val router = mock(Router::class.java)

        // Set up routes
        routeManager.addRoute(Route(
            id = "test-route",
            name = "Test Route",
            path = "/test",
            methods = listOf("GET"),
            targetUrl = "http://example.com",
            plugins = listOf("test-plugin"),
            enabled = true
        ))

        // Verify that the route was added to the router
        verify(router).route(HttpMethod.GET, "/test")
    }

    @Test
    fun `should update route`() {
        // Create a new route
        val newRoute = Route(
            id = "new-route",
            name = "New Route",
            path = "/new",
            methods = listOf("POST"),
            targetUrl = "http://new-example.com",
            plugins = listOf("new-plugin"),
            enabled = true
        )

        // Add the route
        routeManager.addRoute(newRoute)

        // Verify that the route was added
        val route = routeManager.getRoute("new-route")
        assertNotNull(route)
        assertEquals("new-route", route.id)
        assertEquals("/new", route.path)
    }

    @Test
    fun `should remove route`() {
        // Remove the route
        routeManager.removeRoute("test-route")

        // Verify that the route was removed
        val route = routeManager.getRoute("test-route")
        assertNull(route)
    }

    @Test
    fun `should get all routes`() {
        // Get all routes
        val routes = routeManager.getAllRoutes()

        // Verify that the route is in the list
        assertEquals(1, routes.size)
        assertEquals("test-route", routes.first().id)
    }
}
