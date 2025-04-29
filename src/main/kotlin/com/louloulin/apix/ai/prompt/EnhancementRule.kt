package com.louloulin.apix.ai.prompt

import io.vertx.core.json.JsonObject
import java.util.UUID
import java.util.regex.Pattern

/**
 * 提示词增强规则类型
 */
enum class RuleType {
    PREPEND,       // 在提示词前添加内容
    APPEND,        // 在提示词后添加内容
    REPLACE,       // 替换提示词中的内容
    REGEX_REPLACE, // 使用正则表达式替换提示词中的内容
    TRANSFORM      // 转换提示词（如格式化、结构化等）
}

/**
 * 提示词增强规则，用于修改和优化提示词
 */
class EnhancementRule(
    val id: String,
    val name: String,
    val description: String,
    val type: RuleType,
    val pattern: String,
    val replacement: String,
    val priority: Int = 0,
    val requestTypes: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    /**
     * 检查规则是否匹配
     */
    fun matches(prompt: String, requestType: String, model: String): Boolean {
        if (!enabled) {
            return false
        }

        // 检查请求类型
        if (requestTypes.isNotEmpty() && !requestTypes.contains(requestType)) {
            return false
        }

        // 检查模型
        if (models.isNotEmpty() && !models.contains(model)) {
            return false
        }

        // 对于 REPLACE 和 REGEX_REPLACE 类型，检查模式是否匹配
        return when (type) {
            RuleType.REPLACE -> prompt.contains(pattern)
            RuleType.REGEX_REPLACE -> Pattern.compile(pattern).matcher(prompt).find()
            else -> true
        }
    }

    /**
     * 应用规则，修改提示词
     */
    fun apply(prompt: String): String {
        return when (type) {
            RuleType.PREPEND -> "$replacement$prompt"
            RuleType.APPEND -> "$prompt$replacement"
            RuleType.REPLACE -> prompt.replace(pattern, replacement)
            RuleType.REGEX_REPLACE -> Pattern.compile(pattern).matcher(prompt).replaceAll(replacement)
            RuleType.TRANSFORM -> transform(prompt)
        }
    }

    /**
     * 转换提示词（可以根据需要扩展）
     */
    private fun transform(prompt: String): String {
        // 这里可以实现更复杂的转换逻辑
        // 目前简单地返回替换后的内容
        return replacement.replace("{{content}}", prompt)
    }

    /**
     * 转换为 JSON 对象
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("name", name)
            .put("description", description)
            .put("type", type.name)
            .put("pattern", pattern)
            .put("replacement", replacement)
            .put("priority", priority)
            .put("requestTypes", requestTypes)
            .put("models", models)
            .put("enabled", enabled)
    }

    companion object {
        /**
         * 从 JSON 对象创建规则
         */
        fun fromJson(json: JsonObject): EnhancementRule {
            val id = json.getString("id", UUID.randomUUID().toString())
            val name = json.getString("name", "Unnamed Rule")
            val description = json.getString("description", "")
            val typeStr = json.getString("type", RuleType.APPEND.name)
            val type = try {
                RuleType.valueOf(typeStr)
            } catch (e: Exception) {
                RuleType.APPEND
            }
            val pattern = json.getString("pattern", "")
            val replacement = json.getString("replacement", "")
            val priority = json.getInteger("priority", 0)
            val requestTypes = json.getJsonArray("requestTypes", io.vertx.core.json.JsonArray()).map { it.toString() }
            val models = json.getJsonArray("models", io.vertx.core.json.JsonArray()).map { it.toString() }
            val enabled = json.getBoolean("enabled", true)

            return EnhancementRule(id, name, description, type, pattern, replacement, priority, requestTypes, models, enabled)
        }
    }
}
