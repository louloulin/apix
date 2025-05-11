package com.louloulin.apix.cache.semantic

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.louloulin.apix.core.common.EventBusAddresses

/**
 * 分片管理器，负责管理向量索引的分片。
 */
class ShardManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ShardManager::class.java)

    // 分片配置
    private var config = JsonObject()

    // 本地节点ID
    private var localNodeId = ""

    // 分片分配
    private val shardAssignment = ConcurrentHashMap<String, String>()

    // 分片大小
    private val shardSizes = ConcurrentHashMap<String, AtomicInteger>()

    // 是否需要重新平衡
    private val needRebalance = AtomicBoolean(false)

    /**
     * 初始化分片管理器。
     *
     * @param config 分片配置
     * @return Future<Void>
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化分片管理器")

        val promise = Promise.promise<Void>()

        try {
            // 保存配置
            this.config = config

            // 获取本地节点ID
            getLocalNodeId()
                .compose { nodeId ->
                    localNodeId = nodeId

                    // 获取当前分片分配
                    getClusterShardAssignment()
                }
                .compose { assignment ->
                    // 更新分片分配
                    shardAssignment.clear()
                    shardAssignment.putAll(assignment)

                    // 如果没有分片，创建初始分片
                    if (shardAssignment.isEmpty()) {
                        createInitialShards()
                    } else {
                        Future.succeededFuture<Void>()
                    }
                }
                .onSuccess { _ ->
                    logger.info("分片管理器初始化完成，本地节点ID: $localNodeId，分片数量: ${shardAssignment.size}")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("分片管理器初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("分片管理器初始化失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取本地节点ID。
     *
     * @return Future<String> 节点ID
     */
    private fun getLocalNodeId(): Future<String> {
        val promise = Promise.promise<String>()

        // 发送请求到集群服务
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_GET_LOCAL_NODE_ID, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getString("nodeId"))
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("获取本地节点ID失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 获取集群分片分配。
     *
     * @return Future<Map<String, String>> 分片分配
     */
    private fun getClusterShardAssignment(): Future<Map<String, String>> {
        val promise = Promise.promise<Map<String, String>>()

        // 发送请求到集群服务
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_GET_SHARD_ASSIGNMENT, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    val assignment = mutableMapOf<String, String>()
                    val assignmentJson = response.getJsonObject("assignment", JsonObject())
                    for (shardId in assignmentJson.fieldNames()) {
                        assignment[shardId] = assignmentJson.getString(shardId)
                    }
                    promise.complete(assignment)
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("获取集群分片分配失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 创建初始分片。
     *
     * @return Future<Void>
     */
    private fun createInitialShards(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 获取初始分片数量
            val initialShardCount = config.getInteger("initialShardCount", 10)

            // 创建初始分片
            for (i in 0 until initialShardCount) {
                val shardId = "shard-$i"
                shardAssignment[shardId] = localNodeId
            }

            // 保存分片分配
            saveShardAssignment()
                .onSuccess { _ ->
                    logger.info("初始分片创建完成，数量: $initialShardCount")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("保存分片分配失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("创建初始分片失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 保存分片分配。
     *
     * @return Future<Void>
     */
    private fun saveShardAssignment(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 构建分片分配
        val assignment = JsonObject()
        for ((shardId, nodeId) in shardAssignment) {
            assignment.put(shardId, nodeId)
        }

        // 构建请求
        val request = JsonObject()
            .put("assignment", assignment)

        // 发送请求到集群服务
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_SET_SHARD_ASSIGNMENT, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete()
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("保存分片分配失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 获取键所在的分片ID。
     *
     * @param key 键
     * @return String 分片ID
     */
    fun getShardForKey(key: String): String {
        // 计算哈希值
        val hash = key.hashCode()

        // 获取分片数量
        val shardCount = shardAssignment.size

        // 计算分片索引
        val shardIndex = Math.abs(hash % shardCount)

        // 获取分片ID
        val shardIds = shardAssignment.keys.toList()
        val shardId = shardIds[shardIndex]

        // 增加分片大小
        val shardSize = shardSizes.computeIfAbsent(shardId) { AtomicInteger(0) }
        val newSize = shardSize.incrementAndGet()

        // 检查是否需要重新平衡
        val maxShardSize = config.getInteger("maxShardSize", 10000)
        if (newSize > maxShardSize) {
            needRebalance.set(true)
        }

        return shardId
    }

    /**
     * 获取分片所在的节点ID。
     *
     * @param shardId 分片ID
     * @return String? 节点ID
     */
    fun getShardNodeId(shardId: String): String? {
        return shardAssignment[shardId]
    }

    /**
     * 分配分片。
     *
     * @param shardId 分片ID
     * @param nodeId 节点ID
     * @return Future<Void>
     */
    fun assignShard(shardId: String, nodeId: String): Future<Void> {
        // 更新分片分配
        shardAssignment[shardId] = nodeId

        // 保存分片分配
        return saveShardAssignment()
    }

    /**
     * 获取本地分片ID列表。
     *
     * @return List<String> 本地分片ID列表
     */
    fun getLocalShardIds(): List<String> {
        return shardAssignment.entries
            .filter { it.value == localNodeId }
            .map { it.key }
    }

    /**
     * 获取远程分片ID列表。
     *
     * @return List<String> 远程分片ID列表
     */
    fun getRemoteShardIds(): List<String> {
        return shardAssignment.entries
            .filter { it.value != localNodeId }
            .map { it.key }
    }

    /**
     * 获取所有分片。
     *
     * @return Map<String, String> 分片分配
     */
    fun getAllShards(): Map<String, String> {
        return shardAssignment.toMap()
    }

    /**
     * 是否需要重新平衡。
     *
     * @return Boolean 是否需要重新平衡
     */
    fun needRebalance(): Boolean {
        return needRebalance.getAndSet(false)
    }

    /**
     * 重新平衡分片。
     *
     * @return Future<Map<String, String>> 新的分片分配
     */
    fun rebalance(): Future<Map<String, String>> {
        logger.info("重新平衡分片")

        val promise = Promise.promise<Map<String, String>>()

        try {
            // 获取集群节点
            getClusterNodes()
                .compose { nodes ->
                    // 如果没有节点，直接返回当前分片分配
                    if (nodes.isEmpty()) {
                        return@compose Future.succeededFuture(shardAssignment.toMap())
                    }

                    // 计算每个节点应该分配的分片数量
                    val shardCount = shardAssignment.size
                    val nodeCount = nodes.size
                    val shardsPerNode = shardCount / nodeCount
                    val extraShards = shardCount % nodeCount

                    // 创建新的分片分配
                    val newAssignment = mutableMapOf<String, String>()
                    val shardIds = shardAssignment.keys.toList()

                    var shardIndex = 0
                    for (i in 0 until nodeCount) {
                        val nodeId = nodes[i]
                        val nodeShardsCount = if (i < extraShards) shardsPerNode + 1 else shardsPerNode

                        for (j in 0 until nodeShardsCount) {
                            if (shardIndex < shardCount) {
                                val shardId = shardIds[shardIndex]
                                newAssignment[shardId] = nodeId
                                shardIndex++
                            }
                        }
                    }

                    // 更新分片分配
                    shardAssignment.clear()
                    shardAssignment.putAll(newAssignment)

                    // 保存分片分配
                    saveShardAssignment()
                        .map { newAssignment }
                }
                .onSuccess { newAssignment ->
                    logger.info("分片重新平衡完成，分片数量: ${newAssignment.size}")
                    promise.complete(newAssignment)
                }
                .onFailure { cause ->
                    logger.error("分片重新平衡失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("分片重新平衡失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取集群节点。
     *
     * @return Future<List<String>> 节点ID列表
     */
    private fun getClusterNodes(): Future<List<String>> {
        val promise = Promise.promise<List<String>>()

        // 发送请求到集群服务
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CLUSTER_GET_NODES, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    val nodes = response.getJsonArray("nodes", io.vertx.core.json.JsonArray())
                    val nodeIds = mutableListOf<String>()
                    for (i in 0 until nodes.size()) {
                        val node = nodes.getJsonObject(i)
                        val nodeId = node.getString("id")
                        nodeIds.add(nodeId)
                    }
                    promise.complete(nodeIds)
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                logger.error("获取集群节点失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }
}
