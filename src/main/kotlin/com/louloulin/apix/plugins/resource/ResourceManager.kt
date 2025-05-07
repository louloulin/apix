package com.louloulin.apix.plugins.resource

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.slf4j.LoggerFactory

/**
 * 资源管理器
 * 统一管理所有共享资源
 */
class ResourceManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ResourceManager::class.java)
    
    // 共享资源池
    private val resourcePool = SharedResourcePool.getInstance(vertx)
    
    // 共享连接池
    private val connectionPool = SharedConnectionPool.getInstance(vertx)
    
    // 共享数据管理器
    private val dataManager = SharedDataManager.getInstance(vertx)
    
    /**
     * 获取或创建 WebClient
     */
    fun getOrCreateWebClient(name: String, options: WebClientOptions? = null): WebClient {
        return resourcePool.getOrCreateWebClient(name, options)
    }
    
    /**
     * 获取或创建 HTTP 客户端
     */
    fun getOrCreateHttpClient(name: String, options: HttpClientOptions? = null): HttpClient {
        return connectionPool.getOrCreateHttpClient(name, options)
    }
    
    /**
     * 在异步映射中设置值
     */
    fun putInAsyncMap(mapName: String, key: String, value: JsonObject, ttl: Long = 0): Future<Void> {
        return dataManager.putInAsyncMap(mapName, key, value, ttl)
    }
    
    /**
     * 从异步映射中获取值
     */
    fun getFromAsyncMap(mapName: String, key: String): Future<JsonObject?> {
        return dataManager.getFromAsyncMap(mapName, key)
    }
    
    /**
     * 在本地映射中设置值
     */
    fun putInLocalMap(mapName: String, key: String, value: Any) {
        dataManager.putInLocalMap(mapName, key, value)
    }
    
    /**
     * 从本地映射中获取值
     */
    fun <T> getFromLocalMap(mapName: String, key: String): T? {
        return dataManager.getFromLocalMap(mapName, key)
    }
    
    /**
     * 记录连接使用
     */
    fun recordConnectionUse(name: String) {
        connectionPool.recordConnectionUse(name)
    }
    
    /**
     * 记录连接错误
     */
    fun recordConnectionError(name: String, error: Throwable) {
        connectionPool.recordConnectionError(name, error)
    }
    
    /**
     * 关闭资源管理器
     */
    fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 关闭所有资源
        val futures = listOf(
            resourcePool.close(),
            connectionPool.close(),
            dataManager.close()
        )
        
        // 等待所有资源关闭完成
        Future.all(futures).onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("All resources closed successfully")
                promise.complete()
            } else {
                logger.error("Error closing resources", ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取资源管理器统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("resourcePool", resourcePool.getStats())
            .put("connectionPool", connectionPool.getStats())
            .put("dataManager", dataManager.getStats())
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: ResourceManager? = null
        
        /**
         * 获取 ResourceManager 的单例实例
         */
        fun getInstance(vertx: Vertx): ResourceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ResourceManager(vertx).also { INSTANCE = it }
            }
        }
    }
}
