package com.louloulin.apix.cache.semantic

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import com.louloulin.apix.core.common.EventBusAddresses

/**
 * 语义缓存管理器，负责管理分布式环境下的语义缓存。
 * 实现plan7.md中的1.3.3节"语义缓存增强"功能。
 */
class SemanticCacheManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SemanticCacheManager::class.java)

    // 单例实例
    companion object {
        @Volatile
        private var instance: SemanticCacheManager? = null

        fun getInstance(vertx: Vertx): SemanticCacheManager {
            return instance ?: synchronized(this) {
                instance ?: SemanticCacheManager(vertx).also { instance = it }
            }
        }
    }

    // 缓存配置
    private val cacheConfig = AtomicReference<JsonObject>(JsonObject())

    // 缓存版本
    private val cacheVersion = AtomicLong(0)

    // 语义缓存
    private val semanticCache = ConcurrentHashMap<String, CacheEntry>()

    // 向量索引
    private val vectorIndex = VectorIndex()

    // 分片管理器
    private val shardManager = ShardManager(vertx)

    // 同步管理器
    private val syncManager = CacheSyncManager(vertx)

    /**
     * 初始化语义缓存管理器。
     *
     * @param config 缓存配置
     * @return Future<Void>
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化语义缓存管理器")

        val promise = Promise.promise<Void>()

        try {
            // 保存配置
            this.cacheConfig.set(config)

            // 初始化向量索引
            val vectorConfig = config.getJsonObject("vector", JsonObject())
            vectorIndex.initialize(vectorConfig)

            // 初始化分片管理器
            val shardConfig = config.getJsonObject("shard", JsonObject())
            shardManager.initialize(shardConfig)
                .compose { _ ->
                    // 初始化同步管理器
                    val syncConfig = config.getJsonObject("sync", JsonObject())
                    syncManager.initialize(syncConfig)
                }
                .onSuccess { _ ->
                    // 注册事件总线处理器
                    registerEventBusHandlers()

                    logger.info("语义缓存管理器初始化完成")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("语义缓存管理器初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("语义缓存管理器初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 注册事件总线处理器。
     */
    private fun registerEventBusHandlers() {
        // 处理语义查询请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_QUERY) { message ->
            val request = message.body()
            val query = request.getString("query")
            val threshold = request.getDouble("threshold", 0.7)

            if (query == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing query parameter")
                )
                return@consumer
            }

            semanticQuery(query, threshold)
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

        // 处理语义缓存请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_STORE) { message ->
            val request = message.body()
            val query = request.getString("query")
            val result = request.getJsonObject("result")
            val vector = request.getJsonArray("vector")

            if (query == null || result == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing required parameters")
                )
                return@consumer
            }

            semanticStore(query, result, vector)
                .onSuccess { _ ->
                    message.reply(JsonObject()
                        .put("success", true)
                    )
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", cause.message)
                    )
                }
        }

        // 处理语义缓存失效请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_INVALIDATE) { message ->
            val request = message.body()
            val key = request.getString("key")

            if (key == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing key parameter")
                )
                return@consumer
            }

            semanticInvalidate(key)
                .onSuccess { _ ->
                    message.reply(JsonObject()
                        .put("success", true)
                    )
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", cause.message)
                    )
                }
        }

        // 处理语义缓存同步请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_SYNC) { message ->
            val request = message.body()
            val targetRegion = request.getString("targetRegion")
            val fromVersion = request.getLong("fromVersion", 0L)

            if (targetRegion == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing targetRegion parameter")
                )
                return@consumer
            }

            syncCache(targetRegion, fromVersion)
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

        // 处理向量索引分片请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_SHARD) { message ->
            val request = message.body()
            val action = request.getString("action")

            if (action == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing action parameter")
                )
                return@consumer
            }

            when (action) {
                "rebalance" -> {
                    rebalanceShards()
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
                "status" -> {
                    val status = getShardStatus()
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("status", status)
                    )
                }
                else -> {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "Unknown action: $action")
                    )
                }
            }
        }
    }

    /**
     * 语义查询。
     *
     * @param query 查询
     * @param threshold 相似度阈值
     * @return Future<JsonObject> 查询结果
     */
    fun semanticQuery(query: String, threshold: Double): Future<JsonObject> {
        logger.debug("语义查询: $query, 阈值: $threshold")

        val promise = Promise.promise<JsonObject>()

        try {
            // 生成查询向量
            generateVector(query)
                .compose { queryVector ->
                    // 在本地分片中查询
                    val localShardIds = shardManager.getLocalShardIds()
                    val localResults = mutableListOf<Future<JsonObject>>()

                    for (shardId in localShardIds) {
                        localResults.add(queryLocalShard(shardId, queryVector, threshold))
                    }

                    // 在远程分片中查询
                    val remoteShardIds = shardManager.getRemoteShardIds()
                    val remoteResults = mutableListOf<Future<JsonObject>>()

                    for (shardId in remoteShardIds) {
                        remoteResults.add(queryRemoteShard(shardId, query, threshold))
                    }

                    // 合并所有结果
                    val allResults = localResults + remoteResults
                    Future.all(allResults).map { results ->
                        mergeQueryResults(results.list())
                    }
                }
                .onSuccess { result ->
                    promise.complete(result)
                }
                .onFailure { cause ->
                    logger.error("语义查询失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("语义查询失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 在本地分片中查询。
     *
     * @param shardId 分片ID
     * @param queryVector 查询向量
     * @param threshold 相似度阈值
     * @return Future<JsonObject> 查询结果
     */
    private fun queryLocalShard(shardId: String, queryVector: JsonArray, threshold: Double): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        try {
            // 在向量索引中查询
            val matches = vectorIndex.search(shardId, queryVector, threshold)
            val results = JsonArray()

            for (match in matches) {
                val key = match.key
                val similarity = match.similarity

                // 从缓存中获取结果
                val cacheEntry = semanticCache[key]
                if (cacheEntry != null) {
                    results.add(JsonObject()
                        .put("key", key)
                        .put("similarity", similarity)
                        .put("result", cacheEntry.result)
                        .put("timestamp", cacheEntry.timestamp)
                    )
                }
            }

            promise.complete(JsonObject()
                .put("shardId", shardId)
                .put("results", results)
            )
        } catch (e: Exception) {
            logger.error("本地分片查询失败: $shardId", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 在远程分片中查询。
     *
     * @param shardId 分片ID
     * @param query 查询
     * @param threshold 相似度阈值
     * @return Future<JsonObject> 查询结果
     */
    private fun queryRemoteShard(shardId: String, query: String, threshold: Double): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // 获取分片所在节点
        val nodeId = shardManager.getShardNodeId(shardId)
        if (nodeId == null) {
            promise.fail("分片 $shardId 的节点未找到")
            return promise.future()
        }

        // 构建请求
        val request = JsonObject()
            .put("shardId", shardId)
            .put("query", query)
            .put("threshold", threshold)

        // 发送请求到远程节点
        vertx.eventBus().request<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_QUERY_SHARD, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getJsonObject("result", JsonObject()))
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("远程分片查询失败: $shardId", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 合并查询结果。
     *
     * @param results 查询结果列表
     * @return JsonObject 合并后的结果
     */
    private fun mergeQueryResults(results: List<JsonObject>): JsonObject {
        // 按相似度排序的结果
        val sortedResults = mutableListOf<JsonObject>()

        // 合并所有分片的结果
        for (result in results) {
            val shardResults = result.getJsonArray("results", JsonArray())
            for (i in 0 until shardResults.size()) {
                val item = shardResults.getJsonObject(i)
                sortedResults.add(item)
            }
        }

        // 按相似度降序排序
        sortedResults.sortByDescending { it.getDouble("similarity") }

        // 构建最终结果
        val finalResults = JsonArray()
        for (item in sortedResults) {
            finalResults.add(item)
        }

        return JsonObject()
            .put("count", finalResults.size())
            .put("results", finalResults)
    }

    /**
     * 存储语义缓存。
     *
     * @param query 查询
     * @param result 结果
     * @param vector 向量
     * @return Future<Void>
     */
    fun semanticStore(query: String, result: JsonObject, vector: JsonArray?): Future<Void> {
        logger.debug("存储语义缓存: $query")

        val promise = Promise.promise<Void>()

        try {
            // 生成缓存键
            val key = generateCacheKey(query)

            // 如果没有提供向量，则生成向量
            val vectorFuture = if (vector != null) {
                Future.succeededFuture(vector)
            } else {
                generateVector(query)
            }

            vectorFuture
                .compose { queryVector ->
                    // 创建缓存条目
                    val timestamp = System.currentTimeMillis()
                    val cacheEntry = CacheEntry(query, result, queryVector, timestamp)

                    // 存储到缓存
                    semanticCache[key] = cacheEntry

                    // 确定分片
                    val shardId = shardManager.getShardForKey(key)

                    // 添加到向量索引
                    vectorIndex.add(shardId, key, queryVector)

                    // 增加缓存版本
                    val newVersion = cacheVersion.incrementAndGet()

                    // 记录缓存操作
                    val operation = CacheOperation(
                        type = CacheOperationType.STORE,
                        key = key,
                        query = query,
                        result = result,
                        vector = queryVector,
                        timestamp = timestamp,
                        version = newVersion
                    )

                    // 同步到其他区域
                    syncManager.recordOperation(operation)
                        .compose { _ ->
                            // 检查是否需要重新平衡分片
                            if (shardManager.needRebalance()) {
                                rebalanceShards()
                            } else {
                                Future.succeededFuture()
                            }
                        }
                }
                .onSuccess { _ ->
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("存储语义缓存失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("存储语义缓存失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 使语义缓存失效。
     *
     * @param key 缓存键
     * @return Future<Void>
     */
    fun semanticInvalidate(key: String): Future<Void> {
        logger.debug("使语义缓存失效: $key")

        val promise = Promise.promise<Void>()

        try {
            // 从缓存中移除
            val cacheEntry = semanticCache.remove(key)
            if (cacheEntry != null) {
                // 确定分片
                val shardId = shardManager.getShardForKey(key)

                // 从向量索引中移除
                vectorIndex.remove(shardId, key)

                // 增加缓存版本
                val newVersion = cacheVersion.incrementAndGet()

                // 记录缓存操作
                val operation = CacheOperation(
                    type = CacheOperationType.INVALIDATE,
                    key = key,
                    query = cacheEntry.query,
                    result = null,
                    vector = null,
                    timestamp = System.currentTimeMillis(),
                    version = newVersion
                )

                // 同步到其他区域
                syncManager.recordOperation(operation)
                    .onSuccess { _ ->
                        promise.complete()
                    }
                    .onFailure { cause ->
                        logger.error("同步缓存操作失败", cause)
                        promise.fail(cause)
                    }
            } else {
                // 缓存条目不存在，直接返回成功
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("使语义缓存失效失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 同步缓存到目标区域。
     *
     * @param targetRegion 目标区域
     * @param fromVersion 起始版本
     * @return Future<JsonObject> 同步结果
     */
    fun syncCache(targetRegion: String, fromVersion: Long): Future<JsonObject> {
        logger.debug("同步缓存到区域: $targetRegion, 起始版本: $fromVersion")

        return syncManager.syncToRegion(targetRegion, fromVersion)
    }

    /**
     * 重新平衡分片。
     *
     * @return Future<JsonObject> 重新平衡结果
     */
    fun rebalanceShards(): Future<JsonObject> {
        logger.debug("重新平衡分片")

        val promise = Promise.promise<JsonObject>()

        try {
            // 获取当前分片分配
            val currentShards = shardManager.getAllShards()

            // 计算新的分片分配
            shardManager.rebalance()
                .compose { newShards ->
                    // 获取需要迁移的分片
                    val migratingShards = mutableListOf<String>()
                    for (shardId in newShards.keys) {
                        val oldNodeId = currentShards[shardId]
                        val newNodeId = newShards[shardId]
                        if (oldNodeId != newNodeId) {
                            migratingShards.add(shardId)
                        }
                    }

                    // 迁移分片
                    val migrations = mutableListOf<Future<Void>>()
                    for (shardId in migratingShards) {
                        val targetNodeId = newShards[shardId]
                        if (targetNodeId != null) {
                            migrations.add(migrateShard(shardId, targetNodeId))
                        }
                    }

                    Future.all(migrations).map {
                        JsonObject()
                            .put("migratedShards", migratingShards.size)
                            .put("totalShards", newShards.size)
                            .put("shards", JsonObject(newShards))
                    }
                }
                .onSuccess { result ->
                    promise.complete(result)
                }
                .onFailure { cause ->
                    logger.error("重新平衡分片失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("重新平衡分片失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 迁移分片。
     *
     * @param shardId 分片ID
     * @param targetNodeId 目标节点ID
     * @return Future<Void>
     */
    private fun migrateShard(shardId: String, targetNodeId: String): Future<Void> {
        logger.debug("迁移分片: $shardId 到节点: $targetNodeId")

        val promise = Promise.promise<Void>()

        try {
            // 获取分片数据
            val shardData = vectorIndex.getShardData(shardId)

            // 构建请求
            val request = JsonObject()
                .put("shardId", shardId)
                .put("data", shardData)

            // 发送请求到目标节点
            vertx.eventBus().request<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_MIGRATE_SHARD, request) { ar ->
                if (ar.succeeded()) {
                    val response = ar.result().body()

                    if (response.getBoolean("success", false)) {
                        // 更新分片分配
                        shardManager.assignShard(shardId, targetNodeId)
                            .onSuccess { _ ->
                                promise.complete()
                            }
                            .onFailure { cause ->
                                promise.fail(cause)
                            }
                    } else {
                        promise.fail(response.getString("error", "Unknown error"))
                    }
                } else {
                    logger.error("迁移分片失败: $shardId", ar.cause())
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            logger.error("迁移分片失败: $shardId", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取分片状态。
     *
     * @return JsonObject 分片状态
     */
    fun getShardStatus(): JsonObject {
        val shards = shardManager.getAllShards()
        val localShards = shardManager.getLocalShardIds()
        val remoteShards = shardManager.getRemoteShardIds()

        val localShardSizes = mutableMapOf<String, Int>()
        for (shardId in localShards) {
            localShardSizes[shardId] = vectorIndex.getShardSize(shardId)
        }

        return JsonObject()
            .put("totalShards", shards.size)
            .put("localShards", localShards.size)
            .put("remoteShards", remoteShards.size)
            .put("shardAssignment", JsonObject(shards))
            .put("localShardSizes", JsonObject(localShardSizes))
    }

    /**
     * 获取缓存状态。
     *
     * @return JsonObject 缓存状态
     */
    fun getStatus(): JsonObject {
        return JsonObject()
            .put("cacheSize", semanticCache.size)
            .put("cacheVersion", cacheVersion.get())
            .put("vectorIndexSize", vectorIndex.getTotalSize())
            .put("shardStatus", getShardStatus())
            .put("syncStatus", syncManager.getStatus())
    }

    /**
     * 生成缓存键。
     *
     * @param query 查询
     * @return String 缓存键
     */
    private fun generateCacheKey(query: String): String {
        return "semantic:${query.hashCode()}"
    }

    /**
     * 生成向量。
     *
     * @param text 文本
     * @return Future<JsonArray> 向量
     */
    private fun generateVector(text: String): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()

        // 构建请求
        val request = JsonObject()
            .put("text", text)

        // 发送请求到向量生成服务
        vertx.eventBus().request<JsonObject>(EventBusAddresses.VECTOR_GENERATE, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getJsonArray("vector"))
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("生成向量失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 缓存条目类。
     */
    data class CacheEntry(
        val query: String,
        val result: JsonObject,
        val vector: JsonArray,
        val timestamp: Long
    )

    /**
     * 缓存操作类型。
     */
    enum class CacheOperationType {
        STORE,
        INVALIDATE
    }

    /**
     * 缓存操作类。
     */
    data class CacheOperation(
        val type: CacheOperationType,
        val key: String,
        val query: String,
        val result: JsonObject?,
        val vector: JsonArray?,
        val timestamp: Long,
        val version: Long
    )
}
