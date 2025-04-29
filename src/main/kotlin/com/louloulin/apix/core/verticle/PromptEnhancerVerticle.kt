package com.louloulin.apix.core.verticle

import com.louloulin.apix.ai.prompt.EnhancementRule
import com.louloulin.apix.ai.prompt.PromptEnhancer
import com.louloulin.apix.ai.prompt.PromptTemplate
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * 提示词增强 Verticle，负责处理提示词增强相关的请求
 */
class PromptEnhancerVerticle : BaseVerticle() {
    private lateinit var promptEnhancer: PromptEnhancer
    
    override fun registerEventBusHandlers() {
        // 提示词增强
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_ENHANCE, this::handleEnhancePrompt)
        
        // 模板管理
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_TEMPLATES_GET, this::handleGetTemplates)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_TEMPLATE_GET, this::handleGetTemplate)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_TEMPLATE_ADD, this::handleAddTemplate)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_TEMPLATE_REMOVE, this::handleRemoveTemplate)
        
        // 规则管理
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_RULES_GET, this::handleGetRules)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_RULE_ADD, this::handleAddRule)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_RULE_REMOVE, this::handleRemoveRule)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_PROMPT_RULES_CLEAR, this::handleClearRules)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        // 初始化提示词增强器
        promptEnhancer = PromptEnhancer(vertx)
        
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject().put("section", "ai.promptEnhancer")) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // 初始化提示词增强器
                    promptEnhancer.initialize(config)
                        .onSuccess {
                            logger.info("PromptEnhancerVerticle 启动成功")
                            startPromise.complete()
                        }
                        .onFailure { err ->
                            logger.error("初始化提示词增强器失败", err)
                            startPromise.fail(err)
                        }
                } else {
                    // 如果配置不存在，使用空配置初始化
                    promptEnhancer.initialize(JsonObject())
                        .onSuccess {
                            logger.info("PromptEnhancerVerticle 使用默认配置启动成功")
                            startPromise.complete()
                        }
                        .onFailure { err ->
                            logger.error("初始化提示词增强器失败", err)
                            startPromise.fail(err)
                        }
                }
            } else {
                // 如果获取配置失败，使用空配置初始化
                promptEnhancer.initialize(JsonObject())
                    .onSuccess {
                        logger.info("PromptEnhancerVerticle 使用默认配置启动成功")
                        startPromise.complete()
                    }
                    .onFailure { err ->
                        logger.error("初始化提示词增强器失败", err)
                        startPromise.fail(err)
                    }
            }
        }
    }
    
    /**
     * 处理提示词增强请求
     */
    private fun handleEnhancePrompt(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        
        promptEnhancer.enhancePrompt(request)
            .onSuccess { enhancedRequest ->
                sendSuccess(message, enhancedRequest)
            }
            .onFailure { err ->
                sendError(message, err)
            }
    }
    
    /**
     * 处理获取所有模板请求
     */
    private fun handleGetTemplates(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val templates = promptEnhancer.getTemplates()
        val templatesArray = JsonArray()
        
        templates.forEach { template ->
            templatesArray.add(template.toJson())
        }
        
        sendSuccess(message, templatesArray)
    }
    
    /**
     * 处理获取单个模板请求
     */
    private fun handleGetTemplate(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val templateId = message.body().getString("id")
        
        if (templateId == null) {
            sendError(message, 400, "Template ID is required")
            return
        }
        
        val template = promptEnhancer.getTemplate(templateId)
        if (template != null) {
            sendSuccess(message, template.toJson())
        } else {
            sendError(message, 404, "Template not found: $templateId")
        }
    }
    
    /**
     * 处理添加模板请求
     */
    private fun handleAddTemplate(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val templateJson = message.body().getJsonObject("template")
        
        if (templateJson == null) {
            sendError(message, 400, "Template is required")
            return
        }
        
        try {
            val template = PromptTemplate.fromJson(templateJson)
            
            promptEnhancer.addTemplate(template)
                .onSuccess {
                    sendSuccess(message, template.toJson())
                }
                .onFailure { err ->
                    sendError(message, err)
                }
        } catch (e: Exception) {
            sendError(message, 400, "Invalid template: ${e.message}")
        }
    }
    
    /**
     * 处理删除模板请求
     */
    private fun handleRemoveTemplate(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val templateId = message.body().getString("id")
        
        if (templateId == null) {
            sendError(message, 400, "Template ID is required")
            return
        }
        
        promptEnhancer.removeTemplate(templateId)
            .onSuccess { removed ->
                sendSuccess(message, JsonObject().put("removed", removed))
            }
            .onFailure { err ->
                sendError(message, err)
            }
    }
    
    /**
     * 处理获取所有规则请求
     */
    private fun handleGetRules(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val rules = promptEnhancer.getRules()
        val rulesArray = JsonArray()
        
        rules.forEach { rule ->
            rulesArray.add(rule.toJson())
        }
        
        sendSuccess(message, rulesArray)
    }
    
    /**
     * 处理添加规则请求
     */
    private fun handleAddRule(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val ruleJson = message.body().getJsonObject("rule")
        
        if (ruleJson == null) {
            sendError(message, 400, "Rule is required")
            return
        }
        
        try {
            val rule = EnhancementRule.fromJson(ruleJson)
            
            promptEnhancer.addRule(rule)
                .onSuccess {
                    sendSuccess(message, rule.toJson())
                }
                .onFailure { err ->
                    sendError(message, err)
                }
        } catch (e: Exception) {
            sendError(message, 400, "Invalid rule: ${e.message}")
        }
    }
    
    /**
     * 处理删除规则请求
     */
    private fun handleRemoveRule(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val ruleId = message.body().getString("id")
        
        if (ruleId == null) {
            sendError(message, 400, "Rule ID is required")
            return
        }
        
        promptEnhancer.removeRule(ruleId)
            .onSuccess { removed ->
                sendSuccess(message, JsonObject().put("removed", removed))
            }
            .onFailure { err ->
                sendError(message, err)
            }
    }
    
    /**
     * 处理清空所有规则请求
     */
    private fun handleClearRules(message: io.vertx.core.eventbus.Message<JsonObject>) {
        promptEnhancer.clearRules()
            .onSuccess {
                sendSuccess(message, true)
            }
            .onFailure { err ->
                sendError(message, err)
            }
    }
}
