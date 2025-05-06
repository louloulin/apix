package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 增强版EventBus工具类，使用EventBusFactory来支持动态切换EventBus实现。
 * 这个类提供了与原始EventBusUtil相同的API，但内部使用EventBusFactory来发送消息。
 */
object EnhancedEventBusUtil {
    private val logger = LoggerFactory.getLogger(EnhancedEventBusUtil::class.java)
    private val messagePool = MessageObjectPool.getInstance()
    
    /**
     * 发送请求并接收响应，使用消息对象池
     * 
     * @param vertx Vertx实例
     * @param address 目标地址
     * @param action 操作名称
     * @param timeout 超时时间（毫秒）
     * @return 包含响应的Future
     */
    fun request(vertx: Vertx, address: String, action: String, timeout: Long = 30000): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        val message = messagePool.borrowJsonObject().put("action", action)
        
        val options = DeliveryOptions().setSendTimeout(timeout)
        
        // 使用EventBusFactory发送请求
        val factory = EventBusFactory.getInstance(vertx)
        factory.request<JsonObject>(address, message, options)
            .onComplete { ar ->
                // 归还消息对象到池
                messagePool.returnJsonObject(message)
                
                if (ar.succeeded()) {
                    promise.complete(ar.result().body())
                } else {
                    logger.warn("EventBus请求失败: address={}, action={}, error={}", address, action, ar.cause().message)
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 发送带参数的请求并接收响应，使用消息对象池
     * 
     * @param vertx Vertx实例
     * @param address 目标地址
     * @param action 操作名称
     * @param params 参数
     * @param timeout 超时时间（毫秒）
     * @return 包含响应的Future
     */
    fun requestWithParams(vertx: Vertx, address: String, action: String, params: JsonObject, timeout: Long = 30000): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        val message = messagePool.borrowJsonObject().put("action", action).put("params", params)
        
        val options = DeliveryOptions().setSendTimeout(timeout)
        
        // 使用EventBusFactory发送请求
        val factory = EventBusFactory.getInstance(vertx)
        factory.request<JsonObject>(address, message, options)
            .onComplete { ar ->
                // 归还消息对象到池
                messagePool.returnJsonObject(message)
                
                if (ar.succeeded()) {
                    promise.complete(ar.result().body())
                } else {
                    logger.warn("EventBus请求失败: address={}, action={}, error={}", address, action, ar.cause().message)
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 发送高优先级请求，使用消息对象池
     * 
     * @param vertx Vertx实例
     * @param address 目标地址
     * @param action 操作名称
     * @param timeout 超时时间（毫秒）
     * @return 包含响应的Future
     */
    fun requestHighPriority(vertx: Vertx, address: String, action: String, timeout: Long = 30000): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        val message = messagePool.borrowJsonObject().put("action", action)
        
        val options = DeliveryOptions()
            .setSendTimeout(timeout)
            .addHeader("priority", "high")
        
        // 使用EventBusFactory发送请求
        val factory = EventBusFactory.getInstance(vertx)
        factory.request<JsonObject>(address, message, options)
            .onComplete { ar ->
                // 归还消息对象到池
                messagePool.returnJsonObject(message)
                
                if (ar.succeeded()) {
                    promise.complete(ar.result().body())
                } else {
                    logger.warn("高优先级EventBus请求失败: address={}, action={}, error={}", address, action, ar.cause().message)
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 发布消息，使用消息对象池
     * 
     * @param vertx Vertx实例
     * @param address 目标地址
     * @param action 操作名称
     */
    fun publish(vertx: Vertx, address: String, action: String) {
        val message = messagePool.borrowJsonObject().put("action", action)
        
        // 使用EventBusFactory发布消息
        val factory = EventBusFactory.getInstance(vertx)
        factory.publish(address, message)
        
        // 归还消息对象到池
        messagePool.returnJsonObject(message)
    }
    
    /**
     * 发布带参数的消息，使用消息对象池
     * 
     * @param vertx Vertx实例
     * @param address 目标地址
     * @param action 操作名称
     * @param params 参数
     */
    fun publishWithParams(vertx: Vertx, address: String, action: String, params: JsonObject) {
        val message = messagePool.borrowJsonObject().put("action", action).put("params", params)
        
        // 使用EventBusFactory发布消息
        val factory = EventBusFactory.getInstance(vertx)
        factory.publish(address, message)
        
        // 归还消息对象到池
        messagePool.returnJsonObject(message)
    }
    
    /**
     * 创建成功响应，使用消息对象池
     * 
     * @param result 结果数据
     * @return 成功响应
     */
    fun createSuccessResponse(result: Any? = null): JsonObject {
        val response = messagePool.borrowJsonObject().put("success", true)
        
        if (result != null) {
            response.put("result", result)
        }
        
        return response
    }
    
    /**
     * 创建失败响应，使用消息对象池
     * 
     * @param error 错误信息
     * @param errorCode 错误代码
     * @return 失败响应
     */
    fun createErrorResponse(error: String, errorCode: Int = 500): JsonObject {
        return messagePool.borrowJsonObject()
            .put("success", false)
            .put("error", error)
            .put("errorCode", errorCode)
    }
    
    /**
     * 回复消息，使用消息对象池
     * 
     * @param message 原始消息
     * @param response 响应内容
     */
    fun reply(message: Message<JsonObject>, response: JsonObject) {
        message.reply(response)
        
        // 注意：这里不归还response，因为它会被EventBus继续使用
        // 调用方负责在不再需要response时归还它
    }
    
    /**
     * 获取消息对象池统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getPoolStats(): JsonObject {
        return messagePool.getStats()
    }
    
    /**
     * 获取EventBus统计信息
     * 
     * @param vertx Vertx实例
     * @return 包含统计信息的JsonObject
     */
    fun getEventBusStats(vertx: Vertx): JsonObject {
        val factory = EventBusFactory.getInstance(vertx)
        return factory.getStats()
    }
    
    /**
     * 切换EventBus类型
     * 
     * @param vertx Vertx实例
     * @param type EventBus类型
     * @return 是否成功切换
     */
    fun switchEventBusType(vertx: Vertx, type: EventBusFactory.EventBusType): Boolean {
        val factory = EventBusFactory.getInstance(vertx)
        return factory.switchType(type)
    }
    
    /**
     * 获取当前EventBus类型
     * 
     * @param vertx Vertx实例
     * @return 当前EventBus类型
     */
    fun getCurrentEventBusType(vertx: Vertx): EventBusFactory.EventBusType {
        val factory = EventBusFactory.getInstance(vertx)
        return factory.getCurrentType()
    }
}
