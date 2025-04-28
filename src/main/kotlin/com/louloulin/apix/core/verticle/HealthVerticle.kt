package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.http.HttpServer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.healthchecks.HealthCheckHandler
import io.vertx.ext.healthchecks.HealthChecks
import io.vertx.ext.healthchecks.Status
import io.vertx.ext.web.Router
import org.slf4j.LoggerFactory
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.OperatingSystemMXBean
import java.lang.management.RuntimeMXBean

/**
 * 负责健康检查和就绪探针的 Verticle
 */
class HealthVerticle : BaseVerticle() {
    private lateinit var configManager: ConfigManager
    private lateinit var httpServer: HttpServer
    private lateinit var router: Router
    private lateinit var healthChecks: HealthChecks

    // 健康检查的端口和主机
    private var healthPort = 8086
    private var healthHost = "0.0.0.0"

    // 组件状态
    private val componentStatus = mutableMapOf<String, Boolean>()

    override fun registerEventBusHandlers() {
        // 健康检查相关处理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HEALTH_CHECK, this::handleHealthCheck)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HEALTH_COMPONENT_STATUS, this::handleComponentStatus)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HEALTH_SYSTEM_INFO, this::handleSystemInfo)
    }

    override fun onStart(startPromise: Promise<Void>) {
        configManager = ConfigManager(vertx)

        // 从配置中获取健康检查的端口和主机
        val healthConfig = configManager.getConfig().getJsonObject("health", JsonObject())
        healthPort = healthConfig.getInteger("port", 8086)
        healthHost = healthConfig.getString("host", "0.0.0.0")

        // 创建健康检查处理器
        healthChecks = HealthChecks.create(vertx)

        // 注册健康检查
        registerHealthChecks()

        // 创建路由器
        router = Router.router(vertx)

        // 设置路由处理器
        setupRoutes()

        // 创建 HTTP 服务器
        httpServer = vertx.createHttpServer()
            .requestHandler(router)
            .listen(healthPort, healthHost) { ar ->
                if (ar.succeeded()) {
                    logger.info("HealthVerticle started on {}:{}", healthHost, healthPort)
                    startPromise.complete()
                } else {
                    logger.error("Failed to start HealthVerticle", ar.cause())
                    startPromise.fail(ar.cause())
                }
            }
    }

    /**
     * 设置路由处理器
     */
    private fun setupRoutes() {
        // 创建健康检查处理器
        val healthCheckHandler = HealthCheckHandler.createWithHealthChecks(healthChecks)

        // 添加健康检查路由
        router.get("/health").handler(healthCheckHandler)

        // 添加就绪探针路由
        router.get("/ready").handler { context ->
            val allReady = componentStatus.values.all { it }

            if (allReady) {
                context.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("status", "UP")
                        .put("checks", JsonArray(componentStatus.map { (name, status) ->
                            JsonObject()
                                .put("name", name)
                                .put("status", if (status) "UP" else "DOWN")
                        }))
                        .encode()
                    )
            } else {
                context.response()
                    .setStatusCode(503)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("status", "DOWN")
                        .put("checks", JsonArray(componentStatus.map { (name, status) ->
                            JsonObject()
                                .put("name", name)
                                .put("status", if (status) "UP" else "DOWN")
                        }))
                        .encode()
                    )
            }
        }

        // 添加存活探针路由
        router.get("/live").handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("status", "UP")
                    .encode()
                )
        }

        // 添加系统信息路由
        router.get("/info").handler { context ->
            val systemInfo = getSystemInfo()

            context.response()
                .putHeader("Content-Type", "application/json")
                .end(systemInfo.encode())
        }
    }

    /**
     * 注册健康检查
     */
    private fun registerHealthChecks() {
        // 注册内存健康检查
        healthChecks.register("memory") { promise ->
            val memoryMXBean = ManagementFactory.getMemoryMXBean()
            val heapMemoryUsage = memoryMXBean.heapMemoryUsage
            val usedMemory = heapMemoryUsage.used
            val maxMemory = heapMemoryUsage.max
            val memoryUsageRatio = usedMemory.toDouble() / maxMemory

            if (memoryUsageRatio < 0.9) {
                promise.complete(Status.OK(JsonObject()
                    .put("used", usedMemory)
                    .put("max", maxMemory)
                    .put("usageRatio", memoryUsageRatio)
                ))
            } else {
                promise.complete(Status.KO(JsonObject()
                    .put("used", usedMemory)
                    .put("max", maxMemory)
                    .put("usageRatio", memoryUsageRatio)
                ))
            }
        }

        // 注册 CPU 健康检查
        healthChecks.register("cpu") { promise ->
            val osMXBean = ManagementFactory.getOperatingSystemMXBean()
            val systemLoadAverage = osMXBean.systemLoadAverage
            val availableProcessors = osMXBean.availableProcessors
            val normalizedLoadAverage = systemLoadAverage / availableProcessors

            if (normalizedLoadAverage < 0.8) {
                promise.complete(Status.OK(JsonObject()
                    .put("systemLoadAverage", systemLoadAverage)
                    .put("availableProcessors", availableProcessors)
                    .put("normalizedLoadAverage", normalizedLoadAverage)
                ))
            } else {
                promise.complete(Status.KO(JsonObject()
                    .put("systemLoadAverage", systemLoadAverage)
                    .put("availableProcessors", availableProcessors)
                    .put("normalizedLoadAverage", normalizedLoadAverage)
                ))
            }
        }

        // 注册 Vert.x 事件循环健康检查
        healthChecks.register("eventLoop") { promise ->
            val blocked = vertx.isEventLoopBlocked()

            if (!blocked) {
                promise.complete(Status.OK())
            } else {
                promise.complete(Status.KO(JsonObject()
                    .put("blocked", true)
                ))
            }
        }

        // 注册组件健康检查
        healthChecks.register("components") { promise ->
            val allUp = componentStatus.values.all { it }

            if (allUp) {
                promise.complete(Status.OK(JsonObject()
                    .put("components", JsonObject(componentStatus.mapValues { it.value.toString() }))
                ))
            } else {
                promise.complete(Status.KO(JsonObject()
                    .put("components", JsonObject(componentStatus.mapValues { it.value.toString() }))
                ))
            }
        }
    }

    /**
     * 处理健康检查请求
     */
    private fun handleHealthCheck(message: io.vertx.core.eventbus.Message<JsonObject>) {
        healthChecks.checkStatus { ar ->
            if (ar.succeeded()) {
                val status = ar.result()
                message.reply(JsonObject()
                    .put("success", true)
                    .put("result", status.toJson())
                )
            } else {
                sendError(message, ar.cause())
            }
        }
    }

    /**
     * 处理组件状态请求
     */
    private fun handleComponentStatus(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val body = message.body()
        val component = body.getString("component")
        val status = body.getBoolean("status")

        if (component == null || status == null) {
            sendError(message, 400, "Component name and status are required")
            return
        }

        // 更新组件状态
        componentStatus[component] = status

        sendSuccess(message, JsonObject()
            .put("component", component)
            .put("status", status)
        )
    }

    /**
     * 处理系统信息请求
     */
    private fun handleSystemInfo(message: io.vertx.core.eventbus.Message<JsonObject>) {
        val systemInfo = getSystemInfo()
        sendSuccess(message, systemInfo)
    }

    /**
     * 获取系统信息
     */
    private fun getSystemInfo(): JsonObject {
        val runtimeMXBean = ManagementFactory.getRuntimeMXBean()
        val memoryMXBean = ManagementFactory.getMemoryMXBean()
        val osMXBean = ManagementFactory.getOperatingSystemMXBean()

        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage

        return JsonObject()
            .put("jvm", JsonObject()
                .put("version", System.getProperty("java.version"))
                .put("vendor", System.getProperty("java.vendor"))
                .put("uptime", runtimeMXBean.uptime)
                .put("startTime", runtimeMXBean.startTime)
                .put("memory", JsonObject()
                    .put("heap", JsonObject()
                        .put("init", heapMemoryUsage.init)
                        .put("used", heapMemoryUsage.used)
                        .put("committed", heapMemoryUsage.committed)
                        .put("max", heapMemoryUsage.max)
                    )
                    .put("nonHeap", JsonObject()
                        .put("init", nonHeapMemoryUsage.init)
                        .put("used", nonHeapMemoryUsage.used)
                        .put("committed", nonHeapMemoryUsage.committed)
                        .put("max", nonHeapMemoryUsage.max)
                    )
                )
            )
            .put("os", JsonObject()
                .put("name", System.getProperty("os.name"))
                .put("version", System.getProperty("os.version"))
                .put("arch", System.getProperty("os.arch"))
                .put("availableProcessors", osMXBean.availableProcessors)
                .put("systemLoadAverage", osMXBean.systemLoadAverage)
            )
            .put("vertx", JsonObject()
                .put("version", vertx.javaClass.`package`.implementationVersion)
                .put("eventLoopBlocked", vertx.isEventLoopBlocked())
            )
            .put("application", JsonObject()
                .put("name", "APIX")
                .put("version", "1.0.0")
                .put("components", JsonObject(componentStatus.mapValues { it.value.toString() }))
            )
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping HealthVerticle...")

        // 关闭 HTTP 服务器
        httpServer.close { ar ->
            if (ar.succeeded()) {
                logger.info("HealthVerticle HTTP server closed")
                stopPromise.complete()
            } else {
                logger.error("Failed to close HealthVerticle HTTP server", ar.cause())
                stopPromise.fail(ar.cause())
            }
        }
    }

    /**
     * 检查 Vert.x 事件循环是否被阻塞
     */
    private fun io.vertx.core.Vertx.isEventLoopBlocked(): Boolean {
        // 这是一个简化的实现，实际上需要更复杂的逻辑来检测事件循环阻塞
        // 在实际应用中，可以使用 Vert.x 的 BlockedThreadChecker 或自定义逻辑
        return false
    }
}
