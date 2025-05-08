package com.louloulin.apix.plugins

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.RoutingContext
import org.mockito.Mockito.mock
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 测试基于 Vert.x EventBus 的插件系统
 */
@RunWith(VertxUnitRunner::class)
class EventBusPluginTest {
    private lateinit var vertx: Vertx
    private lateinit var registry: PluginRegistry

    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        registry = PluginRegistry(vertx)

        // 注册测试插件工厂
        registry.registerFactory("test", TestPluginFactory())

        // 等待设置完成
        testContext.async().complete()
    }

    @After
    fun tearDown(testContext: TestContext) {
        vertx.close(testContext.asyncAssertSuccess())
    }

    @Test
    fun testPluginRegistration(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试配置
        val config = JsonObject()
            .put("plugins", JsonObject()
                .put("id", "test-plugin")
                .put("type", "test")
                .put("priority", 100)
                .put("parallelExecution", true)
            )

        // 加载插件
        registry.loadPlugin(PluginConfig("test-plugin", "test", config)).onComplete { ar ->
            testContext.assertTrue(ar.succeeded())
            testContext.assertNotNull(registry.getPlugin("test-plugin"))
            async.complete()
        }
    }

    @Test
    fun testPluginExecution(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试配置
        val config = JsonObject()
            .put("id", "test-plugin")
            .put("type", "test")
            .put("priority", 100)
            .put("parallelExecution", true)

        // 加载插件
        registry.loadPlugin(PluginConfig("test-plugin", "test", config)).compose { plugin ->
            // 创建插件链
            val chain = registry.createPluginChain()

            // 创建测试上下文
            val context = mock(RoutingContext::class.java)

            // 执行插件链
            chain.execute(context)
        }.onComplete { ar ->
            testContext.assertTrue(ar.succeeded())
            async.complete()
        }
    }

    @Test
    fun testEventBusExecution(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试配置
        val config = JsonObject()
            .put("id", "test-plugin")
            .put("type", "test")
            .put("priority", 100)
            .put("parallelExecution", true)

        // 加载插件
        registry.loadPlugin(PluginConfig("test-plugin", "test", config)).compose { plugin ->
            // 获取 EventBus 地址
            val address = plugin.getEventBusAddress()
            testContext.assertNotNull(address)

            // 创建测试上下文
            val context = mock(RoutingContext::class.java)

            // 序列化上下文
            val contextJson = plugin.serializeContext(context)

            // 通过 EventBus 执行插件
            vertx.eventBus().request<JsonObject>(address!!, contextJson)
        }.onComplete { ar ->
            testContext.assertTrue(ar.succeeded())
            val response = ar.result().body()
            testContext.assertTrue(response.getBoolean("success", false))
            async.complete()
        }
    }

    /**
     * 测试插件工厂
     */
    inner class TestPluginFactory : PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return TestPlugin(config.id, config.type, config)
        }
    }

    /**
     * 测试插件实现
     */
    inner class TestPlugin(
        override val id: String,
        override val type: String,
        override val config: PluginConfig
    ) : Plugin {
        override fun onRequest(context: RoutingContext): Future<Void> {
            // 简单的测试实现
            context.put("test-plugin-executed", true)
            return Future.succeededFuture()
        }

        override fun serializeContext(context: RoutingContext): JsonObject {
            // 简化的序列化实现
            return JsonObject()
                .put("path", "/test")
                .put("method", "GET")
        }

        override fun deserializeContext(vertx: Vertx, json: JsonObject): RoutingContext {
            // 简化的反序列化实现
            return mock(RoutingContext::class.java)
        }
    }
}
