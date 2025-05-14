package com.louloulin.apix.ai.providers

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory

@ExtendWith(VertxExtension::class)
class GoogleVertexAIClientTest {
    private val logger = LoggerFactory.getLogger(GoogleVertexAIClientTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var vertexAIClient: GoogleVertexAIClient
    
    // These should be set via environment variables in a real test
    private val testProjectId = System.getenv("GOOGLE_CLOUD_PROJECT") ?: "test-project"
    private val testLocation = System.getenv("GOOGLE_CLOUD_LOCATION") ?: "us-central1"
    private val testApiKey = System.getenv("GOOGLE_API_KEY") ?: "test-api-key"

    @BeforeEach
    fun setUp(vertx: Vertx) {
        this.vertx = vertx
        vertexAIClient = GoogleVertexAIClient(vertx, testProjectId, testLocation, testApiKey)
    }

    @AfterEach
    fun tearDown() {
        vertexAIClient.close()
    }

    @Test
    fun testGenerateText(testContext: VertxTestContext) {
        // Skip test if API key is not provided
        if (testApiKey == "test-api-key") {
            logger.info("Skipping Google Vertex AI test - no valid API key")
            testContext.completeNow()
            return
        }
        
        // Test parameters
        val prompt = "Write a short poem about artificial intelligence."
        val model = "gemini-pro"
        val options = JsonObject()
            .put("temperature", 0.7f)
            .put("maxOutputTokens", 300)
            .put("topK", 40)
            .put("topP", 0.95f)
        
        // Call the generateText method
        vertexAIClient.generateText(prompt, model, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("candidates"))
                    
                    val candidates = response.getJsonArray("candidates")
                    assert(candidates.size() > 0)
                    
                    val candidate = candidates.getJsonObject(0)
                    assert(candidate.containsKey("content"))
                    
                    val content = candidate.getJsonObject("content")
                    assert(content.containsKey("parts"))
                    
                    val parts = content.getJsonArray("parts")
                    assert(parts.size() > 0)
                    
                    val part = parts.getJsonObject(0)
                    assert(part.containsKey("text"))
                    
                    val text = part.getString("text")
                    logger.info("Gemini response: $text")
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have a valid Google API key
                // or if the Google Vertex AI API is not accessible
                logger.error("Error in generateText test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testChat(testContext: VertxTestContext) {
        // Skip test if API key is not provided
        if (testApiKey == "test-api-key") {
            logger.info("Skipping Google Vertex AI test - no valid API key")
            testContext.completeNow()
            return
        }
        
        // Test parameters
        val message = "What's the capital of France?"
        val model = "gemini-pro"
        val chatHistory = listOf(
            JsonObject()
                .put("role", "user")
                .put("content", "Hello, I have some geography questions."),
            JsonObject()
                .put("role", "model")
                .put("content", "I'd be happy to help with geography questions. What would you like to know?")
        )
        val options = JsonObject()
            .put("temperature", 0.2f)
            .put("maxOutputTokens", 300)
        
        // Call the chat method
        vertexAIClient.chat(message, model, chatHistory, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("candidates"))
                    
                    val candidates = response.getJsonArray("candidates")
                    assert(candidates.size() > 0)
                    
                    val candidate = candidates.getJsonObject(0)
                    assert(candidate.containsKey("content"))
                    
                    val content = candidate.getJsonObject("content")
                    assert(content.containsKey("parts"))
                    
                    val parts = content.getJsonArray("parts")
                    assert(parts.size() > 0)
                    
                    val part = parts.getJsonObject(0)
                    assert(part.containsKey("text"))
                    
                    val text = part.getString("text")
                    logger.info("Gemini chat response: $text")
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have a valid Google API key
                // or if the Google Vertex AI API is not accessible
                logger.error("Error in chat test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testEmbedText(testContext: VertxTestContext) {
        // Skip test if API key is not provided
        if (testApiKey == "test-api-key") {
            logger.info("Skipping Google Vertex AI test - no valid API key")
            testContext.completeNow()
            return
        }
        
        // Test parameters
        val texts = listOf(
            "This is a test sentence for embedding.",
            "Another sentence to embed with different content."
        )
        val model = "textembedding-gecko"
        val options = JsonObject()
            .put("taskType", "RETRIEVAL_QUERY")
        
        // Call the embedText method
        vertexAIClient.embedText(texts, model, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("predictions"))
                    
                    val predictions = response.getJsonArray("predictions")
                    assert(predictions.size() == texts.size)
                    
                    val firstPrediction = predictions.getJsonObject(0)
                    assert(firstPrediction.containsKey("embeddings"))
                    
                    val embeddings = firstPrediction.getJsonObject("embeddings")
                    assert(embeddings.containsKey("values"))
                    
                    val values = embeddings.getJsonArray("values")
                    assert(values.size() > 0)
                    
                    logger.info("Embedding dimensions: ${values.size()}")
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have a valid Google API key
                // or if the Google Vertex AI API is not accessible
                logger.error("Error in embedText test", err)
                testContext.failNow(err)
            }
    }
}
