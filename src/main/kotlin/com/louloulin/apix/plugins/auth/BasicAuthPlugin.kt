package com.louloulin.apix.plugins.auth

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.AsyncResult
import io.vertx.core.Handler
import io.vertx.ext.auth.User
import io.vertx.ext.auth.authentication.AuthenticationProvider
import io.vertx.ext.auth.htpasswd.HtpasswdAuth
import io.vertx.ext.auth.htpasswd.HtpasswdAuthOptions
import io.vertx.ext.web.RoutingContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64

/**
 * 基本认证插件
 *
 * 该插件用于实现 HTTP 基本认证，支持以下功能：
 * - 从 Authorization 头中获取用户名和密码
 * - 验证用户名和密码
 * - 支持 htpasswd 文件认证
 * - 支持内存中用户认证
 *
 * 配置参数：
 * - realm: 认证领域，默认为 "Restricted Area"
 * - htpasswdFile: htpasswd 文件路径
 * - users: 用户列表，每个用户包含 username 和 password
 * - allowPlainTextPassword: 是否允许明文密码，默认为 false
 */
class BasicAuthPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(BasicAuthPlugin::class.java)
    override val type: String = "basicAuth"

    // 认证领域
    private val realm: String

    // 认证提供者
    private val authProvider: AuthenticationProvider

    init {
        // 解析认证领域
        realm = config.config.getString("realm", "Restricted Area")

        // 创建认证提供者
        authProvider = createAuthProvider()

        logger.info("Initialized basic auth plugin: realm={}", realm)
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 获取 Authorization 头
            val authHeader = context.request().getHeader("Authorization")

            if (authHeader == null || !authHeader.startsWith("Basic ")) {
                // 缺少 Authorization 头或格式不正确，返回 401 错误
                sendUnauthorizedResponse(context)
                promise.complete()
                return promise.future()
            }

            // 解析 Authorization 头
            val base64Credentials = authHeader.substring("Basic ".length)
            val credentials = String(Base64.getDecoder().decode(base64Credentials))
            val parts = credentials.split(":", limit = 2)

            if (parts.size != 2) {
                // 格式不正确，返回 401 错误
                sendUnauthorizedResponse(context)
                promise.complete()
                return promise.future()
            }

            val username = parts[0]
            val password = parts[1]

            // 验证用户名和密码
            val authInfo = JsonObject()
                .put("username", username)
                .put("password", password)

            authProvider.authenticate(authInfo) { ar ->
                if (ar.succeeded()) {
                    // 认证成功
                    val user = ar.result()

                    // 将用户信息添加到上下文中
                    context.setUser(user)

                    // 继续处理请求
                    context.next()
                    promise.complete()
                } else {
                    // 认证失败，返回 401 错误
                    sendUnauthorizedResponse(context)
                    promise.complete()
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing basic auth plugin", e)
            // 发生错误，返回 500 错误
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Internal Server Error")
                    .put("message", e.message)
                    .encode()
                )
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 发送未授权响应
     */
    private fun sendUnauthorizedResponse(context: RoutingContext) {
        context.response()
            .setStatusCode(401)
            .putHeader("WWW-Authenticate", "Basic realm=\"$realm\"")
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("error", "Unauthorized")
                .put("message", "Authentication required")
                .encode()
            )
    }

    /**
     * 创建认证提供者
     */
    private fun createAuthProvider(): AuthenticationProvider {
        // 检查是否配置了 htpasswd 文件
        val htpasswdFile = config.config.getString("htpasswdFile")
        if (htpasswdFile != null) {
            // 使用 htpasswd 文件认证
            val htpasswdPath = Paths.get(htpasswdFile)
            if (Files.exists(htpasswdPath)) {
                val options = HtpasswdAuthOptions()
                    .setHtpasswdFile(htpasswdFile)

                // 是否允许明文密码
                val allowPlainTextPassword = config.config.getBoolean("allowPlainTextPassword", false)
                options.setPlainTextEnabled(allowPlainTextPassword)

                return HtpasswdAuth.create(vertx, options)
            } else {
                logger.warn("Htpasswd file not found: {}", htpasswdFile)
            }
        }

        // 使用内存中用户认证
        val users = config.config.getJsonArray("users", JsonArray())

        // 创建内存中用户认证提供者
        return object : AuthenticationProvider {
            override fun authenticate(credentials: JsonObject, resultHandler: Handler<AsyncResult<User>>) {
                val username = credentials.getString("username")
                val password = credentials.getString("password")

                if (username == null || password == null) {
                    resultHandler.handle(io.vertx.core.Future.failedFuture("Invalid credentials"))
                    return
                }

                // 查找用户
                val user = users.map { it as JsonObject }
                    .find { it.getString("username") == username }

                if (user != null && user.getString("password") == password) {
                    // 创建用户对象
                    val userObject = User.create(JsonObject()
                        .put("username", username)
                        .put("roles", user.getJsonArray("roles", JsonArray()))
                    )

                    resultHandler.handle(io.vertx.core.Future.succeededFuture(userObject))
                } else {
                    resultHandler.handle(io.vertx.core.Future.failedFuture("Invalid username or password"))
                }
            }
        }
    }

    override fun shutdown() {
        // 无需释放资源
    }

    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return BasicAuthPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
