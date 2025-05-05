package com.louloulin.apix.core.eventbus

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import com.louloulin.apix.core.memory.ObjectPool
import org.slf4j.LoggerFactory

/**
 * 消息对象池，用于复用常用消息对象，减少GC压力。
 * 这个类提供了对JsonObject和JsonArray等常用消息对象的池化管理。
 */
class MessageObjectPool {
    private val logger = LoggerFactory.getLogger(MessageObjectPool::class.java)
    
    // JsonObject对象池
    private val jsonObjectPool = ObjectPool<JsonObject>("jsonObject", { JsonObject() }, 100, 1000)
    
    // JsonArray对象池
    private val jsonArrayPool = ObjectPool<JsonArray>("jsonArray", { JsonArray() }, 50, 500)
    
    /**
     * 从对象池中借用一个JsonObject
     * 
     * @return 借用的JsonObject
     */
    fun borrowJsonObject(): JsonObject {
        return jsonObjectPool.borrow()
    }
    
    /**
     * 将JsonObject归还到对象池
     * 
     * @param obj 要归还的JsonObject
     */
    fun returnJsonObject(obj: JsonObject) {
        obj.clear() // 清空内容后返回池
        jsonObjectPool.release(obj)
    }
    
    /**
     * 从对象池中借用一个JsonArray
     * 
     * @return 借用的JsonArray
     */
    fun borrowJsonArray(): JsonArray {
        return jsonArrayPool.borrow()
    }
    
    /**
     * 将JsonArray归还到对象池
     * 
     * @param array 要归还的JsonArray
     */
    fun returnJsonArray(array: JsonArray) {
        array.clear() // 清空内容后返回池
        jsonArrayPool.release(array)
    }
    
    /**
     * 获取对象池统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        val stats = JsonObject()
        stats.put("jsonObjectPool", jsonObjectPool.getStats())
        stats.put("jsonArrayPool", jsonArrayPool.getStats())
        return stats
    }
    
    companion object {
        // 单例实例
        private val INSTANCE = MessageObjectPool()
        
        /**
         * 获取MessageObjectPool的单例实例
         * 
         * @return MessageObjectPool实例
         */
        fun getInstance(): MessageObjectPool {
            return INSTANCE
        }
    }
}
