package com.louloulin.apix.core.io

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.OpenOptions
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.Pump
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * 零拷贝处理器，用于高效处理大文件和流数据。
 * 这个类使用 Vert.x 的零拷贝功能，避免不必要的内存复制，提高性能。
 */
class ZeroCopyHandler(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ZeroCopyHandler::class.java)
    
    // 统计信息
    private val totalBytesSent = AtomicLong(0)
    private val totalFilesSent = AtomicLong(0)
    private val totalStreamsSent = AtomicLong(0)
    
    /**
     * 使用零拷贝发送文件。
     * 
     * @param filePath 文件路径
     * @param response HTTP 响应
     * @return 包含操作结果的 Future
     */
    fun sendFile(filePath: String, response: HttpServerResponse): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 检查文件是否存在
        vertx.fileSystem().exists(filePath) { existsResult ->
            if (existsResult.failed() || !existsResult.result()) {
                val error = "文件不存在: $filePath"
                logger.warn(error)
                promise.fail(error)
                return@exists
            }
            
            // 获取文件属性
            vertx.fileSystem().props(filePath) { propsResult ->
                if (propsResult.failed()) {
                    val error = "无法获取文件属性: ${propsResult.cause().message}"
                    logger.warn(error)
                    promise.fail(error)
                    return@props
                }
                
                val props = propsResult.result()
                val fileSize = props.size()
                
                // 设置响应头
                response.putHeader("Content-Length", fileSize.toString())
                
                // 使用零拷贝发送文件
                response.sendFile(filePath) { sendResult ->
                    if (sendResult.succeeded()) {
                        // 更新统计信息
                        totalBytesSent.addAndGet(fileSize)
                        totalFilesSent.incrementAndGet()
                        
                        logger.debug("文件发送成功: {}, 大小: {} 字节", filePath, fileSize)
                        promise.complete()
                    } else {
                        val error = "文件发送失败: ${sendResult.cause().message}"
                        logger.warn(error)
                        promise.fail(sendResult.cause())
                    }
                }
            }
        }
        
        return promise.future()
    }
    
    /**
     * 使用零拷贝从文件到响应流。
     * 
     * @param filePath 文件路径
     * @param response HTTP 响应
     * @param bufferSize 缓冲区大小
     * @return 包含操作结果的 Future
     */
    fun streamFileToResponse(filePath: String, response: HttpServerResponse, bufferSize: Int = 8192): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 打开文件
        vertx.fileSystem().open(filePath, OpenOptions()) { openResult ->
            if (openResult.failed()) {
                val error = "无法打开文件: ${openResult.cause().message}"
                logger.warn(error)
                promise.fail(error)
                return@open
            }
            
            val asyncFile = openResult.result()
            
            // 获取文件大小
            vertx.fileSystem().props(filePath) { propsResult ->
                if (propsResult.failed()) {
                    asyncFile.close()
                    val error = "无法获取文件属性: ${propsResult.cause().message}"
                    logger.warn(error)
                    promise.fail(error)
                    return@props
                }
                
                val fileSize = propsResult.result().size()
                
                // 设置响应头
                response.putHeader("Content-Length", fileSize.toString())
                
                // 创建泵，将文件流传输到响应
                val pump = Pump.pump(asyncFile, response)
                
                // 设置结束处理器
                asyncFile.endHandler {
                    asyncFile.close { closeResult ->
                        if (closeResult.failed()) {
                            logger.warn("关闭文件失败: {}", closeResult.cause().message)
                        }
                        
                        // 更新统计信息
                        totalBytesSent.addAndGet(fileSize)
                        totalStreamsSent.incrementAndGet()
                        
                        logger.debug("文件流传输成功: {}, 大小: {} 字节", filePath, fileSize)
                        
                        // 结束响应
                        response.end()
                        promise.complete()
                    }
                }
                
                // 设置异常处理器
                asyncFile.exceptionHandler { e ->
                    asyncFile.close()
                    val error = "文件流传输失败: ${e.message}"
                    logger.warn(error)
                    promise.fail(e)
                }
                
                // 设置响应异常处理器
                response.exceptionHandler { e ->
                    asyncFile.close()
                    val error = "响应流传输失败: ${e.message}"
                    logger.warn(error)
                    promise.fail(e)
                }
                
                // 开始传输
                pump.start()
            }
        }
        
        return promise.future()
    }
    
    /**
     * 使用零拷贝从一个流到另一个流。
     * 
     * @param source 源流
     * @param target 目标流
     * @param bufferSize 缓冲区大小
     * @return 包含操作结果的 Future
     */
    fun streamToStream(source: io.vertx.core.streams.ReadStream<Buffer>, target: io.vertx.core.streams.WriteStream<Buffer>, bufferSize: Int = 8192): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 创建泵，将源流传输到目标流
        val pump = Pump.pump(source, target)
        
        // 设置结束处理器
        source.endHandler {
            logger.debug("流传输完成")
            promise.complete()
        }
        
        // 设置异常处理器
        source.exceptionHandler { e ->
            val error = "源流传输失败: ${e.message}"
            logger.warn(error)
            promise.fail(e)
        }
        
        target.exceptionHandler { e ->
            val error = "目标流传输失败: ${e.message}"
            logger.warn(error)
            promise.fail(e)
        }
        
        // 开始传输
        pump.start()
        
        return promise.future()
    }
    
    /**
     * 获取零拷贝统计信息。
     * 
     * @return 包含统计信息的 JsonObject
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("totalBytesSent", totalBytesSent.get())
            .put("totalFilesSent", totalFilesSent.get())
            .put("totalStreamsSent", totalStreamsSent.get())
    }
    
    companion object {
        // 单例实例
        private var INSTANCE: ZeroCopyHandler? = null
        
        /**
         * 获取 ZeroCopyHandler 的单例实例。
         * 
         * @param vertx Vertx 实例
         * @return ZeroCopyHandler 实例
         */
        fun getInstance(vertx: Vertx): ZeroCopyHandler {
            if (INSTANCE == null) {
                synchronized(ZeroCopyHandler::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = ZeroCopyHandler(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
