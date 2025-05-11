package com.louloulin.apix.cache.semantic

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import com.louloulin.apix.core.common.EventBusAddresses

/**
 * 缓存同步管理器，负责管理跨区域的缓存同步。
 */
class CacheSyncManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(CacheSyncManager::class.java)

    // 同步配置
    private var config = JsonObject()

    // 本地区域
    private var localRegion = ""

    // 操作日志
    private val operationLog = ConcurrentLinkedQueue<SemanticCacheManager.CacheOperation>()

    // 区域同步状态
    private val regionSyncStatus = ConcurrentHashMap<String, RegionSyncStatus>()

    // 最后同步时间
    private val lastSyncTime = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 初始化缓存同步管理器。
     *
     * @param config 同步配置
     * @return Future<Void>
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化缓存同步管理器")

        val promise = Promise.promise<Void>()

        try {
            // 保存配置
            this.config = config

            // 获取本地区域
            getLocalRegion()
                .compose { region ->
                    localRegion = region

                    // 获取所有区域
                    getRegions()
                }
                .compose { regions ->
                    // 初始化区域同步状态
                    for (region in regions) {
                        if (region != localRegion) {
                            regionSyncStatus[region] = RegionSyncStatus(region)
                            lastSyncTime[region] = AtomicLong(0)
                        }
                    }

                    // 启动定时同步任务
                    startPeriodicSync()

                    Future.succeededFuture<Void>()
                }
                .onSuccess { _ ->
                    logger.info("缓存同步管理器初始化完成，本地区域: $localRegion")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("缓存同步管理器初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("缓存同步管理器初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取本地区域。
     *
     * @return Future<String> 区域
     */
    private fun getLocalRegion(): Future<String> {
        val promise = Promise.promise<String>()

        // 从配置中获取本地区域
        val region = config.getString("localRegion")
        if (region != null) {
            promise.complete(region)
            return promise.future()
        }

        // 发送请求到集群服务
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_GET_LOCAL_REGION, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getString("region"))
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("获取本地区域失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 获取所有区域。
     *
     * @return Future<List<String>> 区域列表
     */
    private fun getRegions(): Future<List<String>> {
        val promise = Promise.promise<List<String>>()

        // 从配置中获取区域
        val regions = config.getJsonArray("regions")
        if (regions != null) {
            val regionList = mutableListOf<String>()
            for (i in 0 until regions.size()) {
                regionList.add(regions.getString(i))
            }
            promise.complete(regionList)
            return promise.future()
        }

        // 发送请求到集群服务
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_GET_REGIONS, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    val regionArray = response.getJsonArray("regions", JsonArray())
                    val regionList = mutableListOf<String>()
                    for (i in 0 until regionArray.size()) {
                        regionList.add(regionArray.getString(i))
                    }
                    promise.complete(regionList)
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("获取区域列表失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 启动定时同步任务。
     */
    private fun startPeriodicSync() {
        // 获取同步间隔
        val syncInterval = config.getLong("syncInterval", 60000L)

        // 启动定时任务
        vertx.setPeriodic(syncInterval) { _ ->
            // 同步到所有区域
            for (region in regionSyncStatus.keys) {
                syncToRegion(region, 0)
                    .onSuccess { result ->
                        logger.debug("同步到区域 $region 成功: $result")
                    }
                    .onFailure { cause ->
                        logger.error("同步到区域 $region 失败", cause)
                    }
            }
        }

        logger.info("定时同步任务已启动，间隔: ${syncInterval}ms")
    }

    /**
     * 记录缓存操作。
     *
     * @param operation 缓存操作
     * @return Future<Void>
     */
    fun recordOperation(operation: SemanticCacheManager.CacheOperation): Future<Void> {
        // 添加到操作日志
        operationLog.add(operation)

        // 限制操作日志大小
        val maxLogSize = config.getInteger("maxLogSize", 10000)
        while (operationLog.size > maxLogSize) {
            operationLog.poll()
        }

        return Future.succeededFuture()
    }

    /**
     * 同步到目标区域。
     *
     * @param targetRegion 目标区域
     * @param fromVersion 起始版本
     * @return Future<JsonObject> 同步结果
     */
    fun syncToRegion(targetRegion: String, fromVersion: Long): Future<JsonObject> {
        logger.debug("同步到区域: $targetRegion, 起始版本: $fromVersion")

        val promise = Promise.promise<JsonObject>()

        try {
            // 获取区域同步状态
            val syncStatus = regionSyncStatus[targetRegion]
            if (syncStatus == null) {
                promise.fail("区域 $targetRegion 不存在")
                return promise.future()
            }

            // 获取需要同步的操作
            val operations = getOperationsAfterVersion(fromVersion)
            if (operations.isEmpty()) {
                // 没有需要同步的操作
                promise.complete(JsonObject()
                    .put("synced", false)
                    .put("message", "No operations to sync")
                )
                return promise.future()
            }

            // 构建同步请求
            val request = JsonObject()
                .put("sourceRegion", localRegion)
                .put("operations", operationsToJson(operations))

            // 发送请求到目标区域
            vertx.eventBus().request<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_SYNC_RECEIVE, request) { ar ->
                if (ar.succeeded()) {
                    val response = ar.result().body()

                    if (response.getBoolean("success", false)) {
                        // 更新同步状态
                        val lastOperation = operations.last()
                        syncStatus.lastSyncedVersion = lastOperation.version
                        syncStatus.lastSyncTime = System.currentTimeMillis()
                        lastSyncTime[targetRegion]?.set(syncStatus.lastSyncTime)

                        promise.complete(JsonObject()
                            .put("synced", true)
                            .put("operationCount", operations.size)
                            .put("lastVersion", lastOperation.version)
                        )
                    } else {
                        promise.fail(response.getString("error", "Unknown error"))
                    }
                } else {
                    logger.error("同步到区域 $targetRegion 失败", ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("同步到区域 $targetRegion 失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取指定版本之后的操作。
     *
     * @param fromVersion 起始版本
     * @return List<SemanticCacheManager.CacheOperation> 操作列表
     */
    private fun getOperationsAfterVersion(fromVersion: Long): List<SemanticCacheManager.CacheOperation> {
        return operationLog
            .filter { it.version > fromVersion }
            .sortedBy { it.version }
    }

    /**
     * 将操作列表转换为JSON。
     *
     * @param operations 操作列表
     * @return JsonArray JSON数组
     */
    private fun operationsToJson(operations: List<SemanticCacheManager.CacheOperation>): JsonArray {
        val jsonArray = JsonArray()
        for (operation in operations) {
            val jsonObject = JsonObject()
                .put("type", operation.type.name)
                .put("key", operation.key)
                .put("query", operation.query)
                .put("timestamp", operation.timestamp)
                .put("version", operation.version)

            if (operation.result != null) {
                jsonObject.put("result", operation.result)
            }

            if (operation.vector != null) {
                jsonObject.put("vector", operation.vector)
            }

            jsonArray.add(jsonObject)
        }
        return jsonArray
    }

    /**
     * 接收同步操作。
     *
     * @param sourceRegion 源区域
     * @param operations 操作列表
     * @return Future<Void>
     */
    fun receiveSync(sourceRegion: String, operations: JsonArray): Future<Void> {
        logger.debug("接收来自区域 $sourceRegion 的同步操作，数量: ${operations.size()}")

        val promise = Promise.promise<Void>()

        try {
            // 解析操作
            val parsedOperations = parseOperations(operations)

            // 应用操作
            applyOperations(parsedOperations)
                .onSuccess { _ ->
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("应用同步操作失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("接收同步操作失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 解析操作。
     *
     * @param operations 操作JSON数组
     * @return List<SemanticCacheManager.CacheOperation> 操作列表
     */
    private fun parseOperations(operations: JsonArray): List<SemanticCacheManager.CacheOperation> {
        val parsedOperations = mutableListOf<SemanticCacheManager.CacheOperation>()
        for (i in 0 until operations.size()) {
            val operationJson = operations.getJsonObject(i)
            val type = SemanticCacheManager.CacheOperationType.valueOf(operationJson.getString("type"))
            val key = operationJson.getString("key")
            val query = operationJson.getString("query")
            val result = operationJson.getJsonObject("result")
            val vector = operationJson.getJsonArray("vector")
            val timestamp = operationJson.getLong("timestamp")
            val version = operationJson.getLong("version")

            val operation = SemanticCacheManager.CacheOperation(
                type = type,
                key = key,
                query = query,
                result = result,
                vector = vector,
                timestamp = timestamp,
                version = version
            )
            parsedOperations.add(operation)
        }
        return parsedOperations
    }

    /**
     * 应用操作。
     *
     * @param operations 操作列表
     * @return Future<Void>
     */
    private fun applyOperations(operations: List<SemanticCacheManager.CacheOperation>): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 按顺序应用操作
            var future = Future.succeededFuture<Void>()
            for (operation in operations) {
                future = future.compose { applyOperation(operation) }
            }

            future
                .onSuccess { _ ->
                    promise.complete()
                }
                .onFailure { cause ->
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 应用单个操作。
     *
     * @param operation 操作
     * @return Future<Void>
     */
    private fun applyOperation(operation: SemanticCacheManager.CacheOperation): Future<Void> {
        when (operation.type) {
            SemanticCacheManager.CacheOperationType.STORE -> {
                // 构建请求
                val request = JsonObject()
                    .put("key", operation.key)
                    .put("query", operation.query)
                    .put("result", operation.result)
                    .put("vector", operation.vector)
                    .put("timestamp", operation.timestamp)
                    .put("version", operation.version)
                    .put("fromSync", true)

                // 发送请求到语义缓存
                return sendRequest(EventBusAddresses.SEMANTIC_CACHE_STORE, request)
            }
            SemanticCacheManager.CacheOperationType.INVALIDATE -> {
                // 构建请求
                val request = JsonObject()
                    .put("key", operation.key)
                    .put("fromSync", true)

                // 发送请求到语义缓存
                return sendRequest(EventBusAddresses.SEMANTIC_CACHE_INVALIDATE, request)
            }
        }
    }

    /**
     * 发送请求。
     *
     * @param address 地址
     * @param request 请求
     * @return Future<Void>
     */
    private fun sendRequest(address: String, request: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()

        vertx.eventBus().request<JsonObject>(address, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete()
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
     * 获取同步状态。
     *
     * @return JsonObject 同步状态
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
        for ((region, syncStatus) in regionSyncStatus) {
            status.put(region, JsonObject()
                .put("lastSyncedVersion", syncStatus.lastSyncedVersion)
                .put("lastSyncTime", syncStatus.lastSyncTime)
                .put("lastSyncTimeFormatted", formatTime(syncStatus.lastSyncTime))
            )
        }
        return status
    }

    /**
     * 格式化时间。
     *
     * @param timestamp 时间戳
     * @return String 格式化后的时间
     */
    private fun formatTime(timestamp: Long): String {
        if (timestamp == 0L) {
            return "Never"
        }
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60000 -> "${diff / 1000} seconds ago"
            diff < 3600000 -> "${diff / 60000} minutes ago"
            diff < 86400000 -> "${diff / 3600000} hours ago"
            else -> "${diff / 86400000} days ago"
        }
    }

    /**
     * 区域同步状态类。
     *
     * @param region 区域
     * @param lastSyncedVersion 最后同步的版本
     * @param lastSyncTime 最后同步时间
     */
    data class RegionSyncStatus(
        val region: String,
        var lastSyncedVersion: Long = 0,
        var lastSyncTime: Long = 0
    )
}
