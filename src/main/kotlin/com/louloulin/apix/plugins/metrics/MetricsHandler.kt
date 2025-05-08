package com.louloulin.apix.plugins.metrics

import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * 插件指标处理器
 * 提供HTTP API来查看插件的性能指标
 */
class MetricsHandler(private val vertx: Vertx) : Handler<RoutingContext> {
    private val logger = LoggerFactory.getLogger(MetricsHandler::class.java)
    private val metrics = PluginMetrics.getInstance(vertx)

    /**
     * 处理HTTP请求
     */
    override fun handle(context: RoutingContext) {
        try {
            val path = context.request().path()
            val method = context.request().method()

            // 处理不同的路径和方法
            when {
                path.endsWith("/metrics") && method == HttpMethod.GET -> {
                    // 获取所有插件的指标
                    val allMetrics = metrics.getAllMetrics()
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(allMetrics.encode())
                }
                path.matches(Regex(".*/metrics/[^/]+")) && method == HttpMethod.GET -> {
                    // 获取特定插件的指标
                    val pluginId = path.substring(path.lastIndexOf("/") + 1)
                    val pluginMetrics = metrics.getMetrics(pluginId)
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(pluginMetrics.encode())
                }
                path.endsWith("/metrics") && method == HttpMethod.DELETE -> {
                    // 重置所有指标
                    metrics.resetMetrics()
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject().put("success", true).encode())
                }
                else -> {
                    // 不支持的路径或方法
                    context.response()
                        .setStatusCode(404)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", "Not found")
                            .put("message", "The requested resource was not found")
                            .encode())
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling metrics request", e)
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Internal server error")
                    .put("message", e.message)
                    .encode())
        }
    }

    /**
     * 注册路由
     */
    fun registerRoutes(router: Router) {
        // 获取所有插件的指标
        router.get("/api/plugins/metrics").handler(this)

        // 获取特定插件的指标
        router.get("/api/plugins/metrics/:pluginId").handler(this)

        // 重置所有指标
        router.delete("/api/plugins/metrics").handler(this)

        logger.info("Registered metrics routes")
    }
}
