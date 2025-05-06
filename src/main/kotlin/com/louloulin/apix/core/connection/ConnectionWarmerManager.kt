package com.louloulin.apix.core.connection

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 连接预热管理器，提供了连接预热的高级API。
 * 这个类是ConnectionWarmer的包装器，提供了更方便的API。
 */
class ConnectionWarmerManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ConnectionWarmerManager::class.java)
    
    // 连接预热器
    private val connectionWarmer = ConnectionWarmer.getInstance(vertx)
    
    /**
     * 预热指定主机和端口的连接
     * 
     * @param host 主机名
     * @param port 端口
     * @param count 预热连接数量
     * @param ssl 是否使用SSL/TLS
     * @return 预热结果的Future
     */
    fun warmConnections(host: String, port: Int, count: Int, ssl: Boolean = false): Future<JsonObject> {
        return connectionWarmer.warmConnections(host, port, count, ssl)
    }
    
    /**
     * 批量预热连接
     * 
     * @param endpoints 端点列表，每个端点是一个包含host、port、count和ssl字段的JsonObject
     * @return 预热结果的Future
     */
    fun warmConnectionsBatch(endpoints: List<JsonObject>): Future<JsonObject> {
        val endpointsArray = JsonArray()
        for (endpoint in endpoints) {
            endpointsArray.add(endpoint)
        }
        
        return connectionWarmer.warmConnectionsBatch(endpointsArray)
    }
    
    /**
     * 从配置文件预热连接
     * 
     * @param configPath 配置文件路径
     * @return 预热结果的Future
     */
    fun warmConnectionsFromConfig(configPath: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 读取配置文件
        vertx.fileSystem().readFile(configPath) { ar ->
            if (ar.succeeded()) {
                try {
                    val config = JsonObject(ar.result())
                    val endpoints = config.getJsonArray("endpoints", JsonArray())
                    
                    connectionWarmer.warmConnectionsBatch(endpoints)
                        .onComplete { warmAr ->
                            if (warmAr.succeeded()) {
                                promise.complete(warmAr.result())
                            } else {
                                promise.fail(warmAr.cause())
                            }
                        }
                } catch (e: Exception) {
                    logger.error("解析配置文件时发生错误: {}", configPath, e)
                    promise.fail(e)
                }
            } else {
                logger.error("读取配置文件时发生错误: {}", configPath, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取预热连接
     * 
     * @param host 主机名
     * @param port 端口
     * @return 预热连接的Future，如果没有可用的预热连接，则返回null
     */
    fun getWarmedConnection(host: String, port: Int): Future<HttpClient?> {
        return connectionWarmer.getWarmedConnection(host, port)
    }
    
    /**
     * 返回预热连接
     * 
     * @param host 主机名
     * @param port 端口
     * @param client HTTP客户端
     */
    fun returnWarmedConnection(host: String, port: Int, client: HttpClient) {
        connectionWarmer.returnWarmedConnection(host, port, client)
    }
    
    /**
     * 获取预热连接的统计信息
     */
    fun getStats(): JsonObject {
        return connectionWarmer.getStats()
    }
    
    /**
     * 清理所有预热连接
     */
    fun cleanup(): Future<Void> {
        return connectionWarmer.cleanup()
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: ConnectionWarmerManager? = null
        
        /**
         * 获取ConnectionWarmerManager的单例实例
         */
        fun getInstance(vertx: Vertx): ConnectionWarmerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionWarmerManager(vertx).also { INSTANCE = it }
            }
        }
    }
}
