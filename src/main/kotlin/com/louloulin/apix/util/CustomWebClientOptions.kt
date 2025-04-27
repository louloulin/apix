package com.louloulin.apix.util

import io.vertx.core.http.HttpClientOptions
import io.vertx.ext.web.client.WebClientOptions

/**
 * 自定义的 WebClientOptions 创建器，用于原生镜像编译。
 * 解决了 Vert.x 在原生镜像中无法找到版本信息的问题。
 */
class WebClientOptionsFactory {
    companion object {
        /**
         * 创建 WebClientOptions 实例。
         */
        fun create(): WebClientOptions {
            val options = WebClientOptions()
            options.userAgent = "APIX-Gateway/1.0.0"
            return options
        }
    }
}
