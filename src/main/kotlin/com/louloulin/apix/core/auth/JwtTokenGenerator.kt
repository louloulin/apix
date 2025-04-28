package com.louloulin.apix.core.auth

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.PubSecKeyOptions
import io.vertx.ext.auth.jwt.JWTAuth
import io.vertx.ext.auth.jwt.JWTAuthOptions
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * JWT 令牌生成工具类
 */
class JwtTokenGenerator(
    private val vertx: Vertx,
    private val config: JsonObject
) {
    // JWT 认证提供者
    private val jwtAuth: JWTAuth

    init {
        // 创建 JWT 认证选项
        val jwtAuthOptions = JWTAuthOptions()

        // 设置签名算法
        val algorithm = config.getString("algorithm", "HS256")

        if (algorithm == "RS256") {
            // 使用 RS256 算法
            val publicKey = config.getString("publicKey")
            val privateKey = config.getString("privateKey")

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
            val secret = config.getString("secret")

            if (secret != null) {
                jwtAuthOptions.addPubSecKey(PubSecKeyOptions()
                    .setAlgorithm("HS256")
                    .setSymmetric(true)
                    .setPublicKey(secret)
                    .setSecretKey(secret)
                )
            }
        }

        // 创建 JWT 认证提供者
        jwtAuth = JWTAuth.create(vertx, jwtAuthOptions)
    }

    /**
     * 生成 JWT 令牌
     *
     * @param subject 主题（通常是用户 ID）
     * @param claims 自定义声明
     * @param expiresInMinutes 过期时间（分钟）
     * @return JWT 令牌
     */
    fun generateToken(subject: String, claims: JsonObject = JsonObject(), expiresInMinutes: Long = 60): String {
        // 创建声明
        val tokenClaims = JsonObject()
            .put("sub", subject)
            .mergeIn(claims)

        // 设置发行时间和过期时间
        val now = Instant.now()
        tokenClaims.put("iat", now.epochSecond)

        // 创建 JWT 选项
        val options = JWTOptions()

        // 设置发行者
        val issuer = config.getString("issuer")
        if (issuer != null) {
            options.setIssuer(issuer)
        }

        // 设置受众
        val audience = config.getString("audience")
        if (audience != null) {
            options.setAudience(listOf(audience))
        }

        // 设置过期时间
        if (expiresInMinutes > 0) {
            options.setExpiresInMinutes(expiresInMinutes.toInt())
        }

        // 生成令牌
        return jwtAuth.generateToken(tokenClaims, options)
    }

    /**
     * 生成刷新令牌
     *
     * @param subject 主题（通常是用户 ID）
     * @param expiresInDays 过期时间（天）
     * @return 刷新令牌
     */
    fun generateRefreshToken(subject: String, expiresInDays: Long = 30): String {
        // 创建声明
        val tokenClaims = JsonObject()
            .put("sub", subject)
            .put("type", "refresh")

        // 设置发行时间
        val now = Instant.now()
        tokenClaims.put("iat", now.epochSecond)

        // 创建 JWT 选项
        val options = JWTOptions()

        // 设置发行者
        val issuer = config.getString("issuer")
        if (issuer != null) {
            options.setIssuer(issuer)
        }

        // 设置过期时间
        if (expiresInDays > 0) {
            val expiresInMinutes = expiresInDays * 24 * 60
            options.setExpiresInMinutes(expiresInMinutes.toInt())
        }

        // 生成令牌
        return jwtAuth.generateToken(tokenClaims, options)
    }

    /**
     * 验证 JWT 令牌
     *
     * @param token JWT 令牌
     * @return 验证结果
     */
    fun validateToken(token: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        jwtAuth.authenticate(JsonObject().put("token", token)) { ar ->
            if (ar.succeeded()) {
                val user = ar.result()
                promise.complete(user.principal())
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    companion object {
        /**
         * 创建 JWT 令牌生成器
         */
        fun create(vertx: Vertx, config: JsonObject): JwtTokenGenerator {
            return JwtTokenGenerator(vertx, config)
        }
    }
}
