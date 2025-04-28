package com.louloulin.apix.plugins.security

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.time.Instant
import java.util.Base64
import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 请求签名验证插件
 *
 * 该插件用于验证请求签名，支持以下功能：
 * - 支持多种签名算法（HMAC-SHA256、HMAC-SHA1、MD5）
 * - 支持多种签名位置（请求头、查询参数）
 * - 支持时间戳验证，防止重放攻击
 * - 支持 API 密钥管理
 *
 * 配置参数：
 * - signatureLocation: 签名位置，可选值为 "header", "query"，默认为 "header"
 * - signatureName: 签名名称，默认为 "X-Signature"（header 模式下）或 "signature"（query 模式下）
 * - timestampName: 时间戳名称，默认为 "X-Timestamp"（header 模式下）或 "timestamp"（query 模式下）
 * - keyIdName: 密钥 ID 名称，默认为 "X-API-Key"（header 模式下）或 "apiKey"（query 模式下）
 * - algorithm: 签名算法，可选值为 "HMAC-SHA256", "HMAC-SHA1", "MD5"，默认为 "HMAC-SHA256"
 * - maxTimestampAge: 时间戳最大有效期（秒），默认为 300（5分钟）
 * - includeBody: 是否在签名中包含请求体，默认为 true
 * - includeHeaders: 要包含在签名中的请求头列表
 * - includeQueryParams: 要包含在签名中的查询参数列表
 * - excludeQueryParams: 要排除在签名外的查询参数列表
 * - apiKeys: API 密钥列表，每个密钥包含 id 和 secret
 */
class SignatureVerificationPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(SignatureVerificationPlugin::class.java)
    override val type: String = "signatureVerification"

    // 签名位置
    private val signatureLocation: SignatureLocation

    // 签名名称
    private val signatureName: String

    // 时间戳名称
    private val timestampName: String

    // 密钥 ID 名称
    private val keyIdName: String

    // 签名算法
    private val algorithm: SignatureAlgorithm

    // 时间戳最大有效期（秒）
    private val maxTimestampAge: Long

    // 是否在签名中包含请求体
    private val includeBody: Boolean

    // 要包含在签名中的请求头
    private val includeHeaders: Set<String>

    // 要包含在签名中的查询参数
    private val includeQueryParams: Set<String>

    // 要排除在签名外的查询参数
    private val excludeQueryParams: Set<String>

    // API 密钥映射
    private val apiKeys: Map<String, String>

    init {
        // 解析签名位置
        val signatureLocationStr = config.config.getString("signatureLocation", "header")
        signatureLocation = when (signatureLocationStr.lowercase()) {
            "query" -> SignatureLocation.QUERY
            else -> SignatureLocation.HEADER
        }

        // 解析签名名称
        signatureName = config.config.getString("signatureName") ?: when (signatureLocation) {
            SignatureLocation.HEADER -> "X-Signature"
            SignatureLocation.QUERY -> "signature"
        }

        // 解析时间戳名称
        timestampName = config.config.getString("timestampName") ?: when (signatureLocation) {
            SignatureLocation.HEADER -> "X-Timestamp"
            SignatureLocation.QUERY -> "timestamp"
        }

        // 解析密钥 ID 名称
        keyIdName = config.config.getString("keyIdName") ?: when (signatureLocation) {
            SignatureLocation.HEADER -> "X-API-Key"
            SignatureLocation.QUERY -> "apiKey"
        }

        // 解析签名算法
        val algorithmStr = config.config.getString("algorithm", "HMAC-SHA256")
        algorithm = when (algorithmStr.uppercase()) {
            "HMAC-SHA1" -> SignatureAlgorithm.HMAC_SHA1
            "MD5" -> SignatureAlgorithm.MD5
            else -> SignatureAlgorithm.HMAC_SHA256
        }

        // 解析时间戳最大有效期
        maxTimestampAge = config.config.getLong("maxTimestampAge", 300)

        // 解析是否在签名中包含请求体
        includeBody = config.config.getBoolean("includeBody", true)

        // 解析要包含在签名中的请求头
        val includeHeadersArray = config.config.getJsonArray("includeHeaders", JsonArray())
        includeHeaders = includeHeadersArray.map { it.toString().lowercase() }.toSet()

        // 解析要包含在签名中的查询参数
        val includeQueryParamsArray = config.config.getJsonArray("includeQueryParams", JsonArray())
        includeQueryParams = includeQueryParamsArray.map { it.toString() }.toSet()

        // 解析要排除在签名外的查询参数
        val excludeQueryParamsArray = config.config.getJsonArray("excludeQueryParams", JsonArray())
        excludeQueryParams = excludeQueryParamsArray.map { it.toString() }.toSet() + setOf(signatureName)

        // 解析 API 密钥
        val apiKeysArray = config.config.getJsonArray("apiKeys", JsonArray())
        apiKeys = apiKeysArray.mapNotNull {
            val keyObj = it as? JsonObject
            val keyId = keyObj?.getString("id")
            val keySecret = keyObj?.getString("secret")
            if (keyId != null && keySecret != null) {
                keyId to keySecret
            } else {
                null
            }
        }.toMap()

        logger.info("Initialized signature verification plugin: algorithm={}, location={}", algorithm, signatureLocation)
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 获取签名
            val signature = getSignature(context)

            if (signature == null) {
                // 签名不存在，返回 401 错误
                sendError(context, 401, "Missing signature")
                promise.complete()
                return promise.future()
            }

            // 获取时间戳
            val timestamp = getTimestamp(context)

            if (timestamp == null) {
                // 时间戳不存在，返回 401 错误
                sendError(context, 401, "Missing timestamp")
                promise.complete()
                return promise.future()
            }

            // 验证时间戳
            val currentTime = Instant.now().epochSecond
            val timestampAge = currentTime - timestamp

            if (timestampAge < 0 || timestampAge > maxTimestampAge) {
                // 时间戳无效，返回 401 错误
                sendError(context, 401, "Invalid timestamp")
                promise.complete()
                return promise.future()
            }

            // 获取密钥 ID
            val keyId = getKeyId(context)

            if (keyId == null) {
                // 密钥 ID 不存在，返回 401 错误
                sendError(context, 401, "Missing API key")
                promise.complete()
                return promise.future()
            }

            // 获取密钥
            val keySecret = apiKeys[keyId]

            if (keySecret == null) {
                // 密钥不存在，返回 401 错误
                sendError(context, 401, "Invalid API key")
                promise.complete()
                return promise.future()
            }

            // 计算签名
            calculateSignature(context, keySecret, timestamp) { calculatedSignature ->
                if (calculatedSignature == signature) {
                    // 签名验证成功，继续处理请求
                    context.next()
                    promise.complete()
                } else {
                    // 签名验证失败，返回 401 错误
                    sendError(context, 401, "Invalid signature")
                    promise.complete()
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing signature verification plugin", e)
            // 发生错误，返回 500 错误
            sendError(context, 500, "Internal server error: ${e.message}")
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 发送错误响应
     */
    private fun sendError(context: RoutingContext, statusCode: Int, message: String) {
        context.response()
            .setStatusCode(statusCode)
            .putHeader("Content-Type", "application/json")
            .end(JsonObject()
                .put("error", "Signature verification failed")
                .put("message", message)
                .encode()
            )
    }

    /**
     * 获取签名
     */
    private fun getSignature(context: RoutingContext): String? {
        return when (signatureLocation) {
            SignatureLocation.HEADER -> context.request().getHeader(signatureName)
            SignatureLocation.QUERY -> context.request().getParam(signatureName)
        }
    }

    /**
     * 获取时间戳
     */
    private fun getTimestamp(context: RoutingContext): Long? {
        val timestampStr = when (signatureLocation) {
            SignatureLocation.HEADER -> context.request().getHeader(timestampName)
            SignatureLocation.QUERY -> context.request().getParam(timestampName)
        }

        return timestampStr?.toLongOrNull()
    }

    /**
     * 获取密钥 ID
     */
    private fun getKeyId(context: RoutingContext): String? {
        return when (signatureLocation) {
            SignatureLocation.HEADER -> context.request().getHeader(keyIdName)
            SignatureLocation.QUERY -> context.request().getParam(keyIdName)
        }
    }

    /**
     * 计算签名
     */
    private fun calculateSignature(context: RoutingContext, keySecret: String, timestamp: Long, callback: (String) -> Unit) {
        // 构建签名字符串
        val stringToSign = buildStringToSign(context, timestamp)

        // 如果需要包含请求体，则读取请求体
        if (includeBody && shouldIncludeBody(context)) {
            val body = context.body().asString()
            val bodyStr = body ?: ""
            val finalStringToSign = stringToSign + bodyStr

            // 计算签名
            val signature = sign(finalStringToSign, keySecret)
            callback(signature)
        } else {
            // 不包含请求体，直接计算签名
            val signature = sign(stringToSign, keySecret)
            callback(signature)
        }
    }

    /**
     * 构建签名字符串
     */
    private fun buildStringToSign(context: RoutingContext, timestamp: Long): String {
        val request = context.request()
        val method = request.method().name()
        val path = request.path()

        // 构建签名字符串
        val stringBuilder = StringBuilder()

        // 添加请求方法
        stringBuilder.append(method).append("\n")

        // 添加请求路径
        stringBuilder.append(path).append("\n")

        // 添加时间戳
        stringBuilder.append(timestamp).append("\n")

        // 添加查询参数
        val queryParams = TreeMap<String, String>()
        request.params().forEach { (key, value) ->
            if ((includeQueryParams.isEmpty() || key in includeQueryParams) && key !in excludeQueryParams) {
                queryParams[key] = value
            }
        }

        if (queryParams.isNotEmpty()) {
            val queryString = queryParams.entries.joinToString("&") { (key, value) ->
                "${urlEncode(key)}=${urlEncode(value)}"
            }
            stringBuilder.append(queryString).append("\n")
        }

        // 添加请求头
        if (includeHeaders.isNotEmpty()) {
            val headers = TreeMap<String, String>()
            includeHeaders.forEach { headerName ->
                val headerValue = request.getHeader(headerName)
                if (headerValue != null) {
                    headers[headerName.lowercase()] = headerValue
                }
            }

            if (headers.isNotEmpty()) {
                val headerString = headers.entries.joinToString("\n") { (key, value) ->
                    "$key:$value"
                }
                stringBuilder.append(headerString).append("\n")
            }
        }

        return stringBuilder.toString()
    }

    /**
     * 判断是否应该包含请求体
     */
    private fun shouldIncludeBody(context: RoutingContext): Boolean {
        val method = context.request().method()
        return method == io.vertx.core.http.HttpMethod.POST ||
               method == io.vertx.core.http.HttpMethod.PUT ||
               method == io.vertx.core.http.HttpMethod.PATCH
    }

    /**
     * 签名
     */
    private fun sign(data: String, key: String): String {
        return when (algorithm) {
            SignatureAlgorithm.HMAC_SHA256 -> hmacSha256(data, key)
            SignatureAlgorithm.HMAC_SHA1 -> hmacSha1(data, key)
            SignatureAlgorithm.MD5 -> md5(data + key)
        }
    }

    /**
     * HMAC-SHA256 签名
     */
    private fun hmacSha256(data: String, key: String): String {
        try {
            val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(secretKeySpec)
            val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            return Base64.getEncoder().encodeToString(hash)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Failed to generate HMAC-SHA256", e)
        } catch (e: InvalidKeyException) {
            throw RuntimeException("Failed to generate HMAC-SHA256", e)
        }
    }

    /**
     * HMAC-SHA1 签名
     */
    private fun hmacSha1(data: String, key: String): String {
        try {
            val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA1")
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(secretKeySpec)
            val hash = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            return Base64.getEncoder().encodeToString(hash)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Failed to generate HMAC-SHA1", e)
        } catch (e: InvalidKeyException) {
            throw RuntimeException("Failed to generate HMAC-SHA1", e)
        }
    }

    /**
     * MD5 签名
     */
    private fun md5(data: String): String {
        try {
            val md = MessageDigest.getInstance("MD5")
            val hash = md.digest(data.toByteArray(StandardCharsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("Failed to generate MD5", e)
        }
    }

    /**
     * URL 编码
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    override fun shutdown() {
        // 无需释放资源
    }

    /**
     * 签名位置枚举
     */
    enum class SignatureLocation {
        HEADER,  // 请求头
        QUERY    // 查询参数
    }

    /**
     * 签名算法枚举
     */
    enum class SignatureAlgorithm {
        HMAC_SHA256,  // HMAC-SHA256
        HMAC_SHA1,    // HMAC-SHA1
        MD5           // MD5
    }

    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return SignatureVerificationPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
