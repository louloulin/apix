package com.louloulin.apix.core.routing

import com.louloulin.apix.models.Route
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 动态路由管理器，支持运行时更新路由配置。
 * 这个类提供了动态添加、更新和删除路由的功能，无需重启服务。
 */
class DynamicRouteManager(private val vertx: Vertx, private val router: Router) {
    private val logger = LoggerFactory.getLogger(DynamicRouteManager::class.java)

    // 路由缓存
    private val routes = ConcurrentHashMap<String, Route>()

    // 路由处理器缓存
    private val routeHandlers = ConcurrentHashMap<String, io.vertx.ext.web.Route>()

    // 路由版本号
    private val routeVersion = AtomicLong(0)

    init {
        // 注册EventBus处理器，用于动态更新路由
        registerEventBusHandlers()

        logger.info("动态路由管理器初始化完成")
    }

    /**
     * 注册EventBus处理器
     */
    private fun registerEventBusHandlers() {
        // 添加或更新路由
        vertx.eventBus().consumer<JsonObject>("route.update") { message ->
            val routeConfig = message.body()

            try {
                val route = Route(
                    id = routeConfig.getString("id"),
                    name = routeConfig.getString("name", routeConfig.getString("id")),
                    path = routeConfig.getString("path"),
                    methods = listOf(routeConfig.getString("method", "GET")),
                    targetUrl = routeConfig.getString("targetUrl"),
                    plugins = routeConfig.getJsonArray("plugins")?.map { it.toString() } ?: emptyList(),
                    enabled = routeConfig.getBoolean("enabled", true)
                )

                updateRoute(route).onComplete { ar ->
                    if (ar.succeeded()) {
                        message.reply(JsonObject()
                            .put("success", true)
                            .put("message", "路由更新成功")
                            .put("routeId", route.id)
                            .put("version", routeVersion.get())
                        )
                    } else {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", ar.cause().message)
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("路由更新失败", e)
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "路由配置无效: ${e.message}")
                )
            }
        }

        // 删除路由
        vertx.eventBus().consumer<JsonObject>("route.delete") { message ->
            val routeId = message.body().getString("id")

            if (routeId == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "路由ID不能为空")
                )
                return@consumer
            }

            deleteRoute(routeId).onComplete { ar ->
                if (ar.succeeded()) {
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("message", "路由删除成功")
                        .put("routeId", routeId)
                        .put("version", routeVersion.get())
                    )
                } else {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", ar.cause().message)
                    )
                }
            }
        }

        // 获取所有路由
        vertx.eventBus().consumer<JsonObject>("route.list") { message ->
            val routeList = JsonArray()

            routes.values.forEach { route ->
                routeList.add(JsonObject()
                    .put("id", route.id)
                    .put("name", route.name)
                    .put("path", route.path)
                    .put("methods", route.methods)
                    .put("targetUrl", route.targetUrl)
                    .put("plugins", route.plugins)
                    .put("enabled", route.enabled)
                )
            }

            message.reply(JsonObject()
                .put("success", true)
                .put("routes", routeList)
                .put("count", routes.size)
                .put("version", routeVersion.get())
            )
        }

        // 获取单个路由
        vertx.eventBus().consumer<JsonObject>("route.get") { message ->
            val routeId = message.body().getString("id")

            if (routeId == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "路由ID不能为空")
                )
                return@consumer
            }

            val route = routes[routeId]
            if (route == null) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "路由不存在: $routeId")
                )
                return@consumer
            }

            message.reply(JsonObject()
                .put("success", true)
                .put("route", JsonObject()
                    .put("id", route.id)
                    .put("path", route.path)
                    .put("methods", route.methods)
                    .put("targetUrl", route.targetUrl)
                    .put("plugins", route.plugins)
                    .put("enabled", route.enabled)
                )
            )
        }
    }

    /**
     * 添加或更新路由
     *
     * @param route 路由配置
     * @return 包含操作结果的Future
     */
    fun updateRoute(route: Route): Future<Void> {
        val promise = Promise.promise<Void>()

        vertx.runOnContext { _ ->
            try {
                // 检查路由是否已存在
                val existingRoute = routes[route.id]
                if (existingRoute != null) {
                    // 如果路由已存在，先移除旧的路由处理器
                    val oldHandler = routeHandlers.remove(route.id)
                    oldHandler?.remove()
                }

                // 添加新的路由处理器
                if (route.enabled) {
                    // 为每个HTTP方法创建路由处理器
                    val routeHandler = router.route(route.path)

                    // 设置允许的HTTP方法
                    for (methodStr in route.methods) {
                        try {
                            val httpMethod = HttpMethod.valueOf(methodStr)
                            routeHandler.method(httpMethod)
                        } catch (e: Exception) {
                            logger.warn("无效的HTTP方法: {}", methodStr)
                        }
                    }

                    // 添加请求处理器
                    routeHandler.handler { ctx -> handleRequest(ctx, route) }

                    routeHandlers[route.id] = routeHandler
                }

                // 更新路由缓存
                routes[route.id] = route

                // 增加路由版本号
                routeVersion.incrementAndGet()

                logger.info("路由更新成功: ${route.id}, 路径: ${route.path}, 方法: ${route.methods}, 目标: ${route.targetUrl}")
                promise.complete()
            } catch (e: Exception) {
                logger.error("路由更新失败: ${route.id}", e)
                promise.fail(e)
            }
        }

        return promise.future()
    }

    /**
     * 删除路由
     *
     * @param routeId 路由ID
     * @return 包含操作结果的Future
     */
    fun deleteRoute(routeId: String): Future<Void> {
        val promise = Promise.promise<Void>()

        vertx.runOnContext { _ ->
            try {
                // 检查路由是否存在
                if (!routes.containsKey(routeId)) {
                    promise.fail("路由不存在: $routeId")
                    return@runOnContext
                }

                // 移除路由处理器
                val routeHandler = routeHandlers.remove(routeId)
                routeHandler?.remove()

                // 从缓存中移除路由
                routes.remove(routeId)

                // 增加路由版本号
                routeVersion.incrementAndGet()

                logger.info("路由删除成功: $routeId")
                promise.complete()
            } catch (e: Exception) {
                logger.error("路由删除失败: $routeId", e)
                promise.fail(e)
            }
        }

        return promise.future()
    }

    /**
     * 处理请求
     *
     * @param context 路由上下文
     * @param route 路由配置
     */
    private fun handleRequest(context: RoutingContext, route: Route) {
        // 在这里实现请求处理逻辑
        // 可以调用RouteManager中的转发逻辑

        // 这里只是一个简单的示例
        context.response()
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("message", "请求将被转发到: ${route.targetUrl}")
                .put("routeId", route.id)
                .put("path", route.path)
                .put("methods", route.methods)
                .encode()
            )
    }

    /**
     * 获取路由数量
     *
     * @return 路由数量
     */
    fun getRouteCount(): Int {
        return routes.size
    }

    /**
     * 获取当前路由版本号
     *
     * @return 路由版本号
     */
    fun getRouteVersion(): Long {
        return routeVersion.get()
    }

    /**
     * 批量加载路由配置
     *
     * @param routeConfigs 路由配置列表
     * @return 包含操作结果的Future
     */
    fun loadRoutes(routeConfigs: List<Route>): Future<Void> {
        val promise = Promise.promise<Void>()

        vertx.executeBlocking<Void>({ blockingPromise ->
            try {
                // 清空现有路由
                routeHandlers.values.forEach { it.remove() }
                routeHandlers.clear()
                routes.clear()

                // 加载新路由
                for (route in routeConfigs) {
                    if (route.enabled) {
                        // 为每个HTTP方法创建路由处理器
                        val routeHandler = router.route(route.path)

                        // 设置允许的HTTP方法
                        for (methodStr in route.methods) {
                            try {
                                val httpMethod = HttpMethod.valueOf(methodStr)
                                routeHandler.method(httpMethod)
                            } catch (e: Exception) {
                                logger.warn("无效的HTTP方法: {}", methodStr)
                            }
                        }

                        // 添加请求处理器
                        routeHandler.handler { ctx -> handleRequest(ctx, route) }

                        routeHandlers[route.id] = routeHandler
                        routes[route.id] = route
                    }
                }

                // 增加路由版本号
                routeVersion.incrementAndGet()

                logger.info("批量加载路由完成，共加载 ${routes.size} 个路由")
                blockingPromise.complete()
            } catch (e: Exception) {
                logger.error("批量加载路由失败", e)
                blockingPromise.fail(e)
            }
        }).onComplete { ar ->
            if (ar.succeeded()) {
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }
}
