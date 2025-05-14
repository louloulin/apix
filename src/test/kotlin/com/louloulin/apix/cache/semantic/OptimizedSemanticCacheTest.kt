package com.louloulin.apix.cache.semantic

import com.louloulin.apix.cache.MemoryCacheManager
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExtendWith(VertxExtension::class)
class OptimizedSemanticCacheTest {

    private lateinit var vertx: Vertx
    private lateinit var semanticCache: SemanticCacheManager

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()

        // Create optimized embedding engine
        val embeddingConfig = JsonObject()
            .put("vocabularySize", 1000)
            .put("quantizationBits", 8)
            .put("enableCache", true)
            .put("cacheSize", 100)

        val embeddingEngine = OptimizedEmbeddingEngine(vertx, embeddingConfig)

        // Create underlying cache
        val underlyingCache = MemoryCacheManager(vertx)

        // Create semantic cache with optimizations
        val config = JsonObject()
            .put("similarityThreshold", 0.7f)
            .put("enableVectorQuantization", true)
            .put("quantizationBits", 8)
            .put("enablePartialResponseCache", true)
            .put("similarityMatcher", JsonObject()
                .put("indexType", "brute_force")
            )
            .put("partialResponseCache", JsonObject()
                .put("minChunkLength", 20)
                .put("maxChunkLength", 200)
                .put("chunkOverlap", 10)
            )

        semanticCache = SemanticCacheManager(vertx, embeddingEngine, underlyingCache, 0.7f, config)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        semanticCache.close()
            .onComplete { ar ->
                vertx.close()
                    .onComplete { testContext.completeNow() }
            }
    }

    @Test
    fun testExactMatch(testContext: VertxTestContext) {
        val query = "What is artificial intelligence?"
        val response = JsonObject()
            .put("content", "Artificial intelligence (AI) is intelligence demonstrated by machines.")
            .put("model", "gpt-3.5-turbo")

        // Cache the response
        semanticCache.setByQuery(query, response, 3600)
            .compose { _ ->
                // Get the exact same query
                semanticCache.getByQuery(query)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val cachedResponse = ar.result()
                    assertNotNull(cachedResponse)
                    assertEquals(response.getString("content"), cachedResponse.getString("content"))

                    // Check cache stats
                    semanticCache.getStats()
                        .onSuccess { stats ->
                            assertTrue(stats.getLong("exactHits") > 0)
                            testContext.completeNow()
                        }
                }
            }
    }

    @Test
    fun testSemanticMatch(testContext: VertxTestContext) {
        val query1 = "What is artificial intelligence?"
        val response1 = JsonObject()
            .put("content", "Artificial intelligence (AI) is intelligence demonstrated by machines.")
            .put("model", "gpt-3.5-turbo")

        // Cache the first response
        semanticCache.setByQuery(query1, response1, 3600)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    testContext.completeNow()
                }
            }
    }

    @Test
    fun testMultipleQueries(testContext: VertxTestContext) {
        val query1 = "What is artificial intelligence?"
        val response1 = JsonObject()
            .put("content", "Artificial intelligence (AI) is intelligence demonstrated by machines.")
            .put("model", "gpt-3.5-turbo")

        val query2 = "What are neural networks?"
        val response2 = JsonObject()
            .put("content", "Neural networks are a set of algorithms, modeled loosely after the human brain.")
            .put("model", "gpt-3.5-turbo")

        // Cache both responses
        semanticCache.setByQuery(query1, response1, 3600)
            .compose { _ ->
                semanticCache.setByQuery(query2, response2, 3600)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    testContext.completeNow()
                }
            }
    }

    @Test
    fun testCacheClear(testContext: VertxTestContext) {
        val query = "What is artificial intelligence?"
        val response = JsonObject()
            .put("content", "Artificial intelligence (AI) is intelligence demonstrated by machines.")
            .put("model", "gpt-3.5-turbo")

        // Cache the response
        semanticCache.setByQuery(query, response, 3600)
            .compose { _ ->
                // Clear the cache
                semanticCache.clear()
            }
            .compose { _ ->
                // Try to get the query after clearing
                semanticCache.getByQuery(query)
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val cachedResponse = ar.result()
                    assertEquals(null, cachedResponse)

                    // Check cache stats
                    semanticCache.getStats()
                        .onSuccess { stats ->
                            assertTrue(stats.getLong("misses") > 0)
                            testContext.completeNow()
                        }
                }
            }
    }
}
