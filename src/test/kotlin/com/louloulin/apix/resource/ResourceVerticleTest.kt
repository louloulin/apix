package com.louloulin.apix.resource

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File

@ExtendWith(VertxExtension::class)
class ResourceVerticleTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建测试目录
        val staticDir = File("static-test")
        if (!staticDir.exists()) {
            staticDir.mkdirs()
        }
        
        val cacheDir = File("static-cache-test")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        val region1Dir = File("static-region1-test")
        if (!region1Dir.exists()) {
            region1Dir.mkdirs()
        }
        
        // 创建模拟配置响应
        val configResponse = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("resource", JsonObject()
                    .put("manager", JsonObject()
                        .put("enabled", true)
                        .put("root", "static-test")
                        .put("cache", "static-cache-test")
                        .put("process", JsonObject()
                            .put("fingerprint", true)
                            .put("compress", true)
                            .put("dependencies", true)
                        )
                    )
                    .put("distributor", JsonObject()
                        .put("enabled", true)
                        .put("regions", JsonArray()
                            .add(JsonObject()
                                .put("id", "region1")
                                .put("name", "Region 1")
                                .put("path", "static-region1-test")
                                .put("enabled", true)
                            )
                        )
                        .put("syncInterval", 300000)
                        .put("prewarm", JsonObject()
                            .put("enabled", true)
                            .put("interval", 3600000)
                            .put("patterns", JsonArray()
                                .add(".*\\.html")
                                .add(".*\\.css")
                                .add(".*\\.js")
                            )
                        )
                    )
                    .put("optimizer", JsonObject()
                        .put("enabled", true)
                        .put("js", JsonObject()
                            .put("minify", true)
                        )
                        .put("css", JsonObject()
                            .put("minify", true)
                        )
                        .put("html", JsonObject()
                            .put("minify", true)
                            .put("lazyLoad", true)
                        )
                        .put("image", JsonObject()
                            .put("optimize", true)
                        )
                        .put("lazyLoad", JsonObject()
                            .put("enabled", true)
                        )
                    )
                )
            )
        
        // 设置EventBus消息处理器来模拟ConfigVerticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(configResponse)
        }
        
        // 部署ResourceVerticle
        vertx.deployVerticle(ResourceVerticle::class.java.name, testContext.succeeding { _ ->
            testContext.completeNow()
        })
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close(testContext.succeeding { _ ->
            // 清理测试目录
            deleteDirectory(File("static-test"))
            deleteDirectory(File("static-cache-test"))
            deleteDirectory(File("static-region1-test"))
            
            testContext.completeNow()
        })
    }
    
    private fun deleteDirectory(directory: File) {
        if (directory.exists()) {
            directory.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    deleteDirectory(file)
                } else {
                    file.delete()
                }
            }
            directory.delete()
        }
    }
    
    @Test
    fun testGetResourceStatus(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取资源状态请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.RESOURCE_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getJsonObject("manager") != null) { "Manager status should be present" }
                    assert(result.getJsonObject("distributor") != null) { "Distributor status should be present" }
                    assert(result.getJsonObject("optimizer") != null) { "Optimizer status should be present" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testAddAndGetResource(vertx: Vertx, testContext: VertxTestContext) {
        // 创建测试资源
        val content = "<html><body><h1>Test</h1></body></html>"
        val path = "test.html"
        
        // 发送添加资源请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.RESOURCE_ADD, JsonObject()
            .put("path", path)
            .put("content", content)
        ) { addAr ->
            if (addAr.succeeded()) {
                val addResponse = addAr.result().body()
                
                // 验证添加响应
                testContext.verify {
                    assert(addResponse.getBoolean("success", false)) { "Add response should be successful" }
                    val addResult = addResponse.getJsonObject("result")
                    assert(addResult.getString("path") == path) { "Path should match" }
                    
                    // 发送获取资源信息请求
                    vertx.eventBus().request<JsonObject>(EventBusAddresses.RESOURCE_INFO_GET, JsonObject()
                        .put("path", path)
                    ) { getAr ->
                        if (getAr.succeeded()) {
                            val getResponse = getAr.result().body()
                            
                            // 验证获取响应
                            testContext.verify {
                                assert(getResponse.getBoolean("success", false)) { "Get response should be successful" }
                                val getResult = getResponse.getJsonObject("result")
                                assert(getResult.getString("path") == path) { "Path should match" }
                                
                                testContext.completeNow()
                            }
                        } else {
                            testContext.failNow(getAr.cause())
                        }
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }
    }
    
    @Test
    fun testOptimizeResource(vertx: Vertx, testContext: VertxTestContext) {
        // 创建测试资源
        val content = "function test() { /* This is a comment */ var x = 1; }"
        val path = "test.js"
        
        // 发送优化资源请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.RESOURCE_OPTIMIZE, JsonObject()
            .put("path", path)
            .put("content", content)
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getBoolean("optimized", false)) { "Resource should be optimized" }
                    assert(result.getString("path") == path) { "Path should match" }
                    assert(result.getInteger("originalSize") > result.getInteger("optimizedSize")) { "Optimized size should be smaller" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testSyncRegion(vertx: Vertx, testContext: VertxTestContext) {
        // 创建测试资源
        val content = "<html><body><h1>Test</h1></body></html>"
        val path = "test.html"
        
        // 发送添加资源请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.RESOURCE_ADD, JsonObject()
            .put("path", path)
            .put("content", content)
        ) { addAr ->
            if (addAr.succeeded()) {
                // 发送同步区域请求
                vertx.eventBus().request<JsonObject>(EventBusAddresses.RESOURCE_SYNC_REGION, JsonObject()
                    .put("regionId", "region1")
                    .put("fullSync", true)
                ) { syncAr ->
                    if (syncAr.succeeded()) {
                        val syncResponse = syncAr.result().body()
                        
                        // 验证响应
                        testContext.verify {
                            assert(syncResponse.getBoolean("success", false)) { "Sync response should be successful" }
                            val syncResult = syncResponse.getJsonObject("result")
                            assert(syncResult.getBoolean("synced", false)) { "Region should be synced" }
                            assert(syncResult.getString("regionId") == "region1") { "Region ID should match" }
                            
                            // 检查文件是否存在
                            val regionFile = File("static-region1-test/$path")
                            assert(regionFile.exists()) { "File should exist in region directory" }
                            
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(syncAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }
    }
}
