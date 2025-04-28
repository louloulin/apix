package com.louloulin.apix.plugins.validation

import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * 请求参数验证插件
 * 
 * 该插件用于验证请求参数是否符合预定义的规则，支持以下验证：
 * - 必填参数检查
 * - 参数类型检查
 * - 参数格式检查（正则表达式）
 * - 参数范围检查（最小值、最大值）
 * - 参数长度检查（最小长度、最大长度）
 * - 参数枚举值检查
 * 
 * 配置参数：
 * - parameters: 参数验证规则列表
 *   - name: 参数名称
 *   - in: 参数位置，可选值为 "path", "query", "header", "body"
 *   - required: 是否必填，默认为 false
 *   - type: 参数类型，可选值为 "string", "number", "integer", "boolean", "array", "object"
 *   - format: 参数格式，使用正则表达式
 *   - minimum: 最小值（数字类型）
 *   - maximum: 最大值（数字类型）
 *   - minLength: 最小长度（字符串类型）
 *   - maxLength: 最大长度（字符串类型）
 *   - enum: 枚举值列表
 *   - properties: 对象属性验证规则（对象类型）
 *   - items: 数组元素验证规则（数组类型）
 * - failureStatusCode: 验证失败时返回的状态码，默认为 400
 */
class RequestValidatorPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(RequestValidatorPlugin::class.java)
    override val type: String = "requestValidator"
    
    // 参数验证规则列表
    private val parameters: List<ParameterRule>
    
    // 验证失败时返回的状态码
    private val failureStatusCode: Int
    
    init {
        // 解析参数验证规则
        val parametersArray = config.config.getJsonArray("parameters", JsonArray())
        parameters = parametersArray.mapNotNull { item ->
            if (item is JsonObject) {
                parseParameterRule(item)
            } else {
                null
            }
        }
        
        // 解析验证失败时返回的状态码
        failureStatusCode = config.config.getInteger("failureStatusCode", 400)
        
        logger.info("Initialized request validator plugin with {} parameter rules", parameters.size)
    }
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 验证请求参数
            val validationResult = validateRequest(context)
            
            if (validationResult.valid) {
                // 验证通过，继续处理请求
                context.next()
                promise.complete()
            } else {
                // 验证失败，返回错误信息
                context.response()
                    .setStatusCode(failureStatusCode)
                    .putHeader("Content-Type", "application/json")
                    .end(JsonObject()
                        .put("error", "Validation failed")
                        .put("message", validationResult.message)
                        .put("parameter", validationResult.parameter)
                        .encode()
                    )
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error executing request validator plugin", e)
            // 发生错误时，继续处理请求
            context.next()
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 验证请求参数
     */
    private fun validateRequest(context: RoutingContext): ValidationResult {
        // 验证所有参数规则
        for (rule in parameters) {
            val result = validateParameter(context, rule)
            if (!result.valid) {
                return result
            }
        }
        
        // 所有参数验证通过
        return ValidationResult(true)
    }
    
    /**
     * 验证单个参数
     */
    private fun validateParameter(context: RoutingContext, rule: ParameterRule): ValidationResult {
        // 根据参数位置获取参数值
        val value = when (rule.location) {
            ParameterLocation.PATH -> context.pathParam(rule.name)
            ParameterLocation.QUERY -> context.request().getParam(rule.name)
            ParameterLocation.HEADER -> context.request().getHeader(rule.name)
            ParameterLocation.BODY -> {
                if (rule.name.contains(".")) {
                    // 支持嵌套属性，如 "user.name"
                    getNestedProperty(context.body().asJsonObject(), rule.name)
                } else {
                    context.body().asJsonObject()?.getValue(rule.name)?.toString()
                }
            }
        }
        
        // 检查必填参数
        if (rule.required && value == null) {
            return ValidationResult(
                valid = false,
                message = "Required parameter is missing",
                parameter = rule.name
            )
        }
        
        // 如果参数值为空且不是必填，则跳过后续验证
        if (value == null) {
            return ValidationResult(true)
        }
        
        // 验证参数类型
        val typeResult = validateType(value, rule)
        if (!typeResult.valid) {
            return typeResult
        }
        
        // 验证参数格式
        if (rule.format != null) {
            if (!value.matches(Regex(rule.format))) {
                return ValidationResult(
                    valid = false,
                    message = "Parameter format is invalid",
                    parameter = rule.name
                )
            }
        }
        
        // 验证数字范围
        if (rule.type == ParameterType.NUMBER || rule.type == ParameterType.INTEGER) {
            try {
                val numValue = value.toDouble()
                
                if (rule.minimum != null && numValue < rule.minimum) {
                    return ValidationResult(
                        valid = false,
                        message = "Parameter value is less than minimum (${rule.minimum})",
                        parameter = rule.name
                    )
                }
                
                if (rule.maximum != null && numValue > rule.maximum) {
                    return ValidationResult(
                        valid = false,
                        message = "Parameter value is greater than maximum (${rule.maximum})",
                        parameter = rule.name
                    )
                }
            } catch (e: NumberFormatException) {
                return ValidationResult(
                    valid = false,
                    message = "Parameter is not a valid number",
                    parameter = rule.name
                )
            }
        }
        
        // 验证字符串长度
        if (rule.type == ParameterType.STRING) {
            if (rule.minLength != null && value.length < rule.minLength) {
                return ValidationResult(
                    valid = false,
                    message = "Parameter length is less than minimum length (${rule.minLength})",
                    parameter = rule.name
                )
            }
            
            if (rule.maxLength != null && value.length > rule.maxLength) {
                return ValidationResult(
                    valid = false,
                    message = "Parameter length is greater than maximum length (${rule.maxLength})",
                    parameter = rule.name
                )
            }
        }
        
        // 验证枚举值
        if (rule.enum != null && !rule.enum.contains(value)) {
            return ValidationResult(
                valid = false,
                message = "Parameter value is not in allowed values (${rule.enum.joinToString(", ")})",
                parameter = rule.name
            )
        }
        
        // 验证对象属性
        if (rule.type == ParameterType.OBJECT && rule.properties != null) {
            try {
                val jsonObject = JsonObject(value)
                
                for (propertyRule in rule.properties) {
                    val propertyValue = jsonObject.getValue(propertyRule.name)?.toString()
                    
                    // 检查必填属性
                    if (propertyRule.required && propertyValue == null) {
                        return ValidationResult(
                            valid = false,
                            message = "Required property is missing",
                            parameter = "${rule.name}.${propertyRule.name}"
                        )
                    }
                    
                    // 如果属性值为空且不是必填，则跳过后续验证
                    if (propertyValue == null) {
                        continue
                    }
                    
                    // 验证属性类型
                    val propertyTypeResult = validateType(propertyValue, propertyRule)
                    if (!propertyTypeResult.valid) {
                        return ValidationResult(
                            valid = false,
                            message = propertyTypeResult.message,
                            parameter = "${rule.name}.${propertyRule.name}"
                        )
                    }
                }
            } catch (e: Exception) {
                return ValidationResult(
                    valid = false,
                    message = "Parameter is not a valid JSON object",
                    parameter = rule.name
                )
            }
        }
        
        // 验证数组元素
        if (rule.type == ParameterType.ARRAY && rule.items != null) {
            try {
                val jsonArray = JsonArray(value)
                
                for (i in 0 until jsonArray.size()) {
                    val itemValue = jsonArray.getValue(i)?.toString()
                    
                    // 如果元素值为空，则跳过后续验证
                    if (itemValue == null) {
                        continue
                    }
                    
                    // 验证元素类型
                    val itemTypeResult = validateType(itemValue, rule.items)
                    if (!itemTypeResult.valid) {
                        return ValidationResult(
                            valid = false,
                            message = itemTypeResult.message,
                            parameter = "${rule.name}[$i]"
                        )
                    }
                }
            } catch (e: Exception) {
                return ValidationResult(
                    valid = false,
                    message = "Parameter is not a valid JSON array",
                    parameter = rule.name
                )
            }
        }
        
        // 所有验证通过
        return ValidationResult(true)
    }
    
    /**
     * 验证参数类型
     */
    private fun validateType(value: String, rule: ParameterRule): ValidationResult {
        when (rule.type) {
            ParameterType.STRING -> {
                // 字符串类型不需要特殊验证
                return ValidationResult(true)
            }
            ParameterType.NUMBER -> {
                try {
                    value.toDouble()
                    return ValidationResult(true)
                } catch (e: NumberFormatException) {
                    return ValidationResult(
                        valid = false,
                        message = "Parameter is not a valid number",
                        parameter = rule.name
                    )
                }
            }
            ParameterType.INTEGER -> {
                try {
                    value.toInt()
                    return ValidationResult(true)
                } catch (e: NumberFormatException) {
                    return ValidationResult(
                        valid = false,
                        message = "Parameter is not a valid integer",
                        parameter = rule.name
                    )
                }
            }
            ParameterType.BOOLEAN -> {
                if (value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)) {
                    return ValidationResult(true)
                } else {
                    return ValidationResult(
                        valid = false,
                        message = "Parameter is not a valid boolean",
                        parameter = rule.name
                    )
                }
            }
            ParameterType.ARRAY -> {
                try {
                    JsonArray(value)
                    return ValidationResult(true)
                } catch (e: Exception) {
                    return ValidationResult(
                        valid = false,
                        message = "Parameter is not a valid JSON array",
                        parameter = rule.name
                    )
                }
            }
            ParameterType.OBJECT -> {
                try {
                    JsonObject(value)
                    return ValidationResult(true)
                } catch (e: Exception) {
                    return ValidationResult(
                        valid = false,
                        message = "Parameter is not a valid JSON object",
                        parameter = rule.name
                    )
                }
            }
        }
    }
    
    /**
     * 获取嵌套属性值
     */
    private fun getNestedProperty(jsonObject: JsonObject?, path: String): String? {
        if (jsonObject == null) {
            return null
        }
        
        val parts = path.split(".")
        var current: Any? = jsonObject
        
        for (part in parts) {
            if (current is JsonObject) {
                current = current.getValue(part)
            } else {
                return null
            }
        }
        
        return current?.toString()
    }
    
    /**
     * 解析参数验证规则
     */
    private fun parseParameterRule(json: JsonObject): ParameterRule {
        val name = json.getString("name") ?: throw IllegalArgumentException("Parameter name is required")
        
        val locationStr = json.getString("in", "query")
        val location = when (locationStr.lowercase()) {
            "path" -> ParameterLocation.PATH
            "header" -> ParameterLocation.HEADER
            "body" -> ParameterLocation.BODY
            else -> ParameterLocation.QUERY
        }
        
        val typeStr = json.getString("type", "string")
        val type = when (typeStr.lowercase()) {
            "number" -> ParameterType.NUMBER
            "integer" -> ParameterType.INTEGER
            "boolean" -> ParameterType.BOOLEAN
            "array" -> ParameterType.ARRAY
            "object" -> ParameterType.OBJECT
            else -> ParameterType.STRING
        }
        
        val required = json.getBoolean("required", false)
        val format = json.getString("format")
        val minimum = if (json.containsKey("minimum")) json.getDouble("minimum") else null
        val maximum = if (json.containsKey("maximum")) json.getDouble("maximum") else null
        val minLength = if (json.containsKey("minLength")) json.getInteger("minLength") else null
        val maxLength = if (json.containsKey("maxLength")) json.getInteger("maxLength") else null
        
        val enumValues = if (json.containsKey("enum")) {
            json.getJsonArray("enum").map { it.toString() }
        } else {
            null
        }
        
        val properties = if (json.containsKey("properties")) {
            json.getJsonObject("properties").map { entry ->
                val propertyJson = entry.value as JsonObject
                propertyJson.put("name", entry.key)
                parseParameterRule(propertyJson)
            }
        } else {
            null
        }
        
        val items = if (json.containsKey("items")) {
            val itemsJson = json.getJsonObject("items")
            parseParameterRule(itemsJson.put("name", "${name}Item"))
        } else {
            null
        }
        
        return ParameterRule(
            name = name,
            location = location,
            type = type,
            required = required,
            format = format,
            minimum = minimum,
            maximum = maximum,
            minLength = minLength,
            maxLength = maxLength,
            enum = enumValues,
            properties = properties,
            items = items
        )
    }
    
    override fun shutdown() {
        // 无需释放资源
    }
    
    /**
     * 参数位置枚举
     */
    enum class ParameterLocation {
        PATH,    // 路径参数
        QUERY,   // 查询参数
        HEADER,  // 请求头
        BODY     // 请求体
    }
    
    /**
     * 参数类型枚举
     */
    enum class ParameterType {
        STRING,   // 字符串
        NUMBER,   // 数字
        INTEGER,  // 整数
        BOOLEAN,  // 布尔值
        ARRAY,    // 数组
        OBJECT    // 对象
    }
    
    /**
     * 参数验证规则
     */
    data class ParameterRule(
        val name: String,
        val location: ParameterLocation,
        val type: ParameterType,
        val required: Boolean,
        val format: String? = null,
        val minimum: Double? = null,
        val maximum: Double? = null,
        val minLength: Int? = null,
        val maxLength: Int? = null,
        val enum: List<String>? = null,
        val properties: List<ParameterRule>? = null,
        val items: ParameterRule? = null
    )
    
    /**
     * 验证结果
     */
    data class ValidationResult(
        val valid: Boolean,
        val message: String? = null,
        val parameter: String? = null
    )
    
    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return RequestValidatorPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
