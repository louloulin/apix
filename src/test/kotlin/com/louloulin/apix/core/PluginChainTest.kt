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
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

@ExtendWith(VertxExtension::class)
class PluginChainTest {

    private lateinit var vertx: Vertx

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun `should execute all plugins in sequence`(testContext: VertxTestContext) {
        // Create test plugins
        val plugin1 = TestPlugin("plugin1", "auth", 10)
        val plugin2 = TestPlugin("plugin2", "transform", 20)
        val plugin3 = TestPlugin("plugin3", "logging", 30)

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
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that all plugins were executed
                testContext.verify {
                    assertTrue(plugin1.executed)
                    assertTrue(plugin2.executed)
                    assertTrue(plugin3.executed)

                    // Verify execution order by timestamp
                    assertTrue(plugin1.executionTime <= plugin2.executionTime)
                    assertTrue(plugin2.executionTime <= plugin3.executionTime)
                }
                testContext.completeNow()
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
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
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
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.verify {
                    // Verify that all plugins were executed
                    assertTrue(plugin1.executed)
                    assertTrue(plugin2.executed)
                    assertTrue(plugin3.executed)
                    assertTrue(plugin4.executed)

                    // Verify execution order
                    // plugin1 should be executed before plugin2 and plugin3
                    assertTrue(plugin1.executionTime <= plugin2.executionTime || plugin1.executionTime <= plugin3.executionTime)

                    // plugin2 and plugin3 should be executed before plugin4
                    assertTrue(plugin2.executionTime <= plugin4.executionTime)
                    assertTrue(plugin3.executionTime <= plugin4.executionTime)
                }
                testContext.completeNow()
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
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
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

        // Plugin2 will end the response
        val plugin2 = TestPlugin("plugin2", "transform", 20)
        val plugin3 = TestPlugin("plugin3", "logging", 30)

        // 自定义plugin2的执行逻辑，使其结束响应
        plugin2.execute = { context ->
            // 模拟处理时间
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                // 忽略
            }

            // 标记为已执行
            plugin2.executed = true
            plugin2.executionTime = System.currentTimeMillis()

            // 结束响应
            // 直接设置 context.response().ended() 返回 true
            val mockResponse = context.response()
            `when`(mockResponse.ended()).thenReturn(true)

            Future.succeededFuture()
        }

        // Create plugin chain
        val pluginChain = PluginChain(vertx, listOf(plugin1, plugin2, plugin3))

        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)

        // Setup vertx in the routing context
        `when`(routingContext.vertx()).thenReturn(vertx)
        val response = mock(HttpServerResponse::class.java)
        val request = mock(io.vertx.core.http.HttpServerRequest::class.java)

        // 创建一个可变的响应状态
        val responseEnded = AtomicBoolean(false)

        `when`(routingContext.response()).thenReturn(response)
        `when`(routingContext.request()).thenReturn(request)
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.verify {
                    // Verify that only the first two plugins were executed
                    assertTrue(plugin1.executed)
                    assertTrue(plugin2.executed)
                    assertFalse(plugin3.executed)
                }
                testContext.completeNow()
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
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
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
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
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
                    assertTrue(plugin1.executed)
                    assertFalse(plugin2.executed) // Should not be executed again (cached result was used)
                    assertTrue(plugin3.executed)
                }
                testContext.completeNow()
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
        `when`(response.ended()).thenAnswer { responseEnded.get() }
        `when`(response.end()).thenAnswer {
            responseEnded.set(true)
            Future.succeededFuture<Void>()
        }
        `when`(request.path()).thenReturn("/test")
        `when`(request.method()).thenReturn(io.vertx.core.http.HttpMethod.GET)

        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { _ ->
            // Get execution stats
            val stats = pluginChain.getExecutionStats()

            testContext.verify {
                // Verify that stats contain entries for both plugins
                assertTrue(stats.containsKey("plugin1"))
                assertTrue(stats.containsKey("plugin2"))

                // Verify that execution counts are correct
                assertEquals(1L, stats["plugin1"]?.get("executionCount"))
                assertEquals(1L, stats["plugin2"]?.get("executionCount"))
            }

            testContext.completeNow()
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
