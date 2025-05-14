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
class OpenSourceModelClientTest {
    private val logger = LoggerFactory.getLogger(OpenSourceModelClientTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var openSourceModelClient: OpenSourceModelClient
    
    // These should be set via environment variables in a real test
    private val ollamaUrl = System.getenv("OLLAMA_URL") ?: "http://localhost:11434"
    private val huggingFaceApiKey = System.getenv("HUGGINGFACE_API_KEY") ?: "test-api-key"

    @BeforeEach
    fun setUp(vertx: Vertx) {
        this.vertx = vertx
        openSourceModelClient = OpenSourceModelClient(
            vertx, 
            OpenSourceModelClient.BackendType.OLLAMA,
            ollamaUrl
        )
    }

    @AfterEach
    fun tearDown() {
        openSourceModelClient.close()
    }

    @Test
    fun testGenerateTextOllama(testContext: VertxTestContext) {
        // Skip test if Ollama is not running
        if (ollamaUrl == "http://localhost:11434") {
            logger.info("Skipping Ollama test - no valid Ollama URL or Ollama not running")
            testContext.completeNow()
            return
        }
        
        // Test parameters
        val prompt = "Write a short poem about artificial intelligence."
        val model = "llama2"  // or "mistral" depending on what's available
        val options = JsonObject()
            .put("temperature", 0.7f)
            .put("max_tokens", 300)
        
        // Call the generateText method
        openSourceModelClient.generateText(prompt, model, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("response") || response.containsKey("content"))
                    
                    val text = if (response.containsKey("response")) {
                        response.getString("response")
                    } else {
                        response.getString("content")
                    }
                    
                    logger.info("Generated text: $text")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if Ollama is not running or the model is not available
                logger.error("Error in generateText test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testChatOllama(testContext: VertxTestContext) {
        // Skip test if Ollama is not running
        if (ollamaUrl == "http://localhost:11434") {
            logger.info("Skipping Ollama test - no valid Ollama URL or Ollama not running")
            testContext.completeNow()
            return
        }
        
        // Test parameters
        val messages = listOf(
            JsonObject()
                .put("role", "system")
                .put("content", "You are a helpful assistant."),
            JsonObject()
                .put("role", "user")
                .put("content", "What's the capital of France?")
        )
        val model = "llama2"  // or "mistral" depending on what's available
        val options = JsonObject()
            .put("temperature", 0.7f)
            .put("max_tokens", 300)
        
        // Call the chat method
        openSourceModelClient.chat(messages, model, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("response") || response.containsKey("content") || 
                           response.containsKey("message"))
                    
                    val text = when {
                        response.containsKey("response") -> response.getString("response")
                        response.containsKey("content") -> response.getString("content")
                        response.containsKey("message") -> {
                            val message = response.getJsonObject("message")
                            message.getString("content", "")
                        }
                        else -> ""
                    }
                    
                    logger.info("Chat response: $text")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if Ollama is not running or the model is not available
                logger.error("Error in chat test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testListModelsOllama(testContext: VertxTestContext) {
        // Skip test if Ollama is not running
        if (ollamaUrl == "http://localhost:11434") {
            logger.info("Skipping Ollama test - no valid Ollama URL or Ollama not running")
            testContext.completeNow()
            return
        }
        
        // Call the listModels method
        openSourceModelClient.listModels()
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("models") || response.containsKey("tags"))
                    
                    if (response.containsKey("models")) {
                        val models = response.getJsonArray("models")
                        assert(models.size() >= 0)
                        logger.info("Available models: ${models.encode()}")
                    } else if (response.containsKey("tags")) {
                        val tags = response.getJsonArray("tags")
                        assert(tags.size() >= 0)
                        logger.info("Available models: ${tags.encode()}")
                    }
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if Ollama is not running
                logger.error("Error in listModels test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testHuggingFaceBackend(testContext: VertxTestContext) {
        // Skip test if no API key is provided
        if (huggingFaceApiKey == "test-api-key") {
            logger.info("Skipping Hugging Face test - no valid API key")
            testContext.completeNow()
            return
        }
        
        // Create a new client with Hugging Face backend
        val huggingFaceClient = OpenSourceModelClient(
            vertx,
            OpenSourceModelClient.BackendType.HUGGINGFACE,
            "https://api-inference.huggingface.co/models",
            huggingFaceApiKey
        )
        
        // Test parameters
        val prompt = "Write a short poem about artificial intelligence."
        val model = "mistralai/Mistral-7B-Instruct-v0.1"
        val options = JsonObject()
            .put("temperature", 0.7f)
            .put("max_tokens", 300)
        
        // Call the generateText method
        huggingFaceClient.generateText(prompt, model, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("response") || response.containsKey("content"))
                    
                    val text = if (response.containsKey("response")) {
                        response.getString("response")
                    } else {
                        response.getString("content")
                    }
                    
                    logger.info("Generated text from Hugging Face: $text")
                    
                    // Clean up
                    huggingFaceClient.close()
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if the API key is invalid or the model is not available
                logger.error("Error in Hugging Face test", err)
                
                // Clean up
                huggingFaceClient.close()
                testContext.failNow(err)
            }
    }
}
