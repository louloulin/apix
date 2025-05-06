package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.resource.ResourceManager
import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 资源管理Verticle，用于管理系统资源调整。
 * 提供了资源统计信息查询、资源调整配置、手动触发资源调整等功能。
 */
class ResourceManagerVerticle : AbstractVerticle() {
    private val logger = LoggerFactory.getLogger(ResourceManagerVerticle::class.java)
    
    // 资源管理器
    private lateinit var resourceManager: ResourceManager
    
    override fun start(startPromise: Promise<Void>) {
        logger.info("Starting ResourceManagerVerticle")
        
        // 初始化资源管理器
        resourceManager = ResourceManager.getInstance(vertx)
        
        // 注册资源统计信息查询处理器
        vertx.eventBus().consumer<JsonObject>("resource.stats") { message ->
            val stats = resourceManager.getResourceStats()
            message.reply(stats)
        }
        
        // 注册设置自动调整开关处理器
        vertx.eventBus().consumer<JsonObject>("resource.auto_adjust") { message ->
            val body = message.body()
            val enabled = body.getBoolean("enabled", true)
            
            resourceManager.setAutoAdjustEnabled(enabled)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("auto_adjust_enabled", enabled)
            )
        }
        
        // 注册设置调整间隔处理器
        vertx.eventBus().consumer<JsonObject>("resource.adjust_interval") { message ->
            val body = message.body()
            val interval = body.getLong("interval", 60000)
            
            resourceManager.setAdjustInterval(interval)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("adjust_interval_ms", interval)
            )
        }
        
        // 注册设置工作线程池大小范围处理器
        vertx.eventBus().consumer<JsonObject>("resource.worker_pool_size") { message ->
            val body = message.body()
            val min = body.getInteger("min", 10)
            val max = body.getInteger("max", 50)
            
            resourceManager.setWorkerPoolSizeRange(min, max)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("min", min)
                .put("max", max)
            )
        }
        
        // 注册设置事件循环线程池大小范围处理器
        vertx.eventBus().consumer<JsonObject>("resource.event_loop_pool_size") { message ->
            val body = message.body()
            val min = body.getInteger("min", Runtime.getRuntime().availableProcessors())
            val max = body.getInteger("max", Runtime.getRuntime().availableProcessors() * 2)
            
            resourceManager.setEventLoopPoolSizeRange(min, max)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("min", min)
                .put("max", max)
            )
        }
        
        // 注册设置连接池大小范围处理器
        vertx.eventBus().consumer<JsonObject>("resource.connection_pool_size") { message ->
            val body = message.body()
            val min = body.getInteger("min", 50)
            val max = body.getInteger("max", 500)
            
            resourceManager.setConnectionPoolSizeRange(min, max)
            
            message.reply(JsonObject()
                .put("success", true)
                .put("min", min)
                .put("max", max)
            )
        }
        
        // 注册手动触发资源调整处理器
        vertx.eventBus().consumer<JsonObject>("resource.adjust") { message ->
            resourceManager.triggerResourceAdjustment()
                .onComplete { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true)
                        )
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", ar.cause().message)
                        )
                    }
                }
        }
        
        // 注册Verticle部署注册处理器
        vertx.eventBus().consumer<JsonObject>("resource.register_verticle") { message ->
            val body = message.body()
            val verticleName = body.getString("verticle_name")
            val deploymentId = body.getString("deployment_id")
            val instances = body.getInteger("instances", 1)
            val minInstances = body.getInteger("min_instances", 1)
            val maxInstances = body.getInteger("max_instances", 10)
            
            if (verticleName != null && deploymentId != null) {
                resourceManager.registerVerticleDeployment(verticleName, deploymentId, instances, minInstances, maxInstances)
                
                message.reply(JsonObject()
                    .put("success", true)
                )
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing required parameters: verticle_name or deployment_id")
                )
            }
        }
        
        // 注册Verticle部署取消注册处理器
        vertx.eventBus().consumer<JsonObject>("resource.unregister_verticle") { message ->
            val body = message.body()
            val verticleName = body.getString("verticle_name")
            val deploymentId = body.getString("deployment_id")
            
            if (verticleName != null && deploymentId != null) {
                resourceManager.unregisterVerticleDeployment(verticleName, deploymentId)
                
                message.reply(JsonObject()
                    .put("success", true)
                )
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "Missing required parameters: verticle_name or deployment_id")
                )
            }
        }
        
        // 注册更新活跃连接数处理器
        vertx.eventBus().consumer<JsonObject>("resource.active_connections") { message ->
            val body = message.body()
            val count = body.getInteger("count", 0)
            
            resourceManager.updateActiveConnections(count)
            
            message.reply(JsonObject()
                .put("success", true)
            )
        }
        
        // 注册更新活跃请求数处理器
        vertx.eventBus().consumer<JsonObject>("resource.active_requests") { message ->
            val body = message.body()
            val count = body.getInteger("count", 0)
            
            resourceManager.updateActiveRequests(count)
            
            message.reply(JsonObject()
                .put("success", true)
            )
        }
        
        // 设置定期统计信息记录
        vertx.setPeriodic(60000) { // 每分钟记录一次
            val stats = resourceManager.getResourceStats()
            logger.info("资源统计信息: {}", stats.encode())
        }
        
        startPromise.complete()
    }
    
    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping ResourceManagerVerticle")
        stopPromise.complete()
    }
}
