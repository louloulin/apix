package com.louloulin.apix.core

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginType
import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 表示将按顺序执行的插件链。
 * 优化版本支持按优先级分组执行和并行执行。
 * 基于 Vert.x EventBus 实现，支持远程执行和事件驱动模型。
 */
class PluginChain(private val vertx: Vertx, private val plugins: List<Plugin>) {
    private val logger = LoggerFactory.getLogger(PluginChain::class.java)

    // 插件执行性能统计
    private val executionTimes = ConcurrentHashMap<String, AtomicLong>()
    private val executionCounts = ConcurrentHashMap<String, AtomicLong>()

    // 插件执行结果缓存
    private val resultCache = com.louloulin.apix.plugins.cache.PluginResultCache.getInstance(vertx)

    // 插件错误统计
    private val errorStats = com.louloulin.apix.plugins.error.PluginErrorStats.getInstance(vertx)

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

        // 创建CompositeFuture来跟踪所有插件组的执行
        val futures = mutableListOf<Future<Void>>()

        // 按优先级顺序执行插件组
        var currentFuture = Future.succeededFuture<Void>()

        for ((priority, plugins) in pluginsByPriority) {
            // 检查是否有插件可以并行执行
            val parallelPlugins = plugins.filter { it.canExecuteInParallel() }
            val sequentialPlugins = plugins.filter { !it.canExecuteInParallel() }

            // 将当前优先级的插件执行添加到链中
            currentFuture = currentFuture.compose { _ ->
                // 检查响应是否已结束
                if (context.response().ended()) {
                    logger.debug("响应已结束，停止插件链")
                    return@compose Future.succeededFuture()
                }

                val groupFutures = mutableListOf<Future<Void>>()

                // 并行执行可并行的插件
                if (parallelPlugins.isNotEmpty()) {
                    val parallelFutures = parallelPlugins.map { plugin ->
                        executePlugin(context, plugin)
                    }

                    groupFutures.add(
                        CompositeFuture.all(parallelFutures.toList()).map { null as Void? }
                    )
                }

                // 顺序执行需要顺序执行的插件
                if (sequentialPlugins.isNotEmpty()) {
                    val sequentialFuture = sequentialPlugins.fold(
                        Future.succeededFuture<Void>()
                    ) { acc, plugin ->
                        acc.compose { _ ->
                            // 检查响应是否已结束
                            if (context.response().ended()) {
                                logger.debug("响应已结束，停止插件执行")
                                Future.succeededFuture()
                            } else {
                                executePlugin(context, plugin)
                            }
                        }
                    }

                    groupFutures.add(sequentialFuture)
                }

                // 等待当前优先级的所有插件执行完成
                CompositeFuture.all(groupFutures.toList()).map { null as Void? }
            }

            futures.add(currentFuture)
        }

        // 等待所有插件组执行完成
        currentFuture.onComplete { ar ->
            if (ar.succeeded()) {
                promise.complete()
            } else {
                logger.error("插件链执行失败", ar.cause())
                promise.fail(ar.cause())
            }
        }

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
     * 支持通过 EventBus 远程执行插件
     */
    private fun executePlugin(context: RoutingContext, plugin: Plugin): Future<Void> {
        // 检查是否有缓存的结果
        val cachedResult = resultCache.get(context, plugin)
        if (cachedResult != null) {
            logger.debug("使用缓存的插件执行结果: {}", plugin.id)
            return cachedResult
        }

        // 创建一个新的上下文副本，避免并发插件之间的竞态条件
        val contextCopy = cloneRoutingContext(context)

        // 记录开始时间
        val startTime = System.currentTimeMillis()

        // 检查插件是否支持通过 EventBus 执行
        val eventBusAddress = plugin.getEventBusAddress()

        val resultFuture = if (eventBusAddress != null) {
            // 通过 EventBus 执行插件
            executeViaEventBus(contextCopy, plugin, eventBusAddress)
        } else {
            // 直接执行插件
            executeDirectly(contextCopy, plugin)
        }

        // 记录执行时间并处理结果
        resultFuture.onComplete { ar ->
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime

            // 使用 PluginMetrics 记录指标
            val metrics = com.louloulin.apix.plugins.metrics.PluginMetrics.getInstance(vertx)
            if (ar.succeeded()) {
                logger.debug("插件 {} 执行成功，耗时 {} ms", plugin.id, executionTime)
                metrics.recordSuccess(plugin.id, executionTime)

                // 将上下文副本的变更合并到原始上下文
                mergeContextChanges(context, contextCopy)

                // 缓存成功的结果
                resultCache.put(context, plugin, resultFuture)
            } else {
                logger.error("插件 {} 执行失败，耗时 {} ms", plugin.id, executionTime, ar.cause())
                metrics.recordFailure(plugin.id, executionTime, ar.cause())

                // 发布插件错误事件
                publishPluginErrorEvent(plugin, context, ar.cause())
            }

            // 发布插件执行事件
            publishPluginExecutionEvent(plugin, context, executionTime, ar.succeeded(), ar.cause()?.message)
        }

        return resultFuture
    }

    /**
     * 创建路由上下文的副本，避免并发插件之间的竞态条件
     * 注意：实际实现中需要根据 Vert.x 的 API 进行调整
     */
    private fun cloneRoutingContext(original: RoutingContext): RoutingContext {
        // 在实际实现中，可能需要使用更复杂的方法来创建上下文的副本
        // 这里我们简化处理，直接返回原始上下文
        // 在实际实现中，可以使用装饰器模式或代理模式来创建一个安全的上下文副本
        return original
    }

    /**
     * 将上下文副本的变更合并到原始上下文
     * 注意：实际实现中需要根据 Vert.x 的 API 进行调整
     */
    private fun mergeContextChanges(original: RoutingContext, copy: RoutingContext) {
        // 在实际实现中，需要将副本上下文中的变更合并到原始上下文
        // 这里我们简化处理，因为我们使用的是同一个上下文对象
        // 在实际实现中，需要合并属性、响应头等信息
    }

    /**
     * 通过 EventBus 执行插件
     * 使用请求生命周期钩子
     */
    private fun executeViaEventBus(context: RoutingContext, plugin: Plugin, address: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 添加响应处理器来调用 onResponse 钩子
            context.addBodyEndHandler {
                try {
                    plugin.onResponse(context).onFailure { err ->
                        logger.error("插件 {} 的 onResponse 钩子执行失败", plugin.id, err)
                    }
                } catch (e: Exception) {
                    logger.error("插件 {} 的 onResponse 钩子抛出异常", plugin.id, e)
                }
            }

            // 添加错误处理器来调用 onError 钩子
            context.addEndHandler { ar ->
                if (ar.failed()) {
                    try {
                        plugin.onError(context, ar.cause()).onFailure { err ->
                            logger.error("插件 {} 的 onError 钩子执行失败", plugin.id, err)
                        }
                    } catch (e: Exception) {
                        logger.error("插件 {} 的 onError 钩子抛出异常", plugin.id, e)
                    }
                }
            }

            // 序列化上下文
            val contextJson = plugin.serializeContext(context)

            // 通过 EventBus 发送请求
            logger.debug("通过 EventBus 执行插件: {}, 地址: {}", plugin.id, address)

            // 添加请求类型标识
            contextJson.put("_requestType", "onRequest")

            vertx.eventBus().request<JsonObject>(address, contextJson) { ar ->
                if (ar.succeeded()) {
                    val response = ar.result().body()
                    val success = response.getBoolean("success", false)

                    if (success) {
                        // 处理成功响应
                        val updatedContextJson = response.getJsonObject("context")
                        if (updatedContextJson != null) {
                            // 更新上下文
                            updateContext(context, updatedContextJson)
                        }

                        promise.complete()
                    } else {
                        // 处理失败响应
                        val error = response.getString("error", "Unknown error")
                        val errorObj = Exception(error)

                        // 尝试调用 onError 钩子
                        try {
                            plugin.onError(context, errorObj).onComplete { _ ->
                                promise.fail(errorObj)
                            }
                        } catch (ex: Exception) {
                            logger.error("插件 {} 的 onError 钩子抛出异常", plugin.id, ex)
                            promise.fail(errorObj)
                        }
                    }
                } else {
                    // EventBus 请求失败
                    logger.error("通过 EventBus 执行插件失败: {}", plugin.id, ar.cause())

                    // 尝试调用 onError 钩子
                    try {
                        plugin.onError(context, ar.cause()).onComplete { _ ->
                            promise.fail(ar.cause())
                        }
                    } catch (ex: Exception) {
                        logger.error("插件 {} 的 onError 钩子抛出异常", plugin.id, ex)
                        promise.fail(ar.cause())
                    }
                }
            }
        } catch (e: Exception) {
            // 异常处理
            logger.error("通过 EventBus 执行插件异常: {}", plugin.id, e)

            // 尝试调用 onError 钩子
            try {
                plugin.onError(context, e).onComplete { _ ->
                    promise.fail(e)
                }
            } catch (ex: Exception) {
                logger.error("插件 {} 的 onError 钩子抛出异常", plugin.id, ex)
                promise.fail(e)
            }
        }

        return promise.future()
    }

    /**
     * 直接执行插件
     * 使用请求生命周期钩子
     */
    private fun executeDirectly(context: RoutingContext, plugin: Plugin): Future<Void> {
        try {
            // 直接执行插件
            logger.debug("直接执行插件: {}", plugin.id)

            // 添加响应处理器来调用 onResponse 钩子
            context.addBodyEndHandler {
                try {
                    plugin.onResponse(context).onFailure { err ->
                        logger.error("插件 {} 的 onResponse 钩子执行失败", plugin.id, err)
                    }
                } catch (e: Exception) {
                    logger.error("插件 {} 的 onResponse 钩子抛出异常", plugin.id, e)
                }
            }

            // 添加错误处理器来调用 onError 钩子
            context.addEndHandler { ar ->
                if (ar.failed()) {
                    try {
                        plugin.onError(context, ar.cause()).onFailure { err ->
                            logger.error("插件 {} 的 onError 钩子执行失败", plugin.id, err)
                        }
                    } catch (e: Exception) {
                        logger.error("插件 {} 的 onError 钩子抛出异常", plugin.id, e)
                    }
                }
            }

            // 执行插件的 onRequest 钩子
            return plugin.onRequest(context).recover { error ->
                // 记录错误
                logger.error("插件 {} 的 onRequest 钩子执行失败", plugin.id, error)

                // 发布插件错误事件
                publishPluginErrorEvent(plugin, context, error)

                // 检查是否应该继续执行
                if (shouldContinueOnError(plugin)) {
                    // 调用插件的错误处理方法
                    plugin.onError(context, error).compose { _ ->
                        // 如果错误处理成功，继续执行
                        Future.succeededFuture()
                    }
                } else {
                    // 传播错误
                    Future.failedFuture(error)
                }
            }
        } catch (e: Exception) {
            // 插件执行异常
            logger.error("插件执行异常: {}", plugin.id, e)

            // 发布插件错误事件
            publishPluginErrorEvent(plugin, context, e)

            // 检查是否应该继续执行
            if (shouldContinueOnError(plugin)) {
                // 尝试调用 onError 钩子
                try {
                    return plugin.onError(context, e).compose { _ ->
                        // 如果错误处理成功，继续执行
                        Future.succeededFuture()
                    }
                } catch (ex: Exception) {
                    logger.error("插件 {} 的 onError 钩子抛出异常", plugin.id, ex)
                    return Future.failedFuture(e)
                }
            } else {
                // 传播错误
                return Future.failedFuture(e)
            }
        }
    }

    /**
     * 更新上下文
     * 将从 EventBus 接收到的上下文数据应用到当前上下文
     */
    private fun updateContext(context: RoutingContext, updatedContextJson: JsonObject) {
        // 实际应用中需要实现完整的上下文更新逻辑
        // 这里只是一个简化的示例

        // 更新属性
        val attributes = updatedContextJson.getJsonObject("attributes")
        if (attributes != null) {
            attributes.forEach { entry ->
                context.put(entry.key, entry.value)
            }
        }

        // 更新响应头
        val headers = updatedContextJson.getJsonObject("response_headers")
        if (headers != null) {
            headers.forEach { entry ->
                context.response().putHeader(entry.key, entry.value.toString())
            }
        }
    }

    /**
     * 发布插件执行事件
     * 用于监控和指标收集
     */
    private fun publishPluginExecutionEvent(
        plugin: Plugin,
        context: RoutingContext,
        executionTime: Long,
        success: Boolean,
        errorMessage: String?
    ) {
        val event = JsonObject()
            .put("plugin_id", plugin.id)
            .put("plugin_type", plugin.type)
            .put("execution_time", executionTime)
            .put("success", success)
            .put("timestamp", System.currentTimeMillis())
            .put("path", context.request().path())
            .put("method", context.request().method().name())

        if (errorMessage != null) {
            event.put("error", errorMessage)
        }

        // 发布事件
        vertx.eventBus().publish("metrics.plugin.execution", event)
    }

    /**
     * 发布插件错误事件
     * 用于错误统计和分析
     */
    private fun publishPluginErrorEvent(
        plugin: Plugin,
        context: RoutingContext,
        error: Throwable
    ) {
        val event = JsonObject()
            .put("plugin_id", plugin.id)
            .put("plugin_type", plugin.type)
            .put("error_message", error.message)
            .put("error_type", error.javaClass.name)
            .put("timestamp", System.currentTimeMillis())
            .put("path", context.request().path())
            .put("method", context.request().method().name())

        // 发布事件
        vertx.eventBus().publish("plugin.error", event)
    }

    /**
     * 判断插件错误是否应该继续执行
     */
    private fun shouldContinueOnError(plugin: Plugin): Boolean {
        // 从插件配置中获取错误处理策略
        return plugin.config.getBoolean("continueOnError") ?: false
    }

    // 已移除缓存相关方法，使用 PluginResultCache 类代替

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
     *
     * @param plugin 插件，如果为null则清除所有缓存
     */
    fun clearCache(plugin: Plugin? = null) {
        resultCache.clear(plugin)
        if (plugin == null) {
            logger.info("所有插件执行结果缓存已清除")
        } else {
            logger.info("插件 {} 的执行结果缓存已清除", plugin.id)
        }
    }
}
