package com.louloulin.apix.core.common

/**
 * 定义系统常量
 */
object Constants {
    // 会话相关
    const val SESSION_ID_HEADER = "X-APIX-Session-ID"
    const val SESSION_TIMEOUT_SECONDS = 1800 // 30分钟
    
    // 请求转发相关
    const val FORWARDED_HEADER = "X-APIX-Forwarded"
    const val FORWARDED_FOR_HEADER = "X-APIX-Forwarded-For"
    const val FORWARDED_HOST_HEADER = "X-APIX-Forwarded-Host"
    const val FORWARDED_PROTO_HEADER = "X-APIX-Forwarded-Proto"
    
    // 负载均衡相关
    const val LOAD_BALANCER_STRATEGY_HEADER = "X-APIX-LB-Strategy"
    const val LOAD_BALANCER_NODE_HEADER = "X-APIX-LB-Node"
    const val LOAD_BALANCER_GROUP_HEADER = "X-APIX-LB-Group"
    
    // 会话亲和性相关
    const val AFFINITY_ENABLED_HEADER = "X-APIX-Affinity-Enabled"
    const val AFFINITY_STRATEGY_HEADER = "X-APIX-Affinity-Strategy"
    
    // 请求追踪相关
    const val TRACE_ID_HEADER = "X-APIX-Trace-ID"
    const val SPAN_ID_HEADER = "X-APIX-Span-ID"
    const val PARENT_SPAN_ID_HEADER = "X-APIX-Parent-Span-ID"
    
    // 缓存控制相关
    const val CACHE_CONTROL_HEADER = "X-APIX-Cache-Control"
    const val CACHE_TTL_HEADER = "X-APIX-Cache-TTL"
    
    // 安全相关
    const val API_KEY_HEADER = "X-APIX-API-Key"
    const val AUTH_TOKEN_HEADER = "X-APIX-Auth-Token"
    
    // 限流相关
    const val RATE_LIMIT_HEADER = "X-APIX-Rate-Limit"
    const val RATE_LIMIT_REMAINING_HEADER = "X-APIX-Rate-Limit-Remaining"
    const val RATE_LIMIT_RESET_HEADER = "X-APIX-Rate-Limit-Reset"
    
    // 版本控制相关
    const val API_VERSION_HEADER = "X-APIX-API-Version"
    
    // 内容协商相关
    const val ACCEPT_VERSION_HEADER = "X-APIX-Accept-Version"
    const val CONTENT_TYPE_JSON = "application/json"
    const val CONTENT_TYPE_XML = "application/xml"
    const val CONTENT_TYPE_TEXT = "text/plain"
    
    // 错误处理相关
    const val ERROR_CODE_HEADER = "X-APIX-Error-Code"
    const val ERROR_MESSAGE_HEADER = "X-APIX-Error-Message"
    
    // 性能监控相关
    const val RESPONSE_TIME_HEADER = "X-APIX-Response-Time"
    const val SERVER_TIMING_HEADER = "Server-Timing"
    
    // 系统相关
    const val DEFAULT_CHARSET = "UTF-8"
    const val DEFAULT_BUFFER_SIZE = 8192
    const val DEFAULT_TIMEOUT_MS = 30000 // 30秒
    
    // 文件路径相关
    const val CONFIG_DIR = "config"
    const val PLUGINS_DIR = "plugins"
    const val TEMP_DIR = "temp"
    const val LOGS_DIR = "logs"
    
    // 事件类型
    const val EVENT_TYPE_REQUEST = "request"
    const val EVENT_TYPE_RESPONSE = "response"
    const val EVENT_TYPE_ERROR = "error"
    const val EVENT_TYPE_SYSTEM = "system"
    
    // 状态码
    const val STATUS_OK = 200
    const val STATUS_CREATED = 201
    const val STATUS_ACCEPTED = 202
    const val STATUS_NO_CONTENT = 204
    const val STATUS_BAD_REQUEST = 400
    const val STATUS_UNAUTHORIZED = 401
    const val STATUS_FORBIDDEN = 403
    const val STATUS_NOT_FOUND = 404
    const val STATUS_METHOD_NOT_ALLOWED = 405
    const val STATUS_CONFLICT = 409
    const val STATUS_TOO_MANY_REQUESTS = 429
    const val STATUS_INTERNAL_SERVER_ERROR = 500
    const val STATUS_SERVICE_UNAVAILABLE = 503
    const val STATUS_GATEWAY_TIMEOUT = 504
}
