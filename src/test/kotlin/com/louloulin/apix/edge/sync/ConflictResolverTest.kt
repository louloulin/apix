package com.louloulin.apix.edge.sync

import io.vertx.core.json.JsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConflictResolverTest {

    private val conflictResolver = ConflictResolver()

    @Test
    fun `test detect conflicts with no conflicts`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")

        // 创建合并后的数据（无冲突）
        val mergedData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("key3", "value3")

        // 检测冲突
        val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

        // 验证冲突
        assertEquals(0, conflicts.size)
    }

    @Test
    fun `test detect conflicts with simple conflicts`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")

        // 创建合并后的数据（有冲突）
        val mergedData = JsonObject()
            .put("key1", "conflict value")
            .put("key2", "value2")

        // 检测冲突
        val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

        // 验证冲突
        assertEquals(1, conflicts.size)
        assertEquals("key1", conflicts[0].path)
        assertEquals("value1", conflicts[0].baseValue)
        assertEquals("conflict value", conflicts[0].mergedValue)
    }

    @Test
    fun `test detect conflicts with nested conflicts`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "nested value 2")
            )

        // 创建合并后的数据（有嵌套冲突）
        val mergedData = JsonObject()
            .put("key1", "value1")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "conflict nested value")
            )

        // 检测冲突
        val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

        // 验证冲突
        assertEquals(1, conflicts.size)
        assertEquals("nested.nested2", conflicts[0].path)
        assertEquals("nested value 2", conflicts[0].baseValue)
        assertEquals("conflict nested value", conflicts[0].mergedValue)
    }

    @Test
    fun `test detect conflicts with multiple conflicts`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "nested value 2")
            )

        // 创建合并后的数据（有多个冲突）
        val mergedData = JsonObject()
            .put("key1", "conflict value")
            .put("key2", "value2")
            .put("nested", JsonObject()
                .put("nested1", "conflict nested value 1")
                .put("nested2", "conflict nested value 2")
            )

        // 检测冲突
        val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

        // 验证冲突
        assertEquals(3, conflicts.size)
        
        // 找到 key1 的冲突
        val key1Conflict = conflicts.find { it.path == "key1" }
        assertNotNull(key1Conflict)
        assertEquals("value1", key1Conflict?.baseValue)
        assertEquals("conflict value", key1Conflict?.mergedValue)
        
        // 找到 nested.nested1 的冲突
        val nested1Conflict = conflicts.find { it.path == "nested.nested1" }
        assertNotNull(nested1Conflict)
        assertEquals("nested value 1", nested1Conflict?.baseValue)
        assertEquals("conflict nested value 1", nested1Conflict?.mergedValue)
        
        // 找到 nested.nested2 的冲突
        val nested2Conflict = conflicts.find { it.path == "nested.nested2" }
        assertNotNull(nested2Conflict)
        assertEquals("nested value 2", nested2Conflict?.baseValue)
        assertEquals("conflict nested value 2", nested2Conflict?.mergedValue)
    }

    @Test
    fun `test resolve conflicts with simple conflicts`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")

        // 创建合并后的数据（有冲突）
        val mergedData = JsonObject()
            .put("key1", "conflict value")
            .put("key2", "value2")

        // 检测冲突
        val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

        // 解决冲突
        val resolvedData = conflictResolver.resolveConflicts(baseData, mergedData, conflicts)

        // 验证解决后的数据
        assertEquals(2, resolvedData.fieldNames().size)
        assertEquals("conflict value", resolvedData.getString("key1"))
        assertEquals("value2", resolvedData.getString("key2"))
    }

    @Test
    fun `test resolve conflicts with nested conflicts`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "nested value 2")
            )

        // 创建合并后的数据（有嵌套冲突）
        val mergedData = JsonObject()
            .put("key1", "value1")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "conflict nested value")
            )

        // 检测冲突
        val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

        // 解决冲突
        val resolvedData = conflictResolver.resolveConflicts(baseData, mergedData, conflicts)

        // 验证解决后的数据
        assertEquals(2, resolvedData.fieldNames().size)
        assertEquals("value1", resolvedData.getString("key1"))
        
        val nested = resolvedData.getJsonObject("nested")
        assertEquals(2, nested.fieldNames().size)
        assertEquals("nested value 1", nested.getString("nested1"))
        assertEquals("conflict nested value", nested.getString("nested2"))
    }

    @Test
    fun `test resolve conflicts with multiple conflicts`() {
        // 创建基础数据
        val baseData = JsonObject()
            .put("key1", "value1")
            .put("key2", "value2")
            .put("nested", JsonObject()
                .put("nested1", "nested value 1")
                .put("nested2", "nested value 2")
            )

        // 创建合并后的数据（有多个冲突）
        val mergedData = JsonObject()
            .put("key1", "conflict value")
            .put("key2", "value2")
            .put("nested", JsonObject()
                .put("nested1", "conflict nested value 1")
                .put("nested2", "conflict nested value 2")
            )

        // 检测冲突
        val conflicts = conflictResolver.detectConflicts(baseData, mergedData)

        // 解决冲突
        val resolvedData = conflictResolver.resolveConflicts(baseData, mergedData, conflicts)

        // 验证解决后的数据
        assertEquals(3, resolvedData.fieldNames().size)
        assertEquals("conflict value", resolvedData.getString("key1"))
        assertEquals("value2", resolvedData.getString("key2"))
        
        val nested = resolvedData.getJsonObject("nested")
        assertEquals(2, nested.fieldNames().size)
        assertEquals("conflict nested value 1", nested.getString("nested1"))
        assertEquals("conflict nested value 2", nested.getString("nested2"))
    }
}
