package com.louloulin.apix.scaling

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 优雅缩容管理器，负责管理系统的优雅缩容过程。
 */
class GracefulScaleDownManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(GracefulScaleDownManager::class.java)
    
    // 优雅缩容是否启用
    private val gracefulScaleDownEnabled = AtomicBoolean(false)
    
    // 正在进行的缩容操作
    private val activeScaleDownOperations = ConcurrentHashMap<String, ScaleDownOperation>()
    
    // 缩容超时时间（毫秒）
    private val scaleDownTimeout = AtomicLong(300000)
    
    // 连接耗尽等待时间（毫秒）
    private val drainTimeout = AtomicLong(60000)
    
    // 缩容检查间隔（毫秒）
    private val checkInterval = AtomicLong(5000)
    
    // 缩容检查定时器 ID
    private var checkTimerId = -1L
    
    /**
     * 初始化优雅缩容管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化优雅缩容管理器")
        
        // 获取配置
        val scaleDownConfig = config.getJsonObject("gracefulScaleDown", JsonObject())
        
        // 更新配置参数
        gracefulScaleDownEnabled.set(scaleDownConfig.getBoolean("enabled", true))
        scaleDownTimeout.set(scaleDownConfig.getLong("scaleDownTimeout", 300000))
        drainTimeout.set(scaleDownConfig.getLong("drainTimeout", 60000))
        checkInterval.set(scaleDownConfig.getLong("checkInterval", 5000))
        
        // 如果优雅缩容未启用，直接返回
        if (!gracefulScaleDownEnabled.get()) {
            logger.info("优雅缩容未启用")
            return Future.succeededFuture()
        }
        
        // 启动缩容检查
        startScaleDownCheck()
        
        logger.info("优雅缩容管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 启动缩容检查。
     */
    private fun startScaleDownCheck() {
        // 停止之前的定时器
        if (checkTimerId != -1L) {
            vertx.cancelTimer(checkTimerId)
        }
        
        // 启动新的定时器
        checkTimerId = vertx.setPeriodic(checkInterval.get()) { _ ->
            checkScaleDownOperations()
        }
        
        logger.info("启动缩容检查，间隔: ${checkInterval.get()} 毫秒")
    }
    
    /**
     * 检查正在进行的缩容操作。
     */
    private fun checkScaleDownOperations() {
        val now = System.currentTimeMillis()
        
        // 检查每个缩容操作
        for ((nodeId, operation) in activeScaleDownOperations) {
            // 检查是否超时
            if (now - operation.startTime > scaleDownTimeout.get()) {
                logger.warn("节点 $nodeId 的缩容操作超时，强制完成")
                completeScaleDownOperation(nodeId, true)
                continue
            }
            
            // 检查操作状态
            when (operation.state) {
                ScaleDownState.INITIATED -> {
                    // 开始停止接收新连接
                    startStopAcceptingConnections(nodeId)
                }
                ScaleDownState.STOPPING_CONNECTIONS -> {
                    // 检查是否已经停止接收新连接
                    checkConnectionsStopped(nodeId)
                }
                ScaleDownState.DRAINING_CONNECTIONS -> {
                    // 检查是否已经耗尽连接
                    checkConnectionsDrained(nodeId)
                }
                ScaleDownState.SHUTTING_DOWN -> {
                    // 检查是否已经关闭
                    checkNodeShutdown(nodeId)
                }
                ScaleDownState.COMPLETED -> {
                    // 移除已完成的操作
                    activeScaleDownOperations.remove(nodeId)
                    logger.info("节点 $nodeId 的缩容操作已完成")
                }
            }
        }
    }
    
    /**
     * 开始优雅缩容操作。
     * 
     * @param nodeId 要缩容的节点 ID
     * @return 操作结果的 Future
     */
    fun startGracefulScaleDown(nodeId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 如果优雅缩容未启用，直接返回
        if (!gracefulScaleDownEnabled.get()) {
            logger.warn("优雅缩容未启用，无法执行缩容操作")
            return Future.failedFuture("优雅缩容未启用")
        }
        
        // 检查是否已经有正在进行的缩容操作
        if (activeScaleDownOperations.containsKey(nodeId)) {
            logger.warn("节点 $nodeId 已经有正在进行的缩容操作")
            return Future.failedFuture("节点已经有正在进行的缩容操作")
        }
        
        // 创建新的缩容操作
        val operation = ScaleDownOperation(
            nodeId = nodeId,
            startTime = System.currentTimeMillis(),
            state = ScaleDownState.INITIATED,
            promise = promise
        )
        
        // 添加到活动操作列表
        activeScaleDownOperations[nodeId] = operation
        
        logger.info("开始节点 $nodeId 的优雅缩容操作")
        
        return promise.future()
    }
    
    /**
     * 开始停止接收新连接。
     * 
     * @param nodeId 节点 ID
     */
    private fun startStopAcceptingConnections(nodeId: String) {
        val operation = activeScaleDownOperations[nodeId] ?: return
        
        logger.info("开始停止节点 $nodeId 接收新连接")
        
        // 发送停止接收新连接的命令
        vertx.eventBus().request<JsonObject>("apix.node.stop.accepting", JsonObject()
            .put("nodeId", nodeId)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    // 更新操作状态
                    operation.state = ScaleDownState.STOPPING_CONNECTIONS
                    activeScaleDownOperations[nodeId] = operation
                    
                    logger.info("节点 $nodeId 已开始停止接收新连接")
                } else {
                    logger.error("停止节点 $nodeId 接收新连接失败: ${response.getString("message", "未知错误")}")
                }
            } else {
                logger.error("停止节点 $nodeId 接收新连接失败", ar.cause())
            }
        }
    }
    
    /**
     * 检查是否已经停止接收新连接。
     * 
     * @param nodeId 节点 ID
     */
    private fun checkConnectionsStopped(nodeId: String) {
        val operation = activeScaleDownOperations[nodeId] ?: return
        
        // 检查是否已经停止接收新连接
        vertx.eventBus().request<JsonObject>("apix.node.connections.status", JsonObject()
            .put("nodeId", nodeId)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    val status = response.getJsonObject("result", JsonObject())
                    val acceptingStopped = status.getBoolean("acceptingStopped", false)
                    
                    if (acceptingStopped) {
                        // 更新操作状态
                        operation.state = ScaleDownState.DRAINING_CONNECTIONS
                        operation.drainStartTime = System.currentTimeMillis()
                        activeScaleDownOperations[nodeId] = operation
                        
                        logger.info("节点 $nodeId 已停止接收新连接，开始耗尽现有连接")
                    }
                } else {
                    logger.error("检查节点 $nodeId 连接状态失败: ${response.getString("message", "未知错误")}")
                }
            } else {
                logger.error("检查节点 $nodeId 连接状态失败", ar.cause())
            }
        }
    }
    
    /**
     * 检查是否已经耗尽连接。
     * 
     * @param nodeId 节点 ID
     */
    private fun checkConnectionsDrained(nodeId: String) {
        val operation = activeScaleDownOperations[nodeId] ?: return
        
        // 检查是否已经耗尽连接
        vertx.eventBus().request<JsonObject>("apix.node.connections.status", JsonObject()
            .put("nodeId", nodeId)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    val status = response.getJsonObject("result", JsonObject())
                    val activeConnections = status.getInteger("activeConnections", 0)
                    
                    // 如果没有活动连接或者已经超过耗尽超时时间，开始关闭节点
                    val now = System.currentTimeMillis()
                    val drainStartTime = operation.drainStartTime ?: now
                    val drainTimeElapsed = now - drainStartTime
                    
                    if (activeConnections == 0 || drainTimeElapsed > drainTimeout.get()) {
                        // 更新操作状态
                        operation.state = ScaleDownState.SHUTTING_DOWN
                        activeScaleDownOperations[nodeId] = operation
                        
                        logger.info("节点 $nodeId 的连接已耗尽 (活动连接: $activeConnections)，开始关闭节点")
                        
                        // 开始关闭节点
                        shutdownNode(nodeId)
                    } else {
                        logger.info("节点 $nodeId 仍有 $activeConnections 个活动连接，等待耗尽 (已等待 ${drainTimeElapsed / 1000} 秒)")
                    }
                } else {
                    logger.error("检查节点 $nodeId 连接状态失败: ${response.getString("message", "未知错误")}")
                }
            } else {
                logger.error("检查节点 $nodeId 连接状态失败", ar.cause())
            }
        }
    }
    
    /**
     * 关闭节点。
     * 
     * @param nodeId 节点 ID
     */
    private fun shutdownNode(nodeId: String) {
        // 发送关闭节点的命令
        vertx.eventBus().request<JsonObject>("apix.node.shutdown", JsonObject()
            .put("nodeId", nodeId)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    logger.info("已发送关闭节点 $nodeId 的命令")
                } else {
                    logger.error("发送关闭节点 $nodeId 的命令失败: ${response.getString("message", "未知错误")}")
                    
                    // 即使发送命令失败，也继续检查节点状态
                }
            } else {
                logger.error("发送关闭节点 $nodeId 的命令失败", ar.cause())
                
                // 即使发送命令失败，也继续检查节点状态
            }
        }
    }
    
    /**
     * 检查节点是否已经关闭。
     * 
     * @param nodeId 节点 ID
     */
    private fun checkNodeShutdown(nodeId: String) {
        // 检查节点是否已经关闭
        vertx.eventBus().request<JsonObject>("apix.node.status", JsonObject()
            .put("nodeId", nodeId)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    val status = response.getJsonObject("result", JsonObject())
                    val isOnline = status.getBoolean("online", true)
                    
                    if (!isOnline) {
                        // 节点已关闭，完成缩容操作
                        completeScaleDownOperation(nodeId, false)
                    } else {
                        logger.info("节点 $nodeId 仍在运行，等待关闭")
                    }
                } else {
                    // 如果获取状态失败，可能是节点已经关闭
                    if (response.getString("message", "").contains("not found") || 
                        response.getString("message", "").contains("no such node")) {
                        completeScaleDownOperation(nodeId, false)
                    } else {
                        logger.error("检查节点 $nodeId 状态失败: ${response.getString("message", "未知错误")}")
                    }
                }
            } else {
                // 如果请求失败，可能是节点已经关闭
                if (ar.cause().message?.contains("not found") == true || 
                    ar.cause().message?.contains("no such node") == true) {
                    completeScaleDownOperation(nodeId, false)
                } else {
                    logger.error("检查节点 $nodeId 状态失败", ar.cause())
                }
            }
        }
    }
    
    /**
     * 完成缩容操作。
     * 
     * @param nodeId 节点 ID
     * @param isTimeout 是否因为超时而完成
     */
    private fun completeScaleDownOperation(nodeId: String, isTimeout: Boolean) {
        val operation = activeScaleDownOperations[nodeId] ?: return
        
        // 更新操作状态
        operation.state = ScaleDownState.COMPLETED
        activeScaleDownOperations[nodeId] = operation
        
        // 完成 Promise
        if (isTimeout) {
            logger.warn("节点 $nodeId 的缩容操作因超时而强制完成")
            operation.promise.complete()
        } else {
            logger.info("节点 $nodeId 的缩容操作已成功完成")
            operation.promise.complete()
        }
    }
    
    /**
     * 取消缩容操作。
     * 
     * @param nodeId 节点 ID
     * @return 操作结果的 Future
     */
    fun cancelScaleDown(nodeId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 检查是否有正在进行的缩容操作
        val operation = activeScaleDownOperations[nodeId]
        if (operation == null) {
            logger.warn("节点 $nodeId 没有正在进行的缩容操作")
            return Future.failedFuture("没有正在进行的缩容操作")
        }
        
        // 如果操作已经进入关闭阶段，无法取消
        if (operation.state == ScaleDownState.SHUTTING_DOWN || operation.state == ScaleDownState.COMPLETED) {
            logger.warn("节点 $nodeId 的缩容操作已经进入关闭阶段，无法取消")
            return Future.failedFuture("缩容操作已经进入关闭阶段，无法取消")
        }
        
        // 发送恢复接收新连接的命令
        vertx.eventBus().request<JsonObject>("apix.node.resume.accepting", JsonObject()
            .put("nodeId", nodeId)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    // 移除缩容操作
                    activeScaleDownOperations.remove(nodeId)
                    
                    // 拒绝原始操作的 Promise
                    operation.promise.fail("缩容操作已取消")
                    
                    logger.info("已取消节点 $nodeId 的缩容操作")
                    promise.complete()
                } else {
                    logger.error("取消节点 $nodeId 的缩容操作失败: ${response.getString("message", "未知错误")}")
                    promise.fail(response.getString("message", "未知错误"))
                }
            } else {
                logger.error("取消节点 $nodeId 的缩容操作失败", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取优雅缩容管理器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", gracefulScaleDownEnabled.get())
            .put("scaleDownTimeout", scaleDownTimeout.get())
            .put("drainTimeout", drainTimeout.get())
            .put("checkInterval", checkInterval.get())
        
        // 添加活动缩容操作
        val operationsArray = JsonObject()
        for ((nodeId, operation) in activeScaleDownOperations) {
            operationsArray.put(nodeId, JsonObject()
                .put("nodeId", operation.nodeId)
                .put("startTime", operation.startTime)
                .put("state", operation.state.name)
                .put("drainStartTime", operation.drainStartTime)
                .put("elapsedTime", System.currentTimeMillis() - operation.startTime)
            )
        }
        status.put("activeOperations", operationsArray)
        
        return status
    }
    
    /**
     * 停止优雅缩容管理器。
     * 
     * @return 停止完成的 Future
     */
    fun stop(): Future<Void> {
        logger.info("停止优雅缩容管理器")
        
        // 停止缩容检查定时器
        if (checkTimerId != -1L) {
            vertx.cancelTimer(checkTimerId)
            checkTimerId = -1L
        }
        
        // 取消所有正在进行的缩容操作
        for ((nodeId, operation) in activeScaleDownOperations) {
            if (operation.state != ScaleDownState.COMPLETED) {
                operation.promise.fail("优雅缩容管理器已停止")
            }
        }
        activeScaleDownOperations.clear()
        
        return Future.succeededFuture()
    }
    
    /**
     * 缩容操作状态枚举。
     */
    enum class ScaleDownState {
        INITIATED,              // 已启动
        STOPPING_CONNECTIONS,   // 正在停止接收新连接
        DRAINING_CONNECTIONS,   // 正在耗尽现有连接
        SHUTTING_DOWN,          // 正在关闭节点
        COMPLETED               // 已完成
    }
    
    /**
     * 缩容操作数据类。
     */
    data class ScaleDownOperation(
        val nodeId: String,
        val startTime: Long,
        var state: ScaleDownState,
        val promise: Promise<Void>,
        var drainStartTime: Long? = null
    )
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: GracefulScaleDownManager? = null
        
        /**
         * 获取 GracefulScaleDownManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return GracefulScaleDownManager 实例
         */
        fun getInstance(vertx: Vertx): GracefulScaleDownManager {
            return instance ?: synchronized(this) {
                instance ?: GracefulScaleDownManager(vertx).also { instance = it }
            }
        }
    }
}
