package com.louloulin.apix.plugins.deploy

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginRegistry
import com.louloulin.apix.plugins.version.PluginVersion
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.file.FileSystem
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件部署器
 * 用于管理插件的部署和卸载，支持热加载
 */
class PluginDeployer(private val vertx: Vertx, private val registry: PluginRegistry) {
    private val logger = LoggerFactory.getLogger(PluginDeployer::class.java)

    // 插件部署目录
    private val pluginDirectory: String = System.getProperty("plugin.dir", "plugins")

    // 插件部署信息
    private val deployments = ConcurrentHashMap<String, PluginDeployment>()

    // 文件系统
    private val fs: FileSystem = vertx.fileSystem()

    /**
     * 初始化插件部署器
     */
    fun initialize(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 创建插件目录（如果不存在）
        fs.mkdirs(pluginDirectory).compose { _ ->
            // 设置文件监视器
            setupFileWatcher()

            // 加载已部署的插件
            loadDeployedPlugins()
        }.onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("Plugin deployer initialized")
                promise.complete()
            } else {
                logger.error("Failed to initialize plugin deployer", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 设置文件监视器
     * 监视插件目录的变化，自动加载新插件和卸载已删除的插件
     */
    private fun setupFileWatcher(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 定期扫描插件目录
            val scanInterval = 5000L // 5秒
            val knownFiles = mutableSetOf<String>()

            // 初始扫描
            fs.readDir(pluginDirectory) { ar ->
                if (ar.succeeded()) {
                    ar.result().forEach { path ->
                        if (path.endsWith(".json")) {
                            knownFiles.add(path)
                        }
                    }
                }
            }

            // 设置定时器
            vertx.setPeriodic(scanInterval) { _ ->
                fs.readDir(pluginDirectory) { ar ->
                    if (ar.succeeded()) {
                        val currentFiles = mutableSetOf<String>()

                        // 检查新文件和修改的文件
                        ar.result().forEach { path ->
                            if (path.endsWith(".json")) {
                                currentFiles.add(path)

                                // 新文件或修改的文件
                                if (!knownFiles.contains(path) || isFileModified(path)) {
                                    logger.info("Plugin configuration file created or modified: {}", path)
                                    deployPlugin(path)
                                }
                            }
                        }

                        // 检查删除的文件
                        knownFiles.forEach { path ->
                            if (!currentFiles.contains(path)) {
                                logger.info("Plugin configuration file deleted: {}", path)
                                undeployPlugin(path)
                            }
                        }

                        // 更新已知文件列表
                        knownFiles.clear()
                        knownFiles.addAll(currentFiles)
                    }
                }
            }

            logger.info("File watcher set up for directory: {}", pluginDirectory)
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to set up file watcher", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 检查文件是否被修改
     *
     * @param path 文件路径
     * @return 是否被修改
     */
    private fun isFileModified(path: String): Boolean {
        // 实际应用中可以使用文件的修改时间或内容哈希值来检测文件是否被修改
        // 这里简化处理，始终返回 true
        return true
    }

    /**
     * 加载已部署的插件
     */
    private fun loadDeployedPlugins(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 读取插件目录中的所有 JSON 文件
        fs.readDir(pluginDirectory) { ar ->
            if (ar.succeeded()) {
                val futures = mutableListOf<Future<*>>()

                ar.result().forEach { path ->
                    if (path.endsWith(".json")) {
                        futures.add(deployPlugin(path))
                    }
                }

                // 等待所有插件部署完成
                Future.all(futures).onComplete { result ->
                    if (result.succeeded()) {
                        logger.info("Loaded {} deployed plugins", futures.size)
                        promise.complete()
                    } else {
                        logger.error("Failed to load deployed plugins", result.cause())
                        promise.fail(result.cause())
                    }
                }
            } else {
                logger.error("Failed to read plugin directory", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 部署插件
     *
     * @param configPath 配置文件路径
     * @return 部署完成的 Future
     */
    fun deployPlugin(configPath: String): Future<String> {
        val promise = Promise.promise<String>()

        // 读取配置文件
        fs.readFile(configPath) { ar ->
            if (ar.succeeded()) {
                try {
                    val config = JsonObject(ar.result())
                    val pluginId = config.getString("id")
                    val pluginType = config.getString("type")

                    if (pluginId == null || pluginType == null) {
                        promise.fail("Invalid plugin configuration: missing id or type")
                        return@readFile
                    }

                    // 解析版本
                    val versionStr = config.getString("version", "1.0.0")
                    val version = PluginVersion.fromString(versionStr)

                    // 创建插件配置
                    val pluginConfig = PluginConfig(pluginId, pluginType, config, version)

                    // 检查是否已部署
                    val deploymentId = getDeploymentId(configPath)
                    if (deployments.containsKey(deploymentId)) {
                        // 如果已部署，先卸载
                        undeployPlugin(configPath).compose { _ ->
                            // 然后重新部署
                            registry.loadPlugin(pluginConfig)
                        }.onComplete { result ->
                            if (result.succeeded()) {
                                // 记录部署信息
                                deployments[deploymentId] = PluginDeployment(
                                    deploymentId,
                                    pluginId,
                                    configPath,
                                    version,
                                    System.currentTimeMillis()
                                )

                                logger.info("Redeployed plugin: {} version {}", pluginId, version)
                                promise.complete(deploymentId)
                            } else {
                                logger.error("Failed to redeploy plugin: {}", pluginId, result.cause())
                                promise.fail(result.cause())
                            }
                        }
                    } else {
                        // 如果未部署，直接部署
                        registry.loadPlugin(pluginConfig).onComplete { result ->
                            if (result.succeeded()) {
                                // 记录部署信息
                                deployments[deploymentId] = PluginDeployment(
                                    deploymentId,
                                    pluginId,
                                    configPath,
                                    version,
                                    System.currentTimeMillis()
                                )

                                logger.info("Deployed plugin: {} version {}", pluginId, version)
                                promise.complete(deploymentId)
                            } else {
                                logger.error("Failed to deploy plugin: {}", pluginId, result.cause())
                                promise.fail(result.cause())
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to parse plugin configuration", e)
                    promise.fail(e)
                }
            } else {
                logger.error("Failed to read plugin configuration file: {}", configPath, ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 卸载插件
     *
     * @param configPath 配置文件路径
     * @return 卸载完成的 Future
     */
    fun undeployPlugin(configPath: String): Future<Void> {
        val promise = Promise.promise<Void>()

        val deploymentId = getDeploymentId(configPath)
        val deployment = deployments[deploymentId]

        if (deployment != null) {
            // 卸载插件
            registry.unloadPlugin(deployment.pluginId).onComplete { ar ->
                if (ar.succeeded()) {
                    // 移除部署信息
                    deployments.remove(deploymentId)
                    logger.info("Undeployed plugin: {}", deployment.pluginId)
                    promise.complete()
                } else {
                    logger.error("Failed to undeploy plugin: {}", deployment.pluginId, ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } else {
            logger.warn("Plugin not deployed: {}", configPath)
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 获取部署 ID
     *
     * @param configPath 配置文件路径
     * @return 部署 ID
     */
    private fun getDeploymentId(configPath: String): String {
        return File(configPath).nameWithoutExtension
    }

    /**
     * 获取所有部署信息
     *
     * @return 部署信息列表
     */
    fun getDeployments(): List<PluginDeployment> {
        return deployments.values.toList()
    }

    /**
     * 获取部署信息
     *
     * @param deploymentId 部署 ID
     * @return 部署信息
     */
    fun getDeployment(deploymentId: String): PluginDeployment? {
        return deployments[deploymentId]
    }

    /**
     * 获取插件的部署信息
     *
     * @param pluginId 插件 ID
     * @return 部署信息
     */
    fun getPluginDeployment(pluginId: String): PluginDeployment? {
        return deployments.values.find { it.pluginId == pluginId }
    }

    /**
     * 关闭插件部署器
     */
    fun close(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 卸载所有插件
        val futures = deployments.values.map { undeployPlugin(it.configPath) }

        Future.all(futures).onComplete { ar ->
            if (ar.succeeded()) {
                logger.info("Plugin deployer closed")
                promise.complete()
            } else {
                logger.error("Failed to close plugin deployer", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: PluginDeployer? = null

        /**
         * 获取 PluginDeployer 的单例实例
         */
        fun getInstance(vertx: Vertx, registry: PluginRegistry): PluginDeployer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PluginDeployer(vertx, registry).also { INSTANCE = it }
            }
        }
    }
}

/**
 * 插件部署信息
 */
data class PluginDeployment(
    val deploymentId: String,
    val pluginId: String,
    val configPath: String,
    val version: PluginVersion,
    val deployedAt: Long
)
