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

/**
 * Client for interacting with the Cohere API.
 * Supports Generate, Chat, and Embed endpoints.
 */
class CohereClient(
    private val vertx: Vertx,
    private val apiKey: String,
    private val baseUrl: String = "https://api.cohere.ai"
) {
    private val logger = LoggerFactory.getLogger(CohereClient::class.java)
    private val webClient: WebClient

    init {
        val options = WebClientOptions()
            .setUserAgent("APIX-CohereClient")
            .setKeepAlive(true)
            .setMaxPoolSize(50)
            .setConnectTimeout(30000)
            .setIdleTimeout(60000)

        webClient = WebClient.create(vertx, options)
        logger.info("Initialized Cohere API client with base URL: $baseUrl")
    }

    /**
     * Generate text using Cohere's generate endpoint.
     *
     * @param prompt The prompt to generate text from
     * @param model The model to use (e.g., "command", "command-light", etc.)
     * @param options Additional options for generation
     * @return Future with the generated text response
     */
    fun generate(prompt: String, model: String, options: JsonObject = JsonObject()): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        val requestBody = JsonObject()
            .put("prompt", prompt)
            .put("model", model)

        // Add optional parameters if provided
        if (options.containsKey("max_tokens")) {
            requestBody.put("max_tokens", options.getInteger("max_tokens"))
        }
        if (options.containsKey("temperature")) {
            requestBody.put("temperature", options.getFloat("temperature"))
        }
        if (options.containsKey("k")) {
            requestBody.put("k", options.getInteger("k"))
        }
        if (options.containsKey("p")) {
            requestBody.put("p", options.getFloat("p"))
        }
        if (options.containsKey("frequency_penalty")) {
            requestBody.put("frequency_penalty", options.getFloat("frequency_penalty"))
        }
        if (options.containsKey("presence_penalty")) {
            requestBody.put("presence_penalty", options.getFloat("presence_penalty"))
        }
        if (options.containsKey("stop_sequences")) {
            requestBody.put("stop_sequences", options.getJsonArray("stop_sequences"))
        }
        if (options.containsKey("return_likelihoods")) {
            requestBody.put("return_likelihoods", options.getString("return_likelihoods"))
        }
        if (options.containsKey("stream")) {
            requestBody.put("stream", options.getBoolean("stream"))
        }

        webClient.postAbs("$baseUrl/v1/generate")
            .putHeader("Authorization", "Bearer $apiKey")
            .putHeader("Content-Type", "application/json")
            .putHeader("Accept", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Cohere generate API", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * Chat with Cohere's chat endpoint.
     *
     * @param message The user message
     * @param model The model to use (e.g., "command", "command-light", etc.)
     * @param chatHistory Previous messages in the conversation
     * @param options Additional options for the chat
     * @return Future with the chat response
     */
    fun chat(
        message: String,
        model: String,
        chatHistory: List<JsonObject> = emptyList(),
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        val requestBody = JsonObject()
            .put("message", message)
            .put("model", model)

        // Add chat history if provided
        if (chatHistory.isNotEmpty()) {
            val historyArray = JsonArray()
            chatHistory.forEach { historyArray.add(it) }
            requestBody.put("chat_history", historyArray)
        }

        // Add optional parameters if provided
        if (options.containsKey("temperature")) {
            requestBody.put("temperature", options.getFloat("temperature"))
        }
        if (options.containsKey("p")) {
            requestBody.put("p", options.getFloat("p"))
        }
        if (options.containsKey("k")) {
            requestBody.put("k", options.getInteger("k"))
        }
        if (options.containsKey("max_tokens")) {
            requestBody.put("max_tokens", options.getInteger("max_tokens"))
        }
        if (options.containsKey("stream")) {
            requestBody.put("stream", options.getBoolean("stream"))
        }
        if (options.containsKey("preamble")) {
            requestBody.put("preamble", options.getString("preamble"))
        }
        if (options.containsKey("tools")) {
            requestBody.put("tools", options.getJsonArray("tools"))
        }

        webClient.postAbs("$baseUrl/v1/chat")
            .putHeader("Authorization", "Bearer $apiKey")
            .putHeader("Content-Type", "application/json")
            .putHeader("Accept", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Cohere chat API", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * Generate embeddings for text using Cohere's embed endpoint.
     *
     * @param texts List of texts to embed
     * @param model The embedding model to use (e.g., "embed-english-v3.0")
     * @param options Additional options for embedding
     * @return Future with the embedding response
     */
    fun embed(
        texts: List<String>,
        model: String = "embed-english-v3.0",
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        val requestBody = JsonObject()
            .put("texts", JsonArray(texts))
            .put("model", model)

        // Add optional parameters if provided
        if (options.containsKey("truncate")) {
            requestBody.put("truncate", options.getString("truncate"))
        }
        if (options.containsKey("input_type")) {
            requestBody.put("input_type", options.getString("input_type"))
        }

        webClient.postAbs("$baseUrl/v1/embed")
            .putHeader("Authorization", "Bearer $apiKey")
            .putHeader("Content-Type", "application/json")
            .putHeader("Accept", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Cohere embed API", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * Handle HTTP response from Cohere API.
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
                logger.error("Error parsing Cohere API response", e)
                promise.fail("Error parsing response: ${e.message}")
            }
        } else {
            var errorMessage = "Cohere API error: HTTP $statusCode"
            try {
                val errorBody = response.bodyAsJsonObject()
                errorMessage = errorBody.getString("message", errorMessage)
            } catch (e: Exception) {
                // If we can't parse the error as JSON, use the status code message
                errorMessage = "Cohere API error: HTTP $statusCode - ${response.bodyAsString()}"
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
        logger.info("Closed Cohere API client")
    }
}
