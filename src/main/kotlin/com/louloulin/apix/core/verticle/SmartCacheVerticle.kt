package com.louloulin.apix.core.verticle

import com.louloulin.apix.cache.AdaptiveTTLManager
import com.louloulin.apix.cache.BloomFilterManager
import com.louloulin.apix.cache.CacheProtectionManager
import com.louloulin.apix.cache.HotDataManager
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 智能缓存策略 Verticle，负责管理系统的智能缓存策略功能。
 */
class SmartCacheVerticle : BaseVerticle() {
    // 使用 BaseVerticle 中的 logger
    
    // 自适应 TTL 管理器
    private lateinit var adaptiveTTLManager: AdaptiveTTLManager
    
    // 热点数据管理器
    private lateinit var hotDataManager: HotDataManager
    
    // 缓存穿透防护管理器
    private lateinit var cacheProtectionManager: CacheProtectionManager
    
    // 布隆过滤器管理器
    private lateinit var bloomFilterManager: BloomFilterManager
    
    override fun registerEventBusHandlers() {
        // 自适应 TTL 相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_ADAPTIVE_TTL_STATUS_GET, this::handleGetAdaptiveTTLStatus)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_ADAPTIVE_TTL_GET, this::handleGetAdaptiveTTL)
        
        // 热点数据相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_HOT_DATA_STATUS_GET, this::handleGetHotDataStatus)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_HOT_DATA_CHECK, this::handleCheckHotData)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_HOT_DATA_GET_ALL, this::handleGetAllHotData)
        
        // 缓存穿透防护相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_PROTECTION_STATUS_GET, this::handleGetCacheProtectionStatus)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_PROTECTION_PENETRATION, this::handlePreventCachePenetration)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_PROTECTION_BREAKDOWN, this::handlePreventCacheBreakdown)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_PROTECTION_AVALANCHE, this::handlePreventCacheAvalanche)
        
        // 布隆过滤器相关
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_BLOOM_FILTER_STATUS_GET, this::handleGetBloomFilterStatus)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_BLOOM_FILTER_ADD, this::handleAddToBloomFilter)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_BLOOM_FILTER_CHECK, this::handleCheckBloomFilter)
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // 初始化自适应 TTL 管理器
                    adaptiveTTLManager = AdaptiveTTLManager.getInstance(vertx)
                    
                    // 初始化热点数据管理器
                    hotDataManager = HotDataManager.getInstance(vertx)
                    
                    // 初始化缓存穿透防护管理器
                    cacheProtectionManager = CacheProtectionManager.getInstance(vertx)
                    
                    // 初始化布隆过滤器管理器
                    bloomFilterManager = BloomFilterManager.getInstance(vertx)
                    
                    // 初始化所有管理器
                    adaptiveTTLManager.initialize(config)
                        .compose { _ -> hotDataManager.initialize(config) }
                        .compose { _ -> bloomFilterManager.initialize(config) }
                        .compose { _ -> cacheProtectionManager.initialize(config) }
                        .onSuccess { _ ->
                            logger.info("SmartCacheVerticle 启动成功")
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("SmartCacheVerticle 启动失败", cause)
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
        logger.info("SmartCacheVerticle 停止")
        stopPromise.complete()
    }
    
    /**
     * 处理获取自适应 TTL 状态请求。
     */
    private fun handleGetAdaptiveTTLStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 获取自适应 TTL 状态
        val status = adaptiveTTLManager.getStatus()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }
    
    /**
     * 处理获取自适应 TTL 请求。
     */
    private fun handleGetAdaptiveTTL(message: io.vertx.core.eventbus.Message<JsonObject>) {
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
        
        // 获取推荐的 TTL
        val ttl = adaptiveTTLManager.getRecommendedTTL(key, namespace ?: "apix")
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("key", key)
                .put("namespace", namespace ?: "apix")
                .put("ttl", ttl)
            )
        )
    }
    
    /**
     * 处理获取热点数据状态请求。
     */
    private fun handleGetHotDataStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 获取热点数据状态
        val status = hotDataManager.getStatus()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }
    
    /**
     * 处理检查热点数据请求。
     */
    private fun handleCheckHotData(message: io.vertx.core.eventbus.Message<JsonObject>) {
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
        
        // 检查是否是热点数据
        val isHotData = hotDataManager.isHotData(key, namespace ?: "apix")
        val accessCount = hotDataManager.getHotDataAccessCount(key, namespace ?: "apix")
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("key", key)
                .put("namespace", namespace ?: "apix")
                .put("isHotData", isHotData)
                .put("accessCount", accessCount)
            )
        )
    }
    
    /**
     * 处理获取所有热点数据请求。
     */
    private fun handleGetAllHotData(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 获取所有热点数据
        val hotData = hotDataManager.getAllHotData()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", hotData)
        )
    }
    
    /**
     * 处理获取缓存穿透防护状态请求。
     */
    private fun handleGetCacheProtectionStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 获取缓存穿透防护状态
        val status = cacheProtectionManager.getStatus()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }
    
    /**
     * 处理防止缓存穿透请求。
     */
    private fun handlePreventCachePenetration(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val key = request.getString("key")
        val namespace = request.getString("namespace")
        val loaderAddress = request.getString("loaderAddress")
        
        if (key == null || loaderAddress == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 key 或 loaderAddress 参数")
            )
            return
        }
        
        // 创建数据加载函数
        val loader = {
            val promise = Promise.promise<Any>()
            
            vertx.eventBus().request<JsonObject>(loaderAddress, JsonObject()
                .put("key", key)
                .put("namespace", namespace ?: "apix")
            ) { ar ->
                if (ar.succeeded()) {
                    val response = ar.result().body()
                    if (response.getBoolean("success", false)) {
                        promise.complete(response.getValue("result"))
                    } else {
                        promise.fail(response.getString("message", "加载数据失败"))
                    }
                } else {
                    promise.fail(ar.cause())
                }
            }
            
            promise.future()
        }
        
        // 防止缓存穿透
        cacheProtectionManager.preventCachePenetration(key, namespace ?: "apix", loader)
            .onSuccess { result ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", result)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "防止缓存穿透失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理防止缓存击穿请求。
     */
    private fun handlePreventCacheBreakdown(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val key = request.getString("key")
        val namespace = request.getString("namespace")
        val loaderAddress = request.getString("loaderAddress")
        
        if (key == null || loaderAddress == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 key 或 loaderAddress 参数")
            )
            return
        }
        
        // 创建数据加载函数
        val loader = {
            val promise = Promise.promise<Any>()
            
            vertx.eventBus().request<JsonObject>(loaderAddress, JsonObject()
                .put("key", key)
                .put("namespace", namespace ?: "apix")
            ) { ar ->
                if (ar.succeeded()) {
                    val response = ar.result().body()
                    if (response.getBoolean("success", false)) {
                        promise.complete(response.getValue("result"))
                    } else {
                        promise.fail(response.getString("message", "加载数据失败"))
                    }
                } else {
                    promise.fail(ar.cause())
                }
            }
            
            promise.future()
        }
        
        // 防止缓存击穿
        cacheProtectionManager.preventCacheBreakdown(key, namespace ?: "apix", loader)
            .onSuccess { result ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", result)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "防止缓存击穿失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理防止缓存雪崩请求。
     */
    private fun handlePreventCacheAvalanche(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val request = message.body()
        val key = request.getString("key")
        val namespace = request.getString("namespace")
        val ttl = request.getLong("ttl", 0)
        val loaderAddress = request.getString("loaderAddress")
        
        if (key == null || loaderAddress == null) {
            message.reply(JsonObject()
                .put("success", false)
                .put("message", "缺少 key 或 loaderAddress 参数")
            )
            return
        }
        
        // 创建数据加载函数
        val loader = {
            val promise = Promise.promise<Any>()
            
            vertx.eventBus().request<JsonObject>(loaderAddress, JsonObject()
                .put("key", key)
                .put("namespace", namespace ?: "apix")
            ) { ar ->
                if (ar.succeeded()) {
                    val response = ar.result().body()
                    if (response.getBoolean("success", false)) {
                        promise.complete(response.getValue("result"))
                    } else {
                        promise.fail(response.getString("message", "加载数据失败"))
                    }
                } else {
                    promise.fail(ar.cause())
                }
            }
            
            promise.future()
        }
        
        // 防止缓存雪崩
        cacheProtectionManager.preventCacheAvalanche(key, namespace ?: "apix", ttl, loader)
            .onSuccess { result ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", result)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "防止缓存雪崩失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理获取布隆过滤器状态请求。
     */
    private fun handleGetBloomFilterStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        // 获取布隆过滤器状态
        val status = bloomFilterManager.getStatus()
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", status)
        )
    }
    
    /**
     * 处理添加到布隆过滤器请求。
     */
    private fun handleAddToBloomFilter(message: io.vertx.core.eventbus.Message<JsonObject>) {
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
        
        // 添加到布隆过滤器
        bloomFilterManager.add(key, namespace ?: "apix")
            .onSuccess {
                message.reply(JsonObject()
                    .put("success", true)
                )
            }
            .onFailure { cause ->
                message.reply(JsonObject()
                    .put("success", false)
                    .put("message", "添加到布隆过滤器失败: ${cause.message}")
                )
            }
    }
    
    /**
     * 处理检查布隆过滤器请求。
     */
    private fun handleCheckBloomFilter(message: io.vertx.core.eventbus.Message<JsonObject>) {
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
        
        // 检查布隆过滤器
        val mightContain = bloomFilterManager.mightContain(key, namespace ?: "apix")
        
        message.reply(JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("key", key)
                .put("namespace", namespace ?: "apix")
                .put("mightContain", mightContain)
            )
        )
    }
}
