package com.louloulin.apix.ai.router

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Service for routing AI requests to appropriate models based on content analysis.
 */
class ModelRouter(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ModelRouter::class.java)
    
    // Routing rules
    private val routingRules = mutableListOf<RoutingRule>()
    
    /**
     * Initialize the model router with configuration.
     */
    fun initialize(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // Clear existing rules
            routingRules.clear()
            
            // Load routing rules from configuration
            val rulesArray = config.getJsonArray("rules", JsonArray())
            for (i in 0 until rulesArray.size()) {
                val ruleJson = rulesArray.getJsonObject(i)
                val rule = RoutingRule.fromJson(ruleJson)
                routingRules.add(rule)
            }
            
            logger.info("Initialized model router with ${routingRules.size} rules")
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to initialize model router", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * Route a request to the appropriate model based on content analysis.
     */
    fun routeRequest(request: JsonObject): Future<String> {
        val promise = Promise.promise<String>()
        
        try {
            // Extract request content
            val content = request.getString("content", "")
            val contentType = request.getString("contentType", "text")
            val requestType = request.getString("requestType", "completion")
            val defaultModel = request.getString("defaultModel", "gpt-3.5-turbo")
            
            // Apply routing rules
            val selectedModel = findMatchingModel(content, contentType, requestType, defaultModel)
            
            logger.debug("Routed request to model: $selectedModel")
            promise.complete(selectedModel)
        } catch (e: Exception) {
            logger.error("Failed to route request", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * Find a matching model based on routing rules.
     */
    private fun findMatchingModel(content: String, contentType: String, requestType: String, defaultModel: String): String {
        // Apply rules in order (first match wins)
        for (rule in routingRules) {
            if (rule.matches(content, contentType, requestType)) {
                return rule.targetModel
            }
        }
        
        // If no rule matches, return the default model
        return defaultModel
    }
    
    /**
     * Add a routing rule.
     */
    fun addRule(rule: RoutingRule): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            routingRules.add(rule)
            logger.info("Added routing rule: $rule")
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to add routing rule", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * Remove a routing rule by ID.
     */
    fun removeRule(ruleId: String): Future<Boolean> {
        val promise = Promise.promise<Boolean>()
        
        try {
            val removed = routingRules.removeIf { it.id == ruleId }
            if (removed) {
                logger.info("Removed routing rule: $ruleId")
            } else {
                logger.warn("Routing rule not found: $ruleId")
            }
            promise.complete(removed)
        } catch (e: Exception) {
            logger.error("Failed to remove routing rule", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * Get all routing rules.
     */
    fun getRules(): List<RoutingRule> {
        return routingRules.toList()
    }
    
    /**
     * Clear all routing rules.
     */
    fun clearRules(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            routingRules.clear()
            logger.info("Cleared all routing rules")
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to clear routing rules", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
}
