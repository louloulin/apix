package com.louloulin.apix.plugins

import com.louloulin.apix.core.PluginChain
import com.louloulin.apix.plugins.dependency.PluginDependencyManager
import com.louloulin.apix.plugins.error.PluginErrorStats
import com.louloulin.apix.plugins.metrics.PluginMetrics
import com.louloulin.apix.plugins.version.PluginVersion
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 统一的插件管理器
 * 负责插件的注册、加载、管理和执行
 * 合并了原有的PluginManager和PluginRegistry功能
 */
class UnifiedPluginManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(UnifiedPluginManager::class.java)
    
    // 插件存储
    private val plugins = ConcurrentHashMap<String, Plugin>()
    
    // 插件工厂
    private val factories = ConcurrentHashMap<String, PluginFactory>()
    
    // 插件版本管理
    private val pluginVersions = ConcurrentHashMap<String, MutableList<PluginVersion>>()
    
    // 插件指标收集
    private val metrics = PluginMetrics.getInstance(vertx)
    
    // 插件错误统计
    private val errorStats = PluginErrorStats.getInstance(vertx)
    
    // 插件缓存
    private val pluginCache = ConcurrentHashMap<String, Any>()
    
    // 插件启用状态
    private val enabledPlugins = ConcurrentHashMap<String, Boolean>()
    
    // 插件执行计数
    private val executionCounts = ConcurrentHashMap<String, AtomicLong>()
    
    init {
        // 注册EventBus处理器
        registerEventBusHandlers()
        
        // 定期清理缓存
        vertx.setPeriodic(3600000) { // 每小时清理一次
            cleanupCache()
        }
    }
    
    /**
     * 注册EventBus处理器
     */
    private fun registerEventBusHandlers() {
        // 处理插件创建请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_CREATE) { message ->
            val request = message.body()
            createPlugin(request).onComplete { ar ->
                if (ar.succeeded()) {
                    message.reply(JsonObject().put("success", true).put("id", ar.result()))
                } else {
                    message.reply(JsonObject().put("success", false).put("error", ar.cause().message))
                }
            }
        }
        
        // 处理插件获取请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_GET) { message ->
            val request = message.body()
            val pluginId = request.getString("id")
            val plugin = getPlugin(pluginId)
            
            if (plugin != null) {
                message.reply(JsonObject().put("success", true).put("plugin", serializePlugin(plugin)))
            } else {
                message.reply(JsonObject().put("success", false).put("error", "Plugin not found: $pluginId"))
            }
        }
        
        // 处理插件更新请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_UPDATE) { message ->
            val request = message.body()
            updatePlugin(request).onComplete { ar ->
                if (ar.succeeded()) {
                    message.reply(JsonObject().put("success", true))
                } else {
                    message.reply(JsonObject().put("success", false).put("error", ar.cause().message))
                }
            }
        }
        
        // 处理插件删除请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_DELETE) { message ->
            val request = message.body()
            val pluginId = request.getString("id")
            
            unloadPlugin(pluginId).onComplete { ar ->
                if (ar.succeeded()) {
                    message.reply(JsonObject().put("success", true))
                } else {
                    message.reply(JsonObject().put("success", false).put("error", ar.cause().message))
                }
            }
        }
        
        // 处理插件列表请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_LIST) { message ->
            val plugins = listPlugins()
            message.reply(JsonObject().put("success", true).put("plugins", plugins))
        }
        
        // 处理插件启用/禁用请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_ENABLE) { message ->
            val request = message.body()
            val pluginId = request.getString("id")
            val enabled = request.getBoolean("enabled")
            
            setPluginEnabled(pluginId, enabled).onComplete { ar ->
                if (ar.succeeded()) {
                    message.reply(JsonObject().put("success", true))
                } else {
                    message.reply(JsonObject().put("success", false).put("error", ar.cause().message))
                }
            }
        }
    }
    
    /**
     * 注册插件工厂
     */
    fun registerFactory(type: String, factory: PluginFactory) {
        factories[type] = factory
        logger.info("Registered plugin factory for type: {}", type)
        
        // 发布插件工厂注册事件
        vertx.eventBus().publish(
            "plugins.factory.registered",
            JsonObject()
                .put("type", type)
                .put("timestamp", System.currentTimeMillis())
        )
    }
    
    /**
     * 从配置加载插件
     */
    fun loadFromConfig(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            val pluginsArray = config.getJsonArray("plugins")
            if (pluginsArray == null || pluginsArray.isEmpty) {
                logger.info("No plugins configured")
                promise.complete()
                return promise.future()
            }
            
            val futures = mutableListOf<Future<String>>()
            
            // 加载每个插件
            pluginsArray.forEach { pluginObj ->
                val pluginConfig = pluginObj as JsonObject
                val pluginId = pluginConfig.getString("id")
                val pluginType = pluginConfig.getString("type")
                
                if (pluginId != null && pluginType != null) {
                    val config = PluginConfig(pluginId, pluginType, pluginConfig)
                    futures.add(createPlugin(config))
                } else {
                    logger.warn("Invalid plugin configuration: {}", pluginConfig.encode())
                }
            }
            
            // 等待所有插件加载完成
            CompositeFuture.all(futures.toList()).onComplete { ar ->
                if (ar.succeeded()) {
                    logger.info("Successfully loaded {} plugins", futures.size)
                    promise.complete()
                } else {
                    logger.error("Failed to load plugins from configuration", ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading plugins from configuration", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 创建插件
     */
    fun createPlugin(config: PluginConfig): Future<String> {
        val promise = Promise.promise<String>()
        val factory = factories[config.type]
        
        if (factory != null) {
            vertx.executeBlocking<Plugin>({ p ->
                try {
                    val plugin = factory.create(config)
                    p.complete(plugin)
                } catch (e: Exception) {
                    p.fail(e)
                }
            }).compose { plugin ->
                // 初始化插件
                plugin.initialize(vertx).map {
                    // 注册 EventBus 处理器
                    plugin.registerEventBusHandlers(vertx)
                    
                    // 添加版本信息
                    val version = config.version
                    pluginVersions.computeIfAbsent(plugin.id) { mutableListOf() }.add(version)
                    
                    // 默认启用插件
                    enabledPlugins[plugin.id] = true
                    
                    // 初始化执行计数
                    executionCounts[plugin.id] = AtomicLong(0)
                    
                    plugins[plugin.id] = plugin
                    logger.info("Created plugin: {} version {}", plugin.id, version)
                    
                    // 发布插件创建事件
                    vertx.eventBus().publish(
                        "plugins.created",
                        JsonObject()
                            .put("id", plugin.id)
                            .put("type", plugin.type)
                            .put("version", config.version.toString())
                            .put("timestamp", System.currentTimeMillis())
                    )
                    
                    plugin.id
                }
            }.onComplete { ar ->
                if (ar.succeeded()) {
                    promise.complete(ar.result())
                } else {
                    logger.error("Failed to create plugin: {}", config.id, ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } else {
            val msg = "Unknown plugin type: ${config.type}"
            logger.warn(msg)
            promise.fail(msg)
        }
        
        return promise.future()
    }
    
    /**
     * 创建插件
     */
    fun createPlugin(config: JsonObject): Future<String> {
        val pluginId = config.getString("id")
        val pluginType = config.getString("type")
        
        if (pluginId == null || pluginType == null) {
            return Future.failedFuture("Invalid plugin configuration: missing id or type")
        }
        
        return createPlugin(PluginConfig(pluginId, pluginType, config))
    }
    
    /**
     * 更新插件
     */
    fun updatePlugin(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()
        val pluginId = config.getString("id")
        
        if (pluginId == null) {
            promise.fail("Invalid plugin configuration: missing id")
            return promise.future()
        }
        
        val plugin = plugins[pluginId]
        if (plugin == null) {
            promise.fail("Plugin not found: $pluginId")
            return promise.future()
        }
        
        // 更新插件配置
        val updatedConfig = plugin.config.config.copy().mergeIn(config)
        val newConfig = PluginConfig(pluginId, plugin.type, updatedConfig)
        
        // 卸载旧插件
        unloadPlugin(pluginId).compose { _ ->
            // 创建新插件
            createPlugin(newConfig).map { null as Void? }
        }.onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("Updated plugin: {}", pluginId)
                promise.complete()
            } else {
                logger.error("Failed to update plugin: {}", pluginId, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 卸载插件
     */
    fun unloadPlugin(pluginId: String): Future<Void> {
        val plugin = plugins.remove(pluginId)
        
        if (plugin != null) {
            logger.info("Unloading plugin: {}", pluginId)
            
            // 移除版本信息
            pluginVersions.remove(pluginId)
            
            // 移除启用状态
            enabledPlugins.remove(pluginId)
            
            // 移除执行计数
            executionCounts.remove(pluginId)
            
            // 调用插件的shutdown方法
            try {
                plugin.shutdown()
                
                // 发布插件卸载事件
                vertx.eventBus().publish(
                    "plugins.unloaded",
                    JsonObject()
                        .put("id", pluginId)
                        .put("timestamp", System.currentTimeMillis())
                )
                
                return Future.succeededFuture()
            } catch (e: Exception) {
                logger.error("Error shutting down plugin: {}", pluginId, e)
                return Future.failedFuture(e)
            }
        } else {
            logger.warn("Plugin not found: {}", pluginId)
            return Future.failedFuture("Plugin not found: $pluginId")
        }
    }
    
    /**
     * 获取插件
     */
    fun getPlugin(id: String): Plugin? {
        return plugins[id]
    }
    
    /**
     * 获取所有插件
     */
    fun getAllPlugins(): Collection<Plugin> {
        return plugins.values
    }
    
    /**
     * 获取启用的插件
     */
    fun getEnabledPlugins(): List<Plugin> {
        return plugins.values.filter { isPluginEnabled(it.id) }
    }
    
    /**
     * 检查插件是否存在
     */
    fun hasPlugin(id: String): Boolean {
        return plugins.containsKey(id)
    }
    
    /**
     * 检查插件是否启用
     */
    fun isPluginEnabled(id: String): Boolean {
        return enabledPlugins[id] ?: false
    }
    
    /**
     * 设置插件启用状态
     */
    fun setPluginEnabled(id: String, enabled: Boolean): Future<Void> {
        val plugin = plugins[id]
        
        if (plugin != null) {
            enabledPlugins[id] = enabled
            logger.info("Plugin {} is now {}", id, if (enabled) "enabled" else "disabled")
            
            // 发布插件状态变更事件
            vertx.eventBus().publish(
                "plugins.state.changed",
                JsonObject()
                    .put("id", id)
                    .put("enabled", enabled)
                    .put("timestamp", System.currentTimeMillis())
            )
            
            return Future.succeededFuture()
        } else {
            logger.warn("Plugin not found: {}", id)
            return Future.failedFuture("Plugin not found: $id")
        }
    }
    
    /**
     * 列出所有插件
     */
    fun listPlugins(): JsonObject {
        val result = JsonObject()
        
        plugins.forEach { (id, plugin) ->
            val pluginInfo = JsonObject()
                .put("id", id)
                .put("type", plugin.type)
                .put("enabled", isPluginEnabled(id))
                .put("versions", pluginVersions[id]?.map { it.toString() } ?: emptyList<String>())
                .put("executionCount", executionCounts[id]?.get() ?: 0)
            
            result.put(id, pluginInfo)
        }
        
        return result
    }
    
    /**
     * 创建插件链
     */
    fun createPluginChain(): PluginChain {
        // 获取所有启用的插件
        val enabledPlugins = getEnabledPlugins()
        
        // 创建插件链
        return PluginChain(vertx, enabledPlugins)
    }
    
    /**
     * 创建插件链
     */
    fun createPluginChain(pluginIds: List<String>): PluginChain {
        // 获取指定的插件，并过滤出启用的插件
        val chainPlugins = pluginIds
            .mapNotNull { plugins[it] }
            .filter { isPluginEnabled(it.id) }
        
        // 创建插件链
        return PluginChain(vertx, chainPlugins)
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
     * 序列化插件
     */
    private fun serializePlugin(plugin: Plugin): JsonObject {
        return JsonObject()
            .put("id", plugin.id)
            .put("type", plugin.type)
            .put("config", plugin.config.config)
            .put("enabled", isPluginEnabled(plugin.id))
            .put("versions", pluginVersions[plugin.id]?.map { it.toString() } ?: emptyList<String>())
            .put("executionCount", executionCounts[plugin.id]?.get() ?: 0)
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: UnifiedPluginManager? = null
        
        /**
         * 获取单例实例
         */
        fun getInstance(vertx: Vertx): UnifiedPluginManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UnifiedPluginManager(vertx).also { INSTANCE = it }
            }
        }
    }
}

/**
 * EventBus地址常量
 */
object EventBusAddresses {
    const val PLUGIN_CREATE = "plugins.create"
    const val PLUGIN_GET = "plugins.get"
    const val PLUGIN_UPDATE = "plugins.update"
    const val PLUGIN_DELETE = "plugins.delete"
    const val PLUGIN_LIST = "plugins.list"
    const val PLUGIN_ENABLE = "plugins.enable"
}
