package com.louloulin.apix.plugins.security

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * CSRF 保护插件
 * 
 * 该插件用于防止跨站请求伪造（CSRF）攻击，支持以下功能：
 * - 支持多种 CSRF 令牌存储方式（Cookie、Session）
 * - 支持多种 CSRF 令牌验证方式（请求头、表单参数、查询参数）
 * - 支持排除特定路径和方法
 * - 支持自定义令牌名称和 Cookie 配置
 * 
 * 配置参数：
 * - tokenName: 令牌名称，默认为 "X-CSRF-TOKEN"
 * - headerName: 请求头名称，默认为 "X-CSRF-TOKEN"
 * - paramName: 参数名称，默认为 "_csrf"
 * - cookieName: Cookie 名称，默认为 "XSRF-TOKEN"
 * - cookieHttpOnly: Cookie 是否 HttpOnly，默认为 false
 * - cookieSecure: Cookie 是否 Secure，默认为 false
 * - cookiePath: Cookie 路径，默认为 "/"
 * - cookieMaxAge: Cookie 最大有效期（秒），默认为 86400（1天）
 * - cookieSameSite: Cookie SameSite 属性，可选值为 "Strict", "Lax", "None"，默认为 "Lax"
 * - storage: 令牌存储方式，可选值为 "cookie", "session"，默认为 "cookie"
 * - validationMode: 令牌验证方式，可选值为 "header", "form", "query", "all"，默认为 "all"
 * - excludedPaths: 排除的路径列表
 * - excludedMethods: 排除的方法列表，默认为 ["GET", "HEAD", "OPTIONS"]
 * - tokenLength: 令牌长度，默认为 32
 */
class CsrfProtectionPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(CsrfProtectionPlugin::class.java)
    override val type: String = "csrfProtection"
    
    // 令牌名称
    private val tokenName: String
    
    // 请求头名称
    private val headerName: String
    
    // 参数名称
    private val paramName: String
    
    // Cookie 名称
    private val cookieName: String
    
    // Cookie 配置
    private val cookieHttpOnly: Boolean
    private val cookieSecure: Boolean
    private val cookiePath: String
    private val cookieMaxAge: Long
    private val cookieSameSite: String
    
    // 令牌存储方式
    private val storage: TokenStorage
    
    // 令牌验证方式
    private val validationMode: ValidationMode
    
    // 排除的路径
    private val excludedPaths: List<String>
    
    // 排除的方法
    private val excludedMethods: Set<HttpMethod>
    
    // 令牌长度
    private val tokenLength: Int
    
    // 安全随机数生成器
    private val secureRandom = SecureRandom()
    
    // 令牌缓存（用于 Session 存储方式）
    private val tokenCache = ConcurrentHashMap<String, String>()
    
    init {
        // 解析令牌名称
        tokenName = config.config.getString("tokenName", "X-CSRF-TOKEN")
        
        // 解析请求头名称
        headerName = config.config.getString("headerName", "X-CSRF-TOKEN")
        
        // 解析参数名称
        paramName = config.config.getString("paramName", "_csrf")
        
        // 解析 Cookie 名称
        cookieName = config.config.getString("cookieName", "XSRF-TOKEN")
        
        // 解析 Cookie 配置
        cookieHttpOnly = config.config.getBoolean("cookieHttpOnly", false)
        cookieSecure = config.config.getBoolean("cookieSecure", false)
        cookiePath = config.config.getString("cookiePath", "/")
        cookieMaxAge = config.config.getLong("cookieMaxAge", 86400)
        cookieSameSite = config.config.getString("cookieSameSite", "Lax")
        
        // 解析令牌存储方式
        val storageStr = config.config.getString("storage", "cookie")
        storage = when (storageStr.lowercase()) {
            "session" -> TokenStorage.SESSION
            else -> TokenStorage.COOKIE
        }
        
        // 解析令牌验证方式
        val validationModeStr = config.config.getString("validationMode", "all")
        validationMode = when (validationModeStr.lowercase()) {
            "header" -> ValidationMode.HEADER
            "form" -> ValidationMode.FORM
            "query" -> ValidationMode.QUERY
            else -> ValidationMode.ALL
        }
        
        // 解析排除的路径
        val excludedPathsArray = config.config.getJsonArray("excludedPaths", JsonArray())
        excludedPaths = excludedPathsArray.map { it.toString() }
        
        // 解析排除的方法
        val excludedMethodsArray = config.config.getJsonArray("excludedMethods", JsonArray()
            .add("GET")
            .add("HEAD")
            .add("OPTIONS")
        )
        excludedMethods = excludedMethodsArray.map { 
            try {
                HttpMethod.valueOf(it.toString().uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid HTTP method: {}", it)
                null
            }
        }.filterNotNull().toSet()
        
        // 解析令牌长度
        tokenLength = config.config.getInteger("tokenLength", 32)
        
        logger.info("Initialized CSRF protection plugin: storage={}, validationMode={}", storage, validationMode)
    }
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 检查是否排除当前请求
            if (isExcluded(context)) {
                // 排除当前请求，继续处理
                context.next()
                promise.complete()
                return promise.future()
            }
            
            // 获取或生成 CSRF 令牌
            val csrfToken = getOrCreateToken(context)
            
            // 检查是否需要验证令牌
            if (requiresValidation(context)) {
                // 验证令牌
                val valid = validateToken(context, csrfToken)
                
                if (!valid) {
                    // 令牌验证失败，返回 403 错误
                    context.response()
                        .setStatusCode(403)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", "CSRF token validation failed")
                            .encode()
                        )
                    promise.complete()
                    return promise.future()
                }
            }
            
            // 令牌验证成功或不需要验证，继续处理请求
            context.next()
            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing CSRF protection plugin", e)
            // 发生错误，返回 500 错误
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Internal server error")
                    .put("message", e.message)
                    .encode()
                )
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 检查是否排除当前请求
     */
    private fun isExcluded(context: RoutingContext): Boolean {
        val request = context.request()
        val method = request.method()
        val path = request.path()
        
        // 检查方法是否排除
        if (method in excludedMethods) {
            return true
        }
        
        // 检查路径是否排除
        for (excludedPath in excludedPaths) {
            if (path.startsWith(excludedPath)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * 检查是否需要验证令牌
     */
    private fun requiresValidation(context: RoutingContext): Boolean {
        val method = context.request().method()
        
        // 只验证非 GET、HEAD、OPTIONS 请求
        return method !in setOf(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS)
    }
    
    /**
     * 获取或生成 CSRF 令牌
     */
    private fun getOrCreateToken(context: RoutingContext): String {
        return when (storage) {
            TokenStorage.COOKIE -> getOrCreateCookieToken(context)
            TokenStorage.SESSION -> getOrCreateSessionToken(context)
        }
    }
    
    /**
     * 获取或生成 Cookie 令牌
     */
    private fun getOrCreateCookieToken(context: RoutingContext): String {
        // 尝试从 Cookie 获取令牌
        val cookie = context.request().getCookie(cookieName)
        val token = cookie?.value
        
        if (token != null) {
            return token
        }
        
        // 生成新令牌
        val newToken = generateToken()
        
        // 设置 Cookie
        context.response()
            .putHeader("Set-Cookie", buildCookieHeader(cookieName, newToken))
        
        return newToken
    }
    
    /**
     * 获取或生成 Session 令牌
     */
    private fun getOrCreateSessionToken(context: RoutingContext): String {
        // 获取会话 ID
        val sessionId = context.session()?.id() ?: context.request().remoteAddress().toString()
        
        // 尝试从缓存获取令牌
        val token = tokenCache[sessionId]
        
        if (token != null) {
            return token
        }
        
        // 生成新令牌
        val newToken = generateToken()
        
        // 存储令牌
        tokenCache[sessionId] = newToken
        
        // 设置响应头
        context.response()
            .putHeader(tokenName, newToken)
        
        return newToken
    }
    
    /**
     * 验证令牌
     */
    private fun validateToken(context: RoutingContext, expectedToken: String): Boolean {
        // 根据验证方式获取令牌
        val actualToken = when (validationMode) {
            ValidationMode.HEADER -> getTokenFromHeader(context)
            ValidationMode.FORM -> getTokenFromForm(context)
            ValidationMode.QUERY -> getTokenFromQuery(context)
            ValidationMode.ALL -> getTokenFromAny(context)
        }
        
        return actualToken == expectedToken
    }
    
    /**
     * 从请求头获取令牌
     */
    private fun getTokenFromHeader(context: RoutingContext): String? {
        return context.request().getHeader(headerName)
    }
    
    /**
     * 从表单参数获取令牌
     */
    private fun getTokenFromForm(context: RoutingContext): String? {
        return context.request().getFormAttribute(paramName)
    }
    
    /**
     * 从查询参数获取令牌
     */
    private fun getTokenFromQuery(context: RoutingContext): String? {
        return context.request().getParam(paramName)
    }
    
    /**
     * 从任意位置获取令牌
     */
    private fun getTokenFromAny(context: RoutingContext): String? {
        return getTokenFromHeader(context)
            ?: getTokenFromForm(context)
            ?: getTokenFromQuery(context)
    }
    
    /**
     * 生成令牌
     */
    private fun generateToken(): String {
        val bytes = ByteArray(tokenLength)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
    
    /**
     * 构建 Cookie 头
     */
    private fun buildCookieHeader(name: String, value: String): String {
        val cookieBuilder = StringBuilder()
        
        // 添加名称和值
        cookieBuilder.append("$name=$value")
        
        // 添加路径
        cookieBuilder.append("; Path=$cookiePath")
        
        // 添加 Max-Age
        cookieBuilder.append("; Max-Age=$cookieMaxAge")
        
        // 添加 HttpOnly
        if (cookieHttpOnly) {
            cookieBuilder.append("; HttpOnly")
        }
        
        // 添加 Secure
        if (cookieSecure) {
            cookieBuilder.append("; Secure")
        }
        
        // 添加 SameSite
        cookieBuilder.append("; SameSite=$cookieSameSite")
        
        return cookieBuilder.toString()
    }
    
    override fun shutdown() {
        // 清空令牌缓存
        tokenCache.clear()
    }
    
    /**
     * 令牌存储方式枚举
     */
    enum class TokenStorage {
        COOKIE,  // Cookie 存储
        SESSION  // Session 存储
    }
    
    /**
     * 令牌验证方式枚举
     */
    enum class ValidationMode {
        HEADER,  // 请求头验证
        FORM,    // 表单参数验证
        QUERY,   // 查询参数验证
        ALL      // 所有方式验证
    }
    
    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return CsrfProtectionPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
