package com.louloulin.apix.core.streaming

import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.ReadStream

/**
 * 模拟读取流，用于测试
 */
class MockReadStream : ReadStream<Buffer> {
    private var handler: Handler<Buffer>? = null
    private var endHandler: Handler<Void>? = null
    private var exceptionHandler: Handler<Throwable>? = null
    private var paused = false
    
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
     * 写入数据
     */
    fun write(buffer: Buffer) {
        if (!paused && handler != null) {
            handler!!.handle(buffer)
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
     * 触发异常
     */
    fun fail(t: Throwable) {
        if (exceptionHandler != null) {
            exceptionHandler!!.handle(t)
        }
    }
}
