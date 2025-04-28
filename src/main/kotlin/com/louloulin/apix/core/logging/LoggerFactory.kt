package com.louloulin.apix.core.logging

/**
 * 日志工厂类
 * 
 * 该类提供了创建结构化日志处理器的工厂方法。
 */
object LoggerFactory {
    /**
     * 获取结构化日志处理器
     */
    fun getLogger(name: String): StructuredLogger {
        return StructuredLogger.getLogger(name)
    }
    
    /**
     * 获取结构化日志处理器
     */
    fun getLogger(clazz: Class<*>): StructuredLogger {
        return StructuredLogger.getLogger(clazz)
    }
    
    /**
     * 获取结构化日志处理器
     */
    inline fun <reified T> getLogger(): StructuredLogger {
        return StructuredLogger.getLogger(T::class.java)
    }
}
