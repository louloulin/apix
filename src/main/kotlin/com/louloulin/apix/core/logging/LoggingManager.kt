package com.louloulin.apix.core.logging

import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import com.louloulin.apix.core.util.RuntimeMetrics
import java.util.concurrent.ConcurrentHashMap

/**
 * 日志管理器，用于动态调整日志级别和格式。
 * 提供了API来控制日志输出，优化性能。
 */
class LoggingManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(LoggingManager::class.java)

    // 默认日志格式
    private val defaultPattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

    // 性能优化的日志格式（减少时间戳精度和线程信息）
    private val performancePattern = "%d{yyyy-MM-dd HH:mm:ss} %-5level %logger{20} - %msg%n"

    // 详细的日志格式（包含更多上下文信息）
    private val verbosePattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %X{requestId} - %msg%n"

    // 日志级别缓存
    private val loggerLevels = ConcurrentHashMap<String, Level>()

    init {
        // 注册EventBus处理器
        registerEventBusHandlers()

        logger.info("日志管理器初始化完成")
    }

    /**
     * 注册EventBus处理器
     */
    private fun registerEventBusHandlers() {
        // 设置日志级别
        vertx.eventBus().consumer<JsonObject>("logging.setLevel") { message ->
            val body = message.body()
            val loggerName = body.getString("logger")
            val levelName = body.getString("level")

            if (loggerName != null && levelName != null) {
                try {
                    val level = Level.valueOf(levelName.uppercase())
                    setLoggerLevel(loggerName, level)
                    message.reply(JsonObject()
                        .put("success", true)
                        .put("message", "日志级别已设置: $loggerName -> $levelName")
                    )
                } catch (e: Exception) {
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", "无效的日志级别: $levelName")
                    )
                }
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "缺少必要参数: logger 或 level")
                )
            }
        }

        // 获取日志级别
        vertx.eventBus().consumer<JsonObject>("logging.getLevel") { message ->
            val body = message.body()
            val loggerName = body.getString("logger")

            if (loggerName != null) {
                val level = getLoggerLevel(loggerName)
                message.reply(JsonObject()
                    .put("success", true)
                    .put("logger", loggerName)
                    .put("level", level.toString())
                )
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "缺少必要参数: logger")
                )
            }
        }

        // 获取所有日志级别
        vertx.eventBus().consumer<JsonObject>("logging.getAllLevels") { message ->
            val levels = JsonObject()

            val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
            loggerContext.loggerList.forEach { logger ->
                if (logger.level != null) {
                    levels.put(logger.name, logger.level.toString())
                }
            }

            message.reply(JsonObject()
                .put("success", true)
                .put("levels", levels)
            )
        }

        // 设置日志格式
        vertx.eventBus().consumer<JsonObject>("logging.setPattern") { message ->
            val body = message.body()
            val pattern = body.getString("pattern")
            val type = body.getString("type", "default")

            if (pattern != null) {
                setLogPattern(pattern)
                message.reply(JsonObject()
                    .put("success", true)
                    .put("message", "日志格式已设置")
                )
            } else if (type != null) {
                when (type) {
                    "performance" -> setLogPattern(performancePattern)
                    "verbose" -> setLogPattern(verbosePattern)
                    else -> setLogPattern(defaultPattern)
                }

                message.reply(JsonObject()
                    .put("success", true)
                    .put("message", "日志格式已设置为: $type")
                )
            } else {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "缺少必要参数: pattern 或 type")
                )
            }
        }
    }

    /**
     * 设置日志级别
     *
     * @param loggerName 日志记录器名称
     * @param level 日志级别
     */
    fun setLoggerLevel(loggerName: String, level: Level) {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = loggerContext.getLogger(loggerName)
        logger.level = level

        // 缓存日志级别
        loggerLevels[loggerName] = level

        this.logger.info("日志级别已设置: {} -> {}", loggerName, level)
    }

    /**
     * 获取日志级别
     *
     * @param loggerName 日志记录器名称
     * @return 日志级别
     */
    fun getLoggerLevel(loggerName: String): Level {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val logger = loggerContext.getLogger(loggerName)

        return logger.level ?: Level.INFO
    }

    /**
     * 设置日志格式
     *
     * @param pattern 日志格式模式
     */
    fun setLogPattern(pattern: String) {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext

        // 更新控制台输出格式
        loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).iteratorForAppenders().forEach { appender ->
            if (appender is ConsoleAppender<*>) {
                val encoder = appender.encoder as? PatternLayoutEncoder
                if (encoder != null) {
                    encoder.pattern = pattern
                    encoder.context = loggerContext
                    encoder.start()
                }
            }
        }

        logger.info("日志格式已更新")
    }

    /**
     * 配置文件日志
     *
     * @param filePath 日志文件路径
     * @param pattern 日志格式模式
     * @param maxHistory 保留的日志文件数量
     */
    fun configureFileLogging(filePath: String, pattern: String = defaultPattern, maxHistory: Int = 7) {
        val loggerContext = LoggerFactory.getILoggerFactory() as LoggerContext
        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

        // 创建文件附加器
        val fileAppender = RollingFileAppender<ILoggingEvent>()
        fileAppender.context = loggerContext
        fileAppender.name = "FILE"
        fileAppender.file = filePath

        // 创建滚动策略
        val rollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>()
        rollingPolicy.context = loggerContext
        rollingPolicy.fileNamePattern = "$filePath.%d{yyyy-MM-dd}"
        rollingPolicy.maxHistory = maxHistory
        rollingPolicy.setParent(fileAppender)
        rollingPolicy.start()

        fileAppender.rollingPolicy = rollingPolicy

        // 创建编码器
        val encoder = PatternLayoutEncoder()
        encoder.context = loggerContext
        encoder.pattern = pattern
        encoder.start()

        fileAppender.encoder = encoder
        fileAppender.start()

        // 添加到根日志记录器
        rootLogger.addAppender(fileAppender)

        logger.info("文件日志已配置: {}", filePath)
    }

    /**
     * 优化日志配置，根据系统负载动态调整日志级别
     *
     * @param highLoadThreshold 高负载阈值
     */
    fun optimizeLoggingForLoad(highLoadThreshold: Double = 0.7) {
        // 定期检查系统负载
        vertx.setPeriodic(60000) { _ ->
            val systemLoad = getSystemLoad()

            if (systemLoad > highLoadThreshold) {
                // 高负载情况下，降低日志级别
                setLoggerLevel("com.louloulin.apix", Level.WARN)
                setLogPattern(performancePattern)
                logger.info("系统负载较高 ({}), 已降低日志级别", systemLoad)
            } else {
                // 正常负载情况下，恢复默认日志级别
                setLoggerLevel("com.louloulin.apix", Level.INFO)
                setLogPattern(defaultPattern)
            }
        }
    }

    /**
     * 获取系统负载
     *
     * @return 系统负载（0-1之间的值）
     */
    private fun getSystemLoad(): Double {
        val osInfo = RuntimeMetrics.getOperatingSystemInfo()
        return osInfo.systemLoadAverage / osInfo.availableProcessors
    }

    companion object {
        // 单例实例
        private var INSTANCE: LoggingManager? = null

        /**
         * 获取LoggingManager的单例实例
         *
         * @param vertx Vertx实例
         * @return LoggingManager实例
         */
        fun getInstance(vertx: Vertx): LoggingManager {
            if (INSTANCE == null) {
                synchronized(LoggingManager::class.java) {
                    if (INSTANCE == null) {
                        INSTANCE = LoggingManager(vertx)
                    }
                }
            }
            return INSTANCE!!
        }
    }
}
