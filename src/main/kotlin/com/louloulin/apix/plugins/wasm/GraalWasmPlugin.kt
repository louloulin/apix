package com.louloulin.apix.plugins.wasm

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import org.graalvm.polyglot.Value

/**
 * 基于 GraalVM 的 WebAssembly 插件实现
 */
class GraalWasmPlugin(
    override val id: String,
    override val type: String,
    override val config: PluginConfig
) : BaseWasmPlugin(id, type, config) {
    // GraalVM WebAssembly 上下文管理器
    private lateinit var wasmContextManager: GraalWasmContext

    /**
     * 初始化 GraalVM WebAssembly 环境
     */
    override fun initialize(vertx: Vertx): Future<Void> {
        return super.initialize(vertx).compose { _ ->
            try {
                // 初始化 WebAssembly 上下文管理器
                wasmContextManager = GraalWasmContext.getInstance(vertx)

                // 加载 WebAssembly 模块
                return@compose wasmContextManager.loadModule(wasmModulePath).map { _ ->
                    logger.info("Initialized GraalVM WebAssembly plugin: {}", id)
                    null as Void?
                }
            } catch (e: Exception) {
                logger.error("Failed to initialize GraalVM WebAssembly plugin: {}", id, e)
                return@compose Future.failedFuture(e)
            }
        }
    }

    /**
     * 实例化 WebAssembly 模块
     */
    override fun instantiateWasmModule(vertx: Vertx): Future<Any> {
        return wasmContextManager.loadModule(wasmModulePath).compose { module ->
            wasmContextManager.instantiateModule(module).map { it as Any }
        }
    }

    /**
     * 调用 WebAssembly 函数
     */
    override fun invokeWasmFunction(instance: Any, functionName: String, params: List<Any>): Future<Any> {
        return wasmContextManager.invokeFunction(instance as Value, functionName, params)
    }

    /**
     * 关闭 WebAssembly 环境
     */
    override fun shutdown() {
        // 注意：我们不在这里关闭 wasmContextManager，因为它是单例实例，可能被其他插件使用
        logger.info("Shutting down WebAssembly plugin: {}", id)
    }
}
