package com.louloulin.apix.plugins.deploy

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginRegistry
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.file.FileSystem
import io.vertx.core.file.FileSystemException
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 插件热部署管理器
 * 用于监控插件目录，自动加载/更新插件
 */
class PluginHotDeployer(
    private val vertx: Vertx,
    private val pluginRegistry: PluginRegistry,
    private val pluginDir: String = "plugins"
) {
    private val logger = LoggerFactory.getLogger(PluginHotDeployer::class.java)

    // 文件监视器ID
    private var watcherId: Long = -1

    // 是否正在运行
    private val running = AtomicBoolean(false)

    // 已加载的插件文件及其最后修改时间
    private val loadedPluginFiles = ConcurrentHashMap<String, Long>()

    /**
     * 启动插件热部署
     */
    fun start(): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!running.compareAndSet(false, true)) {
            // 如果已经运行，直接返回
            return Future.succeededFuture()
        }

        try {
            // 确保插件目录存在
            val fs = vertx.fileSystem()
            ensurePluginDirectory(fs).compose { _ ->
                // 初始加载所有插件
                loadAllPlugins()
            }.compose { _ ->
                // 设置文件监视器
                setupFileWatcher(fs)
            }.onComplete { ar ->
                if (ar.succeeded()) {
                    logger.info("Plugin hot deployer started")
                    promise.complete()
                } else {
                    running.set(false) // 启动失败，重置状态
                    logger.error("Failed to start plugin hot deployer", ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            running.set(false) // 启动失败，重置状态
            logger.error("Error starting plugin hot deployer", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 停止插件热部署
     */
    fun stop(): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!running.compareAndSet(true, false)) {
            // 如果没有运行，直接返回
            return Future.succeededFuture()
        }

        try {
            // 取消文件监视器
            if (watcherId != -1L) {
                try {
                    // 关闭定时器
                    vertx.cancelTimer(watcherId)
                    promise.complete()
                } catch (e: Exception) {
                    logger.warn("Failed to close file watcher", e)
                    promise.complete() // 即使关闭失败也继续
                }
            } else {
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error stopping plugin hot deployer", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 确保插件目录存在
     */
    private fun ensurePluginDirectory(fs: FileSystem): Future<Void> {
        val promise = Promise.promise<Void>()

        fs.exists(pluginDir) { ar ->
            if (ar.succeeded()) {
                if (ar.result()) {
                    // 目录已存在
                    promise.complete()
                } else {
                    // 创建目录
                    fs.mkdir(pluginDir) { mkdirAr ->
                        if (mkdirAr.succeeded()) {
                            logger.info("Created plugin directory: {}", pluginDir)
                            promise.complete()
                        } else {
                            logger.error("Failed to create plugin directory: {}", pluginDir, mkdirAr.cause())
                            promise.fail(mkdirAr.cause())
                        }
                    }
                }
            } else {
                logger.error("Failed to check plugin directory: {}", pluginDir, ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 加载所有插件
     */
    private fun loadAllPlugins(): Future<Void> {
        val promise = Promise.promise<Void>()

        vertx.fileSystem().readDir(pluginDir) { ar ->
            if (ar.succeeded()) {
                val files = ar.result()
                val jsonFiles = files.filter { it.endsWith(".json") }

                if (jsonFiles.isEmpty()) {
                    logger.info("No plugin configuration files found in {}", pluginDir)
                    promise.complete()
                    return@readDir
                }

                // 并行加载所有插件
                val futures = jsonFiles.map { file -> loadPluginFromFile(file) }

                Future.all(futures).onComplete { loadAr ->
                    if (loadAr.succeeded()) {
                        logger.info("Loaded {} plugins", jsonFiles.size)
                        promise.complete()
                    } else {
                        logger.error("Failed to load plugins", loadAr.cause())
                        promise.fail(loadAr.cause())
                    }
                }
            } else {
                logger.error("Failed to read plugin directory: {}", pluginDir, ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 从文件加载插件
     */
    private fun loadPluginFromFile(filePath: String): Future<Plugin> {
        val promise = Promise.promise<Plugin>()

        try {
            val file = File(filePath)

            // 记录文件最后修改时间
            loadedPluginFiles[filePath] = file.lastModified()

            // 读取文件内容
            vertx.fileSystem().readFile(filePath) { ar ->
                if (ar.succeeded()) {
                    try {
                        val config = JsonObject(ar.result())
                        val pluginId = config.getString("id")
                        val pluginType = config.getString("type")

                        if (pluginId == null || pluginType == null) {
                            promise.fail("Invalid plugin configuration: missing id or type")
                            return@readFile
                        }

                        // 检查插件是否已存在
                        if (pluginRegistry.hasPlugin(pluginId)) {
                            // 卸载现有插件
                            pluginRegistry.unloadPlugin(pluginId).compose { _ ->
                                // 加载新插件
                                pluginRegistry.loadPlugin(PluginConfig(pluginId, pluginType, config))
                            }.onComplete { loadAr ->
                                if (loadAr.succeeded()) {
                                    logger.info("Reloaded plugin: {}", pluginId)
                                    promise.complete(loadAr.result())
                                } else {
                                    logger.error("Failed to reload plugin: {}", pluginId, loadAr.cause())
                                    promise.fail(loadAr.cause())
                                }
                            }
                        } else {
                            // 加载新插件
                            pluginRegistry.loadPlugin(PluginConfig(pluginId, pluginType, config))
                                .onComplete { loadAr ->
                                    if (loadAr.succeeded()) {
                                        logger.info("Loaded plugin: {}", pluginId)
                                        promise.complete(loadAr.result())
                                    } else {
                                        logger.error("Failed to load plugin: {}", pluginId, loadAr.cause())
                                        promise.fail(loadAr.cause())
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        logger.error("Error parsing plugin configuration: {}", filePath, e)
                        promise.fail(e)
                    }
                } else {
                    logger.error("Failed to read plugin file: {}", filePath, ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("Error loading plugin from file: {}", filePath, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 设置文件监视器
     */
    private fun setupFileWatcher(fs: FileSystem): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 使用异步方式设置文件监视器
            vertx.executeBlocking<Long>({ p ->
                try {
                    // 这里模拟文件监视器的行为
                    // 实际实现中应该使用 Vert.x 的 FileSystem.watch 方法
                    // 由于当前版本可能不支持该方法，我们这里使用定时器模拟

                    // 创建定时器，定期扫描插件目录
                    val timerId = vertx.setPeriodic(5000) { _ ->
                        if (running.get()) {
                            try {
                                // 扫描插件目录
                                val files = File(pluginDir).listFiles()
                                if (files != null) {
                                    for (file in files) {
                                        if (file.isFile && file.name.endsWith(".json")) {
                                            val absolutePath = file.absolutePath
                                            val lastModified = file.lastModified()
                                            val previousModified = loadedPluginFiles[absolutePath]

                                            if (previousModified == null || previousModified < lastModified) {
                                                logger.info("Plugin file changed: {}", absolutePath)
                                                loadPluginFromFile(absolutePath).onComplete { loadAr ->
                                                    if (loadAr.succeeded()) {
                                                        logger.info("Hot-reloaded plugin from file: {}", absolutePath)
                                                    } else {
                                                        logger.error("Failed to hot-reload plugin from file: {}", absolutePath, loadAr.cause())
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.error("Error scanning plugin directory", e)
                            }
                        }
                    }

                    p.complete(timerId)
                } catch (e: Exception) {
                    p.fail(e)
                }
            }).onComplete { ar ->
                if (ar.succeeded()) {
                    watcherId = ar.result()
                    logger.info("File watcher set up for directory: {}", pluginDir)
                    promise.complete()
                } else {
                    logger.error("Failed to set up file watcher for directory: {}", pluginDir, ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("Error setting up file watcher", e)
            promise.fail(e)
        }

        return promise.future()
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PluginHotDeployer? = null

        /**
         * 获取 PluginHotDeployer 的单例实例
         */
        fun getInstance(vertx: Vertx, pluginRegistry: PluginRegistry, pluginDir: String = "plugins"): PluginHotDeployer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PluginHotDeployer(vertx, pluginRegistry, pluginDir).also { INSTANCE = it }
            }
        }
    }
}
