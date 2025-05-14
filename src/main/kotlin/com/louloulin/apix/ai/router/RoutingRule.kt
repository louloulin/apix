package com.louloulin.apix.ai.router

import io.vertx.core.json.JsonObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * Rule for routing AI requests to specific models.
 */
data class RoutingRule(
    val id: String,
    val name: String,
    val priority: Int,
    val condition: RuleCondition,
    val targetModel: String
) {
    /**
     * Check if this rule matches the given request.
     */
    fun matches(content: String, contentType: String, requestType: String): Boolean {
        return condition.matches(content, contentType, requestType)
    }

    /**
     * Convert to JSON.
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("name", name)
            .put("priority", priority)
            .put("condition", condition.toJson())
            .put("targetModel", targetModel)
    }

    companion object {
        /**
         * Create from JSON.
         */
        fun fromJson(json: JsonObject): RoutingRule {
            val id = json.getString("id", UUID.randomUUID().toString())
            val name = json.getString("name", "Unnamed Rule")
            val priority = json.getInteger("priority", 0)
            val conditionJson = json.getJsonObject("condition", JsonObject())
            val condition = RuleCondition.fromJson(conditionJson)
            val targetModel = json.getString("targetModel", "gpt-3.5-turbo")
            // Support for Cohere models
            val finalTargetModel = if (targetModel.startsWith("cohere:")) {
                targetModel
            } else {
                targetModel
            }

            return RoutingRule(id, name, priority, condition, finalTargetModel)
        }
    }
}

/**
 * Condition for a routing rule.
 */
data class RuleCondition(
    val type: ConditionType,
    val pattern: String,
    val contentTypes: List<String> = listOf(),
    val requestTypes: List<String> = listOf()
) {
    // Compiled pattern for regex conditions
    private val compiledPattern: Pattern? = if (type == ConditionType.REGEX) Pattern.compile(pattern) else null

    /**
     * Check if this condition matches the given request.
     */
    fun matches(content: String, contentType: String, requestType: String): Boolean {
        // Check content type and request type first
        if (contentTypes.isNotEmpty() && !contentTypes.contains(contentType)) {
            return false
        }

        if (requestTypes.isNotEmpty() && !requestTypes.contains(requestType)) {
            return false
        }

        // Check content pattern
        return when (type) {
            ConditionType.CONTAINS -> content.contains(pattern)
            ConditionType.STARTS_WITH -> content.startsWith(pattern)
            ConditionType.ENDS_WITH -> content.endsWith(pattern)
            ConditionType.REGEX -> compiledPattern?.matcher(content)?.find() ?: false
            ConditionType.TOKEN_COUNT -> {
                val tokenCount = estimateTokenCount(content)
                val threshold = pattern.toIntOrNull() ?: 0
                tokenCount >= threshold
            }
            ConditionType.LANGUAGE -> detectLanguage(content) == pattern
        }
    }

    /**
     * Estimate token count for a string.
     * This is a simple approximation - in production, use a proper tokenizer.
     */
    private fun estimateTokenCount(text: String): Int {
        // Simple approximation: 1 token ≈ 4 characters
        return text.length / 4
    }

    /**
     * Detect language of a string.
     * This is a simple implementation - in production, use a proper language detector.
     */
    private fun detectLanguage(text: String): String {
        // Simple language detection based on character frequency
        val chineseChars = text.count { it.code in 0x4E00..0x9FFF }
        val japaneseChars = text.count { it.code in 0x3040..0x309F || it.code in 0x30A0..0x30FF }
        val koreanChars = text.count { it.code in 0xAC00..0xD7A3 }

        return when {
            chineseChars > text.length * 0.1 -> "zh"
            japaneseChars > text.length * 0.1 -> "ja"
            koreanChars > text.length * 0.1 -> "ko"
            // Add more language detection logic as needed
            else -> "en" // Default to English
        }
    }

    /**
     * Convert to JSON.
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("type", type.name)
            .put("pattern", pattern)
            .put("contentTypes", contentTypes)
            .put("requestTypes", requestTypes)
    }

    companion object {
        /**
         * Create from JSON.
         */
        fun fromJson(json: JsonObject): RuleCondition {
            val typeStr = json.getString("type", ConditionType.CONTAINS.name)
            val type = try {
                ConditionType.valueOf(typeStr)
            } catch (e: IllegalArgumentException) {
                ConditionType.CONTAINS
            }

            val pattern = json.getString("pattern", "")

            val contentTypesArray = json.getJsonArray("contentTypes")
            val contentTypes = if (contentTypesArray != null) {
                (0 until contentTypesArray.size()).map { contentTypesArray.getString(it) }
            } else {
                listOf()
            }

            val requestTypesArray = json.getJsonArray("requestTypes")
            val requestTypes = if (requestTypesArray != null) {
                (0 until requestTypesArray.size()).map { requestTypesArray.getString(it) }
            } else {
                listOf()
            }

            return RuleCondition(type, pattern, contentTypes, requestTypes)
        }
    }
}

/**
 * Types of conditions for routing rules.
 */
enum class ConditionType {
    CONTAINS,
    STARTS_WITH,
    ENDS_WITH,
    REGEX,
    TOKEN_COUNT,
    LANGUAGE
}
