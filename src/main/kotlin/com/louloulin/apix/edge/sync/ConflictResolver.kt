package com.louloulin.apix.edge.sync

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 冲突解决器，用于检测和解决数据同步过程中的冲突。
 */
class ConflictResolver {
    private val logger = LoggerFactory.getLogger(ConflictResolver::class.java)

    /**
     * 检测冲突。
     *
     * @param baseData 基础数据
     * @param mergedData 合并后的数据
     * @return List<Conflict> 冲突列表
     */
    fun detectConflicts(baseData: JsonObject, mergedData: JsonObject): List<Conflict> {
        logger.debug("检测冲突")

        val conflicts = mutableListOf<Conflict>()

        // 检查所有字段是否有冲突
        for (key in mergedData.fieldNames()) {
            if (baseData.containsKey(key)) {
                val baseValue = baseData.getValue(key)
                val mergedValue = mergedData.getValue(key)

                // 如果是JsonObject类型，递归检查冲突
                if (baseValue is JsonObject && mergedValue is JsonObject) {
                    val nestedConflicts = detectNestedConflicts(key, baseValue, mergedValue)
                    conflicts.addAll(nestedConflicts)
                } else if (isConflict(baseValue, mergedValue)) {
                    // 添加冲突
                    conflicts.add(Conflict(key, baseValue, mergedValue))
                }
            }
        }

        logger.debug("检测到 ${conflicts.size} 个冲突")

        return conflicts
    }

    /**
     * 检测嵌套对象中的冲突。
     *
     * @param path 路径
     * @param baseData 基础数据
     * @param mergedData 合并后的数据
     * @return List<Conflict> 冲突列表
     */
    private fun detectNestedConflicts(path: String, baseData: JsonObject, mergedData: JsonObject): List<Conflict> {
        val conflicts = mutableListOf<Conflict>()

        // 检查所有字段是否有冲突
        for (key in mergedData.fieldNames()) {
            val fullPath = "$path.$key"

            if (baseData.containsKey(key)) {
                val baseValue = baseData.getValue(key)
                val mergedValue = mergedData.getValue(key)

                // 如果是JsonObject类型，递归检查冲突
                if (baseValue is JsonObject && mergedValue is JsonObject) {
                    val nestedConflicts = detectNestedConflicts(fullPath, baseValue, mergedValue)
                    conflicts.addAll(nestedConflicts)
                } else if (isConflict(baseValue, mergedValue)) {
                    // 添加冲突
                    conflicts.add(Conflict(fullPath, baseValue, mergedValue))
                }
            }
        }

        return conflicts
    }

    /**
     * 判断两个值是否冲突。
     *
     * @param baseValue 基础值
     * @param mergedValue 合并后的值
     * @return 是否冲突
     */
    private fun isConflict(baseValue: Any?, mergedValue: Any?): Boolean {
        // 在这个简单实现中，我们只检查值是否不同
        // 在实际应用中，可能需要更复杂的冲突检测逻辑
        return baseValue != mergedValue
    }

    /**
     * 解决冲突。
     *
     * @param baseData 基础数据
     * @param mergedData 合并后的数据
     * @param conflicts 冲突列表
     * @return JsonObject 解决冲突后的数据
     */
    fun resolveConflicts(baseData: JsonObject, mergedData: JsonObject, conflicts: List<Conflict>): JsonObject {
        logger.debug("解决 ${conflicts.size} 个冲突")

        // 创建合并数据的副本
        val resolvedData = mergedData.copy()

        // 解决每个冲突
        for (conflict in conflicts) {
            resolveConflict(resolvedData, conflict)
        }

        return resolvedData
    }

    /**
     * 解决单个冲突。
     *
     * @param data 数据
     * @param conflict 冲突
     */
    private fun resolveConflict(data: JsonObject, conflict: Conflict) {
        logger.debug("解决冲突: ${conflict.path}")

        // 解析路径
        val pathParts = conflict.path.split(".")

        // 如果是顶级字段
        if (pathParts.size == 1) {
            // 在这个简单实现中，我们总是选择合并后的值
            // 在实际应用中，可能需要更复杂的冲突解决策略
            // 例如，根据字段类型、优先级等选择不同的解决方案
            data.put(pathParts[0], conflict.mergedValue)
            return
        }

        // 如果是嵌套字段
        var current = data
        for (i in 0 until pathParts.size - 1) {
            val part = pathParts[i]

            if (!current.containsKey(part) || current.getValue(part) !is JsonObject) {
                // 如果路径不存在或不是JsonObject，创建一个新的
                current.put(part, JsonObject())
            }

            current = current.getJsonObject(part)
        }

        // 设置最后一个字段的值
        current.put(pathParts.last(), conflict.mergedValue)
    }

    /**
     * 冲突类，表示一个数据冲突。
     *
     * @param path 冲突路径
     * @param baseValue 基础值
     * @param mergedValue 合并后的值
     */
    data class Conflict(
        val path: String,
        val baseValue: Any?,
        val mergedValue: Any?
    )
}
