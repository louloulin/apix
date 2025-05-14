package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class SmartTTLCacheStrategyTest {
    private val logger = LoggerFactory.getLogger(SmartTTLCacheStrategyTest::class.java)
    private lateinit var strategy: SmartTTLCacheStrategy

    @BeforeEach
    fun setUp() {
        // 创建一个SmartTTLCacheStrategy实例，用于测试
        strategy = SmartTTLCacheStrategy(
            defaultTtl = 3600,
            minTtl = 60,
            maxTtl = 86400,
            learningRate = 0.1,
            modelWeights = mapOf(
                "gpt-4" to 1.2,
                "gpt-3.5-turbo" to 0.8
            ),
            queryTypeWeights = mapOf(
                "chat" to 1.5,
                "completion" to 0.7
            )
        )
    }

    @Test
    fun testGetName() {
        assertEquals("SmartTTL", strategy.getName())
    }

    @Test
    fun testShouldCache() {
        // 默认情况下，所有内容都应该被缓存
        val key = "test-key"
        val value = JsonObject().put("test", "value")
        assertTrue(strategy.shouldCache(key, value))
    }

    @Test
    fun testCalculateTtlWithModelId() {
        // 测试使用模型ID计算TTL
        val key = "test-key"
        val value = JsonObject()
            .put("model", "gpt-4")
            .put("query", "What is the capital of France?")

        // 设置模型特定的TTL
        strategy.setModelTtl("gpt-4", 7200)

        // 计算TTL
        val ttl = strategy.calculateTtl(key, value)
        
        // 应该使用模型特定的TTL
        assertEquals(7200, ttl)
    }

    @Test
    fun testCalculateTtlWithQueryType() {
        // 测试使用查询类型计算TTL
        val key = "test-key"
        val value = JsonObject()
            .put("queryType", "chat")
            .put("query", "What is the capital of France?")

        // 设置查询类型特定的TTL
        strategy.setQueryTypeTtl("chat", 5400)

        // 计算TTL
        val ttl = strategy.calculateTtl(key, value)
        
        // 应该使用查询类型特定的TTL
        assertEquals(5400, ttl)
    }

    @Test
    fun testCalculateTtlWithDefaultValue() {
        // 测试使用默认TTL
        val key = "test-key"
        val value = JsonObject()
            .put("query", "What is the capital of France?")

        // 计算TTL
        val ttl = strategy.calculateTtl(key, value)
        
        // 应该使用默认TTL
        assertEquals(3600, ttl)
    }

    @Test
    fun testCalculateTtlWithAccessStats() {
        // 测试根据访问统计调整TTL
        val key = "test-key"
        val value = JsonObject()
            .put("model", "gpt-4")
            .put("query", "What is the capital of France?")

        // 模拟多次访问
        for (i in 1..10) {
            strategy.onCacheHit(key)
        }

        // 计算TTL
        val ttl = strategy.calculateTtl(key, value)
        
        // 由于访问频率高，TTL应该增加
        logger.info("Adjusted TTL: $ttl")
        assertTrue(ttl >= 3600, "TTL should be increased due to high access frequency")
    }

    @Test
    fun testOnCacheHitAndMiss() {
        val key = "test-key"
        
        // 模拟缓存命中
        strategy.onCacheHit(key)
        
        // 模拟缓存未命中
        strategy.onCacheMiss(key)
        
        // 获取统计信息
        val stats = strategy.getStats()
        
        // 验证统计信息
        assertEquals(2, stats.getLong("totalAccesses"))
        assertEquals(1, stats.getLong("totalHits"))
        assertEquals(0.5, stats.getDouble("hitRate"))
    }

    @Test
    fun testGetStats() {
        // 模拟一些缓存操作
        strategy.onCacheHit("key1")
        strategy.onCacheHit("key2")
        strategy.onCacheMiss("key3")
        
        // 获取统计信息
        val stats = strategy.getStats()
        
        // 验证统计信息
        assertEquals("SmartTTL", stats.getString("name"))
        assertEquals(3, stats.getLong("totalAccesses"))
        assertEquals(2, stats.getLong("totalHits"))
        assertTrue(stats.containsKey("hitRate"))
        assertTrue(stats.containsKey("keysTracked"))
    }

    @Test
    fun testModelAndQueryTypeTtlPriority() {
        // 测试模型ID和查询类型TTL的优先级
        val key = "test-key"
        val value = JsonObject()
            .put("model", "gpt-4")
            .put("queryType", "chat")
            .put("query", "What is the capital of France?")

        // 设置模型特定的TTL
        strategy.setModelTtl("gpt-4", 7200)
        
        // 设置查询类型特定的TTL
        strategy.setQueryTypeTtl("chat", 5400)

        // 计算TTL
        val ttl = strategy.calculateTtl(key, value)
        
        // 模型特定的TTL应该优先于查询类型特定的TTL
        assertEquals(7200, ttl)
    }
}
