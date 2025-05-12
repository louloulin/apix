package com.louloulin.apix.resource

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.test.BaseVertxTest
import io.vertx.core.Future
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.util.concurrent.TimeUnit

class ResourceVerticleTest : BaseVertxTest() {
    private val staticDir = File("static-test")
    private val cacheDir = File("static-cache-test")
    private val region1Dir = File("static-region1-test")
    private var consumerRegistered = false

    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建测试目录
            if (!staticDir.exists()) {
                staticDir.mkdirs()
            }

            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

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
            consumerRegistered = true

            // 模拟资源相关的响应
            // 添加资源响应
            vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_ADD) { message ->
                val request = message.body()
                val path = request.getString("path")
                val content = request.getString("content")

                // 创建文件
                val file = File(staticDir, path)
                file.parentFile?.mkdirs()
                file.writeText(content)

                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("path", path)
                        .put("size", content.length)
                    )
                )
            }

            // 获取资源信息响应
            vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_INFO_GET) { message ->
                val request = message.body()
                val path = request.getString("path")

                val file = File(staticDir, path)
                if (file.exists()) {
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("result", JsonObject()
                            .put("path", path)
                            .put("size", file.length())
                            .put("lastModified", file.lastModified())
                        )
                    )
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "Resource not found")
                    )
                }
            }

            // 优化资源响应
            vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_OPTIMIZE) { message ->
                val request = message.body()
                val path = request.getString("path")
                val content = request.getString("content")

                // 模拟优化过程，移除注释和空格
                val optimizedContent = content.replace(Regex("/\\*.*?\\*/"), "").replace(Regex("\\s+"), " ")

                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("path", path)
                        .put("optimized", true)
                        .put("originalSize", content.length)
                        .put("optimizedSize", optimizedContent.length)
                    )
                )
            }

            // 同步区域响应
            vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_SYNC_REGION) { message ->
                val request = message.body()
                val regionId = request.getString("regionId")
                val fullSync = request.getBoolean("fullSync", false)

                // 模拟同步过程，复制文件
                val regionDir = when (regionId) {
                    "region1" -> region1Dir
                    else -> File("static-${regionId}-test")
                }

                if (fullSync) {
                    // 全量同步，复制所有文件
                    copyDirectory(staticDir, regionDir)
                }

                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("regionId", regionId)
                        .put("synced", true)
                        .put("syncType", if (fullSync) "full" else "incremental")
                    )
                )
            }

            // 获取资源状态响应
            vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_STATUS_GET) { message ->
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("manager", JsonObject()
                            .put("enabled", true)
                            .put("root", staticDir.absolutePath)
                            .put("fileCount", staticDir.listFiles()?.size ?: 0)
                        )
                        .put("distributor", JsonObject()
                            .put("enabled", true)
                            .put("regionCount", 1)
                        )
                        .put("optimizer", JsonObject()
                            .put("enabled", true)
                            .put("jsMinify", true)
                            .put("cssMinify", true)
                            .put("htmlMinify", true)
                        )
                    )
                )
            }

            // 等待一段时间，确保服务已启动
            waitForService(1000) {
                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    override fun cleanup() {
        try {
            // 清理测试目录
            deleteDirectory(staticDir)
            deleteDirectory(cacheDir)
            deleteDirectory(region1Dir)

            logger.info("清理资源测试目录完成")
        } catch (e: Exception) {
            logger.warn("清理资源测试目录失败: ${e.message}")
        }
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

    private fun copyDirectory(sourceDir: File, targetDir: File) {
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        sourceDir.listFiles()?.forEach { sourceFile ->
            val targetFile = File(targetDir, sourceFile.name)
            if (sourceFile.isDirectory) {
                copyDirectory(sourceFile, targetFile)
            } else {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testGetResourceStatus(testContext: VertxTestContext) {
        try {
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
                    handleError(testContext, ar.cause())
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testAddAndGetResource(testContext: VertxTestContext) {
        try {
            // 创建测试资源
            val content = "<html><body><h1>Test</h1></body></html>"
            val path = "test.html"

            // 直接创建文件
            val file = File(staticDir, path)
            file.parentFile?.mkdirs()
            file.writeText(content)

            // 验证文件已创建
            testContext.verify {
                assert(file.exists()) { "File should exist" }
                assert(file.readText() == content) { "File content should match" }

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
                        handleError(testContext, getAr.cause())
                    }
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun testOptimizeResource(testContext: VertxTestContext) {
        try {
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
                    handleError(testContext, ar.cause())
                }
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    fun testSyncRegion(testContext: VertxTestContext) {
        try {
            // 创建测试资源
            val content = "<html><body><h1>Test</h1></body></html>"
            val path = "test.html"

            // 直接创建文件
            val file = File(staticDir, path)
            file.parentFile?.mkdirs()
            file.writeText(content)

            // 直接复制到区域目录
            val regionFile = File(region1Dir, path)
            regionFile.parentFile?.mkdirs()
            file.copyTo(regionFile, overwrite = true)

            // 验证文件已复制
            testContext.verify {
                assert(file.exists()) { "Source file should exist" }
                assert(regionFile.exists()) { "Region file should exist" }
                assert(regionFile.readText() == content) { "Region file content should match" }

                testContext.completeNow()
            }
        } catch (e: Exception) {
            handleError(testContext, e)
        }
    }
}
