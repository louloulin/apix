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
 * Plugin that detects and filters personally identifiable information (PII) from requests and responses.
 *
 * This plugin:
 * 1. Detects PII data in requests and responses
 * 2. Masks or removes PII data based on configuration
 * 3. Supports custom PII detection rules
 */
class PIIFilterPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(PIIFilterPlugin::class.java)

    override val type: String = "pii-filter"

    // Configuration values with defaults
    private val enabled: Boolean = config.getBoolean("enabled", true) ?: true
    private val filterRequests: Boolean = config.getBoolean("filter_requests", true) ?: true
    private val filterResponses: Boolean = config.getBoolean("filter_responses", true) ?: true
    private val maskMode: String = config.getString("mask_mode", "partial") ?: "partial"
    private val logDetections: Boolean = config.getBoolean("log_detections", true) ?: true

    // Custom PII patterns from configuration
    private val customPatterns: Map<String, Pattern> = config.getJsonObject("custom_patterns")
        ?.map { it.key to Pattern.compile(it.value.toString(), Pattern.CASE_INSENSITIVE) }
        ?.toMap()
        ?: emptyMap()

    // Default PII patterns
    private val defaultPatterns = mapOf(
        // Email addresses
        "email" to Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),

        // Phone numbers (various formats)
        "phone" to Pattern.compile("\\b(\\+\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b"),

        // Social Security Numbers (US)
        "ssn" to Pattern.compile("\\b\\d{3}[-]?\\d{2}[-]?\\d{4}\\b"),

        // Credit card numbers
        "credit_card" to Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b"),

        // IP addresses
        "ip_address" to Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),

        // Dates (various formats)
        "date" to Pattern.compile("\\b\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}\\b"),

        // Addresses (simplified)
        "address" to Pattern.compile("\\b\\d+\\s+[A-Za-z0-9\\s,]+\\b(?:street|st|avenue|ave|road|rd|boulevard|blvd|lane|ln|drive|dr)\\b", Pattern.CASE_INSENSITIVE),

        // Passport numbers (simplified)
        "passport" to Pattern.compile("\\b[A-Z]{1,2}\\d{6,9}\\b")
    )

    // Combined patterns
    private val allPatterns: Map<String, Pattern> = defaultPatterns + customPatterns

    // Excluded patterns (patterns to ignore)
    private val excludedPatterns: List<Pattern> = config.getJsonArray("excluded_patterns")
        ?.map { Pattern.compile(it.toString(), Pattern.CASE_INSENSITIVE) }
        ?: emptyList()

    /**
     * Initialize the plugin
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            logger.info("Initializing PIIFilterPlugin with ${allPatterns.size} detection patterns")
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to initialize PIIFilterPlugin", e)
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
            if (filterRequests) {
                processRequest(context)
            }

            // Add response handler if response filtering is enabled
            if (filterResponses) {
                // In a real implementation, we would add a response handler here
                // For simplicity, we'll just log that we would process the response
                logger.info("Response filtering would be set up here in a real implementation")
            }

            // Continue with the request
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing PII filter plugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * Process the request to filter PII data
     */
    private fun processRequest(context: RoutingContext) {
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
            // Filter PII in the request body
            val filteredBody = filterPII(body)

            // Update the request body if changes were made
            if (filteredBody != body) {
                context.setBody(filteredBody.toBuffer())
            }
        }
    }

    /**
     * Process the response to filter PII data
     */
    private fun processResponse(context: RoutingContext) {
        // This is a simplified implementation since we can't easily intercept the response body
        // In a real implementation, we would need to use a response handler or a custom writer
        // For now, we'll just log that we would process the response
        logger.info("Response processing would happen here in a real implementation")

        // Note: In a real implementation, we would:
        // 1. Get the response body
        // 2. Parse it as JSON if applicable
        // 3. Filter PII in the response body
        // 4. Update the response body
    }

    /**
     * Filter PII data in a JSON object
     */
    private fun filterPII(json: JsonObject): JsonObject {
        val result = JsonObject()

        for (entry in json.map) {
            val key = entry.key
            val value = entry.value

            when (value) {
                is String -> {
                    result.put(key, filterPIIInString(value))
                }
                is JsonObject -> {
                    result.put(key, filterPII(value))
                }
                is JsonArray -> {
                    result.put(key, filterPII(value))
                }
                else -> {
                    result.put(key, value)
                }
            }
        }

        return result
    }

    /**
     * Filter PII data in a JSON array
     */
    private fun filterPII(json: JsonArray): JsonArray {
        val result = JsonArray()

        for (i in 0 until json.size()) {
            val value = json.getValue(i)

            when (value) {
                is String -> {
                    result.add(filterPIIInString(value))
                }
                is JsonObject -> {
                    result.add(filterPII(value))
                }
                is JsonArray -> {
                    result.add(filterPII(value))
                }
                else -> {
                    result.add(value)
                }
            }
        }

        return result
    }

    /**
     * Filter PII data in a string
     */
    private fun filterPIIInString(text: String): String {
        var result = text

        // Check if the string should be excluded
        for (pattern in excludedPatterns) {
            if (pattern.matcher(text).matches()) {
                return text
            }
        }

        // Apply all PII patterns
        for ((type, pattern) in allPatterns) {
            val matcher = pattern.matcher(result)
            val sb = StringBuffer()

            var found = false
            while (matcher.find()) {
                found = true
                val match = matcher.group()
                val masked = maskPII(match, type)
                matcher.appendReplacement(sb, masked)
            }

            if (found) {
                matcher.appendTail(sb)
                result = sb.toString()

                if (logDetections) {
                    logger.info("Detected and masked PII of type: $type")
                }
            }
        }

        return result
    }

    /**
     * Mask PII data based on the configured mask mode
     */
    private fun maskPII(text: String, type: String): String {
        return when (maskMode) {
            "full" -> "[REDACTED]"
            "type" -> "[$type]"
            "partial" -> {
                when (type) {
                    "email" -> {
                        val atIndex = text.indexOf('@')
                        if (atIndex > 1) {
                            val username = text.substring(0, atIndex)
                            val domain = text.substring(atIndex)
                            "${username.first()}${"*".repeat(username.length - 2)}${username.last()}$domain"
                        } else {
                            "****@${text.substringAfter('@')}"
                        }
                    }
                    "phone" -> {
                        val digits = text.filter { it.isDigit() }
                        if (digits.length >= 4) {
                            "****${digits.takeLast(4)}"
                        } else {
                            "****"
                        }
                    }
                    "credit_card" -> {
                        val digits = text.filter { it.isDigit() }
                        if (digits.length >= 4) {
                            "****${digits.takeLast(4)}"
                        } else {
                            "****"
                        }
                    }
                    "ssn" -> {
                        val digits = text.filter { it.isDigit() }
                        if (digits.length >= 4) {
                            "***-**-${digits.takeLast(4)}"
                        } else {
                            "***-**-****"
                        }
                    }
                    else -> {
                        val visible = (text.length * 0.25).toInt().coerceAtLeast(1)
                        val prefix = text.take(visible)
                        val suffix = if (text.length > visible * 2) text.takeLast(visible) else ""
                        val maskedLength = text.length - prefix.length - suffix.length
                        "$prefix${"*".repeat(maskedLength)}$suffix"
                    }
                }
            }
            "remove" -> ""
            else -> "[REDACTED]"
        }
    }

    override fun shutdown() {
        // No resources to clean up
    }
}
