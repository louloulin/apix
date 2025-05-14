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
class AWSBedrockClientTest {
    private val logger = LoggerFactory.getLogger(AWSBedrockClientTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var bedrockClient: AWSBedrockClient
    
    // These should be set via environment variables in a real test
    private val testAccessKey = System.getenv("AWS_ACCESS_KEY_ID") ?: "test-access-key"
    private val testSecretKey = System.getenv("AWS_SECRET_ACCESS_KEY") ?: "test-secret-key"
    private val testRegion = System.getenv("AWS_REGION") ?: "us-east-1"
    private val testSessionToken = System.getenv("AWS_SESSION_TOKEN")

    @BeforeEach
    fun setUp(vertx: Vertx) {
        this.vertx = vertx
        bedrockClient = AWSBedrockClient(vertx, testAccessKey, testSecretKey, testRegion, testSessionToken)
    }

    @AfterEach
    fun tearDown() {
        bedrockClient.close()
    }

    @Test
    fun testInvokeAnthropicClaude(testContext: VertxTestContext) {
        // Skip test if credentials are not provided
        if (testAccessKey == "test-access-key" || testSecretKey == "test-secret-key") {
            logger.info("Skipping AWS Bedrock test - no valid credentials")
            testContext.completeNow()
            return
        }
        
        // Test parameters
        val prompt = "Human: Write a short poem about artificial intelligence.\n\nAssistant:"
        val modelId = "anthropic.claude-v2"
        val options = JsonObject()
            .put("max_tokens", 300)
            .put("temperature", 0.7f)
            .put("stop_sequences", JsonArray().add("\n\nHuman:"))
        
        // Call the invokeAnthropicClaude method
        bedrockClient.invokeAnthropicClaude(prompt, modelId, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("completion"))
                    
                    val completion = response.getString("completion")
                    logger.info("Claude response: $completion")
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have valid AWS credentials
                // or if the AWS Bedrock API is not accessible
                logger.error("Error in invokeAnthropicClaude test", err)
                testContext.failNow(err)
            }
    }

    @Test
    fun testInvokeAmazonTitan(testContext: VertxTestContext) {
        // Skip test if credentials are not provided
        if (testAccessKey == "test-access-key" || testSecretKey == "test-secret-key") {
            logger.info("Skipping AWS Bedrock test - no valid credentials")
            testContext.completeNow()
            return
        }
        
        // Test parameters
        val prompt = "Write a short poem about artificial intelligence."
        val modelId = "amazon.titan-text-express-v1"
        val options = JsonObject()
            .put("max_tokens", 300)
            .put("temperature", 0.7f)
        
        // Call the invokeAmazonTitan method
        bedrockClient.invokeAmazonTitan(prompt, modelId, options)
            .onSuccess { response ->
                testContext.verify {
                    // Verify response contains expected fields
                    assert(response.containsKey("results"))
                    
                    val results = response.getJsonArray("results")
                    assert(results.size() > 0)
                    
                    val result = results.getJsonObject(0)
                    assert(result.containsKey("outputText"))
                    
                    val outputText = result.getString("outputText")
                    logger.info("Titan response: $outputText")
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                // Note: This test will fail if you don't have valid AWS credentials
                // or if the AWS Bedrock API is not accessible
                logger.error("Error in invokeAmazonTitan test", err)
                testContext.failNow(err)
            }
    }
}
