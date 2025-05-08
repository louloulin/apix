package com.louloulin.apix.plugins

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory

/**
 * 测试基于 Vert.x EventBus 的插件功能
 */
@RunWith(VertxUnitRunner::class)
class PluginEventBusTest {
    private val logger = LoggerFactory.getLogger(PluginEventBusTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var registry: PluginRegistry

    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        registry = PluginRegistry.getInstance(vertx)

        // 注册测试插件工厂
        registry.registerFactory("test-plugin", TestPluginFactory())

        // 等待设置完成
        testContext.async().complete()
    }

    @After
    fun tearDown(testContext: TestContext) {
        vertx.close(testContext.asyncAssertSuccess())
    }

    @Test
    fun testEventBusPluginCommunication(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试地址
        val testAddress = "test.eventbus.plugin"

        // 注册消息处理器
        vertx.eventBus().consumer<JsonObject>(testAddress) { message ->
            logger.info("Received message on {}: {}", testAddress, message.body().encode())

            // 回复消息
            message.reply(JsonObject()
                .put("success", true)
                .put("message", "Message received")
                .put("timestamp", System.currentTimeMillis()))
        }

        // 发送消息
        vertx.eventBus().request<JsonObject>(testAddress, JsonObject().put("test", "value")) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                testContext.assertTrue(response.getBoolean("success"))
                testContext.assertNotNull(response.getString("message"))
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }

    @Test
    fun testPluginLifecycle(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试插件配置
        val pluginConfig = JsonObject()
            .put("id", "lifecycle-plugin")
            .put("type", "test-plugin")
            .put("priority", 100)
            .put("testParam", "lifecycle-test")

        // 加载插件
        registry.loadPlugin(PluginConfig("lifecycle-plugin", "test-plugin", pluginConfig))
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val plugin = ar.result()
                    testContext.assertNotNull(plugin)
                    testContext.assertEquals("lifecycle-plugin", plugin.id)

                    // 卸载插件
                    registry.unloadPlugin("lifecycle-plugin")
                        .onComplete { unloadAr ->
                            if (unloadAr.succeeded()) {
                                // 验证插件不再存在
                                testContext.assertNull(registry.getPlugin("lifecycle-plugin"))
                                async.complete()
                            } else {
                                testContext.fail(unloadAr.cause())
                            }
                        }
                } else {
                    testContext.fail(ar.cause())
                }
            }
    }

    /**
     * 测试插件工厂
     */
    class TestPluginFactory : PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return TestPlugin(config.id, config.type, config)
        }
    }

    /**
     * 测试插件实现
     */
    class TestPlugin(
        override val id: String,
        override val type: String,
        override val config: PluginConfig
    ) : AbstractPlugin() {
        private val logger = LoggerFactory.getLogger(TestPlugin::class.java)

        override fun initialize(vertx: Vertx): Future<Void> {
            logger.info("Initializing test plugin: {}", id)
            return super.initialize(vertx)
        }

        override fun execute(context: io.vertx.ext.web.RoutingContext): Future<Void> {
            logger.info("Executing test plugin: {}", id)
            return super.execute(context)
        }

        override fun shutdown(returnFuture: Boolean): Future<Void>? {
            logger.info("Shutting down test plugin: {}", id)
            return super.shutdown(returnFuture)
        }
    }
}
