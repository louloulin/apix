package com.louloulin.apix.edge.config

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

/**
 * Git 配置存储实现
 * 实现了 ConfigStore 接口，提供与 Git 交互的方法
 * 实现 plan7.md 中的 4.2.2 节"配置管理"功能
 */
class GitConfigStore(vertx: Vertx) : AbstractConfigStore(vertx) {
    // Git 仓库 URL
    private var repoUrl: String = ""

    // Git 仓库分支
    private var branch: String = "main"

    // Git 用户名
    private var username: String = ""

    // Git 密码或令牌
    private var password: String = ""

    // 本地仓库路径
    private var localRepoPath: String = ""

    // Web 客户端
    private lateinit var webClient: WebClient

    /**
     * 获取配置存储名称
     *
     * @return String 配置存储名称
     */
    override fun getName(): String {
        return "Git Config Store"
    }

    /**
     * 获取配置存储类型
     *
     * @return ConfigStoreType 配置存储类型
     */
    override fun getType(): ConfigStoreType {
        return ConfigStoreType.GIT
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
            // 获取 Git 配置
            repoUrl = config.getString("repoUrl", "")
            branch = config.getString("branch", "main")
            username = config.getString("username", "")
            password = config.getString("password", "")
            localRepoPath = config.getString("localRepoPath", "./config-repo")

            // 验证配置
            if (repoUrl.isEmpty()) {
                promise.fail("Git 仓库 URL 不能为空")
                return promise.future()
            }

            // 创建 Web 客户端
            val webClientOptions = WebClientOptions()
                .setUserAgent("APIX GitConfigStore")
                .setKeepAlive(true)
                .setMaxPoolSize(10)

            webClient = WebClient.create(vertx, webClientOptions)

            // 调用父类初始化方法
            super.initialize(config)
                .compose {
                    // 克隆或拉取仓库
                    cloneOrPullRepo()
                }
                .onSuccess {
                    logger.info("Git 配置存储初始化成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("Git 配置存储初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("Git 配置存储初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 克隆或拉取仓库
     *
     * @return Future<Void> 操作结果
     */
    private fun cloneOrPullRepo(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 检查本地仓库是否存在
            val repoDir = File(localRepoPath)

            if (repoDir.exists() && repoDir.isDirectory && File(repoDir, ".git").exists()) {
                // 拉取仓库
                pullRepo()
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { cause ->
                        logger.error("拉取仓库失败", cause)
                        promise.fail(cause)
                    }
            } else {
                // 克隆仓库
                cloneRepo()
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { cause ->
                        logger.error("克隆仓库失败", cause)
                        promise.fail(cause)
                    }
            }
        } catch (e: Exception) {
            logger.error("克隆或拉取仓库失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 克隆仓库
     *
     * @return Future<Void> 克隆结果
     */
    private fun cloneRepo(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 创建本地仓库目录
            val repoDir = File(localRepoPath)
            if (!repoDir.exists()) {
                repoDir.mkdirs()
            }

            // 构建克隆命令
            val command = StringBuilder("git clone")

            // 添加分支
            command.append(" -b ").append(branch)

            // 添加认证信息
            val authRepoUrl = if (username.isNotEmpty() && password.isNotEmpty()) {
                repoUrl.replace("://", "://$username:$password@")
            } else {
                repoUrl
            }

            // 添加仓库 URL
            command.append(" ").append(authRepoUrl)

            // 添加本地路径
            command.append(" ").append(localRepoPath)

            // 执行命令
            vertx.executeBlocking<Void>({ promise ->
                try {
                    val process = Runtime.getRuntime().exec(command.toString())
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        promise.complete()
                    } else {
                        val error = process.errorStream.bufferedReader().readText()
                        promise.fail("克隆仓库失败: $error")
                    }
                } catch (e: Exception) {
                    promise.fail(e)
                }
            }, false)
                .onSuccess {
                    logger.info("克隆仓库成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("克隆仓库失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("克隆仓库失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 拉取仓库
     *
     * @return Future<Void> 拉取结果
     */
    private fun pullRepo(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 构建拉取命令
            val command = "git -C $localRepoPath pull origin $branch"

            // 执行命令
            vertx.executeBlocking<Void>({ promise ->
                try {
                    val process = Runtime.getRuntime().exec(command)
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        promise.complete()
                    } else {
                        val error = process.errorStream.bufferedReader().readText()
                        promise.fail("拉取仓库失败: $error")
                    }
                } catch (e: Exception) {
                    promise.fail(e)
                }
            }, false)
                .onSuccess {
                    logger.info("拉取仓库成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("拉取仓库失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("拉取仓库失败", e)
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

            // 拉取仓库
            pullRepo()
                .onSuccess {
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
     * 获取配置列表
     *
     * @param path 配置路径
     * @return Future<JsonArray> 配置列表
     */
    override fun listConfigs(path: String): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()

        try {
            // 构建目录路径
            val dirPath = Paths.get(localRepoPath, path)

            // 检查目录是否存在
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                promise.complete(JsonArray())
                return promise.future()
            }

            // 读取目录
            vertx.fileSystem().readDir(dirPath.toString())
                .onSuccess { files ->
                    val result = JsonArray()

                    // 遍历文件
                    for (file in files) {
                        val filePath = Paths.get(file)
                        val fileName = filePath.fileName.toString()

                        // 只包含 JSON 文件
                        if (fileName.endsWith(".json")) {
                            // 获取相对路径
                            val relativePath = Paths.get(path, fileName).toString()

                            result.add(JsonObject()
                                .put("path", relativePath)
                                .put("name", fileName)
                                .put("type", "file")
                            )
                        } else if (Files.isDirectory(filePath)) {
                            // 获取相对路径
                            val relativePath = Paths.get(path, fileName).toString()

                            result.add(JsonObject()
                                .put("path", relativePath)
                                .put("name", fileName)
                                .put("type", "directory")
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
     * 加载配置
     *
     * @param path 配置路径
     * @return Future<JsonObject> 配置内容
     */
    override fun loadConfig(path: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        try {
            // 构建文件路径
            val filePath = Paths.get(localRepoPath, path)

            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                promise.fail("配置不存在: $path")
                return promise.future()
            }

            // 读取文件内容
            vertx.fileSystem().readFile(filePath.toString())
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
                    logger.error("读取配置失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("加载配置失败: {}", path, e)
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
            val filePath = Paths.get(localRepoPath, path)

            // 创建父目录
            Files.createDirectories(filePath.parent)

            // 写入文件
            vertx.fileSystem().writeFile(filePath.toString(), Buffer.buffer(config.encodePrettily()))
                .compose {
                    // 提交更改
                    commitChanges(path, message)
                }
                .compose {
                    // 推送更改
                    pushChanges()
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
     * 提交更改
     *
     * @param path 配置路径
     * @param message 提交消息
     * @return Future<Void> 提交结果
     */
    private fun commitChanges(path: String, message: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 构建添加命令
            val addCommand = "git -C $localRepoPath add $path"

            // 执行添加命令
            vertx.executeBlocking<Void>({ promise ->
                try {
                    val process = Runtime.getRuntime().exec(addCommand)
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        promise.complete()
                    } else {
                        val error = process.errorStream.bufferedReader().readText()
                        promise.fail("添加文件失败: $error")
                    }
                } catch (e: Exception) {
                    promise.fail(e)
                }
            }, false)
                .compose {
                    // 构建提交命令
                    val commitCommand = "git -C $localRepoPath commit -m \"$message\""

                    // 执行提交命令
                    vertx.executeBlocking<Void>({ promise ->
                        try {
                            val process = Runtime.getRuntime().exec(commitCommand)
                            val exitCode = process.waitFor()

                            if (exitCode == 0) {
                                promise.complete()
                            } else {
                                val error = process.errorStream.bufferedReader().readText()
                                promise.fail("提交更改失败: $error")
                            }
                        } catch (e: Exception) {
                            promise.fail(e)
                        }
                    }, false)
                }
                .onSuccess {
                    logger.info("提交更改成功: {}", path)
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("提交更改失败: {}", path, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("提交更改失败: {}", path, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 推送更改
     *
     * @return Future<Void> 推送结果
     */
    private fun pushChanges(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 构建推送命令
            val pushCommand = "git -C $localRepoPath push origin $branch"

            // 执行推送命令
            vertx.executeBlocking<Void>({ promise ->
                try {
                    val process = Runtime.getRuntime().exec(pushCommand)
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        promise.complete()
                    } else {
                        val error = process.errorStream.bufferedReader().readText()
                        promise.fail("推送更改失败: $error")
                    }
                } catch (e: Exception) {
                    promise.fail(e)
                }
            }, false)
                .onSuccess {
                    logger.info("推送更改成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("推送更改失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("推送更改失败", e)
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
            val filePath = Paths.get(localRepoPath, path)

            // 检查文件是否存在
            if (!Files.exists(filePath)) {
                promise.fail("配置不存在: $path")
                return promise.future()
            }

            // 删除文件
            vertx.fileSystem().delete(filePath.toString())
                .compose {
                    // 提交更改
                    commitChanges(path, message)
                }
                .compose {
                    // 推送更改
                    pushChanges()
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
            // 构建历史命令
            val logCommand = "git -C $localRepoPath log --pretty=format:\"%H|%an|%at|%s\" -- $path"

            // 执行历史命令
            vertx.executeBlocking<JsonArray>({ promise ->
                try {
                    val process = Runtime.getRuntime().exec(logCommand)
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        val output = process.inputStream.bufferedReader().readText()
                        val history = JsonArray()

                        // 解析历史记录
                        for (line in output.split("\n")) {
                            if (line.isNotEmpty()) {
                                val parts = line.split("|")
                                if (parts.size >= 4) {
                                    val commit = parts[0]
                                    val author = parts[1]
                                    val timestamp = parts[2].toLong() * 1000
                                    val message = parts[3]

                                    history.add(JsonObject()
                                        .put("commit", commit)
                                        .put("author", author)
                                        .put("timestamp", timestamp)
                                        .put("message", message)
                                    )
                                }
                            }
                        }

                        promise.complete(history)
                    } else {
                        val error = process.errorStream.bufferedReader().readText()
                        promise.fail("获取历史记录失败: $error")
                    }
                } catch (e: Exception) {
                    promise.fail(e)
                }
            }, false)
                .onSuccess { history ->
                    promise.complete(history)
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
            // 构建临时文件路径
            val tempFile = "$localRepoPath/.temp-${UUID.randomUUID()}.json"

            // 构建显示命令
            val showCommand = "git -C $localRepoPath show $version:$path > $tempFile"

            // 执行显示命令
            vertx.executeBlocking<Void>({ promise ->
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("bash", "-c", showCommand))
                    val exitCode = process.waitFor()

                    if (exitCode == 0) {
                        promise.complete()
                    } else {
                        val error = process.errorStream.bufferedReader().readText()
                        promise.fail("获取版本内容失败: $error")
                    }
                } catch (e: Exception) {
                    promise.fail(e)
                }
            }, false)
                .compose {
                    // 读取临时文件
                    vertx.fileSystem().readFile(tempFile)
                }
                .compose { buffer ->
                    // 删除临时文件
                    vertx.fileSystem().delete(tempFile)
                        .map { buffer }
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
            val auditLogPath = "$localRepoPath/.audit-logs/$path.log"

            // 检查文件是否存在
            vertx.fileSystem().exists(auditLogPath)
                .compose { exists ->
                    if (exists) {
                        // 读取审计日志文件
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
            val auditLogDir = "$localRepoPath/.audit-logs"
            val auditLogPath = "$auditLogDir/$path.log"

            // 创建审计日志目录
            vertx.fileSystem().mkdirs(auditLogDir)
                .compose {
                    // 检查文件是否存在
                    vertx.fileSystem().exists(auditLogPath)
                }
                .compose { exists ->
                    if (exists) {
                        // 读取审计日志文件
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

                    // 写入审计日志文件
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

    /**
     * 关闭配置存储
     *
     * @return Future<Void> 关闭结果
     */
    override fun close(): Future<Void> {
        // 关闭 Web 客户端
        webClient.close()

        // 调用父类关闭方法
        return super.close()
    }
}
