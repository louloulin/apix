package com.louloulin.apix.plugins

/**
 * 定义插件类型和优先级
 */
enum class PluginType(val priority: Int) {
    // 认证和安全类插件（最高优先级）
    AUTHENTICATION(10),
    SECURITY(20),
    
    // 请求处理类插件
    VALIDATION(30),
    TRANSFORMATION(40),
    
    // 业务逻辑类插件
    BUSINESS_LOGIC(50),
    
    // AI 特定类插件
    AI_PROCESSING(60),
    
    // 缓存类插件
    CACHING(70),
    
    // 日志和监控类插件（最低优先级）
    LOGGING(80),
    MONITORING(90),
    
    // 默认类型
    OTHER(100);
    
    companion object {
        /**
         * 根据插件类型字符串获取对应的 PluginType
         */
        fun fromString(type: String): PluginType {
            return when (type.lowercase()) {
                "authentication", "auth", "apikey", "jwt", "basic" -> AUTHENTICATION
                "security", "ratelimit", "ipfilter", "csrf", "signature" -> SECURITY
                "validation", "validator", "request-validator", "prompt-validator" -> VALIDATION
                "transformation", "transform", "request-transformer", "response-transformer" -> TRANSFORMATION
                "business", "logic", "business-logic" -> BUSINESS_LOGIC
                "ai", "ai-processing", "prompt", "completion" -> AI_PROCESSING
                "cache", "caching", "response-cache" -> CACHING
                "log", "logging", "request-logger", "structured-logger" -> LOGGING
                "monitor", "monitoring", "metrics" -> MONITORING
                else -> OTHER
            }
        }
    }
}
