package com.louloulin.apix.ai.prompt

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
 * 提示词增强器测试
 */
@ExtendWith(VertxExtension::class)
class PromptEnhancerTest {
    private lateinit var vertx: Vertx
    private lateinit var promptEnhancer: PromptEnhancer

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        promptEnhancer = PromptEnhancer(vertx)

        // 初始化提示词增强器
        promptEnhancer.initialize(JsonObject())
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
    fun `test add and get template`(testContext: VertxTestContext) {
        // 创建模板
        val templateId = UUID.randomUUID().toString()
        val template = PromptTemplate(
            id = templateId,
            name = "Test Template",
            description = "A test template",
            template = "System: {{system}}\n\nUser: {{content}}",
            variables = listOf("system", "content"),
            category = "test",
            tags = listOf("test", "example")
        )

        // 添加模板
        promptEnhancer.addTemplate(template).onComplete { ar ->
            if (ar.succeeded()) {
                // 获取模板
                val retrievedTemplate = promptEnhancer.getTemplate(templateId)
                testContext.verify {
                    assertNotNull(retrievedTemplate)
                    assertEquals(templateId, retrievedTemplate.id)
                    assertEquals("Test Template", retrievedTemplate.name)
                    assertEquals("A test template", retrievedTemplate.description)
                    assertEquals("System: {{system}}\n\nUser: {{content}}", retrievedTemplate.template)
                    assertEquals(listOf("system", "content"), retrievedTemplate.variables)
                    assertEquals("test", retrievedTemplate.category)
                    assertEquals(listOf("test", "example"), retrievedTemplate.tags)
                }

                // 获取所有模板
                val templates = promptEnhancer.getTemplates()
                testContext.verify {
                    assertTrue(templates.isNotEmpty())
                    assertTrue(templates.any { it.id == templateId })
                }

                // 测试应用模板
                val variables = JsonObject()
                    .put("system", "You are a helpful assistant.")
                    .put("content", "Hello, world!")

                val result = template.apply("Hello, world!", variables)
                testContext.verify {
                    assertEquals("System: You are a helpful assistant.\n\nUser: Hello, world!", result)
                }

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test add and remove template`(testContext: VertxTestContext) {
        // 创建模板
        val templateId = UUID.randomUUID().toString()
        val template = PromptTemplate(
            id = templateId,
            name = "Test Template",
            description = "A test template",
            template = "{{content}}",
            variables = listOf("content"),
            category = "test",
            tags = listOf("test")
        )

        // 添加模板
        promptEnhancer.addTemplate(template).onComplete { ar ->
            if (ar.succeeded()) {
                // 验证模板已添加
                val retrievedTemplate = promptEnhancer.getTemplate(templateId)
                testContext.verify {
                    assertNotNull(retrievedTemplate)
                }

                // 删除模板
                promptEnhancer.removeTemplate(templateId).onComplete { removeAr ->
                    if (removeAr.succeeded()) {
                        val removed = removeAr.result()
                        testContext.verify {
                            assertTrue(removed)
                            assertEquals(null, promptEnhancer.getTemplate(templateId))
                        }

                        testContext.completeNow()
                    } else {
                        testContext.failNow(removeAr.cause())
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test add and get rule`(testContext: VertxTestContext) {
        // 创建规则
        val ruleId = UUID.randomUUID().toString()
        val rule = EnhancementRule(
            id = ruleId,
            name = "Test Rule",
            description = "A test rule",
            type = RuleType.PREPEND,
            pattern = "",
            replacement = "You are a helpful assistant.\n\n",
            priority = 10,
            requestTypes = listOf("completion"),
            models = listOf("gpt-4"),
            enabled = true
        )

        // 添加规则
        promptEnhancer.addRule(rule).onComplete { ar ->
            if (ar.succeeded()) {
                // 获取所有规则
                val rules = promptEnhancer.getRules()
                testContext.verify {
                    assertTrue(rules.isNotEmpty())
                    assertTrue(rules.any { it.id == ruleId })

                    val retrievedRule = rules.first { it.id == ruleId }
                    assertEquals(ruleId, retrievedRule.id)
                    assertEquals("Test Rule", retrievedRule.name)
                    assertEquals("A test rule", retrievedRule.description)
                    assertEquals(RuleType.PREPEND, retrievedRule.type)
                    assertEquals("", retrievedRule.pattern)
                    assertEquals("You are a helpful assistant.\n\n", retrievedRule.replacement)
                    assertEquals(10, retrievedRule.priority)
                    assertEquals(listOf("completion"), retrievedRule.requestTypes)
                    assertEquals(listOf("gpt-4"), retrievedRule.models)
                    assertTrue(retrievedRule.enabled)
                }

                testContext.completeNow()
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test add and remove rule`(testContext: VertxTestContext) {
        // 创建规则
        val ruleId = UUID.randomUUID().toString()
        val rule = EnhancementRule(
            id = ruleId,
            name = "Test Rule",
            description = "A test rule",
            type = RuleType.APPEND,
            pattern = "",
            replacement = "\n\nThank you!",
            priority = 5,
            requestTypes = listOf("chat"),
            models = listOf("gpt-3.5-turbo"),
            enabled = true
        )

        // 添加规则
        promptEnhancer.addRule(rule).onComplete { ar ->
            if (ar.succeeded()) {
                // 验证规则已添加
                val rules = promptEnhancer.getRules()
                testContext.verify {
                    assertTrue(rules.any { it.id == ruleId })
                }

                // 删除规则
                promptEnhancer.removeRule(ruleId).onComplete { removeAr ->
                    if (removeAr.succeeded()) {
                        val removed = removeAr.result()
                        testContext.verify {
                            assertTrue(removed)
                            assertTrue(promptEnhancer.getRules().none { it.id == ruleId })
                        }

                        testContext.completeNow()
                    } else {
                        testContext.failNow(removeAr.cause())
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test clear rules`(testContext: VertxTestContext) {
        // 创建规则
        val rule1 = EnhancementRule(
            id = UUID.randomUUID().toString(),
            name = "Rule 1",
            description = "First rule",
            type = RuleType.PREPEND,
            pattern = "",
            replacement = "Prefix: ",
            priority = 10,
            requestTypes = emptyList(),
            models = emptyList(),
            enabled = true
        )

        val rule2 = EnhancementRule(
            id = UUID.randomUUID().toString(),
            name = "Rule 2",
            description = "Second rule",
            type = RuleType.APPEND,
            pattern = "",
            replacement = " :Suffix",
            priority = 5,
            requestTypes = emptyList(),
            models = emptyList(),
            enabled = true
        )

        // 添加规则
        promptEnhancer.addRule(rule1).onComplete { ar1 ->
            if (ar1.succeeded()) {
                promptEnhancer.addRule(rule2).onComplete { ar2 ->
                    if (ar2.succeeded()) {
                        // 验证规则已添加
                        val rules = promptEnhancer.getRules()
                        testContext.verify {
                            assertEquals(2, rules.size)
                        }

                        // 清空规则
                        promptEnhancer.clearRules().onComplete { clearAr ->
                            if (clearAr.succeeded()) {
                                testContext.verify {
                                    assertTrue(promptEnhancer.getRules().isEmpty())
                                }

                                testContext.completeNow()
                            } else {
                                testContext.failNow(clearAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(ar2.cause())
                    }
                }
            } else {
                testContext.failNow(ar1.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test enhance prompt with rules`(testContext: VertxTestContext) {
        // 创建规则
        val prependRule = EnhancementRule(
            id = UUID.randomUUID().toString(),
            name = "Prepend Rule",
            description = "Add system instruction",
            type = RuleType.PREPEND,
            pattern = "",
            replacement = "You are a helpful assistant.\n\n",
            priority = 10,
            requestTypes = emptyList(),
            models = emptyList(),
            enabled = true
        )

        val appendRule = EnhancementRule(
            id = UUID.randomUUID().toString(),
            name = "Append Rule",
            description = "Add closing",
            type = RuleType.APPEND,
            pattern = "",
            replacement = "\n\nPlease provide a detailed response.",
            priority = 5,
            requestTypes = emptyList(),
            models = emptyList(),
            enabled = true
        )

        // 添加规则
        promptEnhancer.addRule(prependRule).onComplete { ar1 ->
            if (ar1.succeeded()) {
                promptEnhancer.addRule(appendRule).onComplete { ar2 ->
                    if (ar2.succeeded()) {
                        // 创建请求
                        val request = JsonObject()
                            .put("prompt", "What is the capital of France?")
                            .put("type", "completion")
                            .put("model", "gpt-4")

                        // 增强提示词
                        promptEnhancer.enhancePrompt(request).onComplete { enhanceAr ->
                            if (enhanceAr.succeeded()) {
                                val enhancedRequest = enhanceAr.result()
                                testContext.verify {
                                    assertNotNull(enhancedRequest)
                                    assertEquals(
                                        "You are a helpful assistant.\n\nWhat is the capital of France?\n\nPlease provide a detailed response.",
                                        enhancedRequest.getString("prompt")
                                    )
                                    assertTrue(enhancedRequest.getBoolean("enhanced", false))
                                }

                                testContext.completeNow()
                            } else {
                                testContext.failNow(enhanceAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(ar2.cause())
                    }
                }
            } else {
                testContext.failNow(ar1.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test enhance prompt with template`(testContext: VertxTestContext) {
        // 创建模板
        val templateId = UUID.randomUUID().toString()
        val template = PromptTemplate(
            id = templateId,
            name = "Test Template",
            description = "A test template",
            template = "System: {{system}}\n\nUser: {{content}}\n\nAssistant:",
            variables = listOf("system", "content"),
            category = "test",
            tags = listOf("test")
        )

        // 添加模板
        promptEnhancer.addTemplate(template).onComplete { ar ->
            if (ar.succeeded()) {
                // 创建请求
                val request = JsonObject()
                    .put("prompt", "What is the capital of France?")
                    .put("type", "chat")
                    .put("model", "gpt-3.5-turbo")
                    .put("templateId", templateId)
                    .put("variables", JsonObject()
                        .put("system", "You are a helpful assistant specialized in geography.")
                    )

                // 增强提示词
                promptEnhancer.enhancePrompt(request).onComplete { enhanceAr ->
                    if (enhanceAr.succeeded()) {
                        val enhancedRequest = enhanceAr.result()
                        testContext.verify {
                            assertNotNull(enhancedRequest)
                            assertEquals(
                                "System: You are a helpful assistant specialized in geography.\n\nUser: What is the capital of France?\n\nAssistant:",
                                enhancedRequest.getString("prompt")
                            )
                            assertTrue(enhancedRequest.getBoolean("enhanced", false))
                        }

                        testContext.completeNow()
                    } else {
                        testContext.failNow(enhanceAr.cause())
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test rule matching and application`(testContext: VertxTestContext) {
        // 创建规则
        val replaceRule = EnhancementRule(
            id = UUID.randomUUID().toString(),
            name = "Replace Rule",
            description = "Replace 'France' with 'France (Europe)'",
            type = RuleType.REPLACE,
            pattern = "France",
            replacement = "France (Europe)",
            priority = 10,
            requestTypes = listOf("completion"),
            models = listOf("gpt-4"),
            enabled = true
        )

        val regexRule = EnhancementRule(
            id = UUID.randomUUID().toString(),
            name = "Regex Rule",
            description = "Replace questions with formatted questions",
            type = RuleType.REGEX_REPLACE,
            pattern = "What is the (.*?)\\?",
            replacement = "QUESTION: What is the $1?",
            priority = 5,
            requestTypes = emptyList(),
            models = emptyList(),
            enabled = true
        )

        // 添加规则
        promptEnhancer.addRule(replaceRule).onComplete { ar1 ->
            if (ar1.succeeded()) {
                promptEnhancer.addRule(regexRule).onComplete { ar2 ->
                    if (ar2.succeeded()) {
                        // 创建请求
                        val request = JsonObject()
                            .put("prompt", "What is the capital of France?")
                            .put("type", "completion")
                            .put("model", "gpt-4")

                        // 增强提示词
                        promptEnhancer.enhancePrompt(request).onComplete { enhanceAr ->
                            if (enhanceAr.succeeded()) {
                                val enhancedRequest = enhanceAr.result()
                                testContext.verify {
                                    assertNotNull(enhancedRequest)
                                    assertEquals(
                                        "QUESTION: What is the capital of France (Europe)?",
                                        enhancedRequest.getString("prompt")
                                    )
                                    assertTrue(enhancedRequest.getBoolean("enhanced", false))
                                }

                                testContext.completeNow()
                            } else {
                                testContext.failNow(enhanceAr.cause())
                            }
                        }
                    } else {
                        testContext.failNow(ar2.cause())
                    }
                }
            } else {
                testContext.failNow(ar1.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }

    @Test
    fun `test transform rule`(testContext: VertxTestContext) {
        // 创建规则
        val transformRule = EnhancementRule(
            id = UUID.randomUUID().toString(),
            name = "Transform Rule",
            description = "Transform prompt into a structured format",
            type = RuleType.TRANSFORM,
            pattern = "",
            replacement = "{\n  \"query\": \"{{content}}\",\n  \"format\": \"detailed\",\n  \"style\": \"academic\"\n}",
            priority = 10,
            requestTypes = emptyList(),
            models = emptyList(),
            enabled = true
        )

        // 添加规则
        promptEnhancer.addRule(transformRule).onComplete { ar ->
            if (ar.succeeded()) {
                // 创建请求
                val request = JsonObject()
                    .put("prompt", "What is the capital of France?")
                    .put("type", "completion")
                    .put("model", "gpt-4")

                // 增强提示词
                promptEnhancer.enhancePrompt(request).onComplete { enhanceAr ->
                    if (enhanceAr.succeeded()) {
                        val enhancedRequest = enhanceAr.result()
                        testContext.verify {
                            assertNotNull(enhancedRequest)
                            assertEquals(
                                "{\n  \"query\": \"What is the capital of France?\",\n  \"format\": \"detailed\",\n  \"style\": \"academic\"\n}",
                                enhancedRequest.getString("prompt")
                            )
                            assertTrue(enhancedRequest.getBoolean("enhanced", false))
                        }

                        testContext.completeNow()
                    } else {
                        testContext.failNow(enhanceAr.cause())
                    }
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        assert(testContext.awaitCompletion(5, TimeUnit.SECONDS))
    }
}
