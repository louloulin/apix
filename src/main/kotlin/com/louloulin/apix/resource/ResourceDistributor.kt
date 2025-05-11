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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * 资源分发器
 * 负责静态资源的多区域同步、增量更新、按需分发和预热策略
 * 实现plan7.md中的3.3.2节"资源分发"功能
 */
class ResourceDistributor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ResourceDistributor::class.java)

    // 资源分发配置
    private val distributionConfig = AtomicReference<JsonObject>(JsonObject())

    // 资源分发是否启用
    private val distributionEnabled = AtomicBoolean(false)

    // 资源管理器
    private lateinit var resourceManager: ResourceManager

    // 区域列表
    private val regions = ConcurrentHashMap<String, RegionInfo>()

    // 同步状态
    private val syncStatus = ConcurrentHashMap<String, JsonObject>()

    // 文件系统
    private val fs: FileSystem = vertx.fileSystem()

    // 同步定时器ID
    private var syncTimerId: Long = -1

    // 预热定时器ID
    private var prewarmTimerId: Long = -1

    /**
     * 获取ResourceDistributor实例
     */
    companion object {
        private var instance: ResourceDistributor? = null

        @Synchronized
        fun getInstance(vertx: Vertx): ResourceDistributor {
            if (instance == null) {
                instance = ResourceDistributor(vertx)
            }
            return instance!!
        }
    }

    /**
     * 初始化资源分发器
     *
     * @param config 资源分发配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化资源分发器")

        val promise = Promise.promise<Void>()

        try {
            // 保存配置
            this.distributionConfig.set(config)

            // 获取资源分发启用状态
            val enabled = config.getBoolean("enabled", false)
            this.distributionEnabled.set(enabled)

            if (!enabled) {
                logger.info("资源分发功能已禁用")
                promise.complete()
                return promise.future()
            }

            // 获取资源管理器
            resourceManager = ResourceManager.getInstance(vertx)

            // 加载区域
            loadRegions(config)
                .compose {
                    // 创建区域目录
                    createRegionDirectories()
                }
                .compose {
                    // 初始同步
                    initialSync()
                }
                .compose {
                    // 启动定时同步
                    startPeriodicSync()
                }
                .compose {
                    // 启动预热
                    startPrewarm()
                }
                .onSuccess {
                    logger.info("资源分发器初始化完成")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("资源分发器初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("资源分发器初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 加载区域
     *
     * @param config 资源分发配置
     * @return Future<Void> 加载结果
     */
    private fun loadRegions(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 清空区域列表
            regions.clear()

            // 获取区域配置
            val regionsConfig = config.getJsonArray("regions", JsonArray())

            if (regionsConfig.isEmpty) {
                logger.warn("未配置区域")
                promise.complete()
                return promise.future()
            }

            // 加载每个区域
            for (i in 0 until regionsConfig.size()) {
                val regionConfig = regionsConfig.getJsonObject(i)
                val id = regionConfig.getString("id", "")
                val name = regionConfig.getString("name", "")
                val path = regionConfig.getString("path", "")
                val enabled = regionConfig.getBoolean("enabled", true)

                if (id.isEmpty() || path.isEmpty()) {
                    logger.warn("区域配置无效: {}", regionConfig.encode())
                    continue
                }

                if (!enabled) {
                    logger.info("区域已禁用: {}", id)
                    continue
                }

                // 创建区域信息
                val regionInfo = RegionInfo(id, name, path)

                // 添加到区域列表
                regions[id] = regionInfo

                // 初始化同步状态
                syncStatus[id] = JsonObject()
                    .put("lastSync", 0L)
                    .put("status", "pending")
                    .put("message", "等待同步")
            }

            logger.info("加载了 {} 个区域", regions.size)
            promise.complete()
        } catch (e: Exception) {
            logger.error("加载区域失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 创建区域目录
     *
     * @return Future<Void> 创建结果
     */
    private fun createRegionDirectories(): Future<Void> {
        val promise = Promise.promise<Void>()

        val futures = mutableListOf<Future<Void>>()

        for ((id, region) in regions) {
            fs.mkdirs(region.path)
                .onSuccess {
                    logger.info("区域目录创建成功: {}", region.path)
                }
                .onFailure { cause ->
                    logger.error("区域目录创建失败: {}", region.path, cause)
                }
                .let { futures.add(it) }
        }

        Future.all(futures)
            .map { null as Void? }
            .onSuccess {
                promise.complete()
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 初始同步
     *
     * @return Future<Void> 同步结果
     */
    private fun initialSync(): Future<Void> {
        val promise = Promise.promise<Void>()

        val futures = mutableListOf<Future<Void>>()

        for ((id, region) in regions) {
            // 同步区域
            syncRegion(id, true)
                .onSuccess {
                    logger.info("区域初始同步成功: {}", id)
                }
                .onFailure { cause ->
                    logger.error("区域初始同步失败: {}", id, cause)
                }
                .let { futures.add(it) }
        }

        Future.all(futures)
            .map { null as Void? }
            .onSuccess {
                promise.complete()
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 启动定时同步
     *
     * @return Future<Void> 启动结果
     */
    private fun startPeriodicSync(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 获取同步间隔
            val syncInterval = distributionConfig.get().getLong("syncInterval", 300000L) // 默认5分钟

            // 启动定时同步
            syncTimerId = vertx.setPeriodic(syncInterval) {
                for ((id, _) in regions) {
                    syncRegion(id, false)
                        .onSuccess {
                            logger.debug("区域定时同步成功: {}", id)
                        }
                        .onFailure { cause ->
                            logger.error("区域定时同步失败: {}", id, cause)
                        }
                }
            }

            logger.info("定时同步已启动，间隔: {}ms", syncInterval)
            promise.complete()
        } catch (e: Exception) {
            logger.error("启动定时同步失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 启动预热
     *
     * @return Future<Void> 启动结果
     */
    private fun startPrewarm(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 获取预热配置
            val prewarmConfig = distributionConfig.get().getJsonObject("prewarm", JsonObject())
            val enabled = prewarmConfig.getBoolean("enabled", false)

            if (!enabled) {
                logger.info("资源预热功能已禁用")
                promise.complete()
                return promise.future()
            }

            // 获取预热间隔
            val prewarmInterval = prewarmConfig.getLong("interval", 3600000L) // 默认1小时

            // 启动定时预热
            prewarmTimerId = vertx.setPeriodic(prewarmInterval) {
                prewarmResources()
                    .onSuccess {
                        logger.debug("资源预热成功")
                    }
                    .onFailure { cause ->
                        logger.error("资源预热失败", cause)
                    }
            }

            // 立即执行一次预热
            prewarmResources()
                .onSuccess {
                    logger.info("资源预热已启动，间隔: {}ms", prewarmInterval)
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("资源预热失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("启动预热失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 同步区域
     *
     * @param regionId 区域ID
     * @param fullSync 是否完全同步
     * @return Future<Void> 同步结果
     */
    private fun syncRegion(regionId: String, fullSync: Boolean): Future<Void> {
        val promise = Promise.promise<Void>()

        // 获取区域信息
        val region = regions[regionId]

        if (region == null) {
            return Future.failedFuture("区域不存在: $regionId")
        }

        // 更新同步状态
        syncStatus[regionId] = JsonObject()
            .put("lastSync", System.currentTimeMillis())
            .put("status", "syncing")
            .put("message", "正在同步")

        // 获取所有资源
        val resources = resourceManager.getAllResources()

        // 同步每个资源
        val futures = mutableListOf<Future<Void>>()

        for ((path, info) in resources) {
            // 目标路径
            val targetPath = "${region.path}/$path"

            // 检查是否需要同步
            if (!fullSync) {
                fs.exists(targetPath)
                    .compose { exists ->
                        if (exists) {
                            // 如果文件存在，检查是否需要更新
                            fs.props(targetPath)
                                .compose { props ->
                                    if (props.lastModifiedTime() < info.lastModified) {
                                        // 如果目标文件较旧，需要更新
                                        syncResource(path, info, region)
                                    } else {
                                        // 如果目标文件较新或相同，不需要更新
                                        Future.succeededFuture<Void>()
                                    }
                                }
                        } else {
                            // 如果文件不存在，需要同步
                            syncResource(path, info, region)
                        }
                    }
                    .onSuccess {
                        // 成功同步一个资源
                    }
                    .onFailure { cause ->
                        logger.error("同步资源失败: {}", path, cause)
                    }
                    .let { futures.add(it) }
            } else {
                // 完全同步，直接同步资源
                syncResource(path, info, region)
                    .onSuccess {
                        // 成功同步一个资源
                    }
                    .onFailure { cause ->
                        logger.error("同步资源失败: {}", path, cause)
                    }
                    .let { futures.add(it) }
            }
        }

        Future.all(futures)
            .map { null as Void? }
            .onSuccess {
                // 更新同步状态
                syncStatus[regionId] = JsonObject()
                    .put("lastSync", System.currentTimeMillis())
                    .put("status", "success")
                    .put("message", "同步成功")

                logger.info("区域同步成功: {}", regionId)
                promise.complete()
            }
            .onFailure { cause ->
                // 更新同步状态
                syncStatus[regionId] = JsonObject()
                    .put("lastSync", System.currentTimeMillis())
                    .put("status", "failed")
                    .put("message", "同步失败: ${cause.message}")

                logger.error("区域同步失败: {}", regionId, cause)
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 同步资源
     *
     * @param path 资源路径
     * @param info 资源信息
     * @param region 区域信息
     * @return Future<Void> 同步结果
     */
    private fun syncResource(path: String, info: ResourceInfo, region: RegionInfo): Future<Void> {
        val promise = Promise.promise<Void>()

        // 源路径
        val sourcePath = "${resourceManager.resourceRoot.get()}/$path"

        // 目标路径
        val targetPath = "${region.path}/$path"

        // 创建目标目录
        val targetDir = File(targetPath).parent
        fs.mkdirs(targetDir)
            .compose {
                // 复制文件
                fs.copy(sourcePath, targetPath, io.vertx.core.file.CopyOptions().setReplaceExisting(true))
            }
            .compose { _ ->
                // 如果有指纹版本，也复制
                val fingerprintPath = info.fingerprintPath
                if (fingerprintPath != null) {
                    val sourceFingerprint = "${resourceManager.cacheRoot.get()}/$fingerprintPath"
                    val targetFingerprint = "${region.path}/$fingerprintPath"
                    fs.copy(sourceFingerprint, targetFingerprint, io.vertx.core.file.CopyOptions().setReplaceExisting(true))
                } else {
                    Future.succeededFuture<Void>()
                }
            }
            .compose { _ ->
                // 如果有gzip版本，也复制
                val gzipPath = info.gzipPath
                if (gzipPath != null) {
                    val sourceGzip = "${resourceManager.cacheRoot.get()}/$gzipPath"
                    val targetGzip = "${region.path}/$gzipPath"
                    fs.copy(sourceGzip, targetGzip, io.vertx.core.file.CopyOptions().setReplaceExisting(true))
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
     * 预热资源
     *
     * @return Future<Void> 预热结果
     */
    private fun prewarmResources(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 获取预热配置
        val prewarmConfig = distributionConfig.get().getJsonObject("prewarm", JsonObject())
        val patterns = prewarmConfig.getJsonArray("patterns", JsonArray())

        if (patterns.isEmpty) {
            logger.info("未配置预热模式")
            promise.complete()
            return promise.future()
        }

        // 获取所有资源
        val resources = resourceManager.getAllResources()

        // 筛选需要预热的资源
        val prewarmResources = resources.filter { (path, _) ->
            patterns.any { pattern ->
                val patternStr = pattern.toString()
                path.matches(Regex(patternStr))
            }
        }

        if (prewarmResources.isEmpty()) {
            logger.info("没有匹配的资源需要预热")
            promise.complete()
            return promise.future()
        }

        // 预热每个资源
        val futures = mutableListOf<Future<Void>>()

        for ((path, info) in prewarmResources) {
            // 预热资源
            prewarmResource(path, info)
                .onSuccess {
                    logger.debug("资源预热成功: {}", path)
                }
                .onFailure { cause ->
                    logger.error("资源预热失败: {}", path, cause)
                }
                .let { futures.add(it) }
        }

        Future.all(futures)
            .map { null as Void? }
            .onSuccess {
                logger.info("资源预热完成，共预热 {} 个资源", prewarmResources.size)
                promise.complete()
            }
            .onFailure { cause ->
                logger.error("资源预热失败", cause)
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 预热资源
     *
     * @param path 资源路径
     * @param info 资源信息
     * @return Future<Void> 预热结果
     */
    private fun prewarmResource(path: String, info: ResourceInfo): Future<Void> {
        val promise = Promise.promise<Void>()

        // 在实际实现中，这里应该调用CDN的预热API
        // 这里只是一个示例，直接返回成功

        promise.complete()

        return promise.future()
    }

    /**
     * 获取区域信息
     *
     * @param regionId 区域ID
     * @return RegionInfo? 区域信息
     */
    fun getRegionInfo(regionId: String): RegionInfo? {
        return regions[regionId]
    }

    /**
     * 获取所有区域
     *
     * @return Map<String, RegionInfo> 区域映射表
     */
    fun getAllRegions(): Map<String, RegionInfo> {
        return regions.toMap()
    }

    /**
     * 获取同步状态
     *
     * @param regionId 区域ID
     * @return JsonObject 同步状态
     */
    fun getSyncStatus(regionId: String): JsonObject {
        return syncStatus[regionId] ?: JsonObject()
            .put("status", "unknown")
            .put("message", "未知区域")
    }

    /**
     * 获取所有同步状态
     *
     * @return Map<String, JsonObject> 同步状态映射表
     */
    fun getAllSyncStatus(): Map<String, JsonObject> {
        return syncStatus.toMap()
    }

    /**
     * 手动同步区域
     *
     * @param regionId 区域ID
     * @param fullSync 是否完全同步
     * @return Future<Void> 同步结果
     */
    fun manualSyncRegion(regionId: String, fullSync: Boolean): Future<Void> {
        if (!distributionEnabled.get()) {
            return Future.failedFuture("资源分发功能已禁用")
        }

        return syncRegion(regionId, fullSync)
    }

    /**
     * 手动同步所有区域
     *
     * @param fullSync 是否完全同步
     * @return Future<Void> 同步结果
     */
    fun manualSyncAllRegions(fullSync: Boolean): Future<Void> {
        val promise = Promise.promise<Void>()

        if (!distributionEnabled.get()) {
            return Future.failedFuture("资源分发功能已禁用")
        }

        val futures = mutableListOf<Future<Void>>()

        for ((id, _) in regions) {
            syncRegion(id, fullSync)
                .onSuccess {
                    logger.info("区域手动同步成功: {}", id)
                }
                .onFailure { cause ->
                    logger.error("区域手动同步失败: {}", id, cause)
                }
                .let { futures.add(it) }
        }

        Future.all(futures)
            .map { null as Void? }
            .onSuccess {
                promise.complete()
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 手动预热资源
     *
     * @return Future<Void> 预热结果
     */
    fun manualPrewarmResources(): Future<Void> {
        if (!distributionEnabled.get()) {
            return Future.failedFuture("资源分发功能已禁用")
        }

        return prewarmResources()
    }

    /**
     * 获取资源分发状态
     *
     * @return JsonObject 资源分发状态
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", distributionEnabled.get())
            .put("regionCount", regions.size)

        val regionsArray = JsonArray()
        for ((id, region) in regions) {
            regionsArray.add(JsonObject()
                .put("id", id)
                .put("name", region.name)
                .put("path", region.path)
                .put("syncStatus", syncStatus[id])
            )
        }

        status.put("regions", regionsArray)

        return status
    }

    /**
     * 更新资源分发配置
     *
     * @param config 新的资源分发配置
     * @return Future<Void> 更新结果
     */
    fun updateConfig(config: JsonObject): Future<Void> {
        logger.info("更新资源分发配置")

        // 停止定时器
        if (syncTimerId != -1L) {
            vertx.cancelTimer(syncTimerId)
            syncTimerId = -1L
        }

        if (prewarmTimerId != -1L) {
            vertx.cancelTimer(prewarmTimerId)
            prewarmTimerId = -1L
        }

        // 清空区域列表和同步状态
        regions.clear()
        syncStatus.clear()

        // 重新初始化
        return initialize(config)
    }

    /**
     * 关闭资源分发器
     *
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void> {
        logger.info("关闭资源分发器")

        // 停止定时器
        if (syncTimerId != -1L) {
            vertx.cancelTimer(syncTimerId)
            syncTimerId = -1L
        }

        if (prewarmTimerId != -1L) {
            vertx.cancelTimer(prewarmTimerId)
            prewarmTimerId = -1L
        }

        // 清空区域列表和同步状态
        regions.clear()
        syncStatus.clear()

        return Future.succeededFuture()
    }
}

/**
 * 区域信息
 */
data class RegionInfo(
    val id: String,
    val name: String,
    val path: String
)
