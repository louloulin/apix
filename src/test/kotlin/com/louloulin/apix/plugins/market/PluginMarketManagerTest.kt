package com.louloulin.apix.plugins.market

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import io.vertx.core.Future
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
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@ExtendWith(VertxExtension::class)
class PluginMarketManagerTest {
    private val logger = LoggerFactory.getLogger(PluginMarketManagerTest::class.java)
    
    private lateinit var vertx: Vertx
    private lateinit var pluginMarketManager: PluginMarketManager
    
    // 测试插件目录
    private val testPluginsDir = "test-plugins"
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 设置插件目录
        System.setProperty("apix.plugins.dir", testPluginsDir)
        
        // 创建测试插件目录
        val testPluginsDirFile = File(testPluginsDir)
        if (testPluginsDirFile.exists()) {
            testPluginsDirFile.listFiles()?.forEach { it.delete() }
        } else {
            testPluginsDirFile.mkdirs()
        }
        
        // 创建插件市场管理器
        pluginMarketManager = PluginMarketManager(vertx)
        
        // 注册测试插件工厂
        pluginMarketManager.registerPluginFactory("test", TestPluginFactory())
        
        // 初始化插件市场管理器
        pluginMarketManager.initialize()
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        // 关闭插件市场管理器
        pluginMarketManager.close()
            .onSuccess {
                // 删除测试插件目录
                val testPluginsDirFile = File(testPluginsDir)
                if (testPluginsDirFile.exists()) {
                    testPluginsDirFile.listFiles()?.forEach { it.delete() }
                    testPluginsDirFile.delete()
                }
                
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testUploadAndDownloadPlugin(testContext: VertxTestContext) {
        // 创建测试插件
        val pluginId = UUID.randomUUID().toString()
        val pluginName = "Test Plugin"
        val pluginVersion = "1.0.0"
        val pluginType = "test"
        
        // 创建插件元数据
        val metadata = JsonObject()
            .put("id", pluginId)
            .put("name", pluginName)
            .put("version", pluginVersion)
            .put("type", pluginType)
            .put("description", "Test plugin description")
            .put("author", "Test Author")
            .put("license", "MIT")
            .put("tags", JsonArray().add("test").add("example"))
            .put("dependencies", JsonArray())
        
        // 创建插件文件
        val pluginFile = createTestPluginFile(pluginId, pluginVersion)
        
        // 上传插件
        pluginMarketManager.uploadPlugin(pluginFile, metadata)
            .compose { pluginInfo ->
                testContext.verify {
                    // 验证插件信息
                    assert(pluginInfo.id == pluginId)
                    assert(pluginInfo.name == pluginName)
                    assert(pluginInfo.version == pluginVersion)
                    assert(pluginInfo.type == pluginType)
                }
                
                // 下载插件
                pluginMarketManager.downloadPlugin(pluginId)
            }
            .onSuccess { buffer ->
                testContext.verify {
                    // 验证下载的插件文件
                    assert(buffer.length() > 0)
                }
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testInstallAndUninstallPlugin(testContext: VertxTestContext) {
        // 创建测试插件
        val pluginId = UUID.randomUUID().toString()
        val pluginName = "Test Plugin"
        val pluginVersion = "1.0.0"
        val pluginType = "test"
        
        // 创建插件元数据
        val metadata = JsonObject()
            .put("id", pluginId)
            .put("name", pluginName)
            .put("version", pluginVersion)
            .put("type", pluginType)
            .put("description", "Test plugin description")
            .put("author", "Test Author")
            .put("license", "MIT")
            .put("tags", JsonArray().add("test").add("example"))
            .put("dependencies", JsonArray())
        
        // 创建插件文件
        val pluginFile = createTestPluginFile(pluginId, pluginVersion)
        
        // 上传插件
        pluginMarketManager.uploadPlugin(pluginFile, metadata)
            .compose { pluginInfo ->
                // 安装插件
                pluginMarketManager.installPlugin(pluginId)
            }
            .compose { plugin ->
                testContext.verify {
                    // 验证插件
                    assert(plugin.id == pluginId)
                    assert(plugin.type == pluginType)
                }
                
                // 获取已安装的插件
                val installedPlugin = pluginMarketManager.getInstalledPlugin(pluginId)
                testContext.verify {
                    assert(installedPlugin != null)
                    assert(installedPlugin?.id == pluginId)
                    assert(installedPlugin?.type == pluginType)
                }
                
                // 卸载插件
                pluginMarketManager.uninstallPlugin(pluginId)
            }
            .onSuccess {
                testContext.verify {
                    // 验证插件已卸载
                    val installedPlugin = pluginMarketManager.getInstalledPlugin(pluginId)
                    assert(installedPlugin == null)
                }
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testSearchPlugins(testContext: VertxTestContext) {
        // 创建多个测试插件
        val plugin1Id = UUID.randomUUID().toString()
        val plugin1Metadata = JsonObject()
            .put("id", plugin1Id)
            .put("name", "Test Plugin 1")
            .put("version", "1.0.0")
            .put("type", "test")
            .put("description", "Test plugin 1 description")
            .put("author", "Test Author")
            .put("license", "MIT")
            .put("tags", JsonArray().add("test").add("example"))
            .put("dependencies", JsonArray())
        
        val plugin2Id = UUID.randomUUID().toString()
        val plugin2Metadata = JsonObject()
            .put("id", plugin2Id)
            .put("name", "Test Plugin 2")
            .put("version", "1.0.0")
            .put("type", "test")
            .put("description", "Another test plugin description")
            .put("author", "Another Author")
            .put("license", "Apache-2.0")
            .put("tags", JsonArray().add("test").add("advanced"))
            .put("dependencies", JsonArray())
        
        // 创建插件文件
        val plugin1File = createTestPluginFile(plugin1Id, "1.0.0")
        val plugin2File = createTestPluginFile(plugin2Id, "1.0.0")
        
        // 上传插件
        pluginMarketManager.uploadPlugin(plugin1File, plugin1Metadata)
            .compose {
                pluginMarketManager.uploadPlugin(plugin2File, plugin2Metadata)
            }
            .compose {
                // 搜索所有插件
                val allPlugins = pluginMarketManager.searchPlugins("")
                testContext.verify {
                    assert(allPlugins.size >= 2)
                }
                
                // 搜索包含"Another"的插件
                val anotherPlugins = pluginMarketManager.searchPlugins("Another")
                testContext.verify {
                    assert(anotherPlugins.size >= 1)
                    assert(anotherPlugins.any { it.id == plugin2Id })
                }
                
                // 搜索带有"advanced"标签的插件
                val advancedPlugins = pluginMarketManager.searchPlugins("", listOf("advanced"))
                testContext.verify {
                    assert(advancedPlugins.size >= 1)
                    assert(advancedPlugins.any { it.id == plugin2Id })
                }
                
                Future.succeededFuture<Void>()
            }
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testRatePlugin(testContext: VertxTestContext) {
        // 创建测试插件
        val pluginId = UUID.randomUUID().toString()
        val pluginMetadata = JsonObject()
            .put("id", pluginId)
            .put("name", "Test Plugin")
            .put("version", "1.0.0")
            .put("type", "test")
            .put("description", "Test plugin description")
            .put("author", "Test Author")
            .put("license", "MIT")
            .put("tags", JsonArray().add("test"))
            .put("dependencies", JsonArray())
        
        // 创建插件文件
        val pluginFile = createTestPluginFile(pluginId, "1.0.0")
        
        // 上传插件
        pluginMarketManager.uploadPlugin(pluginFile, pluginMetadata)
            .compose {
                // 评价插件
                pluginMarketManager.ratePlugin(pluginId, 4.5)
            }
            .compose { pluginInfo ->
                testContext.verify {
                    assert(pluginInfo.rating == 4.5)
                    assert(pluginInfo.ratingCount == 1)
                }
                
                // 再次评价插件
                pluginMarketManager.ratePlugin(pluginId, 3.5)
            }
            .onSuccess { pluginInfo ->
                testContext.verify {
                    // 验证评分是两次评分的平均值
                    assert(pluginInfo.rating == 4.0)
                    assert(pluginInfo.ratingCount == 2)
                }
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testDeletePlugin(testContext: VertxTestContext) {
        // 创建测试插件
        val pluginId = UUID.randomUUID().toString()
        val pluginMetadata = JsonObject()
            .put("id", pluginId)
            .put("name", "Test Plugin")
            .put("version", "1.0.0")
            .put("type", "test")
            .put("description", "Test plugin description")
            .put("author", "Test Author")
            .put("license", "MIT")
            .put("tags", JsonArray().add("test"))
            .put("dependencies", JsonArray())
        
        // 创建插件文件
        val pluginFile = createTestPluginFile(pluginId, "1.0.0")
        
        // 上传插件
        pluginMarketManager.uploadPlugin(pluginFile, pluginMetadata)
            .compose {
                // 验证插件存在
                val pluginInfo = pluginMarketManager.getPluginInfo(pluginId)
                testContext.verify {
                    assert(pluginInfo != null)
                }
                
                // 删除插件
                pluginMarketManager.deletePlugin(pluginId)
            }
            .onSuccess {
                // 验证插件已删除
                val pluginInfo = pluginMarketManager.getPluginInfo(pluginId)
                testContext.verify {
                    assert(pluginInfo == null)
                }
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    /**
     * 创建测试插件文件
     */
    private fun createTestPluginFile(pluginId: String, version: String): Buffer {
        // 创建临时目录
        val tempDir = Files.createTempDirectory("test-plugin").toFile()
        
        try {
            // 创建插件文件
            val pluginFile = File(tempDir, "plugin.json")
            pluginFile.writeText("""
                {
                    "id": "$pluginId",
                    "name": "Test Plugin",
                    "version": "$version",
                    "type": "test",
                    "description": "Test plugin description",
                    "author": "Test Author",
                    "license": "MIT"
                }
            """.trimIndent())
            
            // 创建插件代码文件
            val pluginCodeFile = File(tempDir, "plugin.js")
            pluginCodeFile.writeText("""
                function initialize() {
                    console.log("Plugin initialized");
                }
                
                function execute(context) {
                    console.log("Plugin executed");
                    return context;
                }
                
                module.exports = {
                    initialize,
                    execute
                };
            """.trimIndent())
            
            // 创建ZIP文件
            val zipFile = File.createTempFile("test-plugin", ".zip")
            val zipOutputStream = ZipOutputStream(zipFile.outputStream())
            
            // 添加文件到ZIP
            addFileToZip(zipOutputStream, pluginFile, "plugin.json")
            addFileToZip(zipOutputStream, pluginCodeFile, "plugin.js")
            
            // 关闭ZIP输出流
            zipOutputStream.close()
            
            // 读取ZIP文件
            val buffer = Buffer.buffer(Files.readAllBytes(zipFile.toPath()))
            
            // 删除临时文件
            zipFile.delete()
            
            return buffer
        } finally {
            // 删除临时目录
            tempDir.listFiles()?.forEach { it.delete() }
            tempDir.delete()
        }
    }
    
    /**
     * 添加文件到ZIP
     */
    private fun addFileToZip(zipOutputStream: ZipOutputStream, file: File, entryName: String) {
        val entry = ZipEntry(entryName)
        zipOutputStream.putNextEntry(entry)
        Files.copy(file.toPath(), zipOutputStream)
        zipOutputStream.closeEntry()
    }
    
    /**
     * 测试插件工厂
     */
    class TestPluginFactory : PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return TestPlugin(config.id, "test", config)
        }
    }
    
    /**
     * 测试插件
     */
    class TestPlugin(
        override val id: String,
        override val type: String,
        override val config: PluginConfig
    ) : Plugin {
        private val logger = LoggerFactory.getLogger(TestPlugin::class.java)
        
        override fun initialize(vertx: io.vertx.core.Vertx): Future<Void> {
            logger.info("Test plugin initialized: {}", id)
            return Future.succeededFuture()
        }
        
        override fun execute(context: io.vertx.ext.web.RoutingContext): Future<Void> {
            logger.info("Test plugin executed: {}", id)
            return Future.succeededFuture()
        }
        
        override fun shutdown(force: Boolean): Future<Void>? {
            logger.info("Test plugin shutdown: {}", id)
            return Future.succeededFuture()
        }
    }
}
