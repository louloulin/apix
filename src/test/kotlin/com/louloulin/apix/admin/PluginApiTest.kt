package com.louloulin.apix.admin

import com.louloulin.apix.core.BaseVertxTest
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.UnifiedPluginManager
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*

@ExtendWith(VertxExtension::class)
class PluginApiTest : BaseVertxTest() {
    private lateinit var webClient: WebClient
    private lateinit var pluginManager: UnifiedPluginManager
    private val adminPort = 8081

    /**
     * 测试插件工厂
     */
    class TestPluginFactory : PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return TestPlugin(config.id, config.type, config)
        }
    }

    /**
     * 测试插件
     */
    class TestPlugin(override val id: String, override val type: String, override val config: PluginConfig) : Plugin {
        private val logger = LoggerFactory.getLogger(TestPlugin::class.java)

        override fun initialize(vertx: Vertx): Future<Void> {
            logger.info("Initializing test plugin: {}", id)
            return Future.succeededFuture()
        }

        override fun execute(context: RoutingContext): Future<Void> {
            logger.info("Executing test plugin: {}", id)
            return Future.succeededFuture()
        }

        override fun shutdown() {
            logger.info("Shutting down test plugin: {}", id)
        }
    }

    @BeforeEach
    override fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        super.setUp(vertx, testContext)
        this.pluginManager = UnifiedPluginManager.getInstance(vertx)

        // 注册测试插件工厂
        pluginManager.registerFactory("test-plugin", TestPluginFactory())

        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(adminPort)
        )

        // 部署 AdminVerticle
        vertx.deployVerticle(AdminVerticle()) { ar ->
            if (ar.succeeded()) {
                logger.info("AdminVerticle deployed successfully")
                testContext.completeNow()
            } else {
                logger.error("Failed to deploy AdminVerticle", ar.cause())
                testContext.failNow(ar.cause())
            }
        }
    }

    @AfterEach
    override fun tearDown(testContext: VertxTestContext) {
        logger.info("Tearing down PluginApiTest")
        webClient.close()
        super.tearDown(testContext)
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
        val pluginId = "test-plugin-${System.currentTimeMillis()}"
        val testPlugin = JsonObject()
            .put("id", pluginId)
            .put("type", "test-plugin") // 使用我们注册的测试插件类型
            .put("config", JsonObject()
                .put("name", "Test Plugin")
                .put("description", "A test plugin for unit testing")
            )

        logger.info("Creating test plugin with ID: {}", pluginId)

        // 直接使用 UnifiedPluginManager 创建插件
        pluginManager.createPlugin(testPlugin)
            .compose { createdPluginId ->
                logger.info("Plugin created with ID: {}", createdPluginId)
                testContext.verify {
                    assertEquals(pluginId, createdPluginId, "Plugin ID should match")
                    assertTrue(pluginManager.hasPlugin(pluginId), "Plugin should exist in manager")
                }

                // 获取插件
                val plugin = pluginManager.getPlugin(pluginId)
                testContext.verify {
                    assertNotNull(plugin, "Plugin should not be null")
                    assertEquals(pluginId, plugin?.id, "Plugin ID should match")
                    assertEquals("test-plugin", plugin?.type, "Plugin type should match")
                }

                // 禁用插件
                pluginManager.setPluginEnabled(pluginId, false)
            }
            .compose { _ ->
                // 验证插件已禁用
                testContext.verify {
                    assertFalse(pluginManager.isPluginEnabled(pluginId), "Plugin should be disabled")
                }

                // 启用插件
                pluginManager.setPluginEnabled(pluginId, true)
            }
            .compose { _ ->
                // 验证插件已启用
                testContext.verify {
                    assertTrue(pluginManager.isPluginEnabled(pluginId), "Plugin should be enabled")
                }

                // 删除插件
                pluginManager.unloadPlugin(pluginId)
            }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.verify {
                        assertFalse(pluginManager.hasPlugin(pluginId), "Plugin should be removed")
                        checkpoint.flag()
                    }
                } else {
                    logger.error("Failed to complete plugin test", ar.cause())
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testGetPluginTypes(testContext: VertxTestContext) {
        val checkpoint = testContext.checkpoint()

        // 直接获取插件类型
        val types = pluginManager.getPluginTypes()

        testContext.verify {
            assertNotNull(types, "Plugin types should not be null")
            assertTrue(types.contains("test-plugin"), "Plugin types should contain 'test-plugin'")
            checkpoint.flag()
        }
    }
}
