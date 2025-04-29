package com.louloulin.apix.core.verticle

import com.louloulin.apix.config.ConfigManager
import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.models.Route
import io.vertx.core.Promise
import io.vertx.core.http.HttpMethod
import io.vertx.core.http.HttpServer
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.ext.web.handler.CorsHandler
import io.vertx.ext.web.handler.LoggerHandler
import java.util.UUID

/**
 * 负责管理 API 接口的 Verticle
 */
class AdminVerticle : BaseVerticle() {
    private lateinit var httpServer: HttpServer
    private lateinit var router: Router
    private lateinit var configManager: ConfigManager

    override fun registerEventBusHandlers() {
        // 不需要注册 EventBus 处理器
    }

    override fun onStart(startPromise: Promise<Void>) {
        logger.info("Starting AdminVerticle...")

        // 初始化配置管理器
        configManager = ConfigManager(vertx)

        // 创建 HTTP 服务器与高并发优化设置
        val serverOptions = HttpServerOptions()
            // TCP 优化
            .setTcpNoDelay(true)              // 禁用 Nagle 算法以降低延迟
            .setTcpFastOpen(true)             // 启用 TCP Fast Open 以加快连接
            .setTcpQuickAck(true)             // 启用 TCP Quick ACK 以提高响应性

            // 套接字重用
            .setReusePort(true)               // 启用端口重用以提高负载分配
            .setReuseAddress(true)            // 启用地址重用以加快重启

            // 连接处理
            .setAcceptBacklog(10000)          // 增加接受队列大小
            .setIdleTimeout(0)                // 禁用空闲超时

            // 性能优化
            .setHandle100ContinueAutomatically(true) // 自动处理 100-Continue
            .setCompressionSupported(true)     // 启用压缩

        httpServer = vertx.createHttpServer(serverOptions)

        // 创建路由器
        router = Router.router(vertx)

        // 添加 CORS 处理器
        val corsHandler = CorsHandler.create("*")
            .allowedMethods(setOf(
                HttpMethod.GET,
                HttpMethod.POST,
                HttpMethod.PUT,
                HttpMethod.DELETE,
                HttpMethod.OPTIONS
            ))
            .allowedHeaders(setOf(
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "X-API-Key"
            ))

        router.route().handler(corsHandler)

        // 添加日志处理器
        router.route().handler(LoggerHandler.create())

        // 添加请求体处理器
        router.route().handler(BodyHandler.create())

        // 设置 API 路由
        setupRoutes()

        // 启动 HTTP 服务器
        val port = config().getInteger("admin.port", 8080)
        val host = config().getString("admin.host", "0.0.0.0")

        // 设置端口和主机
        httpServer.requestHandler(router).listen(port, host) { ar ->
            if (ar.succeeded()) {
                logger.info("AdminVerticle HTTP server started on {}:{}", host, port)
                startPromise.complete()
            } else {
                logger.error("Failed to start AdminVerticle HTTP server", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }

    /**
     * 设置 API 路由
     */
    private fun setupRoutes() {
        // 并发控制 API
        router.get("/api/concurrency/metrics").handler(this::getConcurrencyMetrics)
        router.get("/api/concurrency/metrics/:serviceId").handler(this::getConcurrencyMetrics)
        router.post("/api/concurrency/limits").handler(this::setConcurrencyLimit)
        router.post("/api/concurrency/reset").handler(this::resetConcurrencyMetrics)

        // 内存管理 API
        router.get("/api/memory/usage").handler(this::getMemoryUsage)
        router.post("/api/memory/gc").handler(this::triggerGC)
        router.post("/api/memory/cache/clear").handler(this::clearMemoryCache)

        // 对象池管理 API
        router.get("/api/memory/object-pools").handler(this::getObjectPoolStats)
        router.post("/api/memory/object-pools").handler(this::createObjectPool)
        router.delete("/api/memory/object-pools/:name").handler(this::removeObjectPool)

        // 压测用端点
        router.get("/api/hello").handler(this::helloWorld)
        router.get("/api/hello/:name").handler(this::helloName)
    }

    /**
     * 获取并发控制指标
     */
    private fun getConcurrencyMetrics(ctx: RoutingContext) {
        val serviceId = ctx.pathParam("serviceId")

        val request = JsonObject()
        if (serviceId != null) {
            request.put("serviceId", serviceId)
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONCURRENCY_GET_METRICS, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get concurrency metrics: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 设置并发限制
     */
    private fun setConcurrencyLimit(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Request body is required").encode())
            return
        }

        val serviceId = body.getString("serviceId")
        val limit = body.getInteger("limit")

        if (serviceId == null || limit == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Missing required parameters: serviceId and limit")
                    .encode()
                )
            return
        }

        val request = JsonObject()
            .put("serviceId", serviceId)
            .put("limit", limit)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONCURRENCY_SET_LIMIT, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to set concurrency limit: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 重置并发控制指标
     */
    private fun resetConcurrencyMetrics(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        val serviceId = if (body != null) body.getString("serviceId") else null

        val request = JsonObject()
        if (serviceId != null) {
            request.put("serviceId", serviceId)
        }

        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONCURRENCY_RESET_METRICS, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to reset concurrency metrics: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    override fun stop(stopPromise: Promise<Void>) {
        logger.info("Stopping AdminVerticle...")

        // 关闭 HTTP 服务器
        httpServer.close { ar ->
            if (ar.succeeded()) {
                logger.info("AdminVerticle HTTP server closed")
                stopPromise.complete()
            } else {
                logger.error("Failed to close AdminVerticle HTTP server", ar.cause())
                stopPromise.fail(ar.cause())
            }
        }
    }

    /**
     * 获取内存使用情况
     */
    private fun getMemoryUsage(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_USAGE_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get memory usage: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 触发垃圾回收
     */
    private fun triggerGC(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_GC_TRIGGER, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to trigger garbage collection: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 清理内存缓存
     */
    private fun clearMemoryCache(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.MEMORY_CACHE_CLEAR, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to clear memory cache: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }
    /**
     * 获取对象池统计信息
     */
    private fun getObjectPoolStats(ctx: RoutingContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_GET_STATS, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to get object pool stats: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 创建对象池
     */
    private fun createObjectPool(ctx: RoutingContext) {
        val body = ctx.body().asJsonObject()

        if (body == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Request body is required").encode())
            return
        }

        val name = body.getString("name")
        val initialSize = body.getInteger("initialSize", 10)
        val maxSize = body.getInteger("maxSize", 100)

        if (name == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Pool name is required")
                    .encode()
                )
            return
        }

        val request = JsonObject()
            .put("name", name)
            .put("initialSize", initialSize)
            .put("maxSize", maxSize)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_CREATE, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to create object pool: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * 移除对象池
     */
    private fun removeObjectPool(ctx: RoutingContext) {
        val poolName = ctx.pathParam("name")

        if (poolName == null) {
            ctx.response()
                .setStatusCode(400)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("error", "Pool name is required").encode())
            return
        }

        val request = JsonObject().put("name", poolName)

        vertx.eventBus().request<JsonObject>(EventBusAddresses.OBJECT_POOL_REMOVE, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                ctx.response()
                    .putHeader("Content-Type", "application/json")
                    .end(response.encode())
            } else {
                ctx.response()
                    .setStatusCode(500)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Failed to remove object pool: ${ar.cause().message}")
                        .encode()
                    )
            }
        }
    }

    /**
     * Hello World 端点
     */
    private fun helloWorld(ctx: RoutingContext) {
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("message", "Hello, World!")
                .put("timestamp", System.currentTimeMillis())
                .encode()
            )
    }

    /**
     * Hello Name 端点
     */
    private fun helloName(ctx: RoutingContext) {
        val name = ctx.pathParam("name") ?: "World"

        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("message", "Hello, $name!")
                .put("timestamp", System.currentTimeMillis())
                .encode()
            )
    }
}