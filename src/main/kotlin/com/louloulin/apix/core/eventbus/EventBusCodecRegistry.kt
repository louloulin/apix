package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import com.louloulin.apix.models.Route
import com.louloulin.apix.models.ServiceMetrics
import org.slf4j.LoggerFactory

/**
 * EventBus编解码器注册工具类，用于注册本地消息编解码器。
 * 这个类在应用启动时被调用，为常用数据类型注册本地消息编解码器，
 * 以优化同一JVM内的EventBus通信，减少序列化/反序列化开销。
 */
object EventBusCodecRegistry {
    private val logger = LoggerFactory.getLogger(EventBusCodecRegistry::class.java)
    
    /**
     * 注册所有本地消息编解码器
     *
     * @param vertx Vertx实例
     */
    fun registerLocalCodecs(vertx: Vertx) {
        logger.info("注册EventBus本地消息编解码器...")
        
        // 注册常用数据类型的编解码器
        registerCodec(vertx, JsonObject::class.java)
        registerCodec(vertx, JsonArray::class.java)
        registerCodec(vertx, String::class.java)
        registerCodec(vertx, Boolean::class.java)
        registerCodec(vertx, Int::class.java)
        registerCodec(vertx, Long::class.java)
        registerCodec(vertx, Double::class.java)
        
        // 注册自定义数据类型的编解码器
        registerCodec(vertx, Route::class.java)
        registerCodec(vertx, ServiceMetrics::class.java)
        
        logger.info("EventBus本地消息编解码器注册完成")
    }
    
    /**
     * 注册单个类型的本地消息编解码器
     *
     * @param vertx Vertx实例
     * @param clazz 要注册的类
     */
    private fun <T> registerCodec(vertx: Vertx, clazz: Class<T>) {
        try {
            vertx.eventBus().registerDefaultCodec(clazz, LocalMessageCodec(clazz))
            logger.debug("已注册本地消息编解码器: {}", clazz.simpleName)
        } catch (e: Exception) {
            logger.warn("注册本地消息编解码器失败: {}", clazz.simpleName, e)
        }
    }
}
