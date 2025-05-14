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

    // 缓存预热配置
    private var preloadPatterns = listOf<Regex>()
    private var preloadMaxKeys = 1000
    private var preloadInterval = 3600000L // 默认1小时
    private var preloadTimerId: Long = -1

    // 缓存一致性配置
    private var consistencyCheckInterval = 300000L // 默认5分钟
    private var consistencyTimerId: Long = -1

    /**
     * 配置缓存预热
     * @param patterns 预热的键模式列表
     * @param maxKeys 每次预热的最大键数
     * @param intervalMs 预热间隔（毫秒）
     */
    fun configurePreload(
        patterns: List<String>,
        maxKeys: Int = 1000,
        intervalMs: Long = 3600000L
    ) {
        this.preloadPatterns = patterns.map { Regex(it) }
        this.preloadMaxKeys = maxKeys
        this.preloadInterval = intervalMs
        logger.info("配置缓存预热: patterns=$patterns, maxKeys=$maxKeys, interval=${intervalMs}ms")
    }

    /**
     * 配置缓存一致性检查
     * @param intervalMs 一致性检查间隔（毫秒）
     */
    fun configureConsistencyCheck(intervalMs: Long = 300000L) {
        this.consistencyCheckInterval = intervalMs
        logger.info("配置缓存一致性检查: interval=${intervalMs}ms")
    }

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

                // 启动缓存预热定时器（如果配置了预热模式）
                if (preloadPatterns.isNotEmpty()) {
                    startPreloadTimer()
                }

                // 启动缓存一致性检查定时器
                startConsistencyCheckTimer()

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
            val pattern = notification.getString("pattern")

            if (action != null) {
                when (action) {
                    "remove" -> {
                        if (key != null) {
                            // 从本地缓存中移除
                            localCache.remove(key)
                            logger.debug("收到缓存失效通知，已从本地缓存中移除: $key")
                        }
                    }
                    "clear" -> {
                        // 清除本地缓存
                        localCache.clear()
                        logger.debug("收到缓存清除通知，已清除本地缓存")
                    }
                    "pattern_remove" -> {
                        if (pattern != null) {
                            try {
                                val regex = Regex(pattern)
                                // 移除所有匹配模式的键
                                val keysToRemove = localCache.keys.filter { regex.matches(it) }
                                keysToRemove.forEach { localCache.remove(it) }
                                logger.debug("收到模式失效通知，已从本地缓存中移除 ${keysToRemove.size} 个键，模式: $pattern")
                            } catch (e: Exception) {
                                logger.error("处理模式失效通知失败，无效的正则表达式: $pattern", e)
                            }
                        }
                    }
                    "preload" -> {
                        // 触发缓存预热
                        if (preloadPatterns.isNotEmpty()) {
                            preloadCache()
                        }
                    }
                    "consistency_check" -> {
                        // 触发一致性检查
                        checkCacheConsistency()
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("解析缓存失效通知失败: $message", e)
        }
    }

    /**
     * 发送缓存失效通知
     * @param action 操作类型（"remove", "clear", "pattern_remove", "preload", "consistency_check"）
     * @param key 缓存键（对于某些操作可为 null）
     * @param pattern 模式（对于 pattern_remove 操作）
     * @return 操作结果的 Future
     */
    fun sendCacheNotification(action: String, key: String? = null, pattern: String? = null): Future<Void> {
        val promise = Promise.promise<Void>()

        val notification = JsonObject()
            .put("action", action)

        if (key != null) {
            notification.put("key", key)
        }

        if (pattern != null) {
            notification.put("pattern", pattern)
        }

        redis.publish(pubSubChannel, notification.encode())
            .onSuccess { _ ->
                logger.debug("发送缓存通知成功: $action, key=$key, pattern=$pattern")
                promise.complete()
            }
            .onFailure { err ->
                logger.error("发送缓存通知失败: $action, key=$key, pattern=$pattern", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * 启动缓存预热定时器
     */
    private fun startPreloadTimer() {
        // 取消现有定时器
        if (preloadTimerId != -1L) {
            vertx.cancelTimer(preloadTimerId)
        }

        // 初次预热
        preloadCache()

        // 设置定期预热
        preloadTimerId = vertx.setPeriodic(preloadInterval) {
            preloadCache()
        }

        logger.info("启动缓存预热定时器，间隔: ${preloadInterval}ms")
    }

    /**
     * 启动缓存一致性检查定时器
     */
    private fun startConsistencyCheckTimer() {
        // 取消现有定时器
        if (consistencyTimerId != -1L) {
            vertx.cancelTimer(consistencyTimerId)
        }

        // 设置定期一致性检查
        consistencyTimerId = vertx.setPeriodic(consistencyCheckInterval) {
            checkCacheConsistency()
        }

        logger.info("启动缓存一致性检查定时器，间隔: ${consistencyCheckInterval}ms")
    }

    /**
     * 预热缓存
     */
    private fun preloadCache() {
        if (preloadPatterns.isEmpty()) {
            return
        }

        logger.debug("开始缓存预热操作")

        // 对每个预热模式进行处理
        for (pattern in preloadPatterns) {
            preloadCacheForPattern(pattern.pattern)
        }
    }

    /**
     * 为指定模式预热缓存
     * @param pattern 键模式
     */
    private fun preloadCacheForPattern(pattern: String) {
        // 使用 SCAN 命令查找匹配的键
        scanKeys("$keyPrefix$pattern", preloadMaxKeys)
            .onSuccess { keys ->
                if (keys.isEmpty()) {
                    logger.debug("没有找到匹配模式的键: $pattern")
                    return@onSuccess
                }

                logger.debug("找到 ${keys.size} 个匹配模式的键: $pattern")

                // 批量获取这些键的值
                val futures = mutableListOf<Future<Pair<String, JsonObject?>>>()

                for (redisKey in keys) {
                    // 从 Redis 键中提取原始键（去除前缀）
                    val originalKey = redisKey.substring(keyPrefix.length)

                    // 如果本地缓存中已有该键，则跳过
                    if (localCache.containsKey(originalKey)) {
                        continue
                    }

                    // 获取值并存入本地缓存
                    val future = redis.get(redisKey)
                        .map { response ->
                            if (response != null) {
                                try {
                                    val jsonValue = JsonObject(response.toString())
                                    // 存入本地缓存
                                    localCache[originalKey] = jsonValue
                                    Pair(originalKey, jsonValue)
                                } catch (e: Exception) {
                                    logger.error("解析 JSON 失败，键: $originalKey", e)
                                    Pair(originalKey, null)
                                }
                            } else {
                                Pair(originalKey, null)
                            }
                        }

                    futures.add(future)
                }

                // 等待所有获取操作完成
                Future.all(futures)
                    .onSuccess { _ ->
                        val successCount = futures.count { it.succeeded() && it.result().second != null }
                        logger.info("缓存预热完成，模式: $pattern，成功预热 $successCount/${futures.size} 个键")
                    }
                    .onFailure { err ->
                        logger.error("缓存预热失败，模式: $pattern", err)
                    }
            }
            .onFailure { err ->
                logger.error("扫描匹配模式的键失败: $pattern", err)
            }
    }

    /**
     * 检查缓存一致性
     */
    private fun checkCacheConsistency() {
        logger.debug("开始缓存一致性检查")

        // 随机选择一部分本地缓存键进行检查
        val keysToCheck = localCache.keys.shuffled().take(100)

        if (keysToCheck.isEmpty()) {
            logger.debug("本地缓存为空，无需进行一致性检查")
            return
        }

        val futures = mutableListOf<Future<Pair<String, Boolean>>>()

        for (key in keysToCheck) {
            val redisKey = keyPrefix + key

            // 检查键是否存在于 Redis 中
            val future = redis.exists(listOf(redisKey))
                .map { response ->
                    val exists = response != null && response.toInteger() == 1
                    Pair(key, exists)
                }

            futures.add(future)
        }

        // 等待所有检查完成
        Future.all(futures)
            .onSuccess { _ ->
                val inconsistentKeys = futures.filter { it.succeeded() && !it.result().second }.map { it.result().first }

                if (inconsistentKeys.isNotEmpty()) {
                    logger.info("发现 ${inconsistentKeys.size} 个不一致的缓存键，正在从本地缓存中移除")

                    // 从本地缓存中移除不一致的键
                    for (key in inconsistentKeys) {
                        localCache.remove(key)
                    }
                } else {
                    logger.debug("缓存一致性检查完成，所有检查的键都一致")
                }
            }
            .onFailure { err ->
                logger.error("缓存一致性检查失败", err)
            }
    }

    /**
     * 使用 SCAN 命令扫描匹配的键
     * @param pattern 键模式
     * @param maxKeys 最大返回键数
     * @return 匹配的键列表
     */
    private fun scanKeys(pattern: String, maxKeys: Int): Future<List<String>> {
        val promise = Promise.promise<List<String>>()
        val keys = mutableListOf<String>()
        var cursor = "0"

        fun scan() {
            redis.scan(listOf(cursor, "MATCH", pattern, "COUNT", "100"))
                .onSuccess { response ->
                    if (response != null && response.size() >= 2) {
                        cursor = response.get(0).toString()

                        // 添加扫描到的键
                        val scanKeys = response.get(1)
                        if (scanKeys != null && scanKeys.size() > 0) {
                            for (i in 0 until scanKeys.size()) {
                                keys.add(scanKeys.get(i).toString())

                                // 如果达到最大键数，则停止扫描
                                if (keys.size >= maxKeys) {
                                    promise.complete(keys)
                                    return@onSuccess
                                }
                            }
                        }

                        // 如果有更多的键，继续扫描
                        if (cursor != "0") {
                            scan()
                        } else {
                            promise.complete(keys)
                        }
                    } else {
                        promise.complete(keys)
                    }
                }
                .onFailure { err ->
                    logger.error("使用 SCAN 命令扫描键失败", err)
                    promise.fail(err)
                }
        }

        scan()
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

    /**
     * 根据模式移除缓存项
     * @param pattern 要移除的缓存键模式
     * @return 操作结果的 Future
     */
    fun removeByPattern(pattern: String): Future<Void> {
        val promise = Promise.promise<Void>()
        val redisKeyPattern = keyPrefix + pattern

        // 从本地缓存中移除匹配的键
        try {
            val regex = Regex(pattern)
            val keysToRemove = localCache.keys.filter { regex.matches(it) }
            keysToRemove.forEach { localCache.remove(it) }
            logger.debug("从本地缓存中移除了 ${keysToRemove.size} 个匹配模式的键: $pattern")
        } catch (e: Exception) {
            logger.error("处理模式失败，无效的正则表达式: $pattern", e)
        }

        // 使用 SCAN 命令查找匹配的键
        scanKeys(redisKeyPattern, Int.MAX_VALUE)
            .onSuccess { keys ->
                if (keys.isEmpty()) {
                    logger.debug("没有找到匹配模式的键: $pattern")
                    promise.complete()
                    return@onSuccess
                }

                logger.debug("找到 ${keys.size} 个匹配模式的键: $pattern")

                // 从 Redis 中批量移除这些键
                redis.del(keys)
                    .onSuccess {
                        removes.addAndGet(keys.size.toLong())

                        // 发送模式失效通知
                        sendCacheNotification("pattern_remove", null, pattern)
                            .onSuccess {
                                promise.complete()
                            }
                            .onFailure { err ->
                                logger.warn("Failed to send pattern cache notification: $pattern", err)
                                promise.complete() // 仍然完成操作，只是无法通知其他节点
                            }
                    }
                    .onFailure { err ->
                        logger.error("Error removing cache entries from Redis for pattern: $pattern", err)
                        promise.fail(err)
                    }
            }
            .onFailure { err ->
                logger.error("Error scanning keys for pattern: $pattern", err)
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
            // 取消定时器
            if (preloadTimerId != -1L) {
                vertx.cancelTimer(preloadTimerId)
                preloadTimerId = -1L
            }

            if (consistencyTimerId != -1L) {
                vertx.cancelTimer(consistencyTimerId)
                consistencyTimerId = -1L
            }

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
