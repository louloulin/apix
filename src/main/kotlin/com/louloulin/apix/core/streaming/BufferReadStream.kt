package com.louloulin.apix.core.streaming

import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream
import java.util.LinkedList
import java.util.Queue

/**
 * 一个简单的Buffer读取流实现
 */
class BufferReadStream : ReadStream<Buffer> {
    private var handler: Handler<Buffer>? = null
    private var endHandler: Handler<Void>? = null
    private var exceptionHandler: Handler<Throwable>? = null
    private var paused = false
    private val queue: Queue<Buffer> = LinkedList()
    
    override fun handler(handler: Handler<Buffer>?): ReadStream<Buffer> {
        this.handler = handler
        return this
    }
    
    override fun pause(): ReadStream<Buffer> {
        paused = true
        return this
    }
    
    override fun resume(): ReadStream<Buffer> {
        paused = false
        processQueue()
        return this
    }
    
    override fun fetch(amount: Long): ReadStream<Buffer> {
        return resume()
    }
    
    override fun endHandler(endHandler: Handler<Void>?): ReadStream<Buffer> {
        this.endHandler = endHandler
        return this
    }
    
    override fun exceptionHandler(exceptionHandler: Handler<Throwable>?): ReadStream<Buffer> {
        this.exceptionHandler = exceptionHandler
        return this
    }
    
    /**
     * 处理数据
     */
    fun handle(buffer: Buffer) {
        if (paused) {
            queue.add(buffer)
        } else {
            deliverBuffer(buffer)
        }
    }
    
    /**
     * 结束流
     */
    fun end() {
        if (endHandler != null) {
            endHandler!!.handle(null)
        }
    }
    
    /**
     * 关闭流
     */
    fun close() {
        queue.clear()
        handler = null
        endHandler = null
        exceptionHandler = null
    }
    
    /**
     * 处理异常
     */
    fun handleException(t: Throwable) {
        if (exceptionHandler != null) {
            exceptionHandler!!.handle(t)
        }
    }
    
    /**
     * 处理队列中的数据
     */
    private fun processQueue() {
        if (!paused) {
            while (!queue.isEmpty()) {
                deliverBuffer(queue.poll())
            }
        }
    }
    
    /**
     * 传递数据
     */
    private fun deliverBuffer(buffer: Buffer) {
        if (handler != null) {
            handler!!.handle(buffer)
        }
    }
}
