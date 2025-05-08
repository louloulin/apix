package com.louloulin.apix.plugins.wasm

import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * WebAssembly 内存管理器
 * 用于管理 WebAssembly 模块的内存分配和释放
 */
class WasmMemoryManager {
    private val logger = LoggerFactory.getLogger(WasmMemoryManager::class.java)

    // 内存块分配表
    private val allocations = ConcurrentHashMap<Int, Int>()

    // 下一个可用的内存地址
    private val nextAddress = AtomicInteger(1024) // 从 1KB 开始，避开低地址区域

    /**
     * 分配内存
     *
     * @param size 需要分配的字节数
     * @return 分配的内存地址
     */
    fun allocate(size: Int): Int {
        val address = nextAddress.getAndAdd(size + 8) // 额外分配 8 字节用于存储大小信息
        allocations[address] = size
        logger.debug("分配内存: 地址={}, 大小={}", address, size)
        return address + 8 // 返回数据区域的地址
    }

    /**
     * 释放内存
     *
     * @param address 内存地址
     * @return 是否成功释放
     */
    fun free(address: Int): Boolean {
        val dataAddress = address - 8
        val size = allocations.remove(dataAddress)
        if (size != null) {
            logger.debug("释放内存: 地址={}, 大小={}", dataAddress, size)
            return true
        }
        logger.warn("尝试释放未分配的内存: 地址={}", address)
        return false
    }

    /**
     * 将字符串写入 WebAssembly 内存
     *
     * @param memory WebAssembly 内存
     * @param str 要写入的字符串
     * @return 字符串在内存中的地址
     */
    fun writeStringToMemory(memory: ByteBuffer, str: String): Int {
        val bytes = str.toByteArray(Charsets.UTF_8)
        val address = allocate(bytes.size)

        // 写入数据
        for (i in bytes.indices) {
            memory.put(address + i, bytes[i])
        }

        return address
    }

    /**
     * 从 WebAssembly 内存中读取字符串
     *
     * @param memory WebAssembly 内存
     * @param address 字符串在内存中的地址
     * @param length 字符串长度，如果为 -1 则读取到 null 终止符
     * @return 读取的字符串
     */
    fun readStringFromMemory(memory: ByteBuffer, address: Int, length: Int = -1): String {
        if (length >= 0) {
            // 读取指定长度
            val bytes = ByteArray(length)
            for (i in 0 until length) {
                bytes[i] = memory.get(address + i)
            }
            return String(bytes, Charsets.UTF_8)
        } else {
            // 读取到 null 终止符
            val buffer = Buffer.buffer()
            var i = 0
            while (true) {
                val byte = memory.get(address + i)
                if (byte.toInt() == 0) {
                    break
                }
                buffer.appendByte(byte)
                i++
            }
            return buffer.toString(Charsets.UTF_8)
        }
    }

    /**
     * 将 Buffer 写入 WebAssembly 内存
     *
     * @param memory WebAssembly 内存
     * @param buffer 要写入的 Buffer
     * @return Buffer 在内存中的地址
     */
    fun writeBufferToMemory(memory: ByteBuffer, buffer: Buffer): Int {
        val bytes = buffer.bytes
        val address = allocate(bytes.size)

        // 写入数据
        for (i in bytes.indices) {
            memory.put(address + i, bytes[i])
        }

        return address
    }

    /**
     * 从 WebAssembly 内存中读取 Buffer
     *
     * @param memory WebAssembly 内存
     * @param address Buffer 在内存中的地址
     * @param length Buffer 长度
     * @return 读取的 Buffer
     */
    fun readBufferFromMemory(memory: ByteBuffer, address: Int, length: Int): Buffer {
        val buffer = Buffer.buffer(length)
        for (i in 0 until length) {
            buffer.appendByte(memory.get(address + i))
        }
        return buffer
    }

    /**
     * 将 JSON 对象写入 WebAssembly 内存
     *
     * @param memory WebAssembly 内存
     * @param json 要写入的 JSON 对象
     * @return JSON 对象在内存中的地址
     */
    fun writeJsonToMemory(memory: ByteBuffer, json: Any): Int {
        val jsonStr = when (json) {
            is String -> json
            else -> json.toString()
        }
        return writeStringToMemory(memory, jsonStr)
    }

    /**
     * 从 WebAssembly 内存中读取 JSON 对象
     *
     * @param memory WebAssembly 内存
     * @param address JSON 对象在内存中的地址
     * @param length JSON 对象长度，如果为 -1 则读取到 null 终止符
     * @return 读取的 JSON 对象
     */
    fun readJsonFromMemory(memory: ByteBuffer, address: Int, length: Int = -1): io.vertx.core.json.JsonObject {
        val jsonStr = readStringFromMemory(memory, address, length)
        return try {
            io.vertx.core.json.JsonObject(jsonStr)
        } catch (e: Exception) {
            logger.error("Failed to parse JSON: {}", jsonStr, e)
            io.vertx.core.json.JsonObject()
        }
    }

    /**
     * 清理所有分配的内存
     */
    fun cleanup() {
        allocations.clear()
        nextAddress.set(1024)
        logger.debug("清理所有内存分配")
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: WasmMemoryManager? = null

        /**
         * 获取单例实例
         */
        fun getInstance(): WasmMemoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WasmMemoryManager().also { INSTANCE = it }
            }
        }
    }


}
