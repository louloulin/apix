package com.louloulin.apix.cache

import com.louloulin.apix.cache.semantic.SemanticCacheFactory
import com.louloulin.apix.cache.strategy.CacheStrategyFactory
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(VertxExtension::class)
class CacheServiceTest {

    private lateinit var vertx: Vertx
    private lateinit var cacheService: CacheService

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建嵌入式引擎
        val embeddingEngine = SemanticCacheFactory.createSimpleEmbeddingEngine(vertx)

        // 创建底层缓存
        val memoryCache = CacheFactory.createMemoryCache(vertx)

        // 创建语义缓存
        val semanticCache = SemanticCacheFactory.createSemanticCache(
            vertx,
            embeddingEngine,
            memoryCache,
            0.8f
        )

        // 创建缓存策略
        val ttlStrategy = CacheStrategyFactory.createTtlStrategy(3600)

        // 创建缓存服务
        cacheService = CacheService(vertx, semanticCache, ttlStrategy)

        testContext.completeNow()
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        cacheService.close()
            .onComplete { testContext.completeNow() }
    }

    @Test
    @Disabled("Requires Redis dependency")
    fun testSetAndGet(vertx: Vertx, testContext: VertxTestContext) {
        val key = "test-key"
        val value = JsonObject().put("name", "test").put("value", 123)

        cacheService.set(key, value)
            .compose { cacheService.get(key) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val cachedValue = ar.result()
                    testContext.verify {
                        assertNotNull(cachedValue)
                        assertEquals(value.getString("name"), cachedValue.getString("name"))
                        assertEquals(value.getInteger("value"), cachedValue.getInteger("value"))
                    }
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Disabled("Requires Redis dependency")
    fun testSetAndRemove(vertx: Vertx, testContext: VertxTestContext) {
        val key = "test-key-remove"
        val value = JsonObject().put("name", "test").put("value", 123)

        cacheService.set(key, value)
            .compose { cacheService.remove(key) }
            .compose { cacheService.get(key) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val cachedValue = ar.result()
                    testContext.verify {
                        assertNull(cachedValue)
                    }
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Disabled("Requires Redis dependency")
    fun testSetByQueryAndGetByQuery(vertx: Vertx, testContext: VertxTestContext) {
        val query = "What is the capital of France?"
        val response = JsonObject().put("answer", "Paris").put("confidence", 0.95)

        cacheService.setByQuery(query, response)
            .compose { cacheService.getByQuery(query) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val cachedResponse = ar.result()
                    testContext.verify {
                        assertNotNull(cachedResponse)
                        assertEquals(response.getString("answer"), cachedResponse.getString("answer"))
                        assertEquals(response.getDouble("confidence"), cachedResponse.getDouble("confidence"))
                    }
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Disabled("Requires Redis dependency")
    fun testSemanticSimilarity(vertx: Vertx, testContext: VertxTestContext) {
        val query1 = "What is the capital of France?"
        val response1 = JsonObject().put("answer", "Paris").put("confidence", 0.95)

        val query2 = "Tell me the capital city of France"

        cacheService.setByQuery(query1, response1)
            .compose { cacheService.getByQuery(query2) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val cachedResponse = ar.result()
                    testContext.verify {
                        assertNotNull(cachedResponse)
                        assertEquals(response1.getString("answer"), cachedResponse.getString("answer"))
                        assertEquals(response1.getDouble("confidence"), cachedResponse.getDouble("confidence"))
                    }
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Disabled("Requires Redis dependency")
    fun testGetStats(vertx: Vertx, testContext: VertxTestContext) {
        cacheService.getStats()
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val stats = ar.result()
                    testContext.verify {
                        assertNotNull(stats)
                        assertNotNull(stats.getString("strategy"))
                    }
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }

    @Test
    @Disabled("Requires Redis dependency")
    fun testClear(vertx: Vertx, testContext: VertxTestContext) {
        val key = "test-key-clear"
        val value = JsonObject().put("name", "test").put("value", 123)

        cacheService.set(key, value)
            .compose { cacheService.clear() }
            .compose { cacheService.get(key) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val cachedValue = ar.result()
                    testContext.verify {
                        assertNull(cachedValue)
                    }
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
}
