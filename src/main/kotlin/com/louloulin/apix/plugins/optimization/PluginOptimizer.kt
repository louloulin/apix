package com.louloulin.apix.plugins.optimization

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginChain
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 插件优化器，用于提高插件执行效率和性能。
 * 实现了插件缓存、并行执行和条件执行等优化策略。
 */
class PluginOptimizer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginOptimizer::class.java)
    
    // 插件执行计数器
    private val executionCounters = ConcurrentHashMap<String, AtomicInteger>()
    
    // 插件执行时间统计（毫秒）
    private val executionTimes = ConcurrentHashMap<String, AtomicLong>()
    
    // 插件缓存
    private val pluginCache = ConcurrentHashMap<String, Any>()
    
    // 插件依赖关系图
    private val dependencyGraph = ConcurrentHashMap<String, Set<String>>()
    
    // 插件类型分组
    private val pluginTypeGroups = mapOf(
        "security" to 1,      // 安全类插件优先级最高
        "auth" to 1,          // 认证类插件优先级最高
        "validation" to 2,    // 验证类插件优先级次之
        "transform" to 3,     // 转换类插件优先级再次
        "logging" to 4,       // 日志类插件可以并行执行
        "monitoring" to 4,    // 监控类插件可以并行执行
        "cache" to 5          // 缓存类插件优先级最低
    )
    
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
        val pluginsByPriority = plugins.groupBy { getPluginPriority(it.type) }
        
        // 创建执行Promise
        val promise = Promise.promise<Void>()
        
        // 按优先级顺序执行插件组
        executePluginGroups(pluginsByPriority, 1, context, promise)
        
        return promise.future()
    }
    
    /**
     * 按优先级顺序执行插件组
     */
    private fun executePluginGroups(
        pluginsByPriority: Map<Int, List<Plugin>>, 
        currentPriority: Int,
        context: RoutingContext,
        promise: Promise<Void>
    ) {
        // 如果响应已结束或已达到最大优先级，完成执行
        if (context.response().ended() || currentPriority > 5) {
            promise.complete()
            return
        }
        
        // 获取当前优先级的插件
        val currentPlugins = pluginsByPriority[currentPriority] ?: emptyList()
        
        if (currentPlugins.isEmpty()) {
            // 如果当前优先级没有插件，继续下一个优先级
            executePluginGroups(pluginsByPriority, currentPriority + 1, context, promise)
            return
        }
        
        // 监控和日志插件可以并行执行
        if (currentPriority == 4) {
            executePluginsInParallel(currentPlugins, context).onComplete { result ->
                if (result.failed()) {
                    promise.fail(result.cause())
                    return@onComplete
                }
                
                // 继续下一个优先级
                executePluginGroups(pluginsByPriority, currentPriority + 1, context, promise)
            }
        } else {
            // 其他插件按顺序执行
            executePluginsSequentially(currentPlugins, context).onComplete { result ->
                if (result.failed()) {
                    promise.fail(result.cause())
                    return@onComplete
                }
                
                // 如果响应已结束，完成执行
                if (context.response().ended()) {
                    promise.complete()
                    return@onComplete
                }
                
                // 继续下一个优先级
                executePluginGroups(pluginsByPriority, currentPriority + 1, context, promise)
            }
        }
    }
    
    /**
     * 获取插件优先级
     */
    private fun getPluginPriority(type: String): Int {
        // 从类型前缀中获取优先级
        val prefix = type.split("-").first()
        return pluginTypeGroups[prefix] ?: 3 // 默认优先级为3
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
            executionCounters.computeIfAbsent(plugin.id) { AtomicInteger(0) }.incrementAndGet()
            
            // 执行插件
            plugin.execute(context).onComplete { result ->
                // 记录执行时间
                val executionTime = System.currentTimeMillis() - startTime
                executionTimes.computeIfAbsent(plugin.id) { AtomicLong(0) }.addAndGet(executionTime)
                
                if (result.succeeded()) {
                    // 继续执行下一个插件
                    executeNextPlugin(plugins, index + 1, context, promise)
                } else {
                    logger.error("Plugin execution failed: {}", plugin.id, result.cause())
                    promise.fail(result.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("Exception during plugin execution: {}", plugin.id, e)
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
            executionCounters.computeIfAbsent(plugin.id) { AtomicInteger(0) }.incrementAndGet()
            
            // 执行插件
            plugin.execute(context).onComplete { result ->
                // 记录执行时间
                val executionTime = System.currentTimeMillis() - startTime
                executionTimes.computeIfAbsent(plugin.id) { AtomicLong(0) }.addAndGet(executionTime)
                
                if (result.failed()) {
                    logger.error("Plugin execution failed: {}", plugin.id, result.cause())
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
        executionCounters.computeIfAbsent(plugin.id) { AtomicInteger(0) }.incrementAndGet()
        
        // 执行插件
        return plugin.execute(context).onComplete { result ->
            // 记录执行时间
            val executionTime = System.currentTimeMillis() - startTime
            executionTimes.computeIfAbsent(plugin.id) { AtomicLong(0) }.addAndGet(executionTime)
            
            if (result.failed()) {
                logger.error("Plugin execution failed: {}", plugin.id, result.cause())
            }
        }
    }
    
    /**
     * 优化插件链
     * 
     * @param chain 插件链
     * @return 优化后的插件链
     */
    fun optimizePluginChain(chain: PluginChain): PluginChain {
        // 这里可以实现更复杂的插件链优化逻辑
        // 例如，根据历史执行时间重新排序插件，或者合并相似功能的插件
        return chain
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
    fun addDependency(pluginId: String, dependsOn: String) {
        val dependencies = dependencyGraph.computeIfAbsent(pluginId) { HashSet() }.toMutableSet()
        dependencies.add(dependsOn)
        dependencyGraph[pluginId] = dependencies
    }
    
    /**
     * 获取插件依赖
     */
    fun getDependencies(pluginId: String): Set<String> {
        return dependencyGraph[pluginId] ?: emptySet()
    }
    
    /**
     * 获取插件执行统计信息
     */
    fun getPluginStats(): JsonObject {
        val stats = JsonObject()
        
        // 添加执行计数
        val counters = JsonObject()
        executionCounters.forEach { (id, counter) ->
            counters.put(id, counter.get())
        }
        stats.put("executionCounts", counters)
        
        // 添加平均执行时间
        val avgTimes = JsonObject()
        executionCounters.forEach { (id, counter) ->
            val totalTime = executionTimes[id]?.get() ?: 0
            val count = counter.get()
            val avgTime = if (count > 0) totalTime.toDouble() / count else 0.0
            avgTimes.put(id, avgTime)
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
