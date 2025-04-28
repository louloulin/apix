package com.louloulin.apix.models

/**
 * 表示 API Key 的模型类
 */
data class ApiKey(
    val id: String,
    val key: String,
    val name: String,
    val scopes: List<String>,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = 0 // 0 表示永不过期
)
