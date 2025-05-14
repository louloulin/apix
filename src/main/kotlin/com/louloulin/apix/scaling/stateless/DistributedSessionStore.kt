package com.louloulin.apix.scaling.stateless

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

/**
 * 分布式会话存储
 * 
 * 提供集群环境中的会话管理功能
 */
class DistributedSessionStore(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(DistributedSessionStore::class.java)
    
    // 会话过期时间（默认30分钟）
    private val sessionTimeout = TimeUnit.MINUTES.toMillis(30)
    
    // 会话清理间隔（默认5分钟）
    private val cleanupInterval = TimeUnit.MINUTES.toMillis(5)
    
    // 会话存储名称
    private val sessionMapName = "apix.sessions"
    
    // 会话访问时间存储名称
    private val sessionAccessMapName = "apix.sessions.access"
    
    init {
        // 启动会话清理定时器
        startCleanupTimer()
    }
    
    /**
     * 创建会话
     * 
     * @param sessionId 会话ID
     * @param data 会话数据
     * @return 操作结果的Future
     */
    fun createSession(sessionId: String, data: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 获取会话存储
        vertx.sharedData().getAsyncMap<String, String>(sessionMapName) { ar ->
            if (ar.succeeded()) {
                val sessionMap = ar.result()
                
                // 存储会话数据
                sessionMap.put(sessionId, data.encode()) { putAr ->
                    if (putAr.succeeded()) {
                        // 更新会话访问时间
                        updateSessionAccessTime(sessionId)
                            .onSuccess {
                                promise.complete()
                            }
                            .onFailure { err ->
                                logger.error("更新会话访问时间失败", err)
                                promise.fail(err)
                            }
                    } else {
                        logger.error("存储会话数据失败", putAr.cause())
                        promise.fail(putAr.cause())
                    }
                }
            } else {
                logger.error("获取会话存储失败", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取会话
     * 
     * @param sessionId 会话ID
     * @return 包含会话数据的Future
     */
    fun getSession(sessionId: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        
        // 获取会话存储
        vertx.sharedData().getAsyncMap<String, String>(sessionMapName) { ar ->
            if (ar.succeeded()) {
                val sessionMap = ar.result()
                
                // 获取会话数据
                sessionMap.get(sessionId) { getAr ->
                    if (getAr.succeeded()) {
                        val sessionData = getAr.result()
                        
                        if (sessionData != null) {
                            try {
                                // 解析会话数据
                                val session = JsonObject(sessionData)
                                promise.complete(session)
                            } catch (e: Exception) {
                                logger.error("解析会话数据失败", e)
                                promise.fail(e)
                            }
                        } else {
                            // 会话不存在
                            promise.complete(null)
                        }
                    } else {
                        logger.error("获取会话数据失败", getAr.cause())
                        promise.fail(getAr.cause())
                    }
                }
            } else {
                logger.error("获取会话存储失败", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 更新会话
     * 
     * @param sessionId 会话ID
     * @param data 会话数据
     * @return 操作结果的Future
     */
    fun updateSession(sessionId: String, data: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 获取会话存储
        vertx.sharedData().getAsyncMap<String, String>(sessionMapName) { ar ->
            if (ar.succeeded()) {
                val sessionMap = ar.result()
                
                // 更新会话数据
                sessionMap.put(sessionId, data.encode()) { putAr ->
                    if (putAr.succeeded()) {
                        // 更新会话访问时间
                        updateSessionAccessTime(sessionId)
                            .onSuccess {
                                promise.complete()
                            }
                            .onFailure { err ->
                                logger.error("更新会话访问时间失败", err)
                                promise.fail(err)
                            }
                    } else {
                        logger.error("更新会话数据失败", putAr.cause())
                        promise.fail(putAr.cause())
                    }
                }
            } else {
                logger.error("获取会话存储失败", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 删除会话
     * 
     * @param sessionId 会话ID
     * @return 操作结果的Future
     */
    fun deleteSession(sessionId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 获取会话存储
        vertx.sharedData().getAsyncMap<String, String>(sessionMapName) { ar ->
            if (ar.succeeded()) {
                val sessionMap = ar.result()
                
                // 删除会话数据
                sessionMap.remove(sessionId) { removeAr ->
                    if (removeAr.succeeded()) {
                        // 删除会话访问时间
                        vertx.sharedData().getAsyncMap<String, Long>(sessionAccessMapName) { accessAr ->
                            if (accessAr.succeeded()) {
                                val accessMap = accessAr.result()
                                
                                accessMap.remove(sessionId) { accessRemoveAr ->
                                    if (accessRemoveAr.succeeded()) {
                                        promise.complete()
                                    } else {
                                        logger.error("删除会话访问时间失败", accessRemoveAr.cause())
                                        promise.fail(accessRemoveAr.cause())
                                    }
                                }
                            } else {
                                logger.error("获取会话访问时间存储失败", accessAr.cause())
                                promise.fail(accessAr.cause())
                            }
                        }
                    } else {
                        logger.error("删除会话数据失败", removeAr.cause())
                        promise.fail(removeAr.cause())
                    }
                }
            } else {
                logger.error("获取会话存储失败", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 更新会话访问时间
     * 
     * @param sessionId 会话ID
     * @return 操作结果的Future
     */
    fun updateSessionAccessTime(sessionId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 获取会话访问时间存储
        vertx.sharedData().getAsyncMap<String, Long>(sessionAccessMapName) { ar ->
            if (ar.succeeded()) {
                val accessMap = ar.result()
                
                // 更新会话访问时间
                accessMap.put(sessionId, System.currentTimeMillis()) { putAr ->
                    if (putAr.succeeded()) {
                        promise.complete()
                    } else {
                        logger.error("更新会话访问时间失败", putAr.cause())
                        promise.fail(putAr.cause())
                    }
                }
            } else {
                logger.error("获取会话访问时间存储失败", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 启动会话清理定时器
     */
    private fun startCleanupTimer() {
        vertx.setPeriodic(cleanupInterval) { _ ->
            cleanupExpiredSessions()
        }
    }
    
    /**
     * 清理过期会话
     */
    private fun cleanupExpiredSessions() {
        logger.debug("开始清理过期会话")
        
        // 获取会话访问时间存储
        vertx.sharedData().getAsyncMap<String, Long>(sessionAccessMapName) { ar ->
            if (ar.succeeded()) {
                val accessMap = ar.result()
                
                // 获取所有会话访问时间
                accessMap.entries { entriesAr ->
                    if (entriesAr.succeeded()) {
                        val entries = entriesAr.result()
                        val now = System.currentTimeMillis()
                        val expiredSessionIds = mutableListOf<String>()
                        
                        // 找出过期的会话
                        for (entry in entries) {
                            val sessionId = entry.key
                            val accessTime = entry.value
                            
                            if (now - accessTime > sessionTimeout) {
                                expiredSessionIds.add(sessionId)
                            }
                        }
                        
                        // 删除过期的会话
                        if (expiredSessionIds.isNotEmpty()) {
                            logger.debug("发现 {} 个过期会话", expiredSessionIds.size)
                            
                            for (sessionId in expiredSessionIds) {
                                deleteSession(sessionId)
                                    .onSuccess {
                                        logger.debug("删除过期会话成功: {}", sessionId)
                                    }
                                    .onFailure { err ->
                                        logger.error("删除过期会话失败: {}", sessionId, err)
                                    }
                            }
                        } else {
                            logger.debug("没有发现过期会话")
                        }
                    } else {
                        logger.error("获取会话访问时间条目失败", entriesAr.cause())
                    }
                }
            } else {
                logger.error("获取会话访问时间存储失败", ar.cause())
            }
        }
    }
    
    /**
     * 设置会话过期时间
     * 
     * @param timeout 过期时间（毫秒）
     */
    fun setSessionTimeout(timeout: Long) {
        if (timeout > 0) {
            this.sessionTimeout = timeout
        }
    }
    
    /**
     * 设置会话清理间隔
     * 
     * @param interval 清理间隔（毫秒）
     */
    fun setCleanupInterval(interval: Long) {
        if (interval > 0) {
            this.cleanupInterval = interval
            
            // 重新启动清理定时器
            vertx.cancelTimer(cleanupTimerId)
            startCleanupTimer()
        }
    }
    
    // 清理定时器ID
    private var cleanupTimerId: Long = -1
    
    // 会话过期时间（可配置）
    private var sessionTimeout: Long = TimeUnit.MINUTES.toMillis(30)
    
    // 会话清理间隔（可配置）
    private var cleanupInterval: Long = TimeUnit.MINUTES.toMillis(5)
}
