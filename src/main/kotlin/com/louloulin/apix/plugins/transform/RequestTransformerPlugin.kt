package com.louloulin.apix.plugins.transform

import com.louloulin.apix.core.logging.LoggerFactory
import com.louloulin.apix.plugins.Plugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * 请求转换插件
 *
 * 该插件用于转换请求，支持以下功能：
 * - 添加、修改、删除请求头
 * - 添加、修改、删除查询参数
 * - 添加、修改、删除路径参数
 * - 转换请求体格式（JSON、XML、表单等）
 * - 支持条件转换（基于请求方法、路径、头部等）
 *
 * 配置参数：
 * - headers: 请求头转换规则
 *   - add: 添加的请求头
 *   - remove: 删除的请求头
 *   - rename: 重命名的请求头
 * - queryParams: 查询参数转换规则
 *   - add: 添加的查询参数
 *   - remove: 删除的查询参数
 *   - rename: 重命名的查询参数
 * - pathParams: 路径参数转换规则
 *   - add: 添加的路径参数
 *   - remove: 删除的路径参数
 *   - rename: 重命名的路径参数
 * - body: 请求体转换规则
 *   - type: 转换类型，可选值为 "json_to_xml", "xml_to_json", "json_to_form", "form_to_json"
 *   - template: 转换模板
 * - conditions: 条件转换规则
 *   - method: 请求方法
 *   - path: 请求路径
 *   - header: 请求头
 *   - param: 请求参数
 */
class RequestTransformerPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(RequestTransformerPlugin::class.java)
    override val type: String = "requestTransformer"

    // 请求头转换规则
    private val headerRules: TransformRules

    // 查询参数转换规则
    private val queryParamRules: TransformRules

    // 路径参数转换规则
    private val pathParamRules: TransformRules

    // 请求体转换规则
    private val bodyTransformType: BodyTransformType
    private val bodyTemplate: String?

    // 条件转换规则
    private val conditions: List<Condition>

    init {
        // 解析请求头转换规则
        val headersConfig = config.config.getJsonObject("headers", JsonObject())
        headerRules = parseTransformRules(headersConfig)

        // 解析查询参数转换规则
        val queryParamsConfig = config.config.getJsonObject("queryParams", JsonObject())
        queryParamRules = parseTransformRules(queryParamsConfig)

        // 解析路径参数转换规则
        val pathParamsConfig = config.config.getJsonObject("pathParams", JsonObject())
        pathParamRules = parseTransformRules(pathParamsConfig)

        // 解析请求体转换规则
        val bodyConfig = config.config.getJsonObject("body", JsonObject())
        val bodyTypeStr = bodyConfig.getString("type", "none")
        bodyTransformType = when (bodyTypeStr.lowercase()) {
            "json_to_xml" -> BodyTransformType.JSON_TO_XML
            "xml_to_json" -> BodyTransformType.XML_TO_JSON
            "json_to_form" -> BodyTransformType.JSON_TO_FORM
            "form_to_json" -> BodyTransformType.FORM_TO_JSON
            else -> BodyTransformType.NONE
        }
        bodyTemplate = bodyConfig.getString("template")

        // 解析条件转换规则
        val conditionsArray = config.config.getJsonArray("conditions", JsonArray())
        conditions = conditionsArray.mapNotNull { item ->
            if (item is JsonObject) {
                parseCondition(item)
            } else {
                null
            }
        }

        logger.info("Initialized request transformer plugin: headerRules={}, queryParamRules={}, pathParamRules={}, bodyTransformType={}",
            headerRules, queryParamRules, pathParamRules, bodyTransformType)
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 检查条件是否满足
            if (!checkConditions(context)) {
                // 条件不满足，跳过转换
                context.next()
                promise.complete()
                return promise.future()
            }

            // 转换请求头
            transformHeaders(context)

            // 转换查询参数
            transformQueryParams(context)

            // 转换路径参数
            transformPathParams(context)

            // 转换请求体
            if (bodyTransformType != BodyTransformType.NONE) {
                transformBody(context).onComplete { ar ->
                    if (ar.succeeded()) {
                        // 继续处理请求
                        context.next()
                        promise.complete()
                    } else {
                        // 转换失败，返回错误
                        logger.error("Failed to transform request body", ar.cause())
                        context.response()
                            .setStatusCode(400)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonObject()
                                .put("error", "Failed to transform request body")
                                .put("message", ar.cause().message)
                                .encode()
                            )
                        promise.complete()
                    }
                }
            } else {
                // 不需要转换请求体，继续处理请求
                context.next()
                promise.complete()
            }
        } catch (e: Exception) {
            logger.error("Error executing request transformer plugin", e)
            // 发生错误，返回 500 错误
            context.response()
                .setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("error", "Internal server error")
                    .put("message", e.message)
                    .encode()
                )
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 检查条件是否满足
     */
    private fun checkConditions(context: RoutingContext): Boolean {
        // 如果没有条件，则默认满足
        if (conditions.isEmpty()) {
            return true
        }

        // 检查所有条件
        return conditions.all { condition ->
            when (condition.type) {
                ConditionType.METHOD -> {
                    val method = context.request().method()
                    method.name() == condition.value
                }
                ConditionType.PATH -> {
                    val path = context.request().path()
                    Pattern.compile(condition.value).matcher(path).matches()
                }
                ConditionType.HEADER -> {
                    val header = context.request().getHeader(condition.name)
                    header != null && Pattern.compile(condition.value).matcher(header).matches()
                }
                ConditionType.PARAM -> {
                    val param = context.request().getParam(condition.name)
                    param != null && Pattern.compile(condition.value).matcher(param).matches()
                }
            }
        }
    }

    /**
     * 转换请求头
     */
    private fun transformHeaders(context: RoutingContext) {
        val request = context.request()

        // 添加请求头
        headerRules.add.forEach { (name, value) ->
            request.headers().add(name, value)
        }

        // 删除请求头
        headerRules.remove.forEach { name ->
            request.headers().remove(name)
        }

        // 重命名请求头
        headerRules.rename.forEach { (oldName, newName) ->
            val value = request.getHeader(oldName)
            if (value != null) {
                request.headers().remove(oldName)
                request.headers().add(newName, value)
            }
        }
    }

    /**
     * 转换查询参数
     */
    private fun transformQueryParams(context: RoutingContext) {
        val request = context.request()

        // 添加查询参数
        queryParamRules.add.forEach { (name, value) ->
            context.queryParams().add(name, value)
        }

        // 删除查询参数
        queryParamRules.remove.forEach { name ->
            context.queryParams().remove(name)
        }

        // 重命名查询参数
        queryParamRules.rename.forEach { (oldName, newName) ->
            val values = context.queryParams().getAll(oldName)
            if (values.isNotEmpty()) {
                context.queryParams().remove(oldName)
                values.forEach { value ->
                    context.queryParams().add(newName, value)
                }
            }
        }
    }

    /**
     * 转换路径参数
     */
    private fun transformPathParams(context: RoutingContext) {
        // 添加路径参数
        pathParamRules.add.forEach { (name, value) ->
            context.pathParams()[name] = value
        }

        // 删除路径参数
        pathParamRules.remove.forEach { name ->
            context.pathParams().remove(name)
        }

        // 重命名路径参数
        pathParamRules.rename.forEach { (oldName, newName) ->
            val value = context.pathParam(oldName)
            if (value != null) {
                context.pathParams().remove(oldName)
                context.pathParams()[newName] = value
            }
        }
    }

    /**
     * 转换请求体
     */
    private fun transformBody(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        // 获取请求体
        val body = context.body().buffer()
        if (body == null || body.length() == 0) {
            // 请求体为空，跳过转换
            promise.complete()
            return promise.future()
        }

        try {
            when (bodyTransformType) {
                BodyTransformType.JSON_TO_XML -> {
                    // JSON 转 XML
                    val jsonBody = body.toJsonObject()
                    val xmlBody = jsonToXml(jsonBody)

                    // 更新请求体和 Content-Type
                    context.setBody(Buffer.buffer(xmlBody))
                    context.request().headers().set("Content-Type", "application/xml")

                    promise.complete()
                }
                BodyTransformType.XML_TO_JSON -> {
                    // XML 转 JSON
                    val xmlBody = body.toString()
                    val jsonBody = xmlToJson(xmlBody)

                    // 更新请求体和 Content-Type
                    context.setBody(Buffer.buffer(jsonBody.encode()))
                    context.request().headers().set("Content-Type", "application/json")

                    promise.complete()
                }
                BodyTransformType.JSON_TO_FORM -> {
                    // JSON 转表单
                    val jsonBody = body.toJsonObject()
                    val formBody = jsonToForm(jsonBody)

                    // 更新请求体和 Content-Type
                    context.setBody(Buffer.buffer(formBody))
                    context.request().headers().set("Content-Type", "application/x-www-form-urlencoded")

                    promise.complete()
                }
                BodyTransformType.FORM_TO_JSON -> {
                    // 表单转 JSON
                    val formBody = body.toString()
                    val jsonBody = formToJson(formBody)

                    // 更新请求体和 Content-Type
                    context.setBody(Buffer.buffer(jsonBody.encode()))
                    context.request().headers().set("Content-Type", "application/json")

                    promise.complete()
                }
                BodyTransformType.NONE -> {
                    // 不需要转换
                    promise.complete()
                }
            }
        } catch (e: Exception) {
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * JSON 转 XML
     */
    private fun jsonToXml(json: JsonObject): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.append("<root>")

        jsonToXmlRecursive(json, sb, "")

        sb.append("</root>")
        return sb.toString()
    }

    /**
     * JSON 转 XML（递归）
     */
    private fun jsonToXmlRecursive(json: JsonObject, sb: StringBuilder, prefix: String) {
        json.forEach { (key, value) ->
            val tagName = if (prefix.isEmpty()) key else "$prefix.$key"

            when (value) {
                is JsonObject -> {
                    sb.append("<$tagName>")
                    jsonToXmlRecursive(value, sb, tagName)
                    sb.append("</$tagName>")
                }
                is JsonArray -> {
                    sb.append("<$tagName>")
                    value.forEach { item ->
                        sb.append("<item>")
                        when (item) {
                            is JsonObject -> jsonToXmlRecursive(item, sb, "")
                            else -> sb.append(item.toString())
                        }
                        sb.append("</item>")
                    }
                    sb.append("</$tagName>")
                }
                else -> {
                    sb.append("<$tagName>")
                    sb.append(value.toString())
                    sb.append("</$tagName>")
                }
            }
        }
    }

    /**
     * XML 转 JSON
     */
    private fun xmlToJson(xml: String): JsonObject {
        // 简化实现，实际应该使用 XML 解析库
        // 这里仅作为示例，不建议在生产环境中使用
        val json = JsonObject()

        // 提取标签和内容
        val pattern = Pattern.compile("<([^>]+)>([^<]+)</\\1>")
        val matcher = pattern.matcher(xml)

        while (matcher.find()) {
            val tagName = matcher.group(1)
            val content = matcher.group(2)

            // 尝试将内容解析为数字
            try {
                val number = content.toDouble()
                json.put(tagName, number)
            } catch (e: NumberFormatException) {
                // 不是数字，作为字符串处理
                json.put(tagName, content)
            }
        }

        return json
    }

    /**
     * JSON 转表单
     */
    private fun jsonToForm(json: JsonObject): String {
        val params = mutableListOf<String>()

        jsonToFormRecursive(json, "", params)

        return params.joinToString("&")
    }

    /**
     * JSON 转表单（递归）
     */
    private fun jsonToFormRecursive(json: JsonObject, prefix: String, params: MutableList<String>) {
        json.forEach { (key, value) ->
            val paramName = if (prefix.isEmpty()) key else "$prefix[$key]"

            when (value) {
                is JsonObject -> {
                    jsonToFormRecursive(value, paramName, params)
                }
                is JsonArray -> {
                    value.forEachIndexed { index, item ->
                        when (item) {
                            is JsonObject -> jsonToFormRecursive(item, "$paramName[$index]", params)
                            else -> params.add("$paramName[$index]=${urlEncode(item.toString())}")
                        }
                    }
                }
                else -> {
                    params.add("$paramName=${urlEncode(value.toString())}")
                }
            }
        }
    }

    /**
     * 表单转 JSON
     */
    private fun formToJson(form: String): JsonObject {
        val json = JsonObject()

        if (form.isEmpty()) {
            return json
        }

        val params = form.split("&")

        params.forEach { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) {
                val name = parts[0]
                val value = urlDecode(parts[1])

                // 处理嵌套参数
                if (name.contains("[") && name.contains("]")) {
                    val nestedParts = name.split("[", limit = 2)
                    val rootName = nestedParts[0]
                    val nestedName = nestedParts[1].removeSuffix("]")

                    var rootObj = json.getJsonObject(rootName)
                    if (rootObj == null) {
                        rootObj = JsonObject()
                        json.put(rootName, rootObj)
                    }

                    rootObj.put(nestedName, value)
                } else {
                    json.put(name, value)
                }
            }
        }

        return json
    }

    /**
     * URL 编码
     */
    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    }

    /**
     * URL 解码
     */
    private fun urlDecode(value: String): String {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    /**
     * 解析转换规则
     */
    private fun parseTransformRules(config: JsonObject): TransformRules {
        val add = mutableMapOf<String, String>()
        val remove = mutableListOf<String>()
        val rename = mutableMapOf<String, String>()

        // 解析添加规则
        val addConfig = config.getJsonObject("add", JsonObject())
        addConfig.forEach { (name, value) ->
            add[name] = value.toString()
        }

        // 解析删除规则
        val removeConfig = config.getJsonArray("remove", JsonArray())
        removeConfig.forEach { name ->
            remove.add(name.toString())
        }

        // 解析重命名规则
        val renameConfig = config.getJsonObject("rename", JsonObject())
        renameConfig.forEach { (oldName, newName) ->
            rename[oldName] = newName.toString()
        }

        return TransformRules(add, remove, rename)
    }

    /**
     * 解析条件
     */
    private fun parseCondition(config: JsonObject): Condition? {
        val type = when {
            config.containsKey("method") -> ConditionType.METHOD
            config.containsKey("path") -> ConditionType.PATH
            config.containsKey("header") -> ConditionType.HEADER
            config.containsKey("param") -> ConditionType.PARAM
            else -> return null
        }

        val name = when (type) {
            ConditionType.HEADER -> config.getString("header")
            ConditionType.PARAM -> config.getString("param")
            else -> null
        }

        val value = when (type) {
            ConditionType.METHOD -> config.getString("method")
            ConditionType.PATH -> config.getString("path")
            ConditionType.HEADER -> config.getString("value")
            ConditionType.PARAM -> config.getString("value")
        }

        if (value == null || (type in listOf(ConditionType.HEADER, ConditionType.PARAM) && name == null)) {
            return null
        }

        return Condition(type, name ?: "", value)
    }

    override fun shutdown() {
        // 无需释放资源
    }

    /**
     * 转换规则
     */
    data class TransformRules(
        val add: Map<String, String>,
        val remove: List<String>,
        val rename: Map<String, String>
    )

    /**
     * 条件类型
     */
    enum class ConditionType {
        METHOD,  // 请求方法
        PATH,    // 请求路径
        HEADER,  // 请求头
        PARAM    // 请求参数
    }

    /**
     * 条件
     */
    data class Condition(
        val type: ConditionType,
        val name: String,
        val value: String
    )

    /**
     * 请求体转换类型
     */
    enum class BodyTransformType {
        NONE,         // 不转换
        JSON_TO_XML,  // JSON 转 XML
        XML_TO_JSON,  // XML 转 JSON
        JSON_TO_FORM, // JSON 转表单
        FORM_TO_JSON  // 表单转 JSON
    }

    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return RequestTransformerPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
