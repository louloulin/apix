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
class PartialResponseCacheTest {

    private lateinit var vertx: Vertx
    private lateinit var embeddingEngine: EmbeddingEngine
    private lateinit var partialResponseCache: PartialResponseCache

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        embeddingEngine = SimpleEmbeddingEngine(vertx)

        val config = JsonObject()
            .put("similarityThreshold", 0.7f)
            .put("minChunkLength", 20)
            .put("maxChunkLength", 200)
            .put("chunkOverlap", 10)
            .put("maxCacheSize", 100)
            .put("indexType", "brute_force")

        partialResponseCache = PartialResponseCache(vertx, embeddingEngine, config)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        partialResponseCache.clear()
            .onComplete { ar ->
                vertx.close()
                    .onComplete { testContext.completeNow() }
            }
    }

    @Test
    fun testCacheResponse(testContext: VertxTestContext) {
        val query = "What is artificial intelligence?"
        val response = """
            Artificial intelligence (AI) is intelligence demonstrated by machines, as opposed to natural intelligence displayed by animals including humans.

            AI research has been defined as the field of study of intelligent agents, which refers to any system that perceives its environment and takes actions that maximize its chance of achieving its goals.

            The term "artificial intelligence" had previously been used to describe machines that mimic and display "human" cognitive skills that are associated with the human mind, such as "learning" and "problem-solving".
        """.trimIndent()

        partialResponseCache.cacheResponse(query, response)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())

                    // Check cache stats
                    val stats = partialResponseCache.getStats()
                    assertTrue(stats.getInteger("chunkCount") > 0)

                    testContext.completeNow()
                }
            }
    }

    @Test
    fun testFindRelevantChunks(testContext: VertxTestContext) {
        val query1 = "What is artificial intelligence?"
        val response1 = """
            Artificial intelligence (AI) is intelligence demonstrated by machines, as opposed to natural intelligence displayed by animals including humans.

            AI research has been defined as the field of study of intelligent agents, which refers to any system that perceives its environment and takes actions that maximize its chance of achieving its goals.
        """.trimIndent()

        // Cache a response
        partialResponseCache.cacheResponse(query1, response1)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    testContext.completeNow()
                }
            }
    }

    @Test
    fun testComposeResponse(testContext: VertxTestContext) {
        val query = "What is machine learning?"
        val response = """
            Machine learning is a branch of artificial intelligence and computer science which focuses on the use of data and algorithms to imitate the way that humans learn, gradually improving its accuracy.
        """.trimIndent()

        // Cache the response
        partialResponseCache.cacheResponse(query, response)
            .onComplete { ar ->
                testContext.verify {
                    assertTrue(ar.succeeded())
                    testContext.completeNow()
                }
            }
    }
}
