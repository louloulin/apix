package com.louloulin.apix.ai.router

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for RoutingRule.
 */
class RoutingRuleTest {

    @Test
    fun `test rule creation and json conversion`() {
        val condition = RuleCondition(ConditionType.CONTAINS, "test")
        val rule = RoutingRule("test-id", "Test Rule", 1, condition, "gpt-4")

        // Test properties
        assertEquals("test-id", rule.id)
        assertEquals("Test Rule", rule.name)
        assertEquals(1, rule.priority)
        assertEquals(ConditionType.CONTAINS, rule.condition.type)
        assertEquals("test", rule.condition.pattern)
        assertEquals("gpt-4", rule.targetModel)

        // Test toJson
        val json = rule.toJson()
        assertEquals("test-id", json.getString("id"))
        assertEquals("Test Rule", json.getString("name"))
        assertEquals(1, json.getInteger("priority"))
        assertEquals("gpt-4", json.getString("targetModel"))

        val conditionJson = json.getJsonObject("condition")
        assertEquals("CONTAINS", conditionJson.getString("type"))
        assertEquals("test", conditionJson.getString("pattern"))

        // Test fromJson
        val parsedRule = RoutingRule.fromJson(json)
        assertEquals("test-id", parsedRule.id)
        assertEquals("Test Rule", parsedRule.name)
        assertEquals(1, parsedRule.priority)
        assertEquals(ConditionType.CONTAINS, parsedRule.condition.type)
        assertEquals("test", parsedRule.condition.pattern)
        assertEquals("gpt-4", parsedRule.targetModel)
    }

    @Test
    fun `test rule matching`() {
        // Test CONTAINS condition
        val containsCondition = RuleCondition(ConditionType.CONTAINS, "test")
        val containsRule = RoutingRule("test-id", "Test Rule", 1, containsCondition, "gpt-4")

        assertTrue(containsRule.matches("This is a test message", "text", "completion"))
        assertFalse(containsRule.matches("This is a message", "text", "completion"))

        // Test STARTS_WITH condition
        val startsWithCondition = RuleCondition(ConditionType.STARTS_WITH, "hello")
        val startsWithRule = RoutingRule("test-id", "Test Rule", 1, startsWithCondition, "gpt-4")

        assertTrue(startsWithRule.matches("hello world", "text", "completion"))
        assertFalse(startsWithRule.matches("world hello", "text", "completion"))

        // Test ENDS_WITH condition
        val endsWithCondition = RuleCondition(ConditionType.ENDS_WITH, "world")
        val endsWithRule = RoutingRule("test-id", "Test Rule", 1, endsWithCondition, "gpt-4")

        assertTrue(endsWithRule.matches("hello world", "text", "completion"))
        assertFalse(endsWithRule.matches("world hello", "text", "completion"))

        // Test REGEX condition
        val regexCondition = RuleCondition(ConditionType.REGEX, "\\d{3}-\\d{2}-\\d{4}")
        val regexRule = RoutingRule("test-id", "Test Rule", 1, regexCondition, "gpt-4")

        assertTrue(regexRule.matches("SSN: 123-45-6789", "text", "completion"))
        assertFalse(regexRule.matches("No numbers here", "text", "completion"))

        // Test TOKEN_COUNT condition
        val tokenCountCondition = RuleCondition(ConditionType.TOKEN_COUNT, "8")
        val tokenCountRule = RoutingRule("test-id", "Test Rule", 1, tokenCountCondition, "gpt-4")

        // "This is a long message with many tokens" has 40 chars, which is about 10 tokens
        assertTrue(tokenCountRule.matches("This is a long message with many tokens", "text", "completion"))
        // "Short" has 5 chars, which is about 1 token
        assertFalse(tokenCountRule.matches("Short", "text", "completion"))

        // Test LANGUAGE condition
        val languageCondition = RuleCondition(ConditionType.LANGUAGE, "zh")
        val languageRule = RoutingRule("test-id", "Test Rule", 1, languageCondition, "gpt-4")

        assertTrue(languageRule.matches("这是一个中文测试", "text", "completion"))
        assertFalse(languageRule.matches("This is English", "text", "completion"))
    }

    @Test
    fun `test rule with content type and request type filters`() {
        // Create condition with content type and request type filters
        val condition = RuleCondition(
            type = ConditionType.CONTAINS,
            pattern = "test",
            contentTypes = listOf("text"),
            requestTypes = listOf("completion")
        )

        val rule = RoutingRule("test-id", "Test Rule", 1, condition, "gpt-4")

        // Test matching content type and request type
        assertTrue(rule.matches("This is a test", "text", "completion"))

        // Test non-matching content type
        assertFalse(rule.matches("This is a test", "image", "completion"))

        // Test non-matching request type
        assertFalse(rule.matches("This is a test", "text", "chat"))

        // Test non-matching content and pattern
        assertFalse(rule.matches("This is a message", "text", "completion"))
    }

    @Test
    fun `test rule from json with defaults`() {
        // Create minimal JSON
        val json = JsonObject()
            .put("targetModel", "gpt-4")

        // Parse rule
        val rule = RoutingRule.fromJson(json)

        // Test defaults
        assertTrue(rule.id.isNotEmpty()) // Should generate a random ID
        assertEquals("Unnamed Rule", rule.name)
        assertEquals(0, rule.priority)
        assertEquals(ConditionType.CONTAINS, rule.condition.type)
        assertEquals("", rule.condition.pattern)
        assertEquals("gpt-4", rule.targetModel)
    }
}
