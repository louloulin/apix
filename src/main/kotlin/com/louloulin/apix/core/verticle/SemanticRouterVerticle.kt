package com.louloulin.apix.core.verticle

import com.louloulin.apix.ai.router.SemanticRouter
import com.louloulin.apix.ai.router.SemanticRoutingRule
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 语义路由器Verticle，负责管理语义路由功能。
 */
class SemanticRouterVerticle : BaseVerticle() {
    // 语义路由器
    private lateinit var semanticRouter: SemanticRouter

    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动 SemanticRouterVerticle...")

        // 创建语义路由器
        semanticRouter = SemanticRouter(vertx)

        // 从配置中加载语义路由器配置
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_GET,
            JsonObject().put("section", "semanticRouter")
        ) { ar ->
            if (ar.succeeded() && ar.result().body().getBoolean("success", false)) {
                val config = ar.result().body().getJsonObject("result", JsonObject())

                // 初始化语义路由器
                semanticRouter.initialize(config)
                    .onSuccess {
                        // 注册EventBus处理器
                        registerEventBusHandlers()

                        logger.info("SemanticRouterVerticle 启动完成")
                        startPromise.complete()
                    }
                    .onFailure { err ->
                        logger.error("初始化语义路由器失败", err)
                        startPromise.fail(err)
                    }
            } else {
                // 如果配置请求失败，使用空配置初始化
                semanticRouter.initialize(JsonObject())
                    .onSuccess {
                        // 注册EventBus处理器
                        registerEventBusHandlers()

                        logger.info("SemanticRouterVerticle 使用默认配置启动完成")
                        startPromise.complete()
                    }
                    .onFailure { err ->
                        logger.error("初始化语义路由器失败", err)
                        startPromise.fail(err)
                    }
            }
        }
    }

    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止 SemanticRouterVerticle...")

        // 关闭语义路由器
        semanticRouter.close()
            .onSuccess {
                logger.info("SemanticRouterVerticle 停止完成")
                stopPromise.complete()
            }
            .onFailure { err ->
                logger.error("关闭语义路由器失败", err)
                stopPromise.fail(err)
            }
    }

    override fun registerEventBusHandlers() {
        // 语义路由
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_SEMANTIC_ROUTE) { message ->
            val request = message.body()
            val content = request.getString("content", "")
            val defaultModel = request.getString("defaultModel", "gpt-3.5-turbo")

            semanticRouter.route(content, defaultModel)
                .onSuccess { model ->
                    sendSuccess(message, JsonObject()
                        .put("model", model)
                    )
                }
                .onFailure { err ->
                    sendError(message, err)
                }
        }

        // 获取语义路由规则
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_SEMANTIC_RULES_GET) { message ->
            val rules = semanticRouter.getRules().map { it.toJson() }
            sendSuccess(message, JsonObject()
                .put("rules", rules)
            )
        }

        // 添加语义路由规则
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_SEMANTIC_RULE_ADD) { message ->
            val ruleJson = message.body().getJsonObject("rule")
            if (ruleJson == null) {
                sendError(message, 400, "缺少规则数据")
                return@consumer
            }

            val rule = SemanticRoutingRule.fromJson(ruleJson)
            semanticRouter.addRule(rule)
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("id", rule.id)
                        .put("message", "规则添加成功")
                    )
                }
                .onFailure { err ->
                    sendError(message, err)
                }
        }

        // 移除语义路由规则
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_SEMANTIC_RULE_REMOVE) { message ->
            val ruleId = message.body().getString("id")
            if (ruleId == null) {
                sendError(message, 400, "缺少规则ID")
                return@consumer
            }

            semanticRouter.removeRule(ruleId)
                .onSuccess { removed ->
                    sendSuccess(message, JsonObject()
                        .put("removed", removed)
                        .put("message", if (removed) "规则移除成功" else "规则不存在")
                    )
                }
                .onFailure { err ->
                    sendError(message, err)
                }
        }

        // 清除所有语义路由规则
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.AI_SEMANTIC_RULES_CLEAR) { message ->
            semanticRouter.clearRules()
                .onSuccess {
                    sendSuccess(message, JsonObject()
                        .put("message", "所有规则已清除")
                    )
                }
                .onFailure { err ->
                    sendError(message, err)
                }
        }
    }


}
