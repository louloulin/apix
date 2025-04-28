package com.louloulin.apix.core.logging

import io.vertx.core.json.JsonObject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 结构化日志处理器
 * 
 * 该类提供了结构化日志记录功能，将日志信息格式化为 JSON 格式，
 * 并添加上下文信息，如时间戳、请求 ID、用户 ID 等。
 */
class StructuredLogger(private val name: String) {
    private val logger: Logger = LoggerFactory.getLogger(name)
    private val dateTimeFormatter = DateTimeFormatter.ISO_INSTANT
    
    /**
     * 记录 INFO 级别的日志
     */
    fun info(message: String, vararg args: Any) {
        if (logger.isInfoEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("INFO", formattedMessage)
            logger.info(logEntry.encode())
        }
    }
    
    /**
     * 记录 INFO 级别的日志，带有额外字段
     */
    fun info(message: String, fields: Map<String, Any>, vararg args: Any) {
        if (logger.isInfoEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("INFO", formattedMessage, fields)
            logger.info(logEntry.encode())
        }
    }
    
    /**
     * 记录 DEBUG 级别的日志
     */
    fun debug(message: String, vararg args: Any) {
        if (logger.isDebugEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("DEBUG", formattedMessage)
            logger.debug(logEntry.encode())
        }
    }
    
    /**
     * 记录 DEBUG 级别的日志，带有额外字段
     */
    fun debug(message: String, fields: Map<String, Any>, vararg args: Any) {
        if (logger.isDebugEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("DEBUG", formattedMessage, fields)
            logger.debug(logEntry.encode())
        }
    }
    
    /**
     * 记录 WARN 级别的日志
     */
    fun warn(message: String, vararg args: Any) {
        if (logger.isWarnEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("WARN", formattedMessage)
            logger.warn(logEntry.encode())
        }
    }
    
    /**
     * 记录 WARN 级别的日志，带有额外字段
     */
    fun warn(message: String, fields: Map<String, Any>, vararg args: Any) {
        if (logger.isWarnEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("WARN", formattedMessage, fields)
            logger.warn(logEntry.encode())
        }
    }
    
    /**
     * 记录 ERROR 级别的日志
     */
    fun error(message: String, vararg args: Any) {
        if (logger.isErrorEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("ERROR", formattedMessage)
            logger.error(logEntry.encode())
        }
    }
    
    /**
     * 记录 ERROR 级别的日志，带有异常
     */
    fun error(message: String, throwable: Throwable, vararg args: Any) {
        if (logger.isErrorEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("ERROR", formattedMessage)
                .put("exception", throwable.javaClass.name)
                .put("exceptionMessage", throwable.message)
                .put("stackTrace", throwable.stackTraceToString())
            logger.error(logEntry.encode())
        }
    }
    
    /**
     * 记录 ERROR 级别的日志，带有额外字段
     */
    fun error(message: String, fields: Map<String, Any>, vararg args: Any) {
        if (logger.isErrorEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("ERROR", formattedMessage, fields)
            logger.error(logEntry.encode())
        }
    }
    
    /**
     * 记录 ERROR 级别的日志，带有异常和额外字段
     */
    fun error(message: String, throwable: Throwable, fields: Map<String, Any>, vararg args: Any) {
        if (logger.isErrorEnabled) {
            val formattedMessage = formatMessage(message, *args)
            val logEntry = createLogEntry("ERROR", formattedMessage, fields)
                .put("exception", throwable.javaClass.name)
                .put("exceptionMessage", throwable.message)
                .put("stackTrace", throwable.stackTraceToString())
            logger.error(logEntry.encode())
        }
    }
    
    /**
     * 格式化消息
     */
    private fun formatMessage(message: String, vararg args: Any): String {
        if (args.isEmpty()) {
            return message
        }
        
        return try {
            String.format(message, *args)
        } catch (e: Exception) {
            // 如果格式化失败，返回原始消息
            message
        }
    }
    
    /**
     * 创建日志条目
     */
    private fun createLogEntry(level: String, message: String, fields: Map<String, Any> = emptyMap()): JsonObject {
        val timestamp = Instant.now()
        val formattedTimestamp = dateTimeFormatter.format(timestamp)
        
        val logEntry = JsonObject()
            .put("timestamp", formattedTimestamp)
            .put("level", level)
            .put("logger", name)
            .put("message", message)
        
        // 添加 MDC 上下文
        MDC.getCopyOfContextMap()?.forEach { (key, value) ->
            logEntry.put(key, value)
        }
        
        // 添加额外字段
        fields.forEach { (key, value) ->
            logEntry.put(key, value)
        }
        
        return logEntry
    }
    
    companion object {
        /**
         * 获取 StructuredLogger 实例
         */
        fun getLogger(name: String): StructuredLogger {
            return StructuredLogger(name)
        }
        
        /**
         * 获取 StructuredLogger 实例
         */
        fun getLogger(clazz: Class<*>): StructuredLogger {
            return StructuredLogger(clazz.name)
        }
        
        /**
         * 设置 MDC 上下文
         */
        fun setContext(key: String, value: String) {
            MDC.put(key, value)
        }
        
        /**
         * 清除 MDC 上下文
         */
        fun clearContext(key: String) {
            MDC.remove(key)
        }
        
        /**
         * 清除所有 MDC 上下文
         */
        fun clearAllContext() {
            MDC.clear()
        }
    }
}
