package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import com.louloulin.apix.plugins.PluginRegistry
import com.louloulin.apix.plugins.PluginState
import com.louloulin.apix.plugins.ai.ResponseCachePluginFactory
import com.louloulin.apix.plugins.deploy.PluginHotDeployer
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

/**
 * 负责插件管理的 Verticle
 */
class PluginVerticle : BaseVerticle() {
    // 插件存储
    private val plugins = ConcurrentHashMap<String, Plugin>()

    // 插件工厂存储
    private val pluginFactories = ConcurrentHashMap<String, PluginFactory>()

    // 插件状态存储
    private val pluginStates = ConcurrentHashMap<String, PluginState>()

    // 插件依赖关系存储
    private val pluginDependencies = ConcurrentHashMap<String, Set<String>>()

    // 配置管理器
    private lateinit var configManager: ConfigManager

    // 插件目录
    private lateinit var pluginsDir: File

    // 插件注册表
    private lateinit var pluginRegistry: PluginRegistry

    // 插件热部署管理器
    private lateinit var pluginHotDeployer: PluginHotDeployer

    override fun registerEventBusHandlers() {
        // 插件管理相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_GET_ALL, this::handleGetAllPlugins)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, this::handleGetPluginById)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_CREATE, this::handleCreatePlugin)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_UPDATE, this::handleUpdatePlugin)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_DELETE, this::handleDeletePlugin)

        // 插件生命周期相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_ENABLE, this::handleEnablePlugin)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_DISABLE, this::handleDisablePlugin)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_RELOAD, this::handleReloadPlugin)

        // 插件热加载相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_LOAD_JAR, this::handleLoadPluginJar)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_SCAN_DIR, this::handleScanPluginDir)
    }

    override fun onStart(startPromise: Promise<Void>) {
        configManager = ConfigManager(vertx)

        // 初始化插件目录
        val pluginsDirPath = configManager.getConfig().getString("plugins.dir", "plugins")
        pluginsDir = File(pluginsDirPath)
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        // 初始化插件注册表
        pluginRegistry = PluginRegistry.getInstance(vertx)

        // 初始化插件热部署管理器
        pluginHotDeployer = PluginHotDeployer.getInstance(vertx, pluginRegistry, pluginsDirPath)

        // 注册内置插件工厂
        registerBuiltInPluginFactories()

        // 加载配置中的插件
        loadPluginsFromConfig()
            .compose { _ ->
                // 启动插件热部署
                pluginHotDeployer.start()
            }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    logger.info("PluginVerticle started successfully")
                    startPromise.complete()
                } else {
                    logger.error("Failed to start PluginVerticle", ar.cause())
                    startPromise.fail(ar.cause())
                }
            }
    }

    /**
     * 注册内置插件工厂
     */
    private fun registerBuiltInPluginFactories() {
        logger.info("Registering built-in plugin factories...")

        // 使用 ServiceLoader 加载所有实现了 PluginFactory 接口的类
        val serviceLoader = ServiceLoader.load(PluginFactory::class.java)

        serviceLoader.forEach { factory ->
            val type = factory.javaClass.simpleName.removeSuffix("Factory").lowercase()
            pluginFactories[type] = factory
            logger.info("Registered built-in plugin factory: {}", type)
        }

        // 手动注册一些内置插件工厂
        pluginFactories["response-cache"] = ResponseCachePluginFactory()
        // 这里可以添加更多内置插件工厂

        logger.info("Registered {} built-in plugin factories", pluginFactories.size)
    }

    /**
     * 从配置加载插件
     */
    private fun loadPluginsFromConfig() = vertx.executeBlocking<Void> { promise ->
        try {
            val pluginsConfig = configManager.getConfig().getJsonArray("plugins", JsonArray())

            pluginsConfig.forEach { item ->
                val pluginConfig = item as JsonObject
                val pluginId = pluginConfig.getString("id")
                val pluginType = pluginConfig.getString("type")
                val enabled = pluginConfig.getBoolean("enabled", true)
                val dependencies = pluginConfig.getJsonArray("dependencies", JsonArray()).map { it.toString() }.toSet()

                if (pluginId != null && pluginType != null) {
                    // 存储插件依赖关系
                    pluginDependencies[pluginId] = dependencies

                    // 创建插件
                    createPlugin(pluginId, pluginType, pluginConfig, enabled)
                } else {
                    logger.warn("Invalid plugin configuration: {}", pluginConfig.encode())
                }
            }

            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to load plugins from config", e)
            promise.fail(e)
        }
    }

    /**
     * 创建插件
     */
    private fun createPlugin(id: String, type: String, config: JsonObject, enabled: Boolean = true): Plugin? {
        val factory = pluginFactories[type]

        if (factory != null) {
            try {
                // 创建插件配置
                val pluginConfig = PluginConfig(id, type, config)

                // 创建插件实例
                val plugin = factory.create(pluginConfig)

                // 存储插件
                plugins[id] = plugin

                // 设置插件状态
                val state = if (enabled) PluginState.ENABLED else PluginState.DISABLED
                pluginStates[id] = state

                logger.info("Created plugin: {} ({})", id, type)

                return plugin
            } catch (e: Exception) {
                logger.error("Failed to create plugin: {}", id, e)
            }
        } else {
            logger.warn("Unknown plugin type: {}", type)
        }

        return null
    }

    /**
     * 处理获取所有插件请求
     */
    private fun handleGetAllPlugins(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val result = JsonArray()

        plugins.forEach { (id, plugin) ->
            result.add(JsonObject()
                .put("id", id)
                .put("type", plugin.type)
                .put("state", pluginStates[id]?.name ?: PluginState.UNKNOWN.name)
                .put("dependencies", JsonArray(pluginDependencies[id]?.toList() ?: emptyList<String>()))
            )
        }

        sendSuccess(message, result)
    }

    /**
     * 处理获取插件请求
     */
    private fun handleGetPluginById(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Plugin ID is required")
            return
        }

        val plugin = plugins[id]

        if (plugin != null) {
            val result = JsonObject()
                .put("id", id)
                .put("type", plugin.type)
                .put("state", pluginStates[id]?.name ?: PluginState.UNKNOWN.name)
                .put("dependencies", JsonArray(pluginDependencies[id]?.toList() ?: emptyList<String>()))
                .put("config", plugin.config.config)

            sendSuccess(message, result)
        } else {
            sendError(message, 404, "Plugin not found: $id")
        }
    }

    /**
     * 处理创建插件请求
     */
    private fun handleCreatePlugin(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val body = message.body()
        val id = body.getString("id")
        val type = body.getString("type")
        val config = body.getJsonObject("config", JsonObject())
        val enabled = body.getBoolean("enabled", true)
        val dependencies = body.getJsonArray("dependencies", JsonArray()).map { it.toString() }.toSet()

        if (id == null || type == null) {
            sendError(message, 400, "Plugin ID and type are required")
            return
        }

        if (plugins.containsKey(id)) {
            sendError(message, 409, "Plugin already exists: $id")
            return
        }

        // 存储插件依赖关系
        pluginDependencies[id] = dependencies

        // 创建插件
        val plugin = createPlugin(id, type, config, enabled)

        if (plugin != null) {
            // 保存到配置
            savePluginToConfig(id, type, config, enabled, dependencies)

            sendSuccess(message, JsonObject()
                .put("id", id)
                .put("type", type)
                .put("state", pluginStates[id]?.name ?: PluginState.UNKNOWN.name)
            )
        } else {
            sendError(message, 500, "Failed to create plugin: $id")
        }
    }

    /**
     * 处理更新插件请求
     */
    private fun handleUpdatePlugin(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val body = message.body()
        val id = body.getString("id")
        val config = body.getJsonObject("config")
        val enabled = body.getBoolean("enabled")
        val dependencies = body.getJsonArray("dependencies")?.map { it.toString() }?.toSet()

        if (id == null) {
            sendError(message, 400, "Plugin ID is required")
            return
        }

        val plugin = plugins[id]

        if (plugin != null) {
            // 更新插件配置
            if (config != null) {
                // 创建新的插件配置
                val newConfig = PluginConfig(id, plugin.type, config)

                // 重新创建插件
                val newPlugin = createPlugin(id, plugin.type, config, enabled ?: pluginStates[id] == PluginState.ENABLED)

                if (newPlugin == null) {
                    sendError(message, 500, "Failed to update plugin: $id")
                    return
                }
            }

            // 更新插件状态
            if (enabled != null) {
                pluginStates[id] = if (enabled) PluginState.ENABLED else PluginState.DISABLED
            }

            // 更新插件依赖关系
            if (dependencies != null) {
                pluginDependencies[id] = dependencies
            }

            // 保存到配置
            savePluginToConfig(
                id,
                plugin.type,
                config ?: plugin.config.config,
                enabled ?: pluginStates[id] == PluginState.ENABLED,
                dependencies ?: pluginDependencies[id] ?: emptySet()
            )

            sendSuccess(message, JsonObject()
                .put("id", id)
                .put("type", plugin.type)
                .put("state", pluginStates[id]?.name ?: PluginState.UNKNOWN.name)
            )
        } else {
            sendError(message, 404, "Plugin not found: $id")
        }
    }

    /**
     * 处理删除插件请求
     */
    private fun handleDeletePlugin(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Plugin ID is required")
            return
        }

        val plugin = plugins[id]

        if (plugin != null) {
            // 检查是否有其他插件依赖于此插件
            val dependentPlugins = findDependentPlugins(id)

            if (dependentPlugins.isNotEmpty()) {
                sendError(message, 409, "Cannot delete plugin: $id, it is depended on by: ${dependentPlugins.joinToString()}")
                return
            }

            // 关闭插件
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                logger.error("Error shutting down plugin: {}", id, e)
            }

            // 移除插件
            plugins.remove(id)
            pluginStates.remove(id)
            pluginDependencies.remove(id)

            // 从配置中移除
            removePluginFromConfig(id)

            sendSuccess(message, true)
        } else {
            sendError(message, 404, "Plugin not found: $id")
        }
    }

    /**
     * 处理启用插件请求
     */
    private fun handleEnablePlugin(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Plugin ID is required")
            return
        }

        val plugin = plugins[id]

        if (plugin != null) {
            // 检查插件依赖是否都已启用
            val missingDependencies = checkDependencies(id)

            if (missingDependencies.isNotEmpty()) {
                sendError(message, 409, "Cannot enable plugin: $id, missing dependencies: ${missingDependencies.joinToString()}")
                return
            }

            // 启用插件
            pluginStates[id] = PluginState.ENABLED

            // 更新配置
            updatePluginEnabledState(id, true)

            sendSuccess(message, true)
        } else {
            sendError(message, 404, "Plugin not found: $id")
        }
    }

    /**
     * 处理禁用插件请求
     */
    private fun handleDisablePlugin(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Plugin ID is required")
            return
        }

        val plugin = plugins[id]

        if (plugin != null) {
            // 检查是否有其他启用的插件依赖于此插件
            val dependentPlugins = findEnabledDependentPlugins(id)

            if (dependentPlugins.isNotEmpty()) {
                sendError(message, 409, "Cannot disable plugin: $id, it is depended on by: ${dependentPlugins.joinToString()}")
                return
            }

            // 禁用插件
            pluginStates[id] = PluginState.DISABLED

            // 更新配置
            updatePluginEnabledState(id, false)

            sendSuccess(message, true)
        } else {
            sendError(message, 404, "Plugin not found: $id")
        }
    }

    /**
     * 处理重新加载插件请求
     */
    private fun handleReloadPlugin(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val id = message.body().getString("id")

        if (id == null) {
            sendError(message, 400, "Plugin ID is required")
            return
        }

        val plugin = plugins[id]

        if (plugin != null) {
            // 获取插件信息
            val type = plugin.type
            val config = plugin.config.config
            val enabled = pluginStates[id] == PluginState.ENABLED
            val dependencies = pluginDependencies[id] ?: emptySet()

            // 关闭插件
            try {
                plugin.shutdown()
            } catch (e: Exception) {
                logger.error("Error shutting down plugin: {}", id, e)
            }

            // 移除插件
            plugins.remove(id)
            pluginStates.remove(id)

            // 重新创建插件
            val newPlugin = createPlugin(id, type, config, enabled)

            if (newPlugin != null) {
                sendSuccess(message, JsonObject()
                    .put("id", id)
                    .put("type", type)
                    .put("state", pluginStates[id]?.name ?: PluginState.UNKNOWN.name)
                )
            } else {
                sendError(message, 500, "Failed to reload plugin: $id")
            }
        } else {
            sendError(message, 404, "Plugin not found: $id")
        }
    }

    /**
     * 处理加载插件 JAR 请求
     */
    private fun handleLoadPluginJar(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val jarPath = message.body().getString("jarPath")

        if (jarPath == null) {
            sendError(message, 400, "JAR path is required")
            return
        }

        val jarFile = File(jarPath)

        if (!jarFile.exists() || !jarFile.isFile || !jarFile.name.endsWith(".jar")) {
            sendError(message, 400, "Invalid JAR file: $jarPath")
            return
        }

        try {
            // 加载 JAR 文件
            val urls = arrayOf(jarFile.toURI().toURL())
            val classLoader = URLClassLoader(urls, this.javaClass.classLoader)

            // 使用 ServiceLoader 加载插件工厂
            val serviceLoader = ServiceLoader.load(PluginFactory::class.java, classLoader)

            val loadedFactories = mutableListOf<String>()

            serviceLoader.forEach { factory ->
                val type = factory.javaClass.simpleName.removeSuffix("Factory").lowercase()
                pluginFactories[type] = factory
                loadedFactories.add(type)
                logger.info("Loaded plugin factory from JAR: {}", type)
            }

            if (loadedFactories.isEmpty()) {
                sendError(message, 404, "No plugin factories found in JAR: $jarPath")
            } else {
                sendSuccess(message, JsonObject()
                    .put("jarPath", jarPath)
                    .put("loadedFactories", JsonArray(loadedFactories))
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to load plugin JAR: {}", jarPath, e)
            sendError(message, 500, "Failed to load plugin JAR: ${e.message}")
        }
    }

    /**
     * 处理扫描插件目录请求
     */
    private fun handleScanPluginDir(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val dirPath = message.body().getString("dirPath", pluginsDir.absolutePath)

        val dir = File(dirPath)

        if (!dir.exists() || !dir.isDirectory) {
            sendError(message, 400, "Invalid directory: $dirPath")
            return
        }

        try {
            // 扫描目录中的所有 JAR 文件
            val jarFiles = dir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }

            if (jarFiles == null || jarFiles.isEmpty()) {
                sendSuccess(message, JsonObject()
                    .put("dirPath", dirPath)
                    .put("loadedJars", JsonArray())
                )
                return
            }

            val loadedJars = mutableListOf<JsonObject>()

            // 加载每个 JAR 文件
            for (jarFile in jarFiles) {
                try {
                    // 加载 JAR 文件
                    val urls = arrayOf(jarFile.toURI().toURL())
                    val classLoader = URLClassLoader(urls, this.javaClass.classLoader)

                    // 使用 ServiceLoader 加载插件工厂
                    val serviceLoader = ServiceLoader.load(PluginFactory::class.java, classLoader)

                    val loadedFactories = mutableListOf<String>()

                    serviceLoader.forEach { factory ->
                        val type = factory.javaClass.simpleName.removeSuffix("Factory").lowercase()
                        pluginFactories[type] = factory
                        loadedFactories.add(type)
                        logger.info("Loaded plugin factory from JAR: {}", type)
                    }

                    loadedJars.add(JsonObject()
                        .put("jarPath", jarFile.absolutePath)
                        .put("loadedFactories", JsonArray(loadedFactories))
                    )
                } catch (e: Exception) {
                    logger.error("Failed to load plugin JAR: {}", jarFile.absolutePath, e)
                    loadedJars.add(JsonObject()
                        .put("jarPath", jarFile.absolutePath)
                        .put("error", e.message)
                    )
                }
            }

            sendSuccess(message, JsonObject()
                .put("dirPath", dirPath)
                .put("loadedJars", JsonArray(loadedJars))
            )
        } catch (e: Exception) {
            logger.error("Failed to scan plugin directory: {}", dirPath, e)
            sendError(message, 500, "Failed to scan plugin directory: ${e.message}")
        }
    }

    /**
     * 查找依赖于指定插件的插件
     */
    private fun findDependentPlugins(pluginId: String): Set<String> {
        return pluginDependencies.filter { (_, dependencies) -> pluginId in dependencies }.keys
    }

    /**
     * 查找依赖于指定插件的已启用插件
     */
    private fun findEnabledDependentPlugins(pluginId: String): Set<String> {
        return findDependentPlugins(pluginId).filter { pluginStates[it] == PluginState.ENABLED }.toSet()
    }

    /**
     * 检查插件依赖是否都已启用
     */
    private fun checkDependencies(pluginId: String): Set<String> {
        val dependencies = pluginDependencies[pluginId] ?: return emptySet()

        return dependencies.filter { dependencyId ->
            val plugin = plugins[dependencyId]
            plugin == null || pluginStates[dependencyId] != PluginState.ENABLED
        }.toSet()
    }

    /**
     * 保存插件到配置
     */
    private fun savePluginToConfig(id: String, type: String, config: JsonObject, enabled: Boolean, dependencies: Set<String>) {
        val pluginsConfig = configManager.getConfig().getJsonArray("plugins", JsonArray())

        // 查找现有插件配置
        val existingIndex = (0 until pluginsConfig.size()).find { i ->
            val plugin = pluginsConfig.getJsonObject(i)
            plugin.getString("id") == id
        }

        // 创建新的插件配置
        val pluginConfig = JsonObject()
            .put("id", id)
            .put("type", type)
            .put("config", config)
            .put("enabled", enabled)
            .put("dependencies", JsonArray(dependencies.toList()))

        if (existingIndex != null) {
            // 更新现有插件配置
            pluginsConfig.set(existingIndex.toInt(), pluginConfig)
        } else {
            // 添加新的插件配置
            pluginsConfig.add(pluginConfig)
        }

        // 保存配置
        val appConfig = configManager.getConfig()
        appConfig.put("plugins", pluginsConfig)
        configManager.saveConfig(appConfig)
    }

    /**
     * 从配置中移除插件
     */
    private fun removePluginFromConfig(id: String) {
        val pluginsConfig = configManager.getConfig().getJsonArray("plugins", JsonArray())

        // 查找现有插件配置
        val existingIndex = (0 until pluginsConfig.size()).find { i ->
            val plugin = pluginsConfig.getJsonObject(i)
            plugin.getString("id") == id
        }

        if (existingIndex != null) {
            // 移除插件配置
            pluginsConfig.remove(existingIndex.toInt())

            // 保存配置
            val appConfig = configManager.getConfig()
            appConfig.put("plugins", pluginsConfig)
            configManager.saveConfig(appConfig)
        }
    }

    /**
     * 更新插件启用状态
     */
    private fun updatePluginEnabledState(id: String, enabled: Boolean) {
        val pluginsConfig = configManager.getConfig().getJsonArray("plugins", JsonArray())

        // 查找现有插件配置
        val existingIndex = (0 until pluginsConfig.size()).find { i ->
            val plugin = pluginsConfig.getJsonObject(i)
            plugin.getString("id") == id
        }

        if (existingIndex != null) {
            // 更新插件启用状态
            val pluginConfig = pluginsConfig.getJsonObject(existingIndex.toInt())
            pluginConfig.put("enabled", enabled)

            // 保存配置
            val appConfig = configManager.getConfig()
            appConfig.put("plugins", pluginsConfig)
            configManager.saveConfig(appConfig)
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping PluginVerticle...")

        // 停止插件热部署
        pluginHotDeployer.stop().compose<Void> { _ ->
            // 关闭所有插件
            plugins.forEach { (id, plugin) ->
                try {
                    plugin.shutdown()
                    logger.info("Shut down plugin: {}", id)
                } catch (e: Exception) {
                    logger.error("Error shutting down plugin: {}", id, e)
                }
            }

            plugins.clear()
            pluginFactories.clear()
            pluginStates.clear()
            pluginDependencies.clear()

            Future.succeededFuture<Void>()
        }.onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("PluginVerticle stopped successfully")
                stopPromise.complete()
            } else {
                logger.error("Error stopping PluginVerticle", ar.cause())
                stopPromise.fail(ar.cause())
            }
        }
    }
}
