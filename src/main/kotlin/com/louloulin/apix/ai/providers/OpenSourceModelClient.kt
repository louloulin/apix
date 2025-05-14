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
 * Client for interacting with open source models like Llama and Mistral.
 * Supports multiple backends including Ollama, TGI (Text Generation Inference), and others.
 */
class OpenSourceModelClient(
    private val vertx: Vertx,
    private val backendType: BackendType = BackendType.OLLAMA,
    private val baseUrl: String = backendType.defaultUrl,
    private val apiKey: String? = null
) {
    private val logger = LoggerFactory.getLogger(OpenSourceModelClient::class.java)
    private val webClient: WebClient

    /**
     * Backend types for open source models
     */
    enum class BackendType(val defaultUrl: String) {
        OLLAMA("http://localhost:11434"),
        TGI("http://localhost:8080"),
        HUGGINGFACE("https://api-inference.huggingface.co/models"),
        CUSTOM("http://localhost:8000")
    }

    init {
        val options = WebClientOptions()
            .setUserAgent("APIX-OpenSourceModelClient")
            .setKeepAlive(true)
            .setMaxPoolSize(50)
            .setConnectTimeout(30000)
            .setIdleTimeout(60000)

        webClient = WebClient.create(vertx, options)
        logger.info("Initialized Open Source Model client with backend: $backendType, baseUrl: $baseUrl")
    }

    /**
     * Generate text using an open source model.
     *
     * @param prompt The prompt to generate text from
     * @param model The model to use (e.g., "llama2", "mistral", "llama3")
     * @param options Additional options for generation
     * @return Future with the generated text response
     */
    fun generateText(
        prompt: String,
        model: String,
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        when (backendType) {
            BackendType.OLLAMA -> generateTextOllama(prompt, model, options, promise)
            BackendType.TGI -> generateTextTGI(prompt, model, options, promise)
            BackendType.HUGGINGFACE -> generateTextHuggingFace(prompt, model, options, promise)
            BackendType.CUSTOM -> generateTextCustom(prompt, model, options, promise)
        }

        return promise.future()
    }

    /**
     * Chat with an open source model.
     *
     * @param messages List of chat messages
     * @param model The model to use (e.g., "llama2", "mistral", "llama3")
     * @param options Additional options for the chat
     * @return Future with the chat response
     */
    fun chat(
        messages: List<JsonObject>,
        model: String,
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        when (backendType) {
            BackendType.OLLAMA -> chatOllama(messages, model, options, promise)
            BackendType.TGI -> chatTGI(messages, model, options, promise)
            BackendType.HUGGINGFACE -> chatHuggingFace(messages, model, options, promise)
            BackendType.CUSTOM -> chatCustom(messages, model, options, promise)
        }

        return promise.future()
    }

    /**
     * Generate embeddings for text using an open source model.
     *
     * @param texts List of texts to embed
     * @param model The embedding model to use
     * @param options Additional options for embedding
     * @return Future with the embedding response
     */
    fun embedText(
        texts: List<String>,
        model: String,
        options: JsonObject = JsonObject()
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        when (backendType) {
            BackendType.OLLAMA -> embedTextOllama(texts, model, options, promise)
            BackendType.TGI -> embedTextTGI(texts, model, options, promise)
            BackendType.HUGGINGFACE -> embedTextHuggingFace(texts, model, options, promise)
            BackendType.CUSTOM -> embedTextCustom(texts, model, options, promise)
        }

        return promise.future()
    }

    /**
     * List available models from the backend.
     *
     * @return Future with the list of available models
     */
    fun listModels(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        when (backendType) {
            BackendType.OLLAMA -> listModelsOllama(promise)
            BackendType.TGI -> listModelsTGI(promise)
            BackendType.HUGGINGFACE -> listModelsHuggingFace(promise)
            BackendType.CUSTOM -> listModelsCustom(promise)
        }

        return promise.future()
    }

    // ==================== Ollama Implementation ====================

    private fun generateTextOllama(
        prompt: String,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        val requestBody = JsonObject()
            .put("model", model)
            .put("prompt", prompt)

        // Add optional parameters
        if (options.containsKey("temperature")) {
            requestBody.put("temperature", options.getFloat("temperature"))
        }
        if (options.containsKey("top_p")) {
            requestBody.put("top_p", options.getFloat("top_p"))
        }
        if (options.containsKey("top_k")) {
            requestBody.put("top_k", options.getInteger("top_k"))
        }
        if (options.containsKey("max_tokens")) {
            requestBody.put("num_predict", options.getInteger("max_tokens"))
        }
        if (options.containsKey("stop")) {
            requestBody.put("stop", options.getJsonArray("stop"))
        }
        if (options.containsKey("stream")) {
            requestBody.put("stream", options.getBoolean("stream"))
        }

        webClient.postAbs("$baseUrl/api/generate")
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleOllamaResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Ollama generate API", err)
                promise.fail(err)
            }
    }

    private fun chatOllama(
        messages: List<JsonObject>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        val requestBody = JsonObject()
            .put("model", model)
            .put("messages", JsonArray(messages))

        // Add optional parameters
        if (options.containsKey("temperature")) {
            requestBody.put("temperature", options.getFloat("temperature"))
        }
        if (options.containsKey("top_p")) {
            requestBody.put("top_p", options.getFloat("top_p"))
        }
        if (options.containsKey("top_k")) {
            requestBody.put("top_k", options.getInteger("top_k"))
        }
        if (options.containsKey("max_tokens")) {
            requestBody.put("num_predict", options.getInteger("max_tokens"))
        }
        if (options.containsKey("stop")) {
            requestBody.put("stop", options.getJsonArray("stop"))
        }
        if (options.containsKey("stream")) {
            requestBody.put("stream", options.getBoolean("stream"))
        }

        webClient.postAbs("$baseUrl/api/chat")
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleOllamaResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Ollama chat API", err)
                promise.fail(err)
            }
    }

    private fun embedTextOllama(
        texts: List<String>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // Ollama only supports embedding one text at a time
        if (texts.isEmpty()) {
            promise.complete(JsonObject().put("embeddings", JsonArray()))
            return
        }

        // For multiple texts, we'll process them sequentially and combine the results
        val combinedEmbeddings = JsonArray()
        var currentIndex = 0

        fun processNextText() {
            if (currentIndex >= texts.size) {
                // All texts processed
                promise.complete(JsonObject().put("embeddings", combinedEmbeddings))
                return
            }

            val text = texts[currentIndex]
            val requestBody = JsonObject()
                .put("model", model)
                .put("prompt", text)

            webClient.postAbs("$baseUrl/api/embeddings")
                .putHeader("Content-Type", "application/json")
                .sendJsonObject(requestBody)
                .onSuccess { response ->
                    try {
                        val responseBody = response.bodyAsJsonObject()
                        val embedding = responseBody.getJsonArray("embedding")
                        combinedEmbeddings.add(embedding)

                        // Process next text
                        currentIndex++
                        processNextText()
                    } catch (e: Exception) {
                        logger.error("Error parsing Ollama embeddings API response", e)
                        promise.fail("Error parsing response: ${e.message}")
                    }
                }
                .onFailure { err ->
                    logger.error("Error calling Ollama embeddings API", err)
                    promise.fail(err)
                }
        }

        // Start processing
        processNextText()
    }

    private fun listModelsOllama(promise: Promise<JsonObject>) {
        webClient.getAbs("$baseUrl/api/tags")
            .send()
            .onSuccess { response ->
                try {
                    val responseBody = response.bodyAsJsonObject()
                    promise.complete(responseBody)
                } catch (e: Exception) {
                    logger.error("Error parsing Ollama tags API response", e)
                    promise.fail("Error parsing response: ${e.message}")
                }
            }
            .onFailure { err ->
                logger.error("Error calling Ollama tags API", err)
                promise.fail(err)
            }
    }

    private fun handleOllamaResponse(
        response: HttpResponse<Buffer>,
        promise: Promise<JsonObject>
    ) {
        val statusCode = response.statusCode()

        if (statusCode >= 200 && statusCode < 300) {
            try {
                val responseBody = response.bodyAsJsonObject()
                promise.complete(responseBody)
            } catch (e: Exception) {
                logger.error("Error parsing Ollama API response", e)
                promise.fail("Error parsing response: ${e.message}")
            }
        } else {
            var errorMessage = "Ollama API error: HTTP $statusCode"
            try {
                val errorBody = response.bodyAsJsonObject()
                errorMessage = errorBody.getString("error", errorMessage)
            } catch (e: Exception) {
                // If we can't parse the error as JSON, use the status code message
                errorMessage = "Ollama API error: HTTP $statusCode - ${response.bodyAsString()}"
            }

            logger.error(errorMessage)
            promise.fail(errorMessage)
        }
    }

    // ==================== TGI Implementation ====================

    private fun generateTextTGI(
        prompt: String,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        val requestBody = JsonObject()
            .put("inputs", prompt)
            .put("parameters", buildTGIParameters(options))

        webClient.postAbs("$baseUrl/generate")
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleTGIResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling TGI generate API", err)
                promise.fail(err)
            }
    }

    private fun chatTGI(
        messages: List<JsonObject>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // Convert messages to TGI format
        val formattedMessages = formatMessagesForTGI(messages)

        val requestBody = JsonObject()
            .put("inputs", formattedMessages)
            .put("parameters", buildTGIParameters(options))

        webClient.postAbs("$baseUrl/generate")
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleTGIResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling TGI generate API for chat", err)
                promise.fail(err)
            }
    }

    private fun embedTextTGI(
        texts: List<String>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // TGI doesn't directly support embeddings, so we'll fail with a message
        promise.fail("Embeddings are not supported with TGI backend. Use Hugging Face or Ollama backend instead.")
    }

    private fun listModelsTGI(promise: Promise<JsonObject>) {
        // TGI doesn't have a model listing endpoint, so we'll return a default response
        val response = JsonObject()
            .put("models", JsonArray().add(JsonObject().put("id", "default")))
        promise.complete(response)
    }

    private fun buildTGIParameters(options: JsonObject): JsonObject {
        val parameters = JsonObject()

        if (options.containsKey("temperature")) {
            parameters.put("temperature", options.getFloat("temperature"))
        }
        if (options.containsKey("top_p")) {
            parameters.put("top_p", options.getFloat("top_p"))
        }
        if (options.containsKey("top_k")) {
            parameters.put("top_k", options.getInteger("top_k"))
        }
        if (options.containsKey("max_tokens")) {
            parameters.put("max_new_tokens", options.getInteger("max_tokens"))
        }
        if (options.containsKey("stop")) {
            parameters.put("stop", options.getJsonArray("stop"))
        }
        if (options.containsKey("stream")) {
            parameters.put("stream", options.getBoolean("stream"))
        }

        return parameters
    }

    private fun formatMessagesForTGI(messages: List<JsonObject>): String {
        // Format messages for TGI
        // This is a simple implementation that works with many models
        // Different models may require different formats
        val formattedMessages = StringBuilder()

        for (message in messages) {
            val role = message.getString("role", "user")
            val content = message.getString("content", "")

            when (role) {
                "system" -> formattedMessages.append("<s>[INST] <<SYS>>\n$content\n<</SYS>>\n\n")
                "user" -> {
                    if (formattedMessages.isEmpty()) {
                        formattedMessages.append("<s>[INST] $content [/INST]")
                    } else {
                        formattedMessages.append("\n\n[INST] $content [/INST]")
                    }
                }
                "assistant" -> formattedMessages.append(" $content </s>")
            }
        }

        return formattedMessages.toString()
    }

    private fun handleTGIResponse(
        response: HttpResponse<Buffer>,
        promise: Promise<JsonObject>
    ) {
        val statusCode = response.statusCode()

        if (statusCode >= 200 && statusCode < 300) {
            try {
                val responseBody = response.bodyAsJsonObject()

                // Convert TGI response to a standard format
                val standardResponse = JsonObject()
                    .put("model", "tgi-model")
                    .put("created", System.currentTimeMillis() / 1000)
                    .put("response", responseBody.getString("generated_text", ""))

                promise.complete(standardResponse)
            } catch (e: Exception) {
                logger.error("Error parsing TGI API response", e)
                promise.fail("Error parsing response: ${e.message}")
            }
        } else {
            var errorMessage = "TGI API error: HTTP $statusCode"
            try {
                val errorBody = response.bodyAsJsonObject()
                errorMessage = errorBody.getString("error", errorMessage)
            } catch (e: Exception) {
                // If we can't parse the error as JSON, use the status code message
                errorMessage = "TGI API error: HTTP $statusCode - ${response.bodyAsString()}"
            }

            logger.error(errorMessage)
            promise.fail(errorMessage)
        }
    }

    // ==================== Hugging Face Implementation ====================

    private fun generateTextHuggingFace(
        prompt: String,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        val requestBody = JsonObject()
            .put("inputs", prompt)
            .put("parameters", buildHuggingFaceParameters(options))

        val url = "$baseUrl/$model"

        webClient.postAbs(url)
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer $apiKey")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleHuggingFaceResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Hugging Face API", err)
                promise.fail(err)
            }
    }

    private fun chatHuggingFace(
        messages: List<JsonObject>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // Convert messages to Hugging Face format
        val formattedMessages = formatMessagesForHuggingFace(messages)

        val requestBody = JsonObject()
            .put("inputs", formattedMessages)
            .put("parameters", buildHuggingFaceParameters(options))

        val url = "$baseUrl/$model"

        webClient.postAbs(url)
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer $apiKey")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleHuggingFaceResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Hugging Face API for chat", err)
                promise.fail(err)
            }
    }

    private fun embedTextHuggingFace(
        texts: List<String>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        val requestBody = JsonObject()
            .put("inputs", JsonArray(texts))

        val url = "$baseUrl/$model"

        webClient.postAbs(url)
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer $apiKey")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                handleHuggingFaceEmbeddingResponse(response, promise)
            }
            .onFailure { err ->
                logger.error("Error calling Hugging Face API for embeddings", err)
                promise.fail(err)
            }
    }

    private fun listModelsHuggingFace(promise: Promise<JsonObject>) {
        // Hugging Face doesn't have a simple model listing endpoint in the API
        // We'll return a default response with common models
        val models = JsonArray()
            .add(JsonObject().put("id", "mistralai/Mistral-7B-Instruct-v0.1"))
            .add(JsonObject().put("id", "mistralai/Mistral-7B-Instruct-v0.2"))
            .add(JsonObject().put("id", "meta-llama/Llama-2-7b-chat-hf"))
            .add(JsonObject().put("id", "meta-llama/Llama-2-13b-chat-hf"))
            .add(JsonObject().put("id", "meta-llama/Llama-2-70b-chat-hf"))

        val response = JsonObject().put("models", models)
        promise.complete(response)
    }

    private fun buildHuggingFaceParameters(options: JsonObject): JsonObject {
        val parameters = JsonObject()

        if (options.containsKey("temperature")) {
            parameters.put("temperature", options.getFloat("temperature"))
        }
        if (options.containsKey("top_p")) {
            parameters.put("top_p", options.getFloat("top_p"))
        }
        if (options.containsKey("top_k")) {
            parameters.put("top_k", options.getInteger("top_k"))
        }
        if (options.containsKey("max_tokens")) {
            parameters.put("max_new_tokens", options.getInteger("max_tokens"))
        }
        if (options.containsKey("stop")) {
            parameters.put("stop", options.getJsonArray("stop"))
        }
        if (options.containsKey("stream")) {
            parameters.put("stream", options.getBoolean("stream"))
        }

        return parameters
    }

    private fun formatMessagesForHuggingFace(messages: List<JsonObject>): String {
        // Format messages for Hugging Face
        // This is a simple implementation that works with many models
        // Different models may require different formats
        val formattedMessages = StringBuilder()

        for (message in messages) {
            val role = message.getString("role", "user")
            val content = message.getString("content", "")

            when (role) {
                "system" -> formattedMessages.append("<s>[INST] <<SYS>>\n$content\n<</SYS>>\n\n")
                "user" -> {
                    if (formattedMessages.isEmpty()) {
                        formattedMessages.append("<s>[INST] $content [/INST]")
                    } else {
                        formattedMessages.append("\n\n[INST] $content [/INST]")
                    }
                }
                "assistant" -> formattedMessages.append(" $content </s>")
            }
        }

        return formattedMessages.toString()
    }

    private fun handleHuggingFaceResponse(
        response: HttpResponse<Buffer>,
        promise: Promise<JsonObject>
    ) {
        val statusCode = response.statusCode()

        if (statusCode >= 200 && statusCode < 300) {
            try {
                val responseBody = response.bodyAsJsonObject()

                // Convert Hugging Face response to a standard format
                val standardResponse = JsonObject()
                    .put("model", "huggingface-model")
                    .put("created", System.currentTimeMillis() / 1000)

                // Handle different response formats
                if (responseBody.containsKey("generated_text")) {
                    standardResponse.put("response", responseBody.getString("generated_text"))
                } else if (responseBody.getValue("0") is String) {
                    standardResponse.put("response", responseBody.getString("0"))
                } else if (responseBody.getValue("0") is JsonObject && responseBody.getJsonObject("0").containsKey("generated_text")) {
                    standardResponse.put("response", responseBody.getJsonObject("0").getString("generated_text"))
                } else {
                    standardResponse.put("response", responseBody.encode())
                }

                promise.complete(standardResponse)
            } catch (e: Exception) {
                logger.error("Error parsing Hugging Face API response", e)
                promise.fail("Error parsing response: ${e.message}")
            }
        } else {
            var errorMessage = "Hugging Face API error: HTTP $statusCode"
            try {
                val errorBody = response.bodyAsJsonObject()
                errorMessage = errorBody.getString("error", errorMessage)
            } catch (e: Exception) {
                // If we can't parse the error as JSON, use the status code message
                errorMessage = "Hugging Face API error: HTTP $statusCode - ${response.bodyAsString()}"
            }

            logger.error(errorMessage)
            promise.fail(errorMessage)
        }
    }

    private fun handleHuggingFaceEmbeddingResponse(
        response: HttpResponse<Buffer>,
        promise: Promise<JsonObject>
    ) {
        val statusCode = response.statusCode()

        if (statusCode >= 200 && statusCode < 300) {
            try {
                // Hugging Face returns embeddings as an array of arrays
                val embeddings = response.bodyAsJsonArray()

                // Convert to standard format
                val standardResponse = JsonObject()
                    .put("model", "huggingface-embedding-model")
                    .put("created", System.currentTimeMillis() / 1000)
                    .put("embeddings", embeddings)

                promise.complete(standardResponse)
            } catch (e: Exception) {
                logger.error("Error parsing Hugging Face API embedding response", e)
                promise.fail("Error parsing response: ${e.message}")
            }
        } else {
            var errorMessage = "Hugging Face API error: HTTP $statusCode"
            try {
                val errorBody = response.bodyAsJsonObject()
                errorMessage = errorBody.getString("error", errorMessage)
            } catch (e: Exception) {
                // If we can't parse the error as JSON, use the status code message
                errorMessage = "Hugging Face API error: HTTP $statusCode - ${response.bodyAsString()}"
            }

            logger.error(errorMessage)
            promise.fail(errorMessage)
        }
    }

    // ==================== Custom Implementation ====================

    private fun generateTextCustom(
        prompt: String,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // Implement custom backend logic here
        promise.fail("Custom backend not implemented")
    }

    private fun chatCustom(
        messages: List<JsonObject>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // Implement custom backend logic here
        promise.fail("Custom backend not implemented")
    }

    private fun embedTextCustom(
        texts: List<String>,
        model: String,
        options: JsonObject,
        promise: Promise<JsonObject>
    ) {
        // Implement custom backend logic here
        promise.fail("Custom backend not implemented")
    }

    private fun listModelsCustom(promise: Promise<JsonObject>) {
        // Implement custom backend logic here
        promise.fail("Custom backend not implemented")
    }

    /**
     * Close the client and release resources.
     */
    fun close() {
        webClient.close()
        logger.info("Closed Open Source Model client")
    }
}
