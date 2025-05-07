package com.louloulin.apix.plugins.resource

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 共享资源池
 * 用于管理和共享插件系统中的资源，如连接池、缓存等
 */
class SharedResourcePool(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SharedResourcePool::class.java)
    
    // 共享的 WebClient 实例
    private val webClients = ConcurrentHashMap<String, WebClient>()
    
    // 共享的缓存
    private val caches = ConcurrentHashMap<String, Any>()
    
    // 共享的配置
    private val configs = ConcurrentHashMap<String, JsonObject>()
    
    /**
     * 获取或创建 WebClient
     * 
     * @param name 客户端名称
     * @param options 客户端选项
     * @return WebClient 实例
     */
    fun getOrCreateWebClient(name: String, options: WebClientOptions? = null): WebClient {
        return webClients.computeIfAbsent(name) { 
            logger.info("Creating WebClient: {}", name)
            if (options != null) {
                WebClient.create(vertx, options)
            } else {
                WebClient.create(vertx)
            }
        }
    }
    
    /**
     * 获取缓存
     * 
     * @param name 缓存名称
     * @return 缓存实例
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getCache(name: String): T? {
        return caches[name] as? T
    }
    
    /**
     * 设置缓存
     * 
     * @param name 缓存名称
     * @param cache 缓存实例
     */
    fun <T> setCache(name: String, cache: T) {
        caches[name] = cache as Any
    }
    
    /**
     * 获取配置
     * 
     * @param name 配置名称
     * @return 配置
     */
    fun getConfig(name: String): JsonObject? {
        return configs[name]
    }
    
    /**
     * 设置配置
     * 
     * @param name 配置名称
     * @param config 配置
     */
    fun setConfig(name: String, config: JsonObject) {
        configs[name] = config
    }
    
    /**
     * 关闭所有资源
     * 
     * @return 关闭完成的 Future
     */
    fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 关闭所有 WebClient
            webClients.forEach { (name, client) ->
                logger.info("Closing WebClient: {}", name)
                client.close()
            }
            webClients.clear()
            
            // 清理缓存
            caches.clear()
            
            // 清理配置
            configs.clear()
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error closing shared resources", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取资源统计信息
     * 
     * @return 资源统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("webClients", webClients.size)
            .put("caches", caches.size)
            .put("configs", configs.size)
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: SharedResourcePool? = null
        
        /**
         * 获取 SharedResourcePool 的单例实例
         */
        fun getInstance(vertx: Vertx): SharedResourcePool {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedResourcePool(vertx).also { INSTANCE = it }
            }
        }
    }
}
