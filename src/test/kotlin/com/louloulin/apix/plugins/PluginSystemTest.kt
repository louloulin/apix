package com.louloulin.apix.plugins

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.PluginVerticle
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * 插件系统测试
 */
@ExtendWith(VertxExtension::class)
class PluginSystemTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 部署 PluginVerticle
        vertx.deployVerticle(PluginVerticle())
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun testCreateAndGetPlugin(testContext: VertxTestContext) {
        // 创建插件
        val pluginId = "test-plugin"
        val pluginType = "response-cache"
        val pluginConfig = JsonObject()
            .put("ttl_seconds", 300)
            .put("max_size", 1000)
        
        val createMessage = JsonObject()
            .put("id", pluginId)
            .put("type", pluginType)
            .put("config", pluginConfig)
            .put("enabled", true)
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_CREATE, createMessage) { createAr ->
            if (createAr.succeeded()) {
                val createResponse = createAr.result().body()
                testContext.verify {
                    assert(createResponse.getBoolean("success") == true) { "Expected success to be true" }
                }
                
                // 获取插件
                val getMessage = JsonObject().put("id", pluginId)
                
                vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, getMessage) { getAr ->
                    if (getAr.succeeded()) {
                        val getResponse = getAr.result().body()
                        testContext.verify {
                            assert(getResponse.getBoolean("success") == true) { "Expected success to be true" }
                            val result = getResponse.getJsonObject("result")
                            assert(result.getString("id") == pluginId) { "Expected id to be $pluginId" }
                            assert(result.getString("type") == pluginType) { "Expected type to be $pluginType" }
                            assert(result.getString("state") == PluginState.ENABLED.name) { "Expected state to be ENABLED" }
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(getAr.cause())
                    }
                }
            } else {
                testContext.failNow(createAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testUpdatePlugin(testContext: VertxTestContext) {
        // 创建插件
        val pluginId = "test-plugin-update"
        val pluginType = "response-cache"
        val pluginConfig = JsonObject()
            .put("ttl_seconds", 300)
            .put("max_size", 1000)
        
        val createMessage = JsonObject()
            .put("id", pluginId)
            .put("type", pluginType)
            .put("config", pluginConfig)
            .put("enabled", true)
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_CREATE, createMessage) { createAr ->
            if (createAr.succeeded()) {
                // 更新插件
                val newConfig = JsonObject()
                    .put("ttl_seconds", 600)
                    .put("max_size", 2000)
                
                val updateMessage = JsonObject()
                    .put("id", pluginId)
                    .put("config", newConfig)
                    .put("enabled", false)
                
                vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_UPDATE, updateMessage) { updateAr ->
                    if (updateAr.succeeded()) {
                        val updateResponse = updateAr.result().body()
                        testContext.verify {
                            assert(updateResponse.getBoolean("success") == true) { "Expected success to be true" }
                        }
                        
                        // 获取更新后的插件
                        val getMessage = JsonObject().put("id", pluginId)
                        
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, getMessage) { getAr ->
                            if (getAr.succeeded()) {
                                val getResponse = getAr.result().body()
                                testContext.verify {
                                    assert(getResponse.getBoolean("success") == true) { "Expected success to be true" }
                                    val result = getResponse.getJsonObject("result")
                                    assert(result.getString("id") == pluginId) { "Expected id to be $pluginId" }
                                    assert(result.getString("type") == pluginType) { "Expected type to be $pluginType" }
                                    assert(result.getString("state") == PluginState.DISABLED.name) { "Expected state to be DISABLED" }
                                    val config = result.getJsonObject("config")
                                    assert(config.getInteger("ttl_seconds") == 600) { "Expected ttl_seconds to be 600" }
                                    assert(config.getInteger("max_size") == 2000) { "Expected max_size to be 2000" }
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(getAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(updateAr.cause())
                    }
                }
            } else {
                testContext.failNow(createAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testEnableDisablePlugin(testContext: VertxTestContext) {
        // 创建插件
        val pluginId = "test-plugin-enable-disable"
        val pluginType = "response-cache"
        val pluginConfig = JsonObject()
            .put("ttl_seconds", 300)
            .put("max_size", 1000)
        
        val createMessage = JsonObject()
            .put("id", pluginId)
            .put("type", pluginType)
            .put("config", pluginConfig)
            .put("enabled", false)
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_CREATE, createMessage) { createAr ->
            if (createAr.succeeded()) {
                // 启用插件
                val enableMessage = JsonObject().put("id", pluginId)
                
                vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_ENABLE, enableMessage) { enableAr ->
                    if (enableAr.succeeded()) {
                        val enableResponse = enableAr.result().body()
                        testContext.verify {
                            assert(enableResponse.getBoolean("success") == true) { "Expected success to be true" }
                        }
                        
                        // 获取启用后的插件
                        val getMessage = JsonObject().put("id", pluginId)
                        
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, getMessage) { getAr ->
                            if (getAr.succeeded()) {
                                val getResponse = getAr.result().body()
                                testContext.verify {
                                    assert(getResponse.getBoolean("success") == true) { "Expected success to be true" }
                                    val result = getResponse.getJsonObject("result")
                                    assert(result.getString("state") == PluginState.ENABLED.name) { "Expected state to be ENABLED" }
                                }
                                
                                // 禁用插件
                                val disableMessage = JsonObject().put("id", pluginId)
                                
                                vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_DISABLE, disableMessage) { disableAr ->
                                    if (disableAr.succeeded()) {
                                        val disableResponse = disableAr.result().body()
                                        testContext.verify {
                                            assert(disableResponse.getBoolean("success") == true) { "Expected success to be true" }
                                        }
                                        
                                        // 获取禁用后的插件
                                        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, getMessage) { getAr2 ->
                                            if (getAr2.succeeded()) {
                                                val getResponse2 = getAr2.result().body()
                                                testContext.verify {
                                                    assert(getResponse2.getBoolean("success") == true) { "Expected success to be true" }
                                                    val result2 = getResponse2.getJsonObject("result")
                                                    assert(result2.getString("state") == PluginState.DISABLED.name) { "Expected state to be DISABLED" }
                                                    testContext.completeNow()
                                                }
                                            } else {
                                                testContext.failNow(getAr2.cause())
                                            }
                                        }
                                    } else {
                                        testContext.failNow(disableAr.cause())
                                    }
                                }
                            } else {
                                testContext.failNow(getAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(enableAr.cause())
                    }
                }
            } else {
                testContext.failNow(createAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testDeletePlugin(testContext: VertxTestContext) {
        // 创建插件
        val pluginId = "test-plugin-delete"
        val pluginType = "response-cache"
        val pluginConfig = JsonObject()
            .put("ttl_seconds", 300)
            .put("max_size", 1000)
        
        val createMessage = JsonObject()
            .put("id", pluginId)
            .put("type", pluginType)
            .put("config", pluginConfig)
            .put("enabled", true)
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_CREATE, createMessage) { createAr ->
            if (createAr.succeeded()) {
                // 删除插件
                val deleteMessage = JsonObject().put("id", pluginId)
                
                vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_DELETE, deleteMessage) { deleteAr ->
                    if (deleteAr.succeeded()) {
                        val deleteResponse = deleteAr.result().body()
                        testContext.verify {
                            assert(deleteResponse.getBoolean("success") == true) { "Expected success to be true" }
                        }
                        
                        // 尝试获取已删除的插件
                        val getMessage = JsonObject().put("id", pluginId)
                        
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_BY_ID, getMessage) { getAr ->
                            if (getAr.succeeded()) {
                                val getResponse = getAr.result().body()
                                testContext.verify {
                                    assert(getResponse.getBoolean("success") == false) { "Expected success to be false" }
                                    assert(getResponse.getInteger("errorCode") == 404) { "Expected error code to be 404" }
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(getAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(deleteAr.cause())
                    }
                }
            } else {
                testContext.failNow(createAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
    
    @Test
    fun testReloadPlugin(testContext: VertxTestContext) {
        // 创建插件
        val pluginId = "test-plugin-reload"
        val pluginType = "response-cache"
        val pluginConfig = JsonObject()
            .put("ttl_seconds", 300)
            .put("max_size", 1000)
        
        val createMessage = JsonObject()
            .put("id", pluginId)
            .put("type", pluginType)
            .put("config", pluginConfig)
            .put("enabled", true)
        
        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_CREATE, createMessage) { createAr ->
            if (createAr.succeeded()) {
                // 重新加载插件
                val reloadMessage = JsonObject().put("id", pluginId)
                
                vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_RELOAD, reloadMessage) { reloadAr ->
                    if (reloadAr.succeeded()) {
                        val reloadResponse = reloadAr.result().body()
                        testContext.verify {
                            assert(reloadResponse.getBoolean("success") == true) { "Expected success to be true" }
                            val result = reloadResponse.getJsonObject("result")
                            assert(result.getString("id") == pluginId) { "Expected id to be $pluginId" }
                            assert(result.getString("type") == pluginType) { "Expected type to be $pluginType" }
                            assert(result.getString("state") == PluginState.ENABLED.name) { "Expected state to be ENABLED" }
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(reloadAr.cause())
                    }
                }
            } else {
                testContext.failNow(createAr.cause())
            }
        }
        
        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
