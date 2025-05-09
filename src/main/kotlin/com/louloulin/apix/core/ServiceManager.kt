package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.models.Service
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 管理网关中的服务。
 */
class ServiceManager(
    private val vertx: Vertx,
    private val configManager: ConfigManager
) {
    private val logger = LoggerFactory.getLogger(ServiceManager::class.java)
    private val services = ConcurrentHashMap<String, Service>()

    init {
        // 从配置加载服务
        loadServicesFromConfig()
    }

    /**
     * 从配置加载服务。
     */
    private fun loadServicesFromConfig() {
        logger.info("从配置加载服务...")

        try {
            val servicesConfig = configManager.getServicesConfig()

            servicesConfig.forEach { configObj ->
                val serviceConfig = configObj as JsonObject
                val serviceId = serviceConfig.getString("id")

                if (serviceId != null) {
                    try {
                        val service = Service.fromJson(serviceConfig)
                        services[serviceId] = service
                        logger.info("已加载服务: {}", serviceId)
                    } catch (e: Exception) {
                        logger.error("创建服务失败: {}", serviceId, e)
                    }
                } else {
                    logger.warn("无效的服务配置: {}", serviceConfig.encode())
                }
            }
        } catch (e: Exception) {
            logger.error("从配置加载服务时出错", e)
        }
    }

    /**
     * 通过ID获取服务。
     */
    fun getService(id: String): Service? {
        return services[id]
    }

    /**
     * 获取所有注册的服务。
     */
    fun getAllServices(): Collection<Service> {
        return services.values
    }

    /**
     * 获取服务列表（带分页）。
     */
    fun getServices(offset: Int = 0, limit: Int = 100): List<Service> {
        return services.values.toList()
            .drop(offset)
            .take(limit)
    }

    /**
     * 创建新服务。
     */
    fun createService(serviceJson: JsonObject): Service {
        val service = Service.fromJson(serviceJson)
        updateService(service)
        return service
    }

    /**
     * 更新服务。
     */
    fun updateService(id: String, serviceJson: JsonObject): Service? {
        val existingService = getService(id) ?: return null

        val updatedService = Service.fromJson(serviceJson.copy().put("id", id))
        updateService(updatedService)
        return updatedService
    }

    /**
     * 删除服务。
     */
    fun deleteService(id: String): Boolean {
        if (!services.containsKey(id)) {
            return false
        }

        removeService(id)
        return true
    }

    /**
     * 获取服务健康状态。
     */
    fun getServiceHealth(id: String): JsonObject {
        val service = getService(id) ?: return JsonObject()
            .put("success", false)
            .put("error", "Service not found: $id")

        // 在实际实现中，我们会检查服务的健康状态
        // 这里简单返回一个模拟的健康状态
        return JsonObject()
            .put("success", true)
            .put("service", service.id)
            .put("status", "healthy")
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 添加或更新服务。
     */
    fun updateService(service: Service) {
        services[service.id] = service
        saveServices()
    }

    /**
     * 删除服务。
     */
    fun removeService(id: String) {
        services.remove(id)
        saveServices()
    }

    /**
     * 保存服务到配置。
     */
    private fun saveServices() {
        val servicesArray = io.vertx.core.json.JsonArray()
        services.values.forEach { service ->
            servicesArray.add(service.toJson())
        }

        configManager.updateConfig(JsonObject().put("services", servicesArray))
    }
}
