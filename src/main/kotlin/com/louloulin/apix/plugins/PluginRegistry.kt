package com.louloulin.apix.plugins

import com.louloulin.apix.core.PluginChain
import com.louloulin.apix.plugins.version.PluginVersion
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件注册表
 * 负责插件的注册、加载和管理
 */
class PluginRegistry(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginRegistry::class.java)
    private val plugins = ConcurrentHashMap<String, Plugin>()
    private val factories = ConcurrentHashMap<String, PluginFactory>()

    // 插件版本管理
    private val pluginVersions = ConcurrentHashMap<String, MutableList<PluginVersion>>()

    // 插件指标收集
    private val metrics = PluginMetrics.getInstance(vertx)

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
            val pluginsConfig = config.getJsonArray("plugins", JsonArray())

            // 使用 Vert.x 的异步能力并行加载插件
            val futures = mutableListOf<Future<*>>()

            pluginsConfig.forEach { configObj ->
                val pluginConfig = configObj as JsonObject
                val pluginType = pluginConfig.getString("type")
                val pluginId = pluginConfig.getString("id")

                if (pluginType != null && pluginId != null) {
                    // 解析版本
                    val versionStr = pluginConfig.getString("version", "1.0.0")
                    val version = PluginVersion.fromString(versionStr)

                    futures.add(loadPlugin(PluginConfig(pluginId, pluginType, pluginConfig, version)))
                }
            }

            // 等待所有插件加载完成
            CompositeFuture.all(futures).onComplete { ar ->
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
     * 动态加载插件
     */
    fun loadPlugin(pluginConfig: PluginConfig): Future<Plugin> {
        val promise = Promise.promise<Plugin>()
        val factory = factories[pluginConfig.type]

        if (factory != null) {
            vertx.executeBlocking<Plugin>({ p ->
                try {
                    val plugin = factory.create(pluginConfig)
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
                    val version = pluginConfig.version
                    pluginVersions.computeIfAbsent(plugin.id) { mutableListOf() }.add(version)

                    plugins[plugin.id] = plugin
                    logger.info("Loaded plugin: {} version {}", plugin.id, version)

                    // 发布插件加载事件
                    vertx.eventBus().publish(
                        "plugins.loaded",
                        JsonObject()
                            .put("id", plugin.id)
                            .put("type", plugin.type)
                            .put("version", pluginConfig.version.toString())
                            .put("timestamp", System.currentTimeMillis())
                    )

                    plugin
                }
            }.onComplete { ar ->
                if (ar.succeeded()) {
                    promise.complete(ar.result())
                } else {
                    logger.error("Failed to load plugin: {}", pluginConfig.id, ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } else {
            val msg = "Unknown plugin type: ${pluginConfig.type}"
            logger.warn(msg)
            promise.fail(msg)
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

            // 调用 shutdown 方法，并指定返回 Future
            val shutdownFuture = plugin.shutdown(true)

            val result = Promise.promise<Void>()

            if (shutdownFuture != null) {
                shutdownFuture.onComplete { ar ->
                    if (ar.succeeded()) {
                        // 发布插件卸载事件
                        vertx.eventBus().publish(
                            "plugins.unloaded",
                            JsonObject()
                                .put("id", plugin.id)
                                .put("type", plugin.type)
                                .put("timestamp", System.currentTimeMillis())
                        )
                        result.complete()
                    } else {
                        logger.error("Error shutting down plugin: {}", pluginId, ar.cause())
                        result.fail(ar.cause())
                    }
                }
            } else {
                // 如果插件不返回 Future，直接发布卸载事件
                vertx.eventBus().publish(
                    "plugins.unloaded",
                    JsonObject()
                        .put("id", plugin.id)
                        .put("type", plugin.type)
                        .put("timestamp", System.currentTimeMillis())
                )
                result.complete()
            }

            return result.future()
        } else {
            logger.warn("Plugin not found for unloading: {}", pluginId)
            return Future.succeededFuture()
        }
    }

    /**
     * 创建插件链
     */
    fun createPluginChain(): PluginChain {
        return PluginChain(vertx, plugins.values.toList())
    }

    /**
     * 创建特定插件的插件链
     */
    fun createPluginChain(pluginIds: List<String>): PluginChain {
        val chainPlugins = pluginIds.mapNotNull { plugins[it] }
        return PluginChain(vertx, chainPlugins)
    }

    /**
     * 获取插件指标
     */
    fun getMetrics(): JsonObject {
        return metrics.getAllMetrics()
    }

    /**
     * 获取特定插件的指标
     */
    fun getPluginMetrics(pluginId: String): JsonObject {
        return metrics.getMetrics(pluginId)
    }

    /**
     * 重置指标
     */
    fun resetMetrics() {
        metrics.resetMetrics()
    }

    /**
     * 获取所有插件
     */
    fun getAllPlugins(): List<Plugin> {
        return plugins.values.toList()
    }

    /**
     * 获取特定插件
     */
    fun getPlugin(pluginId: String): Plugin? {
        return plugins[pluginId]
    }

    /**
     * 检查插件是否存在
     */
    fun hasPlugin(pluginId: String): Boolean {
        return plugins.containsKey(pluginId)
    }

    /**
     * 获取插件数量
     */
    fun getPluginCount(): Int {
        return plugins.size
    }

    /**
     * 获取插件版本
     *
     * @param pluginId 插件ID
     * @return 插件版本列表
     */
    fun getPluginVersions(pluginId: String): List<PluginVersion> {
        return pluginVersions[pluginId] ?: emptyList()
    }

    /**
     * 获取插件的最新版本
     *
     * @param pluginId 插件ID
     * @return 最新版本或null
     */
    fun getLatestPluginVersion(pluginId: String): PluginVersion? {
        return pluginVersions[pluginId]?.maxOrNull()
    }

    /**
     * 检查插件是否兼容指定版本
     *
     * @param pluginId 插件ID
     * @param requiredVersion 所需版本
     * @return 是否兼容
     */
    fun isPluginCompatibleWith(pluginId: String, requiredVersion: PluginVersion): Boolean {
        val plugin = getPlugin(pluginId) ?: return false
        return (plugin.config as PluginConfig).isCompatibleWith(requiredVersion)
    }

    /**
     * 执行所有插件的健康检查
     */
    fun healthCheck(): Future<JsonObject> {
        val result = JsonObject()
        val futures = mutableListOf<Future<*>>()

        plugins.forEach { (id, plugin) ->
            val future = plugin.healthCheck().onSuccess { health ->
                result.put(id, health)
            }.onFailure { err ->
                result.put(id, JsonObject()
                    .put("status", "DOWN")
                    .put("error", err.message))
            }

            futures.add(future)
        }

        return CompositeFuture.all(futures).map {
            result.put("status", if (result.map.values.any {
                (it as JsonObject).getString("status") == "DOWN"
            }) "DOWN" else "UP")
            result
        }
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PluginRegistry? = null

        /**
         * 获取 PluginRegistry 的单例实例
         */
        fun getInstance(vertx: Vertx): PluginRegistry {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PluginRegistry(vertx).also { INSTANCE = it }
            }
        }
    }
}
