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
