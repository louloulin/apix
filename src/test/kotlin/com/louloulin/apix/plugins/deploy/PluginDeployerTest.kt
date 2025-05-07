package com.louloulin.apix.plugins.deploy

import com.louloulin.apix.plugins.PluginRegistry
import com.louloulin.apix.plugins.version.PluginVersion
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 插件部署器测试
 */
@RunWith(VertxUnitRunner::class)
class PluginDeployerTest {
    private lateinit var vertx: Vertx
    private lateinit var registry: PluginRegistry
    private lateinit var deployer: PluginDeployer

    // 测试插件目录
    private val testPluginDir = "test-plugins"

    @Before
    fun setUp(testContext: TestContext) {
        // 设置插件目录
        System.setProperty("plugin.dir", testPluginDir)

        // 创建测试插件目录
        val dir = File(testPluginDir)
        if (!dir.exists()) {
            dir.mkdirs()
        }

        vertx = Vertx.vertx()
        registry = PluginRegistry.getInstance(vertx)

        // 注册测试插件工厂
        registry.registerFactory("test", TestPluginFactory())

        // 创建插件部署器
        deployer = PluginDeployer.getInstance(vertx, registry)

        // 初始化插件部署器
        deployer.initialize().onComplete(testContext.asyncAssertSuccess())
    }

    @After
    fun tearDown(testContext: TestContext) {
        // 关闭插件部署器
        deployer.close().onComplete { _ ->
            // 关闭 Vertx
            vertx.close().onComplete { _ ->
                // 清理测试插件目录
                File(testPluginDir).listFiles()?.forEach { it.delete() }
                File(testPluginDir).delete()

                testContext.async().complete()
            }
        }
    }

    @Test
    fun testDeployPlugin(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试插件配置
        val pluginConfig = JsonObject()
            .put("id", "test-plugin")
            .put("type", "test")
            .put("version", "1.0.0")
            .put("priority", 100)
            .put("parallelExecution", true)

        // 保存配置文件
        val configPath = "$testPluginDir/test-plugin.json"
        vertx.fileSystem().writeFile(configPath, pluginConfig.toBuffer()).compose { _ ->
            // 部署插件
            deployer.deployPlugin(configPath)
        }.onComplete { ar ->
            if (ar.succeeded()) {
                // 验证插件已部署
                val deploymentId = ar.result()
                val deployment = deployer.getDeployment(deploymentId)

                testContext.assertNotNull(deployment)
                testContext.assertEquals("test-plugin", deployment?.pluginId)
                testContext.assertEquals(PluginVersion(1, 0, 0), deployment?.version)

                // 验证插件已加载
                val plugin = registry.getPlugin("test-plugin")
                testContext.assertNotNull(plugin)

                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }

    @Test
    fun testUndeployPlugin(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试插件配置
        val pluginConfig = JsonObject()
            .put("id", "test-plugin-undeploy")
            .put("type", "test")
            .put("version", "1.0.0")
            .put("priority", 100)
            .put("parallelExecution", true)

        // 保存配置文件
        val configPath = "$testPluginDir/test-plugin-undeploy.json"
        vertx.fileSystem().writeFile(configPath, pluginConfig.toBuffer()).compose { _ ->
            // 部署插件
            deployer.deployPlugin(configPath)
        }.compose { deploymentId ->
            // 验证插件已部署
            val deployment = deployer.getDeployment(deploymentId)
            testContext.assertNotNull(deployment)

            // 卸载插件
            deployer.undeployPlugin(configPath)
        }.onComplete { ar ->
            if (ar.succeeded()) {
                // 验证插件已卸载
                val deployment = deployer.getPluginDeployment("test-plugin-undeploy")
                testContext.assertNull(deployment)

                // 验证插件已从注册表中移除
                val plugin = registry.getPlugin("test-plugin-undeploy")
                testContext.assertNull(plugin)

                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }

    @Test
    fun testRedeployPlugin(testContext: TestContext) {
        val async = testContext.async()

        // 创建测试插件配置
        val pluginConfig = JsonObject()
            .put("id", "test-plugin-redeploy")
            .put("type", "test")
            .put("version", "1.0.0")
            .put("priority", 100)
            .put("parallelExecution", true)

        // 保存配置文件
        val configPath = "$testPluginDir/test-plugin-redeploy.json"
        vertx.fileSystem().writeFile(configPath, pluginConfig.toBuffer()).compose { _ ->
            // 部署插件
            deployer.deployPlugin(configPath)
        }.compose { deploymentId ->
            // 验证插件已部署
            val deployment = deployer.getDeployment(deploymentId)
            testContext.assertNotNull(deployment)
            testContext.assertEquals(PluginVersion(1, 0, 0), deployment?.version)

            // 更新配置
            val updatedConfig = pluginConfig.copy().put("version", "1.1.0")
            vertx.fileSystem().writeFile(configPath, updatedConfig.toBuffer())
        }.compose { _ ->
            // 重新部署插件
            deployer.deployPlugin(configPath)
        }.onComplete { ar ->
            if (ar.succeeded()) {
                // 验证插件已重新部署
                val deploymentId = ar.result()
                val deployment = deployer.getDeployment(deploymentId)

                testContext.assertNotNull(deployment)
                testContext.assertEquals("test-plugin-redeploy", deployment?.pluginId)
                testContext.assertEquals(PluginVersion(1, 1, 0), deployment?.version)

                // 验证插件版本已更新
                val versions = registry.getPluginVersions("test-plugin-redeploy")
                testContext.assertEquals(1, versions.size)
                testContext.assertEquals(PluginVersion(1, 1, 0), versions[0])

                async.complete()
            } else {
                testContext.fail(ar.cause())
            }
        }
    }

    /**
     * 测试插件工厂
     */
    inner class TestPluginFactory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: com.louloulin.apix.plugins.PluginConfig): com.louloulin.apix.plugins.Plugin {
            return TestPlugin(config.id, config.type, config)
        }
    }

    /**
     * 测试插件实现
     */
    inner class TestPlugin(
        override val id: String,
        override val type: String,
        override val config: com.louloulin.apix.plugins.PluginConfig
    ) : com.louloulin.apix.plugins.AbstractPlugin() {
        override fun onRequest(context: io.vertx.ext.web.RoutingContext): io.vertx.core.Future<Void> {
            return io.vertx.core.Future.succeededFuture()
        }
    }
}
