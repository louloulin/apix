package com.louloulin.apix.edge.control

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.BaseVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 边缘控制Verticle
 * 处理边缘控制中心相关的操作，提供EventBus接口
 * 实现plan7.md中的2.2.3节"边缘控制中心"功能
 */
class EdgeControlVerticle : BaseVerticle() {
    // 边缘控制中心
    private lateinit var edgeControlCenter: EdgeControlCenter
    
    /**
     * 注册EventBus处理器
     */
    override fun registerEventBusHandlers() {
        // 获取边缘控制中心状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_CONTROL_STATUS_GET) { message ->
            val status = edgeControlCenter.getStatus()
            sendSuccess(message, status)
        }
        
        // 获取边缘节点列表
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODES_GET) { message ->
            val filter = message.body()
            
            edgeControlCenter.getNodes(filter)
                .onSuccess { nodes ->
                    sendSuccess(message, nodes)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 获取边缘节点详情
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_DETAILS_GET) { message ->
            val nodeId = message.body().getString("nodeId", "")
            
            if (nodeId.isEmpty()) {
                sendError(message, 400, "nodeId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.getNodeDetails(nodeId)
                .onSuccess { nodeDetails ->
                    sendSuccess(message, nodeDetails)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 添加边缘节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_ADD) { message ->
            val nodeInfo = message.body().getJsonObject("nodeInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (nodeInfo.isEmpty) {
                sendError(message, 400, "nodeInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.addNode(nodeInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 更新边缘节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_UPDATE) { message ->
            val nodeId = message.body().getString("nodeId", "")
            val nodeInfo = message.body().getJsonObject("nodeInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (nodeId.isEmpty()) {
                sendError(message, 400, "nodeId parameter is required")
                return@consumer
            }
            
            if (nodeInfo.isEmpty) {
                sendError(message, 400, "nodeInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.updateNode(nodeId, nodeInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 删除边缘节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_DELETE) { message ->
            val nodeId = message.body().getString("nodeId", "")
            val userId = message.body().getString("userId", "")
            
            if (nodeId.isEmpty()) {
                sendError(message, 400, "nodeId parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.deleteNode(nodeId, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 批量操作边缘节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODES_BATCH_OPERATE) { message ->
            val operation = message.body().getString("operation", "")
            val nodeIds = message.body().getJsonArray("nodeIds", JsonArray()).map { it.toString() }
            val params = message.body().getJsonObject("params", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (operation.isEmpty()) {
                sendError(message, 400, "operation parameter is required")
                return@consumer
            }
            
            if (nodeIds.isEmpty()) {
                sendError(message, 400, "nodeIds parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.batchOperateNodes(operation, nodeIds, params, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 获取节点组列表
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_GROUPS_GET) { message ->
            edgeControlCenter.getNodeGroups()
                .onSuccess { groups ->
                    sendSuccess(message, groups)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 创建节点组
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_GROUP_CREATE) { message ->
            val groupInfo = message.body().getJsonObject("groupInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (groupInfo.isEmpty) {
                sendError(message, 400, "groupInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.createNodeGroup(groupInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 更新节点组
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_GROUP_UPDATE) { message ->
            val groupId = message.body().getString("groupId", "")
            val groupInfo = message.body().getJsonObject("groupInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (groupId.isEmpty()) {
                sendError(message, 400, "groupId parameter is required")
                return@consumer
            }
            
            if (groupInfo.isEmpty) {
                sendError(message, 400, "groupInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.updateNodeGroup(groupId, groupInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 删除节点组
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_GROUP_DELETE) { message ->
            val groupId = message.body().getString("groupId", "")
            val userId = message.body().getString("userId", "")
            
            if (groupId.isEmpty()) {
                sendError(message, 400, "groupId parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.deleteNodeGroup(groupId, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 获取节点监控数据
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_METRICS_GET) { message ->
            val nodeId = message.body().getString("nodeId", "")
            val metrics = message.body().getJsonArray("metrics", JsonArray()).map { it.toString() }
            val timeRange = message.body().getJsonObject("timeRange", JsonObject())
            
            if (nodeId.isEmpty()) {
                sendError(message, 400, "nodeId parameter is required")
                return@consumer
            }
            
            if (metrics.isEmpty()) {
                sendError(message, 400, "metrics parameter is required")
                return@consumer
            }
            
            edgeControlCenter.getNodeMetrics(nodeId, metrics, timeRange)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 获取节点告警
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_ALERTS_GET) { message ->
            val nodeId = message.body().getString("nodeId", "")
            val filter = message.body().getJsonObject("filter", JsonObject())
            
            if (nodeId.isEmpty()) {
                sendError(message, 400, "nodeId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.getNodeAlerts(nodeId, filter)
                .onSuccess { alerts ->
                    sendSuccess(message, alerts)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 创建告警规则
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_ALERT_RULE_CREATE) { message ->
            val ruleInfo = message.body().getJsonObject("ruleInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (ruleInfo.isEmpty) {
                sendError(message, 400, "ruleInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.createAlertRule(ruleInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 更新告警规则
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_ALERT_RULE_UPDATE) { message ->
            val ruleId = message.body().getString("ruleId", "")
            val ruleInfo = message.body().getJsonObject("ruleInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (ruleId.isEmpty()) {
                sendError(message, 400, "ruleId parameter is required")
                return@consumer
            }
            
            if (ruleInfo.isEmpty) {
                sendError(message, 400, "ruleInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.updateAlertRule(ruleId, ruleInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 删除告警规则
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_ALERT_RULE_DELETE) { message ->
            val ruleId = message.body().getString("ruleId", "")
            val userId = message.body().getString("userId", "")
            
            if (ruleId.isEmpty()) {
                sendError(message, 400, "ruleId parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.deleteAlertRule(ruleId, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 远程诊断节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_DIAGNOSE) { message ->
            val nodeId = message.body().getString("nodeId", "")
            val diagnosticType = message.body().getString("diagnosticType", "")
            val params = message.body().getJsonObject("params", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (nodeId.isEmpty()) {
                sendError(message, 400, "nodeId parameter is required")
                return@consumer
            }
            
            if (diagnosticType.isEmpty()) {
                sendError(message, 400, "diagnosticType parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.diagnoseNode(nodeId, diagnosticType, params, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 远程更新节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_NODE_UPDATE_SOFTWARE) { message ->
            val nodeId = message.body().getString("nodeId", "")
            val updateType = message.body().getString("updateType", "")
            val updateInfo = message.body().getJsonObject("updateInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (nodeId.isEmpty()) {
                sendError(message, 400, "nodeId parameter is required")
                return@consumer
            }
            
            if (updateType.isEmpty()) {
                sendError(message, 400, "updateType parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.updateNodeSoftware(nodeId, updateType, updateInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 获取用户列表
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_USERS_GET) { message ->
            val filter = message.body()
            
            edgeControlCenter.getUsers(filter)
                .onSuccess { users ->
                    sendSuccess(message, users)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 创建用户
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_USER_CREATE) { message ->
            val userInfo = message.body().getJsonObject("userInfo", JsonObject())
            val creatorId = message.body().getString("creatorId", "")
            
            if (userInfo.isEmpty) {
                sendError(message, 400, "userInfo parameter is required")
                return@consumer
            }
            
            if (creatorId.isEmpty()) {
                sendError(message, 400, "creatorId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.createUser(userInfo, creatorId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 更新用户
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_USER_UPDATE) { message ->
            val userId = message.body().getString("userId", "")
            val userInfo = message.body().getJsonObject("userInfo", JsonObject())
            val operatorId = message.body().getString("operatorId", "")
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            if (userInfo.isEmpty) {
                sendError(message, 400, "userInfo parameter is required")
                return@consumer
            }
            
            if (operatorId.isEmpty()) {
                sendError(message, 400, "operatorId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.updateUser(userId, userInfo, operatorId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 删除用户
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_USER_DELETE) { message ->
            val userId = message.body().getString("userId", "")
            val operatorId = message.body().getString("operatorId", "")
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            if (operatorId.isEmpty()) {
                sendError(message, 400, "operatorId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.deleteUser(userId, operatorId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 获取角色列表
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_ROLES_GET) { message ->
            edgeControlCenter.getRoles()
                .onSuccess { roles ->
                    sendSuccess(message, roles)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 创建角色
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_ROLE_CREATE) { message ->
            val roleInfo = message.body().getJsonObject("roleInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (roleInfo.isEmpty) {
                sendError(message, 400, "roleInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.createRole(roleInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 更新角色
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_ROLE_UPDATE) { message ->
            val roleId = message.body().getString("roleId", "")
            val roleInfo = message.body().getJsonObject("roleInfo", JsonObject())
            val userId = message.body().getString("userId", "")
            
            if (roleId.isEmpty()) {
                sendError(message, 400, "roleId parameter is required")
                return@consumer
            }
            
            if (roleInfo.isEmpty) {
                sendError(message, 400, "roleInfo parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.updateRole(roleId, roleInfo, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 删除角色
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_ROLE_DELETE) { message ->
            val roleId = message.body().getString("roleId", "")
            val userId = message.body().getString("userId", "")
            
            if (roleId.isEmpty()) {
                sendError(message, 400, "roleId parameter is required")
                return@consumer
            }
            
            if (userId.isEmpty()) {
                sendError(message, 400, "userId parameter is required")
                return@consumer
            }
            
            edgeControlCenter.deleteRole(roleId, userId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
        
        // 获取审计日志
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EDGE_AUDIT_LOGS_GET) { message ->
            val filter = message.body()
            
            edgeControlCenter.getAuditLogs(filter)
                .onSuccess { logs ->
                    sendSuccess(message, logs)
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
        logger.info("启动边缘控制Verticle")
        
        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())
                    
                    // 获取边缘控制中心配置
                    val edgeControlConfig = config.getJsonObject("edgeControl", JsonObject())
                    
                    // 初始化边缘控制中心
                    edgeControlCenter = EdgeControlCenter.getInstance(vertx)
                    
                    edgeControlCenter.initialize(edgeControlConfig)
                        .onSuccess {
                            logger.info("边缘控制中心初始化成功")
                            
                            // 注册边缘控制中心组件状态
                            registerComponentStatus()
                            
                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("边缘控制中心初始化失败", cause)
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
     * 注册边缘控制中心组件状态
     */
    private fun registerComponentStatus() {
        val status = edgeControlCenter.getStatus()
        
        vertx.eventBus().send(EventBusAddresses.HEALTH_COMPONENT_STATUS, JsonObject()
            .put("component", "edgeControl")
            .put("status", true)
        )
    }
    
    /**
     * 停止Verticle
     */
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止边缘控制Verticle")
        
        if (::edgeControlCenter.isInitialized) {
            edgeControlCenter.close()
                .onSuccess {
                    logger.info("边缘控制中心关闭成功")
                    stopPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("边缘控制中心关闭失败", cause)
                    stopPromise.fail(cause)
                }
        } else {
            stopPromise.complete()
        }
    }
}
