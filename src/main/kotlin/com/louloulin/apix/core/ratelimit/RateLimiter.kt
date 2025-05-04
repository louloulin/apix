package com.louloulin.apix.core.ratelimit

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 限流器，支持多种限流算法
 */
class RateLimiter(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(RateLimiter::class.java)

    // 限流器配置
    private var cleanupInterval = 60000L // 清理间隔（毫秒）

    // 限流器实例
    private val tokenBucketLimiters = ConcurrentHashMap<String, TokenBucketLimiter>()
    private val slidingWindowLimiters = ConcurrentHashMap<String, SlidingWindowLimiter>()
    private val leakyBucketLimiters = ConcurrentHashMap<String, LeakyBucketLimiter>()

    /**
     * 初始化限流器
     */
    fun initialize(config: JsonObject) {
        // 加载配置
        cleanupInterval = config.getLong("cleanupInterval", cleanupInterval)

        // 启动清理任务
        startCleanupTask()

        // 注册事件总线处理器
        registerEventBusHandlers()

        logger.info("初始化限流器完成，清理间隔: ${cleanupInterval}ms")
    }

    /**
     * 启动清理任务
     */
    private fun startCleanupTask() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    // 清理过期的限流器
                    cleanupExpiredLimiters()

                    delay(cleanupInterval)
                } catch (e: Exception) {
                    logger.error("清理过期限流器异常", e)
                    delay(cleanupInterval)
                }
            }
        }
    }

    /**
     * 清理过期的限流器
     */
    private fun cleanupExpiredLimiters() {
        val now = System.currentTimeMillis()

        // 清理令牌桶限流器
        tokenBucketLimiters.entries.removeIf { (_, limiter) ->
            val expired = limiter.isExpired(now)
            if (expired) {
                logger.debug("清理过期的令牌桶限流器: ${limiter.id}")
            }
            expired
        }

        // 清理滑动窗口限流器
        slidingWindowLimiters.entries.removeIf { (_, limiter) ->
            val expired = limiter.isExpired(now)
            if (expired) {
                logger.debug("清理过期的滑动窗口限流器: ${limiter.id}")
            }
            expired
        }

        // 清理漏桶限流器
        leakyBucketLimiters.entries.removeIf { (_, limiter) ->
            val expired = limiter.isExpired(now)
            if (expired) {
                logger.debug("清理过期的漏桶限流器: ${limiter.id}")
            }
            expired
        }
    }

    /**
     * 注册事件总线处理器
     */
    private fun registerEventBusHandlers() {
        // 注册限流检查处理器
        vertx.eventBus().consumer<JsonObject>("apix.ratelimit.check") { message ->
            val body = message.body()
            val key = body.getString("key")
            val type = body.getString("type", "token_bucket")
            val tokens = body.getInteger("tokens", 1)

            if (key == null) {
                message.fail(400, "Missing required field: key")
                return@consumer
            }

            // 检查是否允许通过
            val allowed = when (type) {
                "token_bucket" -> checkTokenBucket(key, tokens, body)
                "sliding_window" -> checkSlidingWindow(key, tokens, body)
                "leaky_bucket" -> checkLeakyBucket(key, tokens, body)
                else -> {
                    message.fail(400, "Invalid rate limit type: $type")
                    return@consumer
                }
            }

            // 返回结果
            message.reply(JsonObject()
                .put("allowed", allowed)
                .put("key", key)
                .put("type", type)
                .put("tokens", tokens)
                .put("timestamp", System.currentTimeMillis())
            )
        }

        // 注册限流器创建处理器
        vertx.eventBus().consumer<JsonObject>("apix.ratelimit.create") { message ->
            val body = message.body()
            val key = body.getString("key")
            val type = body.getString("type", "token_bucket")

            if (key == null) {
                message.fail(400, "Missing required field: key")
                return@consumer
            }

            // 创建限流器
            val created = when (type) {
                "token_bucket" -> createTokenBucket(key, body)
                "sliding_window" -> createSlidingWindow(key, body)
                "leaky_bucket" -> createLeakyBucket(key, body)
                else -> {
                    message.fail(400, "Invalid rate limit type: $type")
                    return@consumer
                }
            }

            // 返回结果
            message.reply(JsonObject()
                .put("created", created)
                .put("key", key)
                .put("type", type)
                .put("timestamp", System.currentTimeMillis())
            )
        }

        // 注册限流器删除处理器
        vertx.eventBus().consumer<JsonObject>("apix.ratelimit.delete") { message ->
            val body = message.body()
            val key = body.getString("key")
            val type = body.getString("type")

            if (key == null) {
                message.fail(400, "Missing required field: key")
                return@consumer
            }

            // 删除限流器
            val deleted = if (type == null) {
                // 删除所有类型的限流器
                deleteTokenBucket(key) || deleteSlidingWindow(key) || deleteLeakyBucket(key)
            } else {
                // 删除指定类型的限流器
                when (type) {
                    "token_bucket" -> deleteTokenBucket(key)
                    "sliding_window" -> deleteSlidingWindow(key)
                    "leaky_bucket" -> deleteLeakyBucket(key)
                    else -> {
                        message.fail(400, "Invalid rate limit type: $type")
                        return@consumer
                    }
                }
            }

            // 返回结果
            message.reply(JsonObject()
                .put("deleted", deleted)
                .put("key", key)
                .put("type", type)
                .put("timestamp", System.currentTimeMillis())
            )
        }

        // 注册限流器状态查询处理器
        vertx.eventBus().consumer<JsonObject>("apix.ratelimit.status") { message ->
            val body = message.body()
            val key = body.getString("key")
            val type = body.getString("type")

            if (key == null) {
                message.fail(400, "Missing required field: key")
                return@consumer
            }

            // 获取限流器状态
            val status = if (type == null) {
                // 获取所有类型的限流器状态
                JsonObject()
                    .put("token_bucket", getTokenBucketStatus(key))
                    .put("sliding_window", getSlidingWindowStatus(key))
                    .put("leaky_bucket", getLeakyBucketStatus(key))
            } else {
                // 获取指定类型的限流器状态
                when (type) {
                    "token_bucket" -> getTokenBucketStatus(key)
                    "sliding_window" -> getSlidingWindowStatus(key)
                    "leaky_bucket" -> getLeakyBucketStatus(key)
                    else -> {
                        message.fail(400, "Invalid rate limit type: $type")
                        return@consumer
                    }
                }
            }

            // 返回结果
            message.reply(status)
        }
    }

    /**
     * 检查令牌桶限流器
     */
    fun checkTokenBucket(key: String, tokens: Int, config: JsonObject): Boolean {
        // 获取或创建令牌桶限流器
        val limiter = tokenBucketLimiters[key] ?: createTokenBucket(key, config)

        // 检查是否允许通过
        return limiter.tryAcquire(tokens)
    }

    /**
     * 创建令牌桶限流器
     */
    fun createTokenBucket(key: String, config: JsonObject): TokenBucketLimiter {
        val capacity = config.getInteger("capacity", 100)
        val refillRate = config.getDouble("refillRate", 1.0)
        val refillInterval = config.getLong("refillInterval", 1000L)
        val initialTokens = config.getInteger("initialTokens", capacity)
        val ttl = config.getLong("ttl", 3600000L)

        val limiter = TokenBucketLimiter(
            id = key,
            capacity = capacity,
            refillRate = refillRate,
            refillInterval = refillInterval,
            initialTokens = initialTokens,
            ttl = ttl
        )

        tokenBucketLimiters[key] = limiter
        logger.debug("创建令牌桶限流器: $key, 容量: $capacity, 填充速率: $refillRate, 填充间隔: ${refillInterval}ms")

        return limiter
    }

    /**
     * 删除令牌桶限流器
     */
    fun deleteTokenBucket(key: String): Boolean {
        val removed = tokenBucketLimiters.remove(key) != null
        if (removed) {
            logger.debug("删除令牌桶限流器: $key")
        }
        return removed
    }

    /**
     * 获取令牌桶限流器状态
     */
    fun getTokenBucketStatus(key: String): JsonObject {
        val limiter = tokenBucketLimiters[key]

        return if (limiter != null) {
            JsonObject()
                .put("exists", true)
                .put("tokens", limiter.getTokens())
                .put("capacity", limiter.capacity)
                .put("refillRate", limiter.refillRate)
                .put("refillInterval", limiter.refillInterval)
                .put("lastRefillTime", limiter.lastRefillTime)
                .put("ttl", limiter.ttl)
                .put("creationTime", limiter.creationTime)
                .put("expirationTime", limiter.creationTime + limiter.ttl)
        } else {
            JsonObject()
                .put("exists", false)
        }
    }

    /**
     * 检查滑动窗口限流器
     */
    fun checkSlidingWindow(key: String, tokens: Int, config: JsonObject): Boolean {
        // 获取或创建滑动窗口限流器
        val limiter = slidingWindowLimiters[key] ?: createSlidingWindow(key, config)

        // 检查是否允许通过
        return limiter.tryAcquire(tokens)
    }

    /**
     * 创建滑动窗口限流器
     */
    fun createSlidingWindow(key: String, config: JsonObject): SlidingWindowLimiter {
        val limit = config.getInteger("limit", 100)
        val windowSize = config.getLong("windowSize", 60000L)
        val precision = config.getInteger("precision", 10)
        val ttl = config.getLong("ttl", 3600000L)

        val limiter = SlidingWindowLimiter(
            id = key,
            limit = limit,
            windowSize = windowSize,
            precision = precision,
            ttl = ttl
        )

        slidingWindowLimiters[key] = limiter
        logger.debug("创建滑动窗口限流器: $key, 限制: $limit, 窗口大小: ${windowSize}ms, 精度: $precision")

        return limiter
    }

    /**
     * 删除滑动窗口限流器
     */
    fun deleteSlidingWindow(key: String): Boolean {
        val removed = slidingWindowLimiters.remove(key) != null
        if (removed) {
            logger.debug("删除滑动窗口限流器: $key")
        }
        return removed
    }

    /**
     * 获取滑动窗口限流器状态
     */
    fun getSlidingWindowStatus(key: String): JsonObject {
        val limiter = slidingWindowLimiters[key]

        return if (limiter != null) {
            JsonObject()
                .put("exists", true)
                .put("count", limiter.getCount())
                .put("limit", limiter.limit)
                .put("windowSize", limiter.windowSize)
                .put("precision", limiter.precision)
                .put("ttl", limiter.ttl)
                .put("creationTime", limiter.creationTime)
                .put("expirationTime", limiter.creationTime + limiter.ttl)
        } else {
            JsonObject()
                .put("exists", false)
        }
    }

    /**
     * 检查漏桶限流器
     */
    fun checkLeakyBucket(key: String, tokens: Int, config: JsonObject): Boolean {
        // 获取或创建漏桶限流器
        val limiter = leakyBucketLimiters[key] ?: createLeakyBucket(key, config)

        // 检查是否允许通过
        return limiter.tryAcquire(tokens)
    }

    /**
     * 创建漏桶限流器
     */
    fun createLeakyBucket(key: String, config: JsonObject): LeakyBucketLimiter {
        val capacity = config.getInteger("capacity", 100)
        val leakRate = config.getDouble("leakRate", 1.0)
        val leakInterval = config.getLong("leakInterval", 1000L)
        val ttl = config.getLong("ttl", 3600000L)

        val limiter = LeakyBucketLimiter(
            id = key,
            capacity = capacity,
            leakRate = leakRate,
            leakInterval = leakInterval,
            ttl = ttl
        )

        leakyBucketLimiters[key] = limiter
        logger.debug("创建漏桶限流器: $key, 容量: $capacity, 漏出速率: $leakRate, 漏出间隔: ${leakInterval}ms")

        return limiter
    }

    /**
     * 删除漏桶限流器
     */
    fun deleteLeakyBucket(key: String): Boolean {
        val removed = leakyBucketLimiters.remove(key) != null
        if (removed) {
            logger.debug("删除漏桶限流器: $key")
        }
        return removed
    }

    /**
     * 获取漏桶限流器状态
     */
    fun getLeakyBucketStatus(key: String): JsonObject {
        val limiter = leakyBucketLimiters[key]

        return if (limiter != null) {
            JsonObject()
                .put("exists", true)
                .put("water", limiter.getWater())
                .put("capacity", limiter.capacity)
                .put("leakRate", limiter.leakRate)
                .put("leakInterval", limiter.leakInterval)
                .put("lastLeakTime", limiter.lastLeakTime)
                .put("ttl", limiter.ttl)
                .put("creationTime", limiter.creationTime)
                .put("expirationTime", limiter.creationTime + limiter.ttl)
        } else {
            JsonObject()
                .put("exists", false)
        }
    }

    /**
     * 令牌桶限流器
     */
    class TokenBucketLimiter(
        val id: String,
        val capacity: Int,
        val refillRate: Double,
        val refillInterval: Long,
        initialTokens: Int,
        val ttl: Long
    ) {
        private val tokens = AtomicLong(initialTokens.toLong())
        var lastRefillTime = System.currentTimeMillis()
            private set
        val creationTime = System.currentTimeMillis()

        /**
         * 尝试获取令牌
         */
        @Synchronized
        fun tryAcquire(numTokens: Int): Boolean {
            refill()

            val currentTokens = tokens.get()
            if (currentTokens >= numTokens) {
                tokens.addAndGet(-numTokens.toLong())
                return true
            }

            return false
        }

        /**
         * 填充令牌
         */
        private fun refill() {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRefillTime

            if (elapsed >= refillInterval) {
                val intervalsElapsed = elapsed / refillInterval
                val tokensToAdd = (intervalsElapsed * refillRate).toLong()

                if (tokensToAdd > 0) {
                    val newTokens = Math.min(capacity.toLong(), tokens.get() + tokensToAdd)
                    tokens.set(newTokens)
                    lastRefillTime = now
                }
            }
        }

        /**
         * 获取当前令牌数
         */
        fun getTokens(): Long {
            refill()
            return tokens.get()
        }

        /**
         * 检查是否过期
         */
        fun isExpired(now: Long): Boolean {
            return now - creationTime > ttl
        }
    }

    /**
     * 滑动窗口限流器
     */
    class SlidingWindowLimiter(
        val id: String,
        val limit: Int,
        val windowSize: Long,
        val precision: Int,
        val ttl: Long
    ) {
        private val buckets = ConcurrentHashMap<Long, AtomicLong>()
        val creationTime = System.currentTimeMillis()

        /**
         * 尝试获取令牌
         */
        @Synchronized
        fun tryAcquire(numTokens: Int): Boolean {
            cleanup()

            val currentCount = getCount()
            if (currentCount + numTokens <= limit) {
                val now = System.currentTimeMillis()
                val bucketKey = now / (windowSize / precision)

                buckets.computeIfAbsent(bucketKey) { AtomicLong(0) }
                    .addAndGet(numTokens.toLong())

                return true
            }

            return false
        }

        /**
         * 清理过期的桶
         */
        private fun cleanup() {
            val now = System.currentTimeMillis()
            val cutoff = now - windowSize

            buckets.entries.removeIf { (timestamp, _) ->
                timestamp * (windowSize / precision) < cutoff
            }
        }

        /**
         * 获取当前计数
         */
        fun getCount(): Int {
            cleanup()
            return buckets.values.sumOf { it.get() }.toInt()
        }

        /**
         * 检查是否过期
         */
        fun isExpired(now: Long): Boolean {
            return now - creationTime > ttl
        }
    }

    /**
     * 漏桶限流器
     */
    class LeakyBucketLimiter(
        val id: String,
        val capacity: Int,
        val leakRate: Double,
        val leakInterval: Long,
        val ttl: Long
    ) {
        private val water = AtomicLong(0)
        var lastLeakTime = System.currentTimeMillis()
            private set
        val creationTime = System.currentTimeMillis()

        /**
         * 尝试获取令牌
         */
        @Synchronized
        fun tryAcquire(numTokens: Int): Boolean {
            leak()

            val currentWater = water.get()
            if (currentWater + numTokens <= capacity) {
                water.addAndGet(numTokens.toLong())
                return true
            }

            return false
        }

        /**
         * 漏水
         */
        private fun leak() {
            val now = System.currentTimeMillis()
            val elapsed = now - lastLeakTime

            if (elapsed >= leakInterval) {
                val intervalsElapsed = elapsed / leakInterval
                val waterToLeak = (intervalsElapsed * leakRate).toLong()

                if (waterToLeak > 0) {
                    val newWater = Math.max(0, water.get() - waterToLeak)
                    water.set(newWater)
                    lastLeakTime = now
                }
            }
        }

        /**
         * 获取当前水量
         */
        fun getWater(): Long {
            leak()
            return water.get()
        }

        /**
         * 检查是否过期
         */
        fun isExpired(now: Long): Boolean {
            return now - creationTime > ttl
        }
    }
}
