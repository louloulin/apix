package com.louloulin.apix.plugins.integration

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginRegistry
import com.louloulin.apix.plugins.condition.ConditionParser
import com.louloulin.apix.plugins.deploy.PluginHotDeployer
import com.louloulin.apix.plugins.metrics.PluginMetrics
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.RoutingContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 插件系统集成测试
 * 测试插件系统的各个方面，包括条件执行、指标收集、热部署等
 */
@RunWith(VertxUnitRunner::class)
class PluginSystemIntegrationTest {

    private lateinit var vertx: Vertx
    private lateinit var pluginRegistry: PluginRegistry
    private lateinit var pluginMetrics: PluginMetrics
    private lateinit var conditionParser: ConditionParser
    private lateinit var pluginHotDeployer: PluginHotDeployer
    private lateinit var tempPluginDir: File

    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        pluginRegistry = PluginRegistry.getInstance(vertx)
        pluginMetrics = PluginMetrics.getInstance(vertx)
        conditionParser = ConditionParser.getInstance()

        // 创建临时插件目录
        tempPluginDir = Files.createTempDirectory("test-plugins").toFile()
        tempPluginDir.deleteOnExit()

        // 初始化插件热部署管理器
        pluginHotDeployer = PluginHotDeployer.getInstance(vertx, pluginRegistry, tempPluginDir.absolutePath)

        testContext.async().complete()
    }

    @After
    fun tearDown(testContext: TestContext) {
        // 停止插件热部署管理器
        pluginHotDeployer.stop().compose { _ ->
            // 清理临时目录
            tempPluginDir.deleteRecursively()
            // 关闭Vertx实例
            vertx.close()
        }.onComplete(testContext.asyncAssertSuccess())
    }

    /**
     * 测试条件执行机制
     */
    @Test
    fun testConditionExecution(testContext: TestContext) {
        val async = testContext.async()

        // 创建模拟的RoutingContext
        val context = mock(RoutingContext::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)
        `when`(context.request()).thenReturn(request)
        `when`(request.path()).thenReturn("/api/test")
        `when`(request.method()).thenReturn(HttpMethod.GET)

        // 创建条件配置
        val condition = JsonObject()
            .put("path", "/api/test")
            .put("method", "GET")

        // 创建插件配置
        val pluginConfig = JsonObject()
            .put("id", "test-plugin")
            .put("type", "test")
            .put("condition", condition)

        // 创建测试插件
        val testPlugin = TestPlugin("test-plugin", "test", JsonObject(pluginConfig.toString()))

        // 验证条件执行
        testContext.assertTrue(testPlugin.shouldExecute(context))

        // 修改条件，使其不匹配
        val newCondition = JsonObject()
        newCondition.put("path", "/api/other")
        newCondition.put("method", "GET")

        testPlugin.config.getJsonObject().put("condition", newCondition)

        // 验证条件不匹配
        testContext.assertFalse(testPlugin.shouldExecute(context))

        async.complete()
    }

    /**
     * 测试插件指标收集
     */
    @Test
    fun testPluginMetrics(testContext: TestContext) {
        val async = testContext.async()

        // 记录插件执行
        pluginMetrics.recordSuccess("test-plugin", 100)
        pluginMetrics.recordSuccess("test-plugin", 200)
        pluginMetrics.recordFailure("test-plugin", 300, RuntimeException("Test error"))

        // 获取插件指标
        val metrics = pluginMetrics.getMetrics("test-plugin")

        // 验证指标
        testContext.assertEquals(3, metrics.getLong("executions"))
        testContext.assertEquals(200.0, metrics.getDouble("avgExecutionTime"))
        testContext.assertEquals(1, metrics.getLong("errors"))
        testContext.assertEquals(300, metrics.getLong("maxExecutionTime"))
        testContext.assertEquals(100, metrics.getLong("minExecutionTime"))

        async.complete()
    }

    /**
     * 测试插件热部署
     */
    @Test
    fun testPluginHotDeployment(testContext: TestContext) {
        val async = testContext.async()

        // 创建插件配置文件
        val pluginConfig = JsonObject()
            .put("id", "hot-deploy-test")
            .put("type", "test")
            .put("enabled", true)

        val pluginFile = File(tempPluginDir, "hot-deploy-test.json")
        pluginFile.writeText(pluginConfig.encodePrettily())

        // 启动插件热部署
        pluginHotDeployer.start().onComplete { ar ->
            if (ar.succeeded()) {
                // 等待一段时间，让热部署管理器检测到文件
                vertx.setTimer(5000) { _ ->
                    // 验证插件是否被加载
                    testContext.assertTrue(pluginRegistry.hasPlugin("hot-deploy-test"))

                    // 修改插件配置
                    val updatedConfig = JsonObject()
                        .put("id", "hot-deploy-test")
                        .put("type", "test")
                        .put("enabled", false)

                    pluginFile.writeText(updatedConfig.encodePrettily())

                    // 再次等待，让热部署管理器检测到文件变化
                    vertx.setTimer(5000) { _ ->
                        // 验证插件是否被更新
                        testContext.assertTrue(pluginRegistry.hasPlugin("hot-deploy-test"))

                        // 删除插件配置文件
                        pluginFile.delete()

                        // 再次等待，让热部署管理器检测到文件删除
                        vertx.setTimer(5000) { _ ->
                            // 验证插件是否被卸载
                            testContext.assertFalse(pluginRegistry.hasPlugin("hot-deploy-test"))

                            async.complete()
                        }
                    }
                }
            } else {
                testContext.fail(ar.cause())
            }
        }
    }

    /**
     * 测试插件类，用于测试
     */
    class TestPlugin(
        override val id: String,
        override val type: String,
        jsonConfig: JsonObject
    ) : Plugin {
        override val config: PluginConfig = PluginConfig(id, type, jsonConfig)
        private val initialized = AtomicBoolean(false)
        private val executionCount = AtomicInteger(0)

        override fun initialize(vertx: Vertx): Future<Void> {
            val promise = Promise.promise<Void>()
            initialized.set(true)
            promise.complete()
            return promise.future()
        }

        override fun execute(context: RoutingContext): Future<Void> {
            val promise = Promise.promise<Void>()
            executionCount.incrementAndGet()
            promise.complete()
            return promise.future()
        }

        override fun shutdown() {
            initialized.set(false)
        }

        override fun shouldExecute(context: RoutingContext): Boolean {
            // 使用条件解析器判断是否应该执行
            val conditionConfig = config.getJsonObject("condition")
            if (conditionConfig == null) {
                return true
            }
            return ConditionParser.getInstance().evaluate(conditionConfig, context)
        }

        override fun canExecuteInParallel(): Boolean {
            return true
        }

        override fun getEventBusAddress(): String? {
            return null
        }

        override fun getPriority(): Int {
            return 0
        }

        // 这些方法不是 Plugin 接口的一部分，所以我们将它们作为普通的方法
        fun getGroup(): String {
            return "default"
        }

        fun isInitialized(): Boolean {
            return initialized.get()
        }

        fun getExecutionCount(): Int {
            return executionCount.get()
        }
    }
}
