package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * Plugin that protects against prompt injection attacks.
 *
 * This plugin:
 * 1. Detects potential prompt injection patterns
 * 2. Sanitizes prompts to remove dangerous content
 * 3. Validates prompts against predefined templates
 */
class PromptGuardPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(PromptGuardPlugin::class.java)

    override val type: String = "prompt-guard"

    // Configuration values with defaults
    private val enabled: Boolean = config.getBoolean("enabled", true) ?: true
    private val blockOnDetection: Boolean = config.getBoolean("block_on_detection", true) ?: true
    private val sanitizePrompt: Boolean = config.getBoolean("sanitize_prompt", true) ?: true
    private val logDetections: Boolean = config.getBoolean("log_detections", true) ?: true
    private val validateTemplates: Boolean = config.getBoolean("validate_templates", false) ?: false

    // Custom injection patterns from configuration
    private val customPatterns: List<Pattern> = config.getJsonArray("custom_patterns")
        ?.map { Pattern.compile(it.toString(), Pattern.CASE_INSENSITIVE) }
        ?: emptyList()

    // Default injection patterns
    private val defaultPatterns = listOf(
        // System prompt hijacking attempts
        Pattern.compile("ignore previous instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ignore all previous commands", Pattern.CASE_INSENSITIVE),
        Pattern.compile("ignore above instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("disregard previous instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("forget your instructions", Pattern.CASE_INSENSITIVE),

        // Delimiter attacks
        Pattern.compile("system:\\s*", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\<\\/?system\\>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[system\\]", Pattern.CASE_INSENSITIVE),

        // Role switching attempts
        Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("now you are", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you're no longer", Pattern.CASE_INSENSITIVE),
        Pattern.compile("your new role", Pattern.CASE_INSENSITIVE),

        // Command injection
        Pattern.compile("\\$\\{", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\{\\{", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\{\\%", Pattern.CASE_INSENSITIVE)
    )

    // Combined patterns
    private val allPatterns = defaultPatterns + customPatterns

    // Allowed templates
    private val allowedTemplates: List<String> = config.getJsonArray("allowed_templates")
        ?.map { it.toString() }
        ?: emptyList()

    /**
     * Initialize the plugin
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            logger.info("Initializing PromptGuardPlugin with ${allPatterns.size} detection patterns")
            if (validateTemplates) {
                logger.info("Template validation enabled with ${allowedTemplates.size} templates")
            }
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to initialize PromptGuardPlugin", e)
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
            // Get the request body
            // Try to get the body using different methods depending on Vert.x version
            val body = try {
                context.body().asJsonObject()
            } catch (e: Exception) {
                try {
                    JsonObject(context.getBodyAsString())
                } catch (e: Exception) {
                    null
                }
            }

            if (body == null) {
                // No body to validate
                promise.complete()
                return promise.future()
            }

            // Extract prompt based on the API format
            val promptInfo = extractPrompt(body)

            if (promptInfo.first != null) {
                val (prompt, format, path) = promptInfo

                // Check for injection patterns
                val detectedPatterns = detectInjectionPatterns(prompt!!)

                if (detectedPatterns.isNotEmpty()) {
                    // Injection detected
                    if (logDetections) {
                        logger.warn("Prompt injection detected: ${detectedPatterns.joinToString(", ")}")
                        logger.debug("Original prompt: $prompt")
                    }

                    if (blockOnDetection) {
                        // Block the request
                        context.response()
                            .setStatusCode(400)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("error", "Potential prompt injection detected")
                                .put("details", "The request contains patterns that may be attempting to manipulate the AI system")
                                .encode())
                        promise.complete() // Complete the promise to stop the chain
                        return promise.future()
                    } else if (sanitizePrompt) {
                        // Sanitize the prompt
                        val sanitizedPrompt = sanitizePrompt(prompt, detectedPatterns)

                        // Update the request body with the sanitized prompt
                        when (format) {
                            "openai" -> {
                                val messages = body.getJsonArray("messages")
                                val messageObj = messages.getJsonObject(path.toInt())
                                messageObj.put("content", sanitizedPrompt)
                            }
                            "anthropic" -> {
                                body.put("prompt", sanitizedPrompt)
                            }
                            "text" -> {
                                body.put(path, sanitizedPrompt)
                            }
                        }

                        // Update the request body
                        context.setBody(body.toBuffer())
                    }
                }

                // Validate against templates if enabled
                if (validateTemplates && allowedTemplates.isNotEmpty()) {
                    val isValid = validateAgainstTemplates(prompt)

                    if (!isValid) {
                        // Template validation failed
                        context.response()
                            .setStatusCode(400)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("error", "Template validation failed")
                                .put("details", "The prompt does not match any of the allowed templates")
                                .encode())
                        promise.complete() // Complete the promise to stop the chain
                        return promise.future()
                    }
                }
            }

            // Continue with the request
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing prompt guard plugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Extracts the prompt from the request body based on the API format.
     * Returns a triple of (prompt, format, path) where:
     * - prompt is the extracted prompt text
     * - format is the API format (openai, anthropic, text)
     * - path is the path to the prompt in the request body
     */
    private fun extractPrompt(jsonBody: JsonObject): Triple<String?, String, String> {
        // Try OpenAI format
        val messages = jsonBody.getJsonArray("messages")
        if (messages != null && messages.size() > 0) {
            // Get the last user message
            for (i in messages.size() - 1 downTo 0) {
                val message = messages.getJsonObject(i)
                if (message.getString("role") == "user") {
                    return Triple(message.getString("content"), "openai", i.toString())
                }
            }
        }

        // Try Anthropic format
        val prompt = jsonBody.getString("prompt")
        if (prompt != null) {
            return Triple(prompt, "anthropic", "prompt")
        }

        // Try content field (generic)
        val content = jsonBody.getString("content")
        if (content != null) {
            return Triple(content, "text", "content")
        }

        // Try input field
        val input = jsonBody.getString("input")
        if (input != null) {
            return Triple(input, "text", "input")
        }

        // Try text field
        val text = jsonBody.getString("text")
        if (text != null) {
            return Triple(text, "text", "text")
        }

        return Triple(null, "unknown", "")
    }

    /**
     * Detects injection patterns in the prompt.
     * Returns a list of detected patterns.
     */
    private fun detectInjectionPatterns(prompt: String): List<String> {
        val detectedPatterns = mutableListOf<String>()

        for (pattern in allPatterns) {
            val matcher = pattern.matcher(prompt)
            if (matcher.find()) {
                detectedPatterns.add(pattern.pattern())
            }
        }

        return detectedPatterns
    }

    /**
     * Sanitizes the prompt by removing or replacing detected patterns.
     */
    private fun sanitizePrompt(prompt: String, detectedPatterns: List<String>): String {
        var sanitized = prompt

        for (patternStr in detectedPatterns) {
            val pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE)
            sanitized = pattern.matcher(sanitized).replaceAll("[REMOVED]")
        }

        return sanitized
    }

    /**
     * Validates the prompt against allowed templates.
     */
    private fun validateAgainstTemplates(prompt: String): Boolean {
        if (allowedTemplates.isEmpty()) {
            return true
        }

        for (template in allowedTemplates) {
            val templatePattern = templateToRegex(template)
            if (templatePattern.matcher(prompt).matches()) {
                return true
            }
        }

        return false
    }

    /**
     * Converts a template with placeholders to a regex pattern.
     * Placeholders are in the format {{name}}.
     */
    private fun templateToRegex(template: String): Pattern {
        // Replace placeholders with regex patterns
        val regex = template.replace(Regex("\\{\\{\\w+\\}\\}"), "(.+?)")

        // Escape regex special characters except for the placeholder replacements
        val escapedRegex = regex.replace(Regex("([\\[\\]\\(\\)\\{\\}\\*\\+\\?\\^\\$\\\\\\|\\-])"), "\\\\$1")
            .replace("\\\\(.+?)", "(.+?)")

        return Pattern.compile("^$escapedRegex$", Pattern.DOTALL)
    }

    override fun shutdown() {
        // No resources to clean up
    }
}
