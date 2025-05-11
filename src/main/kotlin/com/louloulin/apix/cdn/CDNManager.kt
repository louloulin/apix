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
 * CDN管理器
 * 负责管理多个CDN提供商，实现CDN故障转移和负载均衡
 * 实现plan7.md中的3.1节"CDN集成架构"功能
 */
class CDNManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(CDNManager::class.java)
    
    // CDN配置
    private val cdnConfig = AtomicReference<JsonObject>(JsonObject())
    
    // CDN是否启用
    private val cdnEnabled = AtomicBoolean(false)
    
    // CDN提供商列表
    private val cdnProviders = ConcurrentHashMap<String, CDNProvider>()
    
    // 主CDN提供商
    private val primaryProvider = AtomicReference<String>()
    
    // CDN健康状态
    private val cdnHealth = ConcurrentHashMap<String, Boolean>()
    
    // 健康检查定时器ID
    private var healthCheckTimerId: Long = -1
    
    /**
     * 获取CDNManager实例
     */
    companion object {
        private var instance: CDNManager? = null
        
        @Synchronized
        fun getInstance(vertx: Vertx): CDNManager {
            if (instance == null) {
                instance = CDNManager(vertx)
            }
            return instance!!
        }
    }
    
    /**
     * 初始化CDN管理器
     * 
     * @param config CDN配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化CDN管理器")
        
        val promise = Promise.promise<Void>()
        
        try {
            // 保存配置
            this.cdnConfig.set(config)
            
            // 获取CDN启用状态
            val enabled = config.getBoolean("enabled", false)
            this.cdnEnabled.set(enabled)
            
            if (!enabled) {
                logger.info("CDN功能已禁用")
                promise.complete()
                return promise.future()
            }
            
            // 加载CDN提供商
            loadCDNProviders(config)
                .onSuccess {
                    // 启动健康检查
                    startHealthCheck()
                    
                    logger.info("CDN管理器初始化完成")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("CDN管理器初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("CDN管理器初始化失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 加载CDN提供商
     * 
     * @param config CDN配置
     * @return Future<Void> 加载结果
     */
    private fun loadCDNProviders(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 获取CDN提供商配置
            val providersConfig = config.getJsonArray("providers", JsonArray())
            
            if (providersConfig.isEmpty) {
                logger.warn("未配置CDN提供商")
                promise.complete()
                return promise.future()
            }
            
            // 获取主CDN提供商
            val primary = config.getString("primary", "")
            if (primary.isNotEmpty()) {
                primaryProvider.set(primary)
            }
            
            // 加载每个CDN提供商
            var loadedCount = 0
            val totalCount = providersConfig.size()
            
            for (i in 0 until totalCount) {
                val providerConfig = providersConfig.getJsonObject(i)
                val name = providerConfig.getString("name", "")
                val type = providerConfig.getString("type", "")
                val enabled = providerConfig.getBoolean("enabled", true)
                
                if (name.isEmpty() || type.isEmpty()) {
                    logger.warn("CDN提供商配置无效: {}", providerConfig.encode())
                    continue
                }
                
                if (!enabled) {
                    logger.info("CDN提供商已禁用: {}", name)
                    continue
                }
                
                // 创建CDN提供商
                val provider = createCDNProvider(type)
                
                if (provider == null) {
                    logger.warn("不支持的CDN提供商类型: {}", type)
                    continue
                }
                
                // 初始化CDN提供商
                provider.initialize(providerConfig)
                    .onSuccess {
                        // 添加到提供商列表
                        cdnProviders[name] = provider
                        cdnHealth[name] = true
                        
                        logger.info("CDN提供商初始化成功: {}", name)
                        
                        // 如果未设置主提供商，使用第一个作为主提供商
                        if (primaryProvider.get() == null && cdnProviders.size == 1) {
                            primaryProvider.set(name)
                        }
                        
                        // 检查是否所有提供商都已加载
                        loadedCount++
                        if (loadedCount == totalCount) {
                            promise.complete()
                        }
                    }
                    .onFailure { cause ->
                        logger.error("CDN提供商初始化失败: {}", name, cause)
                        
                        // 检查是否所有提供商都已加载
                        loadedCount++
                        if (loadedCount == totalCount) {
                            // 即使有提供商初始化失败，也继续完成初始化
                            if (cdnProviders.isNotEmpty()) {
                                promise.complete()
                            } else {
                                promise.fail("所有CDN提供商初始化失败")
                            }
                        }
                    }
            }
            
            // 如果没有提供商需要初始化，直接完成
            if (totalCount == 0) {
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("加载CDN提供商失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 创建CDN提供商
     * 
     * @param type CDN提供商类型
     * @return CDNProvider? CDN提供商实例
     */
    private fun createCDNProvider(type: String): CDNProvider? {
        return when (type.lowercase()) {
            "cloudflare" -> CloudflareCDNProvider(vertx)
            "akamai" -> AkamaiCDNProvider(vertx)
            "cloudfront" -> CloudFrontCDNProvider(vertx)
            else -> null
        }
    }
    
    /**
     * 启动健康检查
     */
    private fun startHealthCheck() {
        // 获取健康检查间隔
        val interval = cdnConfig.get().getLong("healthCheckInterval", 30000L)
        
        // 启动定时健康检查
        healthCheckTimerId = vertx.setPeriodic(interval) {
            checkCDNHealth()
        }
        
        logger.info("CDN健康检查已启动，间隔: {}ms", interval)
    }
    
    /**
     * 检查CDN健康状态
     */
    private fun checkCDNHealth() {
        for ((name, provider) in cdnProviders) {
            provider.getStatus()
                .onSuccess { status ->
                    val healthy = status.getBoolean("healthy", false)
                    cdnHealth[name] = healthy
                    
                    if (healthy) {
                        logger.debug("CDN提供商健康: {}", name)
                    } else {
                        logger.warn("CDN提供商不健康: {}", name)
                        
                        // 如果主提供商不健康，切换到健康的提供商
                        if (name == primaryProvider.get()) {
                            switchToHealthyProvider()
                        }
                    }
                }
                .onFailure { cause ->
                    logger.error("CDN提供商健康检查失败: {}", name, cause)
                    cdnHealth[name] = false
                    
                    // 如果主提供商健康检查失败，切换到健康的提供商
                    if (name == primaryProvider.get()) {
                        switchToHealthyProvider()
                    }
                }
        }
    }
    
    /**
     * 切换到健康的提供商
     */
    private fun switchToHealthyProvider() {
        // 查找健康的提供商
        val healthyProvider = cdnHealth.entries.find { it.value }?.key
        
        if (healthyProvider != null) {
            logger.info("切换主CDN提供商: {} -> {}", primaryProvider.get(), healthyProvider)
            primaryProvider.set(healthyProvider)
        } else {
            logger.warn("没有健康的CDN提供商可用")
        }
    }
    
    /**
     * 获取CDN状态
     * 
     * @return JsonObject CDN状态
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", cdnEnabled.get())
            .put("primary", primaryProvider.get())
        
        val providers = JsonObject()
        for ((name, provider) in cdnProviders) {
            providers.put(name, JsonObject()
                .put("healthy", cdnHealth.getOrDefault(name, false))
            )
        }
        
        status.put("providers", providers)
        
        return status
    }
    
    /**
     * 刷新CDN缓存
     * 
     * @param urls 需要刷新的URL列表
     * @return Future<JsonObject> 刷新结果
     */
    fun purgeCache(urls: List<String>): Future<JsonObject> {
        if (!cdnEnabled.get()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "CDN功能已禁用")
            )
        }
        
        val primary = primaryProvider.get()
        if (primary == null || !cdnProviders.containsKey(primary)) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "没有可用的CDN提供商")
            )
        }
        
        return cdnProviders[primary]!!.purgeCache(urls)
    }
    
    /**
     * 预热CDN缓存
     * 
     * @param urls 需要预热的URL列表
     * @return Future<JsonObject> 预热结果
     */
    fun prewarmCache(urls: List<String>): Future<JsonObject> {
        if (!cdnEnabled.get()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "CDN功能已禁用")
            )
        }
        
        val primary = primaryProvider.get()
        if (primary == null || !cdnProviders.containsKey(primary)) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "没有可用的CDN提供商")
            )
        }
        
        return cdnProviders[primary]!!.prewarmCache(urls)
    }
    
    /**
     * 更新CDN配置
     * 
     * @param config 新的CDN配置
     * @return Future<Void> 更新结果
     */
    fun updateConfig(config: JsonObject): Future<Void> {
        logger.info("更新CDN配置")
        
        // 停止健康检查
        if (healthCheckTimerId != -1L) {
            vertx.cancelTimer(healthCheckTimerId)
            healthCheckTimerId = -1L
        }
        
        // 关闭所有CDN提供商
        val closePromises = cdnProviders.values.map { it.close() }
        
        return Future.all(closePromises)
            .compose {
                // 清空提供商列表
                cdnProviders.clear()
                cdnHealth.clear()
                
                // 重新初始化
                initialize(config)
            }
    }
    
    /**
     * 关闭CDN管理器
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void> {
        logger.info("关闭CDN管理器")
        
        // 停止健康检查
        if (healthCheckTimerId != -1L) {
            vertx.cancelTimer(healthCheckTimerId)
            healthCheckTimerId = -1L
        }
        
        // 关闭所有CDN提供商
        val closePromises = cdnProviders.values.map { it.close() }
        
        return Future.all(closePromises)
            .map { null }
    }
}
