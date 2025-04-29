package com.louloulin.apix.ai.prompt

import io.vertx.core.json.JsonObject
import java.util.UUID

/**
 * 提示词模板，用于格式化和结构化提示词
 */
class PromptTemplate(
    val id: String,
    val name: String,
    val description: String,
    val template: String,
    val variables: List<String> = emptyList(),
    val category: String = "general",
    val tags: List<String> = emptyList()
) {
    /**
     * 应用模板，将变量替换为实际值
     */
    fun apply(content: String, variables: JsonObject): String {
        var result = template
        
        // 替换内容变量
        result = result.replace("{{content}}", content)
        
        // 替换其他变量
        for (key in variables.fieldNames()) {
            val value = variables.getString(key, "")
            result = result.replace("{{$key}}", value)
        }
        
        return result
    }
    
    /**
     * 转换为 JSON 对象
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("name", name)
            .put("description", description)
            .put("template", template)
            .put("variables", variables)
            .put("category", category)
            .put("tags", tags)
    }
    
    companion object {
        /**
         * 从 JSON 对象创建模板
         */
        fun fromJson(json: JsonObject): PromptTemplate {
            val id = json.getString("id", UUID.randomUUID().toString())
            val name = json.getString("name", "Unnamed Template")
            val description = json.getString("description", "")
            val template = json.getString("template", "{{content}}")
            val variables = json.getJsonArray("variables", io.vertx.core.json.JsonArray()).map { it.toString() }
            val category = json.getString("category", "general")
            val tags = json.getJsonArray("tags", io.vertx.core.json.JsonArray()).map { it.toString() }
            
            return PromptTemplate(id, name, description, template, variables, category, tags)
        }
    }
}
