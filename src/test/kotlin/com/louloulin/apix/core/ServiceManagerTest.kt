package com.louloulin.apix.core

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.models.Service
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExtendWith(VertxExtension::class)
class ServiceManagerTest {

    private lateinit var vertx: Vertx
    private lateinit var configManager: ConfigManager
    private lateinit var serviceManager: ServiceManager
    
    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
        
        // 模拟配置管理器
        configManager = mock(ConfigManager::class.java)
        `when`(configManager.getServicesConfig()).thenReturn(JsonArray()
            .add(JsonObject()
                .put("id", "test-service")
                .put("name", "Test Service")
                .put("url", "https://example.com")
                .put("host", "example.com")
                .put("port", 443)
                .put("protocol", "https")
                .put("path", "")
                .put("enabled", true)
            )
        )
        
        // 创建服务管理器
        serviceManager = ServiceManager(vertx, configManager)
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `should load services from configuration`() {
        // 验证服务被加载
        val service = serviceManager.getService("test-service")
        assertNotNull(service)
        assertEquals("test-service", service.id)
        assertEquals("https://example.com", service.url)
        assertEquals("example.com", service.host)
        assertEquals(443, service.port)
        assertEquals("https", service.protocol)
        assertEquals("", service.path)
        assertEquals(true, service.enabled)
    }
    
    @Test
    fun `should update service`() {
        // 创建新服务
        val newService = Service(
            id = "new-service",
            name = "New Service",
            url = "https://new-example.com",
            host = "new-example.com",
            port = 443,
            protocol = "https",
            path = "",
            enabled = true
        )
        
        // 更新服务
        serviceManager.updateService(newService)
        
        // 验证服务被添加
        val service = serviceManager.getService("new-service")
        assertNotNull(service)
        assertEquals("new-service", service.id)
        assertEquals("https://new-example.com", service.url)
    }
    
    @Test
    fun `should remove service`() {
        // 删除服务
        serviceManager.removeService("test-service")
        
        // 验证服务被删除
        val service = serviceManager.getService("test-service")
        assertNull(service)
    }
    
    @Test
    fun `should get all services`() {
        // 获取所有服务
        val services = serviceManager.getAllServices()
        
        // 验证服务在列表中
        assertEquals(1, services.size)
        assertEquals("test-service", services.first().id)
    }
}
