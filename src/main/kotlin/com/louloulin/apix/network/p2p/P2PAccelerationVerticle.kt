package com.louloulin.apix.network.p2p

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.BaseVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 点对点加速Verticle
 * 处理点对点加速相关的操作，提供EventBus接口
 */
class P2PAccelerationVerticle : BaseVerticle() {
    // 使用BaseVerticle中的logger
    
    // 点对点加速管理器
    private lateinit var p2pManager: P2PAccelerationManager
    
    /**
     * 注册EventBus处理器
     */
    override fun registerEventBusHandlers() {
        // 获取点对点加速状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.P2P_STATUS_GET) { message ->
            val status = p2pManager.getStatus()
            sendSuccess(message, status)
        }
        
        // 获取最佳路由
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.P2P_ROUTE_GET) { message ->
            val sourceRegion = message.body().getString("sourceRegion", "")
            val targetRegion = message.body().getString("targetRegion", "")
            
            if (sourceRegion.isEmpty()) {
                sendError(message, 400, "sourceRegion parameter is required")
                return@consumer
            }
            
            if (targetRegion.isEmpty()) {
                sendError(message, 400, "targetRegion parameter is required")
                return@consumer
            }
            
            p2pManager.getBestRoute(sourceRegion, targetRegion)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 添加加速节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.P2P_NODE_ADD) { message ->
            val id = message.body().getString("id", "")
            val name = message.body().getString("name", "")
            val address = message.body().getString("address", "")
            val port = message.body().getInteger("port", 0)
            val region = message.body().getString("region", "")
            
            if (id.isEmpty()) {
                sendError(message, 400, "id parameter is required")
                return@consumer
            }
            
            if (address.isEmpty()) {
                sendError(message, 400, "address parameter is required")
                return@consumer
            }
            
            if (port == 0) {
                sendError(message, 400, "port parameter is required")
                return@consumer
            }
            
            p2pManager.addAccelerationNode(id, name, address, port, region)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 删除加速节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.P2P_NODE_REMOVE) { message ->
            val id = message.body().getString("id", "")
            
            if (id.isEmpty()) {
                sendError(message, 400, "id parameter is required")
                return@consumer
            }
            
            p2pManager.removeAccelerationNode(id)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 更新点对点加速配置
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.P2P_CONFIG_UPDATE) { message ->
            val config = message.body()
            
            p2pManager.updateConfig(config)
                .onSuccess {
                    sendSuccess(message, JsonObject().put("updated", true))
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
    }
    
    /**
     * 启动Verticle
     */
    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动点对点加速Verticle")
        
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // 获取点对点加速配置
                    val p2pConfig = config.getJsonObject("p2p", JsonObject())
                    
                    // 初始化点对点加速管理器
                    p2pManager = P2PAccelerationManager.getInstance(vertx)
                    
                    p2pManager.initialize(p2pConfig)
                        .onSuccess {
                            logger.info("点对点加速管理器初始化成功")
                            
                            // 注册点对点加速组件状态
                            registerComponentStatus()
                            
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("点对点加速管理器初始化失败", cause)
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
     * 注册点对点加速组件状态
     */
    private fun registerComponentStatus() {
        val status = p2pManager.getStatus()
        val enabled = status.getBoolean("enabled", false)
        
        vertx.eventBus().send(EventBusAddresses.HEALTH_COMPONENT_STATUS, JsonObject()
            .put("component", "p2p")
            .put("status", enabled)
        )
    }
    
    /**
     * 停止Verticle
     */
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止点对点加速Verticle")
        
        if (::p2pManager.isInitialized) {
            p2pManager.close()
                .onSuccess {
                    logger.info("点对点加速管理器关闭成功")
                    stopPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("点对点加速管理器关闭失败", cause)
                    stopPromise.fail(cause)
                }
        } else {
            stopPromise.complete()
        }
    }
}
