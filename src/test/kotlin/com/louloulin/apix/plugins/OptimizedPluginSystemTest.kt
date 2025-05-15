package com.louloulin.apix.plugins

import com.louloulin.apix.plugins.execution.OptimizedPluginChain
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(VertxExtension::class)
class OptimizedPluginSystemTest {
    private val logger = LoggerFactory.getLogger(OptimizedPluginSystemTest::class.java)
    
    private lateinit var vertx: Vertx
    private lateinit var pluginSystem: OptimizedPluginSystem
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 创建插件系统
        pluginSystem = OptimizedPluginSystem.getInstance(vertx)
        
        // 注册测试插件工厂
        pluginSystem.registerPluginFactory("test", TestPluginFactory())
        
        // 启动插件系统
        pluginSystem.start()
            .onSuccess { _ ->
                logger.info("Plugin system started")
                testContext.completeNow()
            }
            .onFailure { err ->
                logger.error("Failed to start plugin system", err)
                testContext.failNow(err)
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        // 停止插件系统
        pluginSystem.stop()
            .onSuccess { _ ->
                logger.info("Plugin system stopped")
                vertx.close()
                    .onSuccess { _ ->
                        testContext.completeNow()
                    }
                    .onFailure { err ->
                        logger.error("Failed to close Vertx", err)
                        testContext.failNow(err)
                    }
            }
            .onFailure { err ->
                logger.error("Failed to stop plugin system", err)
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testLoadPlugin(testContext: VertxTestContext) {
        // 创建插件配置
        val config = PluginConfig(
            "test-plugin",
            "test",
            JsonObject()
                .put("id", "test-plugin")
                .put("type", "test")
                .put("name", "Test Plugin")
                .put("description", "A test plugin")
                .put("version", "1.0.0")
        )
        
        // 加载插件
        pluginSystem.loadPlugin(config)
            .onSuccess { plugin ->
                testContext.verify {
                    assertNotNull(plugin)
                    assertEquals("test-plugin", plugin.id)
                    assertEquals("test", plugin.type)
                    
                    // 获取插件
                    val loadedPlugin = pluginSystem.getPlugin("test-plugin")
                    assertNotNull(loadedPlugin)
                    assertEquals("test-plugin", loadedPlugin.id)
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                logger.error("Failed to load plugin", err)
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testCreatePluginChain(testContext: VertxTestContext) {
        // 创建插件配置
        val config1 = PluginConfig(
            "test-plugin-1",
            "test",
            JsonObject()
                .put("id", "test-plugin-1")
                .put("type", "test")
                .put("name", "Test Plugin 1")
                .put("description", "A test plugin")
                .put("version", "1.0.0")
                .put("priority", 1)
        )
        
        val config2 = PluginConfig(
            "test-plugin-2",
            "test",
            JsonObject()
                .put("id", "test-plugin-2")
                .put("type", "test")
                .put("name", "Test Plugin 2")
                .put("description", "Another test plugin")
                .put("version", "1.0.0")
                .put("priority", 2)
                .put("parallelExecution", true)
        )
        
        // 加载插件
        pluginSystem.loadPlugin(config1)
            .compose { _ ->
                pluginSystem.loadPlugin(config2)
            }
            .compose { _ ->
                // 创建插件链
                val chain = pluginSystem.createPluginChain(listOf("test-plugin-1", "test-plugin-2"))
                
                // 验证插件链
                testContext.verify {
                    assertNotNull(chain)
                    assertEquals(2, chain.getPlugins().size)
                    assertEquals("test-plugin-1", chain.getPlugins()[0].id)
                    assertEquals("test-plugin-2", chain.getPlugins()[1].id)
                }
                
                Future.succeededFuture<Void>()
            }
            .onSuccess { _ ->
                testContext.completeNow()
            }
            .onFailure { err ->
                logger.error("Test failed", err)
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testParallelExecution(testContext: VertxTestContext) {
        // 创建插件配置
        val config1 = PluginConfig(
            "test-plugin-1",
            "test",
            JsonObject()
                .put("id", "test-plugin-1")
                .put("type", "test")
                .put("name", "Test Plugin 1")
                .put("description", "A test plugin")
                .put("version", "1.0.0")
                .put("priority", 1)
                .put("parallelExecution", true)
                .put("executionDelay", 100) // 100ms delay
        )
        
        val config2 = PluginConfig(
            "test-plugin-2",
            "test",
            JsonObject()
                .put("id", "test-plugin-2")
                .put("type", "test")
                .put("name", "Test Plugin 2")
                .put("description", "Another test plugin")
                .put("version", "1.0.0")
                .put("priority", 1) // Same priority for parallel execution
                .put("parallelExecution", true)
                .put("executionDelay", 100) // 100ms delay
        )
        
        // 加载插件
        pluginSystem.loadPlugin(config1)
            .compose { _ ->
                pluginSystem.loadPlugin(config2)
            }
            .compose { _ ->
                // 创建上下文
                val context = TestRoutingContext(vertx)
                
                // 记录开始时间
                val startTime = System.currentTimeMillis()
                
                // 执行插件链
                pluginSystem.executePluginChain(listOf("test-plugin-1", "test-plugin-2"), context)
                    .map { _ ->
                        // 记录结束时间
                        val endTime = System.currentTimeMillis()
                        val executionTime = endTime - startTime
                        
                        // 验证执行时间
                        // 如果是并行执行，执行时间应该接近100ms而不是200ms
                        testContext.verify {
                            assertTrue(executionTime < 150, "Execution time should be less than 150ms for parallel execution, but was ${executionTime}ms")
                            
                            // 验证两个插件都被执行了
                            assertTrue(context.get<Boolean>("test-plugin-1-executed") == true)
                            assertTrue(context.get<Boolean>("test-plugin-2-executed") == true)
                        }
                        
                        null as Void?
                    }
            }
            .onSuccess { _ ->
                testContext.completeNow()
            }
            .onFailure { err ->
                logger.error("Test failed", err)
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testSequentialExecution(testContext: VertxTestContext) {
        // 创建插件配置
        val config1 = PluginConfig(
            "test-plugin-1",
            "test",
            JsonObject()
                .put("id", "test-plugin-1")
                .put("type", "test")
                .put("name", "Test Plugin 1")
                .put("description", "A test plugin")
                .put("version", "1.0.0")
                .put("priority", 1)
                .put("parallelExecution", false) // Sequential execution
                .put("executionDelay", 100) // 100ms delay
        )
        
        val config2 = PluginConfig(
            "test-plugin-2",
            "test",
            JsonObject()
                .put("id", "test-plugin-2")
                .put("type", "test")
                .put("name", "Test Plugin 2")
                .put("description", "Another test plugin")
                .put("version", "1.0.0")
                .put("priority", 1) // Same priority
                .put("parallelExecution", false) // Sequential execution
                .put("executionDelay", 100) // 100ms delay
        )
        
        // 加载插件
        pluginSystem.loadPlugin(config1)
            .compose { _ ->
                pluginSystem.loadPlugin(config2)
            }
            .compose { _ ->
                // 创建上下文
                val context = TestRoutingContext(vertx)
                
                // 记录开始时间
                val startTime = System.currentTimeMillis()
                
                // 执行插件链
                pluginSystem.executePluginChain(listOf("test-plugin-1", "test-plugin-2"), context)
                    .map { _ ->
                        // 记录结束时间
                        val endTime = System.currentTimeMillis()
                        val executionTime = endTime - startTime
                        
                        // 验证执行时间
                        // 如果是顺序执行，执行时间应该接近200ms
                        testContext.verify {
                            assertTrue(executionTime >= 200, "Execution time should be at least 200ms for sequential execution, but was ${executionTime}ms")
                            
                            // 验证两个插件都被执行了
                            assertTrue(context.get<Boolean>("test-plugin-1-executed") == true)
                            assertTrue(context.get<Boolean>("test-plugin-2-executed") == true)
                            
                            // 验证执行顺序
                            val plugin1Time = context.get<Long>("test-plugin-1-time") ?: 0
                            val plugin2Time = context.get<Long>("test-plugin-2-time") ?: 0
                            assertTrue(plugin1Time < plugin2Time, "Plugin 1 should be executed before Plugin 2")
                        }
                        
                        null as Void?
                    }
            }
            .onSuccess { _ ->
                testContext.completeNow()
            }
            .onFailure { err ->
                logger.error("Test failed", err)
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testPriorityExecution(testContext: VertxTestContext) {
        // 创建插件配置
        val config1 = PluginConfig(
            "test-plugin-1",
            "test",
            JsonObject()
                .put("id", "test-plugin-1")
                .put("type", "test")
                .put("name", "Test Plugin 1")
                .put("description", "A test plugin")
                .put("version", "1.0.0")
                .put("priority", 2) // Higher priority (executed later)
                .put("executionDelay", 100) // 100ms delay
        )
        
        val config2 = PluginConfig(
            "test-plugin-2",
            "test",
            JsonObject()
                .put("id", "test-plugin-2")
                .put("type", "test")
                .put("name", "Test Plugin 2")
                .put("description", "Another test plugin")
                .put("version", "1.0.0")
                .put("priority", 1) // Lower priority (executed first)
                .put("executionDelay", 100) // 100ms delay
        )
        
        // 加载插件
        pluginSystem.loadPlugin(config1)
            .compose { _ ->
                pluginSystem.loadPlugin(config2)
            }
            .compose { _ ->
                // 创建上下文
                val context = TestRoutingContext(vertx)
                
                // 执行插件链
                pluginSystem.executePluginChain(listOf("test-plugin-1", "test-plugin-2"), context)
                    .map { _ ->
                        // 验证执行顺序
                        testContext.verify {
                            // 验证两个插件都被执行了
                            assertTrue(context.get<Boolean>("test-plugin-1-executed") == true)
                            assertTrue(context.get<Boolean>("test-plugin-2-executed") == true)
                            
                            // 验证执行顺序
                            val plugin1Time = context.get<Long>("test-plugin-1-time") ?: 0
                            val plugin2Time = context.get<Long>("test-plugin-2-time") ?: 0
                            assertTrue(plugin2Time < plugin1Time, "Plugin 2 (priority 1) should be executed before Plugin 1 (priority 2)")
                        }
                        
                        null as Void?
                    }
            }
            .onSuccess { _ ->
                testContext.completeNow()
            }
            .onFailure { err ->
                logger.error("Test failed", err)
                testContext.failNow(err)
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
     * 测试插件
     */
    class TestPlugin(
        override val id: String,
        override val type: String,
        override val config: PluginConfig
    ) : AbstractPlugin() {
        private val logger = LoggerFactory.getLogger(TestPlugin::class.java)
        
        override fun initialize(vertx: Vertx): Future<Void> {
            logger.info("Initializing test plugin: {}", id)
            return Future.succeededFuture()
        }
        
        override fun execute(context: RoutingContext): Future<Void> {
            logger.info("Executing test plugin: {}", id)
            
            val promise = Promise.promise<Void>()
            
            // 获取执行延迟
            val executionDelay = config.getLong("executionDelay", 0)
            
            // 模拟执行延迟
            if (executionDelay > 0) {
                vertx.setTimer(executionDelay) {
                    // 标记插件已执行
                    context.put("$id-executed", true)
                    context.put("$id-time", System.currentTimeMillis())
                    
                    promise.complete()
                }
            } else {
                // 标记插件已执行
                context.put("$id-executed", true)
                context.put("$id-time", System.currentTimeMillis())
                
                promise.complete()
            }
            
            return promise.future()
        }
        
        override fun canExecuteInParallel(): Boolean {
            return config.getBoolean("parallelExecution", false)
        }
        
        override fun getPriority(): Int {
            return config.getInteger("priority", 0)
        }
        
        override fun shutdown() {
            logger.info("Shutting down test plugin: {}", id)
        }
    }
    
    /**
     * 测试路由上下文
     */
    class TestRoutingContext(private val vertx: Vertx) : RoutingContext {
        private val data = mutableMapOf<String, Any>()
        
        override fun <T> get(key: String): T? {
            @Suppress("UNCHECKED_CAST")
            return data[key] as? T
        }
        
        override fun <T> put(key: String, obj: T): RoutingContext {
            data[key] = obj as Any
            return this
        }
        
        override fun <T> remove(key: String): T? {
            @Suppress("UNCHECKED_CAST")
            return data.remove(key) as? T
        }
        
        override fun data(): MutableMap<String, Any> {
            return data
        }
        
        override fun vertx(): Vertx {
            return vertx
        }
        
        // 以下是RoutingContext接口的其他方法
        // 为了简化，这里只实现了必要的方法，其他方法返回默认值或抛出异常
        
        override fun request(): io.vertx.core.http.HttpServerRequest {
            throw UnsupportedOperationException("Not implemented")
        }
        
        override fun response(): io.vertx.core.http.HttpServerResponse {
            throw UnsupportedOperationException("Not implemented")
        }
        
        override fun next(): Unit {
            throw UnsupportedOperationException("Not implemented")
        }
        
        override fun fail(statusCode: Int): Unit {
            throw UnsupportedOperationException("Not implemented")
        }
        
        override fun fail(throwable: Throwable): Unit {
            throw UnsupportedOperationException("Not implemented")
        }
        
        override fun fail(statusCode: Int, throwable: Throwable): Unit {
            throw UnsupportedOperationException("Not implemented")
        }
        
        // 其他方法的实现省略
        // 实际使用时需要根据需求完善
    }
}
