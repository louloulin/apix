package com.louloulin.apix.plugins

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

/**
 * Interface for all plugins in the gateway.
 * 基于 Vert.x EventBus 的插件接口，支持远程执行和事件驱动模型
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
     * 请求生命周期钩子 - 请求阶段
     *
     * @param context 路由上下文
     * @return 处理完成的Future
     */
    fun onRequest(context: RoutingContext): Future<Void> = Future.succeededFuture()

    /**
     * 请求生命周期钩子 - 响应阶段
     *
     * @param context 路由上下文
     * @return 处理完成的Future
     */
    fun onResponse(context: RoutingContext): Future<Void> = Future.succeededFuture()

    /**
     * 请求生命周期钩子 - 错误阶段
     *
     * @param context 路由上下文
     * @param error 错误
     * @return 处理完成的Future
     */
    fun onError(context: RoutingContext, error: Throwable): Future<Void> = Future.succeededFuture()

    /**
     * 健康检查
     *
     * @return 健康检查结果
     */
    fun healthCheck(): Future<JsonObject> = Future.succeededFuture(JsonObject().put("status", "UP"))

    /**
     * Executes the plugin for the given routing context.
     * 默认实现调用 onRequest 方法
     *
     * @param context The routing context to process
     * @return A future that completes when the plugin has been executed
     */
    fun execute(context: RoutingContext): Future<Void> = onRequest(context)

    /**
     * 初始化插件
     *
     * @param vertx Vertx实例
     * @return 初始化完成的Future
     */
    fun initialize(vertx: Vertx): Future<Void> = Future.succeededFuture()

    /**
     * Shuts down the plugin and releases any resources.
     */
    fun shutdown() {}

    /**
     * Shuts down the plugin and releases any resources.
     *
     * @param returnFuture 是否返回 Future，用于兼容现有插件实现
     * @return 关闭完成的Future或null
     */
    fun shutdown(returnFuture: Boolean): Future<Void>? {
        shutdown()
        return if (returnFuture) Future.succeededFuture() else null
    }

    /**
     * 获取插件的EventBus地址
     * 如果返回null，则表示插件不支持通过EventBus执行
     *
     * @return EventBus地址或null
     */
    fun getEventBusAddress(): String? = "plugin.${id}"

    /**
     * 注册EventBus处理器
     * 用于处理通过EventBus发送的请求
     *
     * @param vertx Vertx实例
     * @return 注册完成的Future
     */
    fun registerEventBusHandlers(vertx: Vertx): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val address = getEventBusAddress()
            if (address != null) {
                // 注册处理器，处理通过EventBus发送的请求
                vertx.eventBus().consumer<JsonObject>(address) { message ->
                    try {
                        // 从消息中提取上下文信息
                        val contextJson = message.body()
                        val context = deserializeContext(vertx, contextJson)

                        // 执行插件
                        execute(context).onComplete { ar ->
                            if (ar.succeeded()) {
                                // 返回成功响应
                                message.reply(JsonObject()
                                    .put("success", true)
                                    .put("context", serializeContext(context)))
                            } else {
                                // 返回失败响应
                                message.reply(JsonObject()
                                    .put("success", false)
                                    .put("error", ar.cause().message))
                            }
                        }
                    } catch (e: Exception) {
                        // 处理异常
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", e.message))
                    }
                }
            }

            promise.complete()
        } catch (e: Exception) {
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 序列化上下文
     * 将RoutingContext转换为JsonObject，用于通过EventBus传输
     *
     * @param context 路由上下文
     * @return 序列化后的JsonObject
     */
    fun serializeContext(context: RoutingContext): JsonObject {
        // 简化实现，实际应用中需要更完整的序列化逻辑
        val json = JsonObject()
            .put("path", context.request().path())
            .put("method", context.request().method().name())
            .put("headers", JsonObject())

        // 添加请求头
        context.request().headers().forEach { header ->
            json.getJsonObject("headers").put(header.key, header.value)
        }

        // 添加请求参数
        val params = JsonObject()
        context.request().params().forEach { param ->
            params.put(param.key, param.value)
        }
        json.put("params", params)

        // 添加请求体
        if (context.getBody() != null) {
            json.put("body", context.getBody()?.toString())
        }

        // 添加自定义属性
        val attributes = JsonObject()
        context.data().forEach { (key, value) ->
            if (value is String || value is Number || value is Boolean) {
                attributes.put(key.toString(), value)
            }
        }
        json.put("attributes", attributes)

        return json
    }

    /**
     * 反序列化上下文
     * 将JsonObject转换为RoutingContext，用于从EventBus接收
     *
     * @param vertx Vertx实例
     * @param json 序列化的JsonObject
     * @return 反序列化后的RoutingContext
     */
    fun deserializeContext(vertx: Vertx, json: JsonObject): RoutingContext {
        // 实际应用中需要实现完整的反序列化逻辑
        // 这里只是一个简化的示例，实际实现可能需要使用模拟的RoutingContext
        throw UnsupportedOperationException("Context deserialization not implemented")
    }
}
