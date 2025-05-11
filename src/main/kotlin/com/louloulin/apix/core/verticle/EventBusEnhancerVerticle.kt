package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.eventbus.DistributedEventBus
import com.louloulin.apix.core.eventbus.EventBusManager
import com.louloulin.apix.core.eventbus.HighPerformanceEventBus
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Verticle responsible for managing enhanced EventBus implementations.
 */
class EventBusEnhancerVerticle : BaseVerticle() {
    // Use the logger from BaseVerticle
    
    // EventBus manager
    private lateinit var eventBusManager: EventBusManager
    
    // Enhanced EventBus implementations
    private lateinit var distributedEventBus: DistributedEventBus
    private lateinit var highPerformanceEventBus: HighPerformanceEventBus
    
    override fun registerEventBusHandlers() {
        // EventBus management
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_TYPE_GET, this::handleGetEventBusType)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_TYPE_SET, this::handleSetEventBusType)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_STATS_GET, this::handleGetEventBusStats)
        
        // Distributed EventBus configuration
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_DISTRIBUTED_CONFIG, this::handleDistributedEventBusConfig)
        
        // High-performance EventBus configuration
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_HIGHPERF_CONFIG, this::handleHighPerfEventBusConfig)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        // Initialize EventBus manager
        eventBusManager = EventBusManager.getInstance(vertx)
        
        // Initialize enhanced EventBus implementations
        distributedEventBus = DistributedEventBus.getInstance(vertx)
        highPerformanceEventBus = HighPerformanceEventBus.getInstance(vertx)
        
        // Start enhanced EventBus implementations
        distributedEventBus.start()
            .compose { _ -> highPerformanceEventBus.start() }
            .onSuccess {
                logger.info("EventBusEnhancerVerticle started successfully")
                startPromise.complete()
            }
            .onFailure { cause ->
                logger.error("Failed to start EventBusEnhancerVerticle", cause)
                startPromise.fail(cause)
            }
    }
    
    /**
     * Handle get EventBus type request.
     */
    private fun handleGetEventBusType(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val currentType = eventBusManager.getCurrentType()
        
        val response = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("type", currentType.name)
            )
        
        message.reply(response)
    }
    
    /**
     * Handle set EventBus type request.
     */
    private fun handleSetEventBusType(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val typeStr = request.getString("type")
        
        if (typeStr == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "No type provided"))
            return
        }
        
        try {
            val type = EventBusManager.EventBusType.valueOf(typeStr)
            
            eventBusManager.switchType(type)
                .onSuccess {
                    val response = JsonObject()
                        .put("success", true)
                        .put("result", JsonObject()
                            .put("type", type.name)
                        )
                    
                    message.reply(response)
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "Failed to switch EventBus type: ${cause.message}"))
                }
        } catch (e: IllegalArgumentException) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "Invalid EventBus type: $typeStr"))
        }
    }
    
    /**
     * Handle get EventBus stats request.
     */
    private fun handleGetEventBusStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val stats = eventBusManager.getStats()
        
        val response = JsonObject()
            .put("success", true)
            .put("result", stats)
        
        message.reply(response)
    }
    
    /**
     * Handle distributed EventBus configuration request.
     */
    private fun handleDistributedEventBusConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val action = request.getString("action", "get")
        
        when (action) {
            "get" -> {
                val stats = distributedEventBus.getStats()
                
                val response = JsonObject()
                    .put("success", true)
                    .put("result", stats)
                
                message.reply(response)
            }
            "set" -> {
                val config = request.getJsonObject("config", JsonObject())
                val compressionEnabled = config.getBoolean("compressionEnabled")
                
                if (compressionEnabled != null) {
                    distributedEventBus.setCompressionEnabled(compressionEnabled)
                }
                
                val response = JsonObject()
                    .put("success", true)
                    .put("result", distributedEventBus.getStats())
                
                message.reply(response)
            }
            else -> {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Invalid action: $action"))
            }
        }
    }
    
    /**
     * Handle high-performance EventBus configuration request.
     */
    private fun handleHighPerfEventBusConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val action = request.getString("action", "get")
        
        when (action) {
            "get" -> {
                val stats = highPerformanceEventBus.getStats()
                
                val response = JsonObject()
                    .put("success", true)
                    .put("result", stats)
                
                message.reply(response)
            }
            "registerLocalHandler" -> {
                val address = request.getString("address")
                
                if (address == null) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", "No address provided"))
                    return
                }
                
                // We can't actually register a handler from here, but we can return success
                val response = JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("registrationId", "dummy-id")
                    )
                
                message.reply(response)
            }
            else -> {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "Invalid action: $action"))
            }
        }
    }
}
