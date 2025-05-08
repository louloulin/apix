package com.louloulin.apix.plugins.wasm

import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * WebAssembly 内存管理器测试
 */
@RunWith(VertxUnitRunner::class)
class WasmMemoryManagerTest {
    
    private lateinit var memoryManager: WasmMemoryManager
    private lateinit var memory: ByteBuffer
    
    @Before
    fun setUp(testContext: TestContext) {
        memoryManager = WasmMemoryManager.getInstance()
        memory = ByteBuffer.allocate(1024 * 1024) // 1MB
        testContext.async().complete()
    }
    
    @After
    fun tearDown(testContext: TestContext) {
        memoryManager.cleanup()
        testContext.async().complete()
    }
    
    /**
     * 测试内存分配和释放
     */
    @Test
    fun testAllocateAndFree(testContext: TestContext) {
        val async = testContext.async()
        
        // 分配内存
        val address = memoryManager.allocate(100)
        
        // 验证地址大于0
        testContext.assertTrue(address > 0)
        
        // 释放内存
        val freed = memoryManager.free(address)
        
        // 验证释放成功
        testContext.assertTrue(freed)
        
        async.complete()
    }
    
    /**
     * 测试字符串读写
     */
    @Test
    fun testStringReadWrite(testContext: TestContext) {
        val async = testContext.async()
        
        // 测试字符串
        val testString = "Hello, WebAssembly!"
        
        // 写入内存
        val address = memoryManager.writeStringToMemory(memory, testString)
        
        // 读取内存
        val readString = memoryManager.readStringFromMemory(memory, address, testString.length)
        
        // 验证读取的字符串与原字符串相同
        testContext.assertEquals(testString, readString)
        
        async.complete()
    }
    
    /**
     * 测试 Buffer 读写
     */
    @Test
    fun testBufferReadWrite(testContext: TestContext) {
        val async = testContext.async()
        
        // 测试 Buffer
        val testBuffer = Buffer.buffer("Hello, Buffer!")
        
        // 写入内存
        val address = memoryManager.writeBufferToMemory(memory, testBuffer)
        
        // 读取内存
        val readBuffer = memoryManager.readBufferFromMemory(memory, address, testBuffer.length())
        
        // 验证读取的 Buffer 与原 Buffer 相同
        testContext.assertEquals(testBuffer.toString(), readBuffer.toString())
        
        async.complete()
    }
    
    /**
     * 测试 JSON 读写
     */
    @Test
    fun testJsonReadWrite(testContext: TestContext) {
        val async = testContext.async()
        
        // 测试 JSON
        val testJson = JsonObject()
            .put("name", "WebAssembly")
            .put("version", 1)
            .put("features", JsonObject()
                .put("memory", true)
                .put("tables", true)
            )
        
        // 写入内存
        val address = memoryManager.writeJsonToMemory(memory, testJson)
        
        // 读取内存
        val readJson = memoryManager.readJsonFromMemory(memory, address)
        
        // 验证读取的 JSON 与原 JSON 相同
        testContext.assertEquals(testJson.getString("name"), readJson.getString("name"))
        testContext.assertEquals(testJson.getInteger("version"), readJson.getInteger("version"))
        testContext.assertTrue(readJson.getJsonObject("features").getBoolean("memory"))
        
        async.complete()
    }
    
    /**
     * 测试内存清理
     */
    @Test
    fun testCleanup(testContext: TestContext) {
        val async = testContext.async()
        
        // 分配多个内存块
        val addresses = mutableListOf<Int>()
        for (i in 1..10) {
            addresses.add(memoryManager.allocate(100))
        }
        
        // 清理内存
        memoryManager.cleanup()
        
        // 尝试释放已清理的内存，应该失败
        for (address in addresses) {
            val freed = memoryManager.free(address)
            testContext.assertFalse(freed)
        }
        
        async.complete()
    }
}
