package com.louloulin.apix.core.async

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Function
import java.util.function.Supplier

/**
 * 异步处理增强器，提供高级异步处理功能。
 *
 * 主要功能：
 * 1. 批量处理异步操作
 * 2. 并行执行并控制并发度
 * 3. 失败重试机制
 * 4. 熔断机制
 * 5. 超时处理和取消
 */
class AsyncProcessorEnhancer private constructor(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(AsyncProcessorEnhancer::class.java)

    // 配置
    private var defaultTimeout = 30000L // 默认超时时间（毫秒）
    private var defaultMaxRetries = 3 // 默认最大重试次数
    private var defaultRetryDelay = 1000L // 默认重试延迟（毫秒）
    private var defaultMaxConcurrent = 10 // 默认最大并发数
    private var defaultBatchSize = 100 // 默认批处理大小

    // 统计信息
    private val successCount = AtomicLong(0)
    private val failureCount = AtomicLong(0)
    private val timeoutCount = AtomicLong(0)
    private val retryCount = AtomicLong(0)
    private val totalProcessingTime = AtomicLong(0)
    private val processingCount = AtomicLong(0)

    // 熔断器状态
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreakerState>()

    /**
     * 熔断器状态
     */
    private class CircuitBreakerState(
        val name: String,
        val failureThreshold: Int,
        val resetTimeout: Long
    ) {
        val failures = AtomicInteger(0)
        var open = false
        var lastOpenTime = 0L

        /**
         * 记录失败
         *
         * @return 熔断器是否打开
         */
        fun recordFailure(): Boolean {
            val currentFailures = failures.incrementAndGet()
            if (currentFailures >= failureThreshold && !open) {
                open = true
                lastOpenTime = System.currentTimeMillis()
                return true
            }
            return false
        }

        /**
         * 记录成功
         */
        fun recordSuccess() {
            failures.set(0)
            if (open) {
                open = false
            }
        }

        /**
         * 检查熔断器是否允许请求
         *
         * @return 是否允许请求
         */
        fun allowRequest(): Boolean {
            if (!open) {
                return true
            }

            // 检查是否超过重置超时
            val now = System.currentTimeMillis()
            if (now - lastOpenTime > resetTimeout) {
                // 半开状态，允许一个请求尝试
                return true
            }

            return false
        }
    }

    /**
     * 初始化异步处理增强器
     *
     * @param config 配置
     */
    fun initialize(config: JsonObject) {
        defaultTimeout = config.getLong("defaultTimeout", defaultTimeout)
        defaultMaxRetries = config.getInteger("defaultMaxRetries", defaultMaxRetries)
        defaultRetryDelay = config.getLong("defaultRetryDelay", defaultRetryDelay)
        defaultMaxConcurrent = config.getInteger("defaultMaxConcurrent", defaultMaxConcurrent)
        defaultBatchSize = config.getInteger("defaultBatchSize", defaultBatchSize)

        logger.info("异步处理增强器初始化完成，默认超时: ${defaultTimeout}ms, 默认最大重试: $defaultMaxRetries, " +
                "默认重试延迟: ${defaultRetryDelay}ms, 默认最大并发: $defaultMaxConcurrent, " +
                "默认批处理大小: $defaultBatchSize")
    }

    /**
     * 批量处理异步操作
     *
     * @param items 要处理的项目列表
     * @param processor 处理单个项目的函数
     * @param batchSize 批处理大小
     * @param <T> 项目类型
     * @param <R> 结果类型
     * @return 包含所有结果的Future
     */
    fun <T, R> batchProcess(
        items: List<T>,
        processor: Function<T, Future<R>>,
        batchSize: Int = defaultBatchSize
    ): Future<List<R>> where R : Any {
        val promise = Promise.promise<List<R>>()
        val results = ArrayList<R>(items.size)

        // 如果列表为空，直接返回空结果
        if (items.isEmpty()) {
            promise.complete(results)
            return promise.future()
        }

        // 处理一批项目
        fun processBatch(startIndex: Int) {
            val endIndex = Math.min(startIndex + batchSize, items.size)
            val batch = items.subList(startIndex, endIndex)

            // 创建每个项目的Future
            val futures = batch.map { item -> processor.apply(item) }

            // 等待所有Future完成
            CompositeFuture.all(futures.toList()).onComplete { ar ->
                if (ar.succeeded()) {
                    // 收集结果
                    for (i in 0 until futures.size) {
                        results.add(futures[i].result())
                    }

                    // 检查是否还有更多批次
                    if (endIndex < items.size) {
                        // 处理下一批
                        processBatch(endIndex)
                    } else {
                        // 所有批次处理完成
                        promise.complete(results)
                    }
                } else {
                    // 处理失败
                    promise.fail(ar.cause())
                }
            }
        }

        // 开始处理第一批
        processBatch(0)

        return promise.future()
    }

    /**
     * 并行执行异步操作，控制并发度
     *
     * @param items 要处理的项目列表
     * @param processor 处理单个项目的函数
     * @param maxConcurrent 最大并发数
     * @param <T> 项目类型
     * @param <R> 结果类型
     * @return 包含所有结果的Future
     */
    fun <T, R> parallelProcess(
        items: List<T>,
        processor: Function<T, Future<R>>,
        maxConcurrent: Int = defaultMaxConcurrent
    ): Future<List<R>> where R : Any {
        val promise = Promise.promise<List<R>>()
        val results = ArrayList<R?>(items.size)

        // 初始化结果列表
        for (i in 0 until items.size) {
            results.add(null)
        }

        // 如果列表为空，直接返回空结果
        if (items.isEmpty()) {
            promise.complete(results.filterNotNull())
            return promise.future()
        }

        // 当前正在处理的项目数
        val activeCount = AtomicInteger(0)
        // 下一个要处理的项目索引
        val nextIndex = AtomicInteger(0)
        // 已完成的项目数
        val completedCount = AtomicInteger(0)

        // 处理下一个项目
        fun processNext() {
            // 检查是否还有项目要处理
            val index = nextIndex.getAndIncrement()
            if (index >= items.size) {
                return
            }

            // 增加活动计数
            activeCount.incrementAndGet()

            // 处理项目
            val item = items[index]
            processor.apply(item).onComplete { ar ->
                try {
                    if (ar.succeeded()) {
                        // 保存结果
                        results[index] = ar.result()
                        successCount.incrementAndGet()
                    } else {
                        // 记录失败
                        logger.warn("并行处理项目失败: $item", ar.cause())
                        failureCount.incrementAndGet()
                    }

                    // 减少活动计数
                    activeCount.decrementAndGet()

                    // 增加完成计数
                    val completed = completedCount.incrementAndGet()

                    // 检查是否所有项目都已完成
                    if (completed >= items.size) {
                        promise.complete(results.filterNotNull())
                    } else {
                        // 处理下一个项目
                        processNext()
                    }
                } catch (e: Exception) {
                    logger.error("处理项目结果时发生错误", e)
                    failureCount.incrementAndGet()
                    activeCount.decrementAndGet()

                    // 增加完成计数
                    val completed = completedCount.incrementAndGet()

                    // 检查是否所有项目都已完成
                    if (completed >= items.size) {
                        promise.complete(results.filterNotNull())
                    } else {
                        // 处理下一个项目
                        processNext()
                    }
                }
            }
        }

        // 启动初始并发处理
        val initialConcurrent = Math.min(maxConcurrent, items.size)
        for (i in 0 until initialConcurrent) {
            processNext()
        }

        return promise.future()
    }

    /**
     * 带重试的异步操作
     *
     * @param operation 异步操作
     * @param maxRetries 最大重试次数
     * @param retryDelay 重试延迟（毫秒）
     * @param <T> 结果类型
     * @return 包含结果的Future
     */
    fun <T> withRetry(
        operation: Supplier<Future<T>>,
        maxRetries: Int = defaultMaxRetries,
        retryDelay: Long = defaultRetryDelay
    ): Future<T> {
        val promise = Promise.promise<T>()

        // 执行操作，带重试
        fun executeWithRetry(remainingRetries: Int) {
            val startTime = System.currentTimeMillis()

            operation.get().onComplete { ar ->
                val endTime = System.currentTimeMillis()
                val duration = endTime - startTime

                if (ar.succeeded()) {
                    // 操作成功
                    successCount.incrementAndGet()
                    totalProcessingTime.addAndGet(duration)
                    processingCount.incrementAndGet()
                    promise.complete(ar.result())
                } else {
                    // 操作失败
                    if (remainingRetries > 0) {
                        // 还有重试次数
                        retryCount.incrementAndGet()

                        if (retryDelay > 0) {
                            // 如果有延迟，使用定时器
                            logger.debug("操作失败，将在 ${retryDelay}ms 后重试，剩余重试次数: $remainingRetries", ar.cause())
                            vertx.setTimer(retryDelay) {
                                executeWithRetry(remainingRetries - 1)
                            }
                        } else {
                            // 如果没有延迟，直接重试
                            logger.debug("操作失败，立即重试，剩余重试次数: $remainingRetries", ar.cause())
                            executeWithRetry(remainingRetries - 1)
                        }
                    } else {
                        // 没有重试次数了，操作失败
                        failureCount.incrementAndGet()
                        totalProcessingTime.addAndGet(duration)
                        processingCount.incrementAndGet()
                        logger.warn("操作失败，没有剩余重试次数", ar.cause())
                        promise.fail(ar.cause())
                    }
                }
            }
        }

        // 开始执行操作
        executeWithRetry(maxRetries)

        return promise.future()
    }

    /**
     * 带超时的异步操作
     *
     * @param operation 异步操作
     * @param timeout 超时时间（毫秒）
     * @param <T> 结果类型
     * @return 包含结果的Future
     */
    fun <T> withTimeout(
        operation: Supplier<Future<T>>,
        timeout: Long = defaultTimeout
    ): Future<T> {
        val promise = Promise.promise<T>()

        // 设置超时定时器
        val timerId = vertx.setTimer(timeout) {
            timeoutCount.incrementAndGet()
            // 使用fail而不是tryFail，确保超时会失败
            promise.fail("操作超时，超过 ${timeout}ms")
        }

        // 执行操作
        val startTime = System.currentTimeMillis()

        operation.get().onComplete { ar ->
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            // 取消超时定时器
            vertx.cancelTimer(timerId)

            // 只有在promise还没有完成或失败的情况下才处理结果
            if (!promise.future().isComplete()) {
                if (ar.succeeded()) {
                    // 操作成功
                    successCount.incrementAndGet()
                    totalProcessingTime.addAndGet(duration)
                    processingCount.incrementAndGet()
                    promise.complete(ar.result())
                } else {
                    // 操作失败
                    failureCount.incrementAndGet()
                    totalProcessingTime.addAndGet(duration)
                    processingCount.incrementAndGet()
                    promise.fail(ar.cause())
                }
            }
        }

        return promise.future()
    }

    /**
     * 带熔断的异步操作
     *
     * @param name 熔断器名称
     * @param operation 异步操作
     * @param fallback 失败后的备选方案
     * @param failureThreshold 失败阈值
     * @param resetTimeout 重置超时（毫秒）
     * @param <T> 结果类型
     * @return 包含结果的Future
     */
    fun <T> withCircuitBreaker(
        name: String,
        operation: Supplier<Future<T>>,
        fallback: Function<Throwable, T>,
        failureThreshold: Int = 5,
        resetTimeout: Long = 30000
    ): Future<T> {
        val promise = Promise.promise<T>()

        // 获取或创建熔断器
        val circuitBreaker = circuitBreakers.computeIfAbsent(name) {
            CircuitBreakerState(name, failureThreshold, resetTimeout)
        }

        // 检查熔断器是否允许请求
        if (!circuitBreaker.allowRequest()) {
            // 熔断器打开，使用备选方案
            logger.debug("熔断器 $name 打开，使用备选方案")
            try {
                val result = fallback.apply(RuntimeException("熔断器打开"))
                promise.complete(result)
            } catch (e: Exception) {
                promise.fail(e)
            }
            return promise.future()
        }

        // 执行操作
        val startTime = System.currentTimeMillis()

        operation.get().onComplete { ar ->
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            if (ar.succeeded()) {
                // 操作成功
                successCount.incrementAndGet()
                totalProcessingTime.addAndGet(duration)
                processingCount.incrementAndGet()
                circuitBreaker.recordSuccess()
                promise.complete(ar.result())
            } else {
                // 操作失败
                failureCount.incrementAndGet()
                totalProcessingTime.addAndGet(duration)
                processingCount.incrementAndGet()

                // 记录失败并检查熔断器是否打开
                val opened = circuitBreaker.recordFailure()
                if (opened) {
                    logger.warn("熔断器 $name 打开，失败次数达到阈值 $failureThreshold")
                }

                // 使用备选方案
                try {
                    val result = fallback.apply(ar.cause())
                    promise.complete(result)
                } catch (e: Exception) {
                    promise.fail(e)
                }
            }
        }

        return promise.future()
    }

    /**
     * 获取统计信息
     *
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("successCount", successCount.get())
            .put("failureCount", failureCount.get())
            .put("timeoutCount", timeoutCount.get())
            .put("retryCount", retryCount.get())
            .put("totalProcessingTime", totalProcessingTime.get())
            .put("processingCount", processingCount.get())

        // 计算平均处理时间
        val avgProcessingTime = if (processingCount.get() > 0) {
            totalProcessingTime.get() / processingCount.get()
        } else {
            0
        }
        stats.put("avgProcessingTime", avgProcessingTime)

        // 添加熔断器状态
        val breakersArray = io.vertx.core.json.JsonArray()
        circuitBreakers.forEach { (name, state) ->
            breakersArray.add(JsonObject()
                .put("name", name)
                .put("failures", state.failures.get())
                .put("open", state.open)
                .put("lastOpenTime", state.lastOpenTime)
            )
        }
        stats.put("circuitBreakers", breakersArray)

        return stats
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        successCount.set(0)
        failureCount.set(0)
        timeoutCount.set(0)
        retryCount.set(0)
        totalProcessingTime.set(0)
        processingCount.set(0)
    }

    companion object {
        @Volatile
        private var instance: AsyncProcessorEnhancer? = null

        /**
         * 获取异步处理增强器实例
         *
         * @param vertx Vertx实例
         * @return AsyncProcessorEnhancer实例
         */
        fun getInstance(vertx: Vertx): AsyncProcessorEnhancer {
            return instance ?: synchronized(this) {
                instance ?: AsyncProcessorEnhancer(vertx).also { instance = it }
            }
        }
    }
}
