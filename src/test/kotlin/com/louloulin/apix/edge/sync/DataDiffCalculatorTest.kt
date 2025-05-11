package com.louloulin.apix.edge.sync

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataDiffCalculatorTest {

    private val diffCalculator = DataDiffCalculator()

    @Test
    fun `test calculate diff with added fields`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")

        // 创建目标数据（有添加操作）
        val targetData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")
            .put("key4", "value4")

        // 计算差异
        val diff = diffCalculator.calculateDiff(baseData, targetData)

        // 验证差异
        val added = diff.getJsonObject("added")
        assertEquals(2, added.fieldNames().size)
        assertEquals("value3", added.getString("key3"))
        assertEquals("value4", added.getString("key4"))

        val modified = diff.getJsonObject("modified")
        assertEquals(0, modified.fieldNames().size)

        val deleted = diff.getJsonArray("deleted")
        assertEquals(0, deleted.size())
    }

    @Test
    fun `test calculate diff with modified fields`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")

        // 创建目标数据（有修改操作）
        val targetData = JsonObject()
            .put("key1", "value1")
            .put("key2", "modified value")
            .put("key3", "modified value 2")

        // 计算差异
        val diff = diffCalculator.calculateDiff(baseData, targetData)

        // 验证差异
        val added = diff.getJsonObject("added")
        assertEquals(0, added.fieldNames().size)

        val modified = diff.getJsonObject("modified")
        assertEquals(2, modified.fieldNames().size)
        assertEquals("modified value", modified.getString("key2"))
        assertEquals("modified value 2", modified.getString("key3"))

        val deleted = diff.getJsonArray("deleted")
        assertEquals(0, deleted.size())
    }

    @Test
    fun `test calculate diff with deleted fields`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")
            .put("key4", "value4")

        // 创建目标数据（有删除操作）
        val targetData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")

        // 计算差异
        val diff = diffCalculator.calculateDiff(baseData, targetData)

        // 验证差异
        val added = diff.getJsonObject("added")
        assertEquals(0, added.fieldNames().size)

        val modified = diff.getJsonObject("modified")
        assertEquals(0, modified.fieldNames().size)

        val deleted = diff.getJsonArray("deleted")
        assertEquals(2, deleted.size())
        assertTrue(deleted.contains("key3"))
        assertTrue(deleted.contains("key4"))
    }

    @Test
    fun `test calculate diff with complex changes`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")
            .put("key4", "value4")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "nested value 2")
            )

        // 创建目标数据（有添加、修改和删除操作）
        val targetData = JsonObject()
            .put("key1", "value1")
            .put("key2", "modified value")
            .put("key5", "new value")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "modified nested value")
                .put("nested3", "new nested value")
            )

        // 计算差异
        val diff = diffCalculator.calculateDiff(baseData, targetData)

        // 验证差异
        val added = diff.getJsonObject("added")
        assertEquals(1, added.fieldNames().size)
        assertEquals("new value", added.getString("key5"))

        val modified = diff.getJsonObject("modified")
        assertEquals(2, modified.fieldNames().size)
        assertEquals("modified value", modified.getString("key2"))
        assertTrue(modified.containsKey("nested"))

        val deleted = diff.getJsonArray("deleted")
        assertEquals(2, deleted.size())
        assertTrue(deleted.contains("key3"))
        assertTrue(deleted.contains("key4"))
    }

    @Test
    fun `test apply diff with added fields`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")

        // 创建差异
        val diff = JsonObject()
            .put("added", JsonObject()
                .put("key3", "value3")
                .put("key4", "value4")
            )
            .put("modified", JsonObject())
            .put("deleted", JsonArray())

        // 应用差异
        val result = diffCalculator.applyDiff(baseData, diff)

        // 验证结果
        assertEquals(4, result.fieldNames().size)
        assertEquals("value1", result.getString("key1"))
        assertEquals("value2", result.getString("key2"))
        assertEquals("value3", result.getString("key3"))
        assertEquals("value4", result.getString("key4"))
    }

    @Test
    fun `test apply diff with modified fields`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")

        // 创建差异
        val diff = JsonObject()
            .put("added", JsonObject())
            .put("modified", JsonObject()
                .put("key2", "modified value")
                .put("key3", "modified value 2")
            )
            .put("deleted", JsonArray())

        // 应用差异
        val result = diffCalculator.applyDiff(baseData, diff)

        // 验证结果
        assertEquals(3, result.fieldNames().size)
        assertEquals("value1", result.getString("key1"))
        assertEquals("modified value", result.getString("key2"))
        assertEquals("modified value 2", result.getString("key3"))
    }

    @Test
    fun `test apply diff with deleted fields`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")
            .put("key4", "value4")

        // 创建差异
        val diff = JsonObject()
            .put("added", JsonObject())
            .put("modified", JsonObject())
            .put("deleted", JsonArray().add("key3").add("key4"))

        // 应用差异
        val result = diffCalculator.applyDiff(baseData, diff)

        // 验证结果
        assertEquals(2, result.fieldNames().size)
        assertEquals("value1", result.getString("key1"))
        assertEquals("value2", result.getString("key2"))
        assertFalse(result.containsKey("key3"))
        assertFalse(result.containsKey("key4"))
    }

    @Test
    fun `test apply diff with complex changes`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")
            .put("key4", "value4")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "nested value 2")
            )

        // 创建差异
        val diff = JsonObject()
            .put("added", JsonObject()
                .put("key5", "new value")
            )
            .put("modified", JsonObject()
                .put("key2", "modified value")
                .put("nested", JsonObject()
                    .put("nested1", "nested value 1")
                    .put("nested2", "modified nested value")
                    .put("nested3", "new nested value")
                )
            )
            .put("deleted", JsonArray().add("key3").add("key4"))

        // 应用差异
        val result = diffCalculator.applyDiff(baseData, diff)

        // 验证结果
        assertEquals(3, result.fieldNames().size)
        assertEquals("value1", result.getString("key1"))
        assertEquals("modified value", result.getString("key2"))
        assertEquals("new value", result.getString("key5"))
        assertFalse(result.containsKey("key3"))
        assertFalse(result.containsKey("key4"))

        val nested = result.getJsonObject("nested")
        assertEquals(3, nested.fieldNames().size)
        assertEquals("nested value 1", nested.getString("nested1"))
        assertEquals("modified nested value", nested.getString("nested2"))
        assertEquals("new nested value", nested.getString("nested3"))
    }
}
