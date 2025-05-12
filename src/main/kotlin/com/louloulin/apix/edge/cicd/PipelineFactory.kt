package com.louloulin.apix.edge.cicd

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 流水线工厂类
 * 负责创建和管理不同的 CI/CD 流水线
 * 实现 plan7.md 中的 4.2.1 节"CI/CD 流水线"功能
 */
class PipelineFactory(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(PipelineFactory::class.java)
    
    // 流水线实例映射
    private val pipelines = ConcurrentHashMap<String, Pipeline>()
    
    /**
     * 获取 PipelineFactory 实例
     */
    companion object {
        private var instance: PipelineFactory? = null
        
        @Synchronized
        fun getInstance(vertx: Vertx): PipelineFactory {
            if (instance == null) {
                instance = PipelineFactory(vertx)
            }
            return instance!!
        }
    }
    
    /**
     * 创建流水线
     * 
     * @param type 流水线类型
     * @param config 配置
     * @return Future<Pipeline> 流水线实例
     */
    fun createPipeline(type: PipelineType, config: JsonObject): Future<Pipeline> {
        val promise = Promise.promise<Pipeline>()
        
        try {
            // 生成流水线ID
            val pipelineId = config.getString("id", "${type.name.toLowerCase()}-${System.currentTimeMillis()}")
            
            // 检查是否已存在
            if (pipelines.containsKey(pipelineId)) {
                promise.complete(pipelines[pipelineId])
                return promise.future()
            }
            
            // 创建流水线实例
            val pipeline = when (type) {
                PipelineType.JENKINS -> JenkinsPipeline(vertx)
                PipelineType.GITLAB -> throw UnsupportedOperationException("GitLab CI/CD 流水线尚未实现")
                PipelineType.GITHUB_ACTIONS -> throw UnsupportedOperationException("GitHub Actions 流水线尚未实现")
                PipelineType.CIRCLE_CI -> throw UnsupportedOperationException("CircleCI 流水线尚未实现")
                PipelineType.TRAVIS_CI -> throw UnsupportedOperationException("Travis CI 流水线尚未实现")
                PipelineType.TEAM_CITY -> throw UnsupportedOperationException("TeamCity 流水线尚未实现")
                PipelineType.BAMBOO -> throw UnsupportedOperationException("Bamboo 流水线尚未实现")
                PipelineType.AZURE_DEVOPS -> throw UnsupportedOperationException("Azure DevOps 流水线尚未实现")
                PipelineType.CUSTOM -> throw UnsupportedOperationException("自定义流水线尚未实现")
            }
            
            // 更新配置中的 ID
            val updatedConfig = config.copy().put("id", pipelineId)
            
            // 初始化流水线
            pipeline.initialize(updatedConfig)
                .onSuccess {
                    // 保存流水线实例
                    pipelines[pipelineId] = pipeline
                    
                    logger.info("创建流水线成功: {}, {}", type, pipelineId)
                    promise.complete(pipeline)
                }
                .onFailure { cause ->
                    logger.error("初始化流水线失败: {}, {}", type, pipelineId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("创建流水线失败: {}", type, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 获取流水线
     * 
     * @param pipelineId 流水线ID
     * @return Pipeline? 流水线实例，如果不存在则返回 null
     */
    fun getPipeline(pipelineId: String): Pipeline? {
        return pipelines[pipelineId]
    }
    
    /**
     * 获取所有流水线
     * 
     * @return Map<String, Pipeline> 所有流水线实例
     */
    fun getAllPipelines(): Map<String, Pipeline> {
        return pipelines.toMap()
    }
    
    /**
     * 关闭流水线
     * 
     * @param pipelineId 流水线ID
     * @return Future<Void> 关闭结果
     */
    fun closePipeline(pipelineId: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 检查是否存在
            val pipeline = pipelines[pipelineId]
            if (pipeline == null) {
                promise.complete()
                return promise.future()
            }
            
            // 关闭流水线
            pipeline.close()
                .onSuccess {
                    // 移除流水线实例
                    pipelines.remove(pipelineId)
                    
                    logger.info("关闭流水线成功: {}", pipelineId)
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("关闭流水线失败: {}", pipelineId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("关闭流水线失败: {}", pipelineId, e)
            promise.fail(e)
        }
        
        return promise.future()
    }
    
    /**
     * 关闭所有流水线
     * 
     * @return Future<Void> 关闭结果
     */
    fun closeAllPipelines(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 创建关闭任务列表
            val closeTasks = mutableListOf<Future<Void>>()
            
            // 关闭所有流水线
            for (pipelineId in pipelines.keys) {
                closeTasks.add(closePipeline(pipelineId))
            }
            
            // 等待所有关闭任务完成
            Future.all(closeTasks)
                .onSuccess {
                    logger.info("关闭所有流水线成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("关闭所有流水线失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("关闭所有流水线失败", e)
            promise.fail(e)
        }
        
        return promise.future()
    }
}
