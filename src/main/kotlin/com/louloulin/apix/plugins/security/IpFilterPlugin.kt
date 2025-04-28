package com.louloulin.apix.plugins.security

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * IP 黑白名单插件
 * 
 * 该插件用于根据 IP 地址过滤请求，支持黑名单和白名单模式。
 * - 黑名单模式：拒绝黑名单中的 IP 地址
 * - 白名单模式：只允许白名单中的 IP 地址
 * 
 * 配置参数：
 * - mode: 过滤模式，可选值为 "blacklist" 或 "whitelist"，默认为 "blacklist"
 * - ips: IP 地址列表，支持单个 IP 和 CIDR 格式
 * - statusCode: 拒绝请求时返回的状态码，默认为 403
 * - message: 拒绝请求时返回的消息，默认为 "IP address not allowed"
 */
class IpFilterPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(IpFilterPlugin::class.java)
    override val type: String = "ipFilter"
    
    // 过滤模式
    private val mode: FilterMode
    
    // IP 地址列表
    private val ipList: List<IpRange>
    
    // 拒绝请求时返回的状态码
    private val statusCode: Int
    
    // 拒绝请求时返回的消息
    private val message: String
    
    // IP 缓存，用于提高性能
    private val ipCache = ConcurrentHashMap<String, Boolean>()
    
    init {
        // 解析配置
        val modeStr = config.config.getString("mode", "blacklist")
        mode = when (modeStr.lowercase()) {
            "whitelist" -> FilterMode.WHITELIST
            else -> FilterMode.BLACKLIST
        }
        
        // 解析 IP 列表
        val ipsArray = config.config.getJsonArray("ips", JsonArray())
        ipList = ipsArray.map { ip ->
            parseIpRange(ip.toString())
        }
        
        // 解析状态码和消息
        statusCode = config.config.getInteger("statusCode", 403)
        message = config.config.getString("message", "IP address not allowed")
        
        logger.info("Initialized IP filter plugin: mode={}, ips={}", mode, ipList)
    }
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 获取客户端 IP 地址
            val clientIp = getClientIp(context)
            
            // 检查 IP 是否允许
            val allowed = isIpAllowed(clientIp)
            
            if (allowed) {
                // IP 允许，继续处理请求
                context.next()
                promise.complete()
            } else {
                // IP 不允许，拒绝请求
                context.response()
                    .setStatusCode(statusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", message)
                        .put("ip", clientIp)
                        .encode()
                    )
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error executing IP filter plugin", e)
            // 发生错误时，继续处理请求
            context.next()
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 获取客户端 IP 地址
     */
    private fun getClientIp(context: RoutingContext): String {
        // 尝试从 X-Forwarded-For 头获取 IP
        val forwardedFor = context.request().getHeader("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            return forwardedFor.split(",")[0].trim()
        }
        
        // 尝试从 X-Real-IP 头获取 IP
        val realIp = context.request().getHeader("X-Real-IP")
        if (!realIp.isNullOrBlank()) {
            return realIp.trim()
        }
        
        // 使用远程地址
        return context.request().remoteAddress().host()
    }
    
    /**
     * 检查 IP 是否允许
     */
    private fun isIpAllowed(ip: String): Boolean {
        // 检查缓存
        val cachedResult = ipCache[ip]
        if (cachedResult != null) {
            return cachedResult
        }
        
        // 检查 IP 是否在列表中
        val inList = ipList.any { it.contains(ip) }
        
        // 根据模式判断是否允许
        val allowed = when (mode) {
            FilterMode.BLACKLIST -> !inList // 黑名单模式：不在列表中的允许
            FilterMode.WHITELIST -> inList  // 白名单模式：在列表中的允许
        }
        
        // 缓存结果
        ipCache[ip] = allowed
        
        return allowed
    }
    
    /**
     * 解析 IP 范围
     */
    private fun parseIpRange(ipStr: String): IpRange {
        return if (ipStr.contains("/")) {
            // CIDR 格式
            val parts = ipStr.split("/")
            val ip = parts[0]
            val prefixLength = parts[1].toInt()
            CidrRange(ip, prefixLength)
        } else {
            // 单个 IP
            SingleIp(ipStr)
        }
    }
    
    override fun shutdown() {
        // 清空缓存
        ipCache.clear()
    }
    
    /**
     * 过滤模式
     */
    enum class FilterMode {
        BLACKLIST,  // 黑名单模式
        WHITELIST   // 白名单模式
    }
    
    /**
     * IP 范围接口
     */
    interface IpRange {
        /**
         * 检查 IP 是否在范围内
         */
        fun contains(ip: String): Boolean
        
        /**
         * 获取范围的字符串表示
         */
        override fun toString(): String
    }
    
    /**
     * 单个 IP
     */
    class SingleIp(private val ip: String) : IpRange {
        override fun contains(ip: String): Boolean {
            return this.ip == ip
        }
        
        override fun toString(): String {
            return ip
        }
    }
    
    /**
     * CIDR 范围
     */
    class CidrRange(ip: String, private val prefixLength: Int) : IpRange {
        private val networkAddress: ByteArray
        private val mask: ByteArray
        
        init {
            // 解析 IP 地址
            val inetAddress = InetAddress.getByName(ip)
            val addressBytes = inetAddress.address
            
            // 创建网络地址
            networkAddress = ByteArray(addressBytes.size)
            System.arraycopy(addressBytes, 0, networkAddress, 0, addressBytes.size)
            
            // 创建掩码
            mask = createMask(addressBytes.size, prefixLength)
            
            // 应用掩码到网络地址
            for (i in networkAddress.indices) {
                networkAddress[i] = (networkAddress[i].toInt() and mask[i].toInt()).toByte()
            }
        }
        
        override fun contains(ip: String): Boolean {
            try {
                // 解析 IP 地址
                val inetAddress = InetAddress.getByName(ip)
                val addressBytes = inetAddress.address
                
                // 检查地址长度是否匹配
                if (addressBytes.size != networkAddress.size) {
                    return false
                }
                
                // 应用掩码并检查是否匹配
                for (i in addressBytes.indices) {
                    if ((addressBytes[i].toInt() and mask[i].toInt()).toByte() != networkAddress[i]) {
                        return false
                    }
                }
                
                return true
            } catch (e: Exception) {
                return false
            }
        }
        
        override fun toString(): String {
            return "${InetAddress.getByAddress(networkAddress).hostAddress}/$prefixLength"
        }
        
        /**
         * 创建掩码
         */
        private fun createMask(addressLength: Int, prefixLength: Int): ByteArray {
            val mask = ByteArray(addressLength)
            
            // 填充掩码
            var remainingPrefixLength = prefixLength
            for (i in mask.indices) {
                if (remainingPrefixLength >= 8) {
                    mask[i] = 0xFF.toByte()
                    remainingPrefixLength -= 8
                } else if (remainingPrefixLength > 0) {
                    mask[i] = (0xFF shl (8 - remainingPrefixLength)).toByte()
                    remainingPrefixLength = 0
                } else {
                    mask[i] = 0
                }
            }
            
            return mask
        }
    }
    
    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return IpFilterPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
