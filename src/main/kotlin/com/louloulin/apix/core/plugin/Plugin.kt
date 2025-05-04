package com.louloulin.apix.core.plugin

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext

/**
 * 插件接口，所有插件都必须实现此接口
 */
interface Plugin {
    /**
     * 插件名称
     */
    val name: String
    
    /**
     * 插件版本
     */
    val version: String
    
    /**
     * 插件描述
     */
    val description: String
    
    /**
     * 插件类型
     */
    val type: PluginType
    
    /**
     * 初始化插件
     */
    fun init(context: PluginContext, config: JsonObject): Future<Void>
    
    /**
     * 处理请求
     */
    fun handleRequest(context: RoutingContext): Future<Void>
    
    /**
     * 处理响应
     */
    fun handleResponse(context: RoutingContext): Future<Void>
    
    /**
     * 关闭插件
     */
    fun close(): Future<Void>
}

/**
 * 插件类型
 */
enum class PluginType {
    /**
     * 请求处理插件
     */
    REQUEST,
    
    /**
     * 响应处理插件
     */
    RESPONSE,
    
    /**
     * 安全插件
     */
    SECURITY,
    
    /**
     * 监控插件
     */
    MONITORING,
    
    /**
     * 日志插件
     */
    LOGGING,
    
    /**
     * 缓存插件
     */
    CACHE,
    
    /**
     * 转换插件
     */
    TRANSFORM,
    
    /**
     * 其他插件
     */
    OTHER
}

/**
 * 插件上下文
 */
class PluginContext(
    private val vertx: Vertx,
    private val config: JsonObject
) {
    /**
     * 获取 Vertx 实例
     */
    fun vertx(): Vertx {
        return vertx
    }
    
    /**
     * 获取配置
     */
    fun config(): JsonObject {
        return config
    }
}
