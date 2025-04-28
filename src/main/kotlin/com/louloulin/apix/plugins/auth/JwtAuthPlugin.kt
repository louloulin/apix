package com.louloulin.apix.plugins.auth

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import io.vertx.ext.web.RoutingContext
import java.util.concurrent.ConcurrentHashMap

/**
 * JWT 认证插件
 *
 * 该插件用于验证 JWT 令牌，支持以下功能：
 * - 从请求头、查询参数或 Cookie 中获取 JWT 令牌
 * - 验证 JWT 令牌的签名
 * - 验证 JWT 令牌的过期时间
 * - 验证 JWT 令牌的发行者
 * - 验证 JWT 令牌的受众
 * - 验证 JWT 令牌的权限
 *
 * 配置参数：
 * - tokenLocation: 令牌位置，可选值为 "header", "query", "cookie"，默认为 "header"
 * - tokenName: 令牌名称，默认为 "Authorization"（header 模式下）或 "token"（query 和 cookie 模式下）
 * - tokenPrefix: 令牌前缀，默认为 "Bearer "（header 模式下）或 ""（query 和 cookie 模式下）
 * - publicKey: 公钥（用于验证 RS256 签名）
 * - privateKey: 私钥（用于生成 RS256 签名）
 * - secret: 密钥（用于验证和生成 HS256 签名）
 * - algorithm: 签名算法，可选值为 "HS256", "RS256"，默认为 "HS256"
 * - issuer: 发行者
 * - audience: 受众
 * - requiredClaims: 必需的声明列表
 * - requiredScopes: 必需的作用域列表
 * - ignoreExpiration: 是否忽略过期时间，默认为 false
 * - leeway: 过期时间的容差（秒），默认为 0
 */
class JwtAuthPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(JwtAuthPlugin::class.java)
    override val type: String = "jwtAuth"

    // JWT 认证提供者
    private val jwtAuth: JWTAuth

    // 令牌位置
    private val tokenLocation: TokenLocation

    // 令牌名称
    private val tokenName: String

    // 令牌前缀
    private val tokenPrefix: String

    // 必需的声明
    private val requiredClaims: Map<String, Any>

    // 必需的作用域
    private val requiredScopes: List<String>

    // 令牌缓存
    private val tokenCache = ConcurrentHashMap<String, JsonObject>()

    init {
        // 解析令牌位置
        val tokenLocationStr = config.config.getString("tokenLocation", "header")
        tokenLocation = when (tokenLocationStr.lowercase()) {
            "query" -> TokenLocation.QUERY
            "cookie" -> TokenLocation.COOKIE
            else -> TokenLocation.HEADER
        }

        // 解析令牌名称
        tokenName = config.config.getString("tokenName") ?: when (tokenLocation) {
            TokenLocation.HEADER -> "Authorization"
            TokenLocation.QUERY, TokenLocation.COOKIE -> "token"
        }

        // 解析令牌前缀
        tokenPrefix = config.config.getString("tokenPrefix") ?: when (tokenLocation) {
            TokenLocation.HEADER -> "Bearer "
            TokenLocation.QUERY, TokenLocation.COOKIE -> ""
        }

        // 解析必需的声明
        val requiredClaimsObj = config.config.getJsonObject("requiredClaims", JsonObject())
        requiredClaims = requiredClaimsObj.map.mapValues { it.value }

        // 解析必需的作用域
        val requiredScopesArray = config.config.getJsonArray("requiredScopes", JsonArray())
        requiredScopes = requiredScopesArray.map { it.toString() }

        // 创建 JWT 认证选项
        val jwtAuthOptions = JWTAuthOptions()

        // 设置签名算法
        val algorithm = config.config.getString("algorithm", "HS256")

        if (algorithm == "RS256") {
            // 使用 RS256 算法
            val publicKey = config.config.getString("publicKey")
            val privateKey = config.config.getString("privateKey")

            if (publicKey != null) {
                jwtAuthOptions.addPubSecKey(PubSecKeyOptions()
                    .setAlgorithm("RS256")
                    .setPublicKey(publicKey)
                )
            }

            if (privateKey != null) {
                jwtAuthOptions.addPubSecKey(PubSecKeyOptions()
                    .setAlgorithm("RS256")
                    .setSecretKey(privateKey)
                )
            }
        } else {
            // 使用 HS256 算法
            val secret = config.config.getString("secret")

            if (secret != null) {
                jwtAuthOptions.addPubSecKey(PubSecKeyOptions()
                    .setAlgorithm("HS256")
                    .setSymmetric(true)
                    .setPublicKey(secret)
                    .setSecretKey(secret)
                )
            }
        }

        // 设置发行者
        val issuer = config.config.getString("issuer")
        if (issuer != null) {
            jwtAuthOptions.setJWTOptions(JWTOptions().setIssuer(issuer))
        }

        // 设置受众
        val audience = config.config.getString("audience")
        if (audience != null) {
            jwtAuthOptions.setJWTOptions(JWTOptions().setAudience(listOf(audience)))
        }

        // 设置过期时间容差
        val leeway = config.config.getInteger("leeway", 0)
        if (leeway > 0) {
            jwtAuthOptions.setJWTOptions(JWTOptions().setLeeway(leeway))
        }

        // 设置是否忽略过期时间
        val ignoreExpiration = config.config.getBoolean("ignoreExpiration", false)
        jwtAuthOptions.setJWTOptions(JWTOptions().setIgnoreExpiration(ignoreExpiration))

        // 创建 JWT 认证提供者
        jwtAuth = JWTAuth.create(vertx, jwtAuthOptions)

        logger.info("Initialized JWT auth plugin: location={}, name={}", tokenLocation, tokenName)
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 获取令牌
            val token = getToken(context)

            if (token == null) {
                // 令牌不存在，返回 401 错误
                context.response()
                    .setStatusCode(401)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Unauthorized")
                        .put("message", "Missing JWT token")
                        .encode()
                    )
                promise.complete()
                return promise.future()
            }

            // 验证令牌
            jwtAuth.authenticate(JsonObject().put("token", token)) { authResult ->
                if (authResult.succeeded()) {
                    // 令牌验证成功
                    val user = authResult.result()
                    val principal = user.principal()

                    // 验证必需的声明
                    val claimsValid = validateRequiredClaims(principal)
                    if (!claimsValid) {
                        // 缺少必需的声明，返回 403 错误
                        context.response()
                            .setStatusCode(403)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("error", "Forbidden")
                                .put("message", "Missing required claims")
                                .encode()
                            )
                        promise.complete()
                        return@authenticate
                    }

                    // 验证必需的作用域
                    val scopesValid = validateRequiredScopes(principal)
                    if (!scopesValid) {
                        // 缺少必需的作用域，返回 403 错误
                        context.response()
                            .setStatusCode(403)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("error", "Forbidden")
                                .put("message", "Missing required scopes")
                                .encode()
                            )
                        promise.complete()
                        return@authenticate
                    }

                    // 将用户信息添加到上下文中
                    context.setUser(user)

                    // 继续处理请求
                    context.next()
                    promise.complete()
                } else {
                    // 令牌验证失败，返回 401 错误
                    context.response()
                        .setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", "Unauthorized")
                            .put("message", authResult.cause().message)
                            .encode()
                        )
                    promise.complete()
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing JWT auth plugin", e)
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
     * 获取令牌
     */
    private fun getToken(context: RoutingContext): String? {
        return when (tokenLocation) {
            TokenLocation.HEADER -> {
                // 从请求头获取令牌
                val authHeader = context.request().getHeader(tokenName)
                if (authHeader != null && authHeader.startsWith(tokenPrefix)) {
                    authHeader.substring(tokenPrefix.length)
                } else {
                    null
                }
            }
            TokenLocation.QUERY -> {
                // 从查询参数获取令牌
                val token = context.request().getParam(tokenName)
                if (token != null && token.isNotEmpty()) {
                    token
                } else {
                    null
                }
            }
            TokenLocation.COOKIE -> {
                // 从 Cookie 获取令牌
                val cookie = context.request().getCookie(tokenName)
                if (cookie != null) {
                    cookie.value
                } else {
                    null
                }
            }
        }
    }

    /**
     * 验证必需的声明
     */
    private fun validateRequiredClaims(principal: JsonObject): Boolean {
        if (requiredClaims.isEmpty()) {
            return true
        }

        return requiredClaims.all { (key, value) ->
            principal.containsKey(key) && principal.getValue(key) == value
        }
    }

    /**
     * 验证必需的作用域
     */
    private fun validateRequiredScopes(principal: JsonObject): Boolean {
        if (requiredScopes.isEmpty()) {
            return true
        }

        // 获取令牌中的作用域
        val scopes = when {
            principal.containsKey("scope") -> {
                val scopeStr = principal.getString("scope")
                scopeStr.split(" ")
            }
            principal.containsKey("scopes") -> {
                when (val scopesValue = principal.getValue("scopes")) {
                    is JsonArray -> scopesValue.map { it.toString() }
                    is String -> scopesValue.split(" ")
                    else -> emptyList()
                }
            }
            else -> emptyList()
        }

        return requiredScopes.all { it in scopes }
    }

    /**
     * 生成 JWT 令牌
     */
    fun generateToken(claims: JsonObject, options: JWTOptions = JWTOptions()): String {
        return jwtAuth.generateToken(claims, options)
    }

    override fun shutdown() {
        // 清空令牌缓存
        tokenCache.clear()
    }

    /**
     * 令牌位置枚举
     */
    enum class TokenLocation {
        HEADER,  // 请求头
        QUERY,   // 查询参数
        COOKIE   // Cookie
    }

    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return JwtAuthPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
