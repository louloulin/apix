package com.louloulin.apix.plugins.wasm

import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginRegistry
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * WebAssembly 插件测试
 */
@RunWith(VertxUnitRunner::class)
class WasmPluginTest {
    private lateinit var vertx: Vertx
    private lateinit var registry: PluginRegistry
    
    @Before
    fun setUp(testContext: TestContext) {
        vertx = Vertx.vertx()
        registry = PluginRegistry(vertx)
        
        // 注册 WebAssembly 插件工厂
        registry.registerFactory("wasm-plugin", WasmPluginFactory())
        
        // 等待设置完成
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        vertx.close(testContext.asyncAssertSuccess())
    }
    
    @Test
    fun testWasmPluginRegistration(testContext: TestContext) {
        // 跳过测试，如果没有 WebAssembly 模块
        if (!isWasmModuleAvailable()) {
            testContext.async().complete()
            return
        }
        
        val async = testContext.async()
        
        // 创建测试配置
        val config = JsonObject()
            .put("id", "test-wasm-plugin")
            .put("type", "wasm-plugin")
            .put("priority", 200)
            .put("wasmModulePath", getWasmModulePath())
        
        // 加载插件
        registry.loadPlugin(PluginConfig("test-wasm-plugin", "wasm-plugin", config)).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.assertNotNull(registry.getPlugin("test-wasm-plugin"))
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    /**
     * 检查 WebAssembly 模块是否可用
     */
    private fun isWasmModuleAvailable(): Boolean {
        return Files.exists(Paths.get(getWasmModulePath()))
    }
    
    /**
     * 获取 WebAssembly 模块路径
     */
    private fun getWasmModulePath(): String {
        // 测试用的 WebAssembly 模块路径
        return "src/test/resources/wasm/test_plugin.wasm"
    }
}
