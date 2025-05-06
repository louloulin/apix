package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

/**
 * EventBus工厂类，用于动态切换EventBus实现。
 * 支持在原生Vert.x EventBus和优化的JCToolsEventBus之间切换。
 */
class EventBusFactory(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(EventBusFactory::class.java)
    
    // 当前使用的EventBus类型
    private val currentType = AtomicReference(EventBusType.VERTX)
    
    // JCToolsEventBus实例
    private val jcToolsEventBus by lazy { JCToolsEventBus.getInstance(vertx) }
    
    // 原生Vert.x EventBus实例
    private val vertxEventBus = vertx.eventBus()
    
    /**
     * EventBus类型枚举
     */
    enum class EventBusType {
        VERTX,      // 原生Vert.x EventBus
        JCTOOLS     // 优化的JCToolsEventBus
    }
    
    /**
     * 获取当前使用的EventBus类型
     */
    fun getCurrentType(): EventBusType {
        return currentType.get()
    }
    
    /**
     * 切换EventBus类型
     */
    fun switchType(type: EventBusType): Boolean {
        val oldType = currentType.getAndSet(type)
        val changed = oldType != type
        
        if (changed) {
            logger.info("EventBus类型已切换: {} -> {}", oldType, type)
            
            // 如果切换到JCToolsEventBus，确保它已启动
            if (type == EventBusType.JCTOOLS) {
                jcToolsEventBus.start()
            }
        }
        
        return changed
    }
    
    /**
     * 发送消息
     */
    fun <T> send(address: String, message: Any): Future<Message<T>> {
        return when (currentType.get()) {
            EventBusType.JCTOOLS -> jcToolsEventBus.send(address, message)
            EventBusType.VERTX -> {
                val promise = Promise.promise<Message<T>>()
                vertxEventBus.request<T>(address, message) { ar ->
                    if (ar.succeeded()) {
                        promise.complete(ar.result())
                    } else {
                        promise.fail(ar.cause())
                    }
                }
                promise.future()
            }
        }
    }
    
    /**
     * 发送消息（带选项）
     */
    fun <T> send(address: String, message: Any, options: DeliveryOptions): Future<Message<T>> {
        return when (currentType.get()) {
            EventBusType.JCTOOLS -> jcToolsEventBus.send(address, message, options)
            EventBusType.VERTX -> {
                val promise = Promise.promise<Message<T>>()
                vertxEventBus.request<T>(address, message, options) { ar ->
                    if (ar.succeeded()) {
                        promise.complete(ar.result())
                    } else {
                        promise.fail(ar.cause())
                    }
                }
                promise.future()
            }
        }
    }
    
    /**
     * 发布消息
     */
    fun publish(address: String, message: Any) {
        when (currentType.get()) {
            EventBusType.JCTOOLS -> jcToolsEventBus.publish(address, message)
            EventBusType.VERTX -> vertxEventBus.publish(address, message)
        }
    }
    
    /**
     * 发布消息（带选项）
     */
    fun publish(address: String, message: Any, options: DeliveryOptions) {
        when (currentType.get()) {
            EventBusType.JCTOOLS -> jcToolsEventBus.publish(address, message, options)
            EventBusType.VERTX -> vertxEventBus.publish(address, message, options)
        }
    }
    
    /**
     * 请求-响应模式发送消息
     */
    fun <T> request(address: String, message: Any): Future<Message<T>> {
        return when (currentType.get()) {
            EventBusType.JCTOOLS -> jcToolsEventBus.request(address, message)
            EventBusType.VERTX -> {
                val promise = Promise.promise<Message<T>>()
                vertxEventBus.request<T>(address, message) { ar ->
                    if (ar.succeeded()) {
                        promise.complete(ar.result())
                    } else {
                        promise.fail(ar.cause())
                    }
                }
                promise.future()
            }
        }
    }
    
    /**
     * 请求-响应模式发送消息（带选项）
     */
    fun <T> request(address: String, message: Any, options: DeliveryOptions): Future<Message<T>> {
        return when (currentType.get()) {
            EventBusType.JCTOOLS -> jcToolsEventBus.request(address, message, options)
            EventBusType.VERTX -> {
                val promise = Promise.promise<Message<T>>()
                vertxEventBus.request<T>(address, message, options) { ar ->
                    if (ar.succeeded()) {
                        promise.complete(ar.result())
                    } else {
                        promise.fail(ar.cause())
                    }
                }
                promise.future()
            }
        }
    }
    
    /**
     * 注册消息处理器
     */
    fun <T> consumer(address: String, handler: Handler<Message<T>>): MessageConsumer<T> {
        return when (currentType.get()) {
            EventBusType.JCTOOLS -> jcToolsEventBus.consumer(address, handler)
            EventBusType.VERTX -> vertxEventBus.consumer(address, handler)
        }
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
            .put("currentType", currentType.get().name)
        
        // 添加JCToolsEventBus统计信息（如果可用）
        if (currentType.get() == EventBusType.JCTOOLS) {
            stats.put("jctools", jcToolsEventBus.getStats())
        }
        
        return stats
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: EventBusFactory? = null
        
        /**
         * 获取EventBusFactory的单例实例
         */
        fun getInstance(vertx: Vertx): EventBusFactory {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EventBusFactory(vertx).also { INSTANCE = it }
            }
        }
    }
}
