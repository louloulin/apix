package com.louloulin.apix.plugins.market

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystem
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件市场管理器
 *
 * 该类负责管理插件市场，包括插件的上传、下载、安装和卸载。
 */
class PluginMarketManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PluginMarketManager::class.java)
    
    // 插件目录
    private val pluginsDir: String
    
    // 插件仓库
    private val pluginRepository = ConcurrentHashMap<String, PluginInfo>()
    
    // 插件工厂
    private val pluginFactories = ConcurrentHashMap<String, PluginFactory>()
    
    // 已安装的插件
    private val installedPlugins = ConcurrentHashMap<String, Plugin>()
    
    init {
        // 获取插件目录
        pluginsDir = System.getProperty("apix.plugins.dir") ?: "plugins"
        
        // 创建插件目录
        val pluginsDirFile = File(pluginsDir)
        if (!pluginsDirFile.exists()) {
            pluginsDirFile.mkdirs()
        }
        
        logger.info("PluginMarketManager initialized with plugins directory: {}", pluginsDir)
    }
    
    /**
     * 初始化插件市场
     */
    fun initialize(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 加载插件仓库
        loadPluginRepository()
            .onSuccess {
                logger.info("Plugin repository loaded with {} plugins", pluginRepository.size)
                promise.complete()
            }
            .onFailure { err ->
                logger.error("Failed to load plugin repository", err)
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 加载插件仓库
     */
    private fun loadPluginRepository(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 读取插件仓库文件
        val repositoryFile = Paths.get(pluginsDir, "repository.json").toString()
        val fs = vertx.fileSystem()
        
        fs.exists(repositoryFile)
            .compose { exists ->
                if (exists) {
                    fs.readFile(repositoryFile)
                } else {
                    // 创建空的仓库文件
                    val emptyRepository = JsonObject().put("plugins", JsonArray())
                    fs.writeFile(repositoryFile, Buffer.buffer(emptyRepository.encode()))
                        .map { Buffer.buffer(emptyRepository.encode()) }
                }
            }
            .compose { buffer ->
                try {
                    val repository = JsonObject(buffer)
                    val plugins = repository.getJsonArray("plugins", JsonArray())
                    
                    // 加载插件信息
                    for (i in 0 until plugins.size()) {
                        val pluginJson = plugins.getJsonObject(i)
                        val pluginInfo = PluginInfo.fromJson(pluginJson)
                        pluginRepository[pluginInfo.id] = pluginInfo
                    }
                    
                    Future.succeededFuture<Void>()
                } catch (e: Exception) {
                    Future.failedFuture<Void>(e)
                }
            }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    promise.complete()
                } else {
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 保存插件仓库
     */
    private fun savePluginRepository(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 创建仓库JSON
            val plugins = JsonArray()
            pluginRepository.values.forEach { pluginInfo ->
                plugins.add(pluginInfo.toJson())
            }
            
            val repository = JsonObject().put("plugins", plugins)
            
            // 写入仓库文件
            val repositoryFile = Paths.get(pluginsDir, "repository.json").toString()
            vertx.fileSystem().writeFile(repositoryFile, Buffer.buffer(repository.encode()))
                .onSuccess {
                    promise.complete()
                }
                .onFailure { err ->
                    promise.fail(err)
                }
        } catch (e: Exception) {
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 注册插件工厂
     */
    fun registerPluginFactory(type: String, factory: PluginFactory) {
        pluginFactories[type] = factory
        logger.info("Registered plugin factory for type: {}", type)
    }
    
    /**
     * 上传插件
     */
    fun uploadPlugin(pluginFile: Buffer, metadata: JsonObject): Future<PluginInfo> {
        val promise = Promise.promise<PluginInfo>()
        
        try {
            // 验证元数据
            val id = metadata.getString("id") ?: UUID.randomUUID().toString()
            val name = metadata.getString("name")
            val version = metadata.getString("version")
            val type = metadata.getString("type")
            val description = metadata.getString("description", "")
            val author = metadata.getString("author", "")
            val license = metadata.getString("license", "")
            
            if (name == null || version == null || type == null) {
                promise.fail("Invalid plugin metadata: name, version and type are required")
                return promise.future()
            }
            
            // 创建插件信息
            val pluginInfo = PluginInfo(
                id = id,
                name = name,
                version = version,
                type = type,
                description = description,
                author = author,
                license = license,
                uploadTime = System.currentTimeMillis(),
                downloadCount = 0,
                rating = 0.0,
                ratingCount = 0,
                tags = metadata.getJsonArray("tags", JsonArray()).map { it.toString() },
                dependencies = metadata.getJsonArray("dependencies", JsonArray()).map { it.toString() },
                filename = "$id-$version.zip"
            )
            
            // 保存插件文件
            val pluginFilePath = Paths.get(pluginsDir, pluginInfo.filename).toString()
            vertx.fileSystem().writeFile(pluginFilePath, pluginFile)
                .compose {
                    // 添加到仓库
                    pluginRepository[pluginInfo.id] = pluginInfo
                    
                    // 保存仓库
                    savePluginRepository()
                }
                .onSuccess {
                    logger.info("Plugin uploaded: {}", pluginInfo.name)
                    promise.complete(pluginInfo)
                }
                .onFailure { err ->
                    logger.error("Failed to upload plugin", err)
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("Error uploading plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 下载插件
     */
    fun downloadPlugin(pluginId: String): Future<Buffer> {
        val promise = Promise.promise<Buffer>()
        
        try {
            // 获取插件信息
            val pluginInfo = pluginRepository[pluginId]
            if (pluginInfo == null) {
                promise.fail("Plugin not found: $pluginId")
                return promise.future()
            }
            
            // 读取插件文件
            val pluginFilePath = Paths.get(pluginsDir, pluginInfo.filename).toString()
            vertx.fileSystem().readFile(pluginFilePath)
                .onSuccess { buffer ->
                    // 更新下载计数
                    pluginInfo.downloadCount++
                    
                    // 保存仓库
                    savePluginRepository()
                        .onSuccess {
                            logger.info("Plugin downloaded: {}", pluginInfo.name)
                            promise.complete(buffer)
                        }
                        .onFailure { err ->
                            logger.error("Failed to save plugin repository", err)
                            promise.complete(buffer)
                        }
                }
                .onFailure { err ->
                    logger.error("Failed to read plugin file", err)
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("Error downloading plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 安装插件
     */
    fun installPlugin(pluginId: String): Future<Plugin> {
        val promise = Promise.promise<Plugin>()
        
        try {
            // 获取插件信息
            val pluginInfo = pluginRepository[pluginId]
            if (pluginInfo == null) {
                promise.fail("Plugin not found: $pluginId")
                return promise.future()
            }
            
            // 检查插件工厂
            val factory = pluginFactories[pluginInfo.type]
            if (factory == null) {
                promise.fail("Plugin factory not found for type: ${pluginInfo.type}")
                return promise.future()
            }
            
            // 创建插件配置
            val pluginConfig = PluginConfig(
                id = pluginInfo.id,
                type = pluginInfo.type,
                config = JsonObject()
                    .put("name", pluginInfo.name)
                    .put("version", pluginInfo.version)
                    .put("description", pluginInfo.description)
                    .put("author", pluginInfo.author)
                    .put("license", pluginInfo.license)
            )
            
            // 创建插件实例
            val plugin = factory.create(pluginConfig)
            
            // 初始化插件
            plugin.initialize(vertx)
                .onSuccess {
                    // 添加到已安装插件
                    installedPlugins[pluginInfo.id] = plugin
                    
                    logger.info("Plugin installed: {}", pluginInfo.name)
                    promise.complete(plugin)
                }
                .onFailure { err ->
                    logger.error("Failed to initialize plugin", err)
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("Error installing plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 卸载插件
     */
    fun uninstallPlugin(pluginId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 获取插件
            val plugin = installedPlugins[pluginId]
            if (plugin == null) {
                promise.fail("Plugin not installed: $pluginId")
                return promise.future()
            }
            
            // 关闭插件
            val shutdownFuture = plugin.shutdown(true)
            if (shutdownFuture != null) {
                shutdownFuture
                    .onSuccess {
                        // 从已安装插件中移除
                        installedPlugins.remove(pluginId)
                        
                        logger.info("Plugin uninstalled: {}", plugin.id)
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.error("Failed to shutdown plugin", err)
                        promise.fail(err)
                    }
            } else {
                // 插件没有返回Future，直接移除
                installedPlugins.remove(pluginId)
                
                logger.info("Plugin uninstalled: {}", plugin.id)
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error uninstalling plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取插件信息
     */
    fun getPluginInfo(pluginId: String): PluginInfo? {
        return pluginRepository[pluginId]
    }
    
    /**
     * 获取所有插件信息
     */
    fun getAllPluginInfo(): List<PluginInfo> {
        return pluginRepository.values.toList()
    }
    
    /**
     * 获取已安装的插件
     */
    fun getInstalledPlugin(pluginId: String): Plugin? {
        return installedPlugins[pluginId]
    }
    
    /**
     * 获取所有已安装的插件
     */
    fun getAllInstalledPlugins(): List<Plugin> {
        return installedPlugins.values.toList()
    }
    
    /**
     * 搜索插件
     */
    fun searchPlugins(query: String, tags: List<String> = emptyList()): List<PluginInfo> {
        val queryLower = query.lowercase()
        
        return pluginRepository.values.filter { pluginInfo ->
            // 如果查询为空，则返回所有插件
            if (query.isBlank() && tags.isEmpty()) {
                return@filter true
            }
            
            // 检查标签
            if (tags.isNotEmpty() && !pluginInfo.tags.any { it in tags }) {
                return@filter false
            }
            
            // 检查查询
            if (query.isNotBlank()) {
                return@filter pluginInfo.name.lowercase().contains(queryLower) ||
                        pluginInfo.description.lowercase().contains(queryLower) ||
                        pluginInfo.author.lowercase().contains(queryLower) ||
                        pluginInfo.type.lowercase().contains(queryLower)
            }
            
            true
        }
    }
    
    /**
     * 评价插件
     */
    fun ratePlugin(pluginId: String, rating: Double): Future<PluginInfo> {
        val promise = Promise.promise<PluginInfo>()
        
        try {
            // 获取插件信息
            val pluginInfo = pluginRepository[pluginId]
            if (pluginInfo == null) {
                promise.fail("Plugin not found: $pluginId")
                return promise.future()
            }
            
            // 更新评分
            val newRatingCount = pluginInfo.ratingCount + 1
            val newRating = (pluginInfo.rating * pluginInfo.ratingCount + rating) / newRatingCount
            
            pluginInfo.ratingCount = newRatingCount
            pluginInfo.rating = newRating
            
            // 保存仓库
            savePluginRepository()
                .onSuccess {
                    logger.info("Plugin rated: {} - {}", pluginInfo.name, rating)
                    promise.complete(pluginInfo)
                }
                .onFailure { err ->
                    logger.error("Failed to save plugin repository", err)
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("Error rating plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 删除插件
     */
    fun deletePlugin(pluginId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 获取插件信息
            val pluginInfo = pluginRepository[pluginId]
            if (pluginInfo == null) {
                promise.fail("Plugin not found: $pluginId")
                return promise.future()
            }
            
            // 检查插件是否已安装
            if (installedPlugins.containsKey(pluginId)) {
                promise.fail("Plugin is installed, uninstall it first")
                return promise.future()
            }
            
            // 删除插件文件
            val pluginFilePath = Paths.get(pluginsDir, pluginInfo.filename).toString()
            vertx.fileSystem().delete(pluginFilePath)
                .compose {
                    // 从仓库中移除
                    pluginRepository.remove(pluginId)
                    
                    // 保存仓库
                    savePluginRepository()
                }
                .onSuccess {
                    logger.info("Plugin deleted: {}", pluginInfo.name)
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("Failed to delete plugin", err)
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("Error deleting plugin", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 关闭插件市场管理器
     */
    fun close(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 关闭所有已安装的插件
            val futures = mutableListOf<Future<Void>>()
            
            installedPlugins.values.forEach { plugin ->
                val shutdownFuture = plugin.shutdown(true)
                if (shutdownFuture != null) {
                    futures.add(shutdownFuture)
                }
            }
            
            // 等待所有插件关闭
            Future.all(futures)
                .onSuccess {
                    logger.info("All plugins uninstalled")
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("Failed to uninstall all plugins", err)
                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("Error closing plugin market manager", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
}

/**
 * 插件信息
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val type: String,
    val description: String,
    val author: String,
    val license: String,
    val uploadTime: Long,
    var downloadCount: Int,
    var rating: Double,
    var ratingCount: Int,
    val tags: List<String>,
    val dependencies: List<String>,
    val filename: String
) {
    /**
     * 转换为JSON对象
     */
    fun toJson(): JsonObject {
        val tagsArray = JsonArray()
        tags.forEach { tagsArray.add(it) }
        
        val dependenciesArray = JsonArray()
        dependencies.forEach { dependenciesArray.add(it) }
        
        return JsonObject()
            .put("id", id)
            .put("name", name)
            .put("version", version)
            .put("type", type)
            .put("description", description)
            .put("author", author)
            .put("license", license)
            .put("uploadTime", uploadTime)
            .put("downloadCount", downloadCount)
            .put("rating", rating)
            .put("ratingCount", ratingCount)
            .put("tags", tagsArray)
            .put("dependencies", dependenciesArray)
            .put("filename", filename)
    }
    
    companion object {
        /**
         * 从JSON对象创建插件信息
         */
        fun fromJson(json: JsonObject): PluginInfo {
            val tagsArray = json.getJsonArray("tags", JsonArray())
            val tags = (0 until tagsArray.size()).map { tagsArray.getString(it) }
            
            val dependenciesArray = json.getJsonArray("dependencies", JsonArray())
            val dependencies = (0 until dependenciesArray.size()).map { dependenciesArray.getString(it) }
            
            return PluginInfo(
                id = json.getString("id"),
                name = json.getString("name"),
                version = json.getString("version"),
                type = json.getString("type"),
                description = json.getString("description", ""),
                author = json.getString("author", ""),
                license = json.getString("license", ""),
                uploadTime = json.getLong("uploadTime", 0),
                downloadCount = json.getInteger("downloadCount", 0),
                rating = json.getDouble("rating", 0.0),
                ratingCount = json.getInteger("ratingCount", 0),
                tags = tags,
                dependencies = dependencies,
                filename = json.getString("filename")
            )
        }
    }
}
