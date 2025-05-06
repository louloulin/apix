package com.louloulin.apix.core.connection

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 连接预热器，用于预先建立连接，减少在高负载时建立连接的开销。
 * 特别是对于TLS连接，预热可以显著减少握手延迟。
 */
class ConnectionWarmer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ConnectionWarmer::class.java)
    
    // 预热连接的客户端映射，host:port -> HttpClient
    private val warmedClients = ConcurrentHashMap<String, MutableList<HttpClient>>()
    
    // 预热连接的统计信息
    private val warmedConnectionsCount = ConcurrentHashMap<String, AtomicInteger>()
    private val usedConnectionsCount = ConcurrentHashMap<String, AtomicInteger>()
    
    /**
     * 预热指定主机和端口的连接
     * 
     * @param host 主机名
     * @param port 端口
     * @param count 预热连接数量
     * @param ssl 是否使用SSL/TLS
     * @param options 额外的HTTP客户端选项
     * @return 预热结果的Future
     */
    fun warmConnections(
        host: String,
        port: Int,
        count: Int,
        ssl: Boolean = false,
        options: HttpClientOptions? = null
    ): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        val key = "$host:$port"
        
        logger.info("开始预热连接: host={}, port={}, count={}, ssl={}", host, port, count, ssl)
        
        // 创建HTTP客户端选项
        val clientOptions = options ?: HttpClientOptions()
        clientOptions.setDefaultHost(host)
        clientOptions.setDefaultPort(port)
        clientOptions.setSsl(ssl)
        clientOptions.setKeepAlive(true)
        clientOptions.setTcpKeepAlive(true)
        clientOptions.setIdleTimeout(300) // 5分钟空闲超时
        
        // 创建预热连接
        val results = mutableListOf<Future<HttpClient>>()
        for (i in 0 until count) {
            results.add(createWarmedConnection(key, clientOptions))
        }
        
        // 等待所有连接预热完成
        Future.all(results)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val successCount = warmedConnectionsCount.getOrDefault(key, AtomicInteger(0)).get()
                    logger.info("连接预热完成: host={}, port={}, success={}/{}", host, port, successCount, count)
                    
                    promise.complete(JsonObject()
                        .put("host", host)
                        .put("port", port)
                        .put("requested", count)
                        .put("success", successCount)
                    )
                } else {
                    logger.error("连接预热失败: host={}, port={}", host, port, ar.cause())
                    
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 创建单个预热连接
     */
    private fun createWarmedConnection(key: String, options: HttpClientOptions): Future<HttpClient> {
        val promise = Promise.promise<HttpClient>()
        
        try {
            // 创建HTTP客户端
            val client = vertx.createHttpClient(options)
            
            // 发送HEAD请求以建立连接
            client.request(options.defaultPort, options.defaultHost, "/")
                .compose { request ->
                    request.setMethod(io.vertx.core.http.HttpMethod.HEAD)
                    request.putHeader("Connection", "keep-alive")
                    request.send()
                }
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        // 连接成功，保存客户端
                        warmedClients.computeIfAbsent(key) { mutableListOf() }.add(client)
                        warmedConnectionsCount.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
                        
                        logger.debug("连接预热成功: {}", key)
                        promise.complete(client)
                    } else {
                        // 连接失败，关闭客户端
                        logger.warn("连接预热失败: {}", key, ar.cause())
                        client.close()
                        promise.fail(ar.cause())
                    }
                }
        } catch (e: Exception) {
            logger.error("创建预热连接时发生错误: {}", key, e)
            promise.fail(e)
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
        val promise = Promise.promise<HttpClient?>()
        val key = "$host:$port"
        
        // 获取预热连接列表
        val clients = warmedClients[key]
        if (clients.isNullOrEmpty()) {
            // 没有可用的预热连接
            promise.complete(null)
            return promise.future()
        }
        
        // 从列表中移除一个连接
        synchronized(clients) {
            if (clients.isNotEmpty()) {
                val client = clients.removeAt(0)
                usedConnectionsCount.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
                promise.complete(client)
            } else {
                promise.complete(null)
            }
        }
        
        return promise.future()
    }
    
    /**
     * 返回预热连接
     * 
     * @param host 主机名
     * @param port 端口
     * @param client HTTP客户端
     */
    fun returnWarmedConnection(host: String, port: Int, client: HttpClient) {
        val key = "$host:$port"
        
        // 将连接添加回预热连接列表
        warmedClients.computeIfAbsent(key) { mutableListOf() }.add(client)
    }
    
    /**
     * 批量预热连接
     * 
     * @param endpoints 端点列表，每个端点是一个包含host、port、count和ssl字段的JsonObject
     * @return 预热结果的Future
     */
    fun warmConnectionsBatch(endpoints: JsonArray): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        val results = mutableListOf<Future<JsonObject>>()
        
        // 遍历端点列表
        for (i in 0 until endpoints.size()) {
            val endpoint = endpoints.getJsonObject(i)
            val host = endpoint.getString("host")
            val port = endpoint.getInteger("port")
            val count = endpoint.getInteger("count", 10)
            val ssl = endpoint.getBoolean("ssl", false)
            
            // 预热连接
            results.add(warmConnections(host, port, count, ssl))
        }
        
        // 等待所有预热完成
        Future.all(results)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    val resultsArray = JsonArray()
                    for (future in results) {
                        if (future.succeeded()) {
                            resultsArray.add(future.result())
                        }
                    }
                    
                    promise.complete(JsonObject()
                        .put("success", true)
                        .put("results", resultsArray)
                    )
                } else {
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 获取预热连接的统计信息
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
        
        // 添加总体统计信息
        var totalWarmed = 0
        var totalUsed = 0
        
        // 添加每个端点的统计信息
        val endpointsStats = JsonObject()
        for ((key, clients) in warmedClients) {
            val warmed = warmedConnectionsCount.getOrDefault(key, AtomicInteger(0)).get()
            val used = usedConnectionsCount.getOrDefault(key, AtomicInteger(0)).get()
            val available = clients.size
            
            totalWarmed += warmed
            totalUsed += used
            
            endpointsStats.put(key, JsonObject()
                .put("warmed", warmed)
                .put("used", used)
                .put("available", available)
            )
        }
        
        stats.put("total_warmed", totalWarmed)
        stats.put("total_used", totalUsed)
        stats.put("endpoints", endpointsStats)
        
        return stats
    }
    
    /**
     * 清理所有预热连接
     */
    fun cleanup(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 关闭所有预热连接
            for ((key, clients) in warmedClients) {
                logger.info("清理预热连接: {}, count={}", key, clients.size)
                
                for (client in clients) {
                    try {
                        client.close()
                    } catch (e: Exception) {
                        logger.warn("关闭预热连接时发生错误: {}", key, e)
                    }
                }
                
                clients.clear()
            }
            
            warmedClients.clear()
            warmedConnectionsCount.clear()
            usedConnectionsCount.clear()
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("清理预热连接时发生错误", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: ConnectionWarmer? = null
        
        /**
         * 获取ConnectionWarmer的单例实例
         */
        fun getInstance(vertx: Vertx): ConnectionWarmer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionWarmer(vertx).also { INSTANCE = it }
            }
        }
    }
}
