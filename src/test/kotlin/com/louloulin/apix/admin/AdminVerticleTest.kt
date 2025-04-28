package com.louloulin.apix.admin

import com.louloulin.apix.core.verticle.AdminVerticle
import com.louloulin.apix.core.verticle.ConfigVerticle
import com.louloulin.apix.core.verticle.DeploymentVerticle
import com.louloulin.apix.core.verticle.MonitorVerticle
import com.louloulin.apix.core.verticle.PluginVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * AdminVerticle 测试
 */
@ExtendWith(VertxExtension::class)
class AdminVerticleTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 部署测试所需的 Verticle
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(MonitorVerticle()) }
            .compose { vertx.deployVerticle(PluginVerticle()) }
            .compose { vertx.deployVerticle(DeploymentVerticle()) }
            .compose { vertx.deployVerticle(AdminVerticle()) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 创建 WebClient
                    webClient = WebClient.create(vertx, WebClientOptions()
                        .setDefaultHost("localhost")
                        .setDefaultPort(8081)
                    )
                    
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testHealthEndpoint(testContext: VertxTestContext) {
        webClient.get("/health")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.getString("status") == "UP") { "Expected status to be UP but got ${body.getString("status")}" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testGetRoutes(testContext: VertxTestContext) {
        webClient.get("/api/routes")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("routes")) { "Expected body to contain routes" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testCreateAndGetRoute(testContext: VertxTestContext) {
        // 创建路由
        val routeJson = JsonObject()
            .put("name", "Test Route")
            .put("path", "/test")
            .put("methods", JsonObject().put("GET", true))
            .put("targetUrl", "http://example.com")
            .put("enabled", true)
        
        webClient.post("/api/routes")
            .sendJsonObject(routeJson)
            .onComplete { createAr ->
                if (createAr.succeeded()) {
                    val createResponse = createAr.result()
                    testContext.verify {
                        assert(createResponse.statusCode() == 201) { "Expected status code 201 but got ${createResponse.statusCode()}" }
                        val createBody = createResponse.bodyAsJsonObject()
                        assert(createBody.containsKey("route")) { "Expected body to contain route" }
                        val route = createBody.getJsonObject("route")
                        assert(route.getString("name") == "Test Route") { "Expected name to be Test Route but got ${route.getString("name")}" }
                        
                        // 获取路由
                        val routeId = route.getString("id")
                        webClient.get("/api/routes/$routeId")
                            .send()
                            .onComplete { getAr ->
                                if (getAr.succeeded()) {
                                    val getResponse = getAr.result()
                                    testContext.verify {
                                        assert(getResponse.statusCode() == 200) { "Expected status code 200 but got ${getResponse.statusCode()}" }
                                        val getBody = getResponse.bodyAsJsonObject()
                                        assert(getBody.containsKey("route")) { "Expected body to contain route" }
                                        val getRoute = getBody.getJsonObject("route")
                                        assert(getRoute.getString("id") == routeId) { "Expected id to be $routeId but got ${getRoute.getString("id")}" }
                                        assert(getRoute.getString("name") == "Test Route") { "Expected name to be Test Route but got ${getRoute.getString("name")}" }
                                        testContext.completeNow()
                                    }
                                } else {
                                    testContext.failNow(getAr.cause())
                                }
                            }
                    }
                } else {
                    testContext.failNow(createAr.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testUpdateRoute(testContext: VertxTestContext) {
        // 创建路由
        val routeJson = JsonObject()
            .put("name", "Test Route")
            .put("path", "/test")
            .put("methods", JsonObject().put("GET", true))
            .put("targetUrl", "http://example.com")
            .put("enabled", true)
        
        webClient.post("/api/routes")
            .sendJsonObject(routeJson)
            .onComplete { createAr ->
                if (createAr.succeeded()) {
                    val createResponse = createAr.result()
                    val createBody = createResponse.bodyAsJsonObject()
                    val route = createBody.getJsonObject("route")
                    val routeId = route.getString("id")
                    
                    // 更新路由
                    val updateJson = JsonObject()
                        .put("name", "Updated Route")
                        .put("path", "/updated")
                        .put("methods", JsonObject().put("GET", true).put("POST", true))
                        .put("targetUrl", "http://updated-example.com")
                        .put("enabled", false)
                    
                    webClient.put("/api/routes/$routeId")
                        .sendJsonObject(updateJson)
                        .onComplete { updateAr ->
                            if (updateAr.succeeded()) {
                                val updateResponse = updateAr.result()
                                testContext.verify {
                                    assert(updateResponse.statusCode() == 200) { "Expected status code 200 but got ${updateResponse.statusCode()}" }
                                    val updateBody = updateResponse.bodyAsJsonObject()
                                    assert(updateBody.containsKey("route")) { "Expected body to contain route" }
                                    val updatedRoute = updateBody.getJsonObject("route")
                                    assert(updatedRoute.getString("id") == routeId) { "Expected id to be $routeId but got ${updatedRoute.getString("id")}" }
                                    assert(updatedRoute.getString("name") == "Updated Route") { "Expected name to be Updated Route but got ${updatedRoute.getString("name")}" }
                                    assert(updatedRoute.getString("path") == "/updated") { "Expected path to be /updated but got ${updatedRoute.getString("path")}" }
                                    assert(updatedRoute.getBoolean("enabled") == false) { "Expected enabled to be false but got ${updatedRoute.getBoolean("enabled")}" }
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(updateAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(createAr.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testDeleteRoute(testContext: VertxTestContext) {
        // 创建路由
        val routeJson = JsonObject()
            .put("name", "Test Route")
            .put("path", "/test")
            .put("methods", JsonObject().put("GET", true))
            .put("targetUrl", "http://example.com")
            .put("enabled", true)
        
        webClient.post("/api/routes")
            .sendJsonObject(routeJson)
            .onComplete { createAr ->
                if (createAr.succeeded()) {
                    val createResponse = createAr.result()
                    val createBody = createResponse.bodyAsJsonObject()
                    val route = createBody.getJsonObject("route")
                    val routeId = route.getString("id")
                    
                    // 删除路由
                    webClient.delete("/api/routes/$routeId")
                        .send()
                        .onComplete { deleteAr ->
                            if (deleteAr.succeeded()) {
                                val deleteResponse = deleteAr.result()
                                testContext.verify {
                                    assert(deleteResponse.statusCode() == 204) { "Expected status code 204 but got ${deleteResponse.statusCode()}" }
                                    
                                    // 尝试获取已删除的路由
                                    webClient.get("/api/routes/$routeId")
                                        .send()
                                        .onComplete { getAr ->
                                            if (getAr.succeeded()) {
                                                val getResponse = getAr.result()
                                                testContext.verify {
                                                    assert(getResponse.statusCode() == 404) { "Expected status code 404 but got ${getResponse.statusCode()}" }
                                                    testContext.completeNow()
                                                }
                                            } else {
                                                testContext.failNow(getAr.cause())
                                            }
                                        }
                                }
                            } else {
                                testContext.failNow(deleteAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(createAr.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testGetPlugins(testContext: VertxTestContext) {
        webClient.get("/api/plugins")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("plugins")) { "Expected body to contain plugins" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testGetConfig(testContext: VertxTestContext) {
        webClient.get("/api/config")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("config")) { "Expected body to contain config" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testGetSystemInfo(testContext: VertxTestContext) {
        webClient.get("/api/system/info")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("system")) { "Expected body to contain system" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testGetMetrics(testContext: VertxTestContext) {
        webClient.get("/api/system/metrics")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("metrics")) { "Expected body to contain metrics" }
                        testContext.completeNow()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
