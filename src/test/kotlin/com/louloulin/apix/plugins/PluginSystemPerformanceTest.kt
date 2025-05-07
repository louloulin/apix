package com.louloulin.apix.plugins

import com.louloulin.apix.plugins.condition.ConditionExpressionParser
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.RoutingContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 插件系统性能测试
 */
@RunWith(VertxUnitRunner::class)
class PluginSystemPerformanceTest {
    private val logger = LoggerFactory.getLogger(PluginSystemPerformanceTest::class.java)

    private lateinit var vertx: Vertx
    private lateinit var registry: PluginRegistry

    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        registry = PluginRegistry.getInstance(vertx)

        // 注册测试插件工厂
        registry.registerFactory("test", TestPluginFactory())

        testContext.async().complete()
    }

    @After
    fun tearDown(testContext: TestContext) {
        registry.healthCheck().onComplete { ar ->
            vertx.close(testContext.asyncAssertSuccess())
        }
    }

    @Test
    fun testPluginChainPerformance(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试配置
        val pluginsConfig = JsonArray()

        // 添加 10 个测试插件
        for (i in 1..10) {
            val config = JsonObject()
                .put("id", "test-plugin-$i")
                .put("type", "test")
                .put("priority", i * 100)
                .put("parallelExecution", i % 2 == 0) // 偶数插件并行执行
                .put("condition", JsonObject()
                    .put("path", "/api/*")
                    .put("method", JsonArray().add("GET").add("POST"))
                )

            pluginsConfig.add(config)
        }

        // 加载插件
        val loadConfig = JsonObject().put("plugins", pluginsConfig)
        registry.loadFromConfig(loadConfig).compose { _ ->
            // 创建插件链
            val chain = registry.createPluginChain()

            // 创建测试上下文
            val context = createMockRoutingContext()

            // 预热
            logger.info("Warming up...")
            val warmupFutures = mutableListOf<Future<*>>()
            for (i in 1..100) {
                warmupFutures.add(chain.execute(context))
            }

            Future.all(warmupFutures)
        }.compose { _ ->
            // 创建插件链
            val chain = registry.createPluginChain()

            // 创建测试上下文
            val context = createMockRoutingContext()

            // 执行性能测试
            logger.info("Starting performance test...")
            val iterations = 1000
            val startTime = System.currentTimeMillis()

            val futures = mutableListOf<Future<*>>()
            for (i in 1..iterations) {
                futures.add(chain.execute(context))
            }

            Future.all(futures).map {
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime
                val throughput = iterations * 1000.0 / duration

                logger.info("Performance test results:")
                logger.info("Iterations: {}", iterations)
                logger.info("Duration: {} ms", duration)
                logger.info("Throughput: {}/s", String.format("%.2f", throughput))

                null as Void?
            }
        }.onComplete { ar ->
            if (ar.succeeded()) {
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }

    @Test
    fun testParallelPluginExecution(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试配置
        val pluginsConfig = JsonArray()

        // 添加 10 个并行执行的插件
        for (i in 1..10) {
            val config = JsonObject()
                .put("id", "parallel-plugin-$i")
                .put("type", "test")
                .put("priority", 100) // 相同优先级
                .put("parallelExecution", true)
                .put("delay", 50) // 50ms 延迟

            pluginsConfig.add(config)
        }

        // 加载插件
        val loadConfig = JsonObject().put("plugins", pluginsConfig)
        registry.loadFromConfig(loadConfig).compose { _ ->
            // 创建插件链
            val chain = registry.createPluginChain()

            // 创建测试上下文
            val context = createMockRoutingContext()

            // 执行插件链
            val startTime = System.currentTimeMillis()

            chain.execute(context).map {
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                logger.info("Parallel execution test results:")
                logger.info("Duration: {} ms", duration)

                // 验证并行执行时间小于顺序执行时间
                // 顺序执行时间应该是 10 * 50 = 500ms
                // 并行执行时间应该接近 50ms
                testContext.assertTrue(duration < 300, "Parallel execution should be faster than sequential execution")

                null as Void?
            }
        }.onComplete { ar ->
            if (ar.succeeded()) {
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }

    @Test
    fun testConditionExpressionPerformance(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试配置
        val config = JsonObject()
            .put("path", "/api/users")
            .put("method", JsonArray().add("GET").add("POST"))
            .put("headers", JsonObject().put("Content-Type", "application/json"))
            .put("operator", "AND")

        // 解析条件表达式
        val condition = ConditionExpressionParser.parse(config)

        // 创建测试上下文
        val context = createMockRoutingContext()

        // 执行性能测试
        logger.info("Starting condition expression performance test...")
        val iterations = 10000
        val startTime = System.currentTimeMillis()

        for (i in 1..iterations) {
            condition.evaluate(context)
        }

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        val throughput = iterations * 1000.0 / duration

        logger.info("Condition expression performance test results:")
        logger.info("Iterations: {}", iterations)
        logger.info("Duration: {} ms", duration)
        logger.info("Throughput: {}/s", String.format("%.2f", throughput))

        async.complete()
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
    ) : AbstractPlugin() {
        private val delay = config.getLong("delay") ?: 0

        override fun onRequest(context: RoutingContext): Future<Void> {
            val promise = Promise.promise<Void>()

            if (delay > 0) {
                // 模拟处理延迟
                vertx.setTimer(delay) {
                    context.put("executed-$id", true)
                    promise.complete()
                }
            } else {
                // 直接完成
                context.put("executed-$id", true)
                promise.complete()
            }

            return promise.future()
        }
    }

    /**
     * 创建模拟的 RoutingContext
     */
    private fun createMockRoutingContext(): RoutingContext {
        // 使用 Mockito 创建模拟对象
        val context = org.mockito.Mockito.mock(RoutingContext::class.java)
        val request = org.mockito.Mockito.mock(io.vertx.core.http.HttpServerRequest::class.java)
        val response = org.mockito.Mockito.mock(io.vertx.core.http.HttpServerResponse::class.java)

        // 设置基本行为
        org.mockito.Mockito.`when`(context.request()).thenReturn(request)
        org.mockito.Mockito.`when`(context.response()).thenReturn(response)
        org.mockito.Mockito.`when`(request.path()).thenReturn("/api/users")
        org.mockito.Mockito.`when`(request.method()).thenReturn(HttpMethod.GET)
        org.mockito.Mockito.`when`(request.getHeader("Content-Type")).thenReturn("application/json")
        org.mockito.Mockito.`when`(response.ended()).thenReturn(false)

        // 设置数据存储
        val data = mutableMapOf<String, Any>()
        org.mockito.Mockito.`when`(context.data()).thenReturn(data)
        org.mockito.Mockito.`when`(context.put(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                val value = invocation.getArgument<Any>(1)
                data[key] = value
                context
            }
        org.mockito.Mockito.`when`(context.get<Any>(org.mockito.ArgumentMatchers.anyString()))
            .thenAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                data[key]
            }

        return context
    }
}
