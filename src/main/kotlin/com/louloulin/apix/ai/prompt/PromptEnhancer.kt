package com.louloulin.apix.ai.prompt

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 提示词增强器，用于优化和增强 AI 请求中的提示词
 */
class PromptEnhancer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PromptEnhancer::class.java)
    
    // 提示词模板集合
    private val promptTemplates = mutableMapOf<String, PromptTemplate>()
    
    // 提示词增强规则集合
    private val enhancementRules = mutableListOf<EnhancementRule>()
    
    /**
     * 初始化提示词增强器
     */
    fun initialize(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 清空现有模板和规则
            promptTemplates.clear()
            enhancementRules.clear()
            
            // 加载提示词模板
            val templatesArray = config.getJsonArray("templates", JsonArray())
            for (i in 0 until templatesArray.size()) {
                val templateJson = templatesArray.getJsonObject(i)
                val template = PromptTemplate.fromJson(templateJson)
                promptTemplates[template.id] = template
            }
            
            // 加载增强规则
            val rulesArray = config.getJsonArray("rules", JsonArray())
            for (i in 0 until rulesArray.size()) {
                val ruleJson = rulesArray.getJsonObject(i)
                val rule = EnhancementRule.fromJson(ruleJson)
                enhancementRules.add(rule)
            }
            
            logger.info("初始化提示词增强器完成，加载了 ${promptTemplates.size} 个模板和 ${enhancementRules.size} 条规则")
            promise.complete()
        } catch (e: Exception) {
            logger.error("初始化提示词增强器失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 增强提示词
     */
    fun enhancePrompt(request: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 获取原始提示词
            val originalPrompt = request.getString("prompt", "")
            if (originalPrompt.isEmpty()) {
                // 如果没有提示词，直接返回原始请求
                promise.complete(request)
                return promise.future()
            }
            
            // 获取请求类型和模型
            val requestType = request.getString("type", "")
            val model = request.getString("model", "")
            
            // 应用增强规则
            var enhancedPrompt = originalPrompt
            for (rule in enhancementRules) {
                if (rule.matches(originalPrompt, requestType, model)) {
                    enhancedPrompt = rule.apply(enhancedPrompt)
                }
            }
            
            // 应用模板（如果指定了模板ID）
            val templateId = request.getString("templateId", "")
            if (templateId.isNotEmpty() && promptTemplates.containsKey(templateId)) {
                val template = promptTemplates[templateId]!!
                enhancedPrompt = template.apply(enhancedPrompt, request.getJsonObject("variables", JsonObject()))
            }
            
            // 创建增强后的请求
            val enhancedRequest = request.copy()
            enhancedRequest.put("prompt", enhancedPrompt)
            enhancedRequest.put("enhanced", true)
            
            promise.complete(enhancedRequest)
        } catch (e: Exception) {
            logger.error("增强提示词失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取所有提示词模板
     */
    fun getTemplates(): List<PromptTemplate> {
        return promptTemplates.values.toList()
    }
    
    /**
     * 获取指定ID的提示词模板
     */
    fun getTemplate(id: String): PromptTemplate? {
        return promptTemplates[id]
    }
    
    /**
     * 添加或更新提示词模板
     */
    fun addTemplate(template: PromptTemplate): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            promptTemplates[template.id] = template
            logger.info("添加提示词模板：${template.id}")
            promise.complete()
        } catch (e: Exception) {
            logger.error("添加提示词模板失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 删除提示词模板
     */
    fun removeTemplate(id: String): Future<Boolean> {
        val promise = Promise.promise<Boolean>()
        
        try {
            val removed = promptTemplates.remove(id) != null
            if (removed) {
                logger.info("删除提示词模板：$id")
            } else {
                logger.warn("提示词模板不存在：$id")
            }
            promise.complete(removed)
        } catch (e: Exception) {
            logger.error("删除提示词模板失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取所有增强规则
     */
    fun getRules(): List<EnhancementRule> {
        return enhancementRules.toList()
    }
    
    /**
     * 添加增强规则
     */
    fun addRule(rule: EnhancementRule): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 移除相同ID的规则（如果存在）
            enhancementRules.removeIf { it.id == rule.id }
            
            // 添加新规则
            enhancementRules.add(rule)
            
            // 按优先级排序
            enhancementRules.sortByDescending { it.priority }
            
            logger.info("添加增强规则：${rule.id}")
            promise.complete()
        } catch (e: Exception) {
            logger.error("添加增强规则失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 删除增强规则
     */
    fun removeRule(id: String): Future<Boolean> {
        val promise = Promise.promise<Boolean>()
        
        try {
            val removed = enhancementRules.removeIf { it.id == id }
            if (removed) {
                logger.info("删除增强规则：$id")
            } else {
                logger.warn("增强规则不存在：$id")
            }
            promise.complete(removed)
        } catch (e: Exception) {
            logger.error("删除增强规则失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 清空所有增强规则
     */
    fun clearRules(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            enhancementRules.clear()
            logger.info("清空所有增强规则")
            promise.complete()
        } catch (e: Exception) {
            logger.error("清空增强规则失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
}
