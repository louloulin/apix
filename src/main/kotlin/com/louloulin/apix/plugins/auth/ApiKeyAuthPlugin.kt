package com.louloulin.apix.plugins.auth

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import com.louloulin.apix.plugins.PluginFactory
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * API Key 认证插件
 */
class ApiKeyAuthPlugin(
    override val id: String,
    private val vertx: Vertx,
    override val config: PluginConfig
) : Plugin {
    private val logger = LoggerFactory.getLogger(ApiKeyAuthPlugin::class.java)
    override val type: String = "apiKeyAuth"
    private val handler: ApiKeyAuthHandler

    init {
        // 从配置中获取所需的作用域
        val requiredScopes = config.config.getJsonArray("requiredScopes", io.vertx.core.json.JsonArray()).map { it.toString() }

        // 从配置中获取请求头名称
        val headerName = config.config.getString("headerName", "X-API-Key")

        // 创建处理器
        handler = ApiKeyAuthHandler.create(vertx, requiredScopes, headerName)
    }

    /**
     * 关闭插件并释放资源
     */
    override fun shutdown() {
        // 无需释放资源
    }

    override fun execute(context: RoutingContext): Future<Void> {
        return Future.future { promise ->
            try {
                // 使用处理器验证 API Key
                handler.handle(context)

                // 注意：处理器会调用 context.next() 或者结束响应
                // 所以我们不需要在这里调用 promise.complete()

                // 但是为了满足 Future 接口，我们需要在某个时候完成 promise
                // 我们可以通过监听响应结束事件来完成 promise
                context.response().endHandler {
                    promise.complete()
                }

                // 如果处理器没有结束响应，我们需要手动完成 promise
                if (!context.response().ended()) {
                    promise.complete()
                }
            } catch (e: Exception) {
                logger.error("Error executing API Key auth plugin", e)
                promise.fail(e)
            }
        }
    }

    /**
     * API Key 认证插件工厂
     */
    class Factory : PluginFactory {
        val type = "apiKeyAuth"

        override fun create(config: PluginConfig): Plugin {
            return ApiKeyAuthPlugin(config.id, Vertx.currentContext().owner(), config)
        }
    }
}
