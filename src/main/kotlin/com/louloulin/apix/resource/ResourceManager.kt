package com.louloulin.apix.resource

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
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.Deflater
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

/**
 * 静态资源管理器
 * 负责静态资源的版本控制、打包、指纹和依赖管理
 * 实现plan7.md中的3.3.1节"资源管理"功能
 */
class ResourceManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ResourceManager::class.java)

    // 资源管理配置
    private val resourceConfig = AtomicReference<JsonObject>(JsonObject())

    // 资源管理是否启用
    private val resourceEnabled = AtomicBoolean(false)

    // 资源根目录
    val resourceRoot = AtomicReference<String>("")

    // 资源缓存目录
    val cacheRoot = AtomicReference<String>("")

    // 资源映射表
    private val resourceMap = ConcurrentHashMap<String, ResourceInfo>()

    // 资源依赖表
    private val dependencyMap = ConcurrentHashMap<String, List<String>>()

    // 文件系统
    private val fs: FileSystem = vertx.fileSystem()

    /**
     * 获取ResourceManager实例
     */
    companion object {
        private var instance: ResourceManager? = null

        @Synchronized
        fun getInstance(vertx: Vertx): ResourceManager {
            if (instance == null) {
                instance = ResourceManager(vertx)
            }
            return instance!!
        }
    }

    /**
     * 初始化资源管理器
     *
     * @param config 资源管理配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化资源管理器")

        val promise = Promise.promise<Void>()

        try {
            // 保存配置
            this.resourceConfig.set(config)

            // 获取资源管理启用状态
            val enabled = config.getBoolean("enabled", false)
            this.resourceEnabled.set(enabled)

            if (!enabled) {
                logger.info("资源管理功能已禁用")
                promise.complete()
                return promise.future()
            }

            // 获取资源根目录
            val root = config.getString("root", "static")
            this.resourceRoot.set(root)

            // 获取资源缓存目录
            val cache = config.getString("cache", "static-cache")
            this.cacheRoot.set(cache)

            // 创建目录
            createDirectories()
                .compose {
                    // 扫描资源
                    scanResources()
                }
                .compose {
                    // 处理资源
                    processResources()
                }
                .onSuccess {
                    logger.info("资源管理器初始化完成")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("资源管理器初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("资源管理器初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 创建目录
     *
     * @return Future<Void> 创建结果
     */
    private fun createDirectories(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 创建资源根目录
        fs.mkdirs(resourceRoot.get())
            .compose {
                // 创建资源缓存目录
                fs.mkdirs(cacheRoot.get())
            }
            .onSuccess {
                logger.info("目录创建成功")
                promise.complete()
            }
            .onFailure { cause ->
                logger.error("目录创建失败", cause)
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 扫描资源
     *
     * @return Future<Void> 扫描结果
     */
    private fun scanResources(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 清空资源映射表
        resourceMap.clear()

        // 扫描资源根目录
        scanDirectory(resourceRoot.get())
            .onSuccess {
                logger.info("资源扫描完成，共发现 {} 个资源", resourceMap.size)
                promise.complete()
            }
            .onFailure { cause ->
                logger.error("资源扫描失败", cause)
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 扫描目录
     *
     * @param directory 目录路径
     * @return Future<Void> 扫描结果
     */
    private fun scanDirectory(directory: String): Future<Void> {
        val promise = Promise.promise<Void>()

        fs.readDir(directory)
            .compose { files ->
                val futures = mutableListOf<Future<Void>>()

                for (file in files) {
                    fs.props(file)
                        .compose { props ->
                            if (props.isDirectory) {
                                // 如果是目录，递归扫描
                                scanDirectory(file)
                            } else {
                                // 如果是文件，添加到资源映射表
                                val relativePath = file.removePrefix("${resourceRoot.get()}/")
                                val resourceInfo = ResourceInfo(
                                    path = relativePath,
                                    size = props.size(),
                                    lastModified = props.lastModifiedTime()
                                )

                                resourceMap[relativePath] = resourceInfo
                                Future.succeededFuture<Void>()
                            }
                        }
                        .onSuccess {
                            // 成功处理一个文件
                        }
                        .onFailure { cause ->
                            logger.error("处理文件失败: {}", file, cause)
                        }
                        .let { futures.add(it) }
                }

                Future.all(futures).map { null as Void? }
            }
            .onSuccess {
                promise.complete()
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 处理资源
     *
     * @return Future<Void> 处理结果
     */
    private fun processResources(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 获取处理配置
        val processConfig = resourceConfig.get().getJsonObject("process", JsonObject())
        val generateFingerprint = processConfig.getBoolean("fingerprint", true)
        val compressResources = processConfig.getBoolean("compress", true)
        val analyzeDependencies = processConfig.getBoolean("dependencies", true)

        // 处理每个资源
        val futures = mutableListOf<Future<Void>>()

        for ((path, info) in resourceMap) {
            // 处理单个资源
            processResource(path, info, generateFingerprint, compressResources, analyzeDependencies)
                .onSuccess {
                    // 成功处理一个资源
                }
                .onFailure { cause ->
                    logger.error("处理资源失败: {}", path, cause)
                }
                .let { futures.add(it) }
        }

        Future.all(futures)
            .map { null as Void? }
            .onSuccess {
                logger.info("资源处理完成")
                promise.complete()
            }
            .onFailure { cause ->
                logger.error("资源处理失败", cause)
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 处理单个资源
     *
     * @param path 资源路径
     * @param info 资源信息
     * @param generateFingerprint 是否生成指纹
     * @param compressResources 是否压缩资源
     * @param analyzeDependencies 是否分析依赖
     * @return Future<Void> 处理结果
     */
    private fun processResource(
        path: String,
        info: ResourceInfo,
        generateFingerprint: Boolean,
        compressResources: Boolean,
        analyzeDependencies: Boolean
    ): Future<Void> {
        val promise = Promise.promise<Void>()

        // 读取资源内容
        fs.readFile("${resourceRoot.get()}/$path")
            .compose { content ->
                // 生成指纹
                if (generateFingerprint) {
                    val fingerprint = generateFingerprint(content)
                    info.fingerprint = fingerprint

                    // 创建指纹版本的文件
                    val extension = File(path).extension
                    val baseName = path.removeSuffix(".$extension")
                    val fingerprintPath = "$baseName.$fingerprint.$extension"
                    info.fingerprintPath = fingerprintPath

                    // 写入指纹版本的文件
                    fs.writeFile("${cacheRoot.get()}/$fingerprintPath", content)
                        .map { content }
                } else {
                    Future.succeededFuture(content)
                }
            }
            .compose { content ->
                // 压缩资源
                if (compressResources && shouldCompress(path)) {
                    // 压缩为gzip
                    compressGzip(content)
                        .compose { gzipped ->
                            // 写入gzip版本的文件
                            val gzipPath = "${info.fingerprintPath ?: path}.gz"
                            info.gzipPath = gzipPath

                            fs.writeFile("${cacheRoot.get()}/$gzipPath", gzipped)
                                .map { content }
                        }
                } else {
                    Future.succeededFuture(content)
                }
            }
            .compose { content ->
                // 分析依赖
                if (analyzeDependencies && shouldAnalyzeDependencies(path)) {
                    val dependencies = analyzeDependencies(path, content)
                    dependencyMap[path] = dependencies
                    Future.succeededFuture<Void>()
                } else {
                    Future.succeededFuture<Void>()
                }
            }
            .onSuccess {
                promise.complete()
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 生成指纹
     *
     * @param content 资源内容
     * @return String 指纹
     */
    private fun generateFingerprint(content: Buffer): String {
        val digest = MessageDigest.getInstance("MD5")
        val hash = digest.digest(content.bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * 压缩为gzip
     *
     * @param content 资源内容
     * @return Future<Buffer> 压缩后的内容
     */
    private fun compressGzip(content: Buffer): Future<Buffer> {
        val promise = Promise.promise<Buffer>()

        vertx.executeBlocking<Buffer>({ blockingPromise ->
            try {
                val baos = ByteArrayOutputStream()
                val gzos = GZIPOutputStream(baos)
                gzos.write(content.bytes)
                gzos.close()

                val compressed = Buffer.buffer(baos.toByteArray())
                blockingPromise.complete(compressed)
            } catch (e: Exception) {
                blockingPromise.fail(e)
            }
        }, { ar ->
            if (ar.succeeded()) {
                promise.complete(ar.result())
            } else {
                promise.fail(ar.cause())
            }
        })

        return promise.future()
    }

    /**
     * 判断是否应该压缩
     *
     * @param path 资源路径
     * @return Boolean 是否应该压缩
     */
    private fun shouldCompress(path: String): Boolean {
        val extension = File(path).extension.lowercase()
        val compressibleExtensions = listOf(
            "html", "css", "js", "json", "xml", "svg", "txt", "md"
        )

        return extension in compressibleExtensions
    }

    /**
     * 判断是否应该分析依赖
     *
     * @param path 资源路径
     * @return Boolean 是否应该分析依赖
     */
    private fun shouldAnalyzeDependencies(path: String): Boolean {
        val extension = File(path).extension.lowercase()
        return extension in listOf("html", "css", "js")
    }

    /**
     * 分析依赖
     *
     * @param path 资源路径
     * @param content 资源内容
     * @return List<String> 依赖列表
     */
    private fun analyzeDependencies(path: String, content: Buffer): List<String> {
        val extension = File(path).extension.lowercase()
        val contentStr = content.toString()
        val dependencies = mutableListOf<String>()

        when (extension) {
            "html" -> {
                // 分析HTML中的依赖
                // 查找<script src="...">
                val scriptRegex = """<script[^>]*src=["']([^"']+)["'][^>]*>""".toRegex()
                scriptRegex.findAll(contentStr).forEach {
                    val src = it.groupValues[1]
                    if (!src.startsWith("http") && !src.startsWith("//")) {
                        dependencies.add(src)
                    }
                }

                // 查找<link href="...">
                val linkRegex = """<link[^>]*href=["']([^"']+)["'][^>]*>""".toRegex()
                linkRegex.findAll(contentStr).forEach {
                    val href = it.groupValues[1]
                    if (!href.startsWith("http") && !href.startsWith("//")) {
                        dependencies.add(href)
                    }
                }

                // 查找<img src="...">
                val imgRegex = """<img[^>]*src=["']([^"']+)["'][^>]*>""".toRegex()
                imgRegex.findAll(contentStr).forEach {
                    val src = it.groupValues[1]
                    if (!src.startsWith("http") && !src.startsWith("//") && !src.startsWith("data:")) {
                        dependencies.add(src)
                    }
                }
            }
            "css" -> {
                // 分析CSS中的依赖
                // 查找url(...)
                val urlRegex = """url\(["']?([^"')]+)["']?\)""".toRegex()
                urlRegex.findAll(contentStr).forEach {
                    val url = it.groupValues[1]
                    if (!url.startsWith("http") && !url.startsWith("//") && !url.startsWith("data:")) {
                        dependencies.add(url)
                    }
                }

                // 查找@import "..."
                val importRegex = """@import\s+["']([^"']+)["']""".toRegex()
                importRegex.findAll(contentStr).forEach {
                    val importPath = it.groupValues[1]
                    if (!importPath.startsWith("http") && !importPath.startsWith("//")) {
                        dependencies.add(importPath)
                    }
                }
            }
            "js" -> {
                // 分析JS中的依赖
                // 查找import "..."
                val importRegex = """import\s+["']([^"']+)["']""".toRegex()
                importRegex.findAll(contentStr).forEach {
                    val importPath = it.groupValues[1]
                    if (!importPath.startsWith("http") && !importPath.startsWith("//")) {
                        dependencies.add(importPath)
                    }
                }

                // 查找require("...")
                val requireRegex = """require\(["']([^"']+)["']\)""".toRegex()
                requireRegex.findAll(contentStr).forEach {
                    val requirePath = it.groupValues[1]
                    if (!requirePath.startsWith("http") && !requirePath.startsWith("//")) {
                        dependencies.add(requirePath)
                    }
                }
            }
        }

        return dependencies
    }

    /**
     * 获取资源信息
     *
     * @param path 资源路径
     * @return ResourceInfo? 资源信息
     */
    fun getResourceInfo(path: String): ResourceInfo? {
        return resourceMap[path]
    }

    /**
     * 获取资源指纹路径
     *
     * @param path 资源路径
     * @return String? 指纹路径
     */
    fun getResourceFingerprintPath(path: String): String? {
        return resourceMap[path]?.fingerprintPath
    }

    /**
     * 获取资源gzip路径
     *
     * @param path 资源路径
     * @return String? gzip路径
     */
    fun getResourceGzipPath(path: String): String? {
        return resourceMap[path]?.gzipPath
    }

    /**
     * 获取资源依赖
     *
     * @param path 资源路径
     * @return List<String> 依赖列表
     */
    fun getResourceDependencies(path: String): List<String> {
        return dependencyMap[path] ?: emptyList()
    }

    /**
     * 获取所有资源
     *
     * @return Map<String, ResourceInfo> 资源映射表
     */
    fun getAllResources(): Map<String, ResourceInfo> {
        return resourceMap.toMap()
    }

    /**
     * 添加资源
     *
     * @param path 资源路径
     * @param content 资源内容
     * @return Future<ResourceInfo> 资源信息
     */
    fun addResource(path: String, content: Buffer): Future<ResourceInfo> {
        val promise = Promise.promise<ResourceInfo>()

        if (!resourceEnabled.get()) {
            return Future.failedFuture("资源管理功能已禁用")
        }

        // 写入资源
        fs.writeFile("${resourceRoot.get()}/$path", content)
            .compose {
                // 获取资源属性
                fs.props("${resourceRoot.get()}/$path")
            }
            .compose { props ->
                // 创建资源信息
                val resourceInfo = ResourceInfo(
                    path = path,
                    size = props.size(),
                    lastModified = props.lastModifiedTime()
                )

                // 添加到资源映射表
                resourceMap[path] = resourceInfo

                // 处理资源
                val processConfig = resourceConfig.get().getJsonObject("process", JsonObject())
                val generateFingerprint = processConfig.getBoolean("fingerprint", true)
                val compressResources = processConfig.getBoolean("compress", true)
                val analyzeDependencies = processConfig.getBoolean("dependencies", true)

                processResource(path, resourceInfo, generateFingerprint, compressResources, analyzeDependencies)
                    .map { resourceInfo }
            }
            .onSuccess { resourceInfo ->
                promise.complete(resourceInfo)
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 删除资源
     *
     * @param path 资源路径
     * @return Future<Void> 删除结果
     */
    fun deleteResource(path: String): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!resourceEnabled.get()) {
            return Future.failedFuture("资源管理功能已禁用")
        }

        // 获取资源信息
        val resourceInfo = resourceMap[path]

        if (resourceInfo == null) {
            return Future.failedFuture("资源不存在: $path")
        }

        // 删除原始文件
        fs.delete("${resourceRoot.get()}/$path")
            .compose {
                // 删除指纹版本的文件
                val fingerprintPath = resourceInfo.fingerprintPath
                if (fingerprintPath != null) {
                    fs.delete("${cacheRoot.get()}/$fingerprintPath")
                } else {
                    Future.succeededFuture<Void>()
                }
            }
            .compose {
                // 删除gzip版本的文件
                val gzipPath = resourceInfo.gzipPath
                if (gzipPath != null) {
                    fs.delete("${cacheRoot.get()}/$gzipPath")
                } else {
                    Future.succeededFuture<Void>()
                }
            }
            .compose {
                // 从资源映射表中删除
                resourceMap.remove(path)

                // 从依赖表中删除
                dependencyMap.remove(path)

                Future.succeededFuture<Void>()
            }
            .onSuccess {
                promise.complete()
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 更新资源
     *
     * @param path 资源路径
     * @param content 资源内容
     * @return Future<ResourceInfo> 资源信息
     */
    fun updateResource(path: String, content: Buffer): Future<ResourceInfo> {
        val promise = Promise.promise<ResourceInfo>()

        if (!resourceEnabled.get()) {
            return Future.failedFuture("资源管理功能已禁用")
        }

        // 删除旧资源
        deleteResource(path)
            .compose {
                // 添加新资源
                addResource(path, content)
            }
            .onSuccess { resourceInfo ->
                promise.complete(resourceInfo)
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 获取资源状态
     *
     * @return JsonObject 资源状态
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", resourceEnabled.get())
            .put("resourceCount", resourceMap.size)
            .put("resourceRoot", resourceRoot.get())
            .put("cacheRoot", cacheRoot.get())

        return status
    }

    /**
     * 更新资源管理配置
     *
     * @param config 新的资源管理配置
     * @return Future<Void> 更新结果
     */
    fun updateConfig(config: JsonObject): Future<Void> {
        logger.info("更新资源管理配置")

        // 清空资源映射表和依赖表
        resourceMap.clear()
        dependencyMap.clear()

        // 重新初始化
        return initialize(config)
    }

    /**
     * 关闭资源管理器
     *
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void> {
        logger.info("关闭资源管理器")

        // 清空资源映射表和依赖表
        resourceMap.clear()
        dependencyMap.clear()

        return Future.succeededFuture()
    }
}

/**
 * 资源信息
 */
data class ResourceInfo(
    val path: String,
    val size: Long,
    val lastModified: Long,
    var fingerprint: String? = null,
    var fingerprintPath: String? = null,
    var gzipPath: String? = null
)
