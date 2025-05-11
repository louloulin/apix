package com.louloulin.apix.edge.sync

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.louloulin.apix.core.common.EventBusAddresses
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 边缘同步管理器，负责边缘节点和中心节点之间的数据同步。
 * 实现plan7.md中的2.2.2节"数据同步"功能。
 */
class EdgeSyncManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(EdgeSyncManager::class.java)

    // 单例实例
    companion object {
        @Volatile
        private var instance: EdgeSyncManager? = null

        fun getInstance(vertx: Vertx): EdgeSyncManager {
            return instance ?: synchronized(this) {
                instance ?: EdgeSyncManager(vertx).also { instance = it }
            }
        }
    }

    // 同步配置
    private val syncConfig = AtomicReference<JsonObject>(JsonObject())

    // 同步是否启用
    private val syncEnabled = AtomicBoolean(false)

    // 上次同步时间
    private val lastSyncTime = AtomicLong(0)

    // 同步策略
    private val syncStrategy = AtomicReference<SyncStrategy>()

    // 数据版本
    private val dataVersions = ConcurrentHashMap<String, Long>()

    // 同步状态
    private val syncStatus = ConcurrentHashMap<String, SyncStatus>()

    // 带宽监控器
    private val bandwidthMonitor = BandwidthMonitor(vertx)

    // 网络条件检测器
    private val networkDetector = NetworkConditionDetector(vertx)

    // 差异计算器
    private val diffCalculator = DataDiffCalculator()

    // 冲突解决器
    private val conflictResolver = ConflictResolver()

    // 断点续传管理器
    private val resumableTransferManager = ResumableTransferManager(vertx)

    /**
     * 初始化边缘同步管理器。
     *
     * @param config 同步配置
     * @return Future<Void>
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化边缘同步管理器")

        val promise = Promise.promise<Void>()

        try {
            // 保存配置
            this.syncConfig.set(config)

            // 获取同步启用状态
            val enabled = config.getBoolean("enabled", true)
            this.syncEnabled.set(enabled)

            if (!enabled) {
                logger.info("边缘同步功能已禁用")
                promise.complete()
                return promise.future()
            }

            // 初始化同步策略
            initSyncStrategy(config)

            // 注册事件总线处理器
            registerEventBusHandlers()

            // 启动定时同步任务
            startPeriodicSync()

            // 启动带宽监控
            bandwidthMonitor.start()

            // 启动网络条件检测
            networkDetector.start()

            logger.info("边缘同步管理器初始化完成")
            promise.complete()
        } catch (e: Exception) {
            logger.error("边缘同步管理器初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 初始化同步策略。
     *
     * @param config 同步配置
     */
    private fun initSyncStrategy(config: JsonObject) {
        val strategyConfig = config.getJsonObject("strategy", JsonObject())
        val strategyType = strategyConfig.getString("type", "incremental")

        val strategy = when (strategyType) {
            "incremental" -> IncrementalSyncStrategy(vertx, strategyConfig)
            "bandwidth-aware" -> BandwidthAwareSyncStrategy(vertx, strategyConfig, bandwidthMonitor, networkDetector)
            else -> IncrementalSyncStrategy(vertx, strategyConfig)
        }

        syncStrategy.set(strategy)
        logger.info("已初始化同步策略: $strategyType")
    }

    /**
     * 注册事件总线处理器。
     */
    private fun registerEventBusHandlers() {
        // 处理同步请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_SYNC_REQUEST) { message ->
            val request = message.body()
            val dataType = request.getString("dataType")
            val version = request.getLong("version", 0L)

            if (dataType == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing dataType parameter")
                )
                return@consumer
            }

            syncData(dataType, version)
                .onSuccess { result ->
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", result)
                    )
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", cause.message)
                    )
                }
        }

        // 处理数据差异请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_SYNC_DIFF) { message ->
            val request = message.body()
            val dataType = request.getString("dataType")
            val baseVersion = request.getLong("baseVersion", 0L)
            val targetVersion = request.getLong("targetVersion", 0L)

            if (dataType == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing dataType parameter")
                )
                return@consumer
            }

            calculateDiff(dataType, baseVersion, targetVersion)
                .onSuccess { diff ->
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("diff", diff)
                    )
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", cause.message)
                    )
                }
        }

        // 处理数据合并请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_SYNC_MERGE) { message ->
            val request = message.body()
            val dataType = request.getString("dataType")
            val baseData = request.getJsonObject("baseData")
            val diff = request.getJsonObject("diff")

            if (dataType == null || baseData == null || diff == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing required parameters")
                )
                return@consumer
            }

            applyDiff(dataType, baseData, diff)
                .onSuccess { mergedData ->
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("mergedData", mergedData)
                    )
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", cause.message)
                    )
                }
        }

        // 处理压缩请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_SYNC_COMPRESS) { message ->
            val request = message.body()
            val data = request.getJsonObject("data")
            val level = request.getInteger("level", Deflater.DEFAULT_COMPRESSION)

            if (data == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing data parameter")
                )
                return@consumer
            }

            val compressed = compressData(data.encode(), level)
            message.reply(JsonObject()
                .put("success", true)
                .put("compressed", java.util.Base64.getEncoder().encodeToString(compressed.bytes))
            )
        }

        // 处理解压请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_SYNC_DECOMPRESS) { message ->
            val request = message.body()
            val compressedBase64 = request.getString("compressed")

            if (compressedBase64 == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing compressed parameter")
                )
                return@consumer
            }

            try {
                val compressed = Buffer.buffer(java.util.Base64.getDecoder().decode(compressedBase64))
                val decompressed = decompressData(compressed)
                val jsonData = JsonObject(decompressed)

                message.reply(JsonObject()
                    .put("success", true)
                    .put("data", jsonData)
                )
            } catch (e: Exception) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Failed to decompress data: ${e.message}")
                )
            }
        }

        // 处理断点续传请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_SYNC_RESUME) { message ->
            val request = message.body()
            val transferId = request.getString("transferId")
            val offset = request.getLong("offset", 0L)

            if (transferId == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing transferId parameter")
                )
                return@consumer
            }

            resumeTransfer(transferId, offset)
                .onSuccess { result ->
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", result)
                    )
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", cause.message)
                    )
                }
        }
    }

    /**
     * 启动定时同步任务。
     */
    private fun startPeriodicSync() {
        val syncInterval = syncConfig.get().getLong("syncInterval", 60000L)

        vertx.setPeriodic(syncInterval) { _ ->
            if (syncEnabled.get()) {
                // 检查网络条件
                val networkCondition = networkDetector.getCurrentCondition()

                // 根据网络条件决定是否执行同步
                if (shouldSyncBasedOnNetworkCondition(networkCondition)) {
                    // 执行同步
                    syncAllData()
                } else {
                    logger.info("当前网络条件不适合同步，跳过本次同步。网络条件: $networkCondition")
                }
            }
        }

        logger.info("已启动定时同步任务，间隔: ${syncInterval}ms")
    }

    /**
     * 根据网络条件决定是否执行同步。
     *
     * @param condition 网络条件
     * @return 是否应该执行同步
     */
    private fun shouldSyncBasedOnNetworkCondition(condition: NetworkCondition): Boolean {
        // 如果网络不可用，不执行同步
        if (condition.status == NetworkStatus.UNAVAILABLE) {
            return false
        }

        // 如果网络状况良好，执行同步
        if (condition.status == NetworkStatus.GOOD) {
            return true
        }

        // 如果网络状况一般，根据带宽使用情况决定
        if (condition.status == NetworkStatus.FAIR) {
            val currentBandwidthUsage = bandwidthMonitor.getCurrentBandwidthUsage()
            val maxBandwidthUsage = syncConfig.get().getDouble("maxBandwidthUsage", 0.7)

            return currentBandwidthUsage.usageRatio < maxBandwidthUsage
        }

        // 如果网络状况差，只有在紧急情况下执行同步
        if (condition.status == NetworkStatus.POOR) {
            val lastSync = lastSyncTime.get()
            val currentTime = System.currentTimeMillis()
            val maxSyncDelay = syncConfig.get().getLong("maxSyncDelay", 3600000L) // 默认1小时

            return (currentTime - lastSync) > maxSyncDelay
        }

        return false
    }

    /**
     * 同步所有数据。
     */
    private fun syncAllData() {
        logger.info("开始同步所有数据")

        // 获取需要同步的数据类型
        val dataTypes = listOf("routes", "services", "plugins", "consumers", "certificates")

        // 对每种数据类型执行同步
        for (dataType in dataTypes) {
            syncData(dataType, dataVersions.getOrDefault(dataType, 0L))
                .onSuccess { result ->
                    logger.info("数据类型 $dataType 同步成功: $result")
                }
                .onFailure { cause ->
                    logger.error("数据类型 $dataType 同步失败", cause)
                }
        }

        // 更新最后同步时间
        lastSyncTime.set(System.currentTimeMillis())
    }

    /**
     * 同步指定类型的数据。
     *
     * @param dataType 数据类型
     * @param currentVersion 当前版本
     * @return Future<JsonObject> 同步结果
     */
    fun syncData(dataType: String, currentVersion: Long): Future<JsonObject> {
        logger.info("开始同步数据类型: $dataType, 当前版本: $currentVersion")

        val promise = Promise.promise<JsonObject>()

        // 创建同步状态
        val status = SyncStatus(dataType, currentVersion)
        syncStatus[dataType] = status

        // 使用当前同步策略执行同步
        syncStrategy.get().sync(dataType, currentVersion)
            .onSuccess { result ->
                // 更新数据版本
                val newVersion = result.getLong("version", currentVersion)
                dataVersions[dataType] = newVersion

                // 更新同步状态
                status.complete(newVersion)

                // 返回结果
                promise.complete(result)
            }
            .onFailure { cause ->
                // 更新同步状态
                status.fail(cause)

                // 返回错误
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 计算数据差异。
     *
     * @param dataType 数据类型
     * @param baseVersion 基础版本
     * @param targetVersion 目标版本
     * @return Future<JsonObject> 差异结果
     */
    fun calculateDiff(dataType: String, baseVersion: Long, targetVersion: Long): Future<JsonObject> {
        logger.info("计算数据差异: $dataType, 基础版本: $baseVersion, 目标版本: $targetVersion")

        val promise = Promise.promise<JsonObject>()

        // 获取基础数据
        getDataByVersion(dataType, baseVersion)
            .compose { baseData ->
                // 获取目标数据
                getDataByVersion(dataType, targetVersion)
                    .map { targetData -> Pair(baseData, targetData) }
            }
            .onSuccess { (baseData, targetData) ->
                // 计算差异
                val diff = diffCalculator.calculateDiff(baseData, targetData)
                promise.complete(diff)
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 应用数据差异。
     *
     * @param dataType 数据类型
     * @param baseData 基础数据
     * @param diff 差异数据
     * @return Future<JsonObject> 合并后的数据
     */
    fun applyDiff(dataType: String, baseData: JsonObject, diff: JsonObject): Future<JsonObject> {
        logger.info("应用数据差异: $dataType")

        val promise = Promise.promise<JsonObject>()

        try {
            // 应用差异
            val mergedData = diffCalculator.applyDiff(baseData, diff)

            // 检查冲突
            val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

            if (conflicts.isEmpty()) {
                // 无冲突，直接返回合并结果
                promise.complete(mergedData)
            } else {
                // 有冲突，解决冲突
                val resolvedData = conflictResolver.resolveConflicts(baseData, mergedData, conflicts)
                promise.complete(resolvedData)
            }
        } catch (e: Exception) {
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取指定版本的数据。
     *
     * @param dataType 数据类型
     * @param version 版本
     * @return Future<JsonObject> 数据
     */
    private fun getDataByVersion(dataType: String, version: Long): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // 构建请求
        val request = JsonObject()
            .put("dataType", dataType)
            .put("version", version)

        // 发送请求到控制平面
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_DATA_BY_VERSION, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getJsonObject("data", JsonObject()))
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 压缩数据。
     *
     * @param data 要压缩的数据
     * @param level 压缩级别
     * @return Buffer 压缩后的数据
     */
    fun compressData(data: String, level: Int = Deflater.DEFAULT_COMPRESSION): Buffer {
        val input = data.toByteArray(Charsets.UTF_8)
        val deflater = Deflater(level)
        deflater.setInput(input)
        deflater.finish()

        val outputStream = ByteArrayOutputStream(input.size)
        val buffer = ByteArray(1024)

        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        outputStream.close()
        deflater.end()

        return Buffer.buffer(outputStream.toByteArray())
    }

    /**
     * 解压数据。
     *
     * @param compressedData 压缩的数据
     * @return String 解压后的数据
     */
    fun decompressData(compressedData: Buffer): String {
        val input = compressedData.bytes
        val inflater = Inflater()
        inflater.setInput(input)

        val outputStream = ByteArrayOutputStream(input.size * 2)
        val buffer = ByteArray(1024)

        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            outputStream.write(buffer, 0, count)
        }

        outputStream.close()
        inflater.end()

        return outputStream.toString(Charsets.UTF_8.name())
    }

    /**
     * 恢复传输。
     *
     * @param transferId 传输ID
     * @param offset 偏移量
     * @return Future<JsonObject> 恢复结果
     */
    fun resumeTransfer(transferId: String, offset: Long): Future<JsonObject> {
        return resumableTransferManager.resumeTransfer(transferId, offset)
    }

    /**
     * 获取同步状态。
     *
     * @return Map<String, SyncStatus> 同步状态
     */
    fun getSyncStatus(): Map<String, SyncStatus> {
        return syncStatus.toMap()
    }

    /**
     * 获取数据版本。
     *
     * @return Map<String, Long> 数据版本
     */
    fun getDataVersions(): Map<String, Long> {
        return dataVersions.toMap()
    }

    /**
     * 获取带宽使用情况。
     *
     * @return BandwidthUsage 带宽使用情况
     */
    fun getBandwidthUsage(): BandwidthUsage {
        return bandwidthMonitor.getCurrentBandwidthUsage()
    }

    /**
     * 获取网络条件。
     *
     * @return NetworkCondition 网络条件
     */
    fun getNetworkCondition(): NetworkCondition {
        return networkDetector.getCurrentCondition()
    }

    /**
     * 获取同步配置。
     *
     * @return JsonObject 同步配置
     */
    fun getConfig(): JsonObject {
        return syncConfig.get()
    }

    /**
     * 同步状态类。
     */
    data class SyncStatus(
        val dataType: String,
        val startVersion: Long,
        var endVersion: Long = 0,
        var status: String = "SYNCING",
        var error: String? = null,
        val startTime: Long = System.currentTimeMillis(),
        var endTime: Long = 0
    ) {
        /**
         * 完成同步。
         *
         * @param version 新版本
         */
        fun complete(version: Long) {
            endVersion = version
            status = "COMPLETED"
            endTime = System.currentTimeMillis()
        }

        /**
         * 同步失败。
         *
         * @param cause 失败原因
         */
        fun fail(cause: Throwable) {
            status = "FAILED"
            error = cause.message
            endTime = System.currentTimeMillis()
        }

        /**
         * 获取同步持续时间（毫秒）。
         *
         * @return 持续时间
         */
        fun getDuration(): Long {
            return if (endTime > 0) endTime - startTime else System.currentTimeMillis() - startTime
        }
    }
}
