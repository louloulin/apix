package com.louloulin.apix.plugins.wasm

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory

/**
 * WebAssembly 插件工厂
 * 用于创建 WebAssembly 插件实例
 */
class WasmPluginFactory : PluginFactory {
    /**
     * 创建 WebAssembly 插件实例
     */
    override fun create(config: PluginConfig): Plugin {
        return GraalWasmPlugin(config.id, config.type, config)
    }
}
