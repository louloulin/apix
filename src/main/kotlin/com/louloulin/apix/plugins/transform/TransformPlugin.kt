package com.louloulin.apix.plugins.transform

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.DecodeException
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * 转换插件，用于修改请求和响应的内容。
 */
class TransformPlugin(
    override val id: String,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(TransformPlugin::class.java)

    override val type: String = "transform"

    // 配置值
    private val requestTransforms: List<Transform> = parseTransforms(config.getJsonObject("request"))
    private val responseTransforms: List<Transform> = parseTransforms(config.getJsonObject("response"))

    /**
     * 从配置中解析转换规则。
     */
    private fun parseTransforms(config: JsonObject?): List<Transform> {
        if (config == null) {
            return emptyList()
        }

        val transforms = mutableListOf<Transform>()

        // 添加头部转换
        val headers = config.getJsonObject("headers")
        if (headers != null) {
            headers.fieldNames().forEach { name ->
                val value = headers.getString(name)
                transforms.add(HeaderTransform(name, value))
            }
        }

        // 添加JSON转换
        val json = config.getJsonObject("json")
        if (json != null) {
            json.fieldNames().forEach { path ->
                val value = json.getValue(path)
                transforms.add(JsonTransform(path, value))
            }
        }

        return transforms
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 应用请求转换
            applyRequestTransforms(context)

            // 添加响应处理器来应用响应转换
            context.addHeadersEndHandler { _ ->
                try {
                    applyResponseTransforms(context)
                } catch (e: Exception) {
                    logger.error("Error applying response transforms", e)
                }
            }

            promise.complete()
        } catch (e: Exception) {
            logger.error("Error executing transform plugin", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 应用请求转换。
     */
    private fun applyRequestTransforms(context: RoutingContext) {
        requestTransforms.forEach { transform ->
            transform.apply(context, TransformTarget.REQUEST)
        }
    }

    /**
     * 应用响应转换。
     */
    private fun applyResponseTransforms(context: RoutingContext) {
        responseTransforms.forEach { transform ->
            transform.apply(context, TransformTarget.RESPONSE)
        }
    }

    override fun shutdown() {
        // 没有资源需要清理
    }

    /**
     * 转换目标（请求或响应）。
     */
    enum class TransformTarget {
        REQUEST,
        RESPONSE
    }

    /**
     * 表示一个转换操作。
     */
    interface Transform {
        /**
         * 应用转换到请求或响应。
         */
        fun apply(context: RoutingContext, target: TransformTarget)
    }

    /**
     * 头部转换。
     */
    class HeaderTransform(private val name: String, private val value: String) : Transform {
        override fun apply(context: RoutingContext, target: TransformTarget) {
            when (target) {
                TransformTarget.REQUEST -> context.request().headers().set(name, value)
                TransformTarget.RESPONSE -> context.response().putHeader(name, value)
            }
        }
    }

    /**
     * JSON转换。
     */
    class JsonTransform(private val path: String, private val value: Any) : Transform {
        override fun apply(context: RoutingContext, target: TransformTarget) {
            when (target) {
                TransformTarget.REQUEST -> {
                    val body = context.body().buffer()
                    if (body != null) {
                        try {
                            val jsonBody = JsonObject(body.toString())
                            setValueAtPath(jsonBody, path, value)

                            // 替换请求体
                            context.setBody(Buffer.buffer(jsonBody.encode()))
                        } catch (e: DecodeException) {
                            // 不是有效的JSON，忽略
                        }
                    }
                }
                TransformTarget.RESPONSE -> {
                    // 响应转换将在响应发送前应用
                    // 这需要一个响应体拦截器，这里简化处理
                }
            }
        }

        /**
         * 在JSON对象的指定路径设置值。
         */
        private fun setValueAtPath(json: JsonObject, path: String, value: Any) {
            val parts = path.split(".")
            var current = json

            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                var next = current.getJsonObject(part)

                if (next == null) {
                    next = JsonObject()
                    current.put(part, next)
                }

                current = next
            }

            current.put(parts.last(), value)
        }
    }
}
