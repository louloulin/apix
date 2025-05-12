package com.louloulin.apix.admin

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import org.slf4j.LoggerFactory

/**
 * 用于测试的 AuthHandler 模拟实现
 */
class MockAuthHandler(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(MockAuthHandler::class.java)

    // 模拟用户数据库
    private val users = mutableMapOf(
        "admin" to Pair("admin123", "admin"),
        "user" to Pair("user123", "user")
    )

    /**
     * 设置路由
     */
    fun setupRoutes(router: Router) {
        router.route().handler(BodyHandler.create())

        // 登录路由
        router.post("/auth/login").handler { ctx ->
            val body = ctx.body().asJsonObject()
            val username = body.getString("username")
            val password = body.getString("password")

            // 验证用户凭据
            val userInfo = users[username]
            if (userInfo != null && userInfo.first == password) {
                // 返回成功响应
                ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", true)
                        .put("token", "test-token-" + username)
                        .put("user", JsonObject()
                            .put("username", username)
                            .put("role", userInfo.second))
                        .encode()
                    )
            } else {
                // 返回失败响应
                ctx.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("success", false)
                        .put("error", "Invalid username or password")
                        .encode()
                    )
            }
        }

        // 注册路由
        router.post("/auth/register").handler { ctx ->
            val body = ctx.body().asJsonObject()
            val username = body.getString("username")
            val password = body.getString("password")

            // 创建新用户
            users[username] = Pair(password, "user")

            // 返回成功响应
            ctx.response()
                .setStatusCode(201)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("message", "User registered successfully")
                    .encode()
                )
        }
    }
}
