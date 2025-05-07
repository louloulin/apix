package com.louloulin.apix.plugins.version

import io.vertx.core.json.JsonObject

/**
 * 插件版本
 * 用于管理插件的版本信息
 */
data class PluginVersion(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<PluginVersion> {
    /**
     * 比较版本
     */
    override fun compareTo(other: PluginVersion): Int {
        // 先比较主版本号
        val majorCompare = major.compareTo(other.major)
        if (majorCompare != 0) {
            return majorCompare
        }
        
        // 再比较次版本号
        val minorCompare = minor.compareTo(other.minor)
        if (minorCompare != 0) {
            return minorCompare
        }
        
        // 最后比较补丁版本号
        return patch.compareTo(other.patch)
    }
    
    /**
     * 检查是否兼容
     * 
     * @param other 其他版本
     * @return 是否兼容
     */
    fun isCompatibleWith(other: PluginVersion): Boolean {
        // 主版本号必须相同
        if (major != other.major) {
            return false
        }
        
        // 次版本号必须大于等于
        if (minor < other.minor) {
            return false
        }
        
        // 如果次版本号相同，补丁版本号必须大于等于
        if (minor == other.minor && patch < other.patch) {
            return false
        }
        
        return true
    }
    
    /**
     * 转换为字符串
     */
    override fun toString(): String {
        return "$major.$minor.$patch"
    }
    
    /**
     * 转换为 JSON 对象
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("major", major)
            .put("minor", minor)
            .put("patch", patch)
    }
    
    companion object {
        /**
         * 从字符串解析版本
         * 
         * @param version 版本字符串，格式为 "major.minor.patch"
         * @return 版本对象
         */
        fun fromString(version: String): PluginVersion {
            val parts = version.split(".")
            if (parts.size != 3) {
                throw IllegalArgumentException("Invalid version format: $version")
            }
            
            return try {
                PluginVersion(
                    parts[0].toInt(),
                    parts[1].toInt(),
                    parts[2].toInt()
                )
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Invalid version format: $version", e)
            }
        }
        
        /**
         * 从 JSON 对象解析版本
         * 
         * @param json JSON 对象
         * @return 版本对象
         */
        fun fromJson(json: JsonObject): PluginVersion {
            return PluginVersion(
                json.getInteger("major", 0),
                json.getInteger("minor", 0),
                json.getInteger("patch", 0)
            )
        }
    }
}
