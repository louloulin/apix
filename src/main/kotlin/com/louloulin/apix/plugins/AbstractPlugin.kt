package com.louloulin.apix.plugins

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.ext.web.RoutingContext

/**
 * 抽象插件基类
 * 提供插件接口的默认实现
 */
abstract class AbstractPlugin : Plugin {
    /**
     * 初始化插件
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        return Future.succeededFuture()
    }

    /**
     * 关闭插件
     */
    override fun shutdown() {
        // 默认实现不做任何事情
    }

    /**
     * 请求阶段钩子
     */
    override fun onRequest(context: RoutingContext): Future<Void> {
        return Future.succeededFuture()
    }

    /**
     * 响应阶段钩子
     */
    override fun onResponse(context: RoutingContext): Future<Void> {
        return Future.succeededFuture()
    }

    /**
     * 错误阶段钩子
     */
    override fun onError(context: RoutingContext, error: Throwable): Future<Void> {
        return Future.succeededFuture()
    }

    /**
     * 执行插件
     * 默认实现调用 onRequest 方法
     */
    override fun execute(context: RoutingContext): Future<Void> {
        return onRequest(context)
    }
}
