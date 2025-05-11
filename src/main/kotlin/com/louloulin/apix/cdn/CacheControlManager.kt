package com.louloulin.apix.cdn

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 缓存控制管理器
 * 负责CDN缓存的精细控制，包括缓存标签、缓存刷新和缓存预热
 * 实现plan7.md中的3.1.2节"缓存控制"功能
 */
class CacheControlManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(CacheControlManager::class.java)
    
    // 缓存控制配置
    private val cacheConfig = AtomicReference<JsonObject>(JsonObject())
    
    // 缓存控制是否启用
    private val cacheEnabled = AtomicBoolean(false)
    
    // CDN管理器
    private lateinit var cdnManager: CDNManager
    
    // 缓存标签映射
    private val cacheTags = ConcurrentHashMap<String, MutableSet<String>>()
    
    /**
     * 获取CacheControlManager实例
     */
    companion object {
        private var instance: CacheControlManager? = null
        
        @Synchronized
        fun getInstance(vertx: Vertx): CacheControlManager {
            if (instance == null) {
                instance = CacheControlManager(vertx)
            }
            return instance!!
        }
    }
    
    /**
     * 初始化缓存控制管理器
     * 
     * @param config 缓存控制配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化缓存控制管理器")
        
        val promise = Promise.promise<Void>()
        
        try {
            // 保存配置
            this.cacheConfig.set(config)
            
            // 获取缓存控制启用状态
            val enabled = config.getBoolean("enabled", true)
            this.cacheEnabled.set(enabled)
            
            if (!enabled) {
                logger.info("缓存控制功能已禁用")
                promise.complete()
                return promise.future()
            }
            
            // 获取CDN管理器
            cdnManager = CDNManager.getInstance(vertx)
            
            // 加载缓存标签
            loadCacheTags(config)
            
            logger.info("缓存控制管理器初始化完成")
            promise.complete()
        } catch (e: Exception) {
            logger.error("缓存控制管理器初始化失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 加载缓存标签
     * 
     * @param config 缓存控制配置
     */
    private fun loadCacheTags(config: JsonObject) {
        val tagsConfig = config.getJsonObject("tags", JsonObject())
        
        for (tagName in tagsConfig.fieldNames()) {
            val urls = tagsConfig.getJsonArray(tagName, JsonArray()).map { it.toString() }.toMutableSet()
            cacheTags[tagName] = urls
        }
        
        logger.info("加载了 {} 个缓存标签", cacheTags.size)
    }
    
    /**
     * 获取缓存控制状态
     * 
     * @return JsonObject 缓存控制状态
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", cacheEnabled.get())
        
        val tags = JsonObject()
        for ((tagName, urls) in cacheTags) {
            tags.put(tagName, JsonArray(urls.toList()))
        }
        
        status.put("tags", tags)
        
        return status
    }
    
    /**
     * 添加URL到缓存标签
     * 
     * @param tagName 标签名称
     * @param urls URL列表
     * @return Future<JsonObject> 添加结果
     */
    fun addUrlsToTag(tagName: String, urls: List<String>): Future<JsonObject> {
        if (!cacheEnabled.get()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "缓存控制功能已禁用")
            )
        }
        
        if (urls.isEmpty()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "URL列表为空")
            )
        }
        
        // 获取或创建标签
        val tagUrls = cacheTags.computeIfAbsent(tagName) { mutableSetOf() }
        
        // 添加URL
        tagUrls.addAll(urls)
        
        // 更新配置
        updateTagsConfig()
        
        return Future.succeededFuture(JsonObject()
            .put("success", true)
            .put("tagName", tagName)
            .put("urlCount", tagUrls.size)
        )
    }
    
    /**
     * 从缓存标签中移除URL
     * 
     * @param tagName 标签名称
     * @param urls URL列表
     * @return Future<JsonObject> 移除结果
     */
    fun removeUrlsFromTag(tagName: String, urls: List<String>): Future<JsonObject> {
        if (!cacheEnabled.get()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "缓存控制功能已禁用")
            )
        }
        
        if (urls.isEmpty()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "URL列表为空")
            )
        }
        
        // 获取标签
        val tagUrls = cacheTags[tagName]
        
        if (tagUrls == null) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "标签不存在: $tagName")
            )
        }
        
        // 移除URL
        tagUrls.removeAll(urls)
        
        // 如果标签为空，移除标签
        if (tagUrls.isEmpty()) {
            cacheTags.remove(tagName)
        }
        
        // 更新配置
        updateTagsConfig()
        
        return Future.succeededFuture(JsonObject()
            .put("success", true)
            .put("tagName", tagName)
            .put("urlCount", tagUrls.size)
        )
    }
    
    /**
     * 刷新缓存标签
     * 
     * @param tagName 标签名称
     * @return Future<JsonObject> 刷新结果
     */
    fun purgeTag(tagName: String): Future<JsonObject> {
        if (!cacheEnabled.get()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "缓存控制功能已禁用")
            )
        }
        
        // 获取标签
        val tagUrls = cacheTags[tagName]
        
        if (tagUrls == null || tagUrls.isEmpty()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "标签不存在或为空: $tagName")
            )
        }
        
        // 刷新缓存
        return cdnManager.purgeCache(tagUrls.toList())
    }
    
    /**
     * 预热缓存标签
     * 
     * @param tagName 标签名称
     * @return Future<JsonObject> 预热结果
     */
    fun prewarmTag(tagName: String): Future<JsonObject> {
        if (!cacheEnabled.get()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "缓存控制功能已禁用")
            )
        }
        
        // 获取标签
        val tagUrls = cacheTags[tagName]
        
        if (tagUrls == null || tagUrls.isEmpty()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "标签不存在或为空: $tagName")
            )
        }
        
        // 预热缓存
        return cdnManager.prewarmCache(tagUrls.toList())
    }
    
    /**
     * 更新标签配置
     */
    private fun updateTagsConfig() {
        val tagsConfig = JsonObject()
        
        for ((tagName, urls) in cacheTags) {
            tagsConfig.put(tagName, JsonArray(urls.toList()))
        }
        
        // 更新配置
        val config = cacheConfig.get().copy()
        config.put("tags", tagsConfig)
        cacheConfig.set(config)
    }
    
    /**
     * 更新缓存控制配置
     * 
     * @param config 新的缓存控制配置
     * @return Future<Void> 更新结果
     */
    fun updateConfig(config: JsonObject): Future<Void> {
        logger.info("更新缓存控制配置")
        
        // 重新初始化
        return initialize(config)
    }
    
    /**
     * 关闭缓存控制管理器
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void> {
        logger.info("关闭缓存控制管理器")
        
        // 清空缓存标签
        cacheTags.clear()
        
        return Future.succeededFuture()
    }
}
