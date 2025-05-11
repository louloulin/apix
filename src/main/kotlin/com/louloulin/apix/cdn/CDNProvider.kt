package com.louloulin.apix.cdn

import io.vertx.core.Future
import io.vertx.core.json.JsonObject

/**
 * CDN提供商接口
 * 定义了与CDN提供商交互的标准接口
 */
interface CDNProvider {
    /**
     * 获取CDN提供商名称
     */
    fun getName(): String
    
    /**
     * 初始化CDN提供商
     * 
     * @param config CDN配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void>
    
    /**
     * 获取CDN状态
     * 
     * @return Future<JsonObject> CDN状态
     */
    fun getStatus(): Future<JsonObject>
    
    /**
     * 刷新CDN缓存
     * 
     * @param urls 需要刷新的URL列表
     * @return Future<JsonObject> 刷新结果
     */
    fun purgeCache(urls: List<String>): Future<JsonObject>
    
    /**
     * 预热CDN缓存
     * 
     * @param urls 需要预热的URL列表
     * @return Future<JsonObject> 预热结果
     */
    fun prewarmCache(urls: List<String>): Future<JsonObject>
    
    /**
     * 更新CDN配置
     * 
     * @param config 新的CDN配置
     * @return Future<Void> 更新结果
     */
    fun updateConfig(config: JsonObject): Future<Void>
    
    /**
     * 关闭CDN连接
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void>
}
