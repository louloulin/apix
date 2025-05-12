package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.DeliveryOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * 模拟事件总线管理器，用于测试。
 * 这个类模拟了事件总线的行为，但不会实际发送消息，而是直接返回成功的 Future。
 */
class MockEventBusManager(private val vertx: Vertx) {
    
    companion object {
        @Volatile
        private var instance: MockEventBusManager? = null
        
        fun getInstance(vertx: Vertx): MockEventBusManager {
            return instance ?: synchronized(this) {
                instance ?: MockEventBusManager(vertx).also { instance = it }
            }
        }
    }
    
    /**
     * 模拟发送消息。
     */
    fun <T> request(address: String, message: Any, options: DeliveryOptions? = null): Future<T> {
        val promise = Promise.promise<T>()
        
        // 根据地址返回不同的模拟响应
        when {
            address.contains("CLUSTER_GET_LOCAL_NODE_ID") -> {
                val response = JsonObject()
                    .put("success", true)
                    .put("nodeId", "node1")
                promise.complete(response as T)
            }
            address.contains("CLUSTER_GET_SHARD_ASSIGNMENT") -> {
                val response = JsonObject()
                    .put("success", true)
                    .put("assignment", JsonObject())
                promise.complete(response as T)
            }
            address.contains("CLUSTER_SET_SHARD_ASSIGNMENT") -> {
                val response = JsonObject()
                    .put("success", true)
                promise.complete(response as T)
            }
            address.contains("CLUSTER_GET_LOCAL_REGION") -> {
                val response = JsonObject()
                    .put("success", true)
                    .put("region", "region1")
                promise.complete(response as T)
            }
            address.contains("CLUSTER_GET_REGIONS") -> {
                val response = JsonObject()
                    .put("success", true)
                    .put("regions", JsonArray().add("region1").add("region2"))
                promise.complete(response as T)
            }
            address.contains("CLUSTER_GET_NODES") -> {
                val response = JsonObject()
                    .put("success", true)
                    .put("nodes", JsonArray().add(JsonObject().put("id", "node1")))
                promise.complete(response as T)
            }
            address.contains("VECTOR_GENERATE") -> {
                val vector = JsonArray()
                for (i in 0 until 10) {
                    vector.add(Math.random())
                }
                val response = JsonObject()
                    .put("success", true)
                    .put("vector", vector)
                promise.complete(response as T)
            }
            else -> {
                val response = JsonObject()
                    .put("success", true)
                promise.complete(response as T)
            }
        }
        
        return promise.future()
    }
    
    /**
     * 模拟发布消息。
     */
    fun publish(address: String, message: Any, options: DeliveryOptions? = null) {
        // 不做任何事情，只是模拟发布
    }
    
    /**
     * 模拟发送消息。
     */
    fun send(address: String, message: Any, options: DeliveryOptions? = null) {
        // 不做任何事情，只是模拟发送
    }
}
