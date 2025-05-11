package com.louloulin.apix.cache

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 布隆过滤器管理器测试。
 */
@ExtendWith(VertxExtension::class)
class BloomFilterManagerTest {
    
    private lateinit var vertx: Vertx
    private lateinit var bloomFilterManager: BloomFilterManager
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建布隆过滤器管理器
        bloomFilterManager = BloomFilterManager.getInstance(vertx)
        
        // 初始化布隆过滤器管理器
        val config = JsonObject()
            .put("cache", JsonObject()
                .put("bloomFilter", JsonObject()
                    .put("enabled", true)
                    .put("expectedElements", 1000000)
                    .put("falsePositiveRate", 0.01)
                    .put("syncInterval", 60000)
                )
                .put("cacheNamespace", "test")
            )
        
        bloomFilterManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close().onComplete(testContext.succeedingThenComplete())
    }
    
    @Test
    fun `test add and check bloom filter`(testContext: VertxTestContext) {
        // 添加元素到布隆过滤器
        bloomFilterManager.add("test-key", "test")
            .compose { _ ->
                // 检查元素是否在布隆过滤器中
                val mightContain = bloomFilterManager.mightContain("test-key", "test")
                
                testContext.verify {
                    assertTrue(mightContain)
                    
                    // 检查不存在的元素
                    val mightContain2 = bloomFilterManager.mightContain("non-existent-key", "test")
                    assertEquals(false, mightContain2)
                    
                    // 获取状态
                    val status = bloomFilterManager.getStatus()
                    assertNotNull(status)
                    
                    testContext.completeNow()
                }
                
                Future.succeededFuture<Void>()
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
