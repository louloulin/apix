package com.louloulin.apix.ai.providers

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Client for interacting with the AWS Bedrock API.
 * Supports various models including Anthropic Claude, Amazon Titan, etc.
 */
class AWSBedrockClient(
    private val vertx: Vertx,
    private val accessKey: String,
    private val secretKey: String,
    private val region: String = "us-east-1",
    private val sessionToken: String? = null
) {
    private val logger = LoggerFactory.getLogger(AWSBedrockClient::class.java)
    private val webClient: WebClient
    private val service = "bedrock"
    private val host = "bedrock-runtime.$region.amazonaws.com"
    private val endpoint = "https://$host"

    init {
        val options = WebClientOptions()
            .setUserAgent("APIX-AWSBedrockClient")
            .setKeepAlive(true)
            .setMaxPoolSize(50)
            .setConnectTimeout(30000)
            .setIdleTimeout(60000)

        webClient = WebClient.create(vertx, options)
        logger.info("Initialized AWS Bedrock API client for region: $region")
    }

    /**
     * Invoke a model using AWS Bedrock.
     *
     * @param modelId The model ID (e.g., "anthropic.claude-v2", "amazon.titan-text-express-v1")
     * @param requestBody The request body as a JsonObject
     * @return Future with the model response
     */
    fun invokeModel(modelId: String, requestBody: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        val path = "/runtime/models/$modelId/invoke"
        
        // Convert request body to string
        val bodyString = requestBody.encode()
        
        // Create AWS SigV4 signature
        val timestamp = Instant.now()
        val amzDate = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(timestamp)
        val dateStamp = DateTimeFormatter
            .ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC)
            .format(timestamp)
        
        // Create canonical request
        val canonicalUri = path
        val canonicalQueryString = ""
        val canonicalHeaders = "content-type:application/json\nhost:$host\nx-amz-date:$amzDate\n"
        val signedHeaders = "content-type;host;x-amz-date"
        
        // Add session token if provided
        val canonicalHeadersWithToken = if (sessionToken != null) {
            "$canonicalHeaders\nx-amz-security-token:$sessionToken\n"
        } else {
            canonicalHeaders
        }
        
        val signedHeadersWithToken = if (sessionToken != null) {
            "$signedHeaders;x-amz-security-token"
        } else {
            signedHeaders
        }
        
        // Create payload hash
        val payloadHash = sha256Hex(bodyString)
        
        val canonicalRequest = "POST\n$canonicalUri\n$canonicalQueryString\n$canonicalHeadersWithToken\n$signedHeadersWithToken\n$payloadHash"
        val canonicalRequestHash = sha256Hex(canonicalRequest)
        
        // Create string to sign
        val algorithm = "AWS4-HMAC-SHA256"
        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "$algorithm\n$amzDate\n$credentialScope\n$canonicalRequestHash"
        
        // Calculate signature
        val kSecret = "AWS4$secretKey".toByteArray()
        val kDate = hmacSha256(kSecret, dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        val kSigning = hmacSha256(kService, "aws4_request")
        val signature = hmacSha256Hex(kSigning, stringToSign)
        
        // Create authorization header
        val authorizationHeader = "$algorithm Credential=$accessKey/$credentialScope, SignedHeaders=$signedHeadersWithToken, Signature=$signature"
        
        // Make the request
        val request = webClient.postAbs("$endpoint$path")
            .putHeader("Content-Type", "application/json")
            .putHeader("Host", host)
            .putHeader("X-Amz-Date", amzDate)
            .putHeader("Authorization", authorizationHeader)
        
        // Add session token if provided
        if (sessionToken != null) {
            request.putHeader("X-Amz-Security-Token", sessionToken)
        }
        
        request.sendBuffer(Buffer.buffer(bodyString))
            .onSuccess { response ->
                handleResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling AWS Bedrock API", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * Invoke Anthropic Claude model using AWS Bedrock.
     *
     * @param prompt The prompt to send to the model
     * @param modelId The Claude model ID (e.g., "anthropic.claude-v2", "anthropic.claude-instant-v1")
     * @param options Additional options for the model
     * @return Future with the model response
     */
    fun invokeAnthropicClaude(
        prompt: String,
        modelId: String = "anthropic.claude-v2",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val requestBody = JsonObject()
            .put("prompt", prompt)
            .put("max_tokens_to_sample", options.getInteger("max_tokens", 1000))
            .put("temperature", options.getFloat("temperature", 0.7f))
            .put("top_k", options.getInteger("top_k", 250))
            .put("top_p", options.getFloat("top_p", 0.999f))
            .put("stop_sequences", options.getJsonArray("stop_sequences", JsonArray()))
        
        return invokeModel(modelId, requestBody)
    }
    
    /**
     * Invoke Amazon Titan model using AWS Bedrock.
     *
     * @param prompt The prompt to send to the model
     * @param modelId The Titan model ID (e.g., "amazon.titan-text-express-v1")
     * @param options Additional options for the model
     * @return Future with the model response
     */
    fun invokeAmazonTitan(
        prompt: String,
        modelId: String = "amazon.titan-text-express-v1",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val requestBody = JsonObject()
            .put("inputText", prompt)
            .put("textGenerationConfig", JsonObject()
                .put("maxTokenCount", options.getInteger("max_tokens", 512))
                .put("temperature", options.getFloat("temperature", 0.7f))
                .put("topP", options.getFloat("top_p", 0.9f))
                .put("stopSequences", options.getJsonArray("stop_sequences", JsonArray()))
            )
        
        return invokeModel(modelId, requestBody)
    }
    
    /**
     * Invoke AI21 Jurassic model using AWS Bedrock.
     *
     * @param prompt The prompt to send to the model
     * @param modelId The Jurassic model ID (e.g., "ai21.j2-ultra-v1")
     * @param options Additional options for the model
     * @return Future with the model response
     */
    fun invokeAI21Jurassic(
        prompt: String,
        modelId: String = "ai21.j2-ultra-v1",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val requestBody = JsonObject()
            .put("prompt", prompt)
            .put("maxTokens", options.getInteger("max_tokens", 512))
            .put("temperature", options.getFloat("temperature", 0.7f))
            .put("topP", options.getFloat("top_p", 0.9f))
            .put("stopSequences", options.getJsonArray("stop_sequences", JsonArray()))
        
        return invokeModel(modelId, requestBody)
    }
    
    /**
     * Invoke Cohere Command model using AWS Bedrock.
     *
     * @param prompt The prompt to send to the model
     * @param modelId The Cohere model ID (e.g., "cohere.command-text-v14")
     * @param options Additional options for the model
     * @return Future with the model response
     */
    fun invokeCohere(
        prompt: String,
        modelId: String = "cohere.command-text-v14",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val requestBody = JsonObject()
            .put("prompt", prompt)
            .put("max_tokens", options.getInteger("max_tokens", 512))
            .put("temperature", options.getFloat("temperature", 0.7f))
            .put("p", options.getFloat("top_p", 0.9f))
            .put("k", options.getInteger("top_k", 0))
            .put("stop_sequences", options.getJsonArray("stop_sequences", JsonArray()))
        
        return invokeModel(modelId, requestBody)
    }
    
    /**
     * Handle HTTP response from AWS Bedrock API.
     *
     * @param response The HTTP response
     * @param promise The promise to complete
     */
    private fun handleResponse(
        response: HttpResponse<Buffer>,
        promise: Promise<JsonObject>
    ) {
        val statusCode = response.statusCode()
        
        if (statusCode >= 200 && statusCode < 300) {
            try {
                val responseBody = response.bodyAsJsonObject()
                promise.complete(responseBody)
            } catch (e: Exception) {
                logger.error("Error parsing AWS Bedrock API response", e)
                promise.fail("Error parsing response: ${e.message}")
            }
        } else {
            var errorMessage = "AWS Bedrock API error: HTTP $statusCode"
            try {
                val errorBody = response.bodyAsJsonObject()
                errorMessage = errorBody.getString("message", errorMessage)
            } catch (e: Exception) {
                // If we can't parse the error as JSON, use the status code message
                errorMessage = "AWS Bedrock API error: HTTP $statusCode - ${response.bodyAsString()}"
            }
            
            logger.error(errorMessage)
            promise.fail(errorMessage)
        }
    }
    
    /**
     * Close the client and release resources.
     */
    fun close() {
        webClient.close()
    }
    
    /**
     * Utility method to calculate SHA-256 hash.
     */
    private fun sha256Hex(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Utility method to calculate HMAC-SHA256.
     */
    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }
    
    /**
     * Utility method to calculate HMAC-SHA256 and return as hex string.
     */
    private fun hmacSha256Hex(key: ByteArray, data: String): String {
        val hash = hmacSha256(key, data)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
