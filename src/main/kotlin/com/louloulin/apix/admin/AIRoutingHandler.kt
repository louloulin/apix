package com.louloulin.apix.admin

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for AI routing rules API endpoints.
 */
class AIRoutingHandler {
    private val logger = LoggerFactory.getLogger(AIRoutingHandler::class.java)
    
    // In-memory routing rules store (replace with database in production)
    private val rules = ConcurrentHashMap<String, JsonObject>()
    
    // Initialize with some default rules
    init {
        val technicalRule = JsonObject()
            .put("id", "technical-content")
            .put("name", "Technical Content Rule")
            .put("priority", 100)
            .put("condition", JsonObject()
                .put("type", "CONTAINS")
                .put("pattern", "code")
                .put("contentTypes", JsonArray()
                    .add("text/plain")
                    .add("application/json")
                )
                .put("requestTypes", JsonArray()
                    .add("chat")
                    .add("completion")
                )
            )
            .put("targetModel", "gpt-4")
            .put("enabled", true)
        
        val defaultRule = JsonObject()
            .put("id", "default-rule")
            .put("name", "Default Rule")
            .put("priority", 0)
            .put("condition", JsonObject()
                .put("type", "DEFAULT")
                .put("pattern", "*")
                .put("contentTypes", JsonArray().add("*"))
                .put("requestTypes", JsonArray().add("*"))
            )
            .put("targetModel", "gpt-3.5-turbo")
            .put("enabled", true)
        
        rules["technical-content"] = technicalRule
        rules["default-rule"] = defaultRule
    }
    
    /**
     * Sets up the AI routing rules API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up AI routing rules API routes...")
        
        // AI routing rules endpoints
        router.get("/ai/routing/rules").handler(this::getRules)
        router.post("/ai/routing/rules").handler(this::createRule)
        router.get("/ai/routing/rules/:id").handler(this::getRule)
        router.put("/ai/routing/rules/:id").handler(this::updateRule)
        router.delete("/ai/routing/rules/:id").handler(this::deleteRule)
        router.post("/ai/routing/rules/:id/enable").handler(this::enableRule)
        router.post("/ai/routing/rules/:id/disable").handler(this::disableRule)
    }
    
    /**
     * Gets all AI routing rules.
     */
    private fun getRules(context: RoutingContext) {
        try {
            val rulesArray = JsonArray()
            
            // Sort rules by priority (highest first)
            rules.values
                .sortedByDescending { it.getInteger("priority") }
                .forEach { rule ->
                    rulesArray.add(rule)
                }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("rules", rulesArray)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting AI routing rules", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get AI routing rules: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Creates a new AI routing rule.
     */
    private fun createRule(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()
            
            // Validate required fields
            if (!body.containsKey("name") || !body.containsKey("condition") || !body.containsKey("targetModel")) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing required fields: name, condition, targetModel")
                        .encode()
                    )
                return
            }
            
            // Generate ID if not provided
            if (!body.containsKey("id")) {
                val id = body.getString("name").lowercase().replace(" ", "-") + "-" + UUID.randomUUID().toString().substring(0, 8)
                body.put("id", id)
            }
            
            // Set default values if not provided
            if (!body.containsKey("enabled")) {
                body.put("enabled", true)
            }
            
            if (!body.containsKey("priority")) {
                body.put("priority", 0)
            }
            
            // Check if rule with same ID already exists
            if (rules.containsKey(body.getString("id"))) {
                context.response()
                    .setStatusCode(409)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Rule with ID ${body.getString("id")} already exists")
                        .encode()
                    )
                return
            }
            
            // Add rule to store
            rules[body.getString("id")] = body
            
            context.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("rule", body)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error creating AI routing rule", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to create AI routing rule: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Gets an AI routing rule by ID.
     */
    private fun getRule(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val rule = rules[id]
            
            if (rule == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Rule not found: $id")
                        .encode()
                    )
                return
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("rule", rule)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting AI routing rule", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get AI routing rule: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Updates an AI routing rule.
     */
    private fun updateRule(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val body = context.body().asJsonObject()
            
            val rule = rules[id]
            
            if (rule == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Rule not found: $id")
                        .encode()
                    )
                return
            }
            
            // Update rule properties
            body.fieldNames().forEach { field ->
                if (field != "id") { // Don't allow changing the ID
                    rule.put(field, body.getValue(field))
                }
            }
            
            // Update rule in store
            rules[id] = rule
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("rule", rule)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error updating AI routing rule", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to update AI routing rule: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Deletes an AI routing rule.
     */
    private fun deleteRule(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            
            val rule = rules[id]
            
            if (rule == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Rule not found: $id")
                        .encode()
                    )
                return
            }
            
            // Remove rule from store
            rules.remove(id)
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error deleting AI routing rule", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to delete AI routing rule: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Enables an AI routing rule.
     */
    private fun enableRule(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            
            val rule = rules[id]
            
            if (rule == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Rule not found: $id")
                        .encode()
                    )
                return
            }
            
            // Enable rule
            rule.put("enabled", true)
            
            // Update rule in store
            rules[id] = rule
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("rule", rule)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error enabling AI routing rule", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to enable AI routing rule: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Disables an AI routing rule.
     */
    private fun disableRule(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            
            val rule = rules[id]
            
            if (rule == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Rule not found: $id")
                        .encode()
                    )
                return
            }
            
            // Disable rule
            rule.put("enabled", false)
            
            // Update rule in store
            rules[id] = rule
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("rule", rule)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error disabling AI routing rule", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to disable AI routing rule: ${e.message}")
                    .encode()
                )
        }
    }
}
