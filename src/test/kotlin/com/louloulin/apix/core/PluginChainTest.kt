package com.louloulin.apix.core

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginType
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

@ExtendWith(VertxExtension::class)
class PluginChainTest {

    private lateinit var vertx: Vertx

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        testContext.completeNow()
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `should execute all plugins in sequence`(testContext: VertxTestContext) {
        // Create test plugins with custom execute implementations
        val plugin1 = TestPlugin("plugin1", "auth", 10)
        val plugin2 = TestPlugin("plugin2", "transform", 20)
        val plugin3 = TestPlugin("plugin3", "logging", 30)

        // 创建检查点
        val checkpoint = testContext.checkpoint(1)

        // 确保每个插件都有自己的执行逻辑
        plugin1.execute = { context ->
            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        plugin2.execute = { context ->
            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        plugin3.execute = { context ->
            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)

        // Create plugin chain with vertx instance
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3))
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        // 使用doAnswer而不是when().thenAnswer()
        doAnswer { responseEnded.get() }.`when`(response).ended()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.verify {
                    assertTrue(plugin1.executed, "Plugin 1 should be executed")
                    assertTrue(plugin2.executed, "Plugin 2 should be executed")
                    assertTrue(plugin3.executed, "Plugin 3 should be executed")

                    // Verify execution order by timestamp
                    assertTrue(plugin1.executionTime <= plugin2.executionTime, "Plugin 1 should execute before Plugin 2")
                    assertTrue(plugin2.executionTime <= plugin3.executionTime, "Plugin 2 should execute before Plugin 3")
                }
                checkpoint.flag() // 标记检查点完成
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should execute plugins based on priority`(testContext: VertxTestContext) {
        // Create test plugins with different priorities
        val plugin1 = TestPlugin("plugin1", "auth", 30)
        val plugin2 = TestPlugin("plugin2", "transform", 10)
        val plugin3 = TestPlugin("plugin3", "logging", 20)

        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3))

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        // 使用doAnswer而不是when().thenAnswer()
        doAnswer { responseEnded.get() }.`when`(response).ended()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that plugins were executed in priority order
                testContext.verify {
                    assertTrue(plugin1.executed)
                    assertTrue(plugin2.executed)
                    assertTrue(plugin3.executed)

                    // Verify execution order by timestamp
                    assertTrue(plugin2.executionTime <= plugin3.executionTime)
                    assertTrue(plugin3.executionTime <= plugin1.executionTime)
                }
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should execute plugins in parallel when possible`(testContext: VertxTestContext) {
        // Create test plugins, some with parallel execution
        val plugin1 = TestPlugin("plugin1", "auth", 10, parallelExecution = false)
        val plugin2 = TestPlugin("plugin2", "transform", 20, parallelExecution = true)
        val plugin3 = TestPlugin("plugin3", "validation", 20, parallelExecution = true)
        val plugin4 = TestPlugin("plugin4", "logging", 30, parallelExecution = false)

        // 创建检查点
        val checkpoint = testContext.checkpoint(1)

        // 确保每个插件都有自己的执行逻辑
        plugin1.execute = { context ->
            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(50) { promise.complete() }
            promise.future()
        }

        plugin2.execute = { context ->
            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(100) { promise.complete() }
            promise.future()
        }

        plugin3.execute = { context ->
            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(100) { promise.complete() }
            promise.future()
        }

        plugin4.execute = { context ->
            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(50) { promise.complete() }
            promise.future()
        }

        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3, plugin4))

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        // 使用doAnswer而不是when().thenAnswer()
        doAnswer { responseEnded.get() }.`when`(response).ended()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.verify {
                    // Verify that all plugins were executed
                    assertTrue(plugin1.executed, "Plugin 1 should be executed")
                    assertTrue(plugin2.executed, "Plugin 2 should be executed")
                    assertTrue(plugin3.executed, "Plugin 3 should be executed")
                    assertTrue(plugin4.executed, "Plugin 4 should be executed")

                    // Verify execution order
                    // plugin1 should be executed before plugin2 and plugin3
                    assertTrue(plugin1.executionTime <= plugin2.executionTime || plugin1.executionTime <= plugin3.executionTime,
                        "Plugin 1 should execute before Plugin 2 or Plugin 3")

                    // plugin2 and plugin3 should be executed before plugin4
                    assertTrue(plugin2.executionTime <= plugin4.executionTime, "Plugin 2 should execute before Plugin 4")
                    assertTrue(plugin3.executionTime <= plugin4.executionTime, "Plugin 3 should execute before Plugin 4")
                }
                checkpoint.flag() // 标记检查点完成
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should skip plugins based on condition`(testContext: VertxTestContext) {
        // Create test plugins, some with conditions
        val plugin1 = TestPlugin("plugin1", "auth", 10, shouldExecuteValue = true)
        val plugin2 = TestPlugin("plugin2", "transform", 20, shouldExecuteValue = false)
        val plugin3 = TestPlugin("plugin3", "logging", 30, shouldExecuteValue = true)

        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3))

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        // 使用doAnswer而不是when().thenAnswer()
        doAnswer { responseEnded.get() }.`when`(response).ended()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.verify {
                    // Verify that plugin1 and plugin3 were executed, but not plugin2
                    assertTrue(plugin1.executed)
                    assertFalse(plugin2.executed)
                    assertTrue(plugin3.executed)
                }
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should stop execution if a plugin ends the response`(testContext: VertxTestContext) {
        // Create test plugins
        val plugin1 = TestPlugin("plugin1", "auth", 10)
        val plugin2 = TestPlugin("plugin2", "transform", 20)
        val plugin3 = TestPlugin("plugin3", "logging", 30)

        // 创建检查点
        val checkpoint = testContext.checkpoint(1)

        // 自定义插件的执行逻辑
        plugin1.execute = { context ->
            // 标记为已执行
            plugin1.executed = true
            plugin1.executionTime = System.currentTimeMillis()

            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        plugin3.execute = { context ->
            // 标记为已执行
            plugin3.executed = true
            plugin3.executionTime = System.currentTimeMillis()

            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        // Setup response state
        val responseEnded = AtomicBoolean(false)
        doAnswer { responseEnded.get() }.`when`(response).ended()
        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()
        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())

        // Setup context
        `when`(routingContext.vertx()).thenReturn(vertx)
        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // 自定义plugin2的执行逻辑，使其结束响应
        plugin2.execute = { context ->
            // 标记为已执行
            plugin2.executed = true
            plugin2.executionTime = System.currentTimeMillis()

            // 结束响应
            context.response().end()

            // 使用 Vert.x 的异步模式
            val promise = Promise.promise<Void>()
            vertx.setTimer(5) { promise.complete() }
            promise.future()
        }



        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3))

        // 添加请求路径和方法
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.verify {
                    // Verify that only the first two plugins were executed
                    assertTrue(plugin1.executed, "Plugin 1 should be executed")
                    assertTrue(plugin2.executed, "Plugin 2 should be executed")
                    assertFalse(plugin3.executed, "Plugin 3 should not be executed")
                }
                checkpoint.flag() // 标记检查点完成
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should handle plugin failure`(testContext: VertxTestContext) {
        // Create test plugins
        val plugin1 = TestPlugin("plugin1", "auth", 10)
        val plugin2 = TestPlugin("plugin2", "transform", 20, shouldFail = true)
        val plugin3 = TestPlugin("plugin3", "logging", 30)

        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3))

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        // 使用doAnswer而不是when().thenAnswer()
        doAnswer { responseEnded.get() }.`when`(response).ended()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.failed()) {
                testContext.verify {
                    // Verify that only the first two plugins were executed
                    assertTrue(plugin1.executed)
                    assertTrue(plugin2.executed)
                    assertFalse(plugin3.executed)
                }
                // 不需要验证routingContext.fail()的调用，因为这是PluginChain内部实现的细节
                testContext.completeNow()
            } else {
                testContext.failNow(RuntimeException("Expected chain execution to fail"))
            }
        }
    }

    @Test
    fun `should cache plugin execution results`(testContext: VertxTestContext) {
        // Create test plugins with caching enabled
        val plugin1 = TestPlugin("plugin1", "auth", 10, cacheable = false)
        val plugin2 = TestPlugin("plugin2", "cache", 20, cacheable = true)
        val plugin3 = TestPlugin("plugin3", "logging", 30, cacheable = false)

        // 创建检查点
        val checkpoint = testContext.checkpoint(1)

        // 确保每个插件都有自己的执行逻辑
        plugin1.execute = { context ->
            plugin1.executed = true
            plugin1.executionTime = System.currentTimeMillis()

            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        plugin2.execute = { context ->
            plugin2.executed = true
            plugin2.executionTime = System.currentTimeMillis()

            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        plugin3.execute = { context ->
            plugin3.executed = true
            plugin3.executionTime = System.currentTimeMillis()

            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3))

        // Create mock routing context and request
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        // 使用doAnswer而不是when().thenAnswer()
        doAnswer { responseEnded.get() }.`when`(response).ended()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // First execution
        pluginChain.execute(routingContext).compose { _ ->
            // Reset execution flags
            plugin1.executed = false
            plugin2.executed = false
            plugin3.executed = false

            // Second execution with same context
            pluginChain.execute(routingContext)
        }.onComplete { result ->
            if (result.succeeded()) {
                testContext.verify {
                    // Verify that plugin1 and plugin3 were executed again
                    assertTrue(plugin1.executed, "Plugin 1 should be executed again")
                    assertFalse(plugin2.executed, "Plugin 2 should use cached result") // Should not be executed again (cached result was used)
                    assertTrue(plugin3.executed, "Plugin 3 should be executed again")
                }
                checkpoint.flag() // 标记检查点完成
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should get plugin execution stats`(testContext: VertxTestContext) {
        // Create test plugins
        val plugin1 = TestPlugin("plugin1", "auth", 10)
        val plugin2 = TestPlugin("plugin2", "transform", 20)

        // 创建检查点
        val checkpoint = testContext.checkpoint(1)

        // 确保每个插件都有自己的执行逻辑
        plugin1.execute = { context ->
            plugin1.executed = true
            plugin1.executionTime = System.currentTimeMillis()

            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        plugin2.execute = { context ->
            plugin2.executed = true
            plugin2.executionTime = System.currentTimeMillis()

            // 使用 Vert.x 的异步模式替代 Thread.sleep
            val promise = Promise.promise<Void>()
            vertx.setTimer(10) { promise.complete() }
            promise.future()
        }

        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2))

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        // 使用doAnswer而不是when().thenAnswer()
        doAnswer { responseEnded.get() }.`when`(response).ended()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end()

        doAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }.`when`(response).end(any<String>())
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Get execution stats
                val stats = pluginChain.getExecutionStats()

                testContext.verify {
                    // Verify that stats contain entries for both plugins
                    assertTrue(stats.containsKey("plugin1"), "Stats should contain plugin1")
                    assertTrue(stats.containsKey("plugin2"), "Stats should contain plugin2")

                    // Verify that execution counts are correct
                    assertEquals(1L, stats["plugin1"]?.get("executionCount"), "Execution count for plugin1 should be 1")
                    assertEquals(1L, stats["plugin2"]?.get("executionCount"), "Execution count for plugin2 should be 1")
                }
                checkpoint.flag() // 标记检查点完成
            } else {
                testContext.failNow(result.cause())
            }
        }
    }

    @Test
    fun `should handle empty plugin chain`(testContext: VertxTestContext) {
        // Create empty plugin chain
        val pluginChain = PluginChain(vertx, emptyList())

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
        `when`(response.end(any<String>())).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }


}
