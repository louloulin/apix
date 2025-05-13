package com.louloulin.apix.cache.strategy

import io.vertx.core.json.JsonObject

/**
 * 用户特定的缓存策略，根据用户的特性配置缓存策略
 */
class UserSpecificCacheStrategy(
    private val userTtls: Map<String, Long> = emptyMap(), // 用户 ID 到过期时间的映射
    private val userGroupTtls: Map<String, Long> = emptyMap(), // 用户组到过期时间的映射
    private val defaultTtl: Long = 3600 // 默认过期时间（秒）
) : CacheStrategy {
    
    override fun calculateTtl(key: String, value: JsonObject): Long {
        // 从缓存值中提取用户信息
        val userId = extractUserId(value)
        val userGroup = extractUserGroup(value)
        
        // 根据用户 ID 查找匹配的过期时间
        if (userId != null && userTtls.containsKey(userId)) {
            return userTtls[userId] ?: defaultTtl
        }
        
        // 根据用户组查找匹配的过期时间
        if (userGroup != null && userGroupTtls.containsKey(userGroup)) {
            return userGroupTtls[userGroup] ?: defaultTtl
        }
        
        // 如果没有匹配的用户或用户组，返回默认过期时间
        return defaultTtl
    }
    
    override fun shouldCache(key: String, value: JsonObject): Boolean {
        // 从缓存值中提取用户信息
        val userId = extractUserId(value)
        val userGroup = extractUserGroup(value)
        
        // 如果用户 ID 存在于配置中，则应该被缓存
        if (userId != null && userTtls.containsKey(userId)) {
            return true
        }
        
        // 如果用户组存在于配置中，则应该被缓存
        if (userGroup != null && userGroupTtls.containsKey(userGroup)) {
            return true
        }
        
        // 默认所有内容都应该被缓存
        return true
    }
    
    override fun getName(): String {
        return "UserSpecific"
    }
    
    /**
     * 从缓存值中提取用户 ID
     * @param value 缓存值
     * @return 用户 ID，如果不存在则返回 null
     */
    private fun extractUserId(value: JsonObject): String? {
        // 尝试从不同的字段中提取用户 ID
        return value.getString("userId") ?:
               value.getJsonObject("metadata")?.getString("userId") ?:
               value.getJsonObject("request")?.getString("userId") ?:
               value.getJsonObject("user")?.getString("id")
    }
    
    /**
     * 从缓存值中提取用户组
     * @param value 缓存值
     * @return 用户组，如果不存在则返回 null
     */
    private fun extractUserGroup(value: JsonObject): String? {
        // 尝试从不同的字段中提取用户组
        return value.getString("userGroup") ?:
               value.getJsonObject("metadata")?.getString("userGroup") ?:
               value.getJsonObject("request")?.getString("userGroup") ?:
               value.getJsonObject("user")?.getString("group")
    }
}
