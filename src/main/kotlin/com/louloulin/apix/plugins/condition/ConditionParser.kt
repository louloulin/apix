package com.louloulin.apix.plugins.condition

import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory
import java.util.regex.Pattern

/**
 * 条件表达式解析器
 * 用于解析插件执行条件
 */
class ConditionParser {
    private val logger = LoggerFactory.getLogger(ConditionParser::class.java)

    /**
     * 解析条件表达式
     *
     * @param condition 条件表达式
     * @param context 路由上下文
     * @return 条件是否满足
     */
    fun evaluate(condition: JsonObject, context: RoutingContext): Boolean {
        try {
            // 如果条件为空，默认为true
            if (condition.isEmpty) {
                return true
            }

            // 检查路径条件
            if (condition.containsKey("path")) {
                val pathCondition = condition.getValue("path")
                if (!evaluatePathCondition(pathCondition, context.request().path())) {
                    return false
                }
            }

            // 检查方法条件
            if (condition.containsKey("method")) {
                val methodCondition = condition.getValue("method")
                if (!evaluateMethodCondition(methodCondition, context.request().method().name())) {
                    return false
                }
            }

            // 检查头信息条件
            if (condition.containsKey("headers")) {
                val headersCondition = condition.getJsonObject("headers")
                if (!evaluateHeadersCondition(headersCondition, context.request().headers())) {
                    return false
                }
            }

            // 检查查询参数条件
            if (condition.containsKey("params")) {
                val paramsCondition = condition.getJsonObject("params")
                if (!evaluateParamsCondition(paramsCondition, context.request().params())) {
                    return false
                }
            }

            // 检查自定义条件
            if (condition.containsKey("custom")) {
                val customCondition = condition.getJsonObject("custom")
                if (!evaluateCustomCondition(customCondition, context)) {
                    return false
                }
            }

            // 所有条件都满足
            return true
        } catch (e: Exception) {
            logger.error("Error evaluating condition", e)
            return false
        }
    }

    /**
     * 解析路径条件
     *
     * @param condition 路径条件
     * @param path 请求路径
     * @return 条件是否满足
     */
    private fun evaluatePathCondition(condition: Any, path: String): Boolean {
        return when (condition) {
            is String -> {
                // 支持通配符匹配
                if (condition.contains("*")) {
                    val regex = condition.replace("*", ".*")
                    Pattern.compile(regex).matcher(path).matches()
                } else {
                    condition == path
                }
            }
            is JsonArray -> {
                // 任意一个路径匹配即可
                condition.any { evaluatePathCondition(it, path) }
            }
            else -> {
                logger.warn("Unsupported path condition type: {}", condition.javaClass.name)
                false
            }
        }
    }

    /**
     * 解析方法条件
     *
     * @param condition 方法条件
     * @param method 请求方法
     * @return 条件是否满足
     */
    private fun evaluateMethodCondition(condition: Any, method: String): Boolean {
        return when (condition) {
            is String -> {
                condition.equals(method, ignoreCase = true)
            }
            is JsonArray -> {
                // 任意一个方法匹配即可
                condition.any { it is String && it.toString().equals(method, ignoreCase = true) }
            }
            else -> {
                logger.warn("Unsupported method condition type: {}", condition.javaClass.name)
                false
            }
        }
    }

    /**
     * 解析头信息条件
     *
     * @param condition 头信息条件
     * @param headers 请求头
     * @return 条件是否满足
     */
    private fun evaluateHeadersCondition(condition: JsonObject, headers: io.vertx.core.MultiMap): Boolean {
        // 所有头信息条件都必须满足
        return condition.fieldNames().all { name ->
            val value = condition.getValue(name)
            val headerValue = headers.get(name)

            when (value) {
                is String -> {
                    headerValue != null && (value == headerValue || value == "*")
                }
                is JsonArray -> {
                    headerValue != null && value.any { it is String && it.toString() == headerValue }
                }
                else -> {
                    logger.warn("Unsupported header condition type: {}", value.javaClass.name)
                    false
                }
            }
        }
    }

    /**
     * 解析查询参数条件
     *
     * @param condition 查询参数条件
     * @param params 请求参数
     * @return 条件是否满足
     */
    private fun evaluateParamsCondition(condition: JsonObject, params: io.vertx.core.MultiMap): Boolean {
        // 所有参数条件都必须满足
        return condition.fieldNames().all { name ->
            val value = condition.getValue(name)
            val paramValue = params.get(name)

            when (value) {
                is String -> {
                    paramValue != null && (value == paramValue || value == "*")
                }
                is JsonArray -> {
                    paramValue != null && value.any { it is String && it.toString() == paramValue }
                }
                else -> {
                    logger.warn("Unsupported param condition type: {}", value.javaClass.name)
                    false
                }
            }
        }
    }

    /**
     * 解析自定义条件
     *
     * @param condition 自定义条件
     * @param context 路由上下文
     * @return 条件是否满足
     */
    private fun evaluateCustomCondition(condition: JsonObject, context: RoutingContext): Boolean {
        // 自定义条件可以根据需要扩展
        // 这里只是一个简单的示例
        if (condition.containsKey("expression")) {
            val expression = condition.getString("expression")
            // 这里可以实现一个简单的表达式解析器
            // 或者使用现有的表达式引擎
            return evaluateExpression(expression, context)
        }
        return true
    }

    /**
     * 解析表达式
     *
     * @param expression 表达式
     * @param context 路由上下文
     * @return 表达式结果
     */
    private fun evaluateExpression(expression: String, context: RoutingContext): Boolean {
        // 这里可以实现一个简单的表达式解析器
        // 或者使用现有的表达式引擎
        // 例如：${request.headers.contains('X-API-Key')}
        // 这里只是一个简单的示例
        if (expression.startsWith("${") && expression.endsWith("}")) {
            val expr = expression.substring(2, expression.length - 1)
            return when {
                expr.startsWith("request.headers.contains('") && expr.endsWith("')") -> {
                    val header = expr.substring(24, expr.length - 2)
                    context.request().headers().contains(header)
                }
                expr.startsWith("request.params.contains('") && expr.endsWith("')") -> {
                    val param = expr.substring(23, expr.length - 2)
                    context.request().params().contains(param)
                }
                else -> {
                    logger.warn("Unsupported expression: {}", expr)
                    false
                }
            }
        }
        return false
    }

    companion object {
        private val instance = ConditionParser()

        /**
         * 获取单例实例
         */
        fun getInstance(): ConditionParser {
            return instance
        }
    }
}
