package com.louloulin.apix.scaling.loadbalance

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

/**
 * 高级负载均衡器
 *
 * 提供多种负载均衡策略，包括基于延迟的路由、健康检查和自动故障转移
 */
class AdvancedLoadBalancer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(AdvancedLoadBalancer::class.java)

    // 节点信息
    private val nodes = ConcurrentHashMap<String, NodeInfo>()

    // 节点组
    private val nodeGroups = ConcurrentHashMap<String, MutableList<String>>()

    // 轮询计数器
    private val roundRobinCounters = ConcurrentHashMap<String, AtomicInteger>()

    // 健康检查间隔（默认10秒）
    private var healthCheckInterval = TimeUnit.SECONDS.toMillis(10)

    // 健康检查超时（默认5秒）
    private var healthCheckTimeout = TimeUnit.SECONDS.toMillis(5)

    // 健康检查定时器ID
    private var healthCheckTimerId: Long = -1

    // 统计信息更新间隔（默认1秒）
    private var statsUpdateInterval = TimeUnit.SECONDS.toMillis(1)

    // 统计信息更新定时器ID
    private var statsUpdateTimerId: Long = -1

    // 负载均衡策略
    enum class Strategy {
        ROUND_ROBIN,        // 轮询
        LEAST_CONNECTIONS,  // 最少连接
        LEAST_RESPONSE_TIME, // 最低响应时间
        WEIGHTED_RANDOM,    // 加权随机
        CONSISTENT_HASH     // 一致性哈希
    }

    // 节点状态
    enum class NodeState {
        HEALTHY,    // 健康
        DEGRADED,   // 性能下降
        UNHEALTHY,  // 不健康
        DISABLED    // 禁用
    }

    // 节点信息
    data class NodeInfo(
        val id: String,                 // 节点ID
        val host: String,               // 主机名
        val port: Int,                  // 端口
        var weight: Int = 100,          // 权重（1-100）
        var state: NodeState = NodeState.HEALTHY, // 状态
        var activeConnections: AtomicInteger = AtomicInteger(0), // 活跃连接数
        var totalRequests: AtomicLong = AtomicLong(0),          // 总请求数
        var successfulRequests: AtomicLong = AtomicLong(0),     // 成功请求数
        var failedRequests: AtomicLong = AtomicLong(0),         // 失败请求数
        var responseTimeSum: AtomicLong = AtomicLong(0),        // 响应时间总和（毫秒）
        var lastResponseTime: AtomicLong = AtomicLong(0),       // 最后一次响应时间（毫秒）
        var lastChecked: AtomicLong = AtomicLong(0),            // 最后一次健康检查时间
        var consecutiveFailures: AtomicInteger = AtomicInteger(0), // 连续失败次数
        var metadata: JsonObject = JsonObject()                 // 元数据
    ) {
        // 获取平均响应时间
        fun getAverageResponseTime(): Long {
            val total = totalRequests.get()
            return if (total > 0) responseTimeSum.get() / total else 0
        }

        // 获取成功率
        fun getSuccessRate(): Double {
            val total = totalRequests.get()
            return if (total > 0) successfulRequests.get().toDouble() / total else 0.0
        }

        // 获取节点分数（用于负载均衡决策）
        fun getScore(): Double {
            // 如果节点不健康，返回0分
            if (state != NodeState.HEALTHY && state != NodeState.DEGRADED) {
                return 0.0
            }

            // 计算响应时间分数（响应时间越低，分数越高）
            val avgResponseTime = getAverageResponseTime()
            val responseTimeScore = if (avgResponseTime > 0) {
                1000.0 / avgResponseTime
            } else {
                1.0
            }

            // 计算成功率分数
            val successRateScore = getSuccessRate()

            // 计算连接数分数（连接数越少，分数越高）
            val connectionsScore = 1.0 / (activeConnections.get() + 1)

            // 计算权重分数
            val weightScore = weight / 100.0

            // 综合分数（可以根据需要调整各因素的权重）
            return (responseTimeScore * 0.4 + successRateScore * 0.3 + connectionsScore * 0.2 + weightScore * 0.1)
        }

        // 转换为JSON对象
        fun toJson(): JsonObject {
            return JsonObject()
                .put("id", id)
                .put("host", host)
                .put("port", port)
                .put("weight", weight)
                .put("state", state.name)
                .put("activeConnections", activeConnections.get())
                .put("totalRequests", totalRequests.get())
                .put("successfulRequests", successfulRequests.get())
                .put("failedRequests", failedRequests.get())
                .put("averageResponseTime", getAverageResponseTime())
                .put("lastResponseTime", lastResponseTime.get())
                .put("successRate", getSuccessRate())
                .put("lastChecked", lastChecked.get())
                .put("consecutiveFailures", consecutiveFailures.get())
                .put("score", getScore())
                .put("metadata", metadata)
        }
    }

    init {
        // 启动健康检查定时器
        startHealthCheckTimer()

        // 启动统计信息更新定时器
        startStatsUpdateTimer()
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
        val promise = Promise.promise<Void>()

        try {
            // 创建节点信息
            val nodeInfo = NodeInfo(
                id = nodeId,
                host = host,
                port = port,
                weight = weight.coerceIn(1, 100),
                metadata = metadata
            )

            // 添加节点
            nodes[nodeId] = nodeInfo

            // 如果指定了节点组，将节点添加到组
            if (groupId != null) {
                val group = nodeGroups.computeIfAbsent(groupId) { mutableListOf() }
                if (!group.contains(nodeId)) {
                    group.add(nodeId)
                }
            }

            // 初始化轮询计数器
            roundRobinCounters.computeIfAbsent(nodeId) { AtomicInteger(0) }

            // 执行健康检查
            checkNodeHealth(nodeInfo)
                .onSuccess {
                    logger.info("节点添加成功: {}", nodeId)
                    promise.complete()
                }
                .onFailure { err ->
                    // 即使健康检查失败，也认为节点添加成功，但状态为不健康
                    logger.warn("节点添加成功，但健康检查失败: {}", nodeId, err)
                    nodeInfo.state = NodeState.UNHEALTHY
                    promise.complete()
                }
        } catch (e: Exception) {
            logger.error("添加节点失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 移除节点
     *
     * @param nodeId 节点ID
     * @return 操作结果的Future
     */
    fun removeNode(nodeId: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 移除节点
            val nodeInfo = nodes.remove(nodeId)

            if (nodeInfo != null) {
                // 从所有节点组中移除节点
                for (group in nodeGroups.values) {
                    group.remove(nodeId)
                }

                // 移除轮询计数器
                roundRobinCounters.remove(nodeId)

                logger.info("节点移除成功: {}", nodeId)
            } else {
                logger.warn("节点不存在: {}", nodeId)
            }

            promise.complete()
        } catch (e: Exception) {
            logger.error("移除节点失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 更新节点状态
     *
     * @param nodeId 节点ID
     * @param state 节点状态
     * @return 操作结果的Future
     */
    fun updateNodeState(nodeId: String, state: NodeState): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val nodeInfo = nodes[nodeId]

            if (nodeInfo != null) {
                // 更新节点状态
                nodeInfo.state = state
                logger.info("节点状态更新成功: {} -> {}", nodeId, state)
                promise.complete()
            } else {
                logger.warn("节点不存在: {}", nodeId)
                promise.fail("节点不存在: $nodeId")
            }
        } catch (e: Exception) {
            logger.error("更新节点状态失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 更新节点权重
     *
     * @param nodeId 节点ID
     * @param weight 权重（1-100）
     * @return 操作结果的Future
     */
    fun updateNodeWeight(nodeId: String, weight: Int): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val nodeInfo = nodes[nodeId]

            if (nodeInfo != null) {
                // 更新节点权重
                nodeInfo.weight = weight.coerceIn(1, 100)
                logger.info("节点权重更新成功: {} -> {}", nodeId, weight)
                promise.complete()
            } else {
                logger.warn("节点不存在: {}", nodeId)
                promise.fail("节点不存在: $nodeId")
            }
        } catch (e: Exception) {
            logger.error("更新节点权重失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 选择节点
     *
     * @param groupId 节点组ID（可选）
     * @param strategy 负载均衡策略
     * @param key 一致性哈希的键（仅在使用一致性哈希策略时需要）
     * @return 包含所选节点的Future
     */
    fun selectNode(
        groupId: String? = null,
        strategy: Strategy = Strategy.ROUND_ROBIN,
        key: String? = null
    ): Future<NodeInfo?> {
        val promise = Promise.promise<NodeInfo?>()

        try {
            // 获取可用节点
            val availableNodes = getAvailableNodes(groupId)

            if (availableNodes.isEmpty()) {
                logger.warn("没有可用节点")
                promise.complete(null)
                return promise.future()
            }

            // 根据策略选择节点
            val selectedNode = when (strategy) {
                Strategy.ROUND_ROBIN -> selectNodeRoundRobin(availableNodes, groupId)
                Strategy.LEAST_CONNECTIONS -> selectNodeLeastConnections(availableNodes)
                Strategy.LEAST_RESPONSE_TIME -> selectNodeLeastResponseTime(availableNodes)
                Strategy.WEIGHTED_RANDOM -> selectNodeWeightedRandom(availableNodes)
                Strategy.CONSISTENT_HASH -> selectNodeConsistentHash(availableNodes, key)
            }

            if (selectedNode != null) {
                // 增加活跃连接数
                selectedNode.activeConnections.incrementAndGet()
                promise.complete(selectedNode)
            } else {
                logger.warn("无法选择节点")
                promise.complete(null)
            }
        } catch (e: Exception) {
            logger.error("选择节点失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取可用节点
     *
     * @param groupId 节点组ID（可选）
     * @return 可用节点列表
     */
    private fun getAvailableNodes(groupId: String?): List<NodeInfo> {
        // 如果指定了节点组，获取组内节点
        val nodeIds = if (groupId != null) {
            nodeGroups[groupId] ?: emptyList()
        } else {
            nodes.keys.toList()
        }

        // 过滤出健康和性能下降的节点
        return nodeIds.mapNotNull { nodes[it] }
            .filter { it.state == NodeState.HEALTHY || it.state == NodeState.DEGRADED }
    }

    /**
     * 使用轮询策略选择节点
     *
     * @param availableNodes 可用节点列表
     * @param groupId 节点组ID（可选）
     * @return 所选节点
     */
    private fun selectNodeRoundRobin(availableNodes: List<NodeInfo>, groupId: String?): NodeInfo? {
        if (availableNodes.isEmpty()) {
            return null
        }

        // 使用组ID或默认ID作为计数器键
        val counterKey = groupId ?: "default"

        // 获取或创建计数器
        val counter = roundRobinCounters.computeIfAbsent(counterKey) { AtomicInteger(0) }

        // 递增计数器并取模
        val index = counter.getAndIncrement() % availableNodes.size

        return availableNodes[index]
    }

    /**
     * 使用最少连接策略选择节点
     *
     * @param availableNodes 可用节点列表
     * @return 所选节点
     */
    private fun selectNodeLeastConnections(availableNodes: List<NodeInfo>): NodeInfo? {
        if (availableNodes.isEmpty()) {
            return null
        }

        // 选择活跃连接数最少的节点
        return availableNodes.minByOrNull { it.activeConnections.get() }
    }

    /**
     * 使用最低响应时间策略选择节点
     *
     * @param availableNodes 可用节点列表
     * @return 所选节点
     */
    private fun selectNodeLeastResponseTime(availableNodes: List<NodeInfo>): NodeInfo? {
        if (availableNodes.isEmpty()) {
            return null
        }

        // 选择平均响应时间最低的节点
        return availableNodes.minByOrNull { it.getAverageResponseTime() }
    }

    /**
     * 使用加权随机策略选择节点
     *
     * @param availableNodes 可用节点列表
     * @return 所选节点
     */
    private fun selectNodeWeightedRandom(availableNodes: List<NodeInfo>): NodeInfo? {
        if (availableNodes.isEmpty()) {
            return null
        }

        // 计算总权重
        val totalWeight = availableNodes.sumOf { it.weight }

        // 生成随机数
        val random = ThreadLocalRandom.current().nextInt(totalWeight)

        // 选择节点
        var currentWeight = 0
        for (node in availableNodes) {
            currentWeight += node.weight
            if (random < currentWeight) {
                return node
            }
        }

        // 如果没有选择到节点（理论上不应该发生），返回第一个节点
        return availableNodes.first()
    }

    /**
     * 使用一致性哈希策略选择节点
     *
     * @param availableNodes 可用节点列表
     * @param key 哈希键
     * @return 所选节点
     */
    private fun selectNodeConsistentHash(availableNodes: List<NodeInfo>, key: String?): NodeInfo? {
        if (availableNodes.isEmpty() || key == null) {
            return null
        }

        // 计算哈希值
        val hash = key.hashCode()

        // 使用哈希值选择节点
        val index = Math.abs(hash % availableNodes.size)

        return availableNodes[index]
    }

    /**
     * 记录请求完成
     *
     * @param nodeId 节点ID
     * @param success 是否成功
     * @param responseTime 响应时间（毫秒）
     */
    fun recordRequestCompletion(nodeId: String, success: Boolean, responseTime: Long) {
        val nodeInfo = nodes[nodeId] ?: return

        // 减少活跃连接数
        nodeInfo.activeConnections.decrementAndGet()

        // 增加总请求数
        nodeInfo.totalRequests.incrementAndGet()

        // 更新响应时间
        nodeInfo.responseTimeSum.addAndGet(responseTime)
        nodeInfo.lastResponseTime.set(responseTime)

        if (success) {
            // 增加成功请求数
            nodeInfo.successfulRequests.incrementAndGet()

            // 重置连续失败次数
            nodeInfo.consecutiveFailures.set(0)
        } else {
            // 增加失败请求数
            nodeInfo.failedRequests.incrementAndGet()

            // 增加连续失败次数
            val consecutiveFailures = nodeInfo.consecutiveFailures.incrementAndGet()

            // 如果连续失败次数超过阈值，将节点标记为不健康
            if (consecutiveFailures >= 3) {
                nodeInfo.state = NodeState.UNHEALTHY
                logger.warn("节点连续失败次数过多，标记为不健康: {}", nodeId)
            }
        }
    }

    /**
     * 启动健康检查定时器
     */
    private fun startHealthCheckTimer() {
        healthCheckTimerId = vertx.setPeriodic(healthCheckInterval) { _ ->
            performHealthChecks()
        }
    }

    /**
     * 执行健康检查
     */
    private fun performHealthChecks() {
        logger.debug("开始执行健康检查")

        for (nodeInfo in nodes.values) {
            // 跳过禁用的节点
            if (nodeInfo.state == NodeState.DISABLED) {
                continue
            }

            checkNodeHealth(nodeInfo)
                .onSuccess { healthy ->
                    if (healthy) {
                        // 如果节点之前是不健康的，现在恢复了，更新状态
                        if (nodeInfo.state == NodeState.UNHEALTHY) {
                            nodeInfo.state = NodeState.HEALTHY
                            logger.info("节点恢复健康: {}", nodeInfo.id)
                        }
                    } else {
                        // 如果节点不健康，更新状态
                        if (nodeInfo.state != NodeState.UNHEALTHY) {
                            nodeInfo.state = NodeState.UNHEALTHY
                            logger.warn("节点不健康: {}", nodeInfo.id)
                        }
                    }
                }
                .onFailure { err ->
                    // 健康检查失败，标记节点为不健康
                    nodeInfo.state = NodeState.UNHEALTHY
                    logger.warn("节点健康检查失败: {}", nodeInfo.id, err)
                }
        }
    }

    /**
     * 检查节点健康
     *
     * @param nodeInfo 节点信息
     * @return 包含健康状态的Future
     */
    private fun checkNodeHealth(nodeInfo: NodeInfo): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        // 更新最后检查时间
        nodeInfo.lastChecked.set(System.currentTimeMillis())

        // 创建健康检查请求
        val options = io.vertx.core.http.HttpClientOptions()
            .setConnectTimeout(healthCheckTimeout.toInt())
            .setIdleTimeout(healthCheckTimeout.toInt() / 1000)

        val client = vertx.createHttpClient(options)

        // 发送健康检查请求
        client.request(io.vertx.core.http.HttpMethod.GET, nodeInfo.port, nodeInfo.host, "/health")
            .compose { request ->
                request.putHeader("Connection", "close")
                request.end()

                request.response()
            }
            .compose { response ->
                if (response.statusCode() == 200) {
                    response.body()
                        .map { buffer ->
                            try {
                                val json = JsonObject(buffer)
                                val status = json.getString("status", "")

                                // 检查状态
                                when (status.lowercase()) {
                                    "up", "ok", "healthy" -> true
                                    "degraded" -> {
                                        nodeInfo.state = NodeState.DEGRADED
                                        true
                                    }
                                    else -> false
                                }
                            } catch (e: Exception) {
                                // 如果响应不是JSON或没有status字段，只要状态码是200，就认为是健康的
                                true
                            }
                        }
                } else {
                    Future.succeededFuture(false)
                }
            }
            .onSuccess { healthy ->
                promise.complete(healthy)
            }
            .onFailure { err ->
                promise.fail(err)
            }
            .eventually<Void> { _ ->
                client.close()
                Future.succeededFuture()
            }

        return promise.future()
    }

    /**
     * 启动统计信息更新定时器
     */
    private fun startStatsUpdateTimer() {
        statsUpdateTimerId = vertx.setPeriodic(statsUpdateInterval) { _ ->
            updateNodeStats()
        }
    }

    /**
     * 更新节点统计信息
     */
    private fun updateNodeStats() {
        // 计算每个节点的分数
        for (nodeInfo in nodes.values) {
            // 根据统计信息动态调整节点权重
            adjustNodeWeight(nodeInfo)
        }
    }

    /**
     * 动态调整节点权重
     *
     * @param nodeInfo 节点信息
     */
    private fun adjustNodeWeight(nodeInfo: NodeInfo) {
        // 只调整健康和性能下降的节点的权重
        if (nodeInfo.state != NodeState.HEALTHY && nodeInfo.state != NodeState.DEGRADED) {
            return
        }

        // 获取成功率
        val successRate = nodeInfo.getSuccessRate()

        // 获取平均响应时间
        val avgResponseTime = nodeInfo.getAverageResponseTime()

        // 基础权重（默认为80）
        var weight = 80

        // 根据成功率调整权重
        weight += (successRate * 20).toInt() // 最多增加20

        // 根据响应时间调整权重
        if (avgResponseTime > 0) {
            // 响应时间越低，权重越高
            val responseTimeFactor = Math.min(1000.0 / avgResponseTime, 10.0)
            weight += responseTimeFactor.toInt() // 最多增加10
        }

        // 确保权重在1-100范围内
        weight = weight.coerceIn(1, 100)

        // 更新节点权重
        nodeInfo.weight = weight
    }

    /**
     * 获取所有节点信息
     *
     * @return 节点信息列表
     */
    fun getAllNodes(): List<JsonObject> {
        return nodes.values.map { it.toJson() }
    }

    /**
     * 获取节点组信息
     *
     * @return 节点组信息
     */
    fun getNodeGroups(): JsonObject {
        val result = JsonObject()

        for ((groupId, nodeIds) in nodeGroups) {
            val nodesArray = JsonArray()

            for (nodeId in nodeIds) {
                val nodeInfo = nodes[nodeId]
                if (nodeInfo != null) {
                    nodesArray.add(nodeInfo.toJson())
                }
            }

            result.put(groupId, nodesArray)
        }

        return result
    }

    /**
     * 设置健康检查间隔
     *
     * @param interval 间隔（毫秒）
     */
    fun setHealthCheckInterval(interval: Long) {
        if (interval > 0) {
            this.healthCheckInterval = interval

            // 重新启动健康检查定时器
            if (healthCheckTimerId != -1L) {
                vertx.cancelTimer(healthCheckTimerId)
                startHealthCheckTimer()
            }
        }
    }

    /**
     * 设置健康检查超时
     *
     * @param timeout 超时（毫秒）
     */
    fun setHealthCheckTimeout(timeout: Long) {
        if (timeout > 0) {
            this.healthCheckTimeout = timeout
        }
    }

    /**
     * 设置统计信息更新间隔
     *
     * @param interval 间隔（毫秒）
     */
    fun setStatsUpdateInterval(interval: Long) {
        if (interval > 0) {
            this.statsUpdateInterval = interval

            // 重新启动统计信息更新定时器
            if (statsUpdateTimerId != -1L) {
                vertx.cancelTimer(statsUpdateTimerId)
                startStatsUpdateTimer()
            }
        }
    }

    /**
     * 关闭负载均衡器
     */
    fun close() {
        // 停止定时器
        if (healthCheckTimerId != -1L) {
            vertx.cancelTimer(healthCheckTimerId)
            healthCheckTimerId = -1L
        }

        if (statsUpdateTimerId != -1L) {
            vertx.cancelTimer(statsUpdateTimerId)
            statsUpdateTimerId = -1L
        }

        // 清空数据
        nodes.clear()
        nodeGroups.clear()
        roundRobinCounters.clear()
    }
}
