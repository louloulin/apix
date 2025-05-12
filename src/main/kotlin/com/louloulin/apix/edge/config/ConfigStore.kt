package com.louloulin.apix.edge.config

import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * 配置存储接口
 * 定义了配置存储的通用方法
 * 实现 plan7.md 中的 4.2.2 节"配置管理"功能
 */
interface ConfigStore {
    /**
     * 获取配置存储名称
     * 
     * @return String 配置存储名称
     */
    fun getName(): String
    
    /**
     * 获取配置存储类型
     * 
     * @return ConfigStoreType 配置存储类型
     */
    fun getType(): ConfigStoreType
    
    /**
     * 初始化配置存储
     * 
     * @param config 配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void>
    
    /**
     * 获取配置
     * 
     * @param path 配置路径
     * @return Future<JsonObject> 配置内容
     */
    fun getConfig(path: String): Future<JsonObject>
    
    /**
     * 获取配置列表
     * 
     * @param path 配置路径
     * @return Future<JsonArray> 配置列表
     */
    fun listConfigs(path: String): Future<JsonArray>
    
    /**
     * 保存配置
     * 
     * @param path 配置路径
     * @param config 配置内容
     * @param message 提交消息
     * @return Future<JsonObject> 保存结果
     */
    fun saveConfig(path: String, config: JsonObject, message: String): Future<JsonObject>
    
    /**
     * 删除配置
     * 
     * @param path 配置路径
     * @param message 提交消息
     * @return Future<JsonObject> 删除结果
     */
    fun deleteConfig(path: String, message: String): Future<JsonObject>
    
    /**
     * 获取配置历史
     * 
     * @param path 配置路径
     * @param limit 限制数量
     * @param offset 偏移量
     * @return Future<JsonArray> 配置历史
     */
    fun getConfigHistory(path: String, limit: Int, offset: Int): Future<JsonArray>
    
    /**
     * 获取配置版本
     * 
     * @param path 配置路径
     * @param version 版本
     * @return Future<JsonObject> 配置版本内容
     */
    fun getConfigVersion(path: String, version: String): Future<JsonObject>
    
    /**
     * 比较配置版本
     * 
     * @param path 配置路径
     * @param version1 版本1
     * @param version2 版本2
     * @return Future<JsonObject> 比较结果
     */
    fun compareConfigVersions(path: String, version1: String, version2: String): Future<JsonObject>
    
    /**
     * 恢复配置版本
     * 
     * @param path 配置路径
     * @param version 版本
     * @param message 提交消息
     * @return Future<JsonObject> 恢复结果
     */
    fun restoreConfigVersion(path: String, version: String, message: String): Future<JsonObject>
    
    /**
     * 验证配置
     * 
     * @param path 配置路径
     * @param config 配置内容
     * @return Future<JsonObject> 验证结果
     */
    fun validateConfig(path: String, config: JsonObject): Future<JsonObject>
    
    /**
     * 获取配置审计日志
     * 
     * @param path 配置路径
     * @param limit 限制数量
     * @param offset 偏移量
     * @return Future<JsonArray> 审计日志
     */
    fun getConfigAuditLog(path: String, limit: Int, offset: Int): Future<JsonArray>
    
    /**
     * 获取配置存储状态
     * 
     * @return JsonObject 状态信息
     */
    fun getStatus(): JsonObject
    
    /**
     * 关闭配置存储
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void>
}
