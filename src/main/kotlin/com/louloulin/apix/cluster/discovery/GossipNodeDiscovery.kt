package com.louloulin.apix.cluster.discovery

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetClient
import io.vertx.core.net.NetClientOptions
import io.vertx.core.net.NetServer
import io.vertx.core.net.NetServerOptions
import org.slf4j.LoggerFactory
import java.net.NetworkInterface
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于Gossip协议的节点发现实现
 *
 * Gossip协议是一种去中心化的节点发现和故障检测协议，具有高可靠性和可扩展性
 */
class GossipNodeDiscovery(
    private val vertx: Vertx,
    private val config: JsonObject = JsonObject()
) {
    private val logger = LoggerFactory.getLogger(GossipNodeDiscovery::class.java)

    // 本地节点信息
    private val localNode = JsonObject()

    // 所有已知节点的信息
    private val nodes = ConcurrentHashMap<String, JsonObject>()

    // 可疑节点（可能已失效）
    private val suspectNodes = ConcurrentHashMap<String, Long>()

    // 确认失效的节点
    private val deadNodes = ConcurrentHashMap<String, Long>()

    // 节点状态
    enum class NodeState {
        ALIVE, SUSPECT, DEAD
    }

    // 配置参数
    private val port: Int
    private val gossipInterval: Long
    private val cleanupInterval: Long
    private val suspectTimeout: Long
    private val deadTimeout: Long
    private val seedNodes: List<JsonObject>
    private val fanout: Int

    // 运行状态
    private val running = AtomicBoolean(false)
    private var gossipTimerId: Long = -1
    private var cleanupTimerId: Long = -1
    private var netServer: NetServer? = null
    private var netClient: NetClient? = null

    // 统计信息
    private val gossipMessagesSent = AtomicLong(0)
    private val gossipMessagesReceived = AtomicLong(0)
    private val nodesDiscovered = AtomicInteger(0)
    private val nodesFailed = AtomicInteger(0)
    private val nodesRecovered = AtomicInteger(0)

    // 节点状态变更监听器
    private val stateChangeListeners = mutableListOf<(String, NodeState, NodeState) -> Unit>()

    init {
        // 解析配置
        port = config.getInteger("port", 8001)
        gossipInterval = config.getLong("gossipInterval", 1000)
        cleanupInterval = config.getLong("cleanupInterval", 10000)
        suspectTimeout = config.getLong("suspectTimeout", 5000)
        deadTimeout = config.getLong("deadTimeout", 60000)
        fanout = config.getInteger("fanout", 3)

        // 解析种子节点
        val seedNodesArray = config.getJsonArray("seedNodes", JsonArray())
        seedNodes = (0 until seedNodesArray.size())
            .map { seedNodesArray.getJsonObject(it) }
            .toList()

        // 初始化本地节点信息
        val nodeId = config.getString("nodeId", UUID.randomUUID().toString())
        val host = config.getString("host", getLocalAddress())

        localNode.put("id", nodeId)
            .put("host", host)
            .put("port", port)
            .put("state", NodeState.ALIVE.name)
            .put("timestamp", System.currentTimeMillis())
            .put("generation", 1)

        // 添加自定义元数据
        val metadata = config.getJsonObject("metadata", JsonObject())
        localNode.put("metadata", metadata)

        // 将本地节点添加到节点列表
        nodes[nodeId] = localNode

        logger.info("初始化Gossip节点发现：nodeId={}, host={}, port={}", nodeId, host, port)
    }

    /**
     * 启动节点发现
     */
    fun start(): Future<Void> {
        val promise = Promise.promise<Void>()

        if (running.compareAndSet(false, true)) {
            logger.info("启动Gossip节点发现")

            // 启动网络服务器
            startNetServer()
                .compose { startNetClient() }
                .compose { joinCluster() }
                .compose { startGossipTimer() }
                .compose { startCleanupTimer() }
                .onSuccess {
                    logger.info("Gossip节点发现启动成功")
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("Gossip节点发现启动失败", err)
                    running.set(false)
                    promise.fail(err)
                }
        } else {
            logger.info("Gossip节点发现已经启动")
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 停止节点发现
     */
    fun stop(): Future<Void> {
        val promise = Promise.promise<Void>()

        if (running.compareAndSet(true, false)) {
            logger.info("停止Gossip节点发现")

            // 停止定时器
            if (gossipTimerId != -1L) {
                vertx.cancelTimer(gossipTimerId)
                gossipTimerId = -1L
            }

            if (cleanupTimerId != -1L) {
                vertx.cancelTimer(cleanupTimerId)
                cleanupTimerId = -1L
            }

            // 关闭网络客户端
            val client = netClient
            if (client != null) {
                client.close()
                    .onComplete {
                        netClient = null
                    }
            }

            // 关闭网络服务器
            val server = netServer
            if (server != null) {
                server.close()
                    .onComplete {
                        netServer = null
                        promise.complete()
                    }
            } else {
                promise.complete()
            }

            // 发送离开消息
            sendLeaveMessage()
        } else {
            logger.info("Gossip节点发现已经停止")
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 启动网络服务器
     */
    private fun startNetServer(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val options = NetServerOptions()
                .setTcpKeepAlive(true)
                .setTcpNoDelay(true)

            netServer = vertx.createNetServer(options)

            // 设置连接处理器
            netServer!!.connectHandler { socket ->
                socket.handler { buffer ->
                    handleGossipMessage(buffer, socket.remoteAddress().host())
                }

                socket.closeHandler {
                    logger.debug("Gossip连接关闭：{}", socket.remoteAddress())
                }

                socket.exceptionHandler { err ->
                    logger.warn("Gossip连接异常：{}", socket.remoteAddress(), err)
                }
            }

            // 监听端口
            netServer!!.listen(port) { ar ->
                if (ar.succeeded()) {
                    logger.info("Gossip网络服务器启动成功，监听端口：{}", port)
                    promise.complete()
                } else {
                    logger.error("Gossip网络服务器启动失败", ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("启动Gossip网络服务器失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 启动网络客户端
     */
    private fun startNetClient(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val options = NetClientOptions()
                .setTcpKeepAlive(true)
                .setTcpNoDelay(true)
                .setConnectTimeout(5000)
                .setReconnectAttempts(3)
                .setReconnectInterval(1000)

            netClient = vertx.createNetClient(options)
            promise.complete()
        } catch (e: Exception) {
            logger.error("启动Gossip网络客户端失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 加入集群
     */
    private fun joinCluster(): Future<Void> {
        val promise = Promise.promise<Void>()

        if (seedNodes.isEmpty()) {
            logger.info("没有配置种子节点，作为新集群的第一个节点")
            promise.complete()
            return promise.future()
        }

        logger.info("尝试加入集群，种子节点数量：{}", seedNodes.size)

        // 连接到种子节点
        val futures = mutableListOf<Future<Void>>()

        for (seedNode in seedNodes) {
            val host = seedNode.getString("host")
            val port = seedNode.getInteger("port", this.port)

            if (host != null) {
                futures.add(sendGossipMessage(host, port, createJoinMessage()))
            }
        }

        // 等待至少一个连接成功
        Future.any(futures)
            .onSuccess {
                logger.info("成功连接到至少一个种子节点")
                promise.complete()
            }
            .onFailure { err ->
                logger.warn("无法连接到任何种子节点，将作为新集群的第一个节点", err)
                promise.complete() // 仍然完成，因为这不是致命错误
            }

        return promise.future()
    }

    /**
     * 启动Gossip定时器
     */
    private fun startGossipTimer(): Future<Void> {
        gossipTimerId = vertx.setPeriodic(gossipInterval) { _ ->
            gossip()
        }

        return Future.succeededFuture()
    }

    /**
     * 启动清理定时器
     */
    private fun startCleanupTimer(): Future<Void> {
        cleanupTimerId = vertx.setPeriodic(cleanupInterval) { _ ->
            cleanup()
        }

        return Future.succeededFuture()
    }

    /**
     * 执行Gossip
     */
    private fun gossip() {
        if (!running.get()) {
            return
        }

        try {
            // 更新本地节点时间戳
            localNode.put("timestamp", System.currentTimeMillis())

            // 选择要发送Gossip消息的节点
            val targets = selectGossipTargets()

            if (targets.isEmpty()) {
                logger.debug("没有可用的Gossip目标节点")
                return
            }

            // 创建Gossip消息
            val message = createGossipMessage()

            // 发送Gossip消息
            for (target in targets) {
                val host = target.getString("host")
                val port = target.getInteger("port", this.port)

                if (host != null) {
                    sendGossipMessage(host, port, message)
                        .onFailure { err ->
                            logger.warn("发送Gossip消息失败：host={}, port={}", host, port, err)

                            // 标记节点为可疑
                            markNodeAsSuspect(target.getString("id"))
                        }
                }
            }
        } catch (e: Exception) {
            logger.error("执行Gossip失败", e)
        }
    }

    /**
     * 清理过期节点
     */
    private fun cleanup() {
        if (!running.get()) {
            return
        }

        try {
            val now = System.currentTimeMillis()

            // 检查可疑节点
            val suspectIterator = suspectNodes.entries.iterator()
            while (suspectIterator.hasNext()) {
                val entry = suspectIterator.next()
                val nodeId = entry.key
                val suspectTime = entry.value

                if (now - suspectTime > suspectTimeout) {
                    // 可疑超时，标记为死亡
                    markNodeAsDead(nodeId)
                    suspectIterator.remove()
                }
            }

            // 检查死亡节点
            val deadIterator = deadNodes.entries.iterator()
            while (deadIterator.hasNext()) {
                val entry = deadIterator.next()
                val nodeId = entry.key
                val deadTime = entry.value

                if (now - deadTime > deadTimeout) {
                    // 死亡超时，从列表中移除
                    nodes.remove(nodeId)
                    deadIterator.remove()

                    logger.info("节点已从集群中移除：{}", nodeId)
                }
            }
        } catch (e: Exception) {
            logger.error("清理过期节点失败", e)
        }
    }

    /**
     * 选择Gossip目标节点
     */
    private fun selectGossipTargets(): List<JsonObject> {
        val aliveNodes = nodes.values
            .filter { it.getString("id") != localNode.getString("id") }
            .filter { it.getString("state") == NodeState.ALIVE.name }
            .toList()

        if (aliveNodes.isEmpty()) {
            // 如果没有活跃节点，尝试连接种子节点
            return seedNodes
        }

        // 随机选择fanout个节点
        val targets = mutableListOf<JsonObject>()
        val random = ThreadLocalRandom.current()

        for (i in 0 until minOf(fanout, aliveNodes.size)) {
            val index = random.nextInt(aliveNodes.size)
            targets.add(aliveNodes[index])
        }

        return targets
    }

    /**
     * 创建加入消息
     */
    private fun createJoinMessage(): Buffer {
        val message = JsonObject()
            .put("type", "JOIN")
            .put("node", localNode)

        return Buffer.buffer(message.encode())
    }

    /**
     * 创建离开消息
     */
    private fun createLeaveMessage(): Buffer {
        val message = JsonObject()
            .put("type", "LEAVE")
            .put("nodeId", localNode.getString("id"))

        return Buffer.buffer(message.encode())
    }

    /**
     * 创建Gossip消息
     */
    private fun createGossipMessage(): Buffer {
        val nodesArray = JsonArray()

        // 添加所有节点信息
        for (node in nodes.values) {
            nodesArray.add(node)
        }

        val message = JsonObject()
            .put("type", "GOSSIP")
            .put("nodes", nodesArray)

        return Buffer.buffer(message.encode())
    }

    /**
     * 发送Gossip消息
     */
    private fun sendGossipMessage(host: String, port: Int, message: Buffer): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!running.get() || netClient == null) {
            promise.fail("Gossip节点发现未启动")
            return promise.future()
        }

        netClient!!.connect(port, host) { ar ->
            if (ar.succeeded()) {
                val socket = ar.result()

                socket.write(message)
                    .onSuccess {
                        // 更新统计信息
                        gossipMessagesSent.incrementAndGet()

                        socket.close()
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.warn("发送Gossip消息失败：host={}, port={}", host, port, err)
                        socket.close()
                        promise.fail(err)
                    }
            } else {
                logger.warn("连接到节点失败：host={}, port={}", host, port, ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 发送离开消息
     */
    private fun sendLeaveMessage() {
        if (!running.get() || netClient == null) {
            return
        }

        val message = createLeaveMessage()

        // 选择要发送离开消息的节点
        val targets = selectGossipTargets()

        for (target in targets) {
            val host = target.getString("host")
            val port = target.getInteger("port", this.port)

            if (host != null) {
                sendGossipMessage(host, port, message)
                    .onFailure { err ->
                        logger.warn("发送离开消息失败：host={}, port={}", host, port, err)
                    }
            }
        }
    }

    /**
     * 处理Gossip消息
     */
    private fun handleGossipMessage(buffer: Buffer, sourceHost: String) {
        if (!running.get()) {
            return
        }

        try {
            // 更新统计信息
            gossipMessagesReceived.incrementAndGet()

            val message = JsonObject(buffer)
            val type = message.getString("type")

            when (type) {
                "JOIN" -> handleJoinMessage(message)
                "LEAVE" -> handleLeaveMessage(message)
                "GOSSIP" -> handleGossipUpdate(message)
                else -> logger.warn("收到未知类型的Gossip消息：{}", type)
            }
        } catch (e: Exception) {
            logger.error("处理Gossip消息失败", e)
        }
    }

    /**
     * 处理加入消息
     */
    private fun handleJoinMessage(message: JsonObject) {
        val node = message.getJsonObject("node")

        if (node != null) {
            val nodeId = node.getString("id")

            if (nodeId != null && nodeId != localNode.getString("id")) {
                // 更新节点信息
                updateNodeInfo(node)

                logger.info("节点加入集群：{}", nodeId)

                // 回复Gossip消息
                val host = node.getString("host")
                val port = node.getInteger("port", this.port)

                if (host != null) {
                    sendGossipMessage(host, port, createGossipMessage())
                        .onFailure { err ->
                            logger.warn("回复加入消息失败：host={}, port={}", host, port, err)
                        }
                }
            }
        }
    }

    /**
     * 处理离开消息
     */
    private fun handleLeaveMessage(message: JsonObject) {
        val nodeId = message.getString("nodeId")

        if (nodeId != null && nodeId != localNode.getString("id")) {
            // 标记节点为死亡
            markNodeAsDead(nodeId)

            logger.info("节点离开集群：{}", nodeId)
        }
    }

    /**
     * 处理Gossip更新
     */
    private fun handleGossipUpdate(message: JsonObject) {
        val nodesArray = message.getJsonArray("nodes")

        if (nodesArray != null) {
            for (i in 0 until nodesArray.size()) {
                val node = nodesArray.getJsonObject(i)
                val nodeId = node.getString("id")

                if (nodeId != null && nodeId != localNode.getString("id")) {
                    // 更新节点信息
                    updateNodeInfo(node)
                }
            }
        }
    }

    /**
     * 更新节点信息
     */
    private fun updateNodeInfo(node: JsonObject) {
        val nodeId = node.getString("id") ?: return
        val state = node.getString("state") ?: NodeState.ALIVE.name
        val timestamp = node.getLong("timestamp") ?: System.currentTimeMillis()
        val generation = node.getInteger("generation") ?: 1

        // 获取当前节点信息
        val currentNode = nodes[nodeId]

        if (currentNode == null) {
            // 新节点
            nodes[nodeId] = node.copy()

            // 如果节点状态为ALIVE，增加发现计数
            if (state == NodeState.ALIVE.name) {
                nodesDiscovered.incrementAndGet()
            }

            // 触发状态变更事件
            notifyStateChange(nodeId, null, NodeState.valueOf(state))

            logger.info("发现新节点：id={}, state={}", nodeId, state)
        } else {
            // 现有节点，检查是否需要更新
            val currentState = currentNode.getString("state") ?: NodeState.ALIVE.name
            val currentTimestamp = currentNode.getLong("timestamp") ?: 0
            val currentGeneration = currentNode.getInteger("generation") ?: 0

            // 检查是否需要更新
            if (generation > currentGeneration ||
                (generation == currentGeneration && timestamp > currentTimestamp)) {

                // 更新节点信息
                nodes[nodeId] = node.copy()

                // 如果状态发生变化，触发事件
                if (state != currentState) {
                    val oldState = NodeState.valueOf(currentState)
                    val newState = NodeState.valueOf(state)

                    notifyStateChange(nodeId, oldState, newState)

                    // 更新统计信息
                    if (oldState != NodeState.ALIVE && newState == NodeState.ALIVE) {
                        nodesRecovered.incrementAndGet()
                    } else if (oldState == NodeState.ALIVE && newState != NodeState.ALIVE) {
                        nodesFailed.incrementAndGet()
                    }

                    logger.info("节点状态变更：id={}, oldState={}, newState={}", nodeId, oldState, newState)
                }

                // 如果节点恢复为ALIVE状态，从可疑和死亡列表中移除
                if (state == NodeState.ALIVE.name) {
                    suspectNodes.remove(nodeId)
                    deadNodes.remove(nodeId)
                }
            }
        }
    }

    /**
     * 标记节点为可疑
     */
    private fun markNodeAsSuspect(nodeId: String) {
        val node = nodes[nodeId] ?: return

        // 如果节点已经是可疑或死亡状态，不做处理
        val state = node.getString("state") ?: NodeState.ALIVE.name
        if (state != NodeState.ALIVE.name) {
            return
        }

        // 更新节点状态
        val oldState = NodeState.valueOf(state)
        val newState = NodeState.SUSPECT

        node.put("state", newState.name)
        node.put("timestamp", System.currentTimeMillis())

        // 添加到可疑节点列表
        suspectNodes[nodeId] = System.currentTimeMillis()

        // 触发状态变更事件
        notifyStateChange(nodeId, oldState, newState)

        logger.info("节点标记为可疑：{}", nodeId)
    }

    /**
     * 标记节点为死亡
     */
    private fun markNodeAsDead(nodeId: String) {
        val node = nodes[nodeId] ?: return

        // 如果节点已经是死亡状态，不做处理
        val state = node.getString("state") ?: NodeState.ALIVE.name
        if (state == NodeState.DEAD.name) {
            return
        }

        // 更新节点状态
        val oldState = NodeState.valueOf(state)
        val newState = NodeState.DEAD

        node.put("state", newState.name)
        node.put("timestamp", System.currentTimeMillis())

        // 添加到死亡节点列表
        deadNodes[nodeId] = System.currentTimeMillis()

        // 从可疑节点列表中移除
        suspectNodes.remove(nodeId)

        // 触发状态变更事件
        notifyStateChange(nodeId, oldState, newState)

        // 更新统计信息
        if (oldState == NodeState.ALIVE) {
            nodesFailed.incrementAndGet()
        }

        logger.info("节点标记为死亡：{}", nodeId)
    }

    /**
     * 通知状态变更
     */
    private fun notifyStateChange(nodeId: String, oldState: NodeState?, newState: NodeState) {
        for (listener in stateChangeListeners) {
            try {
                listener(nodeId, oldState ?: newState, newState)
            } catch (e: Exception) {
                logger.error("调用状态变更监听器失败", e)
            }
        }
    }

    /**
     * 添加状态变更监听器
     */
    fun addStateChangeListener(listener: (String, NodeState, NodeState) -> Unit) {
        stateChangeListeners.add(listener)
    }

    /**
     * 移除状态变更监听器
     */
    fun removeStateChangeListener(listener: (String, NodeState, NodeState) -> Unit) {
        stateChangeListeners.remove(listener)
    }

    /**
     * 获取所有节点
     */
    fun getAllNodes(): List<JsonObject> {
        return nodes.values.map { it.copy() }
    }

    /**
     * 获取活跃节点
     */
    fun getAliveNodes(): List<JsonObject> {
        return nodes.values
            .filter { it.getString("state") == NodeState.ALIVE.name }
            .map { it.copy() }
    }

    /**
     * 获取可疑节点
     */
    fun getSuspectNodes(): List<JsonObject> {
        return nodes.values
            .filter { it.getString("state") == NodeState.SUSPECT.name }
            .map { it.copy() }
    }

    /**
     * 获取死亡节点
     */
    fun getDeadNodes(): List<JsonObject> {
        return nodes.values
            .filter { it.getString("state") == NodeState.DEAD.name }
            .map { it.copy() }
    }

    /**
     * 获取本地节点ID
     */
    fun getLocalNodeId(): String {
        return localNode.getString("id")
    }

    /**
     * 获取本地节点信息
     */
    fun getLocalNode(): JsonObject {
        return localNode.copy()
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("gossipMessagesSent", gossipMessagesSent.get())
            .put("gossipMessagesReceived", gossipMessagesReceived.get())
            .put("nodesDiscovered", nodesDiscovered.get())
            .put("nodesFailed", nodesFailed.get())
            .put("nodesRecovered", nodesRecovered.get())
            .put("aliveNodesCount", getAliveNodes().size)
            .put("suspectNodesCount", getSuspectNodes().size)
            .put("deadNodesCount", getDeadNodes().size)
            .put("totalNodesCount", nodes.size)
    }

    /**
     * 获取本地地址
     */
    private fun getLocalAddress(): String {
        // 尝试获取非回环地址
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()

                // 跳过回环和禁用的接口
                if (networkInterface.isLoopback || !networkInterface.isUp) {
                    continue
                }

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()

                    // 使用IPv4地址
                    if (!address.isLoopbackAddress && address.hostAddress.indexOf(':') == -1) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("获取本地地址失败", e)
        }

        // 默认使用localhost
        return "localhost"
    }
}
