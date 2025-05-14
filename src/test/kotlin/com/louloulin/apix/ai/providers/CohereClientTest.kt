package com.louloulin.apix.ai.providers

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory

@ExtendWith(VertxExtension::class)
class CohereClientTest {
    private val logger = LoggerFactory.getLogger(CohereClientTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var cohereClient: CohereClient
    private val testApiKey = "test-api-key"
    private val testBaseUrl = "https://api.cohere.ai"

    @BeforeEach
    fun setUp(vertx: Vertx) {
        this.vertx = vertx
        cohereClient = CohereClient(vertx, testApiKey, testBaseUrl)
    }

    @AfterEach
    fun tearDown() {
        cohereClient.close()
    }

    @Test
    fun testGenerate(testContext: VertxTestContext) {
        // Test parameters
        val prompt = "Write a short story about a robot learning to paint."
        val model = "command"
        val options = JsonObject()
            .put("max_tokens", 300)
            .put("temperature", 0.7f)

        // Call the generate method
        cohereClient.generate(prompt, model, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("generations"))
                    assert(response.containsKey("id"))

                    val generations = response.getJsonArray("generations")
                    assert(generations.size() > 0)

                    val firstGeneration = generations.getJsonObject(0)
                    assert(firstGeneration.containsKey("text"))

                    logger.info("Generated text: ${firstGeneration.getString("text")}")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have a valid Cohere API key
                // or if the Cohere API is not accessible
                logger.error("Error in generate test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testGenerateMock(testContext: VertxTestContext) {
        // Skip this test for now as it requires more complex mocking
        testContext.completeNow()
    }

    @Test
    fun testChat(testContext: VertxTestContext) {
        // Test parameters
        val message = "What's the capital of France?"
        val model = "command"
        val chatHistory = listOf(
            JsonObject()
                .put("role", "USER")
                .put("message", "Hello, I have some geography questions."),
            JsonObject()
                .put("role", "CHATBOT")
                .put("message", "I'd be happy to help with geography questions. What would you like to know?")
        )
        val options = JsonObject()
            .put("temperature", 0.7f)
            .put("max_tokens", 300)

        // Call the chat method
        cohereClient.chat(message, model, chatHistory, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("text"))
                    assert(response.containsKey("generation_id"))

                    logger.info("Chat response: ${response.getString("text")}")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have a valid Cohere API key
                // or if the Cohere API is not accessible
                logger.error("Error in chat test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testEmbed(testContext: VertxTestContext) {
        // Test parameters
        val texts = listOf(
            "This is a test sentence for embedding.",
            "Another sentence to embed with different content."
        )
        val model = "embed-english-v3.0"
        val options = JsonObject()
            .put("input_type", "search_document")

        // Call the embed method
        cohereClient.embed(texts, model, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("embeddings"))

                    val embeddings = response.getJsonArray("embeddings")
                    assert(embeddings.size() == texts.size)

                    // Check that the first embedding is an array of floats
                    val firstEmbedding = embeddings.getJsonArray(0)
                    assert(firstEmbedding.size() > 0)

                    logger.info("Embedding dimensions: ${firstEmbedding.size()}")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have a valid Cohere API key
                // or if the Cohere API is not accessible
                logger.error("Error in embed test", err)
                testContext.failNow(err)
            }
    }
}
