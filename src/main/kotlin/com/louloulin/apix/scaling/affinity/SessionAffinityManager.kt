package com.louloulin.apix.scaling.affinity

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.common.Constants
import com.louloulin.apix.scaling.loadbalance.AdvancedLoadBalancer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32

/**
 * 会话亲和性管理器
 * 
 * 提供会话亲和性功能，确保来自同一会话的请求被路由到同一节点
 */
class SessionAffinityManager(
    private val vertx: Vertx,
    private val loadBalancer: AdvancedLoadBalancer
) {
    private val logger = LoggerFactory.getLogger(SessionAffinityManager::class.java)
    
    // 会话到节点的映射
    private val sessionToNodeMap = ConcurrentHashMap<String, String>()
    
    // 节点到会话的映射
    private val nodeToSessionsMap = ConcurrentHashMap<String, MutableSet<String>>()
    
    // 一致性哈希环
    private val consistentHashRing = ConsistentHashRing<String>(100)
    
    // 会话复制间隔（默认1分钟）
    private var sessionReplicationInterval = TimeUnit.MINUTES.toMillis(1)
    
    // 会话复制定时器ID
    private var sessionReplicationTimerId: Long = -1
    
    // 会话过期时间（默认30分钟）
    private var sessionExpirationTime = TimeUnit.MINUTES.toMillis(30)
    
    // 会话清理间隔（默认5分钟）
    private var sessionCleanupInterval = TimeUnit.MINUTES.toMillis(5)
    
    // 会话清理定时器ID
    private var sessionCleanupTimerId: Long = -1
    
    // 会话访问时间
    private val sessionAccessTimes = ConcurrentHashMap<String, Long>()
    
    // 亲和性策略
    enum class AffinityStrategy {
        STICKY_SESSION,     // 粘性会话（使用会话ID）
        CONSISTENT_HASH,    // 一致性哈希（使用会话ID）
        CLIENT_IP,          // 客户端IP
        CUSTOM_HEADER       // 自定义请求头
    }
    
    // 亲和性配置
    data class AffinityConfig(
        var enabled: Boolean = true,                      // 是否启用亲和性
        var strategy: AffinityStrategy = AffinityStrategy.STICKY_SESSION, // 亲和性策略
        var customHeaderName: String = "",                // 自定义请求头名称
        var failoverEnabled: Boolean = true,              // 是否启用故障转移
        var replicationEnabled: Boolean = true,           // 是否启用会话复制
        var replicationInterval: Long = TimeUnit.MINUTES.toMillis(1), // 复制间隔
        var sessionExpirationTime: Long = TimeUnit.MINUTES.toMillis(30), // 会话过期时间
        var sessionCleanupInterval: Long = TimeUnit.MINUTES.toMillis(5)  // 会话清理间隔
    )
    
    // 亲和性配置
    private val config = AffinityConfig()
    
    init {
        // 启动会话复制定时器
        if (config.replicationEnabled) {
            startSessionReplicationTimer()
        }
        
        // 启动会话清理定时器
        startSessionCleanupTimer()
    }
    
    /**
     * 配置会话亲和性
     * 
     * @param config 亲和性配置
     */
    fun configure(config: AffinityConfig) {
        this.config.enabled = config.enabled
        this.config.strategy = config.strategy
        this.config.customHeaderName = config.customHeaderName
        this.config.failoverEnabled = config.failoverEnabled
        this.config.replicationEnabled = config.replicationEnabled
        
        // 更新会话复制间隔
        if (config.replicationInterval > 0 && config.replicationInterval != this.sessionReplicationInterval) {
            this.sessionReplicationInterval = config.replicationInterval
            
            // 重新启动会话复制定时器
            if (config.replicationEnabled) {
                if (sessionReplicationTimerId != -1L) {
                    vertx.cancelTimer(sessionReplicationTimerId)
                }
                startSessionReplicationTimer()
            }
        }
        
        // 更新会话过期时间
        if (config.sessionExpirationTime > 0) {
            this.sessionExpirationTime = config.sessionExpirationTime
        }
        
        // 更新会话清理间隔
        if (config.sessionCleanupInterval > 0 && config.sessionCleanupInterval != this.sessionCleanupInterval) {
            this.sessionCleanupInterval = config.sessionCleanupInterval
            
            // 重新启动会话清理定时器
            if (sessionCleanupTimerId != -1L) {
                vertx.cancelTimer(sessionCleanupTimerId)
            }
            startSessionCleanupTimer()
        }
        
        // 如果启用了会话复制，但定时器未启动，则启动定时器
        if (config.replicationEnabled && sessionReplicationTimerId == -1L) {
            startSessionReplicationTimer()
        }
        
        // 如果禁用了会话复制，但定时器已启动，则停止定时器
        if (!config.replicationEnabled && sessionReplicationTimerId != -1L) {
            vertx.cancelTimer(sessionReplicationTimerId)
            sessionReplicationTimerId = -1L
        }
    }
    
    /**
     * 获取配置
     * 
     * @return 亲和性配置
     */
    fun getConfig(): AffinityConfig {
        return config
    }
    
    /**
     * 根据会话亲和性选择节点
     * 
     * @param context 路由上下文
     * @param groupId 节点组ID（可选）
     * @return 包含所选节点的Future
     */
    fun selectNodeWithAffinity(
        context: RoutingContext,
        groupId: String? = null
    ): Future<AdvancedLoadBalancer.NodeInfo?> {
        val promise = Promise.promise<AdvancedLoadBalancer.NodeInfo?>()
        
        try {
            // 如果亲和性未启用，使用负载均衡器选择节点
            if (!config.enabled) {
                loadBalancer.selectNode(groupId)
                    .onSuccess { nodeInfo ->
                        promise.complete(nodeInfo)
                    }
                    .onFailure { err ->
                        promise.fail(err)
                    }
                return promise.future()
            }
            
            // 获取亲和性键
            val affinityKey = getAffinityKey(context)
            
            if (affinityKey != null) {
                // 检查是否已有映射
                val nodeId = sessionToNodeMap[affinityKey]
                
                if (nodeId != null) {
                    // 获取节点信息
                    val nodes = loadBalancer.getAllNodes()
                    val nodeInfo = nodes.find { it.getString("id") == nodeId }
                    
                    if (nodeInfo != null) {
                        // 检查节点状态
                        val state = nodeInfo.getString("state")
                        
                        if (state == "HEALTHY" || state == "DEGRADED") {
                            // 节点健康，使用现有映射
                            loadBalancer.selectNode(groupId, AdvancedLoadBalancer.Strategy.CONSISTENT_HASH, nodeId)
                                .onSuccess { selectedNode ->
                                    if (selectedNode != null) {
                                        // 更新会话访问时间
                                        sessionAccessTimes[affinityKey] = System.currentTimeMillis()
                                        promise.complete(selectedNode)
                                    } else {
                                        // 无法选择节点，使用故障转移
                                        handleFailover(affinityKey, groupId)
                                            .onSuccess { failoverNode ->
                                                promise.complete(failoverNode)
                                            }
                                            .onFailure { err ->
                                                promise.fail(err)
                                            }
                                    }
                                }
                                .onFailure { err ->
                                    // 选择节点失败，使用故障转移
                                    handleFailover(affinityKey, groupId)
                                        .onSuccess { failoverNode ->
                                            promise.complete(failoverNode)
                                        }
                                        .onFailure { failoverErr ->
                                            promise.fail(failoverErr)
                                        }
                                }
                            return promise.future()
                        } else if (config.failoverEnabled) {
                            // 节点不健康，使用故障转移
                            handleFailover(affinityKey, groupId)
                                .onSuccess { failoverNode ->
                                    promise.complete(failoverNode)
                                }
                                .onFailure { err ->
                                    promise.fail(err)
                                }
                            return promise.future()
                        }
                    } else if (config.failoverEnabled) {
                        // 节点不存在，使用故障转移
                        handleFailover(affinityKey, groupId)
                            .onSuccess { failoverNode ->
                                promise.complete(failoverNode)
                            }
                            .onFailure { err ->
                                promise.fail(err)
                            }
                        return promise.future()
                    }
                }
                
                // 没有现有映射或节点不可用，创建新映射
                createNewAffinity(affinityKey, groupId)
                    .onSuccess { nodeInfo ->
                        promise.complete(nodeInfo)
                    }
                    .onFailure { err ->
                        promise.fail(err)
                    }
            } else {
                // 无法获取亲和性键，使用负载均衡器选择节点
                loadBalancer.selectNode(groupId)
                    .onSuccess { nodeInfo ->
                        promise.complete(nodeInfo)
                    }
                    .onFailure { err ->
                        promise.fail(err)
                    }
            }
        } catch (e: Exception) {
            logger.error("选择节点失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取亲和性键
     * 
     * @param context 路由上下文
     * @return 亲和性键
     */
    private fun getAffinityKey(context: RoutingContext): String? {
        return when (config.strategy) {
            AffinityStrategy.STICKY_SESSION -> {
                // 使用会话ID
                context.request().getHeader(Constants.SESSION_ID_HEADER)
            }
            AffinityStrategy.CONSISTENT_HASH -> {
                // 使用会话ID
                context.request().getHeader(Constants.SESSION_ID_HEADER)
            }
            AffinityStrategy.CLIENT_IP -> {
                // 使用客户端IP
                context.request().remoteAddress()?.host()
            }
            AffinityStrategy.CUSTOM_HEADER -> {
                // 使用自定义请求头
                if (config.customHeaderName.isNotEmpty()) {
                    context.request().getHeader(config.customHeaderName)
                } else {
                    null
                }
            }
        }
    }
    
    /**
     * 处理故障转移
     * 
     * @param affinityKey 亲和性键
     * @param groupId 节点组ID（可选）
     * @return 包含所选节点的Future
     */
    private fun handleFailover(
        affinityKey: String,
        groupId: String? = null
    ): Future<AdvancedLoadBalancer.NodeInfo?> {
        val promise = Promise.promise<AdvancedLoadBalancer.NodeInfo?>()
        
        // 移除现有映射
        val oldNodeId = sessionToNodeMap.remove(affinityKey)
        
        if (oldNodeId != null) {
            // 从节点到会话的映射中移除
            nodeToSessionsMap[oldNodeId]?.remove(affinityKey)
        }
        
        // 创建新映射
        createNewAffinity(affinityKey, groupId)
            .onSuccess { nodeInfo ->
                promise.complete(nodeInfo)
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 创建新的亲和性映射
     * 
     * @param affinityKey 亲和性键
     * @param groupId 节点组ID（可选）
     * @return 包含所选节点的Future
     */
    private fun createNewAffinity(
        affinityKey: String,
        groupId: String? = null
    ): Future<AdvancedLoadBalancer.NodeInfo?> {
        val promise = Promise.promise<AdvancedLoadBalancer.NodeInfo?>()
        
        // 根据策略选择节点
        val strategy = when (config.strategy) {
            AffinityStrategy.CONSISTENT_HASH -> AdvancedLoadBalancer.Strategy.CONSISTENT_HASH
            else -> AdvancedLoadBalancer.Strategy.LEAST_CONNECTIONS
        }
        
        loadBalancer.selectNode(groupId, strategy, affinityKey)
            .onSuccess { nodeInfo ->
                if (nodeInfo != null) {
                    // 创建映射
                    sessionToNodeMap[affinityKey] = nodeInfo.id
                    
                    // 添加到节点到会话的映射
                    val sessions = nodeToSessionsMap.computeIfAbsent(nodeInfo.id) { mutableSetOf() }
                    sessions.add(affinityKey)
                    
                    // 更新会话访问时间
                    sessionAccessTimes[affinityKey] = System.currentTimeMillis()
                    
                    promise.complete(nodeInfo)
                } else {
                    promise.complete(null)
                }
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 启动会话复制定时器
     */
    private fun startSessionReplicationTimer() {
        sessionReplicationTimerId = vertx.setPeriodic(sessionReplicationInterval) { _ ->
            replicateSessions()
        }
    }
    
    /**
     * 复制会话
     */
    private fun replicateSessions() {
        if (!config.replicationEnabled) {
            return
        }
        
        logger.debug("开始复制会话")
        
        // 获取所有节点
        val nodes = loadBalancer.getAllNodes()
        
        // 过滤出健康的节点
        val healthyNodes = nodes.filter { 
            it.getString("state") == "HEALTHY" || it.getString("state") == "DEGRADED" 
        }
        
        if (healthyNodes.isEmpty()) {
            logger.warn("没有健康的节点，跳过会话复制")
            return
        }
        
        // 对于每个节点，复制其会话到其他节点
        for (node in healthyNodes) {
            val nodeId = node.getString("id")
            val sessions = nodeToSessionsMap[nodeId] ?: continue
            
            // 如果没有会话，跳过
            if (sessions.isEmpty()) {
                continue
            }
            
            // 获取其他健康节点
            val otherNodes = healthyNodes.filter { it.getString("id") != nodeId }
            
            // 如果没有其他健康节点，跳过
            if (otherNodes.isEmpty()) {
                continue
            }
            
            // 复制会话到其他节点
            for (session in sessions) {
                // 使用一致性哈希选择备份节点
                val backupNodeIndex = Math.abs(session.hashCode() % otherNodes.size)
                val backupNode = otherNodes[backupNodeIndex]
                val backupNodeId = backupNode.getString("id")
                
                // 添加到备份节点的会话列表
                val backupSessions = nodeToSessionsMap.computeIfAbsent(backupNodeId) { mutableSetOf() }
                backupSessions.add(session)
                
                logger.debug("会话 {} 从节点 {} 复制到节点 {}", session, nodeId, backupNodeId)
            }
        }
    }
    
    /**
     * 启动会话清理定时器
     */
    private fun startSessionCleanupTimer() {
        sessionCleanupTimerId = vertx.setPeriodic(sessionCleanupInterval) { _ ->
            cleanupExpiredSessions()
        }
    }
    
    /**
     * 清理过期会话
     */
    private fun cleanupExpiredSessions() {
        logger.debug("开始清理过期会话")
        
        val now = System.currentTimeMillis()
        val expiredSessions = mutableListOf<String>()
        
        // 找出过期的会话
        for ((session, accessTime) in sessionAccessTimes) {
            if (now - accessTime > sessionExpirationTime) {
                expiredSessions.add(session)
            }
        }
        
        // 删除过期的会话
        for (session in expiredSessions) {
            // 从会话访问时间映射中移除
            sessionAccessTimes.remove(session)
            
            // 从会话到节点的映射中移除
            val nodeId = sessionToNodeMap.remove(session)
            
            // 从节点到会话的映射中移除
            if (nodeId != null) {
                nodeToSessionsMap[nodeId]?.remove(session)
            }
            
            logger.debug("删除过期会话: {}", session)
        }
        
        logger.debug("清理了 {} 个过期会话", expiredSessions.size)
    }
    
    /**
     * 获取节点的会话数
     * 
     * @param nodeId 节点ID
     * @return 会话数
     */
    fun getNodeSessionCount(nodeId: String): Int {
        return nodeToSessionsMap[nodeId]?.size ?: 0
    }
    
    /**
     * 获取所有会话映射
     * 
     * @return 会话映射
     */
    fun getAllSessionMappings(): JsonObject {
        val result = JsonObject()
        
        for ((session, nodeId) in sessionToNodeMap) {
            result.put(session, nodeId)
        }
        
        return result
    }
    
    /**
     * 获取节点的会话
     * 
     * @param nodeId 节点ID
     * @return 会话列表
     */
    fun getNodeSessions(nodeId: String): List<String> {
        return nodeToSessionsMap[nodeId]?.toList() ?: emptyList()
    }
    
    /**
     * 关闭会话亲和性管理器
     */
    fun close() {
        // 停止定时器
        if (sessionReplicationTimerId != -1L) {
            vertx.cancelTimer(sessionReplicationTimerId)
            sessionReplicationTimerId = -1L
        }
        
        if (sessionCleanupTimerId != -1L) {
            vertx.cancelTimer(sessionCleanupTimerId)
            sessionCleanupTimerId = -1L
        }
        
        // 清空数据
        sessionToNodeMap.clear()
        nodeToSessionsMap.clear()
        sessionAccessTimes.clear()
    }
}
