package com.louloulin.apix.core

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginType
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

/**
 * 用于测试的插件实现
 */
class TestPlugin(
    override val id: String,
    override val type: String,
    private val priority: Int,
    private val parallelExecution: Boolean = false,
    private val shouldExecuteValue: Boolean = true,
    private val shouldFail: Boolean = false,
    private val cacheable: Boolean = false
) : Plugin {
    override val config: PluginConfig = PluginConfig(
        id,
        type,
        JsonObject()
            .put("priority", priority)
            .put("parallelExecution", parallelExecution)
            .put("cacheable", cacheable)
    )

    /**
     * 获取插件是否可缓存
     */
    fun isCacheable(): Boolean {
        return cacheable
    }

    var executed = false
    var executionTime = 0L

    // 可自定义的执行函数
    var execute: (RoutingContext) -> Future<Void> = { context ->
        executed = true
        executionTime = System.currentTimeMillis()

        if (shouldFail) {
            Future.failedFuture("Plugin $id failed")
        } else {
            // 模拟一些处理时间
            try {
                Thread.sleep(10)
            } catch (e: InterruptedException) {
                // 忽略
            }

            Future.succeededFuture()
        }
    }

    override fun execute(context: RoutingContext): Future<Void> {
        return execute.invoke(context)
    }

    private var vertx: Vertx? = null

    override fun initialize(vertx: Vertx): Future<Void> {
        this.vertx = vertx
        return Future.succeededFuture()
    }

    override fun shutdown(returnFuture: Boolean): Future<Void>? {
        // 不需要实现
        return if (returnFuture) Future.succeededFuture() else null
    }

    override fun getPriority(): Int {
        return priority
    }

    override fun canExecuteInParallel(): Boolean {
        return parallelExecution
    }

    override fun shouldExecute(context: RoutingContext): Boolean {
        return shouldExecuteValue
    }

    override fun getEventBusAddress(): String? {
        return null // 测试插件不支持通过 EventBus 执行
    }

    override fun onRequest(context: RoutingContext): Future<Void> {
        // 标记为已执行
        executed = true
        executionTime = System.currentTimeMillis()

        // 使用自定义执行逻辑
        return execute.invoke(context)
    }
}
