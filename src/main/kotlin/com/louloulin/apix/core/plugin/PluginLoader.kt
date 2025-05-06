package com.louloulin.apix.core.plugin

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile

/**
 * 插件加载器，用于高效加载和管理插件。
 * 支持热加载、版本管理和依赖解析。
 */
class PluginLoader(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginLoader::class.java)
    
    // 插件工厂映射
    private val pluginFactories = ConcurrentHashMap<String, PluginFactory>()
    
    // 已加载的插件
    private val loadedPlugins = ConcurrentHashMap<String, Plugin>()
    
    // 插件版本映射
    private val pluginVersions = ConcurrentHashMap<String, String>()
    
    // 插件JAR文件映射
    private val pluginJars = ConcurrentHashMap<String, File>()
    
    // 插件类加载器映射
    private val pluginClassLoaders = ConcurrentHashMap<String, URLClassLoader>()
    
    /**
     * 初始化插件加载器
     */
    init {
        logger.info("Plugin loader initialized")
    }
    
    /**
     * 注册内置插件工厂
     */
    fun registerBuiltInFactories() {
        logger.info("Registering built-in plugin factories")
        
        // 使用ServiceLoader加载所有PluginFactory实现
        val serviceLoader = ServiceLoader.load(PluginFactory::class.java)
        
        serviceLoader.forEach { factory ->
            val type = factory.javaClass.simpleName.removeSuffix("Factory").lowercase()
            pluginFactories[type] = factory
            logger.info("Registered built-in plugin factory: {}", type)
        }
    }
    
    /**
     * 从目录加载插件
     * 
     * @param directory 插件目录
     * @return 加载结果的Future
     */
    fun loadPluginsFromDirectory(directory: String): Future<List<Plugin>> {
        val promise = Promise.promise<List<Plugin>>()
        
        vertx.executeBlocking<List<Plugin>>({ blockingPromise ->
            try {
                val dir = File(directory)
                if (!dir.exists() || !dir.isDirectory) {
                    logger.warn("Plugin directory does not exist or is not a directory: {}", directory)
                    blockingPromise.complete(emptyList())
                    return@executeBlocking
                }
                
                val jarFiles = dir.listFiles { file -> file.name.endsWith(".jar") }
                if (jarFiles == null || jarFiles.isEmpty()) {
                    logger.info("No plugin JAR files found in directory: {}", directory)
                    blockingPromise.complete(emptyList())
                    return@executeBlocking
                }
                
                val loadedPluginsList = mutableListOf<Plugin>()
                
                for (jarFile in jarFiles) {
                    try {
                        val loadedPlugins = loadPluginFromJar(jarFile)
                        loadedPluginsList.addAll(loadedPlugins)
                    } catch (e: Exception) {
                        logger.error("Failed to load plugins from JAR: {}", jarFile.name, e)
                    }
                }
                
                blockingPromise.complete(loadedPluginsList)
            } catch (e: Exception) {
                logger.error("Error loading plugins from directory: {}", directory, e)
                blockingPromise.fail(e)
            }
        }).onComplete { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 从JAR文件加载插件
     * 
     * @param jarFile JAR文件
     * @return 加载的插件列表
     */
    private fun loadPluginFromJar(jarFile: File): List<Plugin> {
        logger.info("Loading plugins from JAR: {}", jarFile.name)
        
        val loadedPlugins = mutableListOf<Plugin>()
        
        try {
            // 读取JAR文件的插件描述符
            val jar = JarFile(jarFile)
            val entry = jar.getEntry("plugin.json")
            
            if (entry != null) {
                val inputStream = jar.getInputStream(entry)
                val descriptorJson = inputStream.bufferedReader().use { it.readText() }
                val descriptor = JsonObject(descriptorJson)
                
                // 创建类加载器
                val urls = arrayOf(jarFile.toURI().toURL())
                val classLoader = URLClassLoader(urls, this.javaClass.classLoader)
                
                // 加载插件工厂
                val serviceLoader = ServiceLoader.load(PluginFactory::class.java, classLoader)
                
                serviceLoader.forEach { factory ->
                    val type = factory.javaClass.simpleName.removeSuffix("Factory").lowercase()
                    pluginFactories[type] = factory
                    logger.info("Registered plugin factory from JAR: {}", type)
                }
                
                // 创建插件实例
                val pluginsArray = descriptor.getJsonArray("plugins", JsonArray())
                
                for (i in 0 until pluginsArray.size()) {
                    val pluginConfig = pluginsArray.getJsonObject(i)
                    val pluginType = pluginConfig.getString("type")
                    val pluginName = pluginConfig.getString("name")
                    val pluginVersion = pluginConfig.getString("version", "1.0.0")
                    
                    if (pluginType != null && pluginName != null) {
                        val factory = pluginFactories[pluginType]
                        
                        if (factory != null) {
                            try {
                                // 创建插件上下文
                                val context = PluginContext(vertx, pluginConfig)
                                
                                // 创建插件实例
                                val plugin = factory.createPlugin(context)
                                
                                // 初始化插件
                                plugin.init(context, pluginConfig).onComplete { ar ->
                                    if (ar.succeeded()) {
                                        // 存储插件信息
                                        loadedPlugins[plugin.name] = plugin
                                        pluginVersions[plugin.name] = pluginVersion
                                        pluginJars[plugin.name] = jarFile
                                        pluginClassLoaders[plugin.name] = classLoader
                                        
                                        loadedPlugins.add(plugin)
                                        
                                        logger.info("Loaded plugin: {} ({})", plugin.name, pluginVersion)
                                    } else {
                                        logger.error("Failed to initialize plugin: {}", plugin.name, ar.cause())
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Failed to create plugin: {}", pluginName, e)
                            }
                        } else {
                            logger.warn("Unknown plugin type: {}", pluginType)
                        }
                    } else {
                        logger.warn("Invalid plugin configuration: {}", pluginConfig.encode())
                    }
                }
            } else {
                logger.warn("No plugin.json found in JAR: {}", jarFile.name)
            }
            
            jar.close()
        } catch (e: Exception) {
            logger.error("Error loading plugins from JAR: {}", jarFile.name, e)
        }
        
        return loadedPlugins
    }
    
    /**
     * 从配置加载插件
     * 
     * @param config 插件配置
     * @return 加载结果的Future
     */
    fun loadPluginsFromConfig(config: JsonObject): Future<List<Plugin>> {
        val promise = Promise.promise<List<Plugin>>()
        
        try {
            val pluginsArray = config.getJsonArray("plugins", JsonArray())
            val loadedPluginsList = mutableListOf<Plugin>()
            
            for (i in 0 until pluginsArray.size()) {
                val pluginConfig = pluginsArray.getJsonObject(i)
                val pluginType = pluginConfig.getString("type")
                val pluginName = pluginConfig.getString("name")
                val pluginVersion = pluginConfig.getString("version", "1.0.0")
                val enabled = pluginConfig.getBoolean("enabled", true)
                
                if (!enabled) {
                    logger.info("Skipping disabled plugin: {}", pluginName)
                    continue
                }
                
                if (pluginType != null && pluginName != null) {
                    val factory = pluginFactories[pluginType]
                    
                    if (factory != null) {
                        try {
                            // 创建插件上下文
                            val context = PluginContext(vertx, pluginConfig)
                            
                            // 创建插件实例
                            val plugin = factory.createPlugin(context)
                            
                            // 初始化插件
                            plugin.init(context, pluginConfig).onComplete { ar ->
                                if (ar.succeeded()) {
                                    // 存储插件信息
                                    loadedPlugins[plugin.name] = plugin
                                    pluginVersions[plugin.name] = pluginVersion
                                    
                                    loadedPluginsList.add(plugin)
                                    
                                    logger.info("Loaded plugin from config: {} ({})", plugin.name, pluginVersion)
                                } else {
                                    logger.error("Failed to initialize plugin from config: {}", plugin.name, ar.cause())
                                }
                            }
                        } catch (e: Exception) {
                            logger.error("Failed to create plugin from config: {}", pluginName, e)
                        }
                    } else {
                        logger.warn("Unknown plugin type in config: {}", pluginType)
                    }
                } else {
                    logger.warn("Invalid plugin configuration: {}", pluginConfig.encode())
                }
            }
            
            promise.complete(loadedPluginsList)
        } catch (e: Exception) {
            logger.error("Error loading plugins from config", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取已加载的插件
     * 
     * @param name 插件名称
     * @return 插件实例，如果不存在则返回null
     */
    fun getPlugin(name: String): Plugin? {
        return loadedPlugins[name]
    }
    
    /**
     * 获取所有已加载的插件
     * 
     * @return 插件列表
     */
    fun getAllPlugins(): List<Plugin> {
        return loadedPlugins.values.toList()
    }
    
    /**
     * 获取插件版本
     * 
     * @param name 插件名称
     * @return 插件版本，如果不存在则返回null
     */
    fun getPluginVersion(name: String): String? {
        return pluginVersions[name]
    }
    
    /**
     * 卸载插件
     * 
     * @param name 插件名称
     * @return 卸载结果的Future
     */
    fun unloadPlugin(name: String): Future<Void> {
        val plugin = loadedPlugins[name]
        
        if (plugin == null) {
            logger.warn("Plugin not found: {}", name)
            return Future.succeededFuture()
        }
        
        return plugin.close().onComplete { ar ->
            if (ar.succeeded()) {
                // 移除插件信息
                loadedPlugins.remove(name)
                pluginVersions.remove(name)
                
                // 关闭类加载器
                val classLoader = pluginClassLoaders.remove(name)
                classLoader?.close()
                
                // 移除JAR文件引用
                pluginJars.remove(name)
                
                logger.info("Unloaded plugin: {}", name)
            } else {
                logger.error("Failed to close plugin: {}", name, ar.cause())
            }
        }
    }
    
    /**
     * 重新加载插件
     * 
     * @param name 插件名称
     * @return 重新加载结果的Future
     */
    fun reloadPlugin(name: String): Future<Plugin> {
        val promise = Promise.promise<Plugin>()
        
        // 获取插件JAR文件
        val jarFile = pluginJars[name]
        
        if (jarFile == null) {
            logger.warn("Plugin JAR file not found: {}", name)
            promise.fail("Plugin JAR file not found: $name")
            return promise.future()
        }
        
        // 卸载插件
        unloadPlugin(name).onComplete { unloadResult ->
            if (unloadResult.failed()) {
                logger.warn("Failed to unload plugin: {}", name, unloadResult.cause())
                // 继续尝试重新加载
            }
            
            try {
                // 重新加载插件
                val loadedPlugins = loadPluginFromJar(jarFile)
                
                // 查找重新加载的插件
                val reloadedPlugin = loadedPlugins.find { it.name == name }
                
                if (reloadedPlugin != null) {
                    promise.complete(reloadedPlugin)
                } else {
                    promise.fail("Plugin not found after reload: $name")
                }
            } catch (e: Exception) {
                logger.error("Failed to reload plugin: {}", name, e)
                promise.fail(e)
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取插件加载器统计信息
     * 
     * @return 统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("loadedPlugins", loadedPlugins.size)
            .put("pluginFactories", pluginFactories.size)
            .put("plugins", JsonArray(loadedPlugins.keys.toList()))
            .put("versions", JsonObject().apply {
                pluginVersions.forEach { (name, version) ->
                    put(name, version)
                }
            })
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: PluginLoader? = null
        
        /**
         * 获取PluginLoader的单例实例
         * 
         * @param vertx Vertx实例
         * @return PluginLoader实例
         */
        fun getInstance(vertx: Vertx): PluginLoader {
            if (INSTANCE == null) {
                synchronized(PluginLoader::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = PluginLoader(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
