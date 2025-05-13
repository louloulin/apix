package com.louloulin.apix.core.verticle

import com.louloulin.apix.cache.CacheService
import com.louloulin.apix.config.ConfigManager
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject

/**
 * 缓存 Verticle，提供缓存服务
 */
class CacheVerticle : BaseVerticle() {

    // 缓存服务
    private lateinit var cacheService: CacheService

    // 配置管理器
    private lateinit var configManager: ConfigManager

    // 事件总线地址
    companion object {
        const val ADDRESS_GET = "apix.cache.get"
        const val ADDRESS_GET_BY_QUERY = "apix.cache.getByQuery"
        const val ADDRESS_SET = "apix.cache.set"
        const val ADDRESS_SET_BY_QUERY = "apix.cache.setByQuery"
        const val ADDRESS_REMOVE = "apix.cache.remove"
        const val ADDRESS_CLEAR = "apix.cache.clear"
        const val ADDRESS_STATS = "apix.cache.stats"
    }

    override fun onStart(startPromise: Promise<Void>) {
        // 初始化配置管理器
        configManager = ConfigManager(vertx)

        // 从配置中创建缓存服务
        val cacheConfig = configManager.getConfig().getJsonObject("cache", JsonObject())
        cacheService = CacheService.createFromConfig(vertx, cacheConfig)

        logger.info("CacheVerticle started successfully")
        startPromise.complete()
    }

    /**
     * 注册事件总线处理器
     */
    override fun registerEventBusHandlers() {
        // 获取缓存
        vertx.eventBus().consumer<JsonObject>(ADDRESS_GET, this::handleGet)

        // 根据查询获取缓存
        vertx.eventBus().consumer<JsonObject>(ADDRESS_GET_BY_QUERY, this::handleGetByQuery)

        // 设置缓存
        vertx.eventBus().consumer<JsonObject>(ADDRESS_SET, this::handleSet)

        // 根据查询设置缓存
        vertx.eventBus().consumer<JsonObject>(ADDRESS_SET_BY_QUERY, this::handleSetByQuery)

        // 删除缓存
        vertx.eventBus().consumer<JsonObject>(ADDRESS_REMOVE, this::handleRemove)

        // 清空缓存
        vertx.eventBus().consumer<JsonObject>(ADDRESS_CLEAR, this::handleClear)

        // 获取缓存统计信息
        vertx.eventBus().consumer<JsonObject>(ADDRESS_STATS, this::handleStats)
    }

    /**
     * 处理获取缓存请求
     */
    private fun handleGet(message: Message<JsonObject>) {
        val key = message.body().getString("key")

        if (key == null) {
            message.fail(400, "Missing key parameter")
            return
        }

        cacheService.get(key)
            .onSuccess { value ->
                message.reply(JsonObject().put("value", value))
            }
            .onFailure { err ->
                logger.error("Error getting cache entry for key: $key", err)
                message.fail(500, err.message)
            }
    }

    /**
     * 处理根据查询获取缓存请求
     */
    private fun handleGetByQuery(message: Message<JsonObject>) {
        val query = message.body().getString("query")

        if (query == null) {
            message.fail(400, "Missing query parameter")
            return
        }

        cacheService.getByQuery(query)
            .onSuccess { value ->
                message.reply(JsonObject().put("value", value))
            }
            .onFailure { err ->
                logger.error("Error getting cache entry for query: $query", err)
                message.fail(500, err.message)
            }
    }

    /**
     * 处理设置缓存请求
     */
    private fun handleSet(message: Message<JsonObject>) {
        val key = message.body().getString("key")
        val value = message.body().getJsonObject("value")

        if (key == null) {
            message.fail(400, "Missing key parameter")
            return
        }

        if (value == null) {
            message.fail(400, "Missing value parameter")
            return
        }

        cacheService.set(key, value)
            .onSuccess {
                message.reply(JsonObject().put("success", true))
            }
            .onFailure { err ->
                logger.error("Error setting cache entry for key: $key", err)
                message.fail(500, err.message)
            }
    }

    /**
     * 处理根据查询设置缓存请求
     */
    private fun handleSetByQuery(message: Message<JsonObject>) {
        val query = message.body().getString("query")
        val response = message.body().getJsonObject("response")

        if (query == null) {
            message.fail(400, "Missing query parameter")
            return
        }

        if (response == null) {
            message.fail(400, "Missing response parameter")
            return
        }

        cacheService.setByQuery(query, response)
            .onSuccess {
                message.reply(JsonObject().put("success", true))
            }
            .onFailure { err ->
                logger.error("Error setting cache entry for query: $query", err)
                message.fail(500, err.message)
            }
    }

    /**
     * 处理删除缓存请求
     */
    private fun handleRemove(message: Message<JsonObject>) {
        val key = message.body().getString("key")

        if (key == null) {
            message.fail(400, "Missing key parameter")
            return
        }

        cacheService.remove(key)
            .onSuccess {
                message.reply(JsonObject().put("success", true))
            }
            .onFailure { err ->
                logger.error("Error removing cache entry for key: $key", err)
                message.fail(500, err.message)
            }
    }

    /**
     * 处理清空缓存请求
     */
    private fun handleClear(message: Message<JsonObject>) {
        cacheService.clear()
            .onSuccess {
                message.reply(JsonObject().put("success", true))
            }
            .onFailure { err ->
                logger.error("Error clearing cache", err)
                message.fail(500, err.message)
            }
    }

    /**
     * 处理获取缓存统计信息请求
     */
    private fun handleStats(message: Message<JsonObject>) {
        cacheService.getStats()
            .onSuccess { stats ->
                message.reply(stats)
            }
            .onFailure { err ->
                logger.error("Error getting cache stats", err)
                message.fail(500, err.message)
            }
    }

    override fun onStop(stopPromise: Promise<Void>) {
        // 关闭缓存服务
        cacheService.close()
            .onSuccess {
                logger.info("CacheVerticle stopped successfully")
                stopPromise.complete()
            }
            .onFailure { err ->
                logger.error("Error stopping CacheVerticle", err)
                stopPromise.fail(err)
            }
    }
}
