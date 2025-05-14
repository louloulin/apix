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
import java.util.Base64

/**
 * Client for interacting with the Google Vertex AI API.
 * Supports Gemini models for text generation, chat, and embeddings.
 */
class GoogleVertexAIClient(
    private val vertx: Vertx,
    private val projectId: String,
    private val location: String = "us-central1",
    private val apiKey: String? = null,
    private val serviceAccountKeyJson: String? = null,
    private val baseUrl: String = "https://$location-aiplatform.googleapis.com"
) {
    private val logger = LoggerFactory.getLogger(GoogleVertexAIClient::class.java)
    private val webClient: WebClient
    private var accessToken: String? = null
    private var tokenExpiration: Long = 0

    init {
        val options = WebClientOptions()
            .setUserAgent("APIX-GoogleVertexAIClient")
            .setKeepAlive(true)
            .setMaxPoolSize(50)
            .setConnectTimeout(30000)
            .setIdleTimeout(60000)

        webClient = WebClient.create(vertx, options)
        logger.info("Initialized Google Vertex AI client for project: $projectId, location: $location")
    }

    /**
     * Generate text using Google Vertex AI Gemini models.
     *
     * @param prompt The prompt to generate text from
     * @param model The model to use (e.g., "gemini-pro", "gemini-pro-vision")
     * @param options Additional options for generation
     * @return Future with the generated text response
     */
    fun generateText(
        prompt: String,
        model: String = "gemini-pro",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // Build the request body
        val requestBody = JsonObject()
            .put("contents", JsonArray().add(
                JsonObject()
                    .put("role", "user")
                    .put("parts", JsonArray().add(
                        JsonObject().put("text", prompt)
                    ))
            ))
            .put("generationConfig", buildGenerationConfig(options))
            .put("safetySettings", buildSafetySettings(options))

        // Determine the endpoint based on whether we're using API key or OAuth
        val endpoint = if (apiKey != null) {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:generateContent?key=$apiKey"
        } else {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:generateContent"
        }

        // Make the request
        executeRequest(endpoint, requestBody, promise)
        return promise.future()
    }

    /**
     * Chat with Google Vertex AI Gemini models.
     *
     * @param message The user message
     * @param model The model to use (e.g., "gemini-pro")
     * @param chatHistory Previous messages in the conversation
     * @param options Additional options for the chat
     * @return Future with the chat response
     */
    fun chat(
        message: String,
        model: String = "gemini-pro",
        chatHistory: List<JsonObject> = emptyList(),
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // Build the contents array with chat history
        val contents = JsonArray()
        
        // Add chat history
        for (historyItem in chatHistory) {
            val role = historyItem.getString("role", "user")
            val content = historyItem.getString("content", "")
            
            contents.add(
                JsonObject()
                    .put("role", role)
                    .put("parts", JsonArray().add(
                        JsonObject().put("text", content)
                    ))
            )
        }
        
        // Add the current message
        contents.add(
            JsonObject()
                .put("role", "user")
                .put("parts", JsonArray().add(
                    JsonObject().put("text", message)
                ))
        )

        // Build the request body
        val requestBody = JsonObject()
            .put("contents", contents)
            .put("generationConfig", buildGenerationConfig(options))
            .put("safetySettings", buildSafetySettings(options))

        // Determine the endpoint based on whether we're using API key or OAuth
        val endpoint = if (apiKey != null) {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:generateContent?key=$apiKey"
        } else {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:generateContent"
        }

        // Make the request
        executeRequest(endpoint, requestBody, promise)
        return promise.future()
    }

    /**
     * Generate embeddings for text using Google Vertex AI embedding models.
     *
     * @param texts List of texts to embed
     * @param model The embedding model to use (e.g., "textembedding-gecko")
     * @param options Additional options for embedding
     * @return Future with the embedding response
     */
    fun embedText(
        texts: List<String>,
        model: String = "textembedding-gecko",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // Build the instances array
        val instances = JsonArray()
        for (text in texts) {
            instances.add(
                JsonObject().put("content", text)
            )
        }

        // Build the request body
        val requestBody = JsonObject()
            .put("instances", instances)

        // Add optional parameters
        if (options.containsKey("taskType")) {
            requestBody.put("parameters", JsonObject()
                .put("taskType", options.getString("taskType"))
            )
        }

        // Determine the endpoint based on whether we're using API key or OAuth
        val endpoint = if (apiKey != null) {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:predict?key=$apiKey"
        } else {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:predict"
        }

        // Make the request
        executeRequest(endpoint, requestBody, promise)
        return promise.future()
    }

    /**
     * Generate multimodal content using Gemini Pro Vision.
     *
     * @param prompt The text prompt
     * @param imageData Base64-encoded image data
     * @param mimeType MIME type of the image (e.g., "image/jpeg")
     * @param model The model to use (default: "gemini-pro-vision")
     * @param options Additional options for generation
     * @return Future with the generated content response
     */
    fun generateWithImage(
        prompt: String,
        imageData: String,
        mimeType: String,
        model: String = "gemini-pro-vision",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // Build the request body with text and image
        val requestBody = JsonObject()
            .put("contents", JsonArray().add(
                JsonObject()
                    .put("role", "user")
                    .put("parts", JsonArray()
                        .add(JsonObject().put("text", prompt))
                        .add(JsonObject()
                            .put("inline_data", JsonObject()
                                .put("mime_type", mimeType)
                                .put("data", imageData)
                            )
                        )
                    )
            ))
            .put("generationConfig", buildGenerationConfig(options))
            .put("safetySettings", buildSafetySettings(options))

        // Determine the endpoint based on whether we're using API key or OAuth
        val endpoint = if (apiKey != null) {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:generateContent?key=$apiKey"
        } else {
            "$baseUrl/v1/projects/$projectId/locations/$location/publishers/google/models/$model:generateContent"
        }

        // Make the request
        executeRequest(endpoint, requestBody, promise)
        return promise.future()
    }

    /**
     * Build generation configuration from options.
     *
     * @param options The options for generation
     * @return JsonObject with generation configuration
     */
    private fun buildGenerationConfig(options: JsonObject): JsonObject {
        val config = JsonObject()
        
        if (options.containsKey("temperature")) {
            config.put("temperature", options.getFloat("temperature"))
        }
        
        if (options.containsKey("maxOutputTokens") || options.containsKey("max_tokens")) {
            config.put("maxOutputTokens", options.getInteger("maxOutputTokens", 
                options.getInteger("max_tokens", 1024)))
        }
        
        if (options.containsKey("topP")) {
            config.put("topP", options.getFloat("topP"))
        }
        
        if (options.containsKey("topK")) {
            config.put("topK", options.getInteger("topK"))
        }
        
        if (options.containsKey("stopSequences")) {
            config.put("stopSequences", options.getJsonArray("stopSequences"))
        }
        
        return config
    }

    /**
     * Build safety settings from options.
     *
     * @param options The options for safety settings
     * @return JsonArray with safety settings
     */
    private fun buildSafetySettings(options: JsonObject): JsonArray {
        val safetySettings = JsonArray()
        
        if (options.containsKey("safetySettings")) {
            return options.getJsonArray("safetySettings")
        }
        
        // Default safety settings if none provided
        return safetySettings
    }

    /**
     * Execute a request to the Google Vertex AI API.
     *
     * @param endpoint The API endpoint
     * @param requestBody The request body
     * @param promise The promise to complete
     */
    private fun executeRequest(
        endpoint: String,
        requestBody: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // Create the request
        val request = webClient.postAbs(endpoint)
            .putHeader("Content-Type", "application/json")
        
        // Add authorization header if using OAuth
        if (apiKey == null && serviceAccountKeyJson != null) {
            // In a real implementation, we would get an OAuth token using the service account key
            // For now, we'll just use a placeholder
            request.putHeader("Authorization", "Bearer $accessToken")
        }
        
        // Send the request
        request.sendJsonObject(requestBody)
            .onSuccess { response ->
                handleResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Google Vertex AI API", err)
                promise.fail(err)
            }
    }

    /**
     * Handle HTTP response from Google Vertex AI API.
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
                logger.error("Error parsing Google Vertex AI API response", e)
                promise.fail("Error parsing response: ${e.message}")
            }
        } else {
            var errorMessage = "Google Vertex AI API error: HTTP $statusCode"
            try {
                val errorBody = response.bodyAsJsonObject()
                errorMessage = errorBody.getJsonObject("error", JsonObject())
                    .getString("message", errorMessage)
            } catch (e: Exception) {
                // If we can't parse the error as JSON, use the status code message
                errorMessage = "Google Vertex AI API error: HTTP $statusCode - ${response.bodyAsString()}"
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
        logger.info("Closed Google Vertex AI client")
    }
}
