package com.louloulin.apix.ha

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 领导者选举服务，用于在控制平面节点之间选举主节点。
 * 使用 Vert.x 的分布式锁实现领导者选举。
 */
class LeaderElectionService(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(LeaderElectionService::class.java)
    
    // 当前节点是否是领导者
    private val isLeader = AtomicBoolean(false)
    
    // 当前领导者信息
    private val currentLeader = AtomicReference<JsonObject>(null)
    
    // 领导者锁
    private val leaderLock = AtomicReference<io.vertx.core.shareddata.Lock>(null)
    
    // 领导者锁名称
    private val leaderLockName = "apix.leader.lock"
    
    // 领导者检查定时器 ID
    private var leaderCheckTimerId = -1L
    
    // 领导者心跳定时器 ID
    private var leaderHeartbeatTimerId = -1L
    
    // 领导者选举尝试次数
    private val electionAttempts = AtomicLong(0)
    
    // 领导者选举间隔（毫秒）
    private val electionInterval = 5000L
    
    // 领导者心跳间隔（毫秒）
    private val heartbeatInterval = 3000L
    
    // 领导者超时（毫秒）
    private val leaderTimeout = 10000L
    
    // 本地节点 ID
    private var nodeId = "node-${vertx.hashCode()}"
    
    // 领导者变更监听器
    private val leaderChangeListeners = mutableListOf<(Boolean, JsonObject?) -> Unit>()
    
    /**
     * 初始化领导者选举服务。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化领导者选举服务")
        
        // 获取配置
        val haConfig = config.getJsonObject("ha", JsonObject())
        
        // 更新配置参数
        nodeId = haConfig.getString("nodeId", nodeId)
        
        // 启动领导者选举
        startLeaderElection()
        
        return Future.succeededFuture()
    }
    
    /**
     * 启动领导者选举。
     */
    private fun startLeaderElection() {
        logger.info("启动领导者选举")
        
        // 如果 Vert.x 不是集群模式，则直接成为领导者
        if (!vertx.isClustered()) {
            logger.info("Vert.x 不是集群模式，直接成为领导者")
            becomeLeader()
            return
        }
        
        // 开始定期检查领导者
        leaderCheckTimerId = vertx.setPeriodic(electionInterval) { _ ->
            checkLeader()
        }
        
        // 立即进行一次领导者检查
        checkLeader()
    }
    
    /**
     * 检查领导者。
     */
    private fun checkLeader() {
        // 如果已经是领导者，则不需要检查
        if (isLeader.get()) {
            return
        }
        
        // 尝试获取领导者锁
        vertx.sharedData().getLockWithTimeout(leaderLockName, leaderTimeout) { lockResult ->
            if (lockResult.succeeded()) {
                // 获取锁成功，成为领导者
                val lock = lockResult.result()
                leaderLock.set(lock)
                becomeLeader()
            } else {
                // 获取锁失败，检查当前领导者
                electionAttempts.incrementAndGet()
                logger.debug("获取领导者锁失败，尝试次数: {}", electionAttempts.get())
                
                // 获取当前领导者信息
                getLeaderInfo()
            }
        }
    }
    
    /**
     * 成为领导者。
     */
    private fun becomeLeader() {
        if (isLeader.compareAndSet(false, true)) {
            logger.info("节点 {} 成为领导者", nodeId)
            
            // 创建领导者信息
            val leaderInfo = JsonObject()
                .put("nodeId", nodeId)
                .put("timestamp", System.currentTimeMillis())
                .put("address", getNodeAddress())
            
            // 更新领导者信息
            currentLeader.set(leaderInfo)
            
            // 发布领导者信息
            publishLeaderInfo(leaderInfo)
            
            // 启动领导者心跳
            startLeaderHeartbeat()
            
            // 通知领导者变更监听器
            notifyLeaderChangeListeners(true, leaderInfo)
        }
    }
    
    /**
     * 放弃领导者角色。
     */
    private fun resignLeader() {
        if (isLeader.compareAndSet(true, false)) {
            logger.info("节点 {} 放弃领导者角色", nodeId)
            
            // 停止领导者心跳
            stopLeaderHeartbeat()
            
            // 释放领导者锁
            val lock = leaderLock.getAndSet(null)
            if (lock != null) {
                lock.release()
            }
            
            // 清除领导者信息
            val oldLeaderInfo = currentLeader.getAndSet(null)
            
            // 通知领导者变更监听器
            notifyLeaderChangeListeners(false, oldLeaderInfo)
        }
    }
    
    /**
     * 启动领导者心跳。
     */
    private fun startLeaderHeartbeat() {
        // 停止之前的心跳定时器
        stopLeaderHeartbeat()
        
        // 启动新的心跳定时器
        leaderHeartbeatTimerId = vertx.setPeriodic(heartbeatInterval) { _ ->
            // 更新领导者信息的时间戳
            val leaderInfo = currentLeader.get()
            if (leaderInfo != null) {
                leaderInfo.put("timestamp", System.currentTimeMillis())
                publishLeaderInfo(leaderInfo)
            }
        }
    }
    
    /**
     * 停止领导者心跳。
     */
    private fun stopLeaderHeartbeat() {
        if (leaderHeartbeatTimerId != -1L) {
            vertx.cancelTimer(leaderHeartbeatTimerId)
            leaderHeartbeatTimerId = -1L
        }
    }
    
    /**
     * 发布领导者信息。
     * 
     * @param leaderInfo 领导者信息
     */
    private fun publishLeaderInfo(leaderInfo: JsonObject) {
        // 将领导者信息保存到共享数据中
        vertx.sharedData().getAsyncMap<String, String>("apix.leader.info") { mapResult ->
            if (mapResult.succeeded()) {
                val map = mapResult.result()
                map.put("current", leaderInfo.encode()) { putResult ->
                    if (putResult.failed()) {
                        logger.error("发布领导者信息失败", putResult.cause())
                    }
                }
            } else {
                logger.error("获取领导者信息共享映射失败", mapResult.cause())
            }
        }
    }
    
    /**
     * 获取领导者信息。
     */
    private fun getLeaderInfo() {
        vertx.sharedData().getAsyncMap<String, String>("apix.leader.info") { mapResult ->
            if (mapResult.succeeded()) {
                val map = mapResult.result()
                map.get("current") { getResult ->
                    if (getResult.succeeded() && getResult.result() != null) {
                        try {
                            val leaderInfoStr = getResult.result()
                            val leaderInfo = JsonObject(leaderInfoStr)
                            
                            // 检查领导者是否超时
                            val timestamp = leaderInfo.getLong("timestamp", 0L)
                            val now = System.currentTimeMillis()
                            
                            if (now - timestamp > leaderTimeout) {
                                // 领导者超时，尝试成为新的领导者
                                logger.info("领导者超时，尝试成为新的领导者")
                                checkLeader()
                            } else {
                                // 更新当前领导者信息
                                val oldLeaderInfo = currentLeader.getAndSet(leaderInfo)
                                
                                // 如果领导者发生变化，通知监听器
                                if (oldLeaderInfo == null || !oldLeaderInfo.getString("nodeId").equals(leaderInfo.getString("nodeId"))) {
                                    logger.info("领导者变更为: {}", leaderInfo.getString("nodeId"))
                                    notifyLeaderChangeListeners(false, leaderInfo)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("解析领导者信息失败", e)
                        }
                    } else {
                        // 没有领导者信息，尝试成为领导者
                        logger.info("没有领导者信息，尝试成为领导者")
                        checkLeader()
                    }
                }
            } else {
                logger.error("获取领导者信息共享映射失败", mapResult.cause())
            }
        }
    }
    
    /**
     * 获取节点地址。
     * 
     * @return 节点地址
     */
    private fun getNodeAddress(): String {
        // 在实际实现中，这里应该返回节点的实际网络地址
        // 为简化实现，这里返回一个基于节点 ID 的模拟地址
        return "http://localhost:8081"
    }
    
    /**
     * 添加领导者变更监听器。
     * 
     * @param listener 监听器函数，参数为 (isLeader: Boolean, leaderInfo: JsonObject?)
     */
    fun addLeaderChangeListener(listener: (Boolean, JsonObject?) -> Unit) {
        leaderChangeListeners.add(listener)
        
        // 如果已经有领导者信息，立即通知新的监听器
        val leaderInfo = currentLeader.get()
        if (leaderInfo != null) {
            listener(isLeader.get(), leaderInfo)
        }
    }
    
    /**
     * 通知所有领导者变更监听器。
     * 
     * @param isLeader 当前节点是否是领导者
     * @param leaderInfo 领导者信息
     */
    private fun notifyLeaderChangeListeners(isLeader: Boolean, leaderInfo: JsonObject?) {
        for (listener in leaderChangeListeners) {
            try {
                listener(isLeader, leaderInfo)
            } catch (e: Exception) {
                logger.error("通知领导者变更监听器失败", e)
            }
        }
    }
    
    /**
     * 检查当前节点是否是领导者。
     * 
     * @return 当前节点是否是领导者
     */
    fun isLeader(): Boolean {
        return isLeader.get()
    }
    
    /**
     * 获取当前领导者信息。
     * 
     * @return 当前领导者信息，如果没有领导者则返回 null
     */
    fun getCurrentLeader(): JsonObject? {
        return currentLeader.get()
    }
    
    /**
     * 停止领导者选举服务。
     * 
     * @return 停止完成的 Future
     */
    fun stop(): Future<Void> {
        logger.info("停止领导者选举服务")
        
        // 如果是领导者，放弃领导者角色
        if (isLeader.get()) {
            resignLeader()
        }
        
        // 停止领导者检查定时器
        if (leaderCheckTimerId != -1L) {
            vertx.cancelTimer(leaderCheckTimerId)
            leaderCheckTimerId = -1L
        }
        
        return Future.succeededFuture()
    }
    
    /**
     * 获取领导者选举服务状态。
     * 
     * @return 包含服务状态的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("isLeader", isLeader.get())
            .put("nodeId", nodeId)
            .put("electionAttempts", electionAttempts.get())
        
        val leader = currentLeader.get()
        if (leader != null) {
            status.put("currentLeader", leader)
        }
        
        return status
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: LeaderElectionService? = null
        
        /**
         * 获取 LeaderElectionService 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return LeaderElectionService 实例
         */
        fun getInstance(vertx: Vertx): LeaderElectionService {
            return instance ?: synchronized(this) {
                instance ?: LeaderElectionService(vertx).also { instance = it }
            }
        }
    }
}
