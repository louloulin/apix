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
 * DNSimple DNS提供商实现
 */
class DNSimpleDNSProvider(private val vertx: Vertx) : SmartDNSProvider {
    private val logger = LoggerFactory.getLogger(DNSimpleDNSProvider::class.java)
    
    // DNSimple API配置
    private val apiConfig = AtomicReference<JsonObject>(JsonObject())
    
    // DNSimple API客户端
    private lateinit var webClient: WebClient
    
    // API令牌
    private var apiToken: String = ""
    
    // 账户ID
    private var accountId: String = ""
    
    // 域名
    private var domain: String = ""
    
    // API基础URL
    private val baseUrl = "https://api.dnsimple.com/v2"
    
    /**
     * 获取DNS提供商名称
     */
    override fun getName(): String {
        return "dnsimple"
    }
    
    /**
     * 初始化DNS提供商
     * 
     * @param config DNS配置
     * @return Future<Void> 初始化结果
     */
    override fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化DNSimple DNS提供商")
        
        val promise = Promise.promise<Void>()
        
        try {
            // 保存配置
            this.apiConfig.set(config)
            
            // 获取API令牌
            this.apiToken = config.getString("apiToken", "")
            if (this.apiToken.isEmpty()) {
                return Future.failedFuture("DNSimple API令牌未配置")
            }
            
            // 获取账户ID
            this.accountId = config.getString("accountId", "")
            if (this.accountId.isEmpty()) {
                return Future.failedFuture("DNSimple账户ID未配置")
            }
            
            // 获取域名
            this.domain = config.getString("domain", "")
            if (this.domain.isEmpty()) {
                return Future.failedFuture("DNSimple域名未配置")
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
                    logger.info("DNSimple API令牌验证成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("DNSimple API令牌验证失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("初始化DNSimple DNS提供商失败", e)
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
        
        webClient.get("$baseUrl/whoami")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Accept", "application/json")
            .send()
            .onSuccess { response ->
                if (response.statusCode() == 200) {
                    promise.complete()
                } else {
                    val errorMessage = "API令牌验证失败: ${response.statusCode()} ${response.statusMessage()}"
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
        
        webClient.get("$baseUrl/accounts/$accountId/domains/$domain")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Accept", "application/json")
            .send()
            .onSuccess { response ->
                if (response.statusCode() == 200) {
                    val body = response.bodyAsJsonObject()
                    val data = body.getJsonObject("data", JsonObject())
                    
                    promise.complete(JsonObject()
                        .put("healthy", true)
                        .put("status", "active")
                        .put("name", data.getString("name", ""))
                        .put("state", data.getString("state", ""))
                    )
                } else {
                    promise.complete(JsonObject()
                        .put("healthy", false)
                        .put("error", "获取域名状态失败: ${response.statusCode()} ${response.statusMessage()}")
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
            .put("name", record.name)
            .put("type", record.type.toString())
            .put("content", record.content)
            .put("ttl", record.ttl)
        
        // 添加优先级（仅适用于MX和SRV记录）
        if (record.type == DNSRecordType.MX || record.type == DNSRecordType.SRV) {
            requestBody.put("priority", record.priority)
        }
        
        webClient.post("$baseUrl/accounts/$accountId/zones/$domain/records")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Accept", "application/json")
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                if (response.statusCode() == 201) {
                    val body = response.bodyAsJsonObject()
                    val data = body.getJsonObject("data", JsonObject())
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("id", data.getString("id", ""))
                        .put("name", data.getString("name", ""))
                        .put("type", data.getString("type", ""))
                        .put("content", data.getString("content", ""))
                        .put("ttl", data.getInteger("ttl", 0))
                    )
                } else {
                    promise.complete(JsonObject()
                        .put("success", false)
                        .put("error", "创建DNS记录失败: ${response.statusCode()} ${response.statusMessage()}")
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
            .put("name", record.name)
            .put("type", record.type.toString())
            .put("content", record.content)
            .put("ttl", record.ttl)
        
        // 添加优先级（仅适用于MX和SRV记录）
        if (record.type == DNSRecordType.MX || record.type == DNSRecordType.SRV) {
            requestBody.put("priority", record.priority)
        }
        
        webClient.patch("$baseUrl/accounts/$accountId/zones/$domain/records/$recordId")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Accept", "application/json")
            .putHeader("Content-Type", "application/json")
            .sendJsonObject(requestBody)
            .onSuccess { response ->
                if (response.statusCode() == 200) {
                    val body = response.bodyAsJsonObject()
                    val data = body.getJsonObject("data", JsonObject())
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("id", data.getString("id", ""))
                        .put("name", data.getString("name", ""))
                        .put("type", data.getString("type", ""))
                        .put("content", data.getString("content", ""))
                        .put("ttl", data.getInteger("ttl", 0))
                    )
                } else {
                    promise.complete(JsonObject()
                        .put("success", false)
                        .put("error", "更新DNS记录失败: ${response.statusCode()} ${response.statusMessage()}")
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
        
        webClient.delete("$baseUrl/accounts/$accountId/zones/$domain/records/$recordId")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Accept", "application/json")
            .send()
            .onSuccess { response ->
                if (response.statusCode() == 204) {
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("id", recordId)
                    )
                } else {
                    promise.complete(JsonObject()
                        .put("success", false)
                        .put("error", "删除DNS记录失败: ${response.statusCode()} ${response.statusMessage()}")
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
        
        webClient.get("$baseUrl/accounts/$accountId/zones/$domain/records/$recordId")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Accept", "application/json")
            .send()
            .onSuccess { response ->
                if (response.statusCode() == 200) {
                    val body = response.bodyAsJsonObject()
                    val data = body.getJsonObject("data", JsonObject())
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("record", data)
                    )
                } else {
                    promise.complete(JsonObject()
                        .put("success", false)
                        .put("error", "获取DNS记录失败: ${response.statusCode()} ${response.statusMessage()}")
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
        
        webClient.get("$baseUrl/accounts/$accountId/zones/$domain/records")
            .putHeader("Authorization", "Bearer $apiToken")
            .putHeader("Accept", "application/json")
            .send()
            .onSuccess { response ->
                if (response.statusCode() == 200) {
                    val body = response.bodyAsJsonObject()
                    val data = body.getJsonArray("data", JsonArray())
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("records", data)
                    )
                } else {
                    promise.complete(JsonObject()
                        .put("success", false)
                        .put("error", "获取DNS记录失败: ${response.statusCode()} ${response.statusMessage()}")
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
        logger.info("更新DNSimple DNS配置")
        
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
        logger.info("关闭DNSimple DNS提供商")
        
        if (::webClient.isInitialized) {
            webClient.close()
        }
        
        return Future.succeededFuture()
    }
}
