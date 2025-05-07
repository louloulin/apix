package com.louloulin.apix.plugins.wasm

import io.vertx.core.Vertx
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
 * GraalVM WebAssembly 上下文测试
 */
@RunWith(VertxUnitRunner::class)
class GraalWasmContextTest {
    private lateinit var vertx: Vertx
    private lateinit var wasmContext: GraalWasmContext
    
    @Before
    fun setUp(testContext: TestContext) {
        // 跳过测试，如果没有 GraalVM WebAssembly 支持
        if (!isGraalVmWasmSupported()) {
            testContext.async().complete()
            return
        }
        
        vertx = Vertx.vertx()
        wasmContext = GraalWasmContext.getInstance(vertx)
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        if (::wasmContext.isInitialized) {
            wasmContext.close()
        }
        
        if (::vertx.isInitialized) {
            vertx.close(testContext.asyncAssertSuccess())
        } else {
            testContext.async().complete()
        }
    }
    
    @Test
    fun testLoadModule(testContext: TestContext) {
        // 跳过测试，如果没有 GraalVM WebAssembly 支持
        if (!isGraalVmWasmSupported()) {
            testContext.async().complete()
            return
        }
        
        val async = testContext.async()
        
        // 创建测试 WebAssembly 模块路径
        val modulePath = getTestWasmModulePath()
        
        // 如果测试模块不存在，跳过测试
        if (!Files.exists(Paths.get(modulePath))) {
            async.complete()
            return
        }
        
        // 加载模块
        wasmContext.loadModule(modulePath).onComplete { ar ->
            if (ar.succeeded()) {
                testContext.assertNotNull(ar.result())
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    @Test
    fun testInstantiateModule(testContext: TestContext) {
        // 跳过测试，如果没有 GraalVM WebAssembly 支持
        if (!isGraalVmWasmSupported()) {
            testContext.async().complete()
            return
        }
        
        val async = testContext.async()
        
        // 创建测试 WebAssembly 模块路径
        val modulePath = getTestWasmModulePath()
        
        // 如果测试模块不存在，跳过测试
        if (!Files.exists(Paths.get(modulePath))) {
            async.complete()
            return
        }
        
        // 加载并实例化模块
        wasmContext.loadModule(modulePath).compose { module ->
            wasmContext.instantiateModule(module)
        }.onComplete { ar ->
            if (ar.succeeded()) {
                testContext.assertNotNull(ar.result())
                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }
    
    /**
     * 检查是否支持 GraalVM WebAssembly
     */
    private fun isGraalVmWasmSupported(): Boolean {
        return try {
            // 尝试加载 GraalVM WebAssembly 类
            Class.forName("org.graalvm.polyglot.Context")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取测试 WebAssembly 模块路径
     */
    private fun getTestWasmModulePath(): String {
        // 测试用的 WebAssembly 模块路径
        return "src/test/resources/wasm/test_module.wasm"
    }
}
