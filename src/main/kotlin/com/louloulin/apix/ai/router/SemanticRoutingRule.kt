package com.louloulin.apix.ai.router

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.UUID

/**
 * 语义路由规则，基于内容的语义相似度进行匹配。
 */
data class SemanticRoutingRule(
    val id: String,
    val name: String,
    val priority: Int,
    val pattern: String,
    val examples: List<String>,
    val targetModel: String,
    val contentTypes: List<String> = listOf(),
    val requestTypes: List<String> = listOf()
) {
    /**
     * 转换为JSON对象。
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("name", name)
            .put("priority", priority)
            .put("pattern", pattern)
            .put("examples", JsonArray(examples))
            .put("targetModel", targetModel)
            .put("contentTypes", JsonArray(contentTypes))
            .put("requestTypes", JsonArray(requestTypes))
    }
    
    companion object {
        /**
         * 从JSON对象创建语义路由规则。
         */
        fun fromJson(json: JsonObject): SemanticRoutingRule {
            val id = json.getString("id", UUID.randomUUID().toString())
            val name = json.getString("name", "未命名规则")
            val priority = json.getInteger("priority", 0)
            val pattern = json.getString("pattern", "")
            
            val examplesArray = json.getJsonArray("examples", JsonArray())
            val examples = (0 until examplesArray.size()).map { examplesArray.getString(it) }
            
            val targetModel = json.getString("targetModel", "gpt-3.5-turbo")
            
            val contentTypesArray = json.getJsonArray("contentTypes", JsonArray())
            val contentTypes = (0 until contentTypesArray.size()).map { contentTypesArray.getString(it) }
            
            val requestTypesArray = json.getJsonArray("requestTypes", JsonArray())
            val requestTypes = (0 until requestTypesArray.size()).map { requestTypesArray.getString(it) }
            
            return SemanticRoutingRule(
                id = id,
                name = name,
                priority = priority,
                pattern = pattern,
                examples = examples,
                targetModel = targetModel,
                contentTypes = contentTypes,
                requestTypes = requestTypes
            )
        }
    }
}
