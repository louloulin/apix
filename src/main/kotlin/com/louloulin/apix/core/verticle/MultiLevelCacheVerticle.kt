package com.louloulin.apix.core.verticle

import com.louloulin.apix.cache.CacheConsistencyManager
import com.louloulin.apix.cache.CacheWarmupManager
import com.louloulin.apix.cache.MultiLevelCacheManager
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 多级缓存 Verticle，负责管理系统的多级缓存功能。
 */
class MultiLevelCacheVerticle : BaseVerticle() {
    // 使用 BaseVerticle 中的 logger
    
    // 多级缓存管理器
    private lateinit var cacheManager: MultiLevelCacheManager
    
    // 缓存一致性管理器
    private lateinit var consistencyManager: CacheConsistencyManager
    
    // 缓存预热管理器
    private lateinit var warmupManager: CacheWarmupManager
    
    override fun registerEventBusHandlers() {
        // 缓存操作
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_GET, this::handleGetCache)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_PUT, this::handlePutCache)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_REMOVE, this::handleRemoveCache)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_CLEAR, this::handleClearCache)
        
        // 缓存统计
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_STATS_GET, this::handleGetCacheStats)
        
        // 缓存一致性
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_CONSISTENCY_STATUS_GET, this::handleGetConsistencyStatus)
        
        // 缓存预热
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_WARMUP_START, this::handleStartWarmup)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_WARMUP_STATUS_GET, this::handleGetWarmupStatus)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // 初始化多级缓存管理器
                    cacheManager = MultiLevelCacheManager.getInstance(vertx)
                    
                    // 初始化缓存一致性管理器
                    consistencyManager = CacheConsistencyManager.getInstance(vertx)
                    
                    // 初始化缓存预热管理器
                    warmupManager = CacheWarmupManager.getInstance(vertx)
                    
                    // 初始化所有管理器
                    cacheManager.initialize(config)
                        .compose { _ -> consistencyManager.initialize(config) }
                        .compose { _ -> warmupManager.initialize(config) }
                        .onSuccess { _ ->
                            logger.info("MultiLevelCacheVerticle 启动成功")
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("MultiLevelCacheVerticle 启动失败", cause)
                            startPromise.fail(cause)
                        }
                } else {
                    val errorMsg = "获取配置失败: ${configResponse.getString("message", "未知错误")}"
                    logger.error(errorMsg)
                    startPromise.fail(errorMsg)
                }
            } else {
                logger.error("获取配置失败", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("MultiLevelCacheVerticle 停止")
        stopPromise.complete()
    }
    
    /**
     * 处理获取缓存请求。
     */
    private fun handleGetCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val key = request.getString("key")
        val namespace = request.getString("namespace")
        
        if (key == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 key 参数")
            )
            return
        }
        
        // 获取缓存
        cacheManager.get(key, namespace ?: "apix")
            .onSuccess { value ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", value)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "获取缓存失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理存储缓存请求。
     */
    private fun handlePutCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val key = request.getString("key")
        val value = request.getValue("value")
        val ttl = request.getLong("ttl", 0)
        val namespace = request.getString("namespace")
        
        if (key == null || value == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 key 或 value 参数")
            )
            return
        }
        
        // 存储缓存
        cacheManager.put(key, value, ttl, namespace ?: "apix")
            .onSuccess {
                message.reply(JsonObject()
                    .put("success", true)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "存储缓存失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理移除缓存请求。
     */
    private fun handleRemoveCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val key = request.getString("key")
        val namespace = request.getString("namespace")
        
        if (key == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 key 参数")
            )
            return
        }
        
        // 移除缓存
        cacheManager.remove(key, namespace ?: "apix")
            .onSuccess {
                message.reply(JsonObject()
                    .put("success", true)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "移除缓存失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理清空缓存请求。
     */
    private fun handleClearCache(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val namespace = request.getString("namespace")
        
        // 清空缓存
        cacheManager.clear(namespace ?: "apix")
            .onSuccess {
                message.reply(JsonObject()
                    .put("success", true)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "清空缓存失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理获取缓存统计信息请求。
     */
    private fun handleGetCacheStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val namespace = request.getString("namespace")
        
        // 获取缓存统计信息
        val stats = cacheManager.getStats(namespace ?: "apix")
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", stats)
        )
    }
    
    /**
     * 处理获取缓存一致性状态请求。
     */
    private fun handleGetConsistencyStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 获取缓存一致性状态
        val status = consistencyManager.getStatus()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }
    
    /**
     * 处理开始缓存预热请求。
     */
    private fun handleStartWarmup(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val keys = request.getJsonArray("keys")?.map { it.toString() } ?: emptyList()
        val handlers = request.getJsonArray("handlers")?.map { it.toString() } ?: emptyList()
        val namespace = request.getString("namespace")
        val mode = request.getString("mode")
        
        // 开始缓存预热
        warmupManager.warmupCache(keys, handlers, namespace ?: "apix", mode ?: "async")
            .onSuccess { result ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", result)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "开始缓存预热失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理获取缓存预热状态请求。
     */
    private fun handleGetWarmupStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 获取缓存预热状态
        val status = warmupManager.getWarmupStatus()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }
}
