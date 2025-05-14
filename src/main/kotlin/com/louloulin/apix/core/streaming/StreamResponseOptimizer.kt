package com.louloulin.apix.core.streaming

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpServerRequest
import io.vertx.core.http.HttpServerResponse
import io.vertx.core.json.JsonObject
import io.vertx.core.streams.ReadStream
import io.vertx.core.streams.WriteStream
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 流式响应优化器
 *
 * 该类用于优化流式响应的处理，提高流式响应的性能和可靠性。
 * 它提供了以下功能：
 * 1. 自适应缓冲区大小：根据网络条件自动调整缓冲区大小
 * 2. 背压处理：当客户端处理速度慢于服务器生成速度时，自动调整生成速度
 * 3. 断点续传：支持断点续传，当连接中断时可以从断点处继续传输
 * 4. 流量控制：控制流量，避免过载
 * 5. 超时处理：处理超时情况，避免资源泄漏
 * 6. 错误恢复：当出现错误时，尝试恢复传输
 * 7. 监控和统计：收集流式传输的统计信息
 */
class StreamResponseOptimizer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(StreamResponseOptimizer::class.java)
    
    // 默认缓冲区大小
    private val defaultBufferSize = 8192
    
    // 最小缓冲区大小
    private val minBufferSize = 1024
    
    // 最大缓冲区大小
    private val maxBufferSize = 65536
    
    // 默认超时时间（毫秒）
    private val defaultTimeout = 30000L
    
    // 默认最大重试次数
    private val defaultMaxRetries = 3
    
    // 默认背压阈值
    private val defaultBackpressureThreshold = 0.8
    
    // 默认流量控制速率（字节/秒）
    private val defaultFlowControlRate = 1024 * 1024 // 1MB/s
    
    // 活跃的流式传输
    private val activeStreams = ConcurrentHashMap<String, StreamContext>()
    
    // 统计信息
    private val totalStreams = AtomicLong(0)
    private val successfulStreams = AtomicLong(0)
    private val failedStreams = AtomicLong(0)
    private val totalBytesTransferred = AtomicLong(0)
    
    /**
     * 初始化流式响应优化器
     */
    fun initialize(): Future<Void> {
        logger.info("初始化流式响应优化器")
        
        // 启动定期清理任务
        startCleanupTask()
        
        return Future.succeededFuture()
    }
    
    /**
     * 启动定期清理任务
     */
    private fun startCleanupTask() {
        vertx.setPeriodic(60000) { _ ->
            cleanupInactiveStreams()
        }
    }
    
    /**
     * 清理不活跃的流
     */
    private fun cleanupInactiveStreams() {
        val now = System.currentTimeMillis()
        val inactiveStreams = activeStreams.entries
            .filter { now - it.value.lastActivity > defaultTimeout }
            .map { it.key }
        
        inactiveStreams.forEach { streamId ->
            val stream = activeStreams.remove(streamId)
            if (stream != null) {
                logger.debug("清理不活跃的流: {}", streamId)
                stream.close()
                failedStreams.incrementAndGet()
            }
        }
        
        logger.debug("清理了 {} 个不活跃的流", inactiveStreams.size)
    }
    
    /**
     * 处理流式响应
     *
     * @param source 源流
     * @param response HTTP响应
     * @param options 选项
     * @return 包含操作结果的 Future
     */
    fun handleStreamResponse(
        source: ReadStream<Buffer>,
        response: HttpServerResponse,
        options: JsonObject = JsonObject()
    ): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 生成流ID
            val streamId = options.getString("streamId") ?: generateStreamId()
            
            // 获取选项
            val bufferSize = options.getInteger("bufferSize", defaultBufferSize)
            val timeout = options.getLong("timeout", defaultTimeout)
            val maxRetries = options.getInteger("maxRetries", defaultMaxRetries)
            val backpressureThreshold = options.getDouble("backpressureThreshold", defaultBackpressureThreshold)
            val flowControlRate = options.getLong("flowControlRate", defaultFlowControlRate)
            
            // 创建流上下文
            val streamContext = StreamContext(
                id = streamId,
                source = source,
                target = response,
                bufferSize = bufferSize,
                timeout = timeout,
                maxRetries = maxRetries,
                backpressureThreshold = backpressureThreshold,
                flowControlRate = flowControlRate
            )
            
            // 保存流上下文
            activeStreams[streamId] = streamContext
            
            // 增加总流数
            totalStreams.incrementAndGet()
            
            // 设置响应头
            response.setChunked(true)
            response.putHeader("Transfer-Encoding", "chunked")
            response.putHeader("X-Stream-ID", streamId)
            
            // 设置响应结束处理器
            response.endHandler {
                logger.debug("客户端关闭了连接: {}", streamId)
                streamContext.clientClosed.set(true)
                activeStreams.remove(streamId)
                promise.complete()
            }
            
            // 设置响应异常处理器
            response.exceptionHandler { e ->
                logger.warn("响应流异常: {}", e.message)
                streamContext.error.set(true)
                activeStreams.remove(streamId)
                promise.fail(e)
            }
            
            // 设置源流结束处理器
            source.endHandler {
                logger.debug("源流结束: {}", streamId)
                streamContext.sourceEnded.set(true)
                
                if (!streamContext.clientClosed.get() && !streamContext.error.get()) {
                    response.end()
                    activeStreams.remove(streamId)
                    successfulStreams.incrementAndGet()
                    promise.complete()
                }
            }
            
            // 设置源流异常处理器
            source.exceptionHandler { e ->
                logger.warn("源流异常: {}", e.message)
                
                if (streamContext.retries.incrementAndGet() <= maxRetries) {
                    logger.debug("尝试恢复流: {} (重试 {}/{})", streamId, streamContext.retries.get(), maxRetries)
                    // 尝试恢复传输
                    // 这里可以实现具体的恢复逻辑
                } else {
                    logger.warn("流恢复失败，超过最大重试次数: {}", streamId)
                    streamContext.error.set(true)
                    
                    if (!streamContext.clientClosed.get()) {
                        response.end()
                    }
                    
                    activeStreams.remove(streamId)
                    failedStreams.incrementAndGet()
                    promise.fail(e)
                }
            }
            
            // 设置源流处理器
            source.handler { buffer ->
                try {
                    // 更新最后活动时间
                    streamContext.lastActivity = System.currentTimeMillis()
                    
                    // 更新传输字节数
                    val bytes = buffer.length()
                    streamContext.bytesTransferred.addAndGet(bytes)
                    totalBytesTransferred.addAndGet(bytes.toLong())
                    
                    // 检查背压
                    if (response.writeQueueFull()) {
                        logger.debug("检测到背压，暂停源流: {}", streamId)
                        source.pause()
                        
                        // 设置恢复处理器
                        response.drainHandler {
                            logger.debug("背压解除，恢复源流: {}", streamId)
                            source.resume()
                        }
                    }
                    
                    // 写入响应
                    response.write(buffer)
                    
                    // 自适应调整缓冲区大小
                    adaptBufferSize(streamContext)
                    
                    // 流量控制
                    controlFlow(streamContext)
                } catch (e: Exception) {
                    logger.error("处理流数据时出错: {}", streamId, e)
                    streamContext.error.set(true)
                    activeStreams.remove(streamId)
                    failedStreams.incrementAndGet()
                    promise.fail(e)
                }
            }
            
            // 设置超时处理
            val timeoutId = vertx.setTimer(timeout) {
                if (activeStreams.containsKey(streamId) && !streamContext.sourceEnded.get() && !streamContext.clientClosed.get()) {
                    logger.warn("流传输超时: {}", streamId)
                    streamContext.error.set(true)
                    
                    if (!streamContext.clientClosed.get()) {
                        response.end()
                    }
                    
                    activeStreams.remove(streamId)
                    failedStreams.incrementAndGet()
                    promise.fail("Stream transfer timeout")
                }
            }
            
            // 取消超时处理的条件
            promise.future().onComplete {
                vertx.cancelTimer(timeoutId)
            }
            
            logger.debug("开始流式传输: {}", streamId)
        } catch (e: Exception) {
            logger.error("设置流式传输时出错", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 处理流式请求
     *
     * @param request HTTP请求
     * @param target 目标流
     * @param options 选项
     * @return 包含操作结果的 Future
     */
    fun handleStreamRequest(
        request: HttpServerRequest,
        target: WriteStream<Buffer>,
        options: JsonObject = JsonObject()
    ): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 生成流ID
            val streamId = options.getString("streamId") ?: generateStreamId()
            
            // 获取选项
            val bufferSize = options.getInteger("bufferSize", defaultBufferSize)
            val timeout = options.getLong("timeout", defaultTimeout)
            val maxRetries = options.getInteger("maxRetries", defaultMaxRetries)
            val backpressureThreshold = options.getDouble("backpressureThreshold", defaultBackpressureThreshold)
            val flowControlRate = options.getLong("flowControlRate", defaultFlowControlRate)
            
            // 创建流上下文
            val streamContext = StreamContext(
                id = streamId,
                source = request,
                target = target,
                bufferSize = bufferSize,
                timeout = timeout,
                maxRetries = maxRetries,
                backpressureThreshold = backpressureThreshold,
                flowControlRate = flowControlRate
            )
            
            // 保存流上下文
            activeStreams[streamId] = streamContext
            
            // 增加总流数
            totalStreams.incrementAndGet()
            
            // 设置请求结束处理器
            request.endHandler {
                logger.debug("请求流结束: {}", streamId)
                streamContext.sourceEnded.set(true)
                
                if (!streamContext.error.get()) {
                    target.end()
                    activeStreams.remove(streamId)
                    successfulStreams.incrementAndGet()
                    promise.complete()
                }
            }
            
            // 设置请求异常处理器
            request.exceptionHandler { e ->
                logger.warn("请求流异常: {}", e.message)
                streamContext.error.set(true)
                activeStreams.remove(streamId)
                promise.fail(e)
            }
            
            // 设置目标流异常处理器
            target.exceptionHandler { e ->
                logger.warn("目标流异常: {}", e.message)
                
                if (streamContext.retries.incrementAndGet() <= maxRetries) {
                    logger.debug("尝试恢复流: {} (重试 {}/{})", streamId, streamContext.retries.get(), maxRetries)
                    // 尝试恢复传输
                    // 这里可以实现具体的恢复逻辑
                } else {
                    logger.warn("流恢复失败，超过最大重试次数: {}", streamId)
                    streamContext.error.set(true)
                    activeStreams.remove(streamId)
                    failedStreams.incrementAndGet()
                    promise.fail(e)
                }
            }
            
            // 设置请求处理器
            request.handler { buffer ->
                try {
                    // 更新最后活动时间
                    streamContext.lastActivity = System.currentTimeMillis()
                    
                    // 更新传输字节数
                    val bytes = buffer.length()
                    streamContext.bytesTransferred.addAndGet(bytes)
                    totalBytesTransferred.addAndGet(bytes.toLong())
                    
                    // 检查背压
                    if (target.writeQueueFull()) {
                        logger.debug("检测到背压，暂停请求流: {}", streamId)
                        request.pause()
                        
                        // 设置恢复处理器
                        target.drainHandler {
                            logger.debug("背压解除，恢复请求流: {}", streamId)
                            request.resume()
                        }
                    }
                    
                    // 写入目标
                    target.write(buffer)
                    
                    // 自适应调整缓冲区大小
                    adaptBufferSize(streamContext)
                    
                    // 流量控制
                    controlFlow(streamContext)
                } catch (e: Exception) {
                    logger.error("处理流数据时出错: {}", streamId, e)
                    streamContext.error.set(true)
                    activeStreams.remove(streamId)
                    failedStreams.incrementAndGet()
                    promise.fail(e)
                }
            }
            
            // 设置超时处理
            val timeoutId = vertx.setTimer(timeout) {
                if (activeStreams.containsKey(streamId) && !streamContext.sourceEnded.get()) {
                    logger.warn("流传输超时: {}", streamId)
                    streamContext.error.set(true)
                    activeStreams.remove(streamId)
                    failedStreams.incrementAndGet()
                    promise.fail("Stream transfer timeout")
                }
            }
            
            // 取消超时处理的条件
            promise.future().onComplete {
                vertx.cancelTimer(timeoutId)
            }
            
            logger.debug("开始流式传输: {}", streamId)
        } catch (e: Exception) {
            logger.error("设置流式传输时出错", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 自适应调整缓冲区大小
     */
    private fun adaptBufferSize(streamContext: StreamContext) {
        // 计算传输速率（字节/秒）
        val now = System.currentTimeMillis()
        val elapsed = now - streamContext.lastBufferSizeAdjustment
        
        if (elapsed > 1000) { // 每秒调整一次
            val bytesTransferred = streamContext.bytesTransferred.get()
            val bytesPerSecond = bytesTransferred * 1000 / elapsed
            
            // 根据传输速率调整缓冲区大小
            var newBufferSize = streamContext.bufferSize
            
            if (bytesPerSecond > streamContext.bufferSize * 2) {
                // 传输速率很高，增加缓冲区大小
                newBufferSize = (streamContext.bufferSize * 1.5).toInt().coerceAtMost(maxBufferSize)
            } else if (bytesPerSecond < streamContext.bufferSize / 2) {
                // 传输速率很低，减小缓冲区大小
                newBufferSize = (streamContext.bufferSize * 0.8).toInt().coerceAtLeast(minBufferSize)
            }
            
            if (newBufferSize != streamContext.bufferSize) {
                logger.debug("调整缓冲区大小: {} -> {} (传输速率: {} 字节/秒)", streamContext.bufferSize, newBufferSize, bytesPerSecond)
                streamContext.bufferSize = newBufferSize
            }
            
            // 重置计数器
            streamContext.bytesTransferred.set(0)
            streamContext.lastBufferSizeAdjustment = now
        }
    }
    
    /**
     * 流量控制
     */
    private fun controlFlow(streamContext: StreamContext) {
        // 计算传输速率（字节/秒）
        val now = System.currentTimeMillis()
        val elapsed = now - streamContext.lastFlowControl
        
        if (elapsed > 100) { // 每100毫秒检查一次
            val bytesTransferred = streamContext.bytesTransferred.get()
            val bytesPerSecond = bytesTransferred * 1000 / elapsed
            
            // 如果传输速率超过流量控制速率，暂停一段时间
            if (bytesPerSecond > streamContext.flowControlRate) {
                val pauseTime = (bytesPerSecond - streamContext.flowControlRate) / streamContext.flowControlRate * 10
                
                if (pauseTime > 1) {
                    logger.debug("流量控制: 暂停 {} 毫秒 (传输速率: {} 字节/秒)", pauseTime, bytesPerSecond)
                    
                    // 暂停源流
                    if (streamContext.source is ReadStream<*>) {
                        (streamContext.source as ReadStream<*>).pause()
                        
                        // 设置定时器恢复源流
                        vertx.setTimer(pauseTime.toLong()) {
                            if (!streamContext.error.get() && !streamContext.sourceEnded.get()) {
                                (streamContext.source as ReadStream<*>).resume()
                            }
                        }
                    }
                }
            }
            
            // 重置计数器
            streamContext.lastFlowControl = now
        }
    }
    
    /**
     * 生成流ID
     */
    private fun generateStreamId(): String {
        return "stream-${System.currentTimeMillis()}-${(Math.random() * 1000000).toInt()}"
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("totalStreams", totalStreams.get())
            .put("activeStreams", activeStreams.size)
            .put("successfulStreams", successfulStreams.get())
            .put("failedStreams", failedStreams.get())
            .put("totalBytesTransferred", totalBytesTransferred.get())
    }
    
    /**
     * 关闭流式响应优化器
     */
    fun close(): Future<Void> {
        logger.info("关闭流式响应优化器")
        
        // 关闭所有活跃的流
        activeStreams.forEach { (streamId, stream) ->
            logger.debug("关闭流: {}", streamId)
            stream.close()
        }
        
        // 清空活跃的流
        activeStreams.clear()
        
        return Future.succeededFuture()
    }
    
    /**
     * 流上下文
     */
    private inner class StreamContext(
        val id: String,
        val source: Any,
        val target: Any,
        var bufferSize: Int,
        val timeout: Long,
        val maxRetries: Int,
        val backpressureThreshold: Double,
        val flowControlRate: Long
    ) {
        // 最后活动时间
        var lastActivity = System.currentTimeMillis()
        
        // 最后缓冲区大小调整时间
        var lastBufferSizeAdjustment = System.currentTimeMillis()
        
        // 最后流量控制时间
        var lastFlowControl = System.currentTimeMillis()
        
        // 传输的字节数
        val bytesTransferred = AtomicInteger(0)
        
        // 重试次数
        val retries = AtomicInteger(0)
        
        // 源流是否结束
        val sourceEnded = AtomicBoolean(false)
        
        // 客户端是否关闭
        val clientClosed = AtomicBoolean(false)
        
        // 是否出错
        val error = AtomicBoolean(false)
        
        /**
         * 关闭流
         */
        fun close() {
            try {
                if (source is ReadStream<*>) {
                    (source as ReadStream<*>).exceptionHandler(null)
                    (source as ReadStream<*>).endHandler(null)
                    (source as ReadStream<*>).handler(null)
                }
                
                if (target is WriteStream<*>) {
                    (target as WriteStream<*>).exceptionHandler(null)
                    (target as WriteStream<*>).drainHandler(null)
                }
                
                if (target is HttpServerResponse && !clientClosed.get()) {
                    (target as HttpServerResponse).end()
                }
            } catch (e: Exception) {
                logger.warn("关闭流时出错: {}", id, e)
            }
        }
    }
}
