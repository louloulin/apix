package com.louloulin.apix.plugins.dependency

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginRegistry
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件依赖管理器
 * 用于管理插件之间的依赖关系
 */
class PluginDependencyManager(private val vertx: Vertx, private val pluginRegistry: PluginRegistry) {
    private val logger = LoggerFactory.getLogger(PluginDependencyManager::class.java)
    
    // 插件依赖关系图
    private val dependencyGraph = ConcurrentHashMap<String, Set<String>>()
    
    /**
     * 添加插件依赖关系
     *
     * @param pluginId 插件ID
     * @param dependencies 依赖的插件ID列表
     * @return 是否添加成功
     */
    fun addDependencies(pluginId: String, dependencies: Set<String>): Boolean {
        // 检查是否存在循环依赖
        if (wouldCreateCycle(pluginId, dependencies)) {
            logger.error("添加依赖关系失败：检测到循环依赖 - 插件 {} 依赖 {}", pluginId, dependencies)
            return false
        }
        
        // 添加依赖关系
        dependencyGraph[pluginId] = dependencies
        logger.debug("添加依赖关系：插件 {} 依赖 {}", pluginId, dependencies)
        return true
    }
    
    /**
     * 移除插件依赖关系
     *
     * @param pluginId 插件ID
     */
    fun removeDependencies(pluginId: String) {
        dependencyGraph.remove(pluginId)
        logger.debug("移除依赖关系：插件 {}", pluginId)
    }
    
    /**
     * 获取插件的依赖
     *
     * @param pluginId 插件ID
     * @return 依赖的插件ID列表
     */
    fun getDependencies(pluginId: String): Set<String> {
        return dependencyGraph[pluginId] ?: emptySet()
    }
    
    /**
     * 获取依赖于指定插件的插件列表
     *
     * @param pluginId 插件ID
     * @return 依赖于该插件的插件ID列表
     */
    fun getDependents(pluginId: String): Set<String> {
        return dependencyGraph.entries
            .filter { it.value.contains(pluginId) }
            .map { it.key }
            .toSet()
    }
    
    /**
     * 检查是否存在循环依赖
     *
     * @param pluginId 插件ID
     * @param dependencies 依赖的插件ID列表
     * @return 是否存在循环依赖
     */
    private fun wouldCreateCycle(pluginId: String, dependencies: Set<String>): Boolean {
        // 如果依赖列表中包含插件自身，则存在循环依赖
        if (dependencies.contains(pluginId)) {
            return true
        }
        
        // 使用深度优先搜索检查是否存在循环依赖
        val visited = mutableSetOf<String>()
        val stack = Stack<String>()
        
        for (dependency in dependencies) {
            if (hasCycle(dependency, pluginId, visited, stack)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 深度优先搜索检查是否存在循环依赖
     *
     * @param current 当前插件ID
     * @param target 目标插件ID
     * @param visited 已访问的插件ID集合
     * @param stack 当前搜索路径
     * @return 是否存在循环依赖
     */
    private fun hasCycle(current: String, target: String, visited: MutableSet<String>, stack: Stack<String>): Boolean {
        // 如果当前插件已经在栈中，则存在循环依赖
        if (stack.contains(current)) {
            return true
        }
        
        // 如果当前插件已经被访问过，则不存在循环依赖
        if (visited.contains(current)) {
            return false
        }
        
        // 如果当前插件依赖目标插件，则存在循环依赖
        if (current == target) {
            return true
        }
        
        // 标记当前插件为已访问
        visited.add(current)
        stack.push(current)
        
        // 递归检查当前插件的依赖
        val dependencies = dependencyGraph[current] ?: emptySet()
        for (dependency in dependencies) {
            if (hasCycle(dependency, target, visited, stack)) {
                return true
            }
        }
        
        // 回溯
        stack.pop()
        
        return false
    }
    
    /**
     * 获取插件的拓扑排序
     * 用于确定插件的加载和执行顺序
     *
     * @return 拓扑排序后的插件ID列表
     */
    fun getTopologicalOrder(): List<String> {
        val result = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val temp = mutableSetOf<String>()
        
        // 对所有插件进行拓扑排序
        for (pluginId in dependencyGraph.keys) {
            if (!visited.contains(pluginId)) {
                topologicalSort(pluginId, visited, temp, result)
            }
        }
        
        // 反转结果，使依赖项在前
        return result.reversed()
    }
    
    /**
     * 拓扑排序算法
     *
     * @param pluginId 插件ID
     * @param visited 已访问的插件ID集合
     * @param temp 临时标记的插件ID集合
     * @param result 排序结果
     */
    private fun topologicalSort(
        pluginId: String,
        visited: MutableSet<String>,
        temp: MutableSet<String>,
        result: MutableList<String>
    ) {
        // 如果插件已经在临时集合中，则存在循环依赖
        if (temp.contains(pluginId)) {
            logger.error("拓扑排序失败：检测到循环依赖 - 插件 {}", pluginId)
            return
        }
        
        // 如果插件已经被访问过，则跳过
        if (visited.contains(pluginId)) {
            return
        }
        
        // 标记插件为临时访问
        temp.add(pluginId)
        
        // 递归访问依赖项
        val dependencies = dependencyGraph[pluginId] ?: emptySet()
        for (dependency in dependencies) {
            topologicalSort(dependency, visited, temp, result)
        }
        
        // 标记插件为已访问
        temp.remove(pluginId)
        visited.add(pluginId)
        
        // 添加到结果中
        result.add(pluginId)
    }
    
    /**
     * 验证插件依赖关系
     * 检查所有依赖的插件是否存在
     *
     * @return 验证结果，包含缺失的依赖
     */
    fun validateDependencies(): Map<String, Set<String>> {
        val missingDependencies = mutableMapOf<String, Set<String>>()
        
        for ((pluginId, dependencies) in dependencyGraph) {
            val missing = dependencies.filter { !pluginRegistry.hasPlugin(it) }.toSet()
            if (missing.isNotEmpty()) {
                missingDependencies[pluginId] = missing
            }
        }
        
        return missingDependencies
    }
    
    /**
     * 加载插件及其依赖
     *
     * @param pluginId 插件ID
     * @return 加载结果
     */
    fun loadPluginWithDependencies(pluginId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 检查插件是否存在
        if (!pluginRegistry.hasPlugin(pluginId)) {
            promise.fail("插件不存在：$pluginId")
            return promise.future()
        }
        
        // 获取插件的依赖
        val dependencies = getDependencies(pluginId)
        
        // 检查依赖是否存在
        val missingDependencies = dependencies.filter { !pluginRegistry.hasPlugin(it) }.toSet()
        if (missingDependencies.isNotEmpty()) {
            promise.fail("插件 $pluginId 的依赖不存在：$missingDependencies")
            return promise.future()
        }
        
        // 按拓扑排序加载依赖
        val order = getTopologicalOrder()
        val toLoad = order.filter { it == pluginId || dependencies.contains(it) }
        
        // 并行加载插件
        val futures = toLoad.map { id ->
            val plugin = pluginRegistry.getPlugin(id)
            if (plugin != null) {
                plugin.initialize(vertx)
            } else {
                Future.failedFuture<Void>("插件不存在：$id")
            }
        }
        
        // 等待所有插件加载完成
        Future.all(futures).onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("插件 {} 及其依赖加载成功", pluginId)
                promise.complete()
            } else {
                logger.error("插件 {} 及其依赖加载失败", pluginId, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 卸载插件及其依赖项
     *
     * @param pluginId 插件ID
     * @param unloadDependents 是否卸载依赖于该插件的插件
     * @return 卸载结果
     */
    fun unloadPluginWithDependencies(pluginId: String, unloadDependents: Boolean = true): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 检查插件是否存在
        if (!pluginRegistry.hasPlugin(pluginId)) {
            promise.fail("插件不存在：$pluginId")
            return promise.future()
        }
        
        // 获取依赖于该插件的插件
        val dependents = if (unloadDependents) getDependents(pluginId) else emptySet()
        
        // 按拓扑排序卸载插件
        val order = getTopologicalOrder()
        val toUnload = order.filter { it == pluginId || dependents.contains(it) }
        
        // 并行卸载插件
        val futures = toUnload.map { id ->
            pluginRegistry.unloadPlugin(id)
        }
        
        // 等待所有插件卸载完成
        Future.all(futures).onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("插件 {} 及其依赖项卸载成功", pluginId)
                promise.complete()
            } else {
                logger.error("插件 {} 及其依赖项卸载失败", pluginId, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PluginDependencyManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(vertx: Vertx, pluginRegistry: PluginRegistry): PluginDependencyManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PluginDependencyManager(vertx, pluginRegistry).also { INSTANCE = it }
            }
        }
    }
}
