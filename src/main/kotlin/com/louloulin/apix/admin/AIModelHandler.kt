package com.louloulin.apix.admin

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Handler for AI model management API endpoints.
 */
class AIModelHandler {
    private val logger = LoggerFactory.getLogger(AIModelHandler::class.java)
    
    // In-memory model store (replace with database in production)
    private val models = ConcurrentHashMap<String, JsonObject>()
    
    // Initialize with some default models
    init {
        val gpt4 = JsonObject()
            .put("id", "gpt-4")
            .put("name", "GPT-4")
            .put("provider", "OpenAI")
            .put("description", "OpenAI's most advanced model, GPT-4 is a large multimodal model that can solve difficult problems with greater accuracy than any of our previous models.")
            .put("maxTokens", 8192)
            .put("enabled", true)
            .put("priority", 100)
            .put("costPerToken", 0.00003)
            .put("capabilities", JsonArray()
                .add("text-generation")
                .add("code-generation")
                .add("reasoning")
            )
        
        val gpt35Turbo = JsonObject()
            .put("id", "gpt-3.5-turbo")
            .put("name", "GPT-3.5 Turbo")
            .put("provider", "OpenAI")
            .put("description", "Most capable GPT-3.5 model and optimized for chat at 1/10th the cost of text-davinci-003.")
            .put("maxTokens", 4096)
            .put("enabled", true)
            .put("priority", 50)
            .put("costPerToken", 0.000002)
            .put("capabilities", JsonArray()
                .add("text-generation")
                .add("code-generation")
            )
        
        val claude3Opus = JsonObject()
            .put("id", "claude-3-opus")
            .put("name", "Claude 3 Opus")
            .put("provider", "Anthropic")
            .put("description", "Anthropic's most powerful model, Claude 3 Opus excels at a wide range of tasks from complex reasoning to creative content generation.")
            .put("maxTokens", 100000)
            .put("enabled", true)
            .put("priority", 90)
            .put("costPerToken", 0.00003)
            .put("capabilities", JsonArray()
                .add("text-generation")
                .add("reasoning")
                .add("creative-writing")
            )
        
        models["gpt-4"] = gpt4
        models["gpt-3.5-turbo"] = gpt35Turbo
        models["claude-3-opus"] = claude3Opus
    }
    
    /**
     * Sets up the AI model management API routes.
     */
    fun setupRoutes(router: Router) {
        logger.info("Setting up AI model management API routes...")
        
        // AI model management endpoints
        router.get("/ai/models").handler(this::getModels)
        router.post("/ai/models").handler(this::createModel)
        router.get("/ai/models/:id").handler(this::getModel)
        router.put("/ai/models/:id").handler(this::updateModel)
        router.delete("/ai/models/:id").handler(this::deleteModel)
        router.post("/ai/models/:id/enable").handler(this::enableModel)
        router.post("/ai/models/:id/disable").handler(this::disableModel)
    }
    
    /**
     * Gets all AI models.
     */
    private fun getModels(context: RoutingContext) {
        try {
            val modelsArray = JsonArray()
            
            models.values.forEach { model ->
                modelsArray.add(model)
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("models", modelsArray)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting AI models", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get AI models: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Creates a new AI model.
     */
    private fun createModel(context: RoutingContext) {
        try {
            val body = context.body().asJsonObject()
            
            // Validate required fields
            if (!body.containsKey("name") || !body.containsKey("provider")) {
                context.response()
                    .setStatusCode(400)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Missing required fields: name, provider")
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
            
            if (!body.containsKey("capabilities")) {
                body.put("capabilities", JsonArray())
            }
            
            // Check if model with same ID already exists
            if (models.containsKey(body.getString("id"))) {
                context.response()
                    .setStatusCode(409)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Model with ID ${body.getString("id")} already exists")
                        .encode()
                    )
                return
            }
            
            // Add model to store
            models[body.getString("id")] = body
            
            context.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("model", body)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error creating AI model", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to create AI model: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Gets an AI model by ID.
     */
    private fun getModel(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val model = models[id]
            
            if (model == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Model not found: $id")
                        .encode()
                    )
                return
            }
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("model", model)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error getting AI model", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to get AI model: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Updates an AI model.
     */
    private fun updateModel(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            val body = context.body().asJsonObject()
            
            val model = models[id]
            
            if (model == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Model not found: $id")
                        .encode()
                    )
                return
            }
            
            // Update model properties
            body.fieldNames().forEach { field ->
                if (field != "id") { // Don't allow changing the ID
                    model.put(field, body.getValue(field))
                }
            }
            
            // Update model in store
            models[id] = model
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("model", model)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error updating AI model", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to update AI model: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Deletes an AI model.
     */
    private fun deleteModel(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            
            val model = models[id]
            
            if (model == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Model not found: $id")
                        .encode()
                    )
                return
            }
            
            // Remove model from store
            models.remove(id)
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error deleting AI model", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to delete AI model: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Enables an AI model.
     */
    private fun enableModel(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            
            val model = models[id]
            
            if (model == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Model not found: $id")
                        .encode()
                    )
                return
            }
            
            // Enable model
            model.put("enabled", true)
            
            // Update model in store
            models[id] = model
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("model", model)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error enabling AI model", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to enable AI model: ${e.message}")
                    .encode()
                )
        }
    }
    
    /**
     * Disables an AI model.
     */
    private fun disableModel(context: RoutingContext) {
        try {
            val id = context.pathParam("id")
            
            val model = models[id]
            
            if (model == null) {
                context.response()
                    .setStatusCode(404)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Model not found: $id")
                        .encode()
                    )
                return
            }
            
            // Disable model
            model.put("enabled", false)
            
            // Update model in store
            models[id] = model
            
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("model", model)
                    .encode()
                )
        } catch (e: Exception) {
            logger.error("Error disabling AI model", e)
            
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to disable AI model: ${e.message}")
                    .encode()
                )
        }
    }
}
