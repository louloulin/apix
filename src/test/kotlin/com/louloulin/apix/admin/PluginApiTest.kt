package com.louloulin.apix.admin

import com.louloulin.apix.plugins.UnifiedPluginManager
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class PluginApiTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private lateinit var pluginManager: UnifiedPluginManager

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        this.pluginManager = UnifiedPluginManager.getInstance(vertx)
        
        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(8081)
        )
        
        // 部署 AdminVerticle
        vertx.deployVerticle(AdminVerticle()) { ar ->
            if (ar.succeeded()) {
                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        webClient.close()
        testContext.completeNow()
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testGetPlugins(testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint()
        
        webClient.get("/api/plugins")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("plugins")) { "Response should contain 'plugins' field" }
                        checkpoint.flag()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testCreateAndDeletePlugin(testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint()
        
        // 创建测试插件
        val testPlugin = JsonObject()
            .put("id", "test-plugin-${System.currentTimeMillis()}")
            .put("type", "authentication")
            .put("config", JsonObject()
                .put("provider", "jwt")
                .put("secret", "test-secret")
            )
        
        // 创建插件
        webClient.post("/api/plugins")
            .sendJsonObject(testPlugin)
            .compose { response ->
                testContext.verify {
                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                    val body = response.bodyAsJsonObject()
                    assert(body.getBoolean("success", false)) { "Expected success to be true" }
                    assert(body.containsKey("plugin")) { "Response should contain 'plugin' field" }
                    val plugin = body.getJsonObject("plugin")
                    assert(plugin.getString("id") == testPlugin.getString("id")) { "Plugin ID should match" }
                }
                
                // 获取插件
                webClient.get("/api/plugins/${testPlugin.getString("id")}")
                    .send()
            }
            .compose { response ->
                testContext.verify {
                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                    val body = response.bodyAsJsonObject()
                    assert(body.containsKey("plugin")) { "Response should contain 'plugin' field" }
                    val plugin = body.getJsonObject("plugin")
                    assert(plugin.getString("id") == testPlugin.getString("id")) { "Plugin ID should match" }
                }
                
                // 禁用插件
                webClient.post("/api/plugins/${testPlugin.getString("id")}/disable")
                    .send()
            }
            .compose { response ->
                testContext.verify {
                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                    val body = response.bodyAsJsonObject()
                    assert(body.getBoolean("success", false)) { "Expected success to be true" }
                }
                
                // 启用插件
                webClient.post("/api/plugins/${testPlugin.getString("id")}/enable")
                    .send()
            }
            .compose { response ->
                testContext.verify {
                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                    val body = response.bodyAsJsonObject()
                    assert(body.getBoolean("success", false)) { "Expected success to be true" }
                }
                
                // 删除插件
                webClient.delete("/api/plugins/${testPlugin.getString("id")}")
                    .send()
            }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.getBoolean("success", false)) { "Expected success to be true" }
                        checkpoint.flag()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testGetPluginTypes(testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint()
        
        webClient.get("/api/plugins/types")
            .send()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val response = ar.result()
                    testContext.verify {
                        assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }
                        val body = response.bodyAsJsonObject()
                        assert(body.containsKey("types")) { "Response should contain 'types' field" }
                        val types = body.getJsonArray("types")
                        assert(types.size() > 0) { "Types array should not be empty" }
                        checkpoint.flag()
                    }
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
}
