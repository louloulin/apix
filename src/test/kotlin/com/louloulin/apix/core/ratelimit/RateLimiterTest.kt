package com.louloulin.apix.core.ratelimit

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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 限流器测试
 */
@ExtendWith(VertxExtension::class)
class RateLimiterTest {
    private lateinit var vertx: Vertx
    private lateinit var rateLimiter: RateLimiter
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        rateLimiter = RateLimiter(vertx)
        
        // 初始化限流器
        val config = JsonObject()
            .put("cleanupInterval", 60000L)
        
        rateLimiter.initialize(config)
        testContext.completeNow()
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `test token bucket limiter`(testContext: VertxTestContext) {
        val key = "test-token-bucket"
        val config = JsonObject()
            .put("capacity", 5)
            .put("refillRate", 1.0)
            .put("refillInterval", 1000L)
            .put("initialTokens", 5)
        
        // 检查是否允许通过
        val allowed1 = rateLimiter.checkTokenBucket(key, 1, config)
        val allowed2 = rateLimiter.checkTokenBucket(key, 2, config)
        val allowed3 = rateLimiter.checkTokenBucket(key, 3, config)
        
        testContext.verify {
            // 前三次请求应该允许通过
            assertTrue(allowed1, "第一次请求应该允许通过")
            assertTrue(allowed2, "第二次请求应该允许通过")
            assertFalse(allowed3, "第三次请求应该不允许通过，因为令牌不足")
        }
        
        // 等待令牌填充
        vertx.setTimer(1100) {
            // 再次检查
            val allowed4 = rateLimiter.checkTokenBucket(key, 1, config)
            
            testContext.verify {
                // 等待填充后，应该允许通过
                assertTrue(allowed4, "填充后的请求应该允许通过")
            }
            
            // 获取令牌桶状态
            val status = rateLimiter.getTokenBucketStatus(key)
            
            testContext.verify {
                // 验证状态
                assertTrue(status.getBoolean("exists"))
                assertEquals(5, status.getInteger("capacity"))
                assertEquals(1.0, status.getDouble("refillRate"))
                assertEquals(1000L, status.getLong("refillInterval"))
            }
            
            testContext.completeNow()
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test sliding window limiter`(testContext: VertxTestContext) {
        val key = "test-sliding-window"
        val config = JsonObject()
            .put("limit", 3)
            .put("windowSize", 1000L)
            .put("precision", 10)
        
        // 检查是否允许通过
        val allowed1 = rateLimiter.checkSlidingWindow(key, 1, config)
        val allowed2 = rateLimiter.checkSlidingWindow(key, 1, config)
        val allowed3 = rateLimiter.checkSlidingWindow(key, 1, config)
        val allowed4 = rateLimiter.checkSlidingWindow(key, 1, config)
        
        testContext.verify {
            // 前三次请求应该允许通过
            assertTrue(allowed1, "第一次请求应该允许通过")
            assertTrue(allowed2, "第二次请求应该允许通过")
            assertTrue(allowed3, "第三次请求应该允许通过")
            assertFalse(allowed4, "第四次请求应该不允许通过，因为超过限制")
        }
        
        // 等待窗口滑动
        vertx.setTimer(1100) {
            // 再次检查
            val allowed5 = rateLimiter.checkSlidingWindow(key, 1, config)
            
            testContext.verify {
                // 等待窗口滑动后，应该允许通过
                assertTrue(allowed5, "窗口滑动后的请求应该允许通过")
            }
            
            // 获取滑动窗口状态
            val status = rateLimiter.getSlidingWindowStatus(key)
            
            testContext.verify {
                // 验证状态
                assertTrue(status.getBoolean("exists"))
                assertEquals(3, status.getInteger("limit"))
                assertEquals(1000L, status.getLong("windowSize"))
                assertEquals(10, status.getInteger("precision"))
            }
            
            testContext.completeNow()
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test leaky bucket limiter`(testContext: VertxTestContext) {
        val key = "test-leaky-bucket"
        val config = JsonObject()
            .put("capacity", 5)
            .put("leakRate", 1.0)
            .put("leakInterval", 1000L)
        
        // 检查是否允许通过
        val allowed1 = rateLimiter.checkLeakyBucket(key, 2, config)
        val allowed2 = rateLimiter.checkLeakyBucket(key, 2, config)
        val allowed3 = rateLimiter.checkLeakyBucket(key, 2, config)
        
        testContext.verify {
            // 前两次请求应该允许通过
            assertTrue(allowed1, "第一次请求应该允许通过")
            assertTrue(allowed2, "第二次请求应该允许通过")
            assertFalse(allowed3, "第三次请求应该不允许通过，因为水量超过容量")
        }
        
        // 等待漏水
        vertx.setTimer(1100) {
            // 再次检查
            val allowed4 = rateLimiter.checkLeakyBucket(key, 1, config)
            
            testContext.verify {
                // 等待漏水后，应该允许通过
                assertTrue(allowed4, "漏水后的请求应该允许通过")
            }
            
            // 获取漏桶状态
            val status = rateLimiter.getLeakyBucketStatus(key)
            
            testContext.verify {
                // 验证状态
                assertTrue(status.getBoolean("exists"))
                assertEquals(5, status.getInteger("capacity"))
                assertEquals(1.0, status.getDouble("leakRate"))
                assertEquals(1000L, status.getLong("leakInterval"))
            }
            
            testContext.completeNow()
        }
        
        assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test delete limiter`(testContext: VertxTestContext) {
        val key = "test-delete"
        
        // 创建限流器
        rateLimiter.createTokenBucket(key, JsonObject()
            .put("capacity", 5)
            .put("refillRate", 1.0)
            .put("refillInterval", 1000L)
        )
        
        // 检查是否存在
        val statusBefore = rateLimiter.getTokenBucketStatus(key)
        
        testContext.verify {
            assertTrue(statusBefore.getBoolean("exists"))
        }
        
        // 删除限流器
        val deleted = rateLimiter.deleteTokenBucket(key)
        
        testContext.verify {
            assertTrue(deleted)
        }
        
        // 检查是否已删除
        val statusAfter = rateLimiter.getTokenBucketStatus(key)
        
        testContext.verify {
            assertFalse(statusAfter.getBoolean("exists"))
        }
        
        testContext.completeNow()
    }
}
