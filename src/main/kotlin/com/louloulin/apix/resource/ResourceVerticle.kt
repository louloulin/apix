package com.louloulin.apix.resource

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.BaseVerticle
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.io.File

/**
 * 资源Verticle
 * 处理静态资源相关的操作，提供EventBus接口
 * 实现plan7.md中的3.3节"静态资源处理"功能
 */
class ResourceVerticle : BaseVerticle() {
    // 资源管理器
    private lateinit var resourceManager: ResourceManager

    // 资源分发器
    private lateinit var resourceDistributor: ResourceDistributor

    // 资源优化器
    private lateinit var resourceOptimizer: ResourceOptimizer

    /**
     * 注册EventBus处理器
     */
    override fun registerEventBusHandlers() {
        // 获取资源状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_STATUS_GET) { message ->
            val status = JsonObject()
                .put("manager", resourceManager.getStatus())
                .put("distributor", resourceDistributor.getStatus())
                .put("optimizer", resourceOptimizer.getStatus())

            sendSuccess(message, status)
        }

        // 获取资源信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_INFO_GET) { message ->
            val path = message.body().getString("path", "")

            if (path.isEmpty()) {
                sendError(message, 400, "path parameter is required")
                return@consumer
            }

            val resourceInfo = resourceManager.getResourceInfo(path)

            if (resourceInfo == null) {
                sendError(message, 404, "Resource not found: $path")
                return@consumer
            }

            val dependencies = resourceManager.getResourceDependencies(path)

            val result = JsonObject()
                .put("path", resourceInfo.path)
                .put("size", resourceInfo.size)
                .put("lastModified", resourceInfo.lastModified)
                .put("fingerprint", resourceInfo.fingerprint)
                .put("fingerprintPath", resourceInfo.fingerprintPath)
                .put("gzipPath", resourceInfo.gzipPath)
                .put("dependencies", JsonArray(dependencies))

            sendSuccess(message, result)
        }

        // 获取所有资源
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_ALL_GET) { message ->
            val resources = resourceManager.getAllResources()

            val result = JsonArray()

            for ((path, info) in resources) {
                val dependencies = resourceManager.getResourceDependencies(path)

                result.add(JsonObject()
                    .put("path", info.path)
                    .put("size", info.size)
                    .put("lastModified", info.lastModified)
                    .put("fingerprint", info.fingerprint)
                    .put("fingerprintPath", info.fingerprintPath)
                    .put("gzipPath", info.gzipPath)
                    .put("dependencies", JsonArray(dependencies))
                )
            }

            sendSuccess(message, result)
        }

        // 添加资源
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_ADD) { message ->
            val path = message.body().getString("path", "")
            val content = message.body().getString("content", "")

            if (path.isEmpty()) {
                sendError(message, 400, "path parameter is required")
                return@consumer
            }

            if (content.isEmpty()) {
                sendError(message, 400, "content parameter is required")
                return@consumer
            }

            // 优化资源
            resourceOptimizer.optimizeResource(path, Buffer.buffer(content))
                .compose { optimized ->
                    // 添加资源
                    resourceManager.addResource(path, optimized)
                }
                .onSuccess { resourceInfo ->
                    val dependencies = resourceManager.getResourceDependencies(path)

                    val result = JsonObject()
                        .put("path", resourceInfo.path)
                        .put("size", resourceInfo.size)
                        .put("lastModified", resourceInfo.lastModified)
                        .put("fingerprint", resourceInfo.fingerprint)
                        .put("fingerprintPath", resourceInfo.fingerprintPath)
                        .put("gzipPath", resourceInfo.gzipPath)
                        .put("dependencies", JsonArray(dependencies))

                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 更新资源
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_UPDATE) { message ->
            val path = message.body().getString("path", "")
            val content = message.body().getString("content", "")

            if (path.isEmpty()) {
                sendError(message, 400, "path parameter is required")
                return@consumer
            }

            if (content.isEmpty()) {
                sendError(message, 400, "content parameter is required")
                return@consumer
            }

            // 优化资源
            resourceOptimizer.optimizeResource(path, Buffer.buffer(content))
                .compose { optimized ->
                    // 更新资源
                    resourceManager.updateResource(path, optimized)
                }
                .onSuccess { resourceInfo ->
                    val dependencies = resourceManager.getResourceDependencies(path)

                    val result = JsonObject()
                        .put("path", resourceInfo.path)
                        .put("size", resourceInfo.size)
                        .put("lastModified", resourceInfo.lastModified)
                        .put("fingerprint", resourceInfo.fingerprint)
                        .put("fingerprintPath", resourceInfo.fingerprintPath)
                        .put("gzipPath", resourceInfo.gzipPath)
                        .put("dependencies", JsonArray(dependencies))

                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 删除资源
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_DELETE) { message ->
            val path = message.body().getString("path", "")

            if (path.isEmpty()) {
                sendError(message, 400, "path parameter is required")
                return@consumer
            }

            resourceManager.deleteResource(path)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("deleted", true)
                        .put("path", path)
                    )
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 同步区域
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_SYNC_REGION) { message ->
            val regionId = message.body().getString("regionId", "")
            val fullSync = message.body().getBoolean("fullSync", false)

            if (regionId.isEmpty()) {
                sendError(message, 400, "regionId parameter is required")
                return@consumer
            }

            resourceDistributor.manualSyncRegion(regionId, fullSync)
                .onSuccess {
                    val status = resourceDistributor.getSyncStatus(regionId)

                    sendSuccess(message, JsonObject()
                        .put("synced", true)
                        .put("regionId", regionId)
                        .put("status", status)
                    )
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 同步所有区域
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_SYNC_ALL) { message ->
            val fullSync = message.body().getBoolean("fullSync", false)

            resourceDistributor.manualSyncAllRegions(fullSync)
                .onSuccess {
                    val status = resourceDistributor.getAllSyncStatus()

                    val statusJson = JsonObject()
                    for ((regionId, regionStatus) in status) {
                        statusJson.put(regionId, regionStatus)
                    }

                    sendSuccess(message, JsonObject()
                        .put("synced", true)
                        .put("status", statusJson)
                    )
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 预热资源
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_PREWARM) { message ->
            resourceDistributor.manualPrewarmResources()
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("prewarmed", true)
                    )
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 优化资源
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_OPTIMIZE) { message ->
            val path = message.body().getString("path", "")
            val content = message.body().getString("content", "")

            if (path.isEmpty()) {
                sendError(message, 400, "path parameter is required")
                return@consumer
            }

            if (content.isEmpty()) {
                sendError(message, 400, "content parameter is required")
                return@consumer
            }

            resourceOptimizer.optimizeResource(path, Buffer.buffer(content))
                .onSuccess { optimized ->
                    sendSuccess(message, JsonObject()
                        .put("optimized", true)
                        .put("path", path)
                        .put("originalSize", content.length)
                        .put("optimizedSize", optimized.length())
                        .put("content", optimized.toString())
                    )
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 压缩资源
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_COMPRESS) { message ->
            val content = message.body().getString("content", "")

            if (content.isEmpty()) {
                sendError(message, 400, "content parameter is required")
                return@consumer
            }

            resourceOptimizer.compressResource(Buffer.buffer(content))
                .onSuccess { compressed ->
                    sendSuccess(message, JsonObject()
                        .put("compressed", true)
                        .put("originalSize", content.length)
                        .put("compressedSize", compressed.length())
                        .put("content", compressed.toString("base64"))
                    )
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 生成懒加载脚本
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_LAZY_LOAD) { message ->
            val resources = message.body().getJsonArray("resources", JsonArray()).map { it.toString() }

            if (resources.isEmpty()) {
                sendError(message, 400, "resources parameter is required")
                return@consumer
            }

            resourceOptimizer.generateLazyLoadingScript(resources)
                .onSuccess { script ->
                    sendSuccess(message, JsonObject()
                        .put("generated", true)
                        .put("script", script)
                    )
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 更新资源配置
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.RESOURCE_CONFIG_UPDATE) { message ->
            val config = message.body()

            // 更新资源管理器配置
            val managerConfig = config.getJsonObject("manager", JsonObject())
            resourceManager.updateConfig(managerConfig)
                .compose {
                    // 更新资源分发器配置
                    val distributorConfig = config.getJsonObject("distributor", JsonObject())
                    resourceDistributor.updateConfig(distributorConfig)
                }
                .compose {
                    // 更新资源优化器配置
                    val optimizerConfig = config.getJsonObject("optimizer", JsonObject())
                    resourceOptimizer.updateConfig(optimizerConfig)
                }
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("updated", true)
                    )
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
        logger.info("启动资源Verticle")

        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // 获取资源配置
                    val resourceConfig = config.getJsonObject("resource", JsonObject())

                    // 初始化资源管理器
                    resourceManager = ResourceManager.getInstance(vertx)

                    // 初始化资源分发器
                    resourceDistributor = ResourceDistributor.getInstance(vertx)

                    // 初始化资源优化器
                    resourceOptimizer = ResourceOptimizer.getInstance(vertx)

                    // 初始化资源管理器
                    resourceManager.initialize(resourceConfig.getJsonObject("manager", JsonObject()))
                        .compose {
                            // 初始化资源分发器
                            resourceDistributor.initialize(resourceConfig.getJsonObject("distributor", JsonObject()))
                        }
                        .compose {
                            // 初始化资源优化器
                            resourceOptimizer.initialize(resourceConfig.getJsonObject("optimizer", JsonObject()))
                        }
                        .onSuccess {
                            logger.info("资源组件初始化成功")

                            // 注册资源组件状态
                            registerComponentStatus()

                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("资源组件初始化失败", cause)
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
     * 注册资源组件状态
     */
    private fun registerComponentStatus() {
        val managerStatus = resourceManager.getStatus()
        val distributorStatus = resourceDistributor.getStatus()
        val optimizerStatus = resourceOptimizer.getStatus()

        val enabled = managerStatus.getBoolean("enabled", false) ||
                distributorStatus.getBoolean("enabled", false) ||
                optimizerStatus.getBoolean("enabled", false)

        vertx.eventBus().send(EventBusAddresses.HEALTH_COMPONENT_STATUS, JsonObject()
            .put("component", "resource")
            .put("status", enabled)
        )
    }

    /**
     * 停止Verticle
     */
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止资源Verticle")

        // 关闭资源管理器
        if (::resourceManager.isInitialized) {
            resourceManager.close()
        }

        // 关闭资源分发器
        if (::resourceDistributor.isInitialized) {
            resourceDistributor.close()
        }

        // 关闭资源优化器
        if (::resourceOptimizer.isInitialized) {
            resourceOptimizer.close()
        }

        stopPromise.complete()
    }
}
