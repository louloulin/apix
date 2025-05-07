package com.louloulin.apix.plugins

import io.vertx.core.Future
import io.vertx.ext.web.RoutingContext

/**
 * Interface for all plugins in the gateway.
 */
interface Plugin {
    /**
     * The unique identifier of the plugin.
     */
    val id: String

    /**
     * The type of the plugin.
     */
    val type: String

    /**
     * The configuration of the plugin.
     */
    val config: PluginConfig

    /**
     * 获取插件类型枚举
     */
    fun getPluginType(): PluginType {
        return PluginType.fromString(type)
    }

    /**
     * 获取插件优先级
     */
    fun getPriority(): Int {
        // 首先从配置中获取优先级，如果没有则使用插件类型的默认优先级
        return config.getInteger("priority") ?: getPluginType().priority
    }

    /**
     * 判断插件是否可以并行执行
     */
    fun canExecuteInParallel(): Boolean {
        // 默认从配置中获取，如果没有配置则默认为false
        return config.getBoolean("parallelExecution") ?: false
    }

    /**
     * 判断插件是否应该执行
     */
    fun shouldExecute(context: RoutingContext): Boolean {
        // 获取条件配置
        val conditionConfig = config.getJsonObject("condition")
        if (conditionConfig == null) {
            // 没有条件配置，默认执行
            return true
        }

        // TODO: 实现条件判断逻辑
        // 这里可以根据条件配置和上下文判断是否应该执行插件
        // 例如：根据请求路径、请求方法、请求头等条件判断

        return true
    }

    /**
     * Executes the plugin for the given routing context.
     *
     * @param context The routing context to process
     * @return A future that completes when the plugin has been executed
     */
    fun execute(context: RoutingContext): Future<Void>

    /**
     * Shuts down the plugin and releases any resources.
     */
    fun shutdown()
}
