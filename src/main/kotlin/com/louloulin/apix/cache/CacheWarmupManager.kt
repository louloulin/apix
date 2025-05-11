package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 缓存预热管理器，负责系统启动时预热缓存，减少冷启动影响。
 */
class CacheWarmupManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(CacheWarmupManager::class.java)

    // 缓存预热是否启用
    private val warmupEnabled = AtomicBoolean(true)

    // 缓存预热模式
    private val warmupMode = AtomicReference<String>("async")

    // 缓存预热超时时间（毫秒）
    private val warmupTimeout = AtomicLong(60000)

    // 缓存预热进度
    private val warmupProgress = AtomicLong(0)

    // 缓存预热总数
    private val warmupTotal = AtomicLong(0)

    // 缓存预热状态
    private val warmupStatus = AtomicReference<String>("idle")

    // 缓存预热开始时间
    private val warmupStartTime = AtomicLong(0)

    // 缓存预热结束时间
    private val warmupEndTime = AtomicLong(0)

    // 缓存预热统计
    private val warmupStats = ConcurrentHashMap<String, WarmupStats>()

    // 缓存命名空间
    private val cacheNamespace = AtomicReference<String>("apix")

    // 多级缓存管理器
    private lateinit var cacheManager: MultiLevelCacheManager

    /**
     * 初始化缓存预热管理器。
     *
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化缓存预热管理器")

        // 获取缓存配置
        val cacheConfig = config.getJsonObject("cache", JsonObject())
        val warmupConfig = cacheConfig.getJsonObject("warmup", JsonObject())

        // 更新配置
        warmupEnabled.set(warmupConfig.getBoolean("enabled", true))
        warmupMode.set(warmupConfig.getString("mode", "async"))
        warmupTimeout.set(warmupConfig.getLong("timeout", 60000))
        cacheNamespace.set(cacheConfig.getString("namespace", "apix"))

        // 获取多级缓存管理器
        cacheManager = MultiLevelCacheManager.getInstance(vertx)

        // 如果预热未启用，直接返回
        if (!warmupEnabled.get()) {
            logger.info("缓存预热未启用")
            return Future.succeededFuture()
        }

        // 注册缓存预热事件处理器
        registerWarmupHandlers()

        logger.info("缓存预热管理器初始化完成，模式: ${warmupMode.get()}")
        return Future.succeededFuture()
    }

    /**
     * 注册缓存预热事件处理器。
     */
    private fun registerWarmupHandlers() {
        // 监听缓存预热请求
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.cache.warmup") { message ->
            val keys = message.body().getJsonArray("keys", JsonArray())
            val handlers = message.body().getJsonArray("handlers", JsonArray())
            val namespace = message.body().getString("namespace", cacheNamespace.get())
            val mode = message.body().getString("mode", warmupMode.get())

            // 执行缓存预热
            warmupCache(keys.map { it.toString() }, handlers.map { it.toString() }, namespace, mode)
                .onSuccess { result ->
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", result)
                    )
                }
                .onFailure { cause ->
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("message", cause.message)
                    )
                }
        }

        // 监听缓存预热状态请求
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.cache.warmup.status") { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", getWarmupStatus())
            )
        }
    }

    /**
     * 执行缓存预热。
     *
     * @param keys 要预热的缓存键列表
     * @param handlers 要调用的预热处理器列表
     * @param namespace 命名空间
     * @param mode 预热模式
     * @return 预热结果的 Future
     */
    fun warmupCache(
        keys: List<String> = emptyList(),
        handlers: List<String> = emptyList(),
        namespace: String = cacheNamespace.get(),
        mode: String = warmupMode.get()
    ): Future<JsonObject> {
        // 如果预热未启用，直接返回
        if (!warmupEnabled.get()) {
            return Future.succeededFuture(JsonObject()
                .put("status", "disabled")
                .put("message", "Cache warmup is disabled")
            )
        }

        // 如果已经在预热中，直接返回
        if (warmupStatus.get() == "warming") {
            return Future.succeededFuture(JsonObject()
                .put("status", "warming")
                .put("progress", warmupProgress.get())
                .put("total", warmupTotal.get())
                .put("message", "Cache warmup is already in progress")
            )
        }

        // 更新预热状态
        warmupStatus.set("warming")
        warmupStartTime.set(System.currentTimeMillis())
        warmupProgress.set(0)
        warmupTotal.set((keys.size + handlers.size).toLong())

        // 创建预热统计
        val stats = warmupStats.computeIfAbsent(namespace) { WarmupStats() }
        stats.reset()

        // 创建预热结果 Promise
        val promise = Promise.promise<JsonObject>()

        // 根据模式执行预热
        if (mode == "sync") {
            // 同步模式
            executeWarmupSync(keys, handlers, namespace, stats, promise)
        } else {
            // 异步模式
            executeWarmupAsync(keys, handlers, namespace, stats, promise)
        }

        return promise.future()
    }

    /**
     * 同步执行缓存预热。
     *
     * @param keys 要预热的缓存键列表
     * @param handlers 要调用的预热处理器列表
     * @param namespace 命名空间
     * @param stats 预热统计
     * @param promise 预热结果 Promise
     */
    private fun executeWarmupSync(
        keys: List<String>,
        handlers: List<String>,
        namespace: String,
        stats: WarmupStats,
        promise: Promise<JsonObject>
    ) {
        // 设置超时定时器
        val timeoutId = vertx.setTimer(warmupTimeout.get()) {
            if (warmupStatus.get() == "warming") {
                // 超时，强制完成预热
                completeWarmup(namespace, stats, "timeout")
                promise.complete(getWarmupStatus())
            }
        }

        // 预热缓存键
        var keysFuture = Future.succeededFuture<Void>()
        for (key in keys) {
            keysFuture = keysFuture.compose { _ ->
                val keyPromise = Promise.promise<Void>()

                // 尝试从缓存中获取值
                cacheManager.get(key, namespace)
                    .onSuccess { value ->
                        // 缓存命中
                        stats.keyHit()
                        warmupProgress.incrementAndGet()
                        keyPromise.complete()
                    }
                    .onFailure { cause ->
                        // 缓存未命中
                        stats.keyMiss()
                        warmupProgress.incrementAndGet()
                        keyPromise.complete()
                    }

                keyPromise.future()
            }
        }

        // 调用预热处理器
        var handlersFuture = keysFuture.compose { _ ->
            var future = Future.succeededFuture<Void>()
            for (handler in handlers) {
                future = future.compose { _ ->
                    val handlerPromise = Promise.promise<Void>()

                    // 调用预热处理器
                    vertx.eventBus().request<JsonObject>(handler, JsonObject()
                        .put("namespace", namespace)
                    ) { ar ->
                        if (ar.succeeded()) {
                            // 处理器调用成功
                            stats.handlerSuccess()
                        } else {
                            // 处理器调用失败
                            stats.handlerFailure()
                            logger.warn("预热处理器 $handler 调用失败: ${ar.cause().message}")
                        }

                        warmupProgress.incrementAndGet()
                        handlerPromise.complete()
                    }

                    handlerPromise.future()
                }
            }
            future
        }

        // 完成预热
        handlersFuture.onComplete { ar ->
            // 取消超时定时器
            vertx.cancelTimer(timeoutId)

            // 完成预热
            completeWarmup(namespace, stats, if (ar.succeeded()) "completed" else "failed")
            promise.complete(getWarmupStatus())
        }
    }

    /**
     * 异步执行缓存预热。
     *
     * @param keys 要预热的缓存键列表
     * @param handlers 要调用的预热处理器列表
     * @param namespace 命名空间
     * @param stats 预热统计
     * @param promise 预热结果 Promise
     */
    private fun executeWarmupAsync(
        keys: List<String>,
        handlers: List<String>,
        namespace: String,
        stats: WarmupStats,
        promise: Promise<JsonObject>
    ) {
        // 设置超时定时器
        val timeoutId = vertx.setTimer(warmupTimeout.get()) {
            if (warmupStatus.get() == "warming") {
                // 超时，强制完成预热
                completeWarmup(namespace, stats, "timeout")
            }
        }

        // 立即返回，表示预热已开始
        promise.complete(JsonObject()
            .put("status", "warming")
            .put("message", "Cache warmup started")
        )

        // 预热缓存键
        for (key in keys) {
            // 尝试从缓存中获取值
            cacheManager.get(key, namespace)
                .onSuccess { value ->
                    // 缓存命中
                    stats.keyHit()
                    checkWarmupProgress(keys.size, handlers.size, stats, timeoutId)
                }
                .onFailure { cause ->
                    // 缓存未命中
                    stats.keyMiss()
                    checkWarmupProgress(keys.size, handlers.size, stats, timeoutId)
                }
        }

        // 调用预热处理器
        for (handler in handlers) {
            // 调用预热处理器
            vertx.eventBus().request<JsonObject>(handler, JsonObject()
                .put("namespace", namespace)
            ) { ar ->
                if (ar.succeeded()) {
                    // 处理器调用成功
                    stats.handlerSuccess()
                } else {
                    // 处理器调用失败
                    stats.handlerFailure()
                    logger.warn("预热处理器 $handler 调用失败: ${ar.cause().message}")
                }

                checkWarmupProgress(keys.size, handlers.size, stats, timeoutId)
            }
        }
    }

    /**
     * 检查预热进度，如果全部完成则完成预热。
     *
     * @param keysCount 缓存键数量
     * @param handlersCount 处理器数量
     * @param stats 预热统计
     * @param timeoutId 超时定时器 ID
     */
    private fun checkWarmupProgress(keysCount: Int, handlersCount: Int, stats: WarmupStats, timeoutId: Long) {
        // 增加进度
        warmupProgress.incrementAndGet()

        // 检查是否全部完成
        val progress = stats.keyHits.get() + stats.keyMisses.get() + stats.handlerSuccesses.get() + stats.handlerFailures.get()
        if (progress >= keysCount + handlersCount) {
            // 取消超时定时器
            vertx.cancelTimer(timeoutId)

            // 完成预热
            completeWarmup(cacheNamespace.get(), stats, "completed")
        }
    }

    /**
     * 完成缓存预热。
     *
     * @param namespace 命名空间
     * @param stats 预热统计
     * @param status 完成状态
     */
    private fun completeWarmup(namespace: String, stats: WarmupStats, status: String) {
        // 更新预热状态
        warmupStatus.set(status)
        warmupEndTime.set(System.currentTimeMillis())

        // 记录预热完成
        logger.info("缓存预热完成: namespace=$namespace, status=$status, duration=${warmupEndTime.get() - warmupStartTime.get()}ms")
        logger.info("预热统计: keyHits=${stats.keyHits.get()}, keyMisses=${stats.keyMisses.get()}, handlerSuccesses=${stats.handlerSuccesses.get()}, handlerFailures=${stats.handlerFailures.get()}")

        // 发布预热完成事件
        vertx.eventBus().publish("${namespace}.cache.warmup.completed", JsonObject()
            .put("namespace", namespace)
            .put("status", status)
            .put("duration", warmupEndTime.get() - warmupStartTime.get())
            .put("stats", JsonObject()
                .put("keyHits", stats.keyHits.get())
                .put("keyMisses", stats.keyMisses.get())
                .put("handlerSuccesses", stats.handlerSuccesses.get())
                .put("handlerFailures", stats.handlerFailures.get())
            )
        )
    }

    /**
     * 获取缓存预热状态。
     *
     * @return 包含预热状态的 JsonObject
     */
    fun getWarmupStatus(): JsonObject {
        val status = warmupStatus.get()
        val progress = warmupProgress.get()
        val total = warmupTotal.get()
        val startTime = warmupStartTime.get()
        val endTime = warmupEndTime.get()

        val result = JsonObject()
            .put("status", status)
            .put("enabled", warmupEnabled.get())
            .put("mode", warmupMode.get())
            .put("progress", progress)
            .put("total", total)
            .put("percentage", if (total > 0) (progress * 100 / total) else 0)
            .put("startTime", startTime)

        if (status != "warming") {
            result.put("endTime", endTime)
            result.put("duration", endTime - startTime)
        }

        // 添加统计信息
        val statsJson = JsonObject()
        for ((namespace, stats) in warmupStats) {
            statsJson.put(namespace, JsonObject()
                .put("keyHits", stats.keyHits.get())
                .put("keyMisses", stats.keyMisses.get())
                .put("handlerSuccesses", stats.handlerSuccesses.get())
                .put("handlerFailures", stats.handlerFailures.get())
            )
        }
        result.put("stats", statsJson)

        return result
    }

    /**
     * 预热统计信息。
     */
    class WarmupStats {
        // 缓存键命中次数
        val keyHits = AtomicLong(0)

        // 缓存键未命中次数
        val keyMisses = AtomicLong(0)

        // 处理器成功次数
        val handlerSuccesses = AtomicLong(0)

        // 处理器失败次数
        val handlerFailures = AtomicLong(0)

        /**
         * 记录缓存键命中。
         */
        fun keyHit() {
            keyHits.incrementAndGet()
        }

        /**
         * 记录缓存键未命中。
         */
        fun keyMiss() {
            keyMisses.incrementAndGet()
        }

        /**
         * 记录处理器成功。
         */
        fun handlerSuccess() {
            handlerSuccesses.incrementAndGet()
        }

        /**
         * 记录处理器失败。
         */
        fun handlerFailure() {
            handlerFailures.incrementAndGet()
        }

        /**
         * 重置统计信息。
         */
        fun reset() {
            keyHits.set(0)
            keyMisses.set(0)
            handlerSuccesses.set(0)
            handlerFailures.set(0)
        }
    }

    companion object {
        // 单例实例
        @Volatile
        private var instance: CacheWarmupManager? = null

        /**
         * 获取 CacheWarmupManager 的单例实例。
         *
         * @param vertx Vert.x 实例
         * @return CacheWarmupManager 实例
         */
        fun getInstance(vertx: Vertx): CacheWarmupManager {
            return instance ?: synchronized(this) {
                instance ?: CacheWarmupManager(vertx).also { instance = it }
            }
        }
    }
}
