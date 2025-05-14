package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class EnhancedCacheStrategyTest {
    private val logger = LoggerFactory.getLogger(EnhancedCacheStrategyTest::class.java)
    private lateinit var lruStrategy: LRUCacheStrategy
    private lateinit var lfuStrategy: LFUCacheStrategy

    @BeforeEach
    fun setUp() {
        // 创建一个小容量的LRU缓存策略，用于测试
        lruStrategy = LRUCacheStrategy(
            maxSize = 10,
            defaultTtl = 3600
        )
        
        // 创建一个小容量的LFU缓存策略，用于测试
        lfuStrategy = LFUCacheStrategy(
            maxSize = 10,
            defaultTtl = 3600
        )
    }

    @Test
    fun testLRUEviction() {
        // 添加11个缓存项，触发淘汰
        for (i in 1..11) {
            val key = "key$i"
            val value = JsonObject().put("value", i)
            
            // 模拟缓存设置
            lruStrategy.onCacheSet(key, value)
            
            // 模拟访问，使得访问频率不同
            for (j in 1..i) {
                lruStrategy.onCacheHit(key)
            }
        }
        
        // 验证shouldCache方法，它会触发淘汰
        val result = lruStrategy.shouldCache("newKey", JsonObject())
        
        // 应该返回true，表示可以缓存
        assertTrue(result)
        
        // 获取统计信息
        val stats = lruStrategy.getStats()
        logger.info("LRU策略统计: $stats")
        
        // 验证当前缓存大小不超过最大容量
        assertTrue(stats.getInteger("currentSize") <= 10)
    }

    @Test
    fun testLFUEviction() {
        // 添加11个缓存项，触发淘汰
        for (i in 1..11) {
            val key = "key$i"
            val value = JsonObject().put("value", i)
            
            // 模拟缓存设置
            lfuStrategy.onCacheSet(key, value)
            
            // 模拟访问，使得访问频率不同
            // 让key1的访问频率最低，应该被淘汰
            if (i > 1) {
                for (j in 1..i) {
                    lfuStrategy.onCacheHit(key)
                }
            }
        }
        
        // 验证shouldCache方法，它会触发淘汰
        val result = lfuStrategy.shouldCache("newKey", JsonObject())
        
        // 应该返回true，表示可以缓存
        assertTrue(result)
        
        // 获取统计信息
        val stats = lfuStrategy.getStats()
        logger.info("LFU策略统计: $stats")
        
        // 验证当前缓存大小不超过最大容量
        assertTrue(stats.getInteger("currentSize") <= 10)
    }

    @Test
    fun testLRUWithModelSpecificTTL() {
        // 设置模型特定的TTL
        val config = JsonObject()
            .put("modelTtl", JsonObject()
                .put("gpt-4", 7200)
                .put("gpt-3.5-turbo", 1800)
            )
        
        lruStrategy.initialize(config)
        
        // 测试不同模型的TTL
        val value1 = JsonObject().put("model", "gpt-4")
        val value2 = JsonObject().put("model", "gpt-3.5-turbo")
        val value3 = JsonObject().put("model", "unknown-model")
        
        assertEquals(7200, lruStrategy.calculateTtl("key1", value1))
        assertEquals(1800, lruStrategy.calculateTtl("key2", value2))
        assertEquals(3600, lruStrategy.calculateTtl("key3", value3)) // 默认TTL
    }

    @Test
    fun testLFUWithQueryTypeSpecificTTL() {
        // 设置查询类型特定的TTL
        val config = JsonObject()
            .put("queryTypeTtl", JsonObject()
                .put("chat", 5400)
                .put("completion", 2700)
            )
        
        lfuStrategy.initialize(config)
        
        // 测试不同查询类型的TTL
        val value1 = JsonObject().put("queryType", "chat")
        val value2 = JsonObject().put("queryType", "completion")
        val value3 = JsonObject().put("queryType", "unknown-type")
        
        assertEquals(5400, lfuStrategy.calculateTtl("key1", value1))
        assertEquals(2700, lfuStrategy.calculateTtl("key2", value2))
        assertEquals(3600, lfuStrategy.calculateTtl("key3", value3)) // 默认TTL
    }

    @Test
    fun testLRUCacheHitAndMiss() {
        // 测试缓存命中和未命中的处理
        val key = "test-key"
        val value = JsonObject().put("test", "value")
        
        // 模拟缓存设置
        lruStrategy.onCacheSet(key, value)
        
        // 模拟多次缓存命中
        for (i in 1..5) {
            lruStrategy.onCacheHit(key)
        }
        
        // 模拟缓存移除
        lruStrategy.onCacheRemove(key)
        
        // 获取统计信息
        val stats = lruStrategy.getStats()
        
        // 验证当前缓存大小为0
        assertEquals(0, stats.getInteger("currentSize"))
    }

    @Test
    fun testLFUCacheHitAndMiss() {
        // 测试缓存命中和未命中的处理
        val key = "test-key"
        val value = JsonObject().put("test", "value")
        
        // 模拟缓存设置
        lfuStrategy.onCacheSet(key, value)
        
        // 模拟多次缓存命中
        for (i in 1..5) {
            lfuStrategy.onCacheHit(key)
        }
        
        // 模拟缓存移除
        lfuStrategy.onCacheRemove(key)
        
        // 获取统计信息
        val stats = lfuStrategy.getStats()
        
        // 验证当前缓存大小为0
        assertEquals(0, stats.getInteger("currentSize"))
    }
}
