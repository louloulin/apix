package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 提示词增强 Verticle 测试
 */
@ExtendWith(VertxExtension::class)
class PromptEnhancerVerticleTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        
        // 部署 ConfigVerticle 和 PromptEnhancerVerticle
        vertx.deployVerticle(ConfigVerticle())
            .compose { vertx.deployVerticle(PromptEnhancerVerticle()) }
            .onComplete { ar ->
                if (ar.succeeded()) {
                    testContext.completeNow()
                } else {
                    testContext.failNow(ar.cause())
                }
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }
    
    @Test
    fun `test get templates`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_PROMPT_TEMPLATES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    val response = ar.result().body()
                    assertTrue(response.getBoolean("success", false))
                    val templates = response.getJsonArray("result")
                    assertNotNull(templates)
                    // 初始状态下应该没有模板
                    assertEquals(0, templates.size())
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test add and get template`(testContext: VertxTestContext) {
        // 创建模板
        val templateId = UUID.randomUUID().toString()
        val template = JsonObject()
            .put("id", templateId)
            .put("name", "Test Template")
            .put("description", "A test template")
            .put("template", "System: {{system}}\n\nUser: {{content}}")
            .put("variables", JsonArray().add("system").add("content"))
            .put("category", "test")
            .put("tags", JsonArray().add("test").add("example"))
        
        // 添加模板
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_PROMPT_TEMPLATE_ADD,
            JsonObject().put("template", template)
        ) { addAr ->
            if (addAr.succeeded()) {
                // 获取模板
                vertx.eventBus().request<JsonObject>(
                    EventBusAddresses.AI_PROMPT_TEMPLATE_GET,
                    JsonObject().put("id", templateId)
                ) { getAr ->
                    if (getAr.succeeded()) {
                        testContext.verify {
                            val response = getAr.result().body()
                            assertTrue(response.getBoolean("success", false))
                            val retrievedTemplate = response.getJsonObject("result")
                            assertNotNull(retrievedTemplate)
                            assertEquals(templateId, retrievedTemplate.getString("id"))
                            assertEquals("Test Template", retrievedTemplate.getString("name"))
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(getAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test get rules`(testContext: VertxTestContext) {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_PROMPT_RULES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                testContext.verify {
                    val response = ar.result().body()
                    assertTrue(response.getBoolean("success", false))
                    val rules = response.getJsonArray("result")
                    assertNotNull(rules)
                    // 初始状态下应该没有规则
                    assertEquals(0, rules.size())
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test add rule`(testContext: VertxTestContext) {
        // 创建规则
        val ruleId = UUID.randomUUID().toString()
        val rule = JsonObject()
            .put("id", ruleId)
            .put("name", "Test Rule")
            .put("description", "A test rule")
            .put("type", "PREPEND")
            .put("pattern", "")
            .put("replacement", "You are a helpful assistant.\n\n")
            .put("priority", 10)
            .put("requestTypes", JsonArray().add("completion"))
            .put("models", JsonArray().add("gpt-4"))
            .put("enabled", true)
        
        // 添加规则
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_PROMPT_RULE_ADD,
            JsonObject().put("rule", rule)
        ) { addAr ->
            if (addAr.succeeded()) {
                // 获取规则
                vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_PROMPT_RULES_GET, JsonObject()) { getRulesAr ->
                    if (getRulesAr.succeeded()) {
                        testContext.verify {
                            val response = getRulesAr.result().body()
                            assertTrue(response.getBoolean("success", false))
                            val rules = response.getJsonArray("result")
                            assertNotNull(rules)
                            assertEquals(1, rules.size())
                            
                            val retrievedRule = rules.getJsonObject(0)
                            assertEquals(ruleId, retrievedRule.getString("id"))
                            assertEquals("Test Rule", retrievedRule.getString("name"))
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(getRulesAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test enhance prompt`(testContext: VertxTestContext) {
        // 创建规则
        val rule = JsonObject()
            .put("id", UUID.randomUUID().toString())
            .put("name", "Prepend Rule")
            .put("description", "Add system instruction")
            .put("type", "PREPEND")
            .put("pattern", "")
            .put("replacement", "You are a helpful assistant.\n\n")
            .put("priority", 10)
            .put("requestTypes", JsonArray())
            .put("models", JsonArray())
            .put("enabled", true)
        
        // 添加规则
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_PROMPT_RULE_ADD,
            JsonObject().put("rule", rule)
        ) { addAr ->
            if (addAr.succeeded()) {
                // 创建请求
                val request = JsonObject()
                    .put("prompt", "What is the capital of France?")
                    .put("type", "completion")
                    .put("model", "gpt-4")
                
                // 增强提示词
                vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_PROMPT_ENHANCE, request) { enhanceAr ->
                    if (enhanceAr.succeeded()) {
                        testContext.verify {
                            val response = enhanceAr.result().body()
                            assertTrue(response.getBoolean("success", false))
                            val enhancedRequest = response.getJsonObject("result")
                            assertNotNull(enhancedRequest)
                            assertEquals(
                                "You are a helpful assistant.\n\nWhat is the capital of France?",
                                enhancedRequest.getString("prompt")
                            )
                            assertTrue(enhancedRequest.getBoolean("enhanced", false))
                            testContext.completeNow()
                        }
                    } else {
                        testContext.failNow(enhanceAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
    
    @Test
    fun `test clear rules`(testContext: VertxTestContext) {
        // 创建规则
        val rule = JsonObject()
            .put("id", UUID.randomUUID().toString())
            .put("name", "Test Rule")
            .put("description", "A test rule")
            .put("type", "APPEND")
            .put("pattern", "")
            .put("replacement", "\n\nThank you!")
            .put("priority", 5)
            .put("requestTypes", JsonArray())
            .put("models", JsonArray())
            .put("enabled", true)
        
        // 添加规则
        vertx.eventBus().request<JsonObject>(
            EventBusAddresses.AI_PROMPT_RULE_ADD,
            JsonObject().put("rule", rule)
        ) { addAr ->
            if (addAr.succeeded()) {
                // 清空规则
                vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_PROMPT_RULES_CLEAR, JsonObject()) { clearAr ->
                    if (clearAr.succeeded()) {
                        // 获取规则
                        vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_PROMPT_RULES_GET, JsonObject()) { getRulesAr ->
                            if (getRulesAr.succeeded()) {
                                testContext.verify {
                                    val response = getRulesAr.result().body()
                                    assertTrue(response.getBoolean("success", false))
                                    val rules = response.getJsonArray("result")
                                    assertNotNull(rules)
                                    assertEquals(0, rules.size())
                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(getRulesAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(clearAr.cause())
                    }
                }
            } else {
                testContext.failNow(addAr.cause())
            }
        }
        
        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
