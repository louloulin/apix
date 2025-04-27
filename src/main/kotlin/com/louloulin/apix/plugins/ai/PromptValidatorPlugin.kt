package com.louloulin.apix.plugins.ai

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * Plugin that validates AI prompts.
 */
class PromptValidatorPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(PromptValidatorPlugin::class.java)
    
    override val type: String = "prompt-validator"
    
    // Configuration values
    private val maxLength: Int = config.getInteger("max_length", 4000) ?: 4000
    private val validateJson: Boolean = config.getBoolean("validate_json", true) ?: true
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // Get the request body
            val body = context.body().buffer()
            
            if (body == null) {
                // No body to validate
                promise.complete()
                return promise.future()
            }
            
            // Validate JSON format if required
            if (validateJson) {
                try {
                    val jsonBody = body.toJsonObject()
                    
                    // Extract prompt based on the API (OpenAI, Anthropic, etc.)
                    val prompt = extractPrompt(jsonBody)
                    
                    if (prompt != null && prompt.length > maxLength) {
                        // Prompt is too long
                        logger.debug("Prompt exceeds maximum length: {}", prompt.length)
                        context.response()
                            .setStatusCode(400)
                            .putHeader("Content-Type", "application/json")
                            .end("""{"error": "Prompt exceeds maximum length of $maxLength characters"}""")
                        promise.complete() // Complete the promise to stop the chain
                        return promise.future()
                    }
                } catch (e: DecodeException) {
                    // Invalid JSON
                    logger.debug("Invalid JSON in request body")
                    context.response()
                        .setStatusCode(400)
                        .putHeader("Content-Type", "application/json")
                        .end("""{"error": "Invalid JSON in request body"}""")
                    promise.complete() // Complete the promise to stop the chain
                    return promise.future()
                }
            }
            
            // Validation passed
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing prompt validator plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * Extracts the prompt from the request body based on the API format.
     */
    private fun extractPrompt(jsonBody: JsonObject): String? {
        // Try OpenAI format
        val messages = jsonBody.getJsonArray("messages")
        if (messages != null) {
            // Concatenate all message content
            return messages.map { 
                (it as? JsonObject)?.getString("content") ?: ""
            }.joinToString(" ")
        }
        
        // Try Anthropic format
        val prompt = jsonBody.getString("prompt")
        if (prompt != null) {
            return prompt
        }
        
        // Try content field (generic)
        val content = jsonBody.getString("content")
        if (content != null) {
            return content
        }
        
        return null
    }
    
    override fun shutdown() {
        // No resources to clean up
    }
    
    /**
     * Extension function to convert a Buffer to a JsonObject.
     */
    private fun Buffer.toJsonObject(): JsonObject {
        return JsonObject(this.toString())
    }
}
