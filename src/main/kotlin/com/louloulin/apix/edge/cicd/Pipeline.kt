package com.louloulin.apix.edge.cicd

import io.vertx.core.Future
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * CI/CD 流水线接口
 * 定义了 CI/CD 流水线的通用方法
 * 实现 plan7.md 中的 4.2.1 节"CI/CD 流水线"功能
 */
interface Pipeline {
    /**
     * 获取流水线名称
     * 
     * @return String 流水线名称
     */
    fun getName(): String
    
    /**
     * 获取流水线类型
     * 
     * @return PipelineType 流水线类型
     */
    fun getType(): PipelineType
    
    /**
     * 初始化流水线
     * 
     * @param config 配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void>
    
    /**
     * 获取流水线配置
     * 
     * @return JsonObject 流水线配置
     */
    fun getConfig(): JsonObject
    
    /**
     * 获取流水线阶段列表
     * 
     * @return Future<JsonArray> 阶段列表
     */
    fun getStages(): Future<JsonArray>
    
    /**
     * 获取流水线阶段详情
     * 
     * @param stageId 阶段ID
     * @return Future<JsonObject> 阶段详情
     */
    fun getStageDetails(stageId: String): Future<JsonObject>
    
    /**
     * 添加流水线阶段
     * 
     * @param stage 阶段配置
     * @return Future<JsonObject> 添加结果
     */
    fun addStage(stage: JsonObject): Future<JsonObject>
    
    /**
     * 更新流水线阶段
     * 
     * @param stageId 阶段ID
     * @param stage 阶段配置
     * @return Future<JsonObject> 更新结果
     */
    fun updateStage(stageId: String, stage: JsonObject): Future<JsonObject>
    
    /**
     * 删除流水线阶段
     * 
     * @param stageId 阶段ID
     * @return Future<JsonObject> 删除结果
     */
    fun deleteStage(stageId: String): Future<JsonObject>
    
    /**
     * 获取流水线任务列表
     * 
     * @param stageId 阶段ID
     * @return Future<JsonArray> 任务列表
     */
    fun getTasks(stageId: String): Future<JsonArray>
    
    /**
     * 获取流水线任务详情
     * 
     * @param stageId 阶段ID
     * @param taskId 任务ID
     * @return Future<JsonObject> 任务详情
     */
    fun getTaskDetails(stageId: String, taskId: String): Future<JsonObject>
    
    /**
     * 添加流水线任务
     * 
     * @param stageId 阶段ID
     * @param task 任务配置
     * @return Future<JsonObject> 添加结果
     */
    fun addTask(stageId: String, task: JsonObject): Future<JsonObject>
    
    /**
     * 更新流水线任务
     * 
     * @param stageId 阶段ID
     * @param taskId 任务ID
     * @param task 任务配置
     * @return Future<JsonObject> 更新结果
     */
    fun updateTask(stageId: String, taskId: String, task: JsonObject): Future<JsonObject>
    
    /**
     * 删除流水线任务
     * 
     * @param stageId 阶段ID
     * @param taskId 任务ID
     * @return Future<JsonObject> 删除结果
     */
    fun deleteTask(stageId: String, taskId: String): Future<JsonObject>
    
    /**
     * 执行流水线
     * 
     * @param params 执行参数
     * @return Future<JsonObject> 执行结果
     */
    fun execute(params: JsonObject): Future<JsonObject>
    
    /**
     * 获取流水线执行历史
     * 
     * @param limit 限制数量
     * @param offset 偏移量
     * @return Future<JsonArray> 执行历史
     */
    fun getExecutionHistory(limit: Int, offset: Int): Future<JsonArray>
    
    /**
     * 获取流水线执行详情
     * 
     * @param executionId 执行ID
     * @return Future<JsonObject> 执行详情
     */
    fun getExecutionDetails(executionId: String): Future<JsonObject>
    
    /**
     * 取消流水线执行
     * 
     * @param executionId 执行ID
     * @return Future<JsonObject> 取消结果
     */
    fun cancelExecution(executionId: String): Future<JsonObject>
    
    /**
     * 获取流水线状态
     * 
     * @return JsonObject 状态信息
     */
    fun getStatus(): JsonObject
    
    /**
     * 关闭流水线
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void>
}
