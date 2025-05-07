package com.louloulin.apix.core

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginType
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 表示将按顺序执行的插件链。
 * 优化版本支持按优先级分组执行和并行执行。
 */
class PluginChain(private val plugins: List<Plugin>) {
    private val logger = LoggerFactory.getLogger(PluginChain::class.java)

    // 插件执行性能统计
    private val executionTimes = ConcurrentHashMap<String, AtomicLong>()
    private val executionCounts = ConcurrentHashMap<String, AtomicLong>()

    // 插件执行结果缓存
    private val resultCache = ConcurrentHashMap<String, Future<Void>>()

    /**
     * 执行给定路由上下文的插件链。
     *
     * @param context 要处理的路由上下文
     * @return 当链执行完成时完成的Future
     */
    fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        if (plugins.isEmpty()) {
            // 没有要执行的插件，立即完成
            promise.complete()
            return promise.future()
        }

        // 按插件优先级分组
        val pluginsByPriority = plugins
            .filter { it.shouldExecute(context) } // 过滤出应该执行的插件
            .groupBy { it.getPriority() }
            .toSortedMap() // 按优先级排序

        if (pluginsByPriority.isEmpty()) {
            // 没有要执行的插件，立即完成
            promise.complete()
            return promise.future()
        }

        // 按优先级顺序执行插件组
        executePluginGroups(context, pluginsByPriority.entries.iterator(), promise)

        return promise.future()
    }

    /**
     * 按优先级顺序执行插件组
     */
    private fun executePluginGroups(
        context: RoutingContext,
        priorityIterator: Iterator<Map.Entry<Int, List<Plugin>>>,
        promise: Promise<Void>
    ) {
        // 检查是否已到达插件组的末尾
        if (!priorityIterator.hasNext()) {
            promise.complete()
            return
        }

        // 检查响应是否已结束
        if (context.response().ended()) {
            logger.debug("响应已结束，停止插件链")
            promise.complete()
            return
        }

        // 获取当前优先级的插件组
        val (priority, pluginGroup) = priorityIterator.next()

        // 将插件分为可并行执行和需要顺序执行的两组
        val parallelPlugins = pluginGroup.filter { it.canExecuteInParallel() }
        val sequentialPlugins = pluginGroup.filter { !it.canExecuteInParallel() }

        // 创建一个Future列表来跟踪所有插件的执行
        val futures = mutableListOf<Future<*>>()

        // 并行执行可并行的插件
        if (parallelPlugins.isNotEmpty()) {
            val parallelFutures = parallelPlugins.map { plugin ->
                executePlugin(context, plugin)
            }
            futures.add(CompositeFuture.all(parallelFutures.toList()).map { null as Void? })
        }

        // 顺序执行需要顺序执行的插件
        if (sequentialPlugins.isNotEmpty()) {
            val sequentialPromise = Promise.promise<Void>()
            executeSequentialPlugins(context, sequentialPlugins.iterator(), sequentialPromise)
            futures.add(sequentialPromise.future())
        }

        // 等待当前优先级的所有插件执行完成
        CompositeFuture.all(futures).onComplete { ar ->
            if (ar.succeeded()) {
                // 检查响应是否已结束
                if (context.response().ended()) {
                    logger.debug("响应已结束，停止插件链")
                    promise.complete()
                    return@onComplete
                }

                // 继续执行下一个优先级的插件组
                executePluginGroups(context, priorityIterator, promise)
            } else {
                // 插件执行失败
                logger.error("优先级 {} 的插件组执行失败", priority, ar.cause())

                // 检查响应是否已结束
                if (!context.response().ended()) {
                    context.fail(ar.cause())
                }

                promise.fail(ar.cause())
            }
        }
    }

    /**
     * 顺序执行插件列表
     */
    private fun executeSequentialPlugins(
        context: RoutingContext,
        pluginIterator: Iterator<Plugin>,
        promise: Promise<Void>
    ) {
        // 检查是否已到达插件列表的末尾
        if (!pluginIterator.hasNext()) {
            promise.complete()
            return
        }

        // 检查响应是否已结束
        if (context.response().ended()) {
            logger.debug("响应已结束，停止顺序插件执行")
            promise.complete()
            return
        }

        // 获取当前插件
        val plugin = pluginIterator.next()

        // 执行插件
        executePlugin(context, plugin).onComplete { ar ->
            if (ar.succeeded()) {
                // 检查响应是否已结束
                if (!context.response().ended()) {
                    // 继续执行下一个插件
                    executeSequentialPlugins(context, pluginIterator, promise)
                } else {
                    // 响应已被插件结束
                    promise.complete()
                }
            } else {
                // 插件执行失败
                logger.error("插件执行失败: {}", plugin.id, ar.cause())

                // 检查响应是否已结束
                if (!context.response().ended()) {
                    context.fail(ar.cause())
                }

                promise.fail(ar.cause())
            }
        }
    }

    /**
     * 执行单个插件，包括性能监控和缓存
     */
    private fun executePlugin(context: RoutingContext, plugin: Plugin): Future<Void> {
        // 检查是否有缓存的结果
        val cacheKey = getCacheKey(context, plugin)
        val cachedResult = resultCache[cacheKey]
        if (cachedResult != null) {
            logger.debug("使用缓存的插件执行结果: {}", plugin.id)
            return cachedResult
        }

        // 记录开始时间
        val startTime = System.currentTimeMillis()

        try {
            // 执行插件
            val result = plugin.execute(context)

            // 记录执行时间
            result.onComplete { ar ->
                val endTime = System.currentTimeMillis()
                val executionTime = endTime - startTime

                // 更新统计信息
                executionTimes.computeIfAbsent(plugin.id) { AtomicLong(0) }.addAndGet(executionTime)
                executionCounts.computeIfAbsent(plugin.id) { AtomicLong(0) }.incrementAndGet()

                if (ar.succeeded()) {
                    logger.debug("插件 {} 执行成功，耗时 {} ms", plugin.id, executionTime)

                    // 缓存成功的结果
                    if (isCacheable(plugin)) {
                        resultCache[cacheKey] = result
                    }
                } else {
                    logger.error("插件 {} 执行失败，耗时 {} ms", plugin.id, executionTime, ar.cause())
                }
            }

            return result
        } catch (e: Exception) {
            // 插件执行异常
            logger.error("插件执行异常: {}", plugin.id, e)

            // 记录执行时间
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime
            executionTimes.computeIfAbsent(plugin.id) { AtomicLong(0) }.addAndGet(executionTime)
            executionCounts.computeIfAbsent(plugin.id) { AtomicLong(0) }.incrementAndGet()

            return Future.failedFuture(e)
        }
    }

    /**
     * 判断插件结果是否可缓存
     */
    private fun isCacheable(plugin: Plugin): Boolean {
        // 从插件配置中获取是否可缓存
        return plugin.config.getBoolean("cacheable") ?: false
    }

    /**
     * 生成缓存键
     */
    private fun getCacheKey(context: RoutingContext, plugin: Plugin): String {
        // 简单实现，可以根据需要扩展
        val request = context.request()
        val path = request?.path() ?: "/unknown"
        val method = request?.method()?.toString() ?: "UNKNOWN"
        return "${plugin.id}:${path}:${method}"
    }

    /**
     * 获取插件执行统计信息
     */
    fun getExecutionStats(): Map<String, Map<String, Long>> {
        val stats = mutableMapOf<String, Map<String, Long>>()

        plugins.forEach { plugin ->
            val pluginId = plugin.id
            val executionTime = executionTimes[pluginId]?.get() ?: 0
            val executionCount = executionCounts[pluginId]?.get() ?: 0
            val avgExecutionTime = if (executionCount > 0) executionTime / executionCount else 0

            stats[pluginId] = mapOf(
                "executionTime" to executionTime,
                "executionCount" to executionCount,
                "avgExecutionTime" to avgExecutionTime
            )
        }

        return stats
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        resultCache.clear()
        logger.info("插件执行结果缓存已清除")
    }
}
