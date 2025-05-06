package com.louloulin.apix.core.network

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.core.net.NetServerOptions
import io.vertx.core.net.NetSocket
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * TCP调优器，用于优化TCP连接参数和性能。
 * 提供了自适应调整TCP参数的功能。
 */
class TcpTuner(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(TcpTuner::class.java)
    
    // 连接计数
    private val connectionCount = AtomicInteger(0)
    
    // 总传输字节数
    private val totalBytesTransferred = AtomicLong(0)
    
    // 连接统计信息
    private val connectionStats = ConcurrentHashMap<String, ConnectionStats>()
    
    // 当前TCP参数
    private var currentTcpParams = TcpParams()
    
    /**
     * 初始化TCP调优器
     */
    init {
        logger.info("TCP tuner initialized with default parameters")
        
        // 定期调整TCP参数
        vertx.setPeriodic(300000) { // 每5分钟调整一次
            adjustTcpParams()
        }
    }
    
    /**
     * 跟踪连接
     * 
     * @param socket 网络套接字
     */
    fun trackConnection(socket: NetSocket) {
        // 增加连接计数
        val id = connectionCount.incrementAndGet()
        val connectionId = "${socket.remoteAddress().host()}:${socket.remoteAddress().port()}-$id"
        
        // 创建连接统计信息
        val stats = ConnectionStats(connectionId, System.currentTimeMillis())
        connectionStats[connectionId] = stats
        
        // 监听数据
        socket.handler { buffer ->
            // 更新统计信息
            stats.bytesReceived.addAndGet(buffer.length().toLong())
            totalBytesTransferred.addAndGet(buffer.length().toLong())
        }
        
        // 监听写入完成
        socket.drainHandler {
            // 更新统计信息
            stats.writeQueueFull.incrementAndGet()
        }
        
        // 监听关闭
        socket.closeHandler {
            // 更新统计信息
            stats.endTime = System.currentTimeMillis()
            stats.duration = stats.endTime - stats.startTime
            
            // 移除统计信息
            connectionStats.remove(connectionId)
            
            logger.debug("Connection closed: {}, duration: {}ms, bytes received: {}", 
                connectionId, stats.duration, stats.bytesReceived.get())
        }
    }
    
    /**
     * 调整TCP参数
     */
    private fun adjustTcpParams() {
        // 计算当前负载
        val activeConnections = connectionStats.size
        val bytesPerSecond = calculateBytesPerSecond()
        
        logger.info("Adjusting TCP parameters, active connections: {}, bytes per second: {}", 
            activeConnections, bytesPerSecond)
        
        // 根据负载调整参数
        if (activeConnections > 1000 || bytesPerSecond > 10 * 1024 * 1024) {
            // 高负载情况
            currentTcpParams = TcpParams(
                receiveBufferSize = 32 * 1024,
                sendBufferSize = 32 * 1024,
                acceptBacklog = 10000,
                reusePort = true
            )
            
            logger.info("Adjusted TCP parameters for high load")
        } else if (activeConnections > 100 || bytesPerSecond > 1 * 1024 * 1024) {
            // 中等负载情况
            currentTcpParams = TcpParams(
                receiveBufferSize = 16 * 1024,
                sendBufferSize = 16 * 1024,
                acceptBacklog = 5000,
                reusePort = true
            )
            
            logger.info("Adjusted TCP parameters for medium load")
        } else {
            // 低负载情况
            currentTcpParams = TcpParams(
                receiveBufferSize = 8 * 1024,
                sendBufferSize = 8 * 1024,
                acceptBacklog = 1000,
                reusePort = true
            )
            
            logger.info("Adjusted TCP parameters for low load")
        }
    }
    
    /**
     * 计算每秒传输字节数
     * 
     * @return 每秒传输字节数
     */
    private fun calculateBytesPerSecond(): Long {
        var totalBytes = 0L
        var totalDuration = 0L
        
        connectionStats.values.forEach { stats ->
            if (stats.duration > 0) {
                totalBytes += stats.bytesReceived.get()
                totalDuration += stats.duration
            }
        }
        
        return if (totalDuration > 0) {
            (totalBytes * 1000) / totalDuration
        } else {
            0
        }
    }
    
    /**
     * 应用TCP参数到NetServerOptions
     * 
     * @param options NetServerOptions
     * @return 更新后的NetServerOptions
     */
    fun applyToNetServerOptions(options: NetServerOptions): NetServerOptions {
        return options
            .setReceiveBufferSize(currentTcpParams.receiveBufferSize)
            .setSendBufferSize(currentTcpParams.sendBufferSize)
            .setAcceptBacklog(currentTcpParams.acceptBacklog)
            .setReusePort(currentTcpParams.reusePort)
    }
    
    /**
     * 获取TCP调优器统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("activeConnections", connectionStats.size)
            .put("totalBytesTransferred", totalBytesTransferred.get())
            .put("bytesPerSecond", calculateBytesPerSecond())
            .put("currentParams", JsonObject()
                .put("receiveBufferSize", currentTcpParams.receiveBufferSize)
                .put("sendBufferSize", currentTcpParams.sendBufferSize)
                .put("acceptBacklog", currentTcpParams.acceptBacklog)
                .put("reusePort", currentTcpParams.reusePort)
            )
    }
    
    /**
     * TCP参数类
     */
    data class TcpParams(
        val receiveBufferSize: Int = 8 * 1024,
        val sendBufferSize: Int = 8 * 1024,
        val acceptBacklog: Int = 1000,
        val reusePort: Boolean = true
    )
    
    /**
     * 连接统计信息类
     */
    inner class ConnectionStats(
        val id: String,
        val startTime: Long,
        var endTime: Long = 0,
        var duration: Long = 0,
        val bytesReceived: AtomicLong = AtomicLong(0),
        val writeQueueFull: AtomicInteger = AtomicInteger(0)
    )
    
    companion object {
        // 单例实例
        private var INSTANCE: TcpTuner? = null
        
        /**
         * 获取TcpTuner的单例实例
         * 
         * @param vertx Vertx实例
         * @return TcpTuner实例
         */
        fun getInstance(vertx: Vertx): TcpTuner {
            if (INSTANCE == null) {
                synchronized(TcpTuner::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = TcpTuner(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
