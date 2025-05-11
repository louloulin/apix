package com.louloulin.apix.cdn

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * Cloudflare CDN提供商实现
 */
class CloudflareCDNProvider(private val vertx: Vertx) : CDNProvider {
    private val logger = LoggerFactory.getLogger(CloudflareCDNProvider::class.java)
    
    // Cloudflare API配置
    private val apiConfig = AtomicReference<JsonObject>(JsonObject())
    
    // Cloudflare API客户端
    private lateinit var webClient: WebClient
    
    // API令牌
    private var apiToken: String = ""
    
    // 区域ID
    private var zoneId: String = ""
    
    // API基础URL
    private val baseUrl = "https://api.cloudflare.com/client/v4"
    
    /**
     * 获取CDN提供商名称
     */
    override fun getName(): String {
        return "cloudflare"
    }
    
    /**
     * 初始化CDN提供商
     * 
     * @param config CDN配置
     * @return Future<Void> 初始化结果
     */
    override fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化Cloudflare CDN提供商")
        
        val promise = Promise.promise<Void>()
        
        try {
            // 保存配置
            this.apiConfig.set(config)
            
            // 获取API令牌
            this.apiToken = config.getString("apiToken", "")
            if (this.apiToken.isEmpty()) {
                return Future.failedFuture("Cloudflare API令牌未配置")
            }
            
            // 获取区域ID
            this.zoneId = config.getString("zoneId", "")
            if (this.zoneId.isEmpty()) {
                return Future.failedFuture("Cloudflare 区域ID未配置")
            }
            
            // 创建Web客户端
            val clientOptions = WebClientOptions()
                .setUserAgent("APIX-Gateway")
                .setKeepAlive(true)
                .setMaxPoolSize(10)
            
            this.webClient = WebClient.create(vertx, clientOptions)
            
            // 验证API令牌
            validateApiToken()
                .onSuccess {
                    logger.info("Cloudflare API令牌验证成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("Cloudflare API令牌验证失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("初始化Cloudflare CDN提供商失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 验证API令牌
     * 
     * @return Future<Void> 验证结果
     */
    private fun validateApiToken(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        webClient.get("$baseUrl/user/tokens/verify")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Content-Type", "application/json")
            .send()
            .onSuccess { response ->
                val body = response.bodyAsJsonObject()
                val success = body.getBoolean("success", false)
                
                if (success) {
                    promise.complete()
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "API令牌验证失败" else errors.getJsonObject(0).getString("message", "API令牌验证失败")
                    promise.fail(errorMessage)
                }
            }
            .onFailure { cause ->
                promise.fail(cause)
            }
        
        return promise.future()
    }
    
    /**
     * 获取CDN状态
     * 
     * @return Future<JsonObject> CDN状态
     */
    override fun getStatus(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        webClient.get("$baseUrl/zones/$zoneId")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Content-Type", "application/json")
            .send()
            .onSuccess { response ->
                val body = response.bodyAsJsonObject()
                val success = body.getBoolean("success", false)
                
                if (success) {
                    val result = body.getJsonObject("result", JsonObject())
                    val status = result.getString("status", "")
                    val healthy = status == "active"
                    
                    promise.complete(JsonObject()
                        .put("healthy", healthy)
                        .put("status", status)
                        .put("name", result.getString("name", ""))
                        .put("plan", result.getJsonObject("plan", JsonObject()).getString("name", ""))
                    )
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "获取区域状态失败" else errors.getJsonObject(0).getString("message", "获取区域状态失败")
                    
                    promise.complete(JsonObject()
                        .put("healthy", false)
                        .put("error", errorMessage)
                    )
                }
            }
            .onFailure { cause ->
                promise.complete(JsonObject()
                    .put("healthy", false)
                    .put("error", cause.message)
                )
            }
        
        return promise.future()
    }
    
    /**
     * 刷新CDN缓存
     * 
     * @param urls 需要刷新的URL列表
     * @return Future<JsonObject> 刷新结果
     */
    override fun purgeCache(urls: List<String>): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        if (urls.isEmpty()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "URL列表为空")
            )
        }
        
        val requestBody = JsonObject()
            .put("files", JsonArray(urls))
        
        webClient.post("$baseUrl/zones/$zoneId/purge_cache")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                val body = response.bodyAsJsonObject()
                val success = body.getBoolean("success", false)
                
                if (success) {
                    val result = body.getJsonObject("result", JsonObject())
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("id", result.getString("id", ""))
                    )
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "刷新缓存失败" else errors.getJsonObject(0).getString("message", "刷新缓存失败")
                    
                    promise.complete(JsonObject()
                        .put("success", false)
                        .put("error", errorMessage)
                    )
                }
            }
            .onFailure { cause ->
                promise.complete(JsonObject()
                    .put("success", false)
                    .put("error", cause.message)
                )
            }
        
        return promise.future()
    }
    
    /**
     * 预热CDN缓存
     * 
     * @param urls 需要预热的URL列表
     * @return Future<JsonObject> 预热结果
     */
    override fun prewarmCache(urls: List<String>): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        if (urls.isEmpty()) {
            return Future.succeededFuture(JsonObject()
                .put("success", false)
                .put("error", "URL列表为空")
            )
        }
        
        // Cloudflare不直接支持缓存预热，我们可以通过发送请求来间接预热
        // 这里我们使用异步方式发送多个请求
        val futures = urls.map { url ->
            val requestPromise = Promise.promise<Boolean>()
            
            webClient.getAbs(url)
                .send()
                .onSuccess {
                    requestPromise.complete(true)
                }
                .onFailure {
                    requestPromise.complete(false)
                }
            
            requestPromise.future()
        }
        
        Future.all(futures)
            .onSuccess {
                promise.complete(JsonObject()
                    .put("success", true)
                    .put("message", "缓存预热请求已发送")
                )
            }
            .onFailure { cause ->
                promise.complete(JsonObject()
                    .put("success", false)
                    .put("error", cause.message)
                )
            }
        
        return promise.future()
    }
    
    /**
     * 更新CDN配置
     * 
     * @param config 新的CDN配置
     * @return Future<Void> 更新结果
     */
    override fun updateConfig(config: JsonObject): Future<Void> {
        logger.info("更新Cloudflare CDN配置")
        
        // 关闭当前客户端
        close()
            .compose {
                // 重新初始化
                initialize(config)
            }
        
        return Future.succeededFuture()
    }
    
    /**
     * 关闭CDN连接
     * 
     * @return Future<Void> 关闭结果
     */
    override fun close(): Future<Void> {
        logger.info("关闭Cloudflare CDN提供商")
        
        if (::webClient.isInitialized) {
            webClient.close()
        }
        
        return Future.succeededFuture()
    }
}
