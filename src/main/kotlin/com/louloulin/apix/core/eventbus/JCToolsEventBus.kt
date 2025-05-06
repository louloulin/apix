package com.louloulin.apix.core.eventbus

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.MultiMap
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.eventbus.EventBus
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageCodec
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.eventbus.MessageProducer
import io.vertx.core.json.JsonObject
import org.jctools.queues.MpscArrayQueue
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

/**
 * 基于JCTools的高性能EventBus实现，实现了Vert.x的EventBus接口。
 * 这个类使用JCTools的无锁队列来优化消息处理，减少锁竞争，提高吞吐量。
 * 同时实现了消息分片和分区机制，将相关消息路由到同一事件循环，减少跨线程通信。
 */
class JCToolsEventBus(private val vertx: Vertx) : EventBus {
    private val logger = LoggerFactory.getLogger(JCToolsEventBus::class.java)

    // 原始EventBus实例，用于委托一些操作
    private val originalEventBus: EventBus = vertx.eventBus()

    // 分区数量，默认为CPU核心数
    private val numPartitions = Runtime.getRuntime().availableProcessors()

    // 消息队列分区，每个分区对应一个事件循环
    private val partitions = Array(numPartitions) {
        ConcurrentHashMap<String, MessageQueue<Any>>()
    }

    // 消息处理器映射，地址 -> 处理器列表
    private val handlers = ConcurrentHashMap<String, MutableList<Handler<Message<Any>>>>()

    // 消费者映射，地址 -> 消费者列表
    private val consumers = ConcurrentHashMap<String, MutableList<MessageConsumer<Any>>>()

    // 消息对象池
    private val messagePool = MessageObjectPool.getInstance()

    // 统计信息
    private val messagesSent = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val messagesProcessed = AtomicLong(0)
    private val processingErrors = AtomicLong(0)

    // 是否已启动
    private val started = AtomicBoolean(false)

    // 批处理大小，可动态调整
    private val batchSize = AtomicInteger(100)

    /**
     * 消息队列接口
     */
    private interface MessageQueue<T> {
        fun offer(item: MessageEntry<T>, highPriority: Boolean = false): Boolean
        fun poll(): MessageEntry<T>?
        fun size(): Int
        fun isEmpty(): Boolean
    }

    /**
     * 基于JCTools的高性能消息队列实现
     */
    private class JCToolsMessageQueue<T> : MessageQueue<T> {
        // 使用JCTools的MPSC队列（多生产者单消费者）
        private val normalQueue = MpscArrayQueue<MessageEntry<T>>(8192) // 普通优先级队列
        private val highPriorityQueue = MpscArrayQueue<MessageEntry<T>>(1024) // 高优先级队列

        override fun offer(item: MessageEntry<T>, highPriority: Boolean): Boolean {
            return if (highPriority) {
                highPriorityQueue.offer(item)
            } else {
                normalQueue.offer(item)
            }
        }

        override fun poll(): MessageEntry<T>? {
            // 优先从高优先级队列中获取消息
            val highPriorityItem = highPriorityQueue.poll()
            if (highPriorityItem != null) {
                return highPriorityItem
            }

            // 然后从普通队列中获取消息
            return normalQueue.poll()
        }

        override fun size(): Int {
            return normalQueue.size() + highPriorityQueue.size()
        }

        override fun isEmpty(): Boolean {
            return normalQueue.isEmpty && highPriorityQueue.isEmpty
        }
    }

    /**
     * 消息条目，包含消息内容和回调
     */
    private data class MessageEntry<T>(
        val address: String,
        val message: Any,
        val options: DeliveryOptions?,
        val replyHandler: Handler<AsyncResult<Message<T>>>?,
        val promise: Promise<Message<T>>?
    )

    /**
     * 异步结果接口
     */
    private interface AsyncResult<T> {
        fun succeeded(): Boolean
        fun failed(): Boolean
        fun result(): T
        fun cause(): Throwable

        companion object {
            fun <T> succeeded(result: T): AsyncResult<T> = SucceededAsyncResult(result)
            fun <T> failed(cause: Throwable): AsyncResult<T> = FailedAsyncResult(cause)
        }
    }

    /**
     * 成功的异步结果
     */
    private class SucceededAsyncResult<T>(private val result: T) : AsyncResult<T> {
        override fun succeeded(): Boolean = true
        override fun failed(): Boolean = false
        override fun result(): T = result
        override fun cause(): Throwable = throw IllegalStateException("No cause for succeeded result")
    }

    /**
     * 失败的异步结果
     */
    private class FailedAsyncResult<T>(private val cause: Throwable) : AsyncResult<T> {
        override fun succeeded(): Boolean = false
        override fun failed(): Boolean = true
        override fun result(): T = throw IllegalStateException("No result for failed result")
        override fun cause(): Throwable = cause
    }

    init {
        logger.info("初始化JCToolsEventBus，分区数量: {}", numPartitions)
    }

    /**
     * 启动消息处理循环
     */
    fun start() {
        if (started.compareAndSet(false, true)) {
            logger.info("启动JCToolsEventBus消息处理循环")

            // 为每个分区启动一个消息处理循环
            for (i in 0 until numPartitions) {
                startProcessingLoop(i)
            }
        }
    }

    /**
     * 启动指定分区的消息处理循环
     */
    private fun startProcessingLoop(partitionId: Int) {
        val partition = partitions[partitionId]

        // 使用Vert.x的周期性定时器来处理消息
        vertx.setPeriodic(1) { _ ->
            try {
                var processedCount = 0
                val currentBatchSize = batchSize.get()

                // 处理每个地址的消息队列
                for ((address, queue) in partition) {
                    // 批量处理消息
                    var count = 0
                    while (count < currentBatchSize && !queue.isEmpty()) {
                        val entry = queue.poll() ?: break

                        try {
                            processMessage(entry)
                            count++
                            processedCount++
                        } catch (e: Exception) {
                            logger.error("处理消息时发生错误: address={}", address, e)
                            processingErrors.incrementAndGet()

                            // 如果有Promise，则完成它（失败）
                            entry.promise?.fail(e)

                            // 如果有回调，则调用它（失败）
                            entry.replyHandler?.handle(AsyncResult.failed(e))
                        }
                    }
                }

                // 更新统计信息
                if (processedCount > 0) {
                    messagesProcessed.addAndGet(processedCount.toLong())
                }

                // 动态调整批处理大小
                if (processedCount >= currentBatchSize) {
                    // 如果处理的消息数量达到了批处理大小，则增加批处理大小
                    batchSize.updateAndGet { size -> min(size * 2, 1000) }
                } else if (processedCount == 0) {
                    // 如果没有处理任何消息，则减小批处理大小
                    batchSize.updateAndGet { size -> max(size / 2, 10) }
                }
            } catch (e: Exception) {
                logger.error("消息处理循环发生错误: partitionId={}", partitionId, e)
            }
        }
    }

    /**
     * 处理单个消息
     */
    private fun <T> processMessage(entry: MessageEntry<T>) {
        val address = entry.address
        val message = entry.message
        val options = entry.options
        val replyHandler = entry.replyHandler
        val promise = entry.promise

        try {
            // 获取处理器列表
            val handlersList = handlers[address]

            if (handlersList != null && handlersList.isNotEmpty()) {
                // 有处理器，将消息分发给所有处理器
                for (handler in handlersList) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        handler.handle(createMessage(address, message as T))
                    } catch (e: Exception) {
                        logger.error("处理器处理消息时发生错误: address={}", address, e)
                    }
                }

                // 更新统计信息
                messagesReceived.incrementAndGet()

                // 如果有Promise，则完成它
                promise?.complete(createMessage(address, message as T))

                // 如果有回调，则调用它
                replyHandler?.handle(AsyncResult.succeeded(createMessage(address, message as T)))
            } else {
                // 没有处理器，使用原始EventBus发送消息
                if (options != null) {
                    if (replyHandler != null) {
                        originalEventBus.request<T>(address, message, options, replyHandler as Handler<io.vertx.core.AsyncResult<Message<T>>>)
                    } else {
                        originalEventBus.send(address, message, options)
                    }
                } else {
                    if (replyHandler != null) {
                        originalEventBus.request<T>(address, message, replyHandler as Handler<io.vertx.core.AsyncResult<Message<T>>>)
                    } else {
                        originalEventBus.send(address, message)
                    }
                }

                // 更新统计信息
                messagesSent.incrementAndGet()
            }
        } catch (e: Exception) {
            logger.error("处理消息时发生错误: address={}", address, e)
            processingErrors.incrementAndGet()

            // 如果有Promise，则完成它（失败）
            promise?.fail(e)

            // 如果有回调，则调用它（失败）
            replyHandler?.handle(AsyncResult.failed(e))
        }
    }

    /**
     * 创建消息对象
     */
    private fun <T> createMessage(address: String, body: T): Message<T> {
        return object : Message<T> {
            override fun address(): String = address
            override fun body(): T = body
            override fun headers() = null
            override fun replyAddress() = null
            override fun isSend() = true

            override fun reply(message: Any) {
                // 不支持回复
            }

            override fun reply(message: Any, options: DeliveryOptions) {
                // 不支持回复
            }

            override fun <R> reply(message: Any, handler: Handler<AsyncResult<Message<R>>>) {
                // 不支持回复
            }

            override fun <R> reply(message: Any, options: DeliveryOptions, handler: Handler<AsyncResult<Message<R>>>) {
                // 不支持回复
            }
        }
    }

    /**
     * 获取消息队列，如果不存在则创建
     */
    private fun <T> getOrCreateQueue(address: String): MessageQueue<T> {
        // 计算分区ID
        val partitionId = getPartitionId(address)
        val partition = partitions[partitionId]

        // 获取或创建队列
        @Suppress("UNCHECKED_CAST")
        return partition.computeIfAbsent(address) { JCToolsMessageQueue<Any>() } as MessageQueue<T>
    }

    /**
     * 计算地址的分区ID
     */
    private fun getPartitionId(address: String): Int {
        return address.hashCode().absoluteValue % numPartitions
    }

    /**
     * 发送消息
     */
    fun <T> send(address: String, message: Any): Future<Message<T>> {
        val promise = Promise.promise<Message<T>>()
        send(address, message, null, null, promise)
        return promise.future()
    }

    /**
     * 发送消息（带选项）
     */
    fun <T> send(address: String, message: Any, options: DeliveryOptions): Future<Message<T>> {
        val promise = Promise.promise<Message<T>>()
        send(address, message, options, null, promise)
        return promise.future()
    }

    /**
     * 发送消息（带回调）
     */
    fun <T> send(address: String, message: Any, replyHandler: Handler<AsyncResult<Message<T>>>) {
        send(address, message, null, replyHandler, null)
    }

    /**
     * 发送消息（带选项和回调）
     */
    fun <T> send(address: String, message: Any, options: DeliveryOptions, replyHandler: Handler<AsyncResult<Message<T>>>) {
        send(address, message, options, replyHandler, null)
    }

    /**
     * 发送消息（内部实现）
     */
    private fun <T> send(
        address: String,
        message: Any,
        options: DeliveryOptions?,
        replyHandler: Handler<AsyncResult<Message<T>>>?,
        promise: Promise<Message<T>>?
    ) {
        // 检查是否已启动
        if (!started.get()) {
            start()
        }

        // 创建消息条目
        val entry = MessageEntry(address, message, options, replyHandler, promise)

        // 获取队列并添加消息
        val queue = getOrCreateQueue<T>(address)
        val highPriority = options?.isHighPriority() ?: false

        if (!queue.offer(entry as MessageEntry<Any>, highPriority)) {
            // 队列已满，使用原始EventBus发送
            if (options != null) {
                if (replyHandler != null) {
                    originalEventBus.request<T>(address, message, options, replyHandler as Handler<io.vertx.core.AsyncResult<Message<T>>>)
                } else {
                    originalEventBus.send(address, message, options)
                }
            } else {
                if (replyHandler != null) {
                    originalEventBus.request<T>(address, message, replyHandler as Handler<io.vertx.core.AsyncResult<Message<T>>>)
                } else {
                    originalEventBus.send(address, message)
                }
            }
        }

        // 更新统计信息
        messagesSent.incrementAndGet()
    }

    /**
     * 发布消息
     */
    fun publish(address: String, message: Any) {
        // 直接使用原始EventBus发布
        originalEventBus.publish(address, message)
    }

    /**
     * 发布消息（带选项）
     */
    fun publish(address: String, message: Any, options: DeliveryOptions) {
        // 直接使用原始EventBus发布
        originalEventBus.publish(address, message, options)
    }

    /**
     * 请求-响应模式发送消息
     */
    fun <T> request(address: String, message: Any): Future<Message<T>> {
        return send(address, message)
    }

    /**
     * 请求-响应模式发送消息（带选项）
     */
    fun <T> request(address: String, message: Any, options: DeliveryOptions): Future<Message<T>> {
        return send(address, message, options)
    }

    /**
     * 注册消息处理器
     */
    fun <T> consumer(address: String, handler: Handler<Message<T>>): MessageConsumer<T> {
        // 检查是否已启动
        if (!started.get()) {
            start()
        }

        // 添加处理器
        @Suppress("UNCHECKED_CAST")
        handlers.computeIfAbsent(address) { mutableListOf() }.add(handler as Handler<Message<Any>>)

        // 创建消费者对象
        val consumer = createConsumer(address, handler)

        // 添加到消费者列表
        consumers.computeIfAbsent(address) { mutableListOf() }.add(consumer as MessageConsumer<Any>)

        return consumer
    }

    /**
     * 创建消费者对象
     */
    private fun <T> createConsumer(address: String, handler: Handler<Message<T>>): MessageConsumer<T> {
        return object : MessageConsumer<T> {
            private val registered = AtomicBoolean(true)

            override fun address(): String = address

            override fun isRegistered(): Boolean = registered.get()

            override fun pause(): MessageConsumer<T> = this

            override fun resume(): MessageConsumer<T> = this

            override fun endHandler(endHandler: Handler<Void>?): MessageConsumer<T> = this

            override fun exceptionHandler(handler: Handler<Throwable>?): MessageConsumer<T> = this

            override fun handler(handler: Handler<Message<T>>?): MessageConsumer<T> = this

            override fun completionHandler(completionHandler: Handler<AsyncResult<Void>>?): MessageConsumer<T> = this

            override fun unregister(): Future<Void> {
                return unregister(null)
            }

            override fun unregister(completionHandler: Handler<AsyncResult<Void>>?): Future<Void> {
                val promise = Promise.promise<Void>()

                if (registered.compareAndSet(true, false)) {
                    // 从处理器列表中移除
                    @Suppress("UNCHECKED_CAST")
                    handlers[address]?.remove(handler as Handler<Message<Any>>)

                    // 从消费者列表中移除
                    consumers[address]?.remove(this as MessageConsumer<Any>)

                    promise.complete()
                    completionHandler?.handle(io.vertx.core.impl.future.SucceededFuture())
                } else {
                    val cause = IllegalStateException("Consumer already unregistered")
                    promise.fail(cause)
                    completionHandler?.handle(io.vertx.core.impl.future.FailedFuture(cause))
                }

                return promise.future()
            }
        }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("messagesSent", messagesSent.get())
            .put("messagesReceived", messagesReceived.get())
            .put("messagesProcessed", messagesProcessed.get())
            .put("processingErrors", processingErrors.get())
            .put("numPartitions", numPartitions)
            .put("batchSize", batchSize.get())
            .put("started", started.get())
            .put("queues", JsonObject())

        // 添加队列统计信息
        val queuesStats = stats.getJsonObject("queues")
        for (i in 0 until numPartitions) {
            val partition = partitions[i]
            val partitionStats = JsonObject()

            for ((address, queue) in partition) {
                partitionStats.put(address, JsonObject()
                    .put("size", queue.size())
                    .put("empty", queue.isEmpty())
                )
            }

            queuesStats.put("partition$i", partitionStats)
        }

        return stats
    }

    /**
     * 检查DeliveryOptions是否设置了高优先级
     */
    private fun DeliveryOptions.isHighPriority(): Boolean {
        return this.headers()?.get("priority") == "high"
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: JCToolsEventBus? = null

        /**
         * 获取JCToolsEventBus的单例实例
         */
        fun getInstance(vertx: Vertx): JCToolsEventBus {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: JCToolsEventBus(vertx).also { INSTANCE = it }
            }
        }
    }
}
