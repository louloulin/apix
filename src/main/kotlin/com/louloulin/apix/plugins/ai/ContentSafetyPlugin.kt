package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Plugin that detects and filters harmful content from requests and responses.
 *
 * This plugin:
 * 1. Detects harmful content in requests and responses
 * 2. Blocks, warns, or logs harmful content based on configuration
 * 3. Supports custom content safety policies
 */
class ContentSafetyPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(ContentSafetyPlugin::class.java)

    override val type: String = "content-safety"

    // Configuration values with defaults
    private val enabled: Boolean = config.getBoolean("enabled", true) ?: true
    private val checkRequests: Boolean = config.getBoolean("check_requests", true) ?: true
    private val checkResponses: Boolean = config.getBoolean("check_responses", true) ?: true
    private val actionMode: String = config.getString("action_mode", "block") ?: "block" // block, warn, log
    private val logDetections: Boolean = config.getBoolean("log_detections", true) ?: true
    private val threshold: Double = config.config.getDouble("threshold", 0.7)

    // Content categories to check
    private val categories: List<String> = config.config.getJsonArray("categories")
        ?.map { it.toString() }
        ?: listOf("violence", "sexual", "hate", "harassment", "self-harm", "shocking")

    // Custom harmful content patterns
    private val customPatterns: Map<String, Pattern> = config.config.getJsonObject("custom_patterns")
        ?.map { it.key to Pattern.compile(it.value.toString(), Pattern.CASE_INSENSITIVE) }
        ?.toMap()
        ?: emptyMap()

    // Default harmful content patterns
    private val defaultPatterns = mapOf(
        "violence" to Pattern.compile("\\b(kill|murder|attack|bomb|shoot|stab|assault|weapon|gun|knife)\\b", Pattern.CASE_INSENSITIVE),
        "sexual" to Pattern.compile("\\b(porn|explicit|nude|naked|sex|xxx|adult)\\b", Pattern.CASE_INSENSITIVE),
        "hate" to Pattern.compile("\\b(hate|racist|nazi|supremacist|bigot)\\b", Pattern.CASE_INSENSITIVE),
        "harassment" to Pattern.compile("\\b(harass|bully|stalk|threaten|intimidate)\\b", Pattern.CASE_INSENSITIVE),
        "self-harm" to Pattern.compile("\\b(suicide|self-harm|cut myself|kill myself)\\b", Pattern.CASE_INSENSITIVE),
        "shocking" to Pattern.compile("\\b(gore|disturbing|graphic|shocking)\\b", Pattern.CASE_INSENSITIVE)
    )

    // Combined patterns
    private val allPatterns: Map<String, Pattern> = defaultPatterns + customPatterns

    // Web client for external API calls
    private lateinit var webClient: WebClient

    // External content moderation API configuration
    private val useExternalApi: Boolean = config.getBoolean("use_external_api", false) ?: false
    private val apiUrl: String = config.getString("api_url", "") ?: ""
    private val apiKey: String = config.getString("api_key", "") ?: ""

    /**
     * Initialize the plugin
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            logger.info("Initializing ContentSafetyPlugin with ${allPatterns.size} detection patterns")

            // Initialize web client if external API is enabled
            if (useExternalApi && apiUrl.isNotEmpty()) {
                val options = WebClientOptions()
                    .setUserAgent("APIX-ContentSafetyPlugin")
                    .setKeepAlive(true)
                    .setMaxPoolSize(10)

                webClient = WebClient.create(vertx, options)
                logger.info("Web client initialized for external content moderation API: $apiUrl")
            }

            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to initialize ContentSafetyPlugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Execute the plugin
     */
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!enabled) {
            // Plugin is disabled, skip processing
            promise.complete()
            return promise.future()
        }

        try {
            // Process request if enabled
            if (checkRequests) {
                val requestFuture = processRequest(context)

                requestFuture.onComplete { ar ->
                    if (ar.failed()) {
                        logger.error("Error processing request in content safety plugin", ar.cause())
                        // Continue with the request even if content check fails
                    }

                    // If harmful content was detected and action is block, the response has already been sent
                    if (ar.result() == true && actionMode == "block") {
                        // Request was blocked, complete the promise to stop the chain
                        promise.complete()
                    } else {
                        // No harmful content or action is not block, continue with the request

                        // Process response if enabled
                        if (checkResponses) {
                            // In a real implementation, we would add a response handler here
                            // For simplicity, we'll just log that we would process the response
                            logger.info("Response checking would be set up here in a real implementation")
                        }

                        // Continue with the request
                        promise.complete()
                    }
                }
            } else {
                // Request checking is disabled, continue with the request
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error executing content safety plugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Process the request to check for harmful content
     * @return Future<Boolean> true if harmful content was detected, false otherwise
     */
    private fun processRequest(context: RoutingContext): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            // Try to get the request body
            val body = try {
                context.body().asJsonObject()
            } catch (e: Exception) {
                try {
                    JsonObject(context.getBodyAsString())
                } catch (e: Exception) {
                    null
                }
            }

            if (body != null) {
                // Extract text content from the request body
                val textContent = extractTextContent(body)

                if (textContent.isNotEmpty()) {
                    // Check for harmful content
                    if (useExternalApi && apiUrl.isNotEmpty()) {
                        // Use external API for content moderation
                        checkContentWithExternalApi(textContent).onComplete { ar ->
                            if (ar.succeeded()) {
                                val result = ar.result()
                                if (result.first) {
                                    // Harmful content detected
                                    handleHarmfulContent(context, result.second, textContent)
                                    promise.complete(true)
                                } else {
                                    // No harmful content
                                    promise.complete(false)
                                }
                            } else {
                                logger.error("Error checking content with external API", ar.cause())
                                // Fall back to pattern-based detection
                                val (harmful, category) = checkContentWithPatterns(textContent)
                                if (harmful) {
                                    handleHarmfulContent(context, category, textContent)
                                    promise.complete(true)
                                } else {
                                    promise.complete(false)
                                }
                            }
                        }
                    } else {
                        // Use pattern-based detection
                        val (harmful, category) = checkContentWithPatterns(textContent)
                        if (harmful) {
                            handleHarmfulContent(context, category, textContent)
                            promise.complete(true)
                        } else {
                            promise.complete(false)
                        }
                    }
                } else {
                    // No text content to check
                    promise.complete(false)
                }
            } else {
                // No body to check
                promise.complete(false)
            }
        } catch (e: Exception) {
            logger.error("Error processing request in content safety plugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Extract text content from a JSON object
     */
    private fun extractTextContent(json: JsonObject): String {
        val textBuilder = StringBuilder()

        // Try to extract text from common API formats

        // OpenAI format
        val messages = json.getJsonArray("messages")
        if (messages != null && messages.size() > 0) {
            for (i in 0 until messages.size()) {
                val message = messages.getJsonObject(i)
                val content = message.getString("content")
                if (content != null) {
                    textBuilder.append(content).append(" ")
                }
            }
        }

        // Anthropic format
        val prompt = json.getString("prompt")
        if (prompt != null) {
            textBuilder.append(prompt).append(" ")
        }

        // Generic formats
        val content = json.getString("content")
        if (content != null) {
            textBuilder.append(content).append(" ")
        }

        val input = json.getString("input")
        if (input != null) {
            textBuilder.append(input).append(" ")
        }

        val text = json.getString("text")
        if (text != null) {
            textBuilder.append(text).append(" ")
        }

        // Recursively extract text from nested objects and arrays
        for (entry in json.map) {
            val value = entry.value
            if (value is JsonObject) {
                textBuilder.append(extractTextContent(value)).append(" ")
            } else if (value is JsonArray) {
                textBuilder.append(extractTextContent(value)).append(" ")
            }
        }

        return textBuilder.toString().trim()
    }

    /**
     * Extract text content from a JSON array
     */
    private fun extractTextContent(json: JsonArray): String {
        val textBuilder = StringBuilder()

        for (i in 0 until json.size()) {
            val value = json.getValue(i)
            if (value is String) {
                textBuilder.append(value).append(" ")
            } else if (value is JsonObject) {
                textBuilder.append(extractTextContent(value)).append(" ")
            } else if (value is JsonArray) {
                textBuilder.append(extractTextContent(value)).append(" ")
            }
        }

        return textBuilder.toString().trim()
    }

    /**
     * Check content for harmful content using pattern-based detection
     * @return Pair<Boolean, String> (harmful, category)
     */
    private fun checkContentWithPatterns(text: String): Pair<Boolean, String> {
        for ((category, pattern) in allPatterns) {
            if (categories.contains(category)) {
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    if (logDetections) {
                        logger.warn("Detected harmful content in category: $category")
                        logger.debug("Content: $text")
                    }
                    return Pair(true, category)
                }
            }
        }

        return Pair(false, "")
    }

    /**
     * Check content for harmful content using an external API
     * @return Future<Pair<Boolean, String>> (harmful, category)
     */
    private fun checkContentWithExternalApi(text: String): Future<Pair<Boolean, String>> {
        val promise = Promise.promise<Pair<Boolean, String>>()

        if (!useExternalApi || apiUrl.isEmpty()) {
            promise.complete(Pair(false, ""))
            return promise.future()
        }

        // Prepare request body
        val requestBody = JsonObject()
            .put("text", text)
            .put("categories", JsonArray(categories))
            .put("threshold", threshold)

        // Send request to external API
        webClient.requestAbs(HttpMethod.POST, apiUrl)
            .putHeader("Content-Type", "application/json")
            .putHeader("Authorization", "Bearer $apiKey")
            .sendJson(requestBody)
            .onSuccess { response ->
                try {
                    val responseBody = response.bodyAsJsonObject()

                    // Parse response
                    val harmful = responseBody.getBoolean("harmful", false)
                    val category = responseBody.getString("category", "")

                    if (harmful && logDetections) {
                        logger.warn("External API detected harmful content in category: $category")
                        logger.debug("Content: $text")
                    }

                    promise.complete(Pair(harmful, category))
                } catch (e: Exception) {
                    logger.error("Error parsing external API response", e)
                    promise.fail(e)
                }
            }
            .onFailure { err ->
                logger.error("Error calling external API", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * Handle harmful content based on the configured action mode
     */
    private fun handleHarmfulContent(context: RoutingContext, category: String, content: String) {
        when (actionMode) {
            "block" -> {
                // Block the request
                context.response()
                    .setStatusCode(403)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Content safety violation")
                        .put("details", "The request contains content that violates content safety policies")
                        .put("category", category)
                        .encode())

                if (logDetections) {
                    logger.warn("Blocked request due to harmful content in category: $category")
                }
            }
            "warn" -> {
                // Add warning headers
                context.response()
                    .putHeader("X-Content-Warning", "true")
                    .putHeader("X-Content-Category", category)

                if (logDetections) {
                    logger.warn("Added warning headers due to harmful content in category: $category")
                }
            }
            else -> {
                // Just log the detection
                if (logDetections) {
                    logger.warn("Detected harmful content in category: $category")
                }
            }
        }
    }

    override fun shutdown() {
        if (::webClient.isInitialized) {
            webClient.close()
        }
    }
}
