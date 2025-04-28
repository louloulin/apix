package com.louloulin.apix.core.verticle

import com.louloulin.apix.ai.router.ModelRouter
import com.louloulin.apix.ai.router.RoutingRule
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * Verticle responsible for AI model routing.
 */
class ModelRouterVerticle : BaseVerticle() {
    private lateinit var modelRouter: ModelRouter
    
    override fun registerEventBusHandlers() {
        // Model routing
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_MODEL_ROUTE, this::handleRouteToModel)
        
        // Rule management
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_MODEL_RULES_GET, this::handleGetRules)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_MODEL_RULE_ADD, this::handleAddRule)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_MODEL_RULE_REMOVE, this::handleRemoveRule)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_MODEL_RULES_CLEAR, this::handleClearRules)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        // Initialize model router
        modelRouter = ModelRouter(vertx)
        
        // Get configuration from ConfigVerticle
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject().put("section", "ai.modelRouter")) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // Initialize model router with configuration
                    modelRouter.initialize(config)
                        .onSuccess {
                            logger.info("ModelRouterVerticle started successfully")
                            startPromise.complete()
                        }
                        .onFailure { err ->
                            logger.error("Failed to initialize model router", err)
                            startPromise.fail(err)
                        }
                } else {
                    // If config not found, initialize with empty config
                    modelRouter.initialize(JsonObject())
                        .onSuccess {
                            logger.info("ModelRouterVerticle started with default configuration")
                            startPromise.complete()
                        }
                        .onFailure { err ->
                            logger.error("Failed to initialize model router", err)
                            startPromise.fail(err)
                        }
                }
            } else {
                // If config request fails, initialize with empty config
                modelRouter.initialize(JsonObject())
                    .onSuccess {
                        logger.info("ModelRouterVerticle started with default configuration")
                        startPromise.complete()
                    }
                    .onFailure { err ->
                        logger.error("Failed to initialize model router", err)
                        startPromise.fail(err)
                    }
            }
        }
    }
    
    /**
     * Handle route to model request.
     */
    private fun handleRouteToModel(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        
        modelRouter.routeRequest(request)
            .onSuccess { model ->
                sendSuccess(message, JsonObject()
                    .put("model", model)
                )
            }
            .onFailure { err ->
                sendError(message, err)
            }
    }
    
    /**
     * Handle get rules request.
     */
    private fun handleGetRules(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val rules = modelRouter.getRules()
        val rulesArray = JsonArray()
        
        rules.forEach { rule ->
            rulesArray.add(rule.toJson())
        }
        
        sendSuccess(message, rulesArray)
    }
    
    /**
     * Handle add rule request.
     */
    private fun handleAddRule(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val ruleJson = message.body().getJsonObject("rule")
        
        if (ruleJson == null) {
            sendError(message, 400, "Rule is required")
            return
        }
        
        try {
            val rule = RoutingRule.fromJson(ruleJson)
            
            modelRouter.addRule(rule)
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
     * Handle remove rule request.
     */
    private fun handleRemoveRule(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val ruleId = message.body().getString("id")
        
        if (ruleId == null) {
            sendError(message, 400, "Rule ID is required")
            return
        }
        
        modelRouter.removeRule(ruleId)
            .onSuccess { removed ->
                sendSuccess(message, JsonObject()
                    .put("removed", removed)
                )
            }
            .onFailure { err ->
                sendError(message, err)
            }
    }
    
    /**
     * Handle clear rules request.
     */
    private fun handleClearRules(message: io.vertx.core.eventbus.Message<JsonObject>) {
        modelRouter.clearRules()
            .onSuccess {
                sendSuccess(message, true)
            }
            .onFailure { err ->
                sendError(message, err)
            }
    }
}
