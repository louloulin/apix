package com.louloulin.apix.core.connection

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 连接预热器测试类
 */
@ExtendWith(VertxExtension::class)
class ConnectionWarmerTest {
    private lateinit var vertx: Vertx
    private lateinit var connectionWarmer: ConnectionWarmer
    private lateinit var connectionWarmerManager: ConnectionWarmerManager
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        connectionWarmer = ConnectionWarmer.getInstance(vertx)
        connectionWarmerManager = ConnectionWarmerManager.getInstance(vertx)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        connectionWarmer.cleanup()
            .compose { vertx.close() }
            .onComplete(testContext.succeedingThenComplete())
    }
    
    /**
     * 测试预热单个连接
     */
    @Test
    fun testWarmSingleConnection(testContext: VertxTestContext) {
        // 使用一个公共的HTTP服务进行测试
        val host = "httpbin.org"
        val port = 443
        val count = 5
        val ssl = true
        
        connectionWarmer.warmConnections(host, port, count, ssl)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val result = ar.result()
                    assertEquals(host, result.getString("host"))
                    assertEquals(port, result.getInteger("port"))
                    assertEquals(count, result.getInteger("requested"))
                    
                    // 获取统计信息
                    val stats = connectionWarmer.getStats()
                    val endpointsStats = stats.getJsonObject("endpoints")
                    val endpointStats = endpointsStats.getJsonObject("$host:$port")
                    
                    assertTrue(endpointStats != null)
                    assertTrue(endpointStats.getInteger("warmed") > 0)
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试批量预热连接
     */
    @Test
    fun testWarmConnectionsBatch(testContext: VertxTestContext) {
        val endpoints = JsonArray()
            .add(JsonObject()
                .put("host", "httpbin.org")
                .put("port", 443)
                .put("count", 3)
                .put("ssl", true)
            )
            .add(JsonObject()
                .put("host", "example.com")
                .put("port", 443)
                .put("count", 2)
                .put("ssl", true)
            )
        
        connectionWarmer.warmConnectionsBatch(endpoints)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val result = ar.result()
                    assertTrue(result.getBoolean("success"))
                    
                    val results = result.getJsonArray("results")
                    assertEquals(2, results.size())
                    
                    // 获取统计信息
                    val stats = connectionWarmer.getStats()
                    val endpointsStats = stats.getJsonObject("endpoints")
                    
                    assertTrue(endpointsStats.containsKey("httpbin.org:443"))
                    assertTrue(endpointsStats.containsKey("example.com:443"))
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试获取和返回预热连接
     */
    @Test
    fun testGetAndReturnWarmedConnection(testContext: VertxTestContext) {
        val host = "httpbin.org"
        val port = 443
        val count = 3
        val ssl = true
        
        connectionWarmer.warmConnections(host, port, count, ssl)
            .compose { result ->
                // 获取预热连接
                connectionWarmer.getWarmedConnection(host, port)
            }
            .compose { client ->
                testContext.verify {
                    assertTrue(client != null)
                }
                
                // 返回预热连接
                connectionWarmer.returnWarmedConnection(host, port, client!!)
                
                // 获取统计信息
                Future.succeededFuture(connectionWarmer.getStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val stats = ar.result()
                    val endpointsStats = stats.getJsonObject("endpoints")
                    val endpointStats = endpointsStats.getJsonObject("$host:$port")
                    
                    assertTrue(endpointStats != null)
                    assertEquals(count, endpointStats.getInteger("warmed"))
                    assertEquals(1, endpointStats.getInteger("used"))
                    assertEquals(count, endpointStats.getInteger("available"))
                    
                    testContext.completeNow()
                }
            }
    }
    
    /**
     * 测试连接预热管理器
     */
    @Test
    fun testConnectionWarmerManager(testContext: VertxTestContext) {
        val host = "httpbin.org"
        val port = 443
        val count = 3
        val ssl = true
        
        connectionWarmerManager.warmConnections(host, port, count, ssl)
            .compose { result ->
                // 获取预热连接
                connectionWarmerManager.getWarmedConnection(host, port)
            }
            .compose { client ->
                testContext.verify {
                    assertTrue(client != null)
                }
                
                // 返回预热连接
                connectionWarmerManager.returnWarmedConnection(host, port, client!!)
                
                // 获取统计信息
                Future.succeededFuture(connectionWarmerManager.getStats())
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val stats = ar.result()
                    val endpointsStats = stats.getJsonObject("endpoints")
                    val endpointStats = endpointsStats.getJsonObject("$host:$port")
                    
                    assertTrue(endpointStats != null)
                    assertEquals(count, endpointStats.getInteger("warmed"))
                    assertEquals(1, endpointStats.getInteger("used"))
                    assertEquals(count, endpointStats.getInteger("available"))
                    
                    testContext.completeNow()
                }
            }
    }
}
