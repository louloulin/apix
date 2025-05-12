package com.louloulin.apix.edge.config

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * 文件系统配置存储实现
 * 实现了 ConfigStore 接口，提供与文件系统交互的方法
 * 实现 plan7.md 中的 4.2.2 节"配置管理"功能
 */
class FileConfigStore(vertx: Vertx) : AbstractConfigStore(vertx) {
    // 配置目录
    private var configDir: String = ""

    // 备份目录
    private var backupDir: String = ""

    /**
     * 获取配置存储名称
     *
     * @return String 配置存储名称
     */
    override fun getName(): String {
        return "File Config Store"
    }

    /**
     * 获取配置存储类型
     *
     * @return ConfigStoreType 配置存储类型
     */
    override fun getType(): ConfigStoreType {
        return ConfigStoreType.FILE
    }

    /**
     * 初始化配置存储
     *
     * @param config 配置
     * @return Future<Void> 初始化结果
     */
    override fun initialize(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 获取配置
            configDir = config.getString("configDir", "./configs")
            backupDir = config.getString("backupDir", "./configs-backup")

            // 创建配置目录
            vertx.fileSystem().mkdirs(configDir)
                .compose {
                    // 创建备份目录
                    vertx.fileSystem().mkdirs(backupDir)
                }
                .compose {
                    // 调用父类初始化方法
                    super.initialize(config)
                }
                .onSuccess {
                    logger.info("文件系统配置存储初始化成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("文件系统配置存储初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("文件系统配置存储初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 加载配置
     *
     * @return Future<Void> 加载结果
     */
    override fun loadConfigs(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 清空缓存
            configCache.clear()

            // 读取配置目录
            vertx.fileSystem().readDir(configDir)
                .compose { files ->
                    // 加载每个配置文件
                    val futures = mutableListOf<Future<Void>>()

                    for (file in files) {
                        // 检查是否为 JSON 文件
                        if (file.endsWith(".json")) {
                            // 获取相对路径
                            val path = file.substring(configDir.length + 1)

                            // 加载配置
                            futures.add(loadConfig(path)
                                .compose<Void> { config ->
                                    // 缓存配置
                                    configCache[path] = config
                                    Future.succeededFuture<Void>()
                                }
                                .recover { cause ->
                                    logger.error("加载配置失败: {}", path, cause)
                                    Future.succeededFuture<Void>()
                                }
                            )
                        }
                    }

                    // 等待所有配置加载完成
                    Future.all(futures).map { null }
                }
                .onSuccess {
                    logger.info("加载配置成功，共 {} 个配置", configCache.size)
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("加载配置失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("加载配置失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 加载配置
     *
     * @param path 配置路径
     * @return Future<JsonObject> 配置内容
     */
    override fun loadConfig(path: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        try {
            // 构建文件路径
            val filePath = Paths.get(configDir, path).toString()

            // 检查文件是否存在
            vertx.fileSystem().exists(filePath)
                .compose { exists ->
                    if (exists) {
                        // 读取文件
                        vertx.fileSystem().readFile(filePath)
                    } else {
                        promise.fail("配置不存在: $path")
                        Future.failedFuture<Buffer>("配置不存在: $path")
                    }
                }
                .onSuccess { buffer ->
                    try {
                        // 解析 JSON
                        val config = buffer.toJsonObject()
                        promise.complete(config)
                    } catch (e: Exception) {
                        logger.error("解析配置失败: {}", path, e)
                        promise.fail(e)
                    }
                }
                .onFailure { cause ->
                    logger.error("加载配置失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("加载配置失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取配置列表
     *
     * @param path 配置路径
     * @return Future<JsonArray> 配置列表
     */
    override fun listConfigs(path: String): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()

        try {
            // 构建目录路径
            val dirPath = Paths.get(configDir, path).toString()

            // 检查目录是否存在
            vertx.fileSystem().exists(dirPath)
                .compose { exists ->
                    if (exists) {
                        // 读取目录
                        vertx.fileSystem().readDir(dirPath)
                    } else {
                        promise.complete(JsonArray())
                        Future.succeededFuture<List<String>>(emptyList())
                    }
                }
                .onSuccess { files ->
                    val result = JsonArray()

                    for (file in files) {
                        // 获取文件名
                        val fileName = Paths.get(file).fileName.toString()

                        // 检查是否为 JSON 文件
                        if (fileName.endsWith(".json")) {
                            // 获取相对路径
                            val relativePath = if (path.isEmpty()) fileName else "$path/$fileName"

                            // 获取文件属性
                            val fileObj = File(file)

                            result.add(JsonObject()
                                .put("path", relativePath)
                                .put("name", fileName)
                                .put("size", fileObj.length())
                                .put("lastModified", fileObj.lastModified())
                            )
                        }
                    }

                    promise.complete(result)
                }
                .onFailure { cause ->
                    logger.error("获取配置列表失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("获取配置列表失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 保存配置内部实现
     *
     * @param path 配置路径
     * @param config 配置内容
     * @param message 提交消息
     * @return Future<JsonObject> 保存结果
     */
    override fun saveConfigInternal(path: String, config: JsonObject, message: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        try {
            // 构建文件路径
            val filePath = Paths.get(configDir, path).toString()

            // 检查文件是否存在
            vertx.fileSystem().exists(filePath)
                .compose { exists ->
                    if (exists) {
                        // 备份文件
                        backupConfig(path)
                    } else {
                        // 创建父目录
                        val parentDir = Paths.get(filePath).parent.toString()
                        vertx.fileSystem().mkdirs(parentDir)
                    }
                }
                .compose {
                    // 写入文件
                    vertx.fileSystem().writeFile(filePath, Buffer.buffer(config.encodePrettily()))
                }
                .onSuccess {
                    promise.complete(JsonObject()
                        .put("path", path)
                        .put("message", message)
                        .put("timestamp", System.currentTimeMillis())
                    )
                }
                .onFailure { cause ->
                    logger.error("保存配置失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("保存配置失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 备份配置
     *
     * @param path 配置路径
     * @return Future<Void> 备份结果
     */
    private fun backupConfig(path: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 构建文件路径
            val filePath = Paths.get(configDir, path).toString()

            // 构建备份目录路径
            val backupDirPath = Paths.get(backupDir, path).parent.toString()

            // 构建备份文件路径
            val timestamp = System.currentTimeMillis()
            val backupFileName = "${Paths.get(path).fileName.toString().removeSuffix(".json")}-$timestamp.json"
            val backupFilePath = Paths.get(backupDirPath, backupFileName).toString()

            // 创建备份目录
            vertx.fileSystem().mkdirs(backupDirPath)
                .compose {
                    // 复制文件
                    vertx.fileSystem().copy(filePath, backupFilePath)
                }
                .onSuccess {
                    logger.info("备份配置成功: {} -> {}", path, backupFilePath)
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("备份配置失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("备份配置失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 删除配置内部实现
     *
     * @param path 配置路径
     * @param message 提交消息
     * @return Future<JsonObject> 删除结果
     */
    override fun deleteConfigInternal(path: String, message: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        try {
            // 构建文件路径
            val filePath = Paths.get(configDir, path).toString()

            // 检查文件是否存在
            vertx.fileSystem().exists(filePath)
                .compose { exists ->
                    if (exists) {
                        // 备份文件
                        backupConfig(path)
                            .compose {
                                // 删除文件
                                vertx.fileSystem().delete(filePath)
                            }
                    } else {
                        promise.fail("配置不存在: $path")
                        Future.failedFuture<Void>("配置不存在: $path")
                    }
                }
                .onSuccess {
                    promise.complete(JsonObject()
                        .put("path", path)
                        .put("message", message)
                        .put("timestamp", System.currentTimeMillis())
                    )
                }
                .onFailure { cause ->
                    logger.error("删除配置失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("删除配置失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 加载配置历史
     *
     * @param path 配置路径
     * @return Future<JsonArray> 配置历史
     */
    override fun loadConfigHistory(path: String): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()

        try {
            // 构建备份目录路径
            val backupDirPath = Paths.get(backupDir, path).parent.toString()
            val fileName = Paths.get(path).fileName.toString().removeSuffix(".json")

            // 检查备份目录是否存在
            vertx.fileSystem().exists(backupDirPath)
                .compose { exists ->
                    if (exists) {
                        // 读取备份目录
                        vertx.fileSystem().readDir(backupDirPath)
                    } else {
                        promise.complete(JsonArray())
                        Future.succeededFuture<List<String>>(emptyList())
                    }
                }
                .onSuccess { files ->
                    val result = JsonArray()

                    for (file in files) {
                        // 获取文件名
                        val backupFileName = Paths.get(file).fileName.toString()

                        // 检查是否为当前配置的备份
                        if (backupFileName.startsWith("$fileName-") && backupFileName.endsWith(".json")) {
                            // 解析时间戳
                            val timestamp = backupFileName.substring(fileName.length + 1, backupFileName.length - 5).toLongOrNull()

                            if (timestamp != null) {
                                // 获取文件属性
                                val fileObj = File(file)

                                result.add(JsonObject()
                                    .put("version", timestamp.toString())
                                    .put("path", path)
                                    .put("timestamp", timestamp)
                                    .put("size", fileObj.length())
                                )
                            }
                        }
                    }

                    // 按时间戳排序
                    result.list.sortByDescending { (it as JsonObject).getLong("timestamp") }

                    promise.complete(result)
                }
                .onFailure { cause ->
                    logger.error("加载配置历史失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("加载配置历史失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 加载配置版本
     *
     * @param path 配置路径
     * @param version 版本
     * @return Future<JsonObject> 配置版本内容
     */
    override fun loadConfigVersion(path: String, version: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        try {
            // 构建备份文件路径
            val fileName = Paths.get(path).fileName.toString().removeSuffix(".json")
            val backupFileName = "$fileName-$version.json"
            val backupFilePath = Paths.get(backupDir, path).parent.resolve(backupFileName).toString()

            // 检查文件是否存在
            vertx.fileSystem().exists(backupFilePath)
                .compose { exists ->
                    if (exists) {
                        // 读取文件
                        vertx.fileSystem().readFile(backupFilePath)
                    } else {
                        promise.fail("配置版本不存在: $path, $version")
                        Future.failedFuture<Buffer>("配置版本不存在: $path, $version")
                    }
                }
                .onSuccess { buffer ->
                    try {
                        // 解析 JSON
                        val config = buffer.toJsonObject()
                        promise.complete(config)
                    } catch (e: Exception) {
                        logger.error("解析配置版本失败: {}, {}", path, version, e)
                        promise.fail(e)
                    }
                }
                .onFailure { cause ->
                    logger.error("加载配置版本失败: {}, {}", path, version, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("加载配置版本失败: {}, {}", path, version, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 验证配置内部实现
     *
     * @param path 配置路径
     * @param config 配置内容
     * @return Future<JsonObject> 验证结果
     */
    override fun validateConfigInternal(path: String, config: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        try {
            // 检查配置是否为空
            if (config.isEmpty) {
                promise.complete(JsonObject()
                    .put("valid", false)
                    .put("message", "配置不能为空")
                )
                return promise.future()
            }

            // 检查配置是否为有效的 JSON
            try {
                config.encode()
            } catch (e: Exception) {
                promise.complete(JsonObject()
                    .put("valid", false)
                    .put("message", "配置不是有效的 JSON: ${e.message}")
                )
                return promise.future()
            }

            // 检查配置路径
            if (path.isEmpty()) {
                promise.complete(JsonObject()
                    .put("valid", false)
                    .put("message", "配置路径不能为空")
                )
                return promise.future()
            }

            // 检查配置路径是否以 .json 结尾
            if (!path.endsWith(".json")) {
                promise.complete(JsonObject()
                    .put("valid", false)
                    .put("message", "配置路径必须以 .json 结尾")
                )
                return promise.future()
            }

            // 验证通过
            promise.complete(JsonObject()
                .put("valid", true)
                .put("message", "配置验证通过")
            )
        } catch (e: Exception) {
            logger.error("验证配置失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 加载配置审计日志
     *
     * @param path 配置路径
     * @return Future<JsonArray> 审计日志
     */
    override fun loadConfigAuditLog(path: String): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()

        try {
            // 构建审计日志文件路径
            val auditLogPath = Paths.get(configDir, ".audit-logs", "$path.log").toString()

            // 检查文件是否存在
            vertx.fileSystem().exists(auditLogPath)
                .compose { exists ->
                    if (exists) {
                        // 读取文件
                        vertx.fileSystem().readFile(auditLogPath)
                    } else {
                        // 返回空数组
                        Future.succeededFuture(Buffer.buffer("[]"))
                    }
                }
                .onSuccess { buffer ->
                    try {
                        // 解析 JSON
                        val auditLog = buffer.toJsonArray()
                        promise.complete(auditLog)
                    } catch (e: Exception) {
                        logger.error("解析审计日志失败: {}", path, e)
                        promise.fail(e)
                    }
                }
                .onFailure { cause ->
                    logger.error("加载审计日志失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("加载审计日志失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 保存审计日志
     *
     * @param path 配置路径
     * @param auditLog 审计日志
     * @return Future<Void> 保存结果
     */
    override fun saveAuditLog(path: String, auditLog: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 构建审计日志文件路径
            val auditLogDir = Paths.get(configDir, ".audit-logs").toString()
            val auditLogPath = Paths.get(auditLogDir, "$path.log").toString()

            // 创建审计日志目录
            vertx.fileSystem().mkdirs(auditLogDir)
                .compose {
                    // 检查文件是否存在
                    vertx.fileSystem().exists(auditLogPath)
                }
                .compose { exists ->
                    if (exists) {
                        // 读取文件
                        vertx.fileSystem().readFile(auditLogPath)
                            .map { buffer ->
                                try {
                                    // 解析 JSON
                                    buffer.toJsonArray()
                                } catch (e: Exception) {
                                    // 创建新数组
                                    JsonArray()
                                }
                            }
                    } else {
                        // 创建新数组
                        Future.succeededFuture(JsonArray())
                    }
                }
                .compose { auditLogs ->
                    // 添加新的审计日志
                    auditLogs.add(0, auditLog)

                    // 限制审计日志数量
                    while (auditLogs.size() > 100) {
                        auditLogs.remove(auditLogs.size() - 1)
                    }

                    // 写入文件
                    vertx.fileSystem().writeFile(auditLogPath, Buffer.buffer(auditLogs.encodePrettily()))
                }
                .onSuccess {
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("保存审计日志失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("保存审计日志失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }
}
