package com.louloulin.apix.core.performance

import com.louloulin.apix.core.routing.DynamicRouteManager
import com.louloulin.apix.models.Route
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * 动态路由管理器测试
 */
@ExtendWith(VertxExtension::class)
class DynamicRouteManagerTest {
    private lateinit var vertx: Vertx
    private lateinit var router: Router
    private lateinit var routeManager: DynamicRouteManager

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        router = Router.router(vertx)
        routeManager = DynamicRouteManager(vertx, router)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun testUpdateRoute(testContext: VertxTestContext) {
        // 创建测试路由
        val route = Route(
            id = "test-route",
            name = "Test Route",
            path = "/api/test",
            methods = listOf("GET"),
            targetUrl = "http://example.com/api",
            plugins = emptyList(),
            enabled = true
        )

        // 更新路由
        routeManager.updateRoute(route).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    // 验证路由数量
                    assert(routeManager.getRouteCount() == 1) { "应该有一个路由" }

                    // 验证路由版本号
                    assert(routeManager.getRouteVersion() > 0) { "路由版本号应该大于0" }

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testDeleteRoute(testContext: VertxTestContext) {
        // 创建测试路由
        val route = Route(
            id = "test-route-delete",
            name = "Test Route Delete",
            path = "/api/test/delete",
            methods = listOf("GET"),
            targetUrl = "http://example.com/api",
            plugins = emptyList(),
            enabled = true
        )

        // 先添加路由
        routeManager.updateRoute(route).onComplete { ar1 ->
            if (ar1.succeeded()) {
                // 然后删除路由
                routeManager.deleteRoute(route.id).onComplete { ar2 ->
                    if (ar2.succeeded()) {
                        testContext.verify {
                            // 验证路由数量
                            assert(routeManager.getRouteCount() == 0) { "删除后应该没有路由" }

                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(ar2.cause())
                    }
                }
            } else {
                testContext.failNow(ar1.cause())
            }
        }

        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testLoadRoutes(testContext: VertxTestContext) {
        // 创建测试路由列表
        val routes = listOf(
            Route(
                id = "route1",
                name = "Route 1",
                path = "/api/route1",
                methods = listOf("GET"),
                targetUrl = "http://example.com/api1",
                plugins = emptyList(),
                enabled = true
            ),
            Route(
                id = "route2",
                name = "Route 2",
                path = "/api/route2",
                methods = listOf("POST"),
                targetUrl = "http://example.com/api2",
                plugins = emptyList(),
                enabled = true
            ),
            Route(
                id = "route3",
                name = "Route 3",
                path = "/api/route3",
                methods = listOf("PUT"),
                targetUrl = "http://example.com/api3",
                plugins = emptyList(),
                enabled = false // 这个路由不会被加载，因为它被禁用了
            )
        )

        // 批量加载路由
        routeManager.loadRoutes(routes).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    // 验证路由数量（应该是2，因为route3被禁用了）
                    assert(routeManager.getRouteCount() == 2) { "应该有2个路由" }

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testEventBusIntegration(testContext: VertxTestContext) {
        // 通过EventBus更新路由
        val routeConfig = JsonObject()
            .put("id", "eventbus-route")
            .put("name", "EventBus Route")
            .put("path", "/api/eventbus")
            .put("method", "GET")
            .put("targetUrl", "http://example.com/api")
            .put("plugins", JsonArray())
            .put("enabled", true)

        vertx.eventBus().request<JsonObject>("route.update", routeConfig).onComplete { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                testContext.verify {
                    // 验证响应
                    assert(response.getBoolean("success")) { "应该返回成功" }

                    // 验证路由数量
                    assert(routeManager.getRouteCount() == 1) { "应该有一个路由" }

                    // 通过EventBus获取路由列表
                    vertx.eventBus().request<JsonObject>("route.list", JsonObject()).onComplete { ar2 ->
                        if (ar2.succeeded()) {
                            val listResponse = ar2.result().body()

                            testContext.verify {
                                // 验证响应
                                assert(listResponse.getBoolean("success")) { "应该返回成功" }

                                // 验证路由数量
                                assert(listResponse.getInteger("count") == 1) { "应该有一个路由" }

                                testContext.completeNow()
                            }
                        } else {
                            testContext.failNow(ar2.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        // 等待测试完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
}
