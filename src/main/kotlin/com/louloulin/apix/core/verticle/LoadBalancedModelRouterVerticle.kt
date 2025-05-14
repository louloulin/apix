package com.louloulin.apix.core.verticle

import com.louloulin.apix.ai.router.LoadBalancedModelRouter
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 负载均衡模型路由器Verticle，负责管理负载均衡模型路由功能。
 */
class LoadBalancedModelRouterVerticle : BaseVerticle() {
    // 负载均衡模型路由器
    private lateinit var loadBalancedModelRouter: LoadBalancedModelRouter
    
    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动 LoadBalancedModelRouterVerticle...")
        
        // 创建负载均衡模型路由器
        loadBalancedModelRouter = LoadBalancedModelRouter(vertx)
        
        // 从配置中加载负载均衡模型路由器配置
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_GET,
            JsonObject().put("section", "loadBalancedModelRouter")
        ) { ar ->
            if (ar.succeeded() && ar.result().body().getBoolean("success", false)) {
                val config = ar.result().body().getJsonObject("result", JsonObject())
                
                // 初始化负载均衡模型路由器
                loadBalancedModelRouter.initialize(config)
                    .onSuccess {
                        // 注册EventBus处理器
                        registerEventBusHandlers()
                        
                        logger.info("LoadBalancedModelRouterVerticle 启动完成")
                        startPromise.complete()
                    }
                    .onFailure { err ->
                        logger.error("初始化负载均衡模型路由器失败", err)
                        startPromise.fail(err)
                    }
            } else {
                // 如果配置请求失败，使用空配置初始化
                loadBalancedModelRouter.initialize(JsonObject())
                    .onSuccess {
                        // 注册EventBus处理器
                        registerEventBusHandlers()
                        
                        logger.info("LoadBalancedModelRouterVerticle 使用默认配置启动完成")
                        startPromise.complete()
                    }
                    .onFailure { err ->
                        logger.error("初始化负载均衡模型路由器失败", err)
                        startPromise.fail(err)
                    }
            }
        }
    }
    
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止 LoadBalancedModelRouterVerticle...")
        
        // 关闭负载均衡模型路由器
        loadBalancedModelRouter.close()
            .onSuccess {
                logger.info("LoadBalancedModelRouterVerticle 停止完成")
                stopPromise.complete()
            }
            .onFailure { err ->
                logger.error("关闭负载均衡模型路由器失败", err)
                stopPromise.fail(err)
            }
    }
    
    override fun registerEventBusHandlers() {
        // 负载均衡路由
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_ROUTE) { message ->
            val request = message.body()
            
            loadBalancedModelRouter.routeRequest(request)
                .onSuccess { model ->
                    sendSuccess(message, JsonObject()
                        .put("model", model)
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "路由请求失败")
                }
        }
        
        // 记录请求完成
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_REQUEST_COMPLETION) { message ->
            val request = message.body()
            val modelId = request.getString("modelId", "")
            val requestId = request.getString("requestId", "")
            val success = request.getBoolean("success", true)
            val errorType = request.getString("errorType", "")
            
            if (modelId.isEmpty() || requestId.isEmpty()) {
                sendError(message, 400, "缺少必要参数")
                return@consumer
            }
            
            loadBalancedModelRouter.recordRequestCompletion(modelId, requestId, success, errorType)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "请求完成记录成功")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "记录请求完成失败")
                }
        }
        
        // 获取模型组
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_GROUPS_GET) { message ->
            val groups = loadBalancedModelRouter.getModelGroups().map { it.toJson() }
            sendSuccess(message, JsonObject()
                .put("groups", groups)
            )
        }
        
        // 获取模型统计信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_STATS_GET) { message ->
            val stats = loadBalancedModelRouter.getModelStats()
            sendSuccess(message, JsonObject()
                .put("stats", stats)
            )
        }
        
        // 添加模型组
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_GROUP_ADD) { message ->
            val groupJson = message.body().getJsonObject("group")
            if (groupJson == null) {
                sendError(message, 400, "缺少模型组数据")
                return@consumer
            }
            
            val group = LoadBalancedModelRouter.ModelGroup.fromJson(groupJson)
            loadBalancedModelRouter.addModelGroup(group)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("id", group.id)
                        .put("message", "模型组添加成功")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "添加模型组失败")
                }
        }
        
        // 移除模型组
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_GROUP_REMOVE) { message ->
            val groupId = message.body().getString("id")
            if (groupId == null) {
                sendError(message, 400, "缺少模型组ID")
                return@consumer
            }
            
            loadBalancedModelRouter.removeModelGroup(groupId)
                .onSuccess { removed ->
                    sendSuccess(message, JsonObject()
                        .put("removed", removed)
                        .put("message", if (removed) "模型组移除成功" else "模型组不存在")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "移除模型组失败")
                }
        }
        
        // 清除所有模型组
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_GROUPS_CLEAR) { message ->
            loadBalancedModelRouter.clearModelGroups()
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "所有模型组已清除")
                    )
                }
                .onFailure { err ->
                    sendError(message, 500, err.message ?: "清除模型组失败")
                }
        }
    }
}
