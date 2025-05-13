package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.ServiceManager
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject

/**
 * 服务管理 Verticle，负责处理服务相关的请求。
 */
class ServiceVerticle : BaseVerticle() {
    private lateinit var serviceManager: ServiceManager
    private lateinit var configManager: ConfigManager

    override fun registerEventBusHandlers() {
        // 服务管理相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SERVICE_GET_ALL, this::handleGetAllServices)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SERVICE_GET_BY_ID, this::handleGetServiceById)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SERVICE_CREATE, this::handleCreateService)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SERVICE_UPDATE, this::handleUpdateService)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SERVICE_DELETE, this::handleDeleteService)
    }

    override fun onStart(startPromise: Promise<Void>) {
        // 初始化配置管理器
        configManager = ConfigManager(vertx)
        
        // 初始化服务管理器
        serviceManager = ServiceManager(vertx, configManager)
        
        logger.info("ServiceVerticle started successfully")
        startPromise.complete()
    }

    /**
     * 处理获取所有服务请求
     */
    private fun handleGetAllServices(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val services = serviceManager.getAllServices()
            val servicesJson = JsonObject()
            
            services.forEach { service ->
                servicesJson.put(service.id, service.toJson())
            }
            
            val result = JsonObject()
                .put("services", servicesJson)
            
            sendSuccess(message, result)
        } catch (e: Exception) {
            logger.error("Error handling get all services request", e)
            sendError(message, e)
        }
    }

    /**
     * 处理获取服务请求
     */
    private fun handleGetServiceById(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val serviceId = message.body().getString("serviceId")
        if (serviceId == null) {
            sendError(message, 400, "Service ID is required")
            return
        }

        try {
            val service = serviceManager.getService(serviceId)
            if (service == null) {
                sendError(message, 404, "Service not found")
                return
            }

            val result = JsonObject()
                .put("service", service.toJson())
            
            sendSuccess(message, result)
        } catch (e: Exception) {
            logger.error("Error handling get service by id request", e)
            sendError(message, e)
        }
    }

    /**
     * 处理创建服务请求
     */
    private fun handleCreateService(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val serviceJson = message.body().getJsonObject("service")
        if (serviceJson == null) {
            sendError(message, 400, "Service configuration is required")
            return
        }

        try {
            val service = serviceManager.createService(serviceJson)
            
            val result = JsonObject()
                .put("service", service.toJson())
            
            sendSuccess(message, result, 201)
        } catch (e: Exception) {
            logger.error("Error handling create service request", e)
            sendError(message, e)
        }
    }

    /**
     * 处理更新服务请求
     */
    private fun handleUpdateService(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val serviceId = message.body().getString("serviceId")
        val serviceJson = message.body().getJsonObject("service")

        if (serviceId == null) {
            sendError(message, 400, "Service ID is required")
            return
        }

        if (serviceJson == null) {
            sendError(message, 400, "Service configuration is required")
            return
        }

        try {
            val service = serviceManager.updateService(serviceId, serviceJson)
            if (service == null) {
                sendError(message, 404, "Service not found")
                return
            }

            val result = JsonObject()
                .put("service", service.toJson())
            
            sendSuccess(message, result)
        } catch (e: Exception) {
            logger.error("Error handling update service request", e)
            sendError(message, e)
        }
    }

    /**
     * 处理删除服务请求
     */
    private fun handleDeleteService(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val serviceId = message.body().getString("serviceId")
        if (serviceId == null) {
            sendError(message, 400, "Service ID is required")
            return
        }

        try {
            val deleted = serviceManager.deleteService(serviceId)
            if (!deleted) {
                sendError(message, 404, "Service not found")
                return
            }

            sendSuccess(message, null, 204)
        } catch (e: Exception) {
            logger.error("Error handling delete service request", e)
            sendError(message, e)
        }
    }
}
