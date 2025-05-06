package com.louloulin.apix.plugins.optimization

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginChain
import com.louloulin.apix.plugins.PluginManager
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 优化的插件管理器，用于高效管理和执行插件。
 * 整合了插件优化器的功能，提供更高效的插件执行机制。
 */
class OptimizedPluginManager(private val vertx: Vertx) : PluginManager {
    private val logger = LoggerFactory.getLogger(OptimizedPluginManager::class.java)
    
    // 插件优化器
    private val pluginOptimizer = PluginOptimizer.getInstance(vertx)
    
    // 插件映射
    private val plugins = ConcurrentHashMap<String, Plugin>()
    
    // 插件链映射
    private val pluginChains = ConcurrentHashMap<String, PluginChain>()
    
    // 路径映射的插件链
    private val pathPluginChains = ConcurrentHashMap<String, PluginChain>()
    
    /**
     * 初始化插件管理器
     */
    init {
        logger.info("Optimized plugin manager initialized")
    }
    
    /**
     * 注册插件
     * 
     * @param plugin 要注册的插件
     * @return 注册结果的Future
     */
    override fun registerPlugin(plugin: Plugin): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 检查插件ID是否已存在
            if (plugins.containsKey(plugin.id)) {
                promise.fail("Plugin with ID ${plugin.id} already exists")
                return promise.future()
            }
            
            // 注册插件
            plugins[plugin.id] = plugin
            
            logger.info("Plugin registered: {} ({})", plugin.id, plugin.type)
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to register plugin: {}", plugin.id, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 注册插件链
     * 
     * @param chain 要注册的插件链
     * @return 注册结果的Future
     */
    override fun registerPluginChain(chain: PluginChain): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 检查插件链ID是否已存在
            if (pluginChains.containsKey(chain.id)) {
                promise.fail("Plugin chain with ID ${chain.id} already exists")
                return promise.future()
            }
            
            // 优化插件链
            val optimizedChain = pluginOptimizer.optimizePluginChain(chain)
            
            // 注册插件链
            pluginChains[optimizedChain.id] = optimizedChain
            
            // 如果有路径模式，注册到路径映射
            optimizedChain.pathPattern?.let { pattern ->
                pathPluginChains[pattern] = optimizedChain
            }
            
            logger.info("Plugin chain registered: {}", optimizedChain.id)
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to register plugin chain: {}", chain.id, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 执行插件链
     * 
     * @param chainId 插件链ID
     * @param context 路由上下文
     * @return 执行结果的Future
     */
    override fun executePluginChain(chainId: String, context: RoutingContext): Future<Void> {
        val chain = pluginChains[chainId]
        
        if (chain == null) {
            logger.warn("Plugin chain not found: {}", chainId)
            return Future.failedFuture("Plugin chain not found: $chainId")
        }
        
        // 获取插件链中的插件
        val chainPlugins = chain.pluginIds.mapNotNull { plugins[it] }
        
        // 使用插件优化器执行插件链
        return pluginOptimizer.optimizeChainExecution(chainPlugins, context)
    }
    
    /**
     * 为路径注册插件链
     * 
     * @param pathPattern 路径模式
     * @param chain 插件链
     * @return 注册结果的Future
     */
    override fun registerPluginChainForPath(pathPattern: String, chain: PluginChain): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 优化插件链
            val optimizedChain = pluginOptimizer.optimizePluginChain(chain)
            
            // 注册插件链
            pluginChains[optimizedChain.id] = optimizedChain
            
            // 注册到路径映射
            pathPluginChains[pathPattern] = optimizedChain
            
            logger.info("Plugin chain registered for path {}: {}", pathPattern, optimizedChain.id)
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to register plugin chain for path {}: {}", pathPattern, chain.id, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 为路由器注册所有插件链
     * 
     * @param router 路由器
     * @return 注册结果的Future
     */
    override fun registerAllPluginChainsToRouter(router: Router): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 为每个路径模式注册处理器
            pathPluginChains.forEach { (pathPattern, chain) ->
                router.route(pathPattern).handler { context ->
                    // 获取插件链中的插件
                    val chainPlugins = chain.pluginIds.mapNotNull { plugins[it] }
                    
                    // 使用插件优化器执行插件链
                    pluginOptimizer.optimizeChainExecution(chainPlugins, context)
                        .onFailure { cause ->
                            logger.error("Failed to execute plugin chain for path {}: {}", pathPattern, chain.id, cause)
                            context.fail(cause)
                        }
                }
                
                logger.info("Registered handler for path pattern: {}", pathPattern)
            }
            
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to register all plugin chains to router", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取插件
     * 
     * @param pluginId 插件ID
     * @return 插件实例，如果不存在则返回null
     */
    override fun getPlugin(pluginId: String): Plugin? {
        return plugins[pluginId]
    }
    
    /**
     * 获取插件链
     * 
     * @param chainId 插件链ID
     * @return 插件链实例，如果不存在则返回null
     */
    override fun getPluginChain(chainId: String): PluginChain? {
        return pluginChains[chainId]
    }
    
    /**
     * 获取所有插件
     * 
     * @return 所有插件的列表
     */
    override fun getAllPlugins(): List<Plugin> {
        return plugins.values.toList()
    }
    
    /**
     * 获取所有插件链
     * 
     * @return 所有插件链的列表
     */
    override fun getAllPluginChains(): List<PluginChain> {
        return pluginChains.values.toList()
    }
    
    /**
     * 获取插件统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getPluginStats(): JsonObject {
        val stats = JsonObject()
        
        // 添加基本统计信息
        stats.put("totalPlugins", plugins.size)
        stats.put("totalPluginChains", pluginChains.size)
        stats.put("totalPathMappings", pathPluginChains.size)
        
        // 添加插件优化器的统计信息
        stats.put("optimizerStats", pluginOptimizer.getPluginStats())
        
        return stats
    }
    
    /**
     * 重置插件统计信息
     */
    fun resetStats() {
        pluginOptimizer.resetStats()
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: OptimizedPluginManager? = null
        
        /**
         * 获取OptimizedPluginManager的单例实例
         * 
         * @param vertx Vertx实例
         * @return OptimizedPluginManager实例
         */
        fun getInstance(vertx: Vertx): OptimizedPluginManager {
            if (INSTANCE == null) {
                synchronized(OptimizedPluginManager::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = OptimizedPluginManager(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
