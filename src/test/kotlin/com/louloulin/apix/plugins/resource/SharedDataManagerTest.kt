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
 * 共享数据管理器测试
 */
@RunWith(VertxUnitRunner::class)
class SharedDataManagerTest {
    private lateinit var vertx: Vertx
    private lateinit var sharedDataManager: SharedDataManager
    
    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        sharedDataManager = SharedDataManager.getInstance(vertx)
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        sharedDataManager.close().onComplete { ar ->
            if (ar.succeeded()) {
                vertx.close(testContext.asyncAssertSuccess())
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    @Test
    fun testLocalMap(testContext: TestContext) {
        val async = testContext.async()
        
        // 在本地映射中设置值
        sharedDataManager.putInLocalMap("test-map", "test-key", "test-value")
        
        // 获取值
        val value = sharedDataManager.getFromLocalMap<String>("test-map", "test-key")
        testContext.assertEquals("test-value", value)
        
        // 删除值
        val removedValue = sharedDataManager.removeFromLocalMap<String>("test-map", "test-key")
        testContext.assertEquals("test-value", removedValue)
        
        // 验证值已被删除
        val valueAfterRemoval = sharedDataManager.getFromLocalMap<String>("test-map", "test-key")
        testContext.assertNull(valueAfterRemoval)
        
        async.complete()
    }
    
    @Test
    fun testAsyncMap(testContext: TestContext) {
        val async = testContext.async()
        
        // 在异步映射中设置值
        val testData = JsonObject().put("name", "test").put("value", 123)
        sharedDataManager.putInAsyncMap("test-async-map", "test-key", testData).compose { _ ->
            // 获取值
            sharedDataManager.getFromAsyncMap("test-async-map", "test-key")
        }.compose { result ->
            testContext.assertNotNull(result)
            testContext.assertEquals("test", result?.getString("name"))
            testContext.assertEquals(123, result?.getInteger("value"))
            
            // 删除值
            sharedDataManager.removeFromAsyncMap("test-async-map", "test-key")
        }.compose { _ ->
            // 验证值已被删除
            sharedDataManager.getFromAsyncMap("test-async-map", "test-key")
        }.onComplete { ar ->
            if (ar.succeeded()) {
                testContext.assertNull(ar.result())
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    @Test
    fun testCounter(testContext: TestContext) {
        val async = testContext.async()
        
        // 获取计数器
        sharedDataManager.getCounter("test-counter").compose { counter ->
            // 增加计数器
            counter.incrementAndGet()
        }.compose { value ->
            // 验证计数器值
            testContext.assertEquals(1L, value)
            
            // 获取同一个计数器
            sharedDataManager.getCounter("test-counter")
        }.compose { counter ->
            // 获取计数器值
            counter.get()
        }.onComplete { ar ->
            if (ar.succeeded()) {
                // 验证计数器值
                testContext.assertEquals(1L, ar.result())
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    @Test
    fun testLock(testContext: TestContext) {
        val async = testContext.async()
        
        // 获取锁
        sharedDataManager.getLock("test-lock").compose { lock ->
            // 验证锁已获取
            testContext.assertNotNull(lock)
            
            // 释放锁
            lock.release()
            
            // 获取带超时的锁
            sharedDataManager.getLockWithTimeout("test-lock", 1000)
        }.onComplete { ar ->
            if (ar.succeeded()) {
                // 验证锁已获取
                testContext.assertNotNull(ar.result())
                
                // 释放锁
                ar.result().release()
                
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
}
