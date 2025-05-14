package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.redis.client.Command
import io.vertx.redis.client.Redis
import io.vertx.redis.client.RedisAPI
import io.vertx.redis.client.RedisOptions
import io.vertx.redis.client.Request
import io.vertx.redis.client.Response
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 基于 Redis 的缓存管理器实现
 * 支持分布式缓存和缓存一致性机制
 */
class RedisCacheManager(
    private val vertx: Vertx,
    private val redisOptions: RedisOptions,
    private val keyPrefix: String = "apix:cache:",
    private val pubSubChannel: String = "apix:cache:notifications"
) : CacheManager {
    private val logger = LoggerFactory.getLogger(RedisCacheManager::class.java)

    // Redis 客户端
    private val redisClient: Redis = Redis.createClient(vertx, redisOptions)
    private val redis: RedisAPI = RedisAPI.api(redisClient)

    // 缓存统计
    private val hits = AtomicLong(0)
    private val misses = AtomicLong(0)
    private val sets = AtomicLong(0)
    private val removes = AtomicLong(0)

    // 本地缓存，用于减少 Redis 访问
    private val localCache = ConcurrentHashMap<String, JsonObject>()

    // 订阅客户端，用于接收缓存失效通知
    private val subscriberClient: Redis = Redis.createClient(vertx, redisOptions)
    private val subscriber: RedisAPI = RedisAPI.api(subscriberClient)

    // 初始化标志
    private var initialized = false

    /**
     * 初始化缓存管理器
     * @return 初始化结果的 Future
     */
    fun initialize(): Future<Void> {
        val promise = Promise.promise<Void>()

        if (initialized) {
            promise.complete()
            return promise.future()
        }

        // 订阅缓存失效通知
        subscriber.subscribe(listOf(pubSubChannel))
            .onSuccess { _ ->
                logger.info("成功订阅缓存失效通知频道: $pubSubChannel")

                // 设置消息处理器
                setupMessageHandler()

                initialized = true
                promise.complete()
            }
            .onFailure { err ->
                logger.error("订阅缓存失效通知频道失败: $pubSubChannel", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * 设置消息处理器
     */
    private fun setupMessageHandler() {
        // 使用 Redis 客户端的消息处理器
        subscriberClient.connect().onSuccess { conn ->
            conn.handler { response ->
                try {
                    if (response.type().toString() == "PUSH") {
                    // 处理 Redis 推送消息
                    val pushArray = response.toString().split(" ")
                    if (pushArray.size >= 3 && pushArray[0] == "message") {
                        val channel = pushArray[1]
                        val message = pushArray[2]

                        if (channel == pubSubChannel) {
                            handleCacheNotification(message)
                        }
                    }
                }
                } catch (e: Exception) {
                    logger.error("处理 Redis 消息失败", e)
                }
            }
        }
    }

    /**
     * 处理缓存失效通知
     * @param message 消息内容
     */
    private fun handleCacheNotification(message: String) {
        try {
            val notification = JsonObject(message)
            val action = notification.getString("action")
            val key = notification.getString("key")

            if (action != null && key != null) {
                when (action) {
                    "remove" -> {
                        // 从本地缓存中移除
                        localCache.remove(key)
                        logger.debug("收到缓存失效通知，已从本地缓存中移除: $key")
                    }
                    "clear" -> {
                        // 清除本地缓存
                        localCache.clear()
                        logger.debug("收到缓存清除通知，已清除本地缓存")
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("解析缓存失效通知失败: $message", e)
        }
    }

    /**
     * 发送缓存失效通知
     * @param action 操作类型（"remove" 或 "clear"）
     * @param key 缓存键（对于 "clear" 操作可为 null）
     * @return 操作结果的 Future
     */
    private fun sendCacheNotification(action: String, key: String? = null): Future<Void> {
        val promise = Promise.promise<Void>()

        val notification = JsonObject()
            .put("action", action)

        if (key != null) {
            notification.put("key", key)
        }

        redis.publish(pubSubChannel, notification.encode())
            .onSuccess { _ ->
                logger.debug("发送缓存失效通知成功: $action, $key")
                promise.complete()
            }
            .onFailure { err ->
                logger.error("发送缓存失效通知失败: $action, $key", err)
                promise.fail(err)
            }

        return promise.future()
    }

    override fun get(key: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        val redisKey = keyPrefix + key

        // 首先检查本地缓存
        val localValue = localCache[key]
        if (localValue != null) {
            hits.incrementAndGet()
            promise.complete(localValue)
            return promise.future()
        }

        // 本地缓存未命中，从 Redis 获取
        redis.get(redisKey)
            .onSuccess { response ->
                if (response != null) {
                    try {
                        val jsonValue = JsonObject(response.toString())
                        hits.incrementAndGet()

                        // 存入本地缓存
                        localCache[key] = jsonValue

                        promise.complete(jsonValue)
                    } catch (e: Exception) {
                        logger.error("Error parsing JSON from Redis for key: $key", e)
                        promise.fail(e)
                    }
                } else {
                    misses.incrementAndGet()
                    promise.complete(null)
                }
            }
            .onFailure { err ->
                logger.error("Error getting cache entry from Redis for key: $key", err)
                promise.fail(err)
            }

        return promise.future()
    }

    override fun set(key: String, value: JsonObject, ttlSeconds: Long): Future<Void> {
        val promise = Promise.promise<Void>()
        val redisKey = keyPrefix + key

        try {
            val jsonString = value.encode()

            // 存入本地缓存
            localCache[key] = value

            if (ttlSeconds > 0) {
                // 使用 SETEX 命令设置带过期时间的键值对
                redis.setex(redisKey, ttlSeconds.toString(), jsonString)
                    .onSuccess {
                        sets.incrementAndGet()
                        promise.complete()
                    }
                    .onFailure { err ->
                        // 如果 Redis 设置失败，从本地缓存中移除
                        localCache.remove(key)
                        logger.error("Error setting cache entry in Redis for key: $key", err)
                        promise.fail(err)
                    }
            } else {
                // 使用 SET 命令设置不过期的键值对
                redis.set(listOf(redisKey, jsonString))
                    .onSuccess {
                        sets.incrementAndGet()
                        promise.complete()
                    }
                    .onFailure { err ->
                        // 如果 Redis 设置失败，从本地缓存中移除
                        localCache.remove(key)
                        logger.error("Error setting cache entry in Redis for key: $key", err)
                        promise.fail(err)
                    }
            }
        } catch (e: Exception) {
            logger.error("Error encoding JSON for Redis for key: $key", e)
            promise.fail(e)
        }

        return promise.future()
    }

    override fun remove(key: String): Future<Void> {
        val promise = Promise.promise<Void>()
        val redisKey = keyPrefix + key

        // 从本地缓存中移除
        localCache.remove(key)

        // 从 Redis 中移除
        redis.del(listOf(redisKey))
            .onSuccess {
                removes.incrementAndGet()

                // 发送缓存失效通知
                sendCacheNotification("remove", key)
                    .onSuccess {
                        promise.complete()
                    }
                    .onFailure { err ->
                        logger.warn("Failed to send cache notification for key: $key", err)
                        promise.complete() // 仍然完成操作，只是无法通知其他节点
                    }
            }
            .onFailure { err ->
                logger.error("Error removing cache entry from Redis for key: $key", err)
                promise.fail(err)
            }

        return promise.future()
    }

    override fun clear(): Future<Void> {
        val promise = Promise.promise<Void>()

        // 清除本地缓存
        localCache.clear()

        // 使用 KEYS 命令查找所有匹配的键（注意：在生产环境中应谨慎使用 KEYS 命令）
        // 更好的方法是使用 SCAN 命令进行分批处理
        redis.keys("$keyPrefix*")
            .onSuccess { response ->
                if (response != null && response.size() > 0) {
                    val keysList = mutableListOf<String>()

                    // 将响应转换为键列表
                    for (i in 0 until response.size()) {
                        keysList.add(response.get(i).toString())
                    }

                    if (keysList.isNotEmpty()) {
                        redis.del(keysList)
                            .onSuccess {
                                // 发送缓存清除通知
                                sendCacheNotification("clear")
                                    .onSuccess {
                                        promise.complete()
                                    }
                                    .onFailure { err ->
                                        logger.warn("Failed to send cache clear notification", err)
                                        promise.complete() // 仍然完成操作，只是无法通知其他节点
                                    }
                            }
                            .onFailure { err ->
                                logger.error("Error deleting keys from Redis", err)
                                promise.fail(err)
                            }
                    } else {
                        promise.complete()
                    }
                } else {
                    promise.complete()
                }
            }
            .onFailure { err ->
                logger.error("Error getting keys from Redis", err)
                promise.fail(err)
            }

        return promise.future()
    }

    override fun getStats(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // 使用 DBSIZE 命令获取数据库大小
        redis.dbsize()
            .onSuccess { response ->
                val dbSize = response.toLong()

                // 使用 INFO 命令获取 Redis 服务器信息
                val infoFuture = redis.info(listOf("server", "memory", "stats"))
                infoFuture.onSuccess { infoResponse ->
                        val redisInfo = parseRedisInfo(infoResponse.toString())

                        val stats = JsonObject()
                            .put("hits", hits.get())
                            .put("misses", misses.get())
                            .put("sets", sets.get())
                            .put("removes", removes.get())
                            .put("hitRatio", if (hits.get() + misses.get() > 0) {
                                hits.get().toDouble() / (hits.get() + misses.get())
                            } else {
                                0.0
                            })
                            .put("dbSize", dbSize)
                            .put("localCacheSize", localCache.size)
                            .put("redisInfo", redisInfo)

                        promise.complete(stats)
                    }
                infoFuture.onFailure { err ->
                    logger.error("Error getting Redis info", err)

                    // 即使无法获取 Redis 信息，仍然返回基本统计信息
                    val stats = JsonObject()
                        .put("hits", hits.get())
                        .put("misses", misses.get())
                        .put("sets", sets.get())
                        .put("removes", removes.get())
                        .put("hitRatio", if (hits.get() + misses.get() > 0) {
                            hits.get().toDouble() / (hits.get() + misses.get())
                        } else {
                            0.0
                        })
                        .put("dbSize", dbSize)
                        .put("localCacheSize", localCache.size)

                    promise.complete(stats)
                }
            }
            .onFailure { err ->
                logger.error("Error getting Redis stats", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * 解析 Redis INFO 命令返回的信息
     * @param infoString INFO 命令返回的字符串
     * @return 解析后的 JsonObject
     */
    private fun parseRedisInfo(infoString: String): JsonObject {
        val result = JsonObject()
        var currentSection = ""

        infoString.lines().forEach { line ->
            if (line.startsWith("#")) {
                // 这是一个新的部分
                currentSection = line.substring(2).trim().lowercase()
                result.put(currentSection, JsonObject())
            } else if (line.contains(":") && currentSection.isNotEmpty()) {
                // 这是一个键值对
                val parts = line.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()

                    val sectionObj = result.getJsonObject(currentSection)
                    if (sectionObj != null) {
                        sectionObj.put(key, value)
                    }
                }
            }
        }

        return result
    }

    override fun close(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 取消订阅
            subscriber.unsubscribe(listOf(pubSubChannel))
                .onSuccess { _ ->
                    // 关闭订阅客户端
                    subscriberClient.close()

                    // 关闭 Redis 客户端
                    redisClient.close()

                    // 清除本地缓存
                    localCache.clear()

                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("Error unsubscribing from Redis channel", err)

                    // 关闭 Redis 客户端
                    try {
                        subscriberClient.close()
                        redisClient.close()
                    } catch (e: Exception) {
                        logger.error("Error closing Redis clients", e)
                    }

                    // 清除本地缓存
                    localCache.clear()

                    promise.fail(err)
                }
        } catch (e: Exception) {
            logger.error("Error closing Redis cache manager", e)
            promise.fail(e)
        }

        return promise.future()
    }
}
