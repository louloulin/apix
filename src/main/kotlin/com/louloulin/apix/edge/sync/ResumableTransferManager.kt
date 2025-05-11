package com.louloulin.apix.edge.sync

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 断点续传管理器，用于管理大文件的断点续传。
 */
class ResumableTransferManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ResumableTransferManager::class.java)
    
    // 传输状态映射
    private val transfers = ConcurrentHashMap<String, TransferState>()
    
    // 临时文件目录
    private val tempDir = "temp"
    
    init {
        // 确保临时目录存在
        vertx.fileSystem().mkdirs(tempDir) { ar ->
            if (ar.failed()) {
                logger.error("创建临时目录失败", ar.cause())
            }
        }
    }
    
    /**
     * 开始新的传输。
     *
     * @param dataType 数据类型
     * @param totalSize 总大小
     * @return Future<String> 传输ID
     */
    fun startTransfer(dataType: String, totalSize: Long): Future<String> {
        val promise = Promise.promise<String>()
        
        try {
            // 生成传输ID
            val transferId = UUID.randomUUID().toString()
            
            // 创建临时文件
            val tempFile = "$tempDir/$transferId"
            
            vertx.fileSystem().open(tempFile, OpenOptions().setCreate(true).setWrite(true)) { ar ->
                if (ar.succeeded()) {
                    val file = ar.result()
                    
                    // 关闭文件
                    file.close { closeAr ->
                        if (closeAr.succeeded()) {
                            // 创建传输状态
                            val state = TransferState(transferId, dataType, tempFile, totalSize)
                            
                            // 保存传输状态
                            transfers[transferId] = state
                            
                            promise.complete(transferId)
                        } else {
                            promise.fail(closeAr.cause())
                        }
                    }
                } else {
                    promise.fail(ar.cause())
                }
            }
        } catch (e: Exception) {
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 写入数据块。
     *
     * @param transferId 传输ID
     * @param offset 偏移量
     * @param data 数据
     * @return Future<Long> 当前偏移量
     */
    fun writeChunk(transferId: String, offset: Long, data: Buffer): Future<Long> {
        val promise = Promise.promise<Long>()
        
        // 获取传输状态
        val state = transfers[transferId]
        
        if (state == null) {
            promise.fail("Transfer not found: $transferId")
            return promise.future()
        }
        
        // 检查偏移量
        if (offset != state.currentOffset) {
            promise.fail("Invalid offset: expected ${state.currentOffset}, got $offset")
            return promise.future()
        }
        
        // 打开文件
        vertx.fileSystem().open(state.tempFile, OpenOptions().setWrite(true)) { ar ->
            if (ar.succeeded()) {
                val file = ar.result()
                
                // 写入数据
                file.write(data, offset) { writeAr ->
                    if (writeAr.succeeded()) {
                        // 更新偏移量
                        val newOffset = offset + data.length()
                        state.currentOffset = newOffset
                        
                        // 关闭文件
                        file.close { closeAr ->
                            if (closeAr.succeeded()) {
                                promise.complete(newOffset)
                            } else {
                                promise.fail(closeAr.cause())
                            }
                        }
                    } else {
                        file.close()
                        promise.fail(writeAr.cause())
                    }
                }
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 完成传输。
     *
     * @param transferId 传输ID
     * @return Future<JsonObject> 完成结果
     */
    fun completeTransfer(transferId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 获取传输状态
        val state = transfers[transferId]
        
        if (state == null) {
            promise.fail("Transfer not found: $transferId")
            return promise.future()
        }
        
        // 检查是否传输完成
        if (state.currentOffset != state.totalSize) {
            promise.fail("Transfer not complete: ${state.currentOffset}/${state.totalSize}")
            return promise.future()
        }
        
        // 读取临时文件
        vertx.fileSystem().readFile(state.tempFile) { ar ->
            if (ar.succeeded()) {
                val data = ar.result()
                
                try {
                    // 解析数据
                    val jsonData = JsonObject(data)
                    
                    // 删除临时文件
                    vertx.fileSystem().delete(state.tempFile) { deleteAr ->
                        if (deleteAr.failed()) {
                            logger.warn("删除临时文件失败: ${state.tempFile}", deleteAr.cause())
                        }
                    }
                    
                    // 移除传输状态
                    transfers.remove(transferId)
                    
                    // 返回结果
                    promise.complete(JsonObject()
                        .put("transferId", transferId)
                        .put("dataType", state.dataType)
                        .put("size", state.totalSize)
                        .put("data", jsonData)
                    )
                } catch (e: Exception) {
                    promise.fail("Invalid JSON data: ${e.message}")
                }
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 恢复传输。
     *
     * @param transferId 传输ID
     * @param offset 偏移量
     * @return Future<JsonObject> 恢复结果
     */
    fun resumeTransfer(transferId: String, offset: Long): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 获取传输状态
        val state = transfers[transferId]
        
        if (state == null) {
            promise.fail("Transfer not found: $transferId")
            return promise.future()
        }
        
        // 检查偏移量
        if (offset > state.totalSize) {
            promise.fail("Invalid offset: $offset > ${state.totalSize}")
            return promise.future()
        }
        
        // 检查文件是否存在
        vertx.fileSystem().exists(state.tempFile) { ar ->
            if (ar.succeeded() && ar.result()) {
                // 更新偏移量
                state.currentOffset = offset
                
                promise.complete(JsonObject()
                    .put("transferId", transferId)
                    .put("dataType", state.dataType)
                    .put("totalSize", state.totalSize)
                    .put("currentOffset", offset)
                    .put("remaining", state.totalSize - offset)
                )
            } else {
                promise.fail("Temp file not found: ${state.tempFile}")
            }
        }
        
        return promise.future()
    }
    
    /**
     * 取消传输。
     *
     * @param transferId 传输ID
     * @return Future<Void> 取消结果
     */
    fun cancelTransfer(transferId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 获取传输状态
        val state = transfers[transferId]
        
        if (state == null) {
            promise.fail("Transfer not found: $transferId")
            return promise.future()
        }
        
        // 删除临时文件
        vertx.fileSystem().delete(state.tempFile) { ar ->
            if (ar.succeeded()) {
                // 移除传输状态
                transfers.remove(transferId)
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取传输状态。
     *
     * @param transferId 传输ID
     * @return TransferState? 传输状态
     */
    fun getTransferState(transferId: String): TransferState? {
        return transfers[transferId]
    }
    
    /**
     * 获取所有传输状态。
     *
     * @return Map<String, TransferState> 所有传输状态
     */
    fun getAllTransferStates(): Map<String, TransferState> {
        return transfers.toMap()
    }
    
    /**
     * 清理过期的传输。
     *
     * @param maxAgeMs 最大年龄（毫秒）
     * @return Int 清理的传输数量
     */
    fun cleanupExpiredTransfers(maxAgeMs: Long): Int {
        val now = System.currentTimeMillis()
        var count = 0
        
        // 查找过期的传输
        val expiredIds = transfers.entries
            .filter { now - it.value.startTime > maxAgeMs }
            .map { it.key }
        
        // 取消过期的传输
        for (id in expiredIds) {
            cancelTransfer(id)
            count++
        }
        
        return count
    }
    
    /**
     * 传输状态类，表示一个文件传输的状态。
     *
     * @param id 传输ID
     * @param dataType 数据类型
     * @param tempFile 临时文件路径
     * @param totalSize 总大小
     * @param currentOffset 当前偏移量
     * @param startTime 开始时间
     */
    data class TransferState(
        val id: String,
        val dataType: String,
        val tempFile: String,
        val totalSize: Long,
        var currentOffset: Long = 0,
        val startTime: Long = System.currentTimeMillis()
    )
}
