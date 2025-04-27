package com.louloulin.apix.core

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import io.vertx.core.http.HttpServerResponse

@ExtendWith(VertxExtension::class)
@Disabled("Mockito issues with Vert.x")
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
        // Create mock plugins
        val plugin1 = createMockPlugin("plugin1")
        val plugin2 = createMockPlugin("plugin2")
        val plugin3 = createMockPlugin("plugin3")
        
        // Create plugin chain
        val pluginChain = PluginChain(listOf(plugin1, plugin2, plugin3))
        
        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val response = mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)
        `when`(response.ended()).thenReturn(false)
        
        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that all plugins were executed in order
                val inOrder = inOrder(plugin1, plugin2, plugin3)
                inOrder.verify(plugin1).execute(routingContext)
                inOrder.verify(plugin2).execute(routingContext)
                inOrder.verify(plugin3).execute(routingContext)
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
    
    @Test
    fun `should stop execution if a plugin ends the response`(testContext: VertxTestContext) {
        // Create mock plugins
        val plugin1 = createMockPlugin("plugin1")
        val plugin2 = createMockPlugin("plugin2", endsResponse = true)
        val plugin3 = createMockPlugin("plugin3")
        
        // Create plugin chain
        val pluginChain = PluginChain(listOf(plugin1, plugin2, plugin3))
        
        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val response = mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)
        
        // First call to ended() returns false, second call returns true
        `when`(response.ended())
            .thenReturn(false) // For plugin1
            .thenReturn(true)  // For plugin2
        
        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                // Verify that only the first two plugins were executed
                verify(plugin1).execute(routingContext)
                verify(plugin2).execute(routingContext)
                verify(plugin3, never()).execute(routingContext)
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
    
    @Test
    fun `should handle plugin failure`(testContext: VertxTestContext) {
        // Create mock plugins
        val plugin1 = createMockPlugin("plugin1")
        val plugin2 = createFailingPlugin("plugin2")
        val plugin3 = createMockPlugin("plugin3")
        
        // Create plugin chain
        val pluginChain = PluginChain(listOf(plugin1, plugin2, plugin3))
        
        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)
        val response = mock(HttpServerResponse::class.java)
        `when`(routingContext.response()).thenReturn(response)
        `when`(response.ended()).thenReturn(false)
        
        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.failed()) {
                // Verify that only the first two plugins were executed
                verify(plugin1).execute(routingContext)
                verify(plugin2).execute(routingContext)
                verify(plugin3, never()).execute(routingContext)
                verify(routingContext).fail(any<Throwable>())
                testContext.completeNow()
            } else {
                testContext.failNow(RuntimeException("Expected chain execution to fail"))
            }
        }
    }
    
    @Test
    fun `should handle empty plugin chain`(testContext: VertxTestContext) {
        // Create empty plugin chain
        val pluginChain = PluginChain(emptyList())
        
        // Create mock routing context
        val routingContext = mock(RoutingContext::class.java)
        
        // Execute plugin chain
        pluginChain.execute(routingContext).onComplete { result ->
            if (result.succeeded()) {
                testContext.completeNow()
            } else {
                testContext.failNow(result.cause())
            }
        }
    }
    
    /**
     * Creates a mock plugin that succeeds.
     */
    private fun createMockPlugin(id: String, endsResponse: Boolean = false): Plugin {
        val plugin = mock(Plugin::class.java)
        
        `when`(plugin.id).thenReturn(id)
        `when`(plugin.type).thenReturn("mock")
        
        // Set up the execute method to return a succeeded future
        `when`(plugin.execute(any(RoutingContext::class.java))).thenAnswer { invocation ->
            val promise = Promise.promise<Void>()
            promise.complete()
            promise.future()
        }
        
        return plugin
    }
    
    /**
     * Creates a mock plugin that fails.
     */
    private fun createFailingPlugin(id: String): Plugin {
        val plugin = mock(Plugin::class.java)
        
        `when`(plugin.id).thenReturn(id)
        `when`(plugin.type).thenReturn("mock")
        
        // Set up the execute method to return a failed future
        `when`(plugin.execute(any(RoutingContext::class.java))).thenAnswer { invocation ->
            val promise = Promise.promise<Void>()
            promise.fail(RuntimeException("Plugin execution failed"))
            promise.future()
        }
        
        return plugin
    }
}
