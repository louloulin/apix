package com.louloulin.apix.plugins.resource

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 共享连接池
 * 用于管理和共享 HTTP 连接
 */
class SharedConnectionPool(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SharedConnectionPool::class.java)
    
    // 共享的 HTTP 客户端
    private val httpClients = ConcurrentHashMap<String, HttpClient>()
    
    // 连接使用统计
    private val connectionUsage = ConcurrentHashMap<String, AtomicInteger>()
    private val requestCounts = ConcurrentHashMap<String, AtomicLong>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()
    
    /**
     * 获取或创建 HTTP 客户端
     * 
     * @param name 客户端名称
     * @param options 客户端选项
     * @return HTTP 客户端
     */
    fun getOrCreateHttpClient(name: String, options: HttpClientOptions? = null): HttpClient {
        return httpClients.computeIfAbsent(name) { 
            logger.info("Creating HTTP client: {}", name)
            connectionUsage.putIfAbsent(name, AtomicInteger(0))
            requestCounts.putIfAbsent(name, AtomicLong(0))
            errorCounts.putIfAbsent(name, AtomicLong(0))
            
            if (options != null) {
                vertx.createHttpClient(options)
            } else {
                vertx.createHttpClient()
            }
        }
    }
    
    /**
     * 获取 HTTP 客户端
     * 
     * @param name 客户端名称
     * @return HTTP 客户端或 null
     */
    fun getHttpClient(name: String): HttpClient? {
        return httpClients[name]
    }
    
    /**
     * 记录连接使用
     * 
     * @param name 客户端名称
     */
    fun recordConnectionUse(name: String) {
        connectionUsage.computeIfAbsent(name) { AtomicInteger(0) }.incrementAndGet()
        requestCounts.computeIfAbsent(name) { AtomicLong(0) }.incrementAndGet()
        
        // 发布连接使用事件
        vertx.eventBus().publish(
            "connection.pool.use",
            JsonObject()
                .put("name", name)
                .put("timestamp", System.currentTimeMillis())
        )
    }
    
    /**
     * 记录连接错误
     * 
     * @param name 客户端名称
     * @param error 错误
     */
    fun recordConnectionError(name: String, error: Throwable) {
        errorCounts.computeIfAbsent(name) { AtomicLong(0) }.incrementAndGet()
        
        // 发布连接错误事件
        vertx.eventBus().publish(
            "connection.pool.error",
            JsonObject()
                .put("name", name)
                .put("error", error.message)
                .put("timestamp", System.currentTimeMillis())
        )
    }
    
    /**
     * 关闭所有连接
     * 
     * @return 关闭完成的 Future
     */
    fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 关闭所有 HTTP 客户端
            httpClients.forEach { (name, client) ->
                logger.info("Closing HTTP client: {}", name)
                client.close()
            }
            httpClients.clear()
            
            // 清理统计信息
            connectionUsage.clear()
            requestCounts.clear()
            errorCounts.clear()
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error closing connection pool", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取连接池统计信息
     * 
     * @return 连接池统计信息
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("clientCount", httpClients.size)
        
        // 添加每个客户端的统计信息
        val clientStats = JsonObject()
        httpClients.keys.forEach { name ->
            val clientStat = JsonObject()
                .put("connections", connectionUsage[name]?.get() ?: 0)
                .put("requests", requestCounts[name]?.get() ?: 0)
                .put("errors", errorCounts[name]?.get() ?: 0)
            
            clientStats.put(name, clientStat)
        }
        
        stats.put("clients", clientStats)
        return stats
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: SharedConnectionPool? = null
        
        /**
         * 获取 SharedConnectionPool 的单例实例
         */
        fun getInstance(vertx: Vertx): SharedConnectionPool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedConnectionPool(vertx).also { INSTANCE = it }
            }
        }
    }
}
