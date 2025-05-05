package com.louloulin.apix.core.http

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 共享连接池，所有事件循环线程共享同一个连接池。
 * 这个类实现了全局共享的HTTP连接池，提高连接复用率，减少TCP/TLS握手开销。
 */
class SharedConnectionPool(
    private val vertx: Vertx,
    private val maxSize: Int = 10000,
    private val ttl: Long = 60000 // 连接存活时间，默认60秒
) {
    private val logger = LoggerFactory.getLogger(SharedConnectionPool::class.java)
    
    // 连接池，按主机名和端口分组
    private val connectionPools = ConcurrentHashMap<String, HttpClient>()
    
    // 连接计数器
    private val connectionCount = AtomicInteger(0)
    
    // 连接使用计数
    private val usageCount = ConcurrentHashMap<String, AtomicInteger>()
    
    // 连接最后使用时间
    private val lastUsedTime = ConcurrentHashMap<String, Long>()
    
    init {
        // 定期清理过期连接
        vertx.setPeriodic(ttl / 2) { _ ->
            cleanupExpiredConnections()
        }
    }
    
    /**
     * 获取连接
     * 
     * @param host 主机名
     * @param port 端口
     * @return 包含连接的Future
     */
    fun getConnection(host: String, port: Int): Future<HttpClient> {
        val key = "$host:$port"
        
        // 更新使用计数和最后使用时间
        usageCount.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
        lastUsedTime[key] = System.currentTimeMillis()
        
        // 从连接池获取连接，如果不存在则创建新连接
        val client = connectionPools.computeIfAbsent(key) { _ ->
            createClient(host, port)
        }
        
        return Future.succeededFuture(client)
    }
    
    /**
     * 创建新的HTTP客户端
     * 
     * @param host 主机名
     * @param port 端口
     * @return HTTP客户端
     */
    private fun createClient(host: String, port: Int): HttpClient {
        logger.debug("创建新的HTTP客户端连接: {}:{}", host, port)
        
        val availableProcessors = Runtime.getRuntime().availableProcessors()
        val options = HttpClientOptions()
            // 连接池设置
            .setKeepAlive(true)
            .setMaxPoolSize(availableProcessors * 10) // 每个核心10个连接
            .setKeepAliveTimeout(60) // 60秒保持连接
            .setIdleTimeout(60) // 60秒空闲超时
            
            // HTTP/2设置
            .setUseAlpn(true)
            .setHttp2ClearTextUpgrade(true)
            .setHttp2MaxPoolSize(availableProcessors * 5)
            .setHttp2MultiplexingLimit(100)
            
            // TCP优化
            .setTcpNoDelay(true)
            .setTcpFastOpen(true)
            .setTcpQuickAck(true)
            
            // 主机和端口
            .setDefaultHost(host)
            .setDefaultPort(port)
        
        connectionCount.incrementAndGet()
        return vertx.createHttpClient(options)
    }
    
    /**
     * 清理过期连接
     */
    private fun cleanupExpiredConnections() {
        val now = System.currentTimeMillis()
        val expiredKeys = lastUsedTime.entries
            .filter { now - it.value > ttl }
            .map { it.key }
        
        for (key in expiredKeys) {
            val usage = usageCount[key]?.get() ?: 0
            if (usage <= 0) {
                // 如果没有活跃使用，关闭连接并从池中移除
                connectionPools.remove(key)?.close()
                usageCount.remove(key)
                lastUsedTime.remove(key)
                connectionCount.decrementAndGet()
                logger.debug("关闭过期连接: {}", key)
            }
        }
    }
    
    /**
     * 关闭所有连接
     */
    fun close() {
        for (client in connectionPools.values) {
            try {
                client.close()
            } catch (e: Exception) {
                logger.warn("关闭HTTP客户端连接失败", e)
            }
        }
        connectionPools.clear()
        usageCount.clear()
        lastUsedTime.clear()
        connectionCount.set(0)
    }
    
    /**
     * 获取连接池统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("connectionCount", connectionCount.get())
            .put("poolSize", connectionPools.size)
            .put("maxSize", maxSize)
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: SharedConnectionPool? = null
        
        /**
         * 获取SharedConnectionPool的单例实例
         * 
         * @param vertx Vertx实例
         * @param maxSize 最大连接数
         * @param ttl 连接存活时间（毫秒）
         * @return SharedConnectionPool实例
         */
        fun getInstance(vertx: Vertx, maxSize: Int = 10000, ttl: Long = 60000): SharedConnectionPool {
            if (INSTANCE == null) {
                synchronized(SharedConnectionPool::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = SharedConnectionPool(vertx, maxSize, ttl)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
