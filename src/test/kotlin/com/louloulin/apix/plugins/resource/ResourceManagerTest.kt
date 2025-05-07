package com.louloulin.apix.plugins.resource

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 资源管理器测试
 */
@RunWith(VertxUnitRunner::class)
class ResourceManagerTest {
    private lateinit var vertx: Vertx
    private lateinit var resourceManager: ResourceManager
    
    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        resourceManager = ResourceManager.getInstance(vertx)
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        resourceManager.close().onComplete { ar ->
            if (ar.succeeded()) {
                vertx.close(testContext.asyncAssertSuccess())
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    @Test
    fun testWebClientSharing(testContext: TestContext) {
        val async = testContext.async()
        
        // 创建两个 WebClient 实例，应该是同一个实例
        val client1 = resourceManager.getOrCreateWebClient("test-client")
        val client2 = resourceManager.getOrCreateWebClient("test-client")
        
        // 验证是同一个实例
        testContext.assertEquals(client1, client2)
        
        // 验证统计信息
        val stats = resourceManager.getStats()
        val resourcePoolStats = stats.getJsonObject("resourcePool")
        testContext.assertEquals(1, resourcePoolStats.getInteger("webClients"))
        
        async.complete()
    }
    
    @Test
    fun testHttpClientSharing(testContext: TestContext) {
        val async = testContext.async()
        
        // 创建两个 HTTP 客户端实例，应该是同一个实例
        val client1 = resourceManager.getOrCreateHttpClient("test-http-client")
        val client2 = resourceManager.getOrCreateHttpClient("test-http-client")
        
        // 验证是同一个实例
        testContext.assertEquals(client1, client2)
        
        // 记录连接使用
        resourceManager.recordConnectionUse("test-http-client")
        resourceManager.recordConnectionUse("test-http-client")
        
        // 验证统计信息
        val stats = resourceManager.getStats()
        val connectionPoolStats = stats.getJsonObject("connectionPool")
        val clientStats = connectionPoolStats.getJsonObject("clients")
        val clientStat = clientStats.getJsonObject("test-http-client")
        testContext.assertEquals(2, clientStat.getInteger("connections"))
        testContext.assertEquals(2, clientStat.getInteger("requests"))
        
        async.complete()
    }
    
    @Test
    fun testSharedDataManager(testContext: TestContext) {
        val async = testContext.async()
        
        // 在本地映射中设置值
        resourceManager.putInLocalMap("test-map", "test-key", "test-value")
        
        // 获取值
        val value = resourceManager.getFromLocalMap<String>("test-map", "test-key")
        testContext.assertEquals("test-value", value)
        
        // 在异步映射中设置值
        val testData = JsonObject().put("name", "test").put("value", 123)
        resourceManager.putInAsyncMap("test-async-map", "test-key", testData).compose { _ ->
            // 获取值
            resourceManager.getFromAsyncMap("test-async-map", "test-key")
        }.onComplete { ar ->
            if (ar.succeeded()) {
                val result = ar.result()
                testContext.assertNotNull(result)
                testContext.assertEquals("test", result?.getString("name"))
                testContext.assertEquals(123, result?.getInteger("value"))
                
                // 验证统计信息
                val stats = resourceManager.getStats()
                val dataManagerStats = stats.getJsonObject("dataManager")
                testContext.assertEquals(1, dataManagerStats.getInteger("asyncMaps"))
                testContext.assertEquals(1, dataManagerStats.getInteger("localMaps"))
                
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
}
