package com.louloulin.apix.core.queue

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 请求队列管理器，负责管理请求队列和优先级处理
 */
class RequestQueueManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(RequestQueueManager::class.java)

    // 队列配置
    private var maxQueueSize = 10000
    private var queueProcessInterval = 10L // 毫秒
    private var defaultPriority = 5
    private var maxPriority = 10
    private var minPriority = 1
    private var defaultTimeout = 30000L // 毫秒
    private var enableTimeout = true
    private var enablePriority = true
    private var enableFairness = true

    // 队列统计
    private val totalRequests = AtomicLong(0)
    private val totalProcessed = AtomicLong(0)
    private val totalRejected = AtomicLong(0)
    private val totalTimedOut = AtomicLong(0)
    private val totalSucceeded = AtomicLong(0)
    private val totalFailed = AtomicLong(0)

    // 队列状态
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // 请求队列 - 按服务分组
    private val requestQueues = ConcurrentHashMap<String, PriorityBlockingQueue<QueuedRequest>>()

    // 队列处理器 - 按服务分组
    private val queueProcessors = ConcurrentHashMap<String, QueueProcessor>()

    // 队列监控
    private val queueSizes = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * 初始化请求队列管理器
     */
    fun initialize(config: JsonObject) {
        // 加载配置
        maxQueueSize = config.getInteger("maxQueueSize", maxQueueSize)
        queueProcessInterval = config.getLong("queueProcessInterval", queueProcessInterval)
        defaultPriority = config.getInteger("defaultPriority", defaultPriority)
        maxPriority = config.getInteger("maxPriority", maxPriority)
        minPriority = config.getInteger("minPriority", minPriority)
        defaultTimeout = config.getLong("defaultTimeout", defaultTimeout)
        enableTimeout = config.getBoolean("enableTimeout", enableTimeout)
        enablePriority = config.getBoolean("enablePriority", enablePriority)
        enableFairness = config.getBoolean("enableFairness", enableFairness)

        // 启动队列处理
        start()

        // 启动队列监控
        startQueueMonitoring()

        // 注册事件总线处理器
        registerEventBusHandlers()

        logger.info("初始化请求队列管理器完成，最大队列大小: $maxQueueSize, 队列处理间隔: ${queueProcessInterval}ms")
    }

    /**
     * 启动队列处理
     */
    fun start() {
        if (isRunning.compareAndSet(false, true)) {
            logger.info("启动请求队列处理")

            // 恢复所有队列处理器
            queueProcessors.values.forEach { it.resume() }
        }
    }

    /**
     * 停止队列处理
     */
    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            logger.info("停止请求队列处理")

            // 暂停所有队列处理器
            queueProcessors.values.forEach { it.pause() }
        }
    }

    /**
     * 暂停队列处理
     */
    fun pause() {
        if (isPaused.compareAndSet(false, true)) {
            logger.info("暂停请求队列处理")

            // 暂停所有队列处理器
            queueProcessors.values.forEach { it.pause() }
        }
    }

    /**
     * 恢复队列处理
     */
    fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            logger.info("恢复请求队列处理")

            // 恢复所有队列处理器
            queueProcessors.values.forEach { it.resume() }
        }
    }

    /**
     * 入队请求
     */
    fun enqueue(
        serviceId: String,
        request: Any,
        priority: Int = defaultPriority,
        timeout: Long = defaultTimeout,
        handler: (Any) -> Future<Any>
    ): Future<Any> {
        // 检查队列是否已满
        val queueSize = getQueueSize(serviceId)
        if (queueSize >= maxQueueSize) {
            totalRejected.incrementAndGet()
            return Future.failedFuture("Queue is full for service: $serviceId")
        }

        // 创建 Promise
        val promise = Promise.promise<Any>()

        // 创建队列请求
        val queuedRequest = QueuedRequest(
            id = totalRequests.incrementAndGet(),
            request = request,
            priority = normalizePriority(priority),
            timestamp = System.currentTimeMillis(),
            timeout = timeout,
            handler = handler as (Any) -> Future<Any>,
            promise = promise
        )

        // 获取或创建队列
        val queue = getOrCreateQueue(serviceId)

        // 获取或创建队列处理器
        val processor = getOrCreateQueueProcessor(serviceId)

        // 入队请求
        queue.offer(queuedRequest)

        // 更新队列大小
        updateQueueSize(serviceId, queue.size)

        // 发布队列更新事件
        publishQueueUpdate(serviceId)

        // 如果队列处理器已暂停，恢复处理
        if (processor.isPaused() && !isPaused.get()) {
            processor.resume()
        }

        return promise.future()
    }

    /**
     * 获取或创建队列
     */
    private fun getOrCreateQueue(serviceId: String): PriorityBlockingQueue<QueuedRequest> {
        return requestQueues.computeIfAbsent(serviceId) {
            PriorityBlockingQueue(
                maxQueueSize,
                compareBy<QueuedRequest> { it.priority }.thenBy { it.timestamp }
            )
        }
    }

    /**
     * 获取或创建队列处理器
     */
    private fun getOrCreateQueueProcessor(serviceId: String): QueueProcessor {
        return queueProcessors.computeIfAbsent(serviceId) {
            val processor = QueueProcessor(serviceId, getOrCreateQueue(serviceId))
            processor.start()
            processor
        }
    }

    /**
     * 获取队列大小
     */
    fun getQueueSize(serviceId: String): Int {
        return queueSizes.computeIfAbsent(serviceId) { AtomicInteger(0) }.get()
    }

    /**
     * 更新队列大小
     */
    private fun updateQueueSize(serviceId: String, size: Int) {
        queueSizes.computeIfAbsent(serviceId) { AtomicInteger(0) }.set(size)
    }

    /**
     * 发布队列更新事件
     */
    private fun publishQueueUpdate(serviceId: String) {
        val queueSize = getQueueSize(serviceId)

        vertx.eventBus().publish(
            "apix.queue.update",
            JsonObject()
                .put("serviceId", serviceId)
                .put("queueSize", queueSize)
                .put("timestamp", System.currentTimeMillis())
        )
    }

    /**
     * 标准化优先级
     */
    private fun normalizePriority(priority: Int): Int {
        return when {
            priority < minPriority -> minPriority
            priority > maxPriority -> maxPriority
            else -> priority
        }
    }

    /**
     * 获取队列统计信息
     */
    fun getQueueStats(serviceId: String): JsonObject {
        val queueSize = getQueueSize(serviceId)
        val queue = requestQueues[serviceId]

        return JsonObject()
            .put("serviceId", serviceId)
            .put("queueSize", queueSize)
            .put("isEmpty", queue?.isEmpty() ?: true)
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 获取所有队列统计信息
     */
    fun getAllQueueStats(): JsonObject {
        val queues = JsonObject()

        requestQueues.keys.forEach { serviceId ->
            queues.put(serviceId, getQueueStats(serviceId))
        }

        return JsonObject()
            .put("queues", queues)
            .put("totalRequests", totalRequests.get())
            .put("totalProcessed", totalProcessed.get())
            .put("totalRejected", totalRejected.get())
            .put("totalTimedOut", totalTimedOut.get())
            .put("totalSucceeded", totalSucceeded.get())
            .put("totalFailed", totalFailed.get())
            .put("isRunning", isRunning.get())
            .put("isPaused", isPaused.get())
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 清空队列
     */
    fun clearQueue(serviceId: String) {
        val queue = requestQueues[serviceId]
        queue?.clear()
        updateQueueSize(serviceId, 0)
        publishQueueUpdate(serviceId)

        logger.info("清空队列: $serviceId")
    }

    /**
     * 清空所有队列
     */
    fun clearAllQueues() {
        requestQueues.keys.forEach { clearQueue(it) }

        logger.info("清空所有队列")
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        totalRequests.set(0)
        totalProcessed.set(0)
        totalRejected.set(0)
        totalTimedOut.set(0)
        totalSucceeded.set(0)
        totalFailed.set(0)

        logger.info("重置队列统计信息")
    }

    /**
     * 启动队列监控
     */
    private fun startQueueMonitoring() {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                try {
                    // 检查超时请求
                    if (enableTimeout) {
                        checkTimeoutRequests()
                    }

                    // 发布队列统计信息
                    publishQueueStats()

                    delay(1000) // 每秒检查一次
                } catch (e: Exception) {
                    logger.error("队列监控异常", e)
                    delay(1000)
                }
            }
        }
    }

    /**
     * 检查超时请求
     */
    private fun checkTimeoutRequests() {
        val now = System.currentTimeMillis()

        requestQueues.forEach { (serviceId, queue) ->
            val timeoutRequests = mutableListOf<QueuedRequest>()

            // 找出超时的请求
            queue.forEach { request ->
                if (enableTimeout && request.timeout > 0 && now - request.timestamp > request.timeout) {
                    timeoutRequests.add(request)
                }
            }

            // 移除超时请求
            if (timeoutRequests.isNotEmpty()) {
                queue.removeAll(timeoutRequests)
                updateQueueSize(serviceId, queue.size)

                // 处理超时请求
                timeoutRequests.forEach { request ->
                    totalTimedOut.incrementAndGet()
                    request.promise.fail("Request timed out after ${request.timeout}ms")
                }

                logger.debug("移除 ${timeoutRequests.size} 个超时请求，服务: $serviceId")

                // 发布队列更新事件
                publishQueueUpdate(serviceId)
            }
        }
    }

    /**
     * 发布队列统计信息
     */
    private fun publishQueueStats() {
        val stats = getAllQueueStats()

        vertx.eventBus().publish(
            "apix.queue.stats",
            stats
        )
    }

    /**
     * 注册事件总线处理器
     */
    private fun registerEventBusHandlers() {
        // 注册队列控制处理器
        vertx.eventBus().consumer<JsonObject>("apix.queue.control") { message ->
            val action = message.body().getString("action")
            val serviceId = message.body().getString("serviceId")

            when (action) {
                "start" -> {
                    start()
                    message.reply(JsonObject().put("success", true))
                }
                "stop" -> {
                    stop()
                    message.reply(JsonObject().put("success", true))
                }
                "pause" -> {
                    pause()
                    message.reply(JsonObject().put("success", true))
                }
                "resume" -> {
                    resume()
                    message.reply(JsonObject().put("success", true))
                }
                "clear" -> {
                    if (serviceId != null) {
                        clearQueue(serviceId)
                    } else {
                        clearAllQueues()
                    }
                    message.reply(JsonObject().put("success", true))
                }
                "reset" -> {
                    resetStats()
                    message.reply(JsonObject().put("success", true))
                }
                "stats" -> {
                    val stats = if (serviceId != null) {
                        getQueueStats(serviceId)
                    } else {
                        getAllQueueStats()
                    }
                    message.reply(stats)
                }
                else -> {
                    message.fail(400, "Unknown action: $action")
                }
            }
        }

        // 注册队列配置处理器
        vertx.eventBus().consumer<JsonObject>("apix.queue.config") { message ->
            val config = message.body()

            // 更新配置
            config.getInteger("maxQueueSize")?.let { maxQueueSize = it }
            config.getLong("queueProcessInterval")?.let { queueProcessInterval = it }
            config.getInteger("defaultPriority")?.let { defaultPriority = it }
            config.getInteger("maxPriority")?.let { maxPriority = it }
            config.getInteger("minPriority")?.let { minPriority = it }
            config.getLong("defaultTimeout")?.let { defaultTimeout = it }
            config.getBoolean("enableTimeout")?.let { enableTimeout = it }
            config.getBoolean("enablePriority")?.let { enablePriority = it }
            config.getBoolean("enableFairness")?.let { enableFairness = it }

            // 更新队列处理器配置
            queueProcessors.values.forEach { processor ->
                processor.setProcessInterval(queueProcessInterval)
            }

            message.reply(JsonObject().put("success", true))
        }
    }

    /**
     * 队列处理器
     */
    inner class QueueProcessor(
        private val serviceId: String,
        private val queue: PriorityBlockingQueue<QueuedRequest>
    ) {
        private val isRunning = AtomicBoolean(false)
        private val isPaused = AtomicBoolean(false)
        private var processInterval = queueProcessInterval

        /**
         * 启动处理器
         */
        fun start() {
            if (isRunning.compareAndSet(false, true)) {
                CoroutineScope(Dispatchers.Default).launch {
                    while (isRunning.get()) {
                        try {
                            if (!isPaused.get()) {
                                processQueue()
                            }
                            delay(processInterval)
                        } catch (e: Exception) {
                            logger.error("处理队列异常，服务: $serviceId", e)
                            delay(processInterval)
                        }
                    }
                }

                logger.debug("启动队列处理器，服务: $serviceId")
            }
        }

        /**
         * 停止处理器
         */
        fun stop() {
            isRunning.set(false)
            logger.debug("停止队列处理器，服务: $serviceId")
        }

        /**
         * 暂停处理器
         */
        fun pause() {
            isPaused.set(true)
            logger.debug("暂停队列处理器，服务: $serviceId")
        }

        /**
         * 恢复处理器
         */
        fun resume() {
            isPaused.set(false)
            logger.debug("恢复队列处理器，服务: $serviceId")
        }

        /**
         * 处理器是否已暂停
         */
        fun isPaused(): Boolean {
            return isPaused.get()
        }

        /**
         * 设置处理间隔
         */
        fun setProcessInterval(interval: Long) {
            processInterval = interval
        }

        /**
         * 处理队列
         */
        private fun processQueue() {
            if (queue.isEmpty()) {
                return
            }

            // 取出请求
            val request = queue.poll()
            if (request != null) {
                // 更新队列大小
                updateQueueSize(serviceId, queue.size)

                // 发布队列更新事件
                publishQueueUpdate(serviceId)

                // 处理请求
                processRequest(request)
            }
        }

        /**
         * 处理请求
         */
        private fun processRequest(request: QueuedRequest) {
            // 检查请求是否已超时
            val now = System.currentTimeMillis()
            if (enableTimeout && request.timeout > 0 && now - request.timestamp > request.timeout) {
                totalTimedOut.incrementAndGet()
                request.promise.fail("Request timed out after ${request.timeout}ms")
                return
            }

            // 处理请求
            totalProcessed.incrementAndGet()

            try {
                request.handler(request.request).onComplete { ar ->
                    if (ar.succeeded()) {
                        totalSucceeded.incrementAndGet()
                        request.promise.complete(ar.result())
                    } else {
                        totalFailed.incrementAndGet()
                        request.promise.fail(ar.cause())
                    }
                }
            } catch (e: Exception) {
                totalFailed.incrementAndGet()
                request.promise.fail(e)
            }
        }
    }

    /**
     * 队列请求
     */
    data class QueuedRequest(
        val id: Long,
        val request: Any,
        val priority: Int,
        val timestamp: Long,
        val timeout: Long,
        val handler: (Any) -> Future<Any>,
        val promise: Promise<Any>
    )
}
