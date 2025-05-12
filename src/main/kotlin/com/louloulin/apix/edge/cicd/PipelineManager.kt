package com.louloulin.apix.edge.cicd

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/**
 * 流水线管理器
 * 负责管理 CI/CD 流水线和流水线模板
 * 实现 plan7.md 中的 4.2.1 节"CI/CD 流水线"功能
 */
class PipelineManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PipelineManager::class.java)
    
    // 配置
    private val config = AtomicReference<JsonObject>(JsonObject())
    
    // 流水线工厂
    private val pipelineFactory = PipelineFactory.getInstance(vertx)
    
    // 流水线模板
    private val templates = ConcurrentHashMap<String, JsonObject>()
    
    // 流水线配置
    private val pipelineConfigs = ConcurrentHashMap<String, JsonObject>()
    
    /**
     * 获取 PipelineManager 实例
     */
    companion object {
        private var instance: PipelineManager? = null
        
        @Synchronized
        fun getInstance(vertx: Vertx): PipelineManager {
            if (instance == null) {
                instance = PipelineManager(vertx)
            }
            return instance!!
        }
    }
    
    /**
     * 初始化流水线管理器
     * 
     * @param config 配置
     * @return Future<Void> 初始化结果
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化流水线管理器")
        
        val promise = Promise.promise<Void>()
        
        try {
            // 保存配置
            this.config.set(config)
            
            // 加载流水线模板
            loadTemplates()
                .compose {
                    // 加载流水线配置
                    loadPipelineConfigs()
                }
                .onSuccess {
                    logger.info("流水线管理器初始化成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("流水线管理器初始化失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("流水线管理器初始化失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 加载流水线模板
     * 
     * @return Future<Void> 加载结果
     */
    private fun loadTemplates(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 获取模板配置
            val templatesConfig = config.get().getJsonArray("templates", JsonArray())
            
            for (i in 0 until templatesConfig.size()) {
                val template = templatesConfig.getJsonObject(i)
                val templateId = template.getString("id", UUID.randomUUID().toString())
                
                templates[templateId] = template
            }
            
            logger.info("加载了 {} 个流水线模板", templates.size)
            promise.complete()
        } catch (e: Exception) {
            logger.error("加载流水线模板失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 加载流水线配置
     * 
     * @return Future<Void> 加载结果
     */
    private fun loadPipelineConfigs(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 获取流水线配置
            val pipelinesConfig = config.get().getJsonArray("pipelines", JsonArray())
            
            for (i in 0 until pipelinesConfig.size()) {
                val pipelineConfig = pipelinesConfig.getJsonObject(i)
                val pipelineId = pipelineConfig.getString("id", UUID.randomUUID().toString())
                
                pipelineConfigs[pipelineId] = pipelineConfig
            }
            
            logger.info("加载了 {} 个流水线配置", pipelineConfigs.size)
            promise.complete()
        } catch (e: Exception) {
            logger.error("加载流水线配置失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线模板列表
     * 
     * @return Future<JsonArray> 模板列表
     */
    fun getTemplates(): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()
        
        try {
            val result = JsonArray()
            
            for ((templateId, template) in templates) {
                result.add(template.copy().put("id", templateId))
            }
            
            promise.complete(result)
        } catch (e: Exception) {
            logger.error("获取流水线模板列表失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线模板详情
     * 
     * @param templateId 模板ID
     * @return Future<JsonObject> 模板详情
     */
    fun getTemplateDetails(templateId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            val template = templates[templateId]
            
            if (template == null) {
                promise.fail("流水线模板不存在: $templateId")
                return promise.future()
            }
            
            promise.complete(template.copy().put("id", templateId))
        } catch (e: Exception) {
            logger.error("获取流水线模板详情失败: {}", templateId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 创建流水线模板
     * 
     * @param template 模板配置
     * @return Future<JsonObject> 创建结果
     */
    fun createTemplate(template: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 生成模板ID
            val templateId = template.getString("id", UUID.randomUUID().toString())
            
            // 检查是否已存在
            if (templates.containsKey(templateId)) {
                promise.fail("流水线模板已存在: $templateId")
                return promise.future()
            }
            
            // 添加创建时间
            val newTemplate = template.copy()
                .put("createdAt", System.currentTimeMillis())
            
            // 保存模板
            templates[templateId] = newTemplate
            
            logger.info("创建流水线模板: {}", templateId)
            
            promise.complete(newTemplate.copy().put("id", templateId))
        } catch (e: Exception) {
            logger.error("创建流水线模板失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 更新流水线模板
     * 
     * @param templateId 模板ID
     * @param template 模板配置
     * @return Future<JsonObject> 更新结果
     */
    fun updateTemplate(templateId: String, template: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 检查是否存在
            if (!templates.containsKey(templateId)) {
                promise.fail("流水线模板不存在: $templateId")
                return promise.future()
            }
            
            // 获取原配置
            val oldTemplate = templates[templateId]!!
            
            // 合并配置
            val newTemplate = oldTemplate.copy().mergeIn(template)
                .put("updatedAt", System.currentTimeMillis())
            
            // 保存模板
            templates[templateId] = newTemplate
            
            logger.info("更新流水线模板: {}", templateId)
            
            promise.complete(newTemplate.copy().put("id", templateId))
        } catch (e: Exception) {
            logger.error("更新流水线模板失败: {}", templateId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 删除流水线模板
     * 
     * @param templateId 模板ID
     * @return Future<JsonObject> 删除结果
     */
    fun deleteTemplate(templateId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 检查是否存在
            if (!templates.containsKey(templateId)) {
                promise.fail("流水线模板不存在: $templateId")
                return promise.future()
            }
            
            // 删除模板
            templates.remove(templateId)
            
            logger.info("删除流水线模板: {}", templateId)
            
            promise.complete(JsonObject()
                .put("id", templateId)
                .put("success", true)
                .put("message", "流水线模板删除成功")
            )
        } catch (e: Exception) {
            logger.error("删除流水线模板失败: {}", templateId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线列表
     * 
     * @return Future<JsonArray> 流水线列表
     */
    fun getPipelines(): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()
        
        try {
            val result = JsonArray()
            
            for ((pipelineId, pipelineConfig) in pipelineConfigs) {
                result.add(pipelineConfig.copy().put("id", pipelineId))
            }
            
            promise.complete(result)
        } catch (e: Exception) {
            logger.error("获取流水线列表失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线详情
     * 
     * @param pipelineId 流水线ID
     * @return Future<JsonObject> 流水线详情
     */
    fun getPipelineDetails(pipelineId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            val pipelineConfig = pipelineConfigs[pipelineId]
            
            if (pipelineConfig == null) {
                promise.fail("流水线不存在: $pipelineId")
                return promise.future()
            }
            
            promise.complete(pipelineConfig.copy().put("id", pipelineId))
        } catch (e: Exception) {
            logger.error("获取流水线详情失败: {}", pipelineId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 创建流水线
     * 
     * @param pipelineConfig 流水线配置
     * @return Future<JsonObject> 创建结果
     */
    fun createPipeline(pipelineConfig: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 生成流水线ID
            val pipelineId = pipelineConfig.getString("id", UUID.randomUUID().toString())
            
            // 检查是否已存在
            if (pipelineConfigs.containsKey(pipelineId)) {
                promise.fail("流水线已存在: $pipelineId")
                return promise.future()
            }
            
            // 检查是否使用模板
            val templateId = pipelineConfig.getString("templateId", "")
            if (templateId.isNotEmpty()) {
                // 获取模板
                val template = templates[templateId]
                if (template == null) {
                    promise.fail("流水线模板不存在: $templateId")
                    return promise.future()
                }
                
                // 合并模板配置
                val templateConfig = template.copy()
                
                // 移除模板特有字段
                templateConfig.remove("id")
                templateConfig.remove("name")
                templateConfig.remove("description")
                templateConfig.remove("createdAt")
                templateConfig.remove("updatedAt")
                
                // 合并配置
                pipelineConfig.mergeIn(templateConfig, true)
            }
            
            // 添加创建时间
            val newPipelineConfig = pipelineConfig.copy()
                .put("createdAt", System.currentTimeMillis())
            
            // 保存流水线配置
            pipelineConfigs[pipelineId] = newPipelineConfig
            
            // 获取流水线类型
            val pipelineType = try {
                PipelineType.valueOf(newPipelineConfig.getString("type"))
            } catch (e: Exception) {
                promise.fail("无效的流水线类型: ${newPipelineConfig.getString("type")}")
                return promise.future()
            }
            
            // 创建流水线实例
            pipelineFactory.createPipeline(pipelineType, newPipelineConfig)
                .onSuccess { pipeline ->
                    logger.info("创建流水线: {}", pipelineId)
                    
                    promise.complete(newPipelineConfig.copy().put("id", pipelineId))
                }
                .onFailure { cause ->
                    // 删除流水线配置
                    pipelineConfigs.remove(pipelineId)
                    
                    logger.error("创建流水线失败: {}", pipelineId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("创建流水线失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 更新流水线
     * 
     * @param pipelineId 流水线ID
     * @param pipelineConfig 流水线配置
     * @return Future<JsonObject> 更新结果
     */
    fun updatePipeline(pipelineId: String, pipelineConfig: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 检查是否存在
            if (!pipelineConfigs.containsKey(pipelineId)) {
                promise.fail("流水线不存在: $pipelineId")
                return promise.future()
            }
            
            // 获取原配置
            val oldPipelineConfig = pipelineConfigs[pipelineId]!!
            
            // 合并配置
            val newPipelineConfig = oldPipelineConfig.copy().mergeIn(pipelineConfig)
                .put("updatedAt", System.currentTimeMillis())
            
            // 保存流水线配置
            pipelineConfigs[pipelineId] = newPipelineConfig
            
            // 获取流水线实例
            val pipeline = pipelineFactory.getPipeline(pipelineId)
            if (pipeline == null) {
                // 获取流水线类型
                val pipelineType = try {
                    PipelineType.valueOf(newPipelineConfig.getString("type"))
                } catch (e: Exception) {
                    promise.fail("无效的流水线类型: ${newPipelineConfig.getString("type")}")
                    return promise.future()
                }
                
                // 创建流水线实例
                pipelineFactory.createPipeline(pipelineType, newPipelineConfig)
                    .onSuccess {
                        logger.info("更新流水线: {}", pipelineId)
                        
                        promise.complete(newPipelineConfig.copy().put("id", pipelineId))
                    }
                    .onFailure { cause ->
                        logger.error("更新流水线失败: {}", pipelineId, cause)
                        promise.fail(cause)
                    }
            } else {
                // 关闭原流水线
                pipelineFactory.closePipeline(pipelineId)
                    .compose {
                        // 获取流水线类型
                        val pipelineType = try {
                            PipelineType.valueOf(newPipelineConfig.getString("type"))
                        } catch (e: Exception) {
                            promise.fail("无效的流水线类型: ${newPipelineConfig.getString("type")}")
                            return@compose Future.failedFuture<Pipeline>(e)
                        }
                        
                        // 创建流水线实例
                        pipelineFactory.createPipeline(pipelineType, newPipelineConfig)
                    }
                    .onSuccess {
                        logger.info("更新流水线: {}", pipelineId)
                        
                        promise.complete(newPipelineConfig.copy().put("id", pipelineId))
                    }
                    .onFailure { cause ->
                        logger.error("更新流水线失败: {}", pipelineId, cause)
                        promise.fail(cause)
                    }
            }
        } catch (e: Exception) {
            logger.error("更新流水线失败: {}", pipelineId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 删除流水线
     * 
     * @param pipelineId 流水线ID
     * @return Future<JsonObject> 删除结果
     */
    fun deletePipeline(pipelineId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 检查是否存在
            if (!pipelineConfigs.containsKey(pipelineId)) {
                promise.fail("流水线不存在: $pipelineId")
                return promise.future()
            }
            
            // 删除流水线配置
            pipelineConfigs.remove(pipelineId)
            
            // 关闭流水线实例
            pipelineFactory.closePipeline(pipelineId)
                .onSuccess {
                    logger.info("删除流水线: {}", pipelineId)
                    
                    promise.complete(JsonObject()
                        .put("id", pipelineId)
                        .put("success", true)
                        .put("message", "流水线删除成功")
                    )
                }
                .onFailure { cause ->
                    logger.error("删除流水线失败: {}", pipelineId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("删除流水线失败: {}", pipelineId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 执行流水线
     * 
     * @param pipelineId 流水线ID
     * @param params 执行参数
     * @return Future<JsonObject> 执行结果
     */
    fun executePipeline(pipelineId: String, params: JsonObject): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 检查是否存在
            if (!pipelineConfigs.containsKey(pipelineId)) {
                promise.fail("流水线不存在: $pipelineId")
                return promise.future()
            }
            
            // 获取流水线实例
            val pipeline = pipelineFactory.getPipeline(pipelineId)
            if (pipeline == null) {
                // 获取流水线配置
                val pipelineConfig = pipelineConfigs[pipelineId]!!
                
                // 获取流水线类型
                val pipelineType = try {
                    PipelineType.valueOf(pipelineConfig.getString("type"))
                } catch (e: Exception) {
                    promise.fail("无效的流水线类型: ${pipelineConfig.getString("type")}")
                    return promise.future()
                }
                
                // 创建流水线实例
                pipelineFactory.createPipeline(pipelineType, pipelineConfig)
                    .compose { newPipeline ->
                        // 执行流水线
                        newPipeline.execute(params)
                    }
                    .onSuccess { result ->
                        logger.info("执行流水线: {}", pipelineId)
                        
                        promise.complete(result)
                    }
                    .onFailure { cause ->
                        logger.error("执行流水线失败: {}", pipelineId, cause)
                        promise.fail(cause)
                    }
            } else {
                // 执行流水线
                pipeline.execute(params)
                    .onSuccess { result ->
                        logger.info("执行流水线: {}", pipelineId)
                        
                        promise.complete(result)
                    }
                    .onFailure { cause ->
                        logger.error("执行流水线失败: {}", pipelineId, cause)
                        promise.fail(cause)
                    }
            }
        } catch (e: Exception) {
            logger.error("执行流水线失败: {}", pipelineId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线执行历史
     * 
     * @param pipelineId 流水线ID
     * @param limit 限制数量
     * @param offset 偏移量
     * @return Future<JsonArray> 执行历史
     */
    fun getPipelineExecutionHistory(pipelineId: String, limit: Int, offset: Int): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()
        
        try {
            // 检查是否存在
            if (!pipelineConfigs.containsKey(pipelineId)) {
                promise.fail("流水线不存在: $pipelineId")
                return promise.future()
            }
            
            // 获取流水线实例
            val pipeline = pipelineFactory.getPipeline(pipelineId)
            if (pipeline == null) {
                // 返回空列表
                promise.complete(JsonArray())
                return promise.future()
            }
            
            // 获取执行历史
            pipeline.getExecutionHistory(limit, offset)
                .onSuccess { result ->
                    promise.complete(result)
                }
                .onFailure { cause ->
                    logger.error("获取流水线执行历史失败: {}", pipelineId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("获取流水线执行历史失败: {}", pipelineId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线执行详情
     * 
     * @param pipelineId 流水线ID
     * @param executionId 执行ID
     * @return Future<JsonObject> 执行详情
     */
    fun getPipelineExecutionDetails(pipelineId: String, executionId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 检查是否存在
            if (!pipelineConfigs.containsKey(pipelineId)) {
                promise.fail("流水线不存在: $pipelineId")
                return promise.future()
            }
            
            // 获取流水线实例
            val pipeline = pipelineFactory.getPipeline(pipelineId)
            if (pipeline == null) {
                promise.fail("流水线实例不存在: $pipelineId")
                return promise.future()
            }
            
            // 获取执行详情
            pipeline.getExecutionDetails(executionId)
                .onSuccess { result ->
                    promise.complete(result)
                }
                .onFailure { cause ->
                    logger.error("获取流水线执行详情失败: {}, {}", pipelineId, executionId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("获取流水线执行详情失败: {}, {}", pipelineId, executionId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 取消流水线执行
     * 
     * @param pipelineId 流水线ID
     * @param executionId 执行ID
     * @return Future<JsonObject> 取消结果
     */
    fun cancelPipelineExecution(pipelineId: String, executionId: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        try {
            // 检查是否存在
            if (!pipelineConfigs.containsKey(pipelineId)) {
                promise.fail("流水线不存在: $pipelineId")
                return promise.future()
            }
            
            // 获取流水线实例
            val pipeline = pipelineFactory.getPipeline(pipelineId)
            if (pipeline == null) {
                promise.fail("流水线实例不存在: $pipelineId")
                return promise.future()
            }
            
            // 取消执行
            pipeline.cancelExecution(executionId)
                .onSuccess { result ->
                    promise.complete(result)
                }
                .onFailure { cause ->
                    logger.error("取消流水线执行失败: {}, {}", pipelineId, executionId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("取消流水线执行失败: {}, {}", pipelineId, executionId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线管理器状态
     * 
     * @return JsonObject 状态信息
     */
    fun getStatus(): JsonObject {
        return JsonObject()
            .put("templateCount", templates.size)
            .put("pipelineCount", pipelineConfigs.size)
            .put("activePipelineCount", pipelineFactory.getAllPipelines().size)
            .put("timestamp", System.currentTimeMillis())
    }
    
    /**
     * 关闭流水线管理器
     * 
     * @return Future<Void> 关闭结果
     */
    fun close(): Future<Void> {
        logger.info("关闭流水线管理器")
        
        val promise = Promise.promise<Void>()
        
        try {
            // 关闭所有流水线
            pipelineFactory.closeAllPipelines()
                .onSuccess {
                    // 清空数据
                    templates.clear()
                    pipelineConfigs.clear()
                    
                    logger.info("流水线管理器关闭成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("关闭流水线失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("关闭流水线管理器失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
}
