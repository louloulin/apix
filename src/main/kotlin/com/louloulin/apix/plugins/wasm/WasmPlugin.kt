package com.louloulin.apix.plugins.wasm

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.io.File

/**
 * WebAssembly 插件接口
 * 支持使用 WebAssembly 编写的插件
 */
interface WasmPlugin : Plugin {
    /**
     * WebAssembly 模块路径
     */
    val wasmModulePath: String

    /**
     * WebAssembly 内存大小（页数，每页 64KB）
     */
    val wasmMemorySize: Int
        get() = 16 // 默认 16 页 (1MB)

    /**
     * 实例化 WebAssembly 模块
     */
    fun instantiateWasmModule(vertx: Vertx): Future<Any>

    /**
     * 调用 WebAssembly 函数
     */
    fun invokeWasmFunction(instance: Any, functionName: String, params: List<Any>): Future<Any>

    /**
     * 将请求上下文转换为 WebAssembly 参数
     */
    fun convertContextToWasmParams(context: RoutingContext): List<Any>

    /**
     * 处理 WebAssembly 函数返回结果
     */
    fun handleWasmResult(context: RoutingContext, result: Any)

    /**
     * 执行插件
     * 默认实现调用 WebAssembly 函数
     */
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        instantiateWasmModule(Vertx.currentContext().owner()).compose { instance ->
            // 将请求上下文转换为 WebAssembly 可理解的格式
            val params = convertContextToWasmParams(context)

            // 调用 WebAssembly 函数
            invokeWasmFunction(instance, "execute", params)
        }.onComplete { ar ->
            if (ar.succeeded()) {
                // 处理返回结果
                handleWasmResult(context, ar.result())
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }
}

/**
 * WebAssembly 插件基本实现
 */
abstract class BaseWasmPlugin(
    override val id: String,
    override val type: String,
    override val config: PluginConfig
) : WasmPlugin {
    protected val logger = LoggerFactory.getLogger(this.javaClass)

    /**
     * WebAssembly 模块路径
     */
    override val wasmModulePath: String = config.getString("wasmModulePath")
        ?: throw IllegalArgumentException("Missing wasmModulePath in plugin config")

    /**
     * WebAssembly 内存大小
     */
    override val wasmMemorySize: Int = config.getInteger("wasmMemorySize") ?: 16

    /**
     * 检查 WebAssembly 模块是否存在
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 检查 WebAssembly 模块文件是否存在
            val file = File(wasmModulePath)
            if (!file.exists() || !file.isFile) {
                promise.fail("WebAssembly module not found: $wasmModulePath")
                return promise.future()
            }

            logger.info("Initialized WebAssembly plugin: {}, module: {}", id, wasmModulePath)
            promise.complete()
        } catch (e: Exception) {
            logger.error("Failed to initialize WebAssembly plugin: {}", id, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 将请求上下文转换为 WebAssembly 参数
     */
    override fun convertContextToWasmParams(context: RoutingContext): List<Any> {
        // 创建一个简化的请求对象，可以被 WebAssembly 模块处理
        val requestObj = JsonObject()
            .put("path", context.request().path())
            .put("method", context.request().method().name())
            .put("headers", JsonObject())

        // 添加请求头
        context.request().headers().forEach { header ->
            requestObj.getJsonObject("headers").put(header.key, header.value)
        }

        // 添加请求参数
        val paramsObj = JsonObject()
        context.request().params().forEach { param ->
            paramsObj.put(param.key, param.value)
        }
        requestObj.put("params", paramsObj)

        // 如果有请求体，添加请求体
        if (context.getBody() != null) {
            requestObj.put("body", context.getBody()?.toString())
        }

        // 返回参数列表，只有一个参数，即请求对象
        return listOf(requestObj.encode())
    }

    /**
     * 处理 WebAssembly 函数返回结果
     */
    override fun handleWasmResult(context: RoutingContext, result: Any) {
        try {
            // 假设结果是 JSON 字符串
            val resultJson = JsonObject(result.toString())

            // 处理状态码
            val statusCode = resultJson.getInteger("statusCode", 200)
            context.response().setStatusCode(statusCode)

            // 处理响应头
            val headers = resultJson.getJsonObject("headers")
            if (headers != null) {
                headers.forEach { entry ->
                    context.response().putHeader(entry.key, entry.value.toString())
                }
            }

            // 处理响应体
            val body = resultJson.getString("body")
            if (body != null) {
                context.response().end(body)
            } else {
                context.response().end()
            }
        } catch (e: Exception) {
            logger.error("Failed to handle WebAssembly result", e)
            context.fail(e)
        }
    }
}
