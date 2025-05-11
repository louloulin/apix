package com.louloulin.apix.edge.sync

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 数据差异计算器，用于计算两个JSON对象之间的差异，并应用差异。
 */
class DataDiffCalculator {
    private val logger = LoggerFactory.getLogger(DataDiffCalculator::class.java)
    
    /**
     * 计算两个JSON对象之间的差异。
     *
     * @param baseData 基础数据
     * @param targetData 目标数据
     * @return JsonObject 差异数据
     */
    fun calculateDiff(baseData: JsonObject, targetData: JsonObject): JsonObject {
        logger.debug("计算差异: baseData=${baseData.encode().length}字节, targetData=${targetData.encode().length}字节")
        
        val diff = JsonObject()
        
        // 添加操作
        val added = JsonObject()
        // 修改操作
        val modified = JsonObject()
        // 删除操作
        val deleted = JsonArray()
        
        // 处理目标数据中的所有键
        for (key in targetData.fieldNames()) {
            if (!baseData.containsKey(key)) {
                // 添加操作
                added.put(key, targetData.getValue(key))
            } else {
                val baseValue = baseData.getValue(key)
                val targetValue = targetData.getValue(key)
                
                if (!equals(baseValue, targetValue)) {
                    // 修改操作
                    modified.put(key, targetValue)
                }
            }
        }
        
        // 处理基础数据中存在但目标数据中不存在的键
        for (key in baseData.fieldNames()) {
            if (!targetData.containsKey(key)) {
                // 删除操作
                deleted.add(key)
            }
        }
        
        // 构建差异对象
        diff.put("added", added)
        diff.put("modified", modified)
        diff.put("deleted", deleted)
        
        logger.debug("差异计算完成: 添加=${added.fieldNames().size}, 修改=${modified.fieldNames().size}, 删除=${deleted.size()}")
        
        return diff
    }
    
    /**
     * 应用差异到基础数据。
     *
     * @param baseData 基础数据
     * @param diff 差异数据
     * @return JsonObject 合并后的数据
     */
    fun applyDiff(baseData: JsonObject, diff: JsonObject): JsonObject {
        logger.debug("应用差异: baseData=${baseData.encode().length}字节, diff=${diff.encode().length}字节")
        
        // 创建基础数据的副本
        val result = baseData.copy()
        
        // 应用添加操作
        val added = diff.getJsonObject("added", JsonObject())
        for (key in added.fieldNames()) {
            result.put(key, added.getValue(key))
        }
        
        // 应用修改操作
        val modified = diff.getJsonObject("modified", JsonObject())
        for (key in modified.fieldNames()) {
            result.put(key, modified.getValue(key))
        }
        
        // 应用删除操作
        val deleted = diff.getJsonArray("deleted", JsonArray())
        for (i in 0 until deleted.size()) {
            val key = deleted.getString(i)
            result.remove(key)
        }
        
        logger.debug("差异应用完成: 结果=${result.encode().length}字节")
        
        return result
    }
    
    /**
     * 比较两个值是否相等。
     *
     * @param value1 值1
     * @param value2 值2
     * @return 是否相等
     */
    private fun equals(value1: Any?, value2: Any?): Boolean {
        if (value1 == null && value2 == null) {
            return true
        }
        
        if (value1 == null || value2 == null) {
            return false
        }
        
        // 处理JsonObject
        if (value1 is JsonObject && value2 is JsonObject) {
            if (value1.fieldNames().size != value2.fieldNames().size) {
                return false
            }
            
            for (key in value1.fieldNames()) {
                if (!value2.containsKey(key) || !equals(value1.getValue(key), value2.getValue(key))) {
                    return false
                }
            }
            
            return true
        }
        
        // 处理JsonArray
        if (value1 is JsonArray && value2 is JsonArray) {
            if (value1.size() != value2.size()) {
                return false
            }
            
            for (i in 0 until value1.size()) {
                if (!equals(value1.getValue(i), value2.getValue(i))) {
                    return false
                }
            }
            
            return true
        }
        
        // 处理其他类型
        return value1 == value2
    }
}
