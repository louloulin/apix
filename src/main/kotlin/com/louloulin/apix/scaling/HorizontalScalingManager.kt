package com.louloulin.apix.scaling

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import com.louloulin.apix.scaling.stateless.StatelessRequestProcessor
import com.louloulin.apix.scaling.loadbalance.AdvancedLoadBalancer
import com.louloulin.apix.scaling.affinity.SessionAffinityManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.common.Constants

/**
 * 水平扩展管理器
 * 
 * 集成无状态设计、负载均衡和会话亲和性，提供完整的水平扩展支持
 */
class HorizontalScalingManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(HorizontalScalingManager::class.java)
    
    // 无状态请求处理器
    private val statelessRequestProcessor = StatelessRequestProcessor(vertx)
    
    // 高级负载均衡器
    private val loadBalancer = AdvancedLoadBalancer(vertx)
    
    // 会话亲和性管理器
    private val sessionAffinityManager = SessionAffinityManager(vertx, loadBalancer)
    
    // 配置
    private val config = ScalingConfig()
    
    /**
     * 水平扩展配置
     */
    data class ScalingConfig(
        var enabled: Boolean = true,                      // 是否启用水平扩展
        var statelessEnabled: Boolean = true,             // 是否启用无状态设计
        var loadBalancingEnabled: Boolean = true,         // 是否启用负载均衡
        var sessionAffinityEnabled: Boolean = true,       // 是否启用会话亲和性
        var defaultStrategy: AdvancedLoadBalancer.Strategy = AdvancedLoadBalancer.Strategy.LEAST_CONNECTIONS, // 默认负载均衡策略
        var healthCheckInterval: Long = 10000,            // 健康检查间隔（毫秒）
        var healthCheckTimeout: Long = 5000,              // 健康检查超时（毫秒）
        var sessionAffinityConfig: SessionAffinityManager.AffinityConfig = SessionAffinityManager.AffinityConfig() // 会话亲和性配置
    )
    
    init {
        // 注册事件总线处理器
        registerEventBusHandlers()
    }
    
    /**
     * 配置水平扩展
     * 
     * @param config 配置
     */
    fun configure(config: ScalingConfig) {
        this.config.enabled = config.enabled
        this.config.statelessEnabled = config.statelessEnabled
        this.config.loadBalancingEnabled = config.loadBalancingEnabled
        this.config.sessionAffinityEnabled = config.sessionAffinityEnabled
        this.config.defaultStrategy = config.defaultStrategy
        
        // 配置负载均衡器
        if (config.healthCheckInterval > 0) {
            this.config.healthCheckInterval = config.healthCheckInterval
            loadBalancer.setHealthCheckInterval(config.healthCheckInterval)
        }
        
        if (config.healthCheckTimeout > 0) {
            this.config.healthCheckTimeout = config.healthCheckTimeout
            loadBalancer.setHealthCheckTimeout(config.healthCheckTimeout)
        }
        
        // 配置会话亲和性
        sessionAffinityManager.configure(config.sessionAffinityConfig)
    }
    
    /**
     * 获取配置
     * 
     * @return 配置
     */
    fun getConfig(): ScalingConfig {
        return config
    }
    
    /**
     * 处理请求
     * 
     * @param context 路由上下文
     * @return 包含处理结果的Future
     */
    fun handleRequest(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 如果水平扩展未启用，直接处理请求
            if (!config.enabled) {
                statelessRequestProcessor.processRequest(context)
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        promise.fail(err)
                    }
                return promise.future()
            }
            
            // 如果启用了会话亲和性，选择节点
            if (config.sessionAffinityEnabled) {
                sessionAffinityManager.selectNodeWithAffinity(context)
                    .compose { nodeInfo ->
                        if (nodeInfo != null) {
                            // 记录所选节点
                            context.put("selectedNode", nodeInfo.id)
                            
                            // 处理请求
                            processRequestWithNode(context, nodeInfo)
                        } else {
                            // 无法选择节点，使用无状态处理
                            statelessRequestProcessor.processRequest(context)
                        }
                    }
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("处理请求失败", err)
                        promise.fail(err)
                    }
            } else if (config.loadBalancingEnabled) {
                // 使用负载均衡选择节点
                loadBalancer.selectNode(null, config.defaultStrategy)
                    .compose { nodeInfo ->
                        if (nodeInfo != null) {
                            // 记录所选节点
                            context.put("selectedNode", nodeInfo.id)
                            
                            // 处理请求
                            processRequestWithNode(context, nodeInfo)
                        } else {
                            // 无法选择节点，使用无状态处理
                            statelessRequestProcessor.processRequest(context)
                        }
                    }
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("处理请求失败", err)
                        promise.fail(err)
                    }
            } else {
                // 使用无状态处理
                statelessRequestProcessor.processRequest(context)
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        promise.fail(err)
                    }
            }
        } catch (e: Exception) {
            logger.error("处理请求失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 使用指定节点处理请求
     * 
     * @param context 路由上下文
     * @param nodeInfo 节点信息
     * @return 包含处理结果的Future
     */
    private fun processRequestWithNode(
        context: RoutingContext,
        nodeInfo: AdvancedLoadBalancer.NodeInfo
    ): Future<Void> {
        val promise = Promise.promise<Void>()
        val startTime = System.currentTimeMillis()
        
        try {
            // 如果是本地节点，直接处理
            if (isLocalNode(nodeInfo)) {
                statelessRequestProcessor.processRequest(context)
                    .onSuccess {
                        // 记录请求完成
                        val endTime = System.currentTimeMillis()
                        loadBalancer.recordRequestCompletion(nodeInfo.id, true, endTime - startTime)
                        
                        promise.complete()
                    }
                    .onFailure { err ->
                        // 记录请求失败
                        val endTime = System.currentTimeMillis()
                        loadBalancer.recordRequestCompletion(nodeInfo.id, false, endTime - startTime)
                        
                        promise.fail(err)
                    }
            } else {
                // 转发请求到远程节点
                forwardRequestToRemoteNode(context, nodeInfo)
                    .onSuccess {
                        // 记录请求完成
                        val endTime = System.currentTimeMillis()
                        loadBalancer.recordRequestCompletion(nodeInfo.id, true, endTime - startTime)
                        
                        promise.complete()
                    }
                    .onFailure { err ->
                        // 记录请求失败
                        val endTime = System.currentTimeMillis()
                        loadBalancer.recordRequestCompletion(nodeInfo.id, false, endTime - startTime)
                        
                        promise.fail(err)
                    }
            }
        } catch (e: Exception) {
            // 记录请求失败
            val endTime = System.currentTimeMillis()
            loadBalancer.recordRequestCompletion(nodeInfo.id, false, endTime - startTime)
            
            logger.error("处理请求失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 检查是否是本地节点
     * 
     * @param nodeInfo 节点信息
     * @return 是否是本地节点
     */
    private fun isLocalNode(nodeInfo: AdvancedLoadBalancer.NodeInfo): Boolean {
        // 检查主机名是否是localhost或127.0.0.1
        val host = nodeInfo.host.lowercase()
        if (host == "localhost" || host == "127.0.0.1") {
            return true
        }
        
        // TODO: 检查是否是本机IP
        
        return false
    }
    
    /**
     * 转发请求到远程节点
     * 
     * @param context 路由上下文
     * @param nodeInfo 节点信息
     * @return 包含处理结果的Future
     */
    private fun forwardRequestToRemoteNode(
        context: RoutingContext,
        nodeInfo: AdvancedLoadBalancer.NodeInfo
    ): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            val request = context.request()
            val client = vertx.createHttpClient()
            
            // 创建请求
            client.request(request.method(), nodeInfo.port, nodeInfo.host, request.uri())
                .compose { clientRequest ->
                    // 复制请求头
                    for (header in request.headers()) {
                        clientRequest.putHeader(header.key, header.value)
                    }
                    
                    // 添加转发标记，防止循环转发
                    clientRequest.putHeader(Constants.FORWARDED_HEADER, "true")
                    
                    // 如果有请求体，复制请求体
                    if (request.method() == io.vertx.core.http.HttpMethod.POST || 
                        request.method() == io.vertx.core.http.HttpMethod.PUT || 
                        request.method() == io.vertx.core.http.HttpMethod.PATCH) {
                        
                        if (context.body() != null) {
                            clientRequest.end(context.body().buffer())
                        } else {
                            clientRequest.end()
                        }
                    } else {
                        clientRequest.end()
                    }
                    
                    // 获取响应
                    clientRequest.response()
                }
                .compose { clientResponse ->
                    // 复制响应状态码
                    context.response().setStatusCode(clientResponse.statusCode())
                    
                    // 复制响应头
                    for (header in clientResponse.headers()) {
                        context.response().putHeader(header.key, header.value)
                    }
                    
                    // 获取响应体
                    clientResponse.body()
                }
                .onSuccess { body ->
                    // 发送响应体
                    context.response().end(body)
                    client.close()
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("转发请求失败", err)
                    client.close()
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("转发请求失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 添加节点
     * 
     * @param nodeId 节点ID
     * @param host 主机名
     * @param port 端口
     * @param weight 权重（1-100）
     * @param groupId 节点组ID（可选）
     * @param metadata 元数据（可选）
     * @return 操作结果的Future
     */
    fun addNode(
        nodeId: String,
        host: String,
        port: Int,
        weight: Int = 100,
        groupId: String? = null,
        metadata: JsonObject = JsonObject()
    ): Future<Void> {
        return loadBalancer.addNode(nodeId, host, port, weight, groupId, metadata)
    }
    
    /**
     * 移除节点
     * 
     * @param nodeId 节点ID
     * @return 操作结果的Future
     */
    fun removeNode(nodeId: String): Future<Void> {
        return loadBalancer.removeNode(nodeId)
    }
    
    /**
     * 获取所有节点
     * 
     * @return 节点列表
     */
    fun getAllNodes(): List<JsonObject> {
        return loadBalancer.getAllNodes()
    }
    
    /**
     * 获取节点组
     * 
     * @return 节点组
     */
    fun getNodeGroups(): JsonObject {
        return loadBalancer.getNodeGroups()
    }
    
    /**
     * 获取会话亲和性管理器
     * 
     * @return 会话亲和性管理器
     */
    fun getSessionAffinityManager(): SessionAffinityManager {
        return sessionAffinityManager
    }
    
    /**
     * 获取负载均衡器
     * 
     * @return 负载均衡器
     */
    fun getLoadBalancer(): AdvancedLoadBalancer {
        return loadBalancer
    }
    
    /**
     * 注册事件总线处理器
     */
    private fun registerEventBusHandlers() {
        // 注册水平扩展配置处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SCALING_CONFIG) { message ->
            val action = message.body().getString("action")
            
            when (action) {
                "get" -> {
                    // 获取配置
                    val config = getConfig()
                    val response = JsonObject()
                        .put("enabled", config.enabled)
                        .put("statelessEnabled", config.statelessEnabled)
                        .put("loadBalancingEnabled", config.loadBalancingEnabled)
                        .put("sessionAffinityEnabled", config.sessionAffinityEnabled)
                        .put("defaultStrategy", config.defaultStrategy.name)
                        .put("healthCheckInterval", config.healthCheckInterval)
                        .put("healthCheckTimeout", config.healthCheckTimeout)
                        .put("sessionAffinityConfig", JsonObject()
                            .put("enabled", config.sessionAffinityConfig.enabled)
                            .put("strategy", config.sessionAffinityConfig.strategy.name)
                            .put("customHeaderName", config.sessionAffinityConfig.customHeaderName)
                            .put("failoverEnabled", config.sessionAffinityConfig.failoverEnabled)
                            .put("replicationEnabled", config.sessionAffinityConfig.replicationEnabled)
                            .put("replicationInterval", config.sessionAffinityConfig.replicationInterval)
                            .put("sessionExpirationTime", config.sessionAffinityConfig.sessionExpirationTime)
                            .put("sessionCleanupInterval", config.sessionAffinityConfig.sessionCleanupInterval)
                        )
                    
                    message.reply(response)
                }
                
                "set" -> {
                    // 设置配置
                    val configJson = message.body().getJsonObject("config")
                    
                    if (configJson != null) {
                        val newConfig = ScalingConfig(
                            enabled = configJson.getBoolean("enabled", config.enabled),
                            statelessEnabled = configJson.getBoolean("statelessEnabled", config.statelessEnabled),
                            loadBalancingEnabled = configJson.getBoolean("loadBalancingEnabled", config.loadBalancingEnabled),
                            sessionAffinityEnabled = configJson.getBoolean("sessionAffinityEnabled", config.sessionAffinityEnabled),
                            defaultStrategy = try {
                                AdvancedLoadBalancer.Strategy.valueOf(configJson.getString("defaultStrategy", config.defaultStrategy.name))
                            } catch (e: Exception) {
                                config.defaultStrategy
                            },
                            healthCheckInterval = configJson.getLong("healthCheckInterval", config.healthCheckInterval),
                            healthCheckTimeout = configJson.getLong("healthCheckTimeout", config.healthCheckTimeout)
                        )
                        
                        // 会话亲和性配置
                        val affinityConfigJson = configJson.getJsonObject("sessionAffinityConfig")
                        if (affinityConfigJson != null) {
                            val affinityConfig = SessionAffinityManager.AffinityConfig(
                                enabled = affinityConfigJson.getBoolean("enabled", config.sessionAffinityConfig.enabled),
                                strategy = try {
                                    SessionAffinityManager.AffinityStrategy.valueOf(affinityConfigJson.getString("strategy", config.sessionAffinityConfig.strategy.name))
                                } catch (e: Exception) {
                                    config.sessionAffinityConfig.strategy
                                },
                                customHeaderName = affinityConfigJson.getString("customHeaderName", config.sessionAffinityConfig.customHeaderName),
                                failoverEnabled = affinityConfigJson.getBoolean("failoverEnabled", config.sessionAffinityConfig.failoverEnabled),
                                replicationEnabled = affinityConfigJson.getBoolean("replicationEnabled", config.sessionAffinityConfig.replicationEnabled),
                                replicationInterval = affinityConfigJson.getLong("replicationInterval", config.sessionAffinityConfig.replicationInterval),
                                sessionExpirationTime = affinityConfigJson.getLong("sessionExpirationTime", config.sessionAffinityConfig.sessionExpirationTime),
                                sessionCleanupInterval = affinityConfigJson.getLong("sessionCleanupInterval", config.sessionAffinityConfig.sessionCleanupInterval)
                            )
                            
                            newConfig.sessionAffinityConfig = affinityConfig
                        }
                        
                        // 应用配置
                        configure(newConfig)
                        
                        message.reply(JsonObject().put("success", true))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", "Missing config"))
                    }
                }
                
                else -> {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "Unknown action: $action"))
                }
            }
        }
        
        // 注册节点管理处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SCALING_NODES) { message ->
            val action = message.body().getString("action")
            
            when (action) {
                "add" -> {
                    // 添加节点
                    val nodeId = message.body().getString("nodeId")
                    val host = message.body().getString("host")
                    val port = message.body().getInteger("port")
                    val weight = message.body().getInteger("weight", 100)
                    val groupId = message.body().getString("groupId")
                    val metadata = message.body().getJsonObject("metadata", JsonObject())
                    
                    if (nodeId != null && host != null && port != null) {
                        addNode(nodeId, host, port, weight, groupId, metadata)
                            .onSuccess {
                                message.reply(JsonObject().put("success", true))
                            }
                            .onFailure { err ->
                                message.reply(JsonObject()
                                    .put("success", false)
                                    .put("error", err.message))
                            }
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", "Missing required parameters"))
                    }
                }
                
                "remove" -> {
                    // 移除节点
                    val nodeId = message.body().getString("nodeId")
                    
                    if (nodeId != null) {
                        removeNode(nodeId)
                            .onSuccess {
                                message.reply(JsonObject().put("success", true))
                            }
                            .onFailure { err ->
                                message.reply(JsonObject()
                                    .put("success", false)
                                    .put("error", err.message))
                            }
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", "Missing nodeId"))
                    }
                }
                
                "list" -> {
                    // 获取所有节点
                    val nodes = getAllNodes()
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("nodes", nodes))
                }
                
                "groups" -> {
                    // 获取节点组
                    val groups = getNodeGroups()
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("groups", groups))
                }
                
                else -> {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "Unknown action: $action"))
                }
            }
        }
        
        // 注册会话亲和性处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SCALING_AFFINITY) { message ->
            val action = message.body().getString("action")
            
            when (action) {
                "getMappings" -> {
                    // 获取所有会话映射
                    val mappings = sessionAffinityManager.getAllSessionMappings()
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("mappings", mappings))
                }
                
                "getNodeSessions" -> {
                    // 获取节点的会话
                    val nodeId = message.body().getString("nodeId")
                    
                    if (nodeId != null) {
                        val sessions = sessionAffinityManager.getNodeSessions(nodeId)
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("sessions", sessions))
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", "Missing nodeId"))
                    }
                }
                
                else -> {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "Unknown action: $action"))
                }
            }
        }
    }
    
    /**
     * 关闭水平扩展管理器
     */
    fun close() {
        // 关闭会话亲和性管理器
        sessionAffinityManager.close()
        
        // 关闭负载均衡器
        loadBalancer.close()
    }
}
