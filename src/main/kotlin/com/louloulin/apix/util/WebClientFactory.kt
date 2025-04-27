package com.louloulin.apix.util

import io.vertx.core.Vertx
import io.vertx.core.http.HttpClientOptions
import io.vertx.ext.web.client.WebClient

/**
 * WebClient 工厂类，用于创建 WebClient 实例。
 * 这个类绕过了 WebClientOptions，直接使用 HttpClientOptions 创建 WebClient。
 * 这样可以避免在原生镜像中出现 "Cannot find vertx-version.txt on classpath" 错误。
 */
object WebClientFactory {
    /**
     * 创建一个 WebClient 实例。
     *
     * @param vertx Vertx 实例
     * @return WebClient 实例
     */
    fun create(vertx: Vertx): WebClient {
        val httpClientOptions = HttpClientOptions()
            .setKeepAlive(true)
            .setMaxPoolSize(50)
            .setConnectTimeout(5000) // 5 seconds
            .setIdleTimeout(60) // 60 seconds
        
        // 使用反射创建 WebClient，绕过 WebClientOptions
        return try {
            val webClientImplClass = Class.forName("io.vertx.ext.web.client.impl.WebClientImpl")
            val constructor = webClientImplClass.getDeclaredConstructor(Vertx::class.java, HttpClientOptions::class.java)
            constructor.isAccessible = true
            constructor.newInstance(vertx, httpClientOptions) as WebClient
        } catch (e: Exception) {
            // 如果反射失败，则使用默认方式创建
            WebClient.create(vertx)
        }
    }
}
