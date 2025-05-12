package com.louloulin.apix.edge.deploy.cloud

import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * 云提供商接口
 * 定义了与云提供商交互的通用方法
 * 实现 plan7.md 中的 4.1.3 节"云原生部署"功能
 */
interface CloudProvider {
    /**
     * 获取云提供商名称
     * 
     * @return String 云提供商名称
     */
    fun getName(): String
    
    /**
     * 获取云提供商类型
     * 
     * @return CloudProviderType 云提供商类型
     */
    fun getType(): CloudProviderType
    
    /**
     * 初始化云提供商
     * 
     * @param config 配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void>
    
    /**
     * 获取区域列表
     * 
     * @return Future<JsonArray> 区域列表
     */
    fun getRegions(): Future<JsonArray>
    
    /**
     * 获取容器集群列表
     * 
     * @param region 区域
     * @return Future<JsonArray> 容器集群列表
     */
    fun getContainerClusters(region: String): Future<JsonArray>
    
    /**
     * 创建容器集群
     * 
     * @param region 区域
     * @param config 集群配置
     * @return Future<JsonObject> 创建结果
     */
    fun createContainerCluster(region: String, config: JsonObject): Future<JsonObject>
    
    /**
     * 删除容器集群
     * 
     * @param region 区域
     * @param clusterId 集群ID
     * @return Future<JsonObject> 删除结果
     */
    fun deleteContainerCluster(region: String, clusterId: String): Future<JsonObject>
    
    /**
     * 获取容器集群详情
     * 
     * @param region 区域
     * @param clusterId 集群ID
     * @return Future<JsonObject> 集群详情
     */
    fun getContainerClusterDetails(region: String, clusterId: String): Future<JsonObject>
    
    /**
     * 获取容器集群节点列表
     * 
     * @param region 区域
     * @param clusterId 集群ID
     * @return Future<JsonArray> 节点列表
     */
    fun getContainerClusterNodes(region: String, clusterId: String): Future<JsonArray>
    
    /**
     * 扩展容器集群
     * 
     * @param region 区域
     * @param clusterId 集群ID
     * @param nodeCount 节点数量
     * @return Future<JsonObject> 扩展结果
     */
    fun scaleContainerCluster(region: String, clusterId: String, nodeCount: Int): Future<JsonObject>
    
    /**
     * 获取 Serverless 服务列表
     * 
     * @param region 区域
     * @return Future<JsonArray> Serverless 服务列表
     */
    fun getServerlessServices(region: String): Future<JsonArray>
    
    /**
     * 部署 Serverless 服务
     * 
     * @param region 区域
     * @param config 服务配置
     * @return Future<JsonObject> 部署结果
     */
    fun deployServerlessService(region: String, config: JsonObject): Future<JsonObject>
    
    /**
     * 删除 Serverless 服务
     * 
     * @param region 区域
     * @param serviceId 服务ID
     * @return Future<JsonObject> 删除结果
     */
    fun deleteServerlessService(region: String, serviceId: String): Future<JsonObject>
    
    /**
     * 获取 Serverless 服务详情
     * 
     * @param region 区域
     * @param serviceId 服务ID
     * @return Future<JsonObject> 服务详情
     */
    fun getServerlessServiceDetails(region: String, serviceId: String): Future<JsonObject>
    
    /**
     * 获取 Serverless 服务日志
     * 
     * @param region 区域
     * @param serviceId 服务ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return Future<JsonArray> 服务日志
     */
    fun getServerlessServiceLogs(region: String, serviceId: String, startTime: Long, endTime: Long): Future<JsonArray>
    
    /**
     * 获取云提供商状态
     * 
     * @return JsonObject 状态信息
     */
    fun getStatus(): JsonObject
    
    /**
     * 关闭云提供商
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void>
}
