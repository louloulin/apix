package com.louloulin.apix.core.network

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.NetSocket
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 网络优化器，用于优化网络连接和传输性能。
 * 实现了连接池管理、TCP优化和流量控制等功能。
 */
class NetworkOptimizer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(NetworkOptimizer::class.java)
    
    // 活跃连接计数
    private val activeConnections = AtomicInteger(0)
    
    // 总连接计数
    private val totalConnections = AtomicLong(0)
    
    // 总传输字节数
    private val totalBytesTransferred = AtomicLong(0)
    
    // 连接池
    private val connectionPools = ConcurrentHashMap<String, ConnectionPool>()
    
    /**
     * 初始化网络优化器
     */
    init {
        logger.info("Network optimizer initialized")
        
        // 定期清理空闲连接
        vertx.setPeriodic(60000) { // 每分钟清理一次
            cleanupIdleConnections()
        }
    }
    
    /**
     * 创建优化的HTTP服务器选项
     * 
     * @param baseOptions 基础选项，如果为null则创建新的选项
     * @return 优化后的HTTP服务器选项
     */
    fun createOptimizedHttpServerOptions(baseOptions: HttpServerOptions? = null): HttpServerOptions {
        val options = baseOptions ?: HttpServerOptions()
        
        return options
            // TCP优化
            .setTcpNoDelay(true)              // 禁用Nagle算法，减少延迟
            .setTcpFastOpen(true)             // 启用TCP Fast Open，加快连接建立
            .setTcpQuickAck(true)             // 启用TCP Quick ACK，提高响应性
            .setReusePort(true)               // 启用端口重用，提高负载分布
            .setReuseAddress(true)            // 启用地址重用，加快重启
            // 连接积压队列
            .setAcceptBacklog(10000)          // 增加连接积压队列大小
            // 超时设置
            .setIdleTimeout(300)              // 空闲超时时间（秒）
            // 缓冲区设置
            .setReceiveBufferSize(4 * 1024)   // 接收缓冲区大小
            .setSendBufferSize(4 * 1024)      // 发送缓冲区大小
    }
    
    /**
     * 创建优化的HTTP客户端选项
     * 
     * @param baseOptions 基础选项，如果为null则创建新的选项
     * @return 优化后的HTTP客户端选项
     */
    fun createOptimizedHttpClientOptions(baseOptions: HttpClientOptions? = null): HttpClientOptions {
        val options = baseOptions ?: HttpClientOptions()
        
        return options
            // 连接池设置
            .setMaxPoolSize(500)              // 最大连接池大小
            .setKeepAlive(true)               // 启用Keep-Alive
            .setKeepAliveTimeout(60)          // Keep-Alive超时时间（秒）
            .setMaxWaitQueueSize(1000)        // 最大等待队列大小
            // TCP优化
            .setTcpNoDelay(true)              // 禁用Nagle算法
            .setTcpFastOpen(true)             // 启用TCP Fast Open
            .setTcpQuickAck(true)             // 启用TCP Quick ACK
            // HTTP/2设置
            .setUseAlpn(true)                 // 启用ALPN
            .setHttp2MaxPoolSize(50)          // HTTP/2连接池大小
            .setHttp2MultiplexingLimit(200)   // 每个连接的最大流数
            .setHttp2KeepAliveTimeout(60)     // HTTP/2 Keep-Alive超时时间（秒）
            // 超时设置
            .setConnectTimeout(10000)         // 连接超时时间（毫秒）
            .setIdleTimeout(60)               // 空闲超时时间（秒）
    }
    
    /**
     * 创建优化的TCP服务器选项
     * 
     * @param baseOptions 基础选项，如果为null则创建新的选项
     * @return 优化后的TCP服务器选项
     */
    fun createOptimizedNetServerOptions(baseOptions: NetServerOptions? = null): NetServerOptions {
        val options = baseOptions ?: NetServerOptions()
        
        return options
            // TCP优化
            .setTcpNoDelay(true)              // 禁用Nagle算法
            .setTcpFastOpen(true)             // 启用TCP Fast Open
            .setTcpQuickAck(true)             // 启用TCP Quick ACK
            .setReusePort(true)               // 启用端口重用
            .setReuseAddress(true)            // 启用地址重用
            // 连接积压队列
            .setAcceptBacklog(10000)          // 增加连接积压队列大小
            // 超时设置
            .setIdleTimeout(300)              // 空闲超时时间（秒）
    }
    
    /**
     * 获取或创建连接池
     * 
     * @param host 主机名
     * @param port 端口
     * @param maxSize 最大连接数
     * @return 连接池
     */
    fun getOrCreateConnectionPool(host: String, port: Int, maxSize: Int = 100): ConnectionPool {
        val key = "$host:$port"
        return connectionPools.computeIfAbsent(key) { ConnectionPool(vertx, host, port, maxSize) }
    }
    
    /**
     * 清理空闲连接
     */
    private fun cleanupIdleConnections() {
        connectionPools.forEach { (key, pool) ->
            pool.cleanupIdleConnections()
            
            // 如果连接池为空，移除它
            if (pool.size() == 0) {
                connectionPools.remove(key)
                logger.debug("Removed empty connection pool: {}", key)
            }
        }
    }
    
    /**
     * 跟踪连接
     * 
     * @param socket 网络套接字
     */
    fun trackConnection(socket: NetSocket) {
        // 增加活跃连接计数
        activeConnections.incrementAndGet()
        
        // 增加总连接计数
        totalConnections.incrementAndGet()
        
        // 监听关闭事件
        socket.closeHandler {
            // 减少活跃连接计数
            activeConnections.decrementAndGet()
        }
        
        // 监听数据传输
        socket.handler { buffer ->
            // 增加总传输字节数
            totalBytesTransferred.addAndGet(buffer.length().toLong())
        }
    }
    
    /**
     * 获取网络统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("activeConnections", activeConnections.get())
            .put("totalConnections", totalConnections.get())
            .put("totalBytesTransferred", totalBytesTransferred.get())
            .put("connectionPools", JsonObject().apply {
                connectionPools.forEach { (key, pool) ->
                    put(key, pool.getStats())
                }
            })
    }
    
    /**
     * 连接池类
     */
    inner class ConnectionPool(
        private val vertx: Vertx,
        private val host: String,
        private val port: Int,
        private val maxSize: Int
    ) {
        private val logger = LoggerFactory.getLogger(ConnectionPool::class.java)
        
        // 空闲连接
        private val idleConnections = mutableListOf<NetSocket>()
        
        // 活跃连接
        private val activeConnections = mutableListOf<NetSocket>()
        
        // 最后使用时间
        private val lastUsedTime = ConcurrentHashMap<NetSocket, Long>()
        
        // 连接计数
        private val connectionCount = AtomicInteger(0)
        
        /**
         * 获取连接
         * 
         * @return 包含连接的Future
         */
        fun getConnection(): Future<NetSocket> {
            val promise = Promise.promise<NetSocket>()
            
            synchronized(idleConnections) {
                if (idleConnections.isNotEmpty()) {
                    // 有空闲连接，使用它
                    val connection = idleConnections.removeAt(0)
                    activeConnections.add(connection)
                    lastUsedTime[connection] = System.currentTimeMillis()
                    promise.complete(connection)
                } else if (connectionCount.get() < maxSize) {
                    // 创建新连接
                    vertx.createNetClient().connect(port, host) { ar ->
                        if (ar.succeeded()) {
                            val connection = ar.result()
                            
                            // 增加连接计数
                            connectionCount.incrementAndGet()
                            
                            // 添加到活跃连接
                            activeConnections.add(connection)
                            
                            // 记录最后使用时间
                            lastUsedTime[connection] = System.currentTimeMillis()
                            
                            // 监听关闭事件
                            connection.closeHandler {
                                synchronized(idleConnections) {
                                    idleConnections.remove(connection)
                                    activeConnections.remove(connection)
                                    lastUsedTime.remove(connection)
                                    connectionCount.decrementAndGet()
                                }
                            }
                            
                            promise.complete(connection)
                        } else {
                            promise.fail(ar.cause())
                        }
                    }
                } else {
                    // 连接池已满，等待连接释放
                    promise.fail("Connection pool is full")
                }
            }
            
            return promise.future()
        }
        
        /**
         * 释放连接
         * 
         * @param connection 要释放的连接
         */
        fun releaseConnection(connection: NetSocket) {
            synchronized(idleConnections) {
                if (activeConnections.remove(connection)) {
                    // 添加到空闲连接
                    idleConnections.add(connection)
                    
                    // 更新最后使用时间
                    lastUsedTime[connection] = System.currentTimeMillis()
                }
            }
        }
        
        /**
         * 清理空闲连接
         */
        fun cleanupIdleConnections() {
            val now = System.currentTimeMillis()
            val idleTimeout = 60000L // 60秒
            
            synchronized(idleConnections) {
                val iterator = idleConnections.iterator()
                while (iterator.hasNext()) {
                    val connection = iterator.next()
                    val lastUsed = lastUsedTime[connection] ?: 0
                    
                    if (now - lastUsed > idleTimeout) {
                        // 连接空闲时间过长，关闭它
                        connection.close()
                        iterator.remove()
                        lastUsedTime.remove(connection)
                        connectionCount.decrementAndGet()
                    }
                }
            }
        }
        
        /**
         * 获取连接池大小
         * 
         * @return 连接池大小
         */
        fun size(): Int {
            return connectionCount.get()
        }
        
        /**
         * 获取连接池统计信息
         * 
         * @return 包含统计信息的JsonObject
         */
        fun getStats(): JsonObject {
            return JsonObject()
                .put("host", host)
                .put("port", port)
                .put("maxSize", maxSize)
                .put("currentSize", connectionCount.get())
                .put("activeConnections", activeConnections.size)
                .put("idleConnections", idleConnections.size)
        }
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: NetworkOptimizer? = null
        
        /**
         * 获取NetworkOptimizer的单例实例
         * 
         * @param vertx Vertx实例
         * @return NetworkOptimizer实例
         */
        fun getInstance(vertx: Vertx): NetworkOptimizer {
            if (INSTANCE == null) {
                synchronized(NetworkOptimizer::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = NetworkOptimizer(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
