package com.louloulin.apix.plugins.condition

import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * 条件表达式
 * 用于判断插件是否应该执行
 */
interface ConditionExpression {
    /**
     * 评估条件表达式
     *
     * @param context 路由上下文
     * @return 条件是否满足
     */
    fun evaluate(context: RoutingContext): Boolean
}

/**
 * 路径条件表达式
 * 用于判断请求路径是否匹配
 */
class PathCondition(private val pattern: String) : ConditionExpression {
    private val logger = LoggerFactory.getLogger(PathCondition::class.java)

    private val regex: Pattern = if (pattern.contains("*")) {
        // 将通配符转换为正则表达式
        val regexPattern = pattern.replace(".", "\\.").replace("*", ".*")
        Pattern.compile(regexPattern)
    } else {
        // 精确匹配
        Pattern.compile("^" + Pattern.quote(pattern) + "$")
    }

    override fun evaluate(context: RoutingContext): Boolean {
        val path = context.request().path()
        val matches = regex.matcher(path).matches()
        logger.debug("Path condition: {} against {}, matches: {}", pattern, path, matches)
        return matches
    }
}

/**
 * 方法条件表达式
 * 用于判断请求方法是否匹配
 */
class MethodCondition(private val methods: List<HttpMethod>) : ConditionExpression {
    private val logger = LoggerFactory.getLogger(MethodCondition::class.java)

    override fun evaluate(context: RoutingContext): Boolean {
        val method = context.request().method()
        val matches = methods.contains(method)
        logger.debug("Method condition: {} against {}, matches: {}", methods, method, matches)
        return matches
    }
}

/**
 * 头信息条件表达式
 * 用于判断请求头是否匹配
 */
class HeaderCondition(private val name: String, private val value: String) : ConditionExpression {
    private val logger = LoggerFactory.getLogger(HeaderCondition::class.java)

    override fun evaluate(context: RoutingContext): Boolean {
        val headerValue = context.request().getHeader(name)
        val matches = headerValue == value
        logger.debug("Header condition: {}={} against {}, matches: {}", name, value, headerValue, matches)
        return matches
    }
}

/**
 * 参数条件表达式
 * 用于判断请求参数是否匹配
 */
class ParamCondition(private val name: String, private val value: String) : ConditionExpression {
    private val logger = LoggerFactory.getLogger(ParamCondition::class.java)

    override fun evaluate(context: RoutingContext): Boolean {
        val paramValue = context.request().getParam(name)
        val matches = paramValue == value
        logger.debug("Param condition: {}={} against {}, matches: {}", name, value, paramValue, matches)
        return matches
    }
}

/**
 * 复合条件表达式
 * 用于组合多个条件表达式
 */
class CompositeCondition(
    private val conditions: List<ConditionExpression>,
    private val operator: Operator
) : ConditionExpression {
    private val logger = LoggerFactory.getLogger(CompositeCondition::class.java)

    enum class Operator {
        AND, OR
    }

    override fun evaluate(context: RoutingContext): Boolean {
        val result = when (operator) {
            Operator.AND -> conditions.all { it.evaluate(context) }
            Operator.OR -> conditions.any { it.evaluate(context) }
        }
        logger.debug("Composite condition with operator {}, result: {}", operator, result)
        return result
    }
}

/**
 * 条件表达式解析器
 * 用于从配置中解析条件表达式
 */
object ConditionExpressionParser {
    private val logger = LoggerFactory.getLogger(ConditionExpressionParser::class.java)

    /**
     * 解析条件表达式
     *
     * @param config 条件配置
     * @return 条件表达式
     */
    fun parse(config: JsonObject): ConditionExpression {
        logger.debug("Parsing condition expression from config: {}", config)

        val conditions = mutableListOf<ConditionExpression>()

        // 解析路径条件
        if (config.containsKey("path")) {
            val path = config.getString("path")
            conditions.add(PathCondition(path))
        }

        // 解析方法条件
        if (config.containsKey("method")) {
            val methodsJson = config.getValue("method")
            val methods = when (methodsJson) {
                is String -> listOf(HttpMethod.valueOf(methodsJson))
                is JsonArray -> methodsJson.map { HttpMethod.valueOf(it.toString()) }
                else -> emptyList()
            }
            if (methods.isNotEmpty()) {
                conditions.add(MethodCondition(methods))
            }
        }

        // 解析头信息条件
        if (config.containsKey("headers")) {
            val headers = config.getJsonObject("headers")
            headers.forEach { entry ->
                conditions.add(HeaderCondition(entry.key, entry.value.toString()))
            }
        }

        // 解析参数条件
        if (config.containsKey("params")) {
            val params = config.getJsonObject("params")
            params.forEach { entry ->
                conditions.add(ParamCondition(entry.key, entry.value.toString()))
            }
        }

        // 如果只有一个条件，直接返回
        if (conditions.size == 1) {
            return conditions.first()
        }

        // 否则创建复合条件
        val operator = if (config.getString("operator", "AND").equals("OR", ignoreCase = true)) {
            CompositeCondition.Operator.OR
        } else {
            CompositeCondition.Operator.AND
        }

        return CompositeCondition(conditions, operator)
    }
}
