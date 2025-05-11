package com.louloulin.apix.edge

import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import com.louloulin.apix.core.verticle.BaseVerticle
import com.louloulin.apix.core.common.EventBusAddresses

/**
 * 边缘控制平面Verticle，负责初始化和管理边缘控制平面功能。
 * 实现plan7.md中的2.2.1节"边缘控制平面架构"功能。
 */
class EdgeControlPlaneVerticle : BaseVerticle() {
    
    // 边缘控制平面管理器
    private lateinit var edgeControlPlaneManager: EdgeControlPlaneManager
    
    override fun registerEventBusHandlers() {
        // 注册边缘控制平面相关的事件总线处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_STATUS_GET) { message ->
            val status = edgeControlPlaneManager.getStatus()
            sendSuccess(message, status)
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODES_GET) { message ->
            try {
                // 获取边缘节点列表
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODES_GET, JsonObject()) { ar ->
                    if (ar.succeeded()) {
                        sendSuccess(message, ar.result().body().getJsonArray("result"))
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } catch (e: Exception) {
                sendError(message, e)
            }
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_VERSION_GET) { message ->
            try {
                // 获取配置版本
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_VERSION_GET, JsonObject()) { ar ->
                    if (ar.succeeded()) {
                        sendSuccess(message, ar.result().body().getJsonObject("result"))
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } catch (e: Exception) {
                sendError(message, e)
            }
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_GET) { message ->
            val version = message.body().getString("version")
            
            try {
                // 获取配置
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_GET, JsonObject().put("version", version)) { ar ->
                    if (ar.succeeded()) {
                        sendSuccess(message, ar.result().body().getJsonObject("result"))
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } catch (e: Exception) {
                sendError(message, e)
            }
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_UPDATE) { message ->
            val config = message.body().getJsonObject("config")
            
            if (config == null) {
                sendError(message, 400, "Missing config parameter")
                return@consumer
            }
            
            try {
                // 更新配置
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_UPDATE, JsonObject().put("config", config)) { ar ->
                    if (ar.succeeded()) {
                        sendSuccess(message, ar.result().body().getJsonObject("result"))
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } catch (e: Exception) {
                sendError(message, e)
            }
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_ROLLBACK) { message ->
            val version = message.body().getString("version")
            
            if (version == null) {
                sendError(message, 400, "Missing version parameter")
                return@consumer
            }
            
            try {
                // 回滚配置
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_CONFIG_ROLLBACK, JsonObject().put("version", version)) { ar ->
                    if (ar.succeeded()) {
                        sendSuccess(message, ar.result().body().getJsonObject("result"))
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } catch (e: Exception) {
                sendError(message, e)
            }
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_REGISTER) { message ->
            val nodeInfo = message.body()
            
            try {
                // 注册边缘节点
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_REGISTER, nodeInfo) { ar ->
                    if (ar.succeeded()) {
                        sendSuccess(message, ar.result().body().getJsonObject("result"))
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } catch (e: Exception) {
                sendError(message, e)
            }
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_HEARTBEAT) { message ->
            val nodeId = message.body().getString("nodeId")
            
            if (nodeId == null) {
                sendError(message, 400, "Missing nodeId parameter")
                return@consumer
            }
            
            try {
                // 更新边缘节点心跳
                vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_PLANE_NODE_HEARTBEAT, JsonObject().put("nodeId", nodeId)) { ar ->
                    if (ar.succeeded()) {
                        sendSuccess(message, ar.result().body().getJsonObject("result"))
                    } else {
                        sendError(message, ar.cause())
                    }
                }
            } catch (e: Exception) {
                sendError(message, e)
            }
        }
    }
    
    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动边缘控制平面Verticle")
        
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // 初始化边缘控制平面管理器
                    edgeControlPlaneManager = EdgeControlPlaneManager.getInstance(vertx)
                    
                    // 初始化边缘控制平面管理器
                    edgeControlPlaneManager.initialize(config)
                        .onSuccess {
                            logger.info("边缘控制平面管理器初始化成功")
                            
                            // 注册边缘控制平面组件状态
                            registerComponentStatus()
                            
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("边缘控制平面管理器初始化失败", cause)
                            startPromise.fail(cause)
                        }
                } else {
                    val error = "获取配置失败: ${configResponse.getString("error", "未知错误")}"
                    logger.error(error)
                    startPromise.fail(error)
                }
            } else {
                logger.error("获取配置失败", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }
    
    /**
     * 注册边缘控制平面组件状态。
     */
    private fun registerComponentStatus() {
        // 向健康检查Verticle注册边缘控制平面组件状态
        val status = edgeControlPlaneManager.getStatus()
        val enabled = status.getBoolean("enabled", false)
        
        vertx.eventBus().send(EventBusAddresses.HEALTH_COMPONENT_STATUS, JsonObject()
            .put("component", "edge-control-plane")
            .put("status", enabled)
        )
    }
}
