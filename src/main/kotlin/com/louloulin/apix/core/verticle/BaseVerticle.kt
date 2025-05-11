package com.louloulin.apix.core.verticle

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 所有 Verticle 的基类，提供通用功能
 */
abstract class BaseVerticle : AbstractVerticle() {
    protected val logger = LoggerFactory.getLogger(this.javaClass)

    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting ${this.javaClass.simpleName}...")
        try {
            registerEventBusHandlers()
            onStart(startPromise)
        } catch (e: Exception) {
            logger.error("Failed to start ${this.javaClass.simpleName}", e)
            startPromise.fail(e)
        }
    }

    /**
     * 注册 EventBus 处理器
     */
    protected abstract fun registerEventBusHandlers()

    /**
     * Verticle 启动时的自定义逻辑
     */
    protected abstract fun onStart(startPromise: Promise<Void>)

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping ${this.javaClass.simpleName}...")
        try {
            onStop(stopPromise)
        } catch (e: Exception) {
            logger.error("Failed to stop ${this.javaClass.simpleName}", e)
            stopPromise.fail(e)
        }
    }

    /**
     * Verticle 停止时的自定义逻辑
     */
    protected open fun onStop(stopPromise: Promise<Void>) {
        // 默认实现，什么都不做
        stopPromise.complete()
    }

    /**
     * 发送成功响应
     */
    protected fun <T> sendSuccess(message: Message<T>, result: Any?, statusCode: Int = 200) {
        val response = JsonObject()
            .put("success", true)
            .put("statusCode", statusCode)

        if (result != null) {
            response.put("result", result)
        }

        message.reply(response)
    }

    /**
     * 发送错误响应
     */
    protected fun <T> sendError(message: Message<T>, error: Throwable) {
        val response = JsonObject()
            .put("success", false)
            .put("error", error.message)
        message.reply(response)
    }

    /**
     * 发送错误响应（带错误码）
     */
    protected fun <T> sendError(message: Message<T>, errorCode: Int, errorMessage: String) {
        val response = JsonObject()
            .put("success", false)
            .put("errorCode", errorCode)
            .put("error", errorMessage)
        message.reply(response)
    }
}
