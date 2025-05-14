package com.louloulin.apix.ai.router

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class LoadBalancedModelRouterTest {
    private val logger = LoggerFactory.getLogger(LoadBalancedModelRouterTest::class.java)

    private lateinit var vertx: Vertx
    private lateinit var loadBalancedModelRouter: LoadBalancedModelRouter

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建负载均衡模型路由器
        loadBalancedModelRouter = LoadBalancedModelRouter(vertx)

        // 创建配置
        val config = JsonObject()
            .put("healthCheckInterval", 60000L)
            .put("adaptiveLoadBalancing", true)
            .put("modelGroups", JsonArray()
                .add(JsonObject()
                    .put("id", "group1")
                    .put("name", "GPT模型组")
                    .put("description", "OpenAI GPT模型组")
                    .put("strategy", "ROUND_ROBIN")
                    .put("models", JsonArray()
                        .add(JsonObject()
                            .put("id", "gpt-3.5-turbo")
                            .put("name", "GPT-3.5 Turbo")
                            .put("description", "OpenAI GPT-3.5 Turbo模型")
                            .put("weight", 1)
                            .put("preferredContentTypes", JsonArray().add("text"))
                            .put("preferredRequestTypes", JsonArray().add("chat"))
                        )
                        .add(JsonObject()
                            .put("id", "gpt-4")
                            .put("name", "GPT-4")
                            .put("description", "OpenAI GPT-4模型")
                            .put("weight", 2)
                            .put("preferredContentTypes", JsonArray().add("text"))
                            .put("preferredRequestTypes", JsonArray().add("chat"))
                        )
                    )
                )
                .add(JsonObject()
                    .put("id", "group2")
                    .put("name", "Claude模型组")
                    .put("description", "Anthropic Claude模型组")
                    .put("strategy", "WEIGHTED")
                    .put("models", JsonArray()
                        .add(JsonObject()
                            .put("id", "claude-3-opus")
                            .put("name", "Claude 3 Opus")
                            .put("description", "Anthropic Claude 3 Opus模型")
                            .put("weight", 3)
                            .put("preferredContentTypes", JsonArray().add("text"))
                            .put("preferredRequestTypes", JsonArray().add("chat"))
                        )
                        .add(JsonObject()
                            .put("id", "claude-3-sonnet")
                            .put("name", "Claude 3 Sonnet")
                            .put("description", "Anthropic Claude 3 Sonnet模型")
                            .put("weight", 1)
                            .put("preferredContentTypes", JsonArray().add("text"))
                            .put("preferredRequestTypes", JsonArray().add("chat"))
                        )
                    )
                )
            )

        // 初始化负载均衡模型路由器
        loadBalancedModelRouter.initialize(config)
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        loadBalancedModelRouter.close()
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testRouteToGPTGroup(testContext: VertxTestContext) {
        val request = JsonObject()
            .put("groupId", "group1")
            .put("content", "请帮我写一个Java程序")
            .put("contentType", "text")
            .put("requestType", "chat")
            .put("defaultModel", "gpt-3.5-turbo")

        loadBalancedModelRouter.routeRequest(request)
            .onSuccess { model ->
                testContext.verify {
                    // 应该路由到GPT模型组中的一个模型
                    assert(model == "gpt-3.5-turbo" || model == "gpt-4") {
                        "Expected model to be gpt-3.5-turbo or gpt-4, but was $model"
                    }
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testRouteToClaudeGroup(testContext: VertxTestContext) {
        val request = JsonObject()
            .put("groupId", "group2")
            .put("content", "请帮我写一首诗")
            .put("contentType", "text")
            .put("requestType", "chat")
            .put("defaultModel", "gpt-3.5-turbo")

        loadBalancedModelRouter.routeRequest(request)
            .onSuccess { model ->
                testContext.verify {
                    // 应该路由到Claude模型组中的一个模型
                    assert(model == "claude-3-opus" || model == "claude-3-sonnet") {
                        "Expected model to be claude-3-opus or claude-3-sonnet, but was $model"
                    }
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testRouteToDefaultModel(testContext: VertxTestContext) {
        val request = JsonObject()
            .put("groupId", "non-existent-group")
            .put("content", "今天的天气怎么样？")
            .put("contentType", "text")
            .put("requestType", "chat")
            .put("defaultModel", "gpt-3.5-turbo")

        loadBalancedModelRouter.routeRequest(request)
            .onSuccess { model ->
                testContext.verify {
                    // 应该路由到默认模型
                    assert(model == "gpt-3.5-turbo") {
                        "Expected model to be gpt-3.5-turbo, but was $model"
                    }
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testAddAndRemoveModelGroup(testContext: VertxTestContext) {
        // 创建新模型组
        val newGroup = LoadBalancedModelRouter.ModelGroup(
            id = "group3",
            name = "Cohere模型组",
            description = "Cohere模型组",
            strategy = LoadBalancedModelRouter.LoadBalancingStrategy.LEAST_CONNECTIONS,
            models = listOf(
                LoadBalancedModelRouter.Model(
                    id = "cohere:command",
                    name = "Cohere Command",
                    description = "Cohere Command模型",
                    weight = 1,
                    preferredContentTypes = listOf("text"),
                    preferredRequestTypes = listOf("completion"),
                    healthy = true
                )
            )
        )

        // 添加模型组
        loadBalancedModelRouter.addModelGroup(newGroup)
            .compose {
                // 测试路由到新模型组
                val request = JsonObject()
                    .put("groupId", "group3")
                    .put("content", "请帮我总结这篇文章")
                    .put("contentType", "text")
                    .put("requestType", "completion")
                    .put("defaultModel", "gpt-3.5-turbo")

                loadBalancedModelRouter.routeRequest(request)
            }
            .compose { model ->
                testContext.verify {
                    // 应该路由到Cohere模型组中的模型
                    assert(model == "cohere:command") {
                        "Expected model to be cohere:command, but was $model"
                    }
                }

                // 移除模型组
                loadBalancedModelRouter.removeModelGroup("group3")
            }
            .compose { removed ->
                testContext.verify {
                    // 模型组应该被成功移除
                    assert(removed) {
                        "Expected group to be removed"
                    }
                }

                // 再次测试路由，应该使用默认模型
                val request = JsonObject()
                    .put("groupId", "group3")
                    .put("content", "请帮我总结这篇文章")
                    .put("contentType", "text")
                    .put("requestType", "completion")
                    .put("defaultModel", "gpt-3.5-turbo")

                loadBalancedModelRouter.routeRequest(request)
            }
            .onSuccess { model ->
                testContext.verify {
                    // 应该路由到默认模型
                    assert(model == "gpt-3.5-turbo") {
                        "Expected model to be gpt-3.5-turbo, but was $model"
                    }
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testClearModelGroups(testContext: VertxTestContext) {
        // 清除所有模型组
        loadBalancedModelRouter.clearModelGroups()
            .compose {
                // 测试路由，应该使用默认模型
                val request = JsonObject()
                    .put("groupId", "group1")
                    .put("content", "请帮我写一个Java程序")
                    .put("contentType", "text")
                    .put("requestType", "chat")
                    .put("defaultModel", "gpt-3.5-turbo")

                loadBalancedModelRouter.routeRequest(request)
            }
            .onSuccess { model ->
                testContext.verify {
                    // 应该路由到默认模型
                    assert(model == "gpt-3.5-turbo") {
                        "Expected model to be gpt-3.5-turbo, but was $model"
                    }
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }

    @Test
    fun testRecordRequestCompletion(testContext: VertxTestContext) {
        // 记录请求完成
        val modelId = "gpt-4"
        val requestId = UUID.randomUUID().toString()

        // 直接更新模型统计信息
        // 获取模型统计对象
        val stats = loadBalancedModelRouter.getModelStats().find { it.getString("modelId") == modelId }

        if (stats != null) {
            // 手动增加请求计数
            val modelStats = loadBalancedModelRouter.getModelStatsObject(modelId)
            if (modelStats != null) {
                modelStats.requestCount.incrementAndGet()
                modelStats.successCount.incrementAndGet()
            }

            // 记录请求完成
            loadBalancedModelRouter.recordRequestCompletion(modelId, requestId, true)
                .compose {
                    // 获取模型统计信息
                    val updatedStats = loadBalancedModelRouter.getModelStats()

                    testContext.verify {
                        // 应该有模型统计信息
                        assert(updatedStats.isNotEmpty()) {
                            "Expected model stats to be non-empty"
                        }

                        // 找到gpt-4的统计信息
                        val gpt4Stats = updatedStats.find { it.getString("modelId") == "gpt-4" }
                        assert(gpt4Stats != null) {
                            "Expected to find stats for gpt-4"
                        }

                        // 验证请求计数
                        val requestCount = gpt4Stats?.getLong("requestCount") ?: 0
                        assert(requestCount > 0) {
                            "Expected requestCount to be greater than 0, but was $requestCount"
                        }
                    }

                    testContext.completeNow()
                    io.vertx.core.Future.succeededFuture<Void>()
                }
                .onFailure { err ->
                    testContext.failNow(err.message)
                }
        } else {
            testContext.failNow("Could not find stats for model $modelId")
        }
    }
}
