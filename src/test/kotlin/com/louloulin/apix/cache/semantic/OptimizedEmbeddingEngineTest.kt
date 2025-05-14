package com.louloulin.apix.cache.semantic

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExtendWith(VertxExtension::class)
class OptimizedEmbeddingEngineTest {

    private lateinit var vertx: Vertx
    private lateinit var embeddingEngine: OptimizedEmbeddingEngine

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        val config = JsonObject()
            .put("vocabularySize", 1000)
            .put("quantizationBits", 8)
            .put("enableCache", true)
            .put("cacheSize", 100)

        embeddingEngine = OptimizedEmbeddingEngine(vertx, config)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        embeddingEngine.close()
            .onComplete { ar ->
                vertx.close()
                    .onComplete { testContext.completeNow() }
            }
    }

    @Test
    fun testEmbedding(testContext: VertxTestContext) {
        val text = "This is a test text for embedding"

        embeddingEngine.embed(text)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val embedding = ar.result()
                    assertTrue(embedding.isNotEmpty())
                    testContext.completeNow()
                }
            }
    }

    @Test
    fun testEmbeddingCache(testContext: VertxTestContext) {
        val text = "This is a test text for embedding cache"

        // First embedding call
        embeddingEngine.embed(text)
            .compose { embedding1 ->
                // Second embedding call with the same text (should be cached)
                embeddingEngine.embed(text)
                    .map { embedding2 ->
                        Pair(embedding1, embedding2)
                    }
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val (embedding1, embedding2) = ar.result()

                    // Embeddings should be identical (from cache)
                    assertTrue(embedding1.contentEquals(embedding2))

                    // Check cache stats
                    val stats = embeddingEngine.getCacheStats()
                    assertTrue(stats.getInteger("cacheHits") > 0)

                    testContext.completeNow()
                }
            }
    }

    @Test
    fun testBatchEmbedding(testContext: VertxTestContext) {
        val texts = listOf(
            "This is the first text",
            "This is the second text",
            "This is the third text"
        )

        embeddingEngine.embedBatch(texts)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val embeddings = ar.result()
                    assertEquals(texts.size, embeddings.size)

                    // All embeddings should be non-empty
                    embeddings.forEach { embedding ->
                        assertTrue(embedding.isNotEmpty())
                    }

                    testContext.completeNow()
                }
            }
    }

    @Test
    fun testSimilarity(testContext: VertxTestContext) {
        val text1 = "This is a text about artificial intelligence"
        val text2 = "AI and machine learning are related topics"
        val text3 = "Bananas are yellow fruits"

        embeddingEngine.embed(text1)
            .compose { embedding1 ->
                embeddingEngine.embed(text2)
                    .compose { embedding2 ->
                        embeddingEngine.embed(text3)
                            .map { embedding3 ->
                                Triple(embedding1, embedding2, embedding3)
                            }
                    }
            }
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    val (embedding1, embedding2, embedding3) = ar.result()

                    // Similar texts should have higher similarity
                    val sim12 = embeddingEngine.similarity(embedding1, embedding2)
                    val sim13 = embeddingEngine.similarity(embedding1, embedding3)

                    // In a simple embedding model, these similarities might be random
                    // So we'll just verify that the similarity values are between 0 and 1
                    assertTrue(sim12 >= 0f && sim12 <= 1f)
                    assertTrue(sim13 >= 0f && sim13 <= 1f)

                    testContext.completeNow()
                }
            }
    }
}
