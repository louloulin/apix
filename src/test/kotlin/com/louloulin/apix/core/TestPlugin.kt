package com.louloulin.apix.core

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginType
import io.vertx.core.Future
import io.vertx.core.Promise
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

    override fun shutdown() {
        // 不需要实现
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
}
