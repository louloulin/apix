package com.louloulin.apix.core.streaming

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.buffer.Buffer
import io.vertx.core.streams.WriteStream
import java.util.LinkedList
import java.util.Queue

/**
 * 一个简单的Buffer写入流实现
 */
class BufferWriteStream : WriteStream<Buffer> {
    private var drainHandler: Handler<Void>? = null
    private var exceptionHandler: Handler<Throwable>? = null
    private var writeQueueFull = false
    private val queue: Queue<Buffer> = LinkedList()
    private var handler: Handler<Buffer>? = null

    override fun write(data: Buffer): Future<Void> {
        if (writeQueueFull) {
            queue.add(data)
        } else {
            deliverBuffer(data)
        }
        return Future.succeededFuture()
    }

    override fun write(data: Buffer, handler: Handler<io.vertx.core.AsyncResult<Void>>): Unit {
        write(data)
            .onComplete(handler)
    }

    override fun end(): Future<Void> {
        return Future.succeededFuture()
    }

    override fun end(handler: Handler<io.vertx.core.AsyncResult<Void>>): Unit {
        end().onComplete(handler)
    }

    override fun setWriteQueueMaxSize(maxSize: Int): WriteStream<Buffer> {
        return this
    }

    override fun writeQueueFull(): Boolean {
        return writeQueueFull
    }

    override fun drainHandler(handler: Handler<Void>?): WriteStream<Buffer> {
        this.drainHandler = handler
        return this
    }

    override fun exceptionHandler(handler: Handler<Throwable>?): WriteStream<Buffer> {
        this.exceptionHandler = handler
        return this
    }

    /**
     * 设置处理器
     */
    fun handler(handler: Handler<Buffer>): BufferWriteStream {
        this.handler = handler
        return this
    }

    /**
     * 设置写入队列已满
     */
    fun setWriteQueueFull(full: Boolean) {
        val wasFull = writeQueueFull
        writeQueueFull = full

        if (wasFull && !full && drainHandler != null) {
            drainHandler!!.handle(null)
        }
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
     * 传递数据
     */
    private fun deliverBuffer(buffer: Buffer) {
        if (handler != null) {
            handler!!.handle(buffer)
        }
    }
}
