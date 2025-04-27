package com.louloulin.apix.models

import io.vertx.core.json.JsonObject

/**
 * 表示网关中的服务。
 * 服务是一个抽象的后端API，可以被多个路由引用。
 */
data class Service(
    val id: String,
    val name: String,
    val url: String,
    val protocol: String = "https",
    val host: String,
    val port: Int,
    val path: String = "",
    val retries: Int = 5,
    val connectTimeout: Int = 60000,
    val readTimeout: Int = 60000,
    val enabled: Boolean = true
) {
    /**
     * 将服务转换为JSON对象。
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("id", id)
            .put("name", name)
            .put("url", url)
            .put("protocol", protocol)
            .put("host", host)
            .put("port", port)
            .put("path", path)
            .put("retries", retries)
            .put("connectTimeout", connectTimeout)
            .put("readTimeout", readTimeout)
            .put("enabled", enabled)
    }
    
    companion object {
        /**
         * 从JSON对象创建服务。
         */
        fun fromJson(json: JsonObject): Service {
            val id = json.getString("id")
                ?: throw IllegalArgumentException("Service ID is required")
            
            val name = json.getString("name", id)
            
            val url = json.getString("url")
                ?: throw IllegalArgumentException("Service URL is required")
            
            // 解析URL以获取协议、主机、端口和路径
            val urlObj = java.net.URL(url)
            val protocol = json.getString("protocol", urlObj.protocol)
            val host = json.getString("host", urlObj.host)
            val port = json.getInteger("port", if (urlObj.port == -1) {
                if (protocol == "https") 443 else 80
            } else urlObj.port)
            val path = json.getString("path", urlObj.path)
            
            val retries = json.getInteger("retries", 5)
            val connectTimeout = json.getInteger("connectTimeout", 60000)
            val readTimeout = json.getInteger("readTimeout", 60000)
            val enabled = json.getBoolean("enabled", true)
            
            return Service(
                id = id,
                name = name,
                url = url,
                protocol = protocol,
                host = host,
                port = port,
                path = path,
                retries = retries,
                connectTimeout = connectTimeout,
                readTimeout = readTimeout,
                enabled = enabled
            )
        }
    }
}
