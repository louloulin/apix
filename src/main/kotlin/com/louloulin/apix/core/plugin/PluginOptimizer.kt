package com.louloulin.apix.core.plugin

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 插件优化器，用于提高插件执行效率和性能。
 * 实现了插件缓存、并行执行和条件执行等优化策略。
 */
class PluginOptimizer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginOptimizer::class.java)
    
    // 插件执行计数器
    private val executionCounters = ConcurrentHashMap<String, AtomicInteger>()
    
    // 插件执行时间统计（毫秒）
    private val executionTimes = ConcurrentHashMap<String, Long>()
    
    // 插件缓存
    private val pluginCache = ConcurrentHashMap<String, Any>()
    
    // 插件依赖关系图
    private val dependencyGraph = ConcurrentHashMap<String, Set<String>>()
    
    /**
     * 初始化插件优化器
     */
    init {
        logger.info("Plugin optimizer initialized")
        
        // 定期清理插件缓存
        vertx.setPeriodic(3600000) { // 每小时清理一次
            cleanupCache()
        }
    }
    
    /**
     * 优化插件链执行
     * 
     * @param plugins 要执行的插件列表
     * @param context 路由上下文
     * @return 执行结果的Future
     */
    fun optimizeChainExecution(plugins: List<Plugin>, context: RoutingContext): Future<Void> {
        if (plugins.isEmpty()) {
            return Future.succeededFuture()
        }
        
        // 按插件类型分组
        val pluginsByType = plugins.groupBy { it.type }
        
        // 创建执行Promise
        val promise = Promise.promise<Void>()
        
        // 首先执行安全插件（串行执行）
        val securityPlugins = pluginsByType[PluginType.SECURITY] ?: emptyList()
        executePluginsSequentially(securityPlugins, context).onComplete { securityResult ->
            if (securityResult.failed() || context.response().ended()) {
                promise.complete(securityResult.result())
                return@onComplete
            }
            
            // 然后并行执行监控和日志插件
            val monitoringPlugins = pluginsByType[PluginType.MONITORING] ?: emptyList()
            val loggingPlugins = pluginsByType[PluginType.LOGGING] ?: emptyList()
            val parallelPlugins = monitoringPlugins + loggingPlugins
            
            if (parallelPlugins.isNotEmpty()) {
                executePluginsInParallel(parallelPlugins, context).onComplete { parallelResult ->
                    if (parallelResult.failed() || context.response().ended()) {
                        promise.complete(parallelResult.result())
                        return@onComplete
                    }
                    
                    // 最后执行其他类型的插件（串行执行）
                    val remainingPlugins = plugins.filter { 
                        it.type != PluginType.SECURITY && 
                        it.type != PluginType.MONITORING && 
                        it.type != PluginType.LOGGING 
                    }
                    
                    executePluginsSequentially(remainingPlugins, context).onComplete { result ->
                        promise.complete(result.result())
                    }
                }
            } else {
                // 没有并行插件，直接执行其他插件
                val remainingPlugins = plugins.filter { 
                    it.type != PluginType.SECURITY
                }
                
                executePluginsSequentially(remainingPlugins, context).onComplete { result ->
                    promise.complete(result.result())
                }
            }
        }
        
        return promise.future()
    }
    
    /**
     * 串行执行插件
     */
    private fun executePluginsSequentially(plugins: List<Plugin>, context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        if (plugins.isEmpty()) {
            promise.complete()
            return promise.future()
        }
        
        executeNextPlugin(plugins, 0, context, promise)
        
        return promise.future()
    }
    
    /**
     * 递归执行下一个插件
     */
    private fun executeNextPlugin(plugins: List<Plugin>, index: Int, context: RoutingContext, promise: Promise<Void>) {
        if (index >= plugins.size || context.response().ended()) {
            promise.complete()
            return
        }
        
        val plugin = plugins[index]
        val startTime = System.currentTimeMillis()
        
        try {
            // 增加执行计数
            executionCounters.computeIfAbsent(plugin.name) { AtomicInteger(0) }.incrementAndGet()
            
            // 执行插件
            plugin.handleRequest(context).onComplete { result ->
                // 记录执行时间
                val executionTime = System.currentTimeMillis() - startTime
                executionTimes.merge(plugin.name, executionTime) { old, new -> old + new }
                
                if (result.succeeded()) {
                    // 继续执行下一个插件
                    executeNextPlugin(plugins, index + 1, context, promise)
                } else {
                    logger.error("Plugin execution failed: {}", plugin.name, result.cause())
                    promise.fail(result.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during plugin execution: {}", plugin.name, e)
            promise.fail(e)
        }
    }
    
    /**
     * 并行执行插件
     */
    private fun executePluginsInParallel(plugins: List<Plugin>, context: RoutingContext): Future<Void> {
        val futures = plugins.map { plugin ->
            val startTime = System.currentTimeMillis()
            
            // 增加执行计数
            executionCounters.computeIfAbsent(plugin.name) { AtomicInteger(0) }.incrementAndGet()
            
            // 执行插件
            plugin.handleRequest(context).onComplete { result ->
                // 记录执行时间
                val executionTime = System.currentTimeMillis() - startTime
                executionTimes.merge(plugin.name, executionTime) { old, new -> old + new }
                
                if (result.failed()) {
                    logger.error("Plugin execution failed: {}", plugin.name, result.cause())
                }
            }
        }
        
        // 等待所有插件执行完成
        return Future.all(futures).map { null }
    }
    
    /**
     * 条件执行插件
     * 
     * @param plugin 要执行的插件
     * @param context 路由上下文
     * @param condition 执行条件
     * @return 执行结果的Future
     */
    fun executePluginConditionally(plugin: Plugin, context: RoutingContext, condition: (RoutingContext) -> Boolean): Future<Void> {
        if (!condition(context)) {
            return Future.succeededFuture()
        }
        
        val startTime = System.currentTimeMillis()
        
        // 增加执行计数
        executionCounters.computeIfAbsent(plugin.name) { AtomicInteger(0) }.incrementAndGet()
        
        // 执行插件
        return plugin.handleRequest(context).onComplete { result ->
            // 记录执行时间
            val executionTime = System.currentTimeMillis() - startTime
            executionTimes.merge(plugin.name, executionTime) { old, new -> old + new }
            
            if (result.failed()) {
                logger.error("Plugin execution failed: {}", plugin.name, result.cause())
            }
        }
    }
    
    /**
     * 获取插件缓存
     */
    fun <T> getFromCache(key: String, type: Class<T>): T? {
        val value = pluginCache[key]
        return if (value != null && type.isInstance(value)) {
            type.cast(value)
        } else {
            null
        }
    }
    
    /**
     * 添加到插件缓存
     */
    fun putInCache(key: String, value: Any) {
        pluginCache[key] = value
    }
    
    /**
     * 清理插件缓存
     */
    private fun cleanupCache() {
        logger.info("Cleaning up plugin cache, current size: {}", pluginCache.size)
        pluginCache.clear()
    }
    
    /**
     * 添加插件依赖关系
     */
    fun addDependency(pluginName: String, dependsOn: String) {
        val dependencies = dependencyGraph.computeIfAbsent(pluginName) { HashSet() }.toMutableSet()
        dependencies.add(dependsOn)
        dependencyGraph[pluginName] = dependencies
    }
    
    /**
     * 获取插件依赖
     */
    fun getDependencies(pluginName: String): Set<String> {
        return dependencyGraph[pluginName] ?: emptySet()
    }
    
    /**
     * 获取插件执行统计信息
     */
    fun getPluginStats(): JsonObject {
        val stats = JsonObject()
        
        // 添加执行计数
        val counters = JsonObject()
        executionCounters.forEach { (name, counter) ->
            counters.put(name, counter.get())
        }
        stats.put("executionCounts", counters)
        
        // 添加平均执行时间
        val avgTimes = JsonObject()
        executionCounters.forEach { (name, counter) ->
            val totalTime = executionTimes[name] ?: 0
            val count = counter.get()
            val avgTime = if (count > 0) totalTime.toDouble() / count else 0.0
            avgTimes.put(name, avgTime)
        }
        stats.put("averageExecutionTimes", avgTimes)
        
        // 添加缓存信息
        stats.put("cacheSize", pluginCache.size)
        
        return stats
    }
    
    /**
     * 重置插件统计信息
     */
    fun resetStats() {
        executionCounters.clear()
        executionTimes.clear()
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: PluginOptimizer? = null
        
        /**
         * 获取PluginOptimizer的单例实例
         * 
         * @param vertx Vertx实例
         * @return PluginOptimizer实例
         */
        fun getInstance(vertx: Vertx): PluginOptimizer {
            if (INSTANCE == null) {
                synchronized(PluginOptimizer::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = PluginOptimizer(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
