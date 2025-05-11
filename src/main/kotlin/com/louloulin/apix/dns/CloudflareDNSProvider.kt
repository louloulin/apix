package com.louloulin.apix.dns

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
 * Cloudflare DNS提供商实现
 */
class CloudflareDNSProvider(private val vertx: Vertx) : SmartDNSProvider {
    private val logger = LoggerFactory.getLogger(CloudflareDNSProvider::class.java)
    
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
     * 获取DNS提供商名称
     */
    override fun getName(): String {
        return "cloudflare"
    }
    
    /**
     * 初始化DNS提供商
     * 
     * @param config DNS配置
     * @return Future<Void> 初始化结果
     */
    override fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化Cloudflare DNS提供商")
        
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
            logger.error("初始化Cloudflare DNS提供商失败", e)
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
     * 获取DNS状态
     * 
     * @return Future<JsonObject> DNS状态
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
     * 创建DNS记录
     * 
     * @param record DNS记录
     * @return Future<JsonObject> 创建结果
     */
    override fun createRecord(record: DNSRecord): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 构建请求体
        val requestBody = JsonObject()
            .put("type", record.type.toString())
            .put("name", record.name)
            .put("content", record.content)
            .put("ttl", record.ttl)
            .put("proxied", record.proxied)
        
        // 添加优先级（仅适用于MX和SRV记录）
        if (record.type == DNSRecordType.MX || record.type == DNSRecordType.SRV) {
            requestBody.put("priority", record.priority)
        }
        
        webClient.post("$baseUrl/zones/$zoneId/dns_records")
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
                        .put("name", result.getString("name", ""))
                        .put("type", result.getString("type", ""))
                        .put("content", result.getString("content", ""))
                        .put("ttl", result.getInteger("ttl", 0))
                    )
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "创建DNS记录失败" else errors.getJsonObject(0).getString("message", "创建DNS记录失败")
                    
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
     * 更新DNS记录
     * 
     * @param recordId 记录ID
     * @param record DNS记录
     * @return Future<JsonObject> 更新结果
     */
    override fun updateRecord(recordId: String, record: DNSRecord): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 构建请求体
        val requestBody = JsonObject()
            .put("type", record.type.toString())
            .put("name", record.name)
            .put("content", record.content)
            .put("ttl", record.ttl)
            .put("proxied", record.proxied)
        
        // 添加优先级（仅适用于MX和SRV记录）
        if (record.type == DNSRecordType.MX || record.type == DNSRecordType.SRV) {
            requestBody.put("priority", record.priority)
        }
        
        webClient.put("$baseUrl/zones/$zoneId/dns_records/$recordId")
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
                        .put("name", result.getString("name", ""))
                        .put("type", result.getString("type", ""))
                        .put("content", result.getString("content", ""))
                        .put("ttl", result.getInteger("ttl", 0))
                    )
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "更新DNS记录失败" else errors.getJsonObject(0).getString("message", "更新DNS记录失败")
                    
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
     * 删除DNS记录
     * 
     * @param recordId 记录ID
     * @return Future<JsonObject> 删除结果
     */
    override fun deleteRecord(recordId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        webClient.delete("$baseUrl/zones/$zoneId/dns_records/$recordId")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Content-Type", "application/json")
            .send()
            .onSuccess { response ->
                val body = response.bodyAsJsonObject()
                val success = body.getBoolean("success", false)
                
                if (success) {
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("id", recordId)
                    )
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "删除DNS记录失败" else errors.getJsonObject(0).getString("message", "删除DNS记录失败")
                    
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
     * 获取DNS记录
     * 
     * @param recordId 记录ID
     * @return Future<JsonObject> DNS记录
     */
    override fun getRecord(recordId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        webClient.get("$baseUrl/zones/$zoneId/dns_records/$recordId")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Content-Type", "application/json")
            .send()
            .onSuccess { response ->
                val body = response.bodyAsJsonObject()
                val success = body.getBoolean("success", false)
                
                if (success) {
                    val result = body.getJsonObject("result", JsonObject())
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("record", result)
                    )
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "获取DNS记录失败" else errors.getJsonObject(0).getString("message", "获取DNS记录失败")
                    
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
     * 获取所有DNS记录
     * 
     * @return Future<JsonObject> 所有DNS记录
     */
    override fun getAllRecords(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        webClient.get("$baseUrl/zones/$zoneId/dns_records")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Content-Type", "application/json")
            .send()
            .onSuccess { response ->
                val body = response.bodyAsJsonObject()
                val success = body.getBoolean("success", false)
                
                if (success) {
                    val result = body.getJsonArray("result", JsonArray())
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("records", result)
                    )
                } else {
                    val errors = body.getJsonArray("errors", JsonArray())
                    val errorMessage = if (errors.isEmpty) "获取DNS记录失败" else errors.getJsonObject(0).getString("message", "获取DNS记录失败")
                    
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
     * 更新DNS配置
     * 
     * @param config 新的DNS配置
     * @return Future<Void> 更新结果
     */
    override fun updateConfig(config: JsonObject): Future<Void> {
        logger.info("更新Cloudflare DNS配置")
        
        // 关闭当前客户端
        close()
            .compose {
                // 重新初始化
                initialize(config)
            }
        
        return Future.succeededFuture()
    }
    
    /**
     * 关闭DNS连接
     * 
     * @return Future<Void> 关闭结果
     */
    override fun close(): Future<Void> {
        logger.info("关闭Cloudflare DNS提供商")
        
        if (::webClient.isInitialized) {
            webClient.close()
        }
        
        return Future.succeededFuture()
    }
}
