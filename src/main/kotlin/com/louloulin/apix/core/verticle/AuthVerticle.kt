package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.models.ApiKey
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * 负责认证和授权的 Verticle
 */
class AuthVerticle : BaseVerticle() {
    private lateinit var configManager: ConfigManager
    private val apiKeys = ConcurrentHashMap<String, ApiKey>()
    
    override fun registerEventBusHandlers() {
        // API Key 相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AUTH_VALIDATE_API_KEY, this::handleValidateApiKey)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AUTH_CREATE_API_KEY, this::handleCreateApiKey)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AUTH_GET_API_KEYS, this::handleGetApiKeys)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AUTH_DELETE_API_KEY, this::handleDeleteApiKey)
        
        // JWT 相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AUTH_VALIDATE_JWT, this::handleValidateJwt)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AUTH_GENERATE_JWT, this::handleGenerateJwt)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        configManager = ConfigManager(vertx)
        
        // 从配置加载 API Keys
        loadApiKeysFromConfig().onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("AuthVerticle started successfully")
                startPromise.complete()
            } else {
                logger.error("Failed to start AuthVerticle", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }
    
    /**
     * 从配置加载 API Keys
     */
    private fun loadApiKeysFromConfig() = vertx.executeBlocking<Void> { promise ->
        try {
            val apiKeysConfig = configManager.getConfig().getJsonArray("apiKeys", JsonArray())
            
            apiKeysConfig.forEach { item ->
                val apiKeyJson = item as JsonObject
                val apiKey = ApiKey(
                    id = apiKeyJson.getString("id"),
                    key = apiKeyJson.getString("key"),
                    name = apiKeyJson.getString("name"),
                    scopes = apiKeyJson.getJsonArray("scopes", JsonArray()).map { it.toString() },
                    enabled = apiKeyJson.getBoolean("enabled", true),
                    createdAt = apiKeyJson.getLong("createdAt", System.currentTimeMillis()),
                    expiresAt = apiKeyJson.getLong("expiresAt", 0)
                )
                apiKeys[apiKey.key] = apiKey
                logger.info("Loaded API Key: {}", apiKey.name)
            }
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to load API Keys from config", e)
            promise.fail(e)
        }
    }
    
    /**
     * 保存 API Keys 到配置
     */
    private fun saveApiKeysToConfig() = vertx.executeBlocking<Void> { promise ->
        try {
            val apiKeysArray = JsonArray()
            apiKeys.values.forEach { apiKey ->
                apiKeysArray.add(JsonObject()
                    .put("id", apiKey.id)
                    .put("key", apiKey.key)
                    .put("name", apiKey.name)
                    .put("scopes", JsonArray(apiKey.scopes))
                    .put("enabled", apiKey.enabled)
                    .put("createdAt", apiKey.createdAt)
                    .put("expiresAt", apiKey.expiresAt)
                )
            }
            
            val config = configManager.getConfig()
            config.put("apiKeys", apiKeysArray)
            configManager.saveConfig(config).onComplete { ar ->
                if (ar.succeeded()) {
                    promise.complete()
                } else {
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to save API Keys to config", e)
            promise.fail(e)
        }
    }
    
    /**
     * 处理验证 API Key 请求
     */
    private fun handleValidateApiKey(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val apiKeyValue = message.body().getString("apiKey")
        val requiredScopes = message.body().getJsonArray("requiredScopes", JsonArray()).map { it.toString() }
        
        if (apiKeyValue == null) {
            sendError(message, 400, "API Key is required")
            return
        }
        
        val apiKey = apiKeys[apiKeyValue]
        
        if (apiKey == null) {
            sendError(message, 401, "Invalid API Key")
            return
        }
        
        if (!apiKey.enabled) {
            sendError(message, 401, "API Key is disabled")
            return
        }
        
        if (apiKey.expiresAt > 0 && apiKey.expiresAt < System.currentTimeMillis()) {
            sendError(message, 401, "API Key has expired")
            return
        }
        
        // 检查作用域
        if (requiredScopes.isNotEmpty() && !requiredScopes.all { apiKey.scopes.contains(it) }) {
            sendError(message, 403, "Insufficient scopes")
            return
        }
        
        // 验证成功
        sendSuccess(message, JsonObject()
            .put("valid", true)
            .put("apiKey", JsonObject()
                .put("id", apiKey.id)
                .put("name", apiKey.name)
                .put("scopes", JsonArray(apiKey.scopes))
            )
        )
    }
    
    /**
     * 处理创建 API Key 请求
     */
    private fun handleCreateApiKey(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val name = message.body().getString("name")
        val scopes = message.body().getJsonArray("scopes", JsonArray()).map { it.toString() }
        val expiresAt = message.body().getLong("expiresAt", 0)
        
        if (name.isNullOrBlank()) {
            sendError(message, 400, "Name is required")
            return
        }
        
        // 生成新的 API Key
        val id = UUID.randomUUID().toString()
        val key = generateApiKey()
        val apiKey = ApiKey(
            id = id,
            key = key,
            name = name,
            scopes = scopes,
            enabled = true,
            createdAt = System.currentTimeMillis(),
            expiresAt = expiresAt
        )
        
        // 保存 API Key
        apiKeys[key] = apiKey
        
        // 保存到配置
        saveApiKeysToConfig().onComplete { ar ->
            if (ar.succeeded()) {
                sendSuccess(message, JsonObject()
                    .put("id", apiKey.id)
                    .put("key", apiKey.key)
                    .put("name", apiKey.name)
                    .put("scopes", JsonArray(apiKey.scopes))
                    .put("enabled", apiKey.enabled)
                    .put("createdAt", apiKey.createdAt)
                    .put("expiresAt", apiKey.expiresAt)
                )
            } else {
                sendError(message, ar.cause())
            }
        }
    }
    
    /**
     * 处理获取 API Keys 请求
     */
    private fun handleGetApiKeys(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val result = JsonArray()
        
        apiKeys.values.forEach { apiKey ->
            result.add(JsonObject()
                .put("id", apiKey.id)
                .put("name", apiKey.name)
                .put("scopes", JsonArray(apiKey.scopes))
                .put("enabled", apiKey.enabled)
                .put("createdAt", apiKey.createdAt)
                .put("expiresAt", apiKey.expiresAt)
                // 不返回 key 值，保证安全
            )
        }
        
        sendSuccess(message, result)
    }
    
    /**
     * 处理删除 API Key 请求
     */
    private fun handleDeleteApiKey(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")
        
        if (id == null) {
            sendError(message, 400, "ID is required")
            return
        }
        
        // 查找要删除的 API Key
        val apiKeyToRemove = apiKeys.values.find { it.id == id }
        
        if (apiKeyToRemove == null) {
            sendError(message, 404, "API Key not found")
            return
        }
        
        // 删除 API Key
        apiKeys.remove(apiKeyToRemove.key)
        
        // 保存到配置
        saveApiKeysToConfig().onComplete { ar ->
            if (ar.succeeded()) {
                sendSuccess(message, true)
            } else {
                sendError(message, ar.cause())
            }
        }
    }
    
    /**
     * 处理验证 JWT 请求
     */
    private fun handleValidateJwt(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // TODO: 实现 JWT 验证
        sendError(message, 501, "JWT validation not implemented yet")
    }
    
    /**
     * 处理生成 JWT 请求
     */
    private fun handleGenerateJwt(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // TODO: 实现 JWT 生成
        sendError(message, 501, "JWT generation not implemented yet")
    }
    
    /**
     * 生成随机 API Key
     */
    private fun generateApiKey(): String {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
    }
}
