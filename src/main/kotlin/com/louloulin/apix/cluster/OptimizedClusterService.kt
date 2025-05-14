package com.louloulin.apix.cluster

import com.louloulin.apix.cluster.communication.OptimizedClusterCommunication
import com.louloulin.apix.cluster.discovery.GossipNodeDiscovery
import com.louloulin.apix.cluster.sync.IncrementalSyncManager
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 优化的集群服务实现
 *
 * 集成了优化的集群通信、Gossip节点发现和增量同步机制
 */
class OptimizedClusterService(
    private val vertx: Vertx,
    private val clusterConfig: ClusterConfig
) {
    private val logger = LoggerFactory.getLogger(OptimizedClusterService::class.java)

    // 优化的集群通信
    private val communication = OptimizedClusterCommunication(vertx)

    // Gossip节点发现
    private val nodeDiscovery: GossipNodeDiscovery

    // 增量同步管理器
    private val syncManager: IncrementalSyncManager

    // 本地节点ID
    private val nodeId: String

    // 运行状态
    private val running = AtomicBoolean(false)

    // 节点状态监听器
    private val nodeStateListeners = ConcurrentHashMap<String, Handler<JsonObject>>()

    init {
        // 初始化节点ID
        nodeId = "node-${vertx.hashCode()}"

        // 初始化Gossip节点发现
        val gossipConfig = clusterConfig.clusterConfig.getJsonObject("gossip", JsonObject())
            .put("nodeId", nodeId)

        nodeDiscovery = GossipNodeDiscovery(vertx, gossipConfig)

        // 初始化增量同步管理器
        val syncConfig = clusterConfig.syncConfig
        syncManager = IncrementalSyncManager(vertx, syncConfig)

        logger.info("初始化优化的集群服务：nodeId={}", nodeId)
    }

    /**
     * 初始化集群服务
     */
    fun initialize(): Future<Void> {
        if (!clusterConfig.enabled || !vertx.isClustered()) {
            logger.info("集群未启用或Vert.x未以集群模式运行，跳过初始化")
            return Future.succeededFuture()
        }

        val promise = Promise.promise<Void>()

        if (running.compareAndSet(false, true)) {
            logger.info("初始化优化的集群服务")

            // 配置优化的集群通信
            communication.configureCompression(true, 6, 1024)
            communication.configureBatching(true, 50, 100)

            // 启动组件
            communication.start()
                .compose { nodeDiscovery.start() }
                .compose { syncManager.start() }
                .compose { registerEventHandlers() }
                .onSuccess {
                    logger.info("优化的集群服务初始化成功")
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("优化的集群服务初始化失败", err)
                    running.set(false)
                    promise.fail(err)
                }
        } else {
            logger.info("优化的集群服务已经初始化")
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 注册事件处理器
     */
    private fun registerEventHandlers(): Future<Void> {
        // 注册节点状态变更监听器
        nodeDiscovery.addStateChangeListener { nodeId, oldState, newState ->
            val stateChange = JsonObject()
                .put("nodeId", nodeId)
                .put("oldState", oldState.name)
                .put("newState", newState.name)
                .put("timestamp", System.currentTimeMillis())

            // 发布节点状态变更事件
            vertx.eventBus().publish(EventBusAddresses.CLUSTER_NODE_STATE_CHANGE, stateChange)

            // 通知监听器
            for (listener in nodeStateListeners.values) {
                try {
                    listener.handle(stateChange)
                } catch (e: Exception) {
                    logger.error("调用节点状态监听器失败", e)
                }
            }
        }

        // 注册EventBus处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_NODES, this::handleGetNodes)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_NODE_INFO, this::handleGetNodeInfo)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_CONFIG_SYNC, this::handleConfigSync)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_ROUTES_SYNC, this::handleRoutesSync)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_SERVICES_SYNC, this::handleServicesSync)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_PLUGINS_SYNC, this::handlePluginsSync)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_STATS, this::handleGetStats)

        return Future.succeededFuture()
    }

    /**
     * 处理获取节点列表请求
     */
    private fun handleGetNodes(message: Message<JsonObject>) {
        if (!running.get()) {
            message.reply(JsonObject()
                .put("success", false)
                .put("error", "集群服务未启动"))
            return
        }

        val nodes = nodeDiscovery.getAliveNodes()
        val nodesArray = JsonArray()

        for (node in nodes) {
            nodesArray.add(node)
        }

        message.reply(JsonObject()
            .put("success", true)
            .put("nodes", nodesArray))
    }

    /**
     * 处理获取节点信息请求
     */
    private fun handleGetNodeInfo(message: Message<JsonObject>) {
        if (!running.get()) {
            message.reply(JsonObject()
                .put("success", false)
                .put("error", "集群服务未启动"))
            return
        }

        val request = message.body()
        val targetNodeId = request.getString("nodeId")

        if (targetNodeId == null) {
            // 返回本地节点信息
            val localNode = nodeDiscovery.getLocalNode()

            message.reply(JsonObject()
                .put("success", true)
                .put("node", localNode))
        } else {
            // 查找指定节点
            val nodes = nodeDiscovery.getAllNodes()
            val node = nodes.find { it.getString("id") == targetNodeId }

            if (node != null) {
                message.reply(JsonObject()
                    .put("success", true)
                    .put("node", node))
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "节点不存在：$targetNodeId"))
            }
        }
    }

    /**
     * 处理配置同步请求
     */
    private fun handleConfigSync(message: Message<JsonObject>) {
        handleDataSync(message, "config")
    }

    /**
     * 处理路由同步请求
     */
    private fun handleRoutesSync(message: Message<JsonObject>) {
        handleDataSync(message, "routes")
    }

    /**
     * 处理服务同步请求
     */
    private fun handleServicesSync(message: Message<JsonObject>) {
        handleDataSync(message, "services")
    }

    /**
     * 处理插件同步请求
     */
    private fun handlePluginsSync(message: Message<JsonObject>) {
        handleDataSync(message, "plugins")
    }

    /**
     * 处理数据同步请求
     */
    private fun handleDataSync(message: Message<JsonObject>, dataType: String) {
        if (!running.get()) {
            message.reply(JsonObject()
                .put("success", false)
                .put("error", "集群服务未启动"))
            return
        }

        val request = message.body()
        val action = request.getString("action")

        when (action) {
            "get" -> {
                val key = request.getString("key")

                if (key != null) {
                    // 获取单个数据
                    syncManager.get(dataType, key)
                        .onSuccess { value ->
                            message.reply(JsonObject()
                                .put("success", true)
                                .put("result", value))
                        }
                        .onFailure { err ->
                            message.reply(JsonObject()
                                .put("success", false)
                                .put("error", err.message))
                        }
                } else {
                    // 获取所有数据
                    syncManager.getAll(dataType)
                        .onSuccess { values ->
                            val result = JsonObject()
                            for ((k, v) in values) {
                                result.put(k, v)
                            }

                            message.reply(JsonObject()
                                .put("success", true)
                                .put("result", result))
                        }
                        .onFailure { err ->
                            message.reply(JsonObject()
                                .put("success", false)
                                .put("error", err.message))
                        }
                }
            }

            "put" -> {
                val key = request.getString("key")
                val value = request.getJsonObject("value")

                if (key != null && value != null) {
                    syncManager.put(dataType, key, value)
                        .onSuccess { version ->
                            message.reply(JsonObject()
                                .put("success", true)
                                .put("version", version))
                        }
                        .onFailure { err ->
                            message.reply(JsonObject()
                                .put("success", false)
                                .put("error", err.message))
                        }
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "缺少必要参数：key或value"))
                }
            }

            "remove" -> {
                val key = request.getString("key")

                if (key != null) {
                    syncManager.remove(dataType, key)
                        .onSuccess { version ->
                            message.reply(JsonObject()
                                .put("success", true)
                                .put("version", version))
                        }
                        .onFailure { err ->
                            message.reply(JsonObject()
                                .put("success", false)
                                .put("error", err.message))
                        }
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "缺少必要参数：key"))
                }
            }

            "clear" -> {
                syncManager.clear(dataType)
                    .onSuccess { version ->
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("version", version))
                    }
                    .onFailure { err ->
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", err.message))
                    }
            }

            "getVersion" -> {
                val version = syncManager.getCurrentVersion(dataType)
                message.reply(JsonObject()
                    .put("success", true)
                    .put("version", version))
            }

            "getChangeLog" -> {
                val fromVersion = request.getLong("fromVersion", 0L)
                val changeLog = syncManager.getChangeLog(dataType, fromVersion)

                val logArray = JsonArray()
                for (entry in changeLog) {
                    logArray.add(JsonObject()
                        .put("version", entry.version)
                        .put("timestamp", entry.timestamp)
                        .put("operation", entry.operation.name)
                        .put("key", entry.key)
                        .put("value", entry.value)
                        .put("metadata", entry.metadata))
                }

                message.reply(JsonObject()
                    .put("success", true)
                    .put("changeLog", logArray))
            }

            else -> {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "未知的操作：$action"))
            }
        }
    }

    /**
     * 处理获取统计信息请求
     */
    private fun handleGetStats(message: Message<JsonObject>) {
        if (!running.get()) {
            message.reply(JsonObject()
                .put("success", false)
                .put("error", "集群服务未启动"))
            return
        }

        val communicationStats = communication.getStats()
        val discoveryStats = nodeDiscovery.getStats()
        val syncStats = syncManager.getStats()

        val stats = JsonObject()
            .put("nodeId", nodeId)
            .put("communication", communicationStats)
            .put("discovery", discoveryStats)
            .put("sync", syncStats)

        message.reply(JsonObject()
            .put("success", true)
            .put("stats", stats))
    }

    /**
     * 获取配置
     */
    fun getConfig(key: String): Future<JsonObject?> {
        return syncManager.get("config", key)
    }

    /**
     * 获取所有配置
     */
    fun getAllConfig(): Future<Map<String, JsonObject>> {
        return syncManager.getAll("config")
    }

    /**
     * 保存配置
     */
    fun putConfig(key: String, value: JsonObject): Future<Long> {
        return syncManager.put("config", key, value)
    }

    /**
     * 删除配置
     */
    fun removeConfig(key: String): Future<Long> {
        return syncManager.remove("config", key)
    }

    /**
     * 获取路由
     */
    fun getRoute(id: String): Future<JsonObject?> {
        return syncManager.get("routes", id)
    }

    /**
     * 获取所有路由
     */
    fun getAllRoutes(): Future<Map<String, JsonObject>> {
        return syncManager.getAll("routes")
    }

    /**
     * 保存路由
     */
    fun putRoute(id: String, route: JsonObject): Future<Long> {
        return syncManager.put("routes", id, route)
    }

    /**
     * 删除路由
     */
    fun removeRoute(id: String): Future<Long> {
        return syncManager.remove("routes", id)
    }

    /**
     * 获取服务
     */
    fun getService(id: String): Future<JsonObject?> {
        return syncManager.get("services", id)
    }

    /**
     * 获取所有服务
     */
    fun getAllServices(): Future<Map<String, JsonObject>> {
        return syncManager.getAll("services")
    }

    /**
     * 保存服务
     */
    fun putService(id: String, service: JsonObject): Future<Long> {
        return syncManager.put("services", id, service)
    }

    /**
     * 删除服务
     */
    fun removeService(id: String): Future<Long> {
        return syncManager.remove("services", id)
    }

    /**
     * 获取插件
     */
    fun getPlugin(id: String): Future<JsonObject?> {
        return syncManager.get("plugins", id)
    }

    /**
     * 获取所有插件
     */
    fun getAllPlugins(): Future<Map<String, JsonObject>> {
        return syncManager.getAll("plugins")
    }

    /**
     * 保存插件
     */
    fun putPlugin(id: String, plugin: JsonObject): Future<Long> {
        return syncManager.put("plugins", id, plugin)
    }

    /**
     * 删除插件
     */
    fun removePlugin(id: String): Future<Long> {
        return syncManager.remove("plugins", id)
    }

    /**
     * 添加节点状态监听器
     */
    fun addNodeStateListener(id: String, handler: Handler<JsonObject>) {
        nodeStateListeners[id] = handler
    }

    /**
     * 移除节点状态监听器
     */
    fun removeNodeStateListener(id: String) {
        nodeStateListeners.remove(id)
    }

    /**
     * 获取节点列表
     */
    fun getNodes(): List<JsonObject> {
        return nodeDiscovery.getAliveNodes()
    }

    /**
     * 获取本地节点ID
     */
    fun getLocalNodeId(): String {
        return nodeId
    }

    /**
     * 获取本地节点信息
     */
    fun getLocalNodeInfo(): JsonObject {
        return nodeDiscovery.getLocalNode()
    }

    /**
     * 停止集群服务
     */
    fun stop(): Future<Void> {
        val promise = Promise.promise<Void>()

        if (running.compareAndSet(true, false)) {
            logger.info("停止优化的集群服务")

            // 停止组件
            syncManager.stop()
                .compose { nodeDiscovery.stop() }
                .compose { communication.stop() }
                .onSuccess {
                    logger.info("优化的集群服务已停止")
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("停止优化的集群服务失败", err)
                    promise.fail(err)
                }
        } else {
            logger.info("优化的集群服务已经停止")
            promise.complete()
        }

        return promise.future()
    }
}
