package com.louloulin.apix.edge

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.json.JsonArray
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.ConcurrentHashMap
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.mode.NodeMode
import com.louloulin.apix.core.mode.NodeModeManager

/**
 * 边缘控制平面管理器，负责边缘节点的轻量级控制平面功能。
 * 实现plan7.md中的2.2.1节"边缘控制平面架构"功能。
 */
class EdgeControlPlaneManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(EdgeControlPlaneManager::class.java)

    // 边缘控制平面配置
    private val controlPlaneConfig = AtomicReference<JsonObject>(JsonObject())

    // 边缘控制平面是否启用
    private val controlPlaneEnabled = AtomicBoolean(false)

    // 节点模式管理器
    private lateinit var nodeModeManager: NodeModeManager

    // 管理的边缘节点
    private val edgeNodes = ConcurrentHashMap<String, JsonObject>()

    // 配置版本
    private val configVersion = AtomicReference<String>("0")

    // 配置历史
    private val configHistory = ConcurrentHashMap<String, JsonObject>()

    // 最大配置历史数量
    private val maxConfigHistorySize = 10

    /**
     * 初始化边缘控制平面管理器。
     *
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化边缘控制平面管理器")

        // 获取边缘控制平面配置
        val edgeConfig = config.getJsonObject("node", JsonObject()).getJsonObject("edge", JsonObject())
        val controlPlaneConfig = edgeConfig.getJsonObject("controlPlane", JsonObject())

        // 检查边缘控制平面是否启用
        controlPlaneEnabled.set(controlPlaneConfig.getBoolean("enabled", false))

        if (!controlPlaneEnabled.get()) {
            logger.info("边缘控制平面功能未启用")
            return Future.succeededFuture()
        }

        // 保存配置
        this.controlPlaneConfig.set(controlPlaneConfig)

        // 获取节点模式管理器
        nodeModeManager = NodeModeManager.getInstance(vertx)

        // 如果不是控制平面节点，则不需要启用边缘控制平面
        if (!nodeModeManager.isControlPlane()) {
            logger.info("当前节点不是控制平面节点，不启用边缘控制平面")
            controlPlaneEnabled.set(false)
            return Future.succeededFuture()
        }

        // 加载初始配置
        loadInitialConfig(config)

        // 注册事件总线处理器
        registerEventBusHandlers()

        // 启动边缘节点发现
        startEdgeNodeDiscovery()

        logger.info("边缘控制平面管理器初始化完成")
        return Future.succeededFuture()
    }

    /**
     * 加载初始配置。
     *
     * @param config 配置信息
     */
    private fun loadInitialConfig(config: JsonObject) {
        // 保存初始配置
        val initialVersion = System.currentTimeMillis().toString()
        configVersion.set(initialVersion)
        configHistory[initialVersion] = config.copy()

        // 清理过期配置历史
        cleanupConfigHistory()
    }

    /**
     * 清理过期配置历史。
     */
    private fun cleanupConfigHistory() {
        if (configHistory.size > maxConfigHistorySize) {
            // 按版本排序
            val sortedVersions = configHistory.keys.sortedBy { it.toLong() }

            // 删除最旧的配置
            val versionsToRemove = sortedVersions.size - maxConfigHistorySize
            for (i in 0 until versionsToRemove) {
                configHistory.remove(sortedVersions[i])
            }
        }
    }

    /**
     * 注册事件总线处理器。
     */
    private fun registerEventBusHandlers() {
        // 处理获取边缘控制平面状态请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_STATUS_GET) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", getStatus())
            )
        }

        // 处理获取边缘节点列表请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODES_GET) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonArray(edgeNodes.values.toList()))
            )
        }

        // 处理获取配置版本请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_VERSION_GET) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("version", configVersion.get())
                    .put("timestamp", System.currentTimeMillis())
                )
            )
        }

        // 处理获取配置请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_GET) { message ->
            val version = message.body().getString("version")

            if (version != null) {
                // 获取指定版本的配置
                val config = configHistory[version]
                if (config != null) {
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", config)
                    )
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "配置版本不存在: $version")
                    )
                }
            } else {
                // 获取最新版本的配置
                val latestVersion = configVersion.get()
                val config = configHistory[latestVersion]

                if (config != null) {
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", JsonObject()
                            .put("version", latestVersion)
                            .put("config", config)
                        )
                    )
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "无法获取最新配置")
                    )
                }
            }
        }

        // 处理更新配置请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_UPDATE) { message ->
            val config = message.body().getJsonObject("config")

            if (config == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing config parameter")
                )
                return@consumer
            }

            try {
                // 更新配置
                val newVersion = updateConfig(config)

                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("version", newVersion)
                        .put("timestamp", System.currentTimeMillis())
                    )
                )
            } catch (e: Exception) {
                logger.error("更新配置失败", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 处理回滚配置请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_ROLLBACK) { message ->
            val version = message.body().getString("version")

            if (version == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing version parameter")
                )
                return@consumer
            }

            try {
                // 回滚配置
                val success = rollbackConfig(version)

                if (success) {
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", JsonObject()
                            .put("version", version)
                            .put("timestamp", System.currentTimeMillis())
                        )
                    )
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "配置版本不存在: $version")
                    )
                }
            } catch (e: Exception) {
                logger.error("回滚配置失败", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 处理边缘节点注册请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_REGISTER) { message ->
            val nodeInfo = message.body()
            val nodeId = nodeInfo.getString("nodeId")

            if (nodeId == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing nodeId parameter")
                )
                return@consumer
            }

            try {
                // 注册边缘节点
                registerEdgeNode(nodeId, nodeInfo)

                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("nodeId", nodeId)
                        .put("configVersion", configVersion.get())
                        .put("timestamp", System.currentTimeMillis())
                    )
                )
            } catch (e: Exception) {
                logger.error("注册边缘节点失败", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }

        // 处理边缘节点心跳请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_HEARTBEAT) { message ->
            val nodeId = message.body().getString("nodeId")

            if (nodeId == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing nodeId parameter")
                )
                return@consumer
            }

            try {
                // 更新边缘节点心跳
                updateEdgeNodeHeartbeat(nodeId)

                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("nodeId", nodeId)
                        .put("configVersion", configVersion.get())
                        .put("timestamp", System.currentTimeMillis())
                    )
                )
            } catch (e: Exception) {
                logger.error("更新边缘节点心跳失败", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", e.message)
                )
            }
        }
    }

    /**
     * 启动边缘节点发现。
     */
    private fun startEdgeNodeDiscovery() {
        // 定期检查边缘节点状态
        vertx.setPeriodic(30000) { _ ->
            checkEdgeNodesStatus()
        }
    }

    /**
     * 检查边缘节点状态。
     */
    private fun checkEdgeNodesStatus() {
        val now = System.currentTimeMillis()
        val timeout = 60000L // 60秒超时

        // 检查每个边缘节点的最后心跳时间
        val nodesToRemove = mutableListOf<String>()

        for ((nodeId, nodeInfo) in edgeNodes) {
            val lastHeartbeat = nodeInfo.getLong("lastHeartbeat", 0)

            if (now - lastHeartbeat > timeout) {
                // 节点可能已经离线
                logger.warn("边缘节点可能已离线: {}", nodeId)
                nodesToRemove.add(nodeId)
            }
        }

        // 移除离线节点
        for (nodeId in nodesToRemove) {
            edgeNodes.remove(nodeId)
            logger.info("移除离线边缘节点: {}", nodeId)

            // 发布节点离线事件
            publishNodeOfflineEvent(nodeId)
        }
    }

    /**
     * 发布节点离线事件。
     *
     * @param nodeId 节点ID
     */
    private fun publishNodeOfflineEvent(nodeId: String) {
        vertx.eventBus().publish(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_OFFLINE, JsonObject()
            .put("nodeId", nodeId)
            .put("timestamp", System.currentTimeMillis())
        )
    }

    /**
     * 注册边缘节点。
     *
     * @param nodeId 节点ID
     * @param nodeInfo 节点信息
     */
    fun registerEdgeNode(nodeId: String, nodeInfo: JsonObject) {
        // 添加或更新节点信息
        val updatedInfo = nodeInfo.copy()
            .put("lastHeartbeat", System.currentTimeMillis())
            .put("registered", true)

        val isNewNode = !edgeNodes.containsKey(nodeId)
        edgeNodes[nodeId] = updatedInfo

        if (isNewNode) {
            logger.info("注册新的边缘节点: {}", nodeId)

            // 发布节点注册事件
            publishNodeRegisteredEvent(nodeId, updatedInfo)
        } else {
            logger.info("更新边缘节点信息: {}", nodeId)
        }
    }

    /**
     * 发布节点注册事件。
     *
     * @param nodeId 节点ID
     * @param nodeInfo 节点信息
     */
    private fun publishNodeRegisteredEvent(nodeId: String, nodeInfo: JsonObject) {
        vertx.eventBus().publish(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_REGISTERED, JsonObject()
            .put("nodeId", nodeId)
            .put("nodeInfo", nodeInfo)
            .put("timestamp", System.currentTimeMillis())
        )
    }

    /**
     * 更新边缘节点心跳。
     *
     * @param nodeId 节点ID
     */
    fun updateEdgeNodeHeartbeat(nodeId: String) {
        // 获取节点信息
        val nodeInfo = edgeNodes[nodeId]

        if (nodeInfo != null) {
            // 更新最后心跳时间
            nodeInfo.put("lastHeartbeat", System.currentTimeMillis())
        } else {
            // 节点未注册，忽略心跳
            logger.warn("收到未注册边缘节点的心跳: {}", nodeId)
        }
    }

    /**
     * 更新配置。
     *
     * @param config 新配置
     * @return 新的配置版本
     */
    fun updateConfig(config: JsonObject): String {
        // 创建新的配置版本
        val newVersion = System.currentTimeMillis().toString()

        // 保存新配置
        configHistory[newVersion] = config.copy()
        configVersion.set(newVersion)

        // 清理过期配置历史
        cleanupConfigHistory()

        // 发布配置更新事件
        publishConfigUpdatedEvent(newVersion, config)

        logger.info("更新配置，新版本: {}", newVersion)

        return newVersion
    }

    /**
     * 发布配置更新事件。
     *
     * @param version 配置版本
     * @param config 配置内容
     */
    private fun publishConfigUpdatedEvent(version: String, config: JsonObject) {
        vertx.eventBus().publish(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_UPDATED, JsonObject()
            .put("version", version)
            .put("config", config)
            .put("timestamp", System.currentTimeMillis())
        )
    }

    /**
     * 回滚配置。
     *
     * @param version 要回滚到的配置版本
     * @return 是否成功回滚
     */
    fun rollbackConfig(version: String): Boolean {
        // 检查版本是否存在
        val config = configHistory[version]
        if (config == null) {
            return false
        }

        // 设置当前版本
        configVersion.set(version)

        // 发布配置更新事件
        publishConfigUpdatedEvent(version, config)

        logger.info("回滚配置到版本: {}", version)

        return true
    }

    /**
     * 获取当前配置版本。
     *
     * @return 当前配置版本
     */
    fun getConfigVersion(): String {
        return configVersion.get()
    }

    /**
     * 获取边缘控制平面状态。
     *
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        return JsonObject()
            .put("enabled", controlPlaneEnabled.get())
            .put("config", controlPlaneConfig.get())
            .put("edgeNodesCount", edgeNodes.size)
            .put("configVersion", configVersion.get())
            .put("configHistorySize", configHistory.size)
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 关闭边缘控制平面管理器，停止所有定时任务和资源。
     *
     * @return Future<Void> 关闭结果
     */
    fun shutdown(): Future<Void> {
        logger.info("关闭边缘控制平面管理器")

        val promise = Promise.promise<Void>()

        try {
            // 禁用控制平面
            controlPlaneEnabled.set(false)

            // 清空数据
            edgeNodes.clear()

            promise.complete()
        } catch (e: Exception) {
            logger.error("关闭边缘控制平面管理器失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    companion object {
        // 单例实例
        @Volatile
        private var instance: EdgeControlPlaneManager? = null

        /**
         * 获取 EdgeControlPlaneManager 的单例实例。
         *
         * @param vertx Vert.x 实例
         * @return EdgeControlPlaneManager 实例
         */
        fun getInstance(vertx: Vertx): EdgeControlPlaneManager {
            return instance ?: synchronized(this) {
                instance ?: EdgeControlPlaneManager(vertx).also { instance = it }
            }
        }
    }
}
