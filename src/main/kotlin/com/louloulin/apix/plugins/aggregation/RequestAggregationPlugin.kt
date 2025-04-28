package com.louloulin.apix.plugins.aggregation

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
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import java.util.concurrent.atomic.AtomicInteger

/**
 * 请求聚合插件
 * 
 * 该插件用于聚合多个后端服务的响应，支持以下功能：
 * - 支持多个后端服务的并行请求
 * - 支持多个后端服务的串行请求
 * - 支持请求之间的数据传递
 * - 支持自定义聚合逻辑
 * - 支持超时控制
 * 
 * 配置参数：
 * - services: 后端服务列表
 *   - name: 服务名称
 *   - url: 服务 URL
 *   - method: 请求方法，默认为 GET
 *   - headers: 请求头
 *   - params: 请求参数
 *   - body: 请求体
 *   - timeout: 超时时间（毫秒），默认为 5000
 *   - dependsOn: 依赖的服务列表
 * - aggregation: 聚合配置
 *   - type: 聚合类型，可选值为 "merge", "template"，默认为 "merge"
 *   - template: 聚合模板，仅在 type 为 "template" 时有效
 *   - mergeStrategy: 合并策略，可选值为 "simple", "nested"，默认为 "simple"
 * - timeout: 总超时时间（毫秒），默认为 10000
 * - parallel: 是否并行请求，默认为 true
 * - continueOnError: 是否在某个请求失败时继续，默认为 false
 */
class RequestAggregationPlugin(
    override val id: String,
    override val config: PluginConfig,
    private val vertx: Vertx
) : Plugin {
    private val logger = LoggerFactory.getLogger(RequestAggregationPlugin::class.java)
    override val type: String = "requestAggregation"
    
    // Web 客户端
    private val webClient: WebClient
    
    // 后端服务列表
    private val services: List<Service>
    
    // 聚合配置
    private val aggregationType: AggregationType
    private val aggregationTemplate: String?
    private val mergeStrategy: MergeStrategy
    
    // 总超时时间（毫秒）
    private val timeout: Long
    
    // 是否并行请求
    private val parallel: Boolean
    
    // 是否在某个请求失败时继续
    private val continueOnError: Boolean
    
    init {
        // 创建 Web 客户端
        webClient = WebClient.create(vertx, WebClientOptions()
            .setKeepAlive(true)
            .setMaxPoolSize(100)
        )
        
        // 解析后端服务列表
        val servicesArray = config.config.getJsonArray("services", JsonArray())
        services = servicesArray.mapNotNull { item ->
            if (item is JsonObject) {
                parseService(item)
            } else {
                null
            }
        }
        
        // 解析聚合配置
        val aggregationConfig = config.config.getJsonObject("aggregation", JsonObject())
        val aggregationTypeStr = aggregationConfig.getString("type", "merge")
        aggregationType = when (aggregationTypeStr.lowercase()) {
            "template" -> AggregationType.TEMPLATE
            else -> AggregationType.MERGE
        }
        aggregationTemplate = aggregationConfig.getString("template")
        
        val mergeStrategyStr = aggregationConfig.getString("mergeStrategy", "simple")
        mergeStrategy = when (mergeStrategyStr.lowercase()) {
            "nested" -> MergeStrategy.NESTED
            else -> MergeStrategy.SIMPLE
        }
        
        // 解析总超时时间
        timeout = config.config.getLong("timeout", 10000)
        
        // 解析是否并行请求
        parallel = config.config.getBoolean("parallel", true)
        
        // 解析是否在某个请求失败时继续
        continueOnError = config.config.getBoolean("continueOnError", false)
        
        logger.info("Initialized request aggregation plugin: services={}, aggregationType={}, parallel={}", 
            services.size, aggregationType, parallel)
    }
    
    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 执行请求聚合
            executeAggregation(context).onComplete { ar ->
                if (ar.succeeded()) {
                    // 聚合成功，设置响应
                    val result = ar.result()
                    context.response()
                        .putHeader("Content-Type", "application/json")
                        .end(result.encode())
                    promise.complete()
                } else {
                    // 聚合失败，返回错误
                    logger.error("Failed to execute request aggregation", ar.cause())
                    context.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end(JsonObject()
                            .put("error", "Failed to execute request aggregation")
                            .put("message", ar.cause().message)
                            .encode()
                        )
                    promise.complete()
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing request aggregation plugin", e)
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
     * 执行请求聚合
     */
    private fun executeAggregation(context: RoutingContext): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 创建上下文
        val aggregationContext = AggregationContext()
        
        // 设置原始请求信息
        aggregationContext.originalRequest = JsonObject()
            .put("method", context.request().method().name())
            .put("path", context.request().path())
            .put("headers", JsonObject().apply {
                context.request().headers().forEach { header ->
                    put(header.key, header.value)
                }
            })
            .put("params", JsonObject().apply {
                context.queryParams().forEach { param ->
                    put(param.key, param.value)
                }
            })
        
        // 获取请求体
        val body = context.body().asString()
        if (!body.isNullOrEmpty()) {
            try {
                aggregationContext.originalRequest.put("body", JsonObject(body))
            } catch (e: Exception) {
                // 不是 JSON 格式，作为字符串处理
                aggregationContext.originalRequest.put("body", body)
            }
        }
        
        // 设置超时定时器
        val timerId = vertx.setTimer(timeout) {
            if (!promise.future().isComplete) {
                promise.fail("Request aggregation timed out after $timeout ms")
            }
        }
        
        if (parallel) {
            // 并行执行请求
            executeParallelRequests(aggregationContext).onComplete { ar ->
                // 取消超时定时器
                vertx.cancelTimer(timerId)
                
                if (ar.succeeded()) {
                    // 聚合结果
                    val result = aggregateResults(aggregationContext)
                    promise.complete(result)
                } else {
                    promise.fail(ar.cause())
                }
            }
        } else {
            // 串行执行请求
            executeSerialRequests(aggregationContext).onComplete { ar ->
                // 取消超时定时器
                vertx.cancelTimer(timerId)
                
                if (ar.succeeded()) {
                    // 聚合结果
                    val result = aggregateResults(aggregationContext)
                    promise.complete(result)
                } else {
                    promise.fail(ar.cause())
                }
            }
        }
        
        return promise.future()
    }
    
    /**
     * 并行执行请求
     */
    private fun executeParallelRequests(context: AggregationContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 按依赖关系对服务进行分组
        val serviceGroups = groupServicesByDependency()
        
        // 串行执行每个组，组内并行执行
        executeServiceGroups(serviceGroups, 0, context).onComplete { ar ->
            if (ar.succeeded()) {
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 串行执行请求
     */
    private fun executeSerialRequests(context: AggregationContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 按依赖关系对服务进行排序
        val sortedServices = sortServicesByDependency()
        
        // 串行执行每个服务
        executeServices(sortedServices, 0, context).onComplete { ar ->
            if (ar.succeeded()) {
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 串行执行服务组
     */
    private fun executeServiceGroups(groups: List<List<Service>>, index: Int, context: AggregationContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        if (index >= groups.size) {
            // 所有组都已执行完毕
            promise.complete()
            return promise.future()
        }
        
        // 获取当前组
        val group = groups[index]
        
        // 并行执行组内的服务
        val futures = group.map { service ->
            executeService(service, context)
        }
        
        // 等待所有服务执行完毕
        Future.all(futures).onComplete { ar ->
            if (ar.succeeded() || continueOnError) {
                // 执行下一个组
                executeServiceGroups(groups, index + 1, context).onComplete { nextAr ->
                    if (nextAr.succeeded()) {
                        promise.complete()
                    } else {
                        promise.fail(nextAr.cause())
                    }
                }
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 串行执行服务
     */
    private fun executeServices(services: List<Service>, index: Int, context: AggregationContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        if (index >= services.size) {
            // 所有服务都已执行完毕
            promise.complete()
            return promise.future()
        }
        
        // 获取当前服务
        val service = services[index]
        
        // 执行服务
        executeService(service, context).onComplete { ar ->
            if (ar.succeeded() || continueOnError) {
                // 执行下一个服务
                executeServices(services, index + 1, context).onComplete { nextAr ->
                    if (nextAr.succeeded()) {
                        promise.complete()
                    } else {
                        promise.fail(nextAr.cause())
                    }
                }
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 执行单个服务
     */
    private fun executeService(service: Service, context: AggregationContext): Future<Void> {
        val promise = Promise.promise<Void>()
        
        try {
            // 准备请求参数
            val url = prepareUrl(service, context)
            val method = service.method
            val headers = prepareHeaders(service, context)
            val body = prepareBody(service, context)
            
            // 创建请求
            val request = when (method) {
                HttpMethod.GET -> webClient.getAbs(url)
                HttpMethod.POST -> webClient.postAbs(url)
                HttpMethod.PUT -> webClient.putAbs(url)
                HttpMethod.DELETE -> webClient.deleteAbs(url)
                HttpMethod.PATCH -> webClient.patchAbs(url)
                else -> webClient.getAbs(url)
            }
            
            // 设置请求头
            headers.forEach { (name, value) ->
                request.putHeader(name, value)
            }
            
            // 设置超时
            request.timeout(service.timeout)
            
            // 发送请求
            if (body != null && method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
                request.sendBuffer(Buffer.buffer(body)) { ar ->
                    handleResponse(ar, service, context, promise)
                }
            } else {
                request.send { ar ->
                    handleResponse(ar, service, context, promise)
                }
            }
        } catch (e: Exception) {
            logger.error("Error executing service ${service.name}", e)
            if (continueOnError) {
                // 记录错误，但继续执行
                context.errors[service.name] = e.message ?: "Unknown error"
                promise.complete()
            } else {
                promise.fail(e)
            }
        }
        
        return promise.future()
    }
    
    /**
     * 处理响应
     */
    private fun handleResponse(ar: io.vertx.core.AsyncResult<HttpResponse<Buffer>>, service: Service, context: AggregationContext, promise: Promise<Void>) {
        if (ar.succeeded()) {
            val response = ar.result()
            val statusCode = response.statusCode()
            
            if (statusCode in 200..299) {
                // 请求成功
                val responseBody = response.bodyAsString()
                
                try {
                    // 尝试解析为 JSON
                    val jsonResponse = if (responseBody.isNullOrEmpty()) {
                        JsonObject()
                    } else {
                        try {
                            JsonObject(responseBody)
                        } catch (e: Exception) {
                            // 不是 JSON 对象，尝试解析为 JSON 数组
                            try {
                                JsonObject().put("data", JsonArray(responseBody))
                            } catch (e2: Exception) {
                                // 不是 JSON 格式，作为字符串处理
                                JsonObject().put("data", responseBody)
                            }
                        }
                    }
                    
                    // 存储响应
                    context.responses[service.name] = jsonResponse
                    promise.complete()
                } catch (e: Exception) {
                    logger.error("Error parsing response from service ${service.name}", e)
                    if (continueOnError) {
                        // 记录错误，但继续执行
                        context.errors[service.name] = e.message ?: "Unknown error"
                        promise.complete()
                    } else {
                        promise.fail(e)
                    }
                }
            } else {
                // 请求失败
                val errorMessage = "Service ${service.name} returned status code $statusCode"
                logger.error(errorMessage)
                if (continueOnError) {
                    // 记录错误，但继续执行
                    context.errors[service.name] = errorMessage
                    promise.complete()
                } else {
                    promise.fail(errorMessage)
                }
            }
        } else {
            // 请求失败
            logger.error("Error calling service ${service.name}", ar.cause())
            if (continueOnError) {
                // 记录错误，但继续执行
                context.errors[service.name] = ar.cause().message ?: "Unknown error"
                promise.complete()
            } else {
                promise.fail(ar.cause())
            }
        }
    }
    
    /**
     * 准备 URL
     */
    private fun prepareUrl(service: Service, context: AggregationContext): String {
        var url = service.url
        
        // 替换 URL 中的变量
        service.params.forEach { (name, value) ->
            val resolvedValue = resolveVariable(value, context)
            url = url.replace("{$name}", resolvedValue)
        }
        
        return url
    }
    
    /**
     * 准备请求头
     */
    private fun prepareHeaders(service: Service, context: AggregationContext): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        
        service.headers.forEach { (name, value) ->
            val resolvedValue = resolveVariable(value, context)
            headers[name] = resolvedValue
        }
        
        return headers
    }
    
    /**
     * 准备请求体
     */
    private fun prepareBody(service: Service, context: AggregationContext): String? {
        if (service.body == null) {
            return null
        }
        
        return resolveVariable(service.body, context)
    }
    
    /**
     * 解析变量
     */
    private fun resolveVariable(value: String, context: AggregationContext): String {
        var result = value
        
        // 替换变量
        val pattern = Regex("\\{([^}]+)\\}")
        val matches = pattern.findAll(value)
        
        for (match in matches) {
            val variable = match.groupValues[1]
            val parts = variable.split(".")
            
            if (parts.size >= 2) {
                val serviceName = parts[0]
                val path = parts.subList(1, parts.size).joinToString(".")
                
                // 从服务响应中获取值
                val serviceResponse = context.responses[serviceName]
                if (serviceResponse != null) {
                    val variableValue = getValueFromPath(serviceResponse, path)
                    if (variableValue != null) {
                        result = result.replace("{$variable}", variableValue.toString())
                    }
                }
            } else if (parts.size == 1) {
                // 从原始请求中获取值
                val path = parts[0]
                val variableValue = getValueFromPath(context.originalRequest, path)
                if (variableValue != null) {
                    result = result.replace("{$variable}", variableValue.toString())
                }
            }
        }
        
        return result
    }
    
    /**
     * 从路径获取值
     */
    private fun getValueFromPath(json: JsonObject, path: String): Any? {
        val parts = path.split(".")
        var current: Any? = json
        
        for (part in parts) {
            current = when (current) {
                is JsonObject -> current.getValue(part)
                is JsonArray -> {
                    try {
                        val index = part.toInt()
                        if (index >= 0 && index < current.size()) {
                            current.getValue(index)
                        } else {
                            null
                        }
                    } catch (e: NumberFormatException) {
                        null
                    }
                }
                else -> null
            }
            
            if (current == null) {
                return null
            }
        }
        
        return current
    }
    
    /**
     * 聚合结果
     */
    private fun aggregateResults(context: AggregationContext): JsonObject {
        return when (aggregationType) {
            AggregationType.TEMPLATE -> aggregateWithTemplate(context)
            AggregationType.MERGE -> aggregateWithMerge(context)
        }
    }
    
    /**
     * 使用模板聚合结果
     */
    private fun aggregateWithTemplate(context: AggregationContext): JsonObject {
        if (aggregationTemplate == null) {
            return JsonObject()
        }
        
        try {
            val template = JsonObject(aggregationTemplate)
            val result = JsonObject()
            
            // 递归处理模板
            processTemplate(template, result, context)
            
            return result
        } catch (e: Exception) {
            logger.error("Error aggregating results with template", e)
            return JsonObject()
                .put("error", "Error aggregating results with template")
                .put("message", e.message)
        }
    }
    
    /**
     * 处理模板
     */
    private fun processTemplate(template: JsonObject, result: JsonObject, context: AggregationContext) {
        template.forEach { (key, value) ->
            when (value) {
                is String -> {
                    // 解析变量
                    val resolvedValue = resolveVariable(value, context)
                    result.put(key, resolvedValue)
                }
                is JsonObject -> {
                    // 递归处理嵌套对象
                    val nestedResult = JsonObject()
                    processTemplate(value, nestedResult, context)
                    result.put(key, nestedResult)
                }
                is JsonArray -> {
                    // 处理数组
                    val array = JsonArray()
                    for (item in value) {
                        if (item is JsonObject) {
                            val nestedResult = JsonObject()
                            processTemplate(item, nestedResult, context)
                            array.add(nestedResult)
                        } else if (item is String) {
                            // 解析变量
                            val resolvedValue = resolveVariable(item, context)
                            array.add(resolvedValue)
                        } else {
                            array.add(item)
                        }
                    }
                    result.put(key, array)
                }
                else -> {
                    // 直接添加值
                    result.put(key, value)
                }
            }
        }
    }
    
    /**
     * 使用合并策略聚合结果
     */
    private fun aggregateWithMerge(context: AggregationContext): JsonObject {
        val result = JsonObject()
        
        when (mergeStrategy) {
            MergeStrategy.SIMPLE -> {
                // 简单合并，将所有响应合并到一个对象中
                context.responses.forEach { (name, response) ->
                    result.put(name, response)
                }
            }
            MergeStrategy.NESTED -> {
                // 嵌套合并，保留响应的嵌套结构
                context.responses.forEach { (name, response) ->
                    mergeJsonObjects(result, response, name)
                }
            }
        }
        
        // 添加错误信息
        if (context.errors.isNotEmpty()) {
            val errors = JsonObject()
            context.errors.forEach { (name, error) ->
                errors.put(name, error)
            }
            result.put("errors", errors)
        }
        
        return result
    }
    
    /**
     * 合并 JSON 对象
     */
    private fun mergeJsonObjects(target: JsonObject, source: JsonObject, prefix: String) {
        source.forEach { (key, value) ->
            val newKey = "$prefix.$key"
            target.put(newKey, value)
        }
    }
    
    /**
     * 按依赖关系对服务进行分组
     */
    private fun groupServicesByDependency(): List<List<Service>> {
        val groups = mutableListOf<List<Service>>()
        val remainingServices = services.toMutableList()
        
        while (remainingServices.isNotEmpty()) {
            // 找出没有依赖或依赖已满足的服务
            val group = remainingServices.filter { service ->
                service.dependsOn.isEmpty() || service.dependsOn.all { dependency ->
                    services.any { it.name == dependency && it !in remainingServices }
                }
            }
            
            if (group.isEmpty()) {
                // 存在循环依赖，无法继续分组
                logger.warn("Circular dependency detected in services")
                groups.add(remainingServices)
                break
            }
            
            groups.add(group)
            remainingServices.removeAll(group)
        }
        
        return groups
    }
    
    /**
     * 按依赖关系对服务进行排序
     */
    private fun sortServicesByDependency(): List<Service> {
        val result = mutableListOf<Service>()
        val remainingServices = services.toMutableList()
        
        while (remainingServices.isNotEmpty()) {
            // 找出没有依赖或依赖已满足的服务
            val service = remainingServices.find { service ->
                service.dependsOn.isEmpty() || service.dependsOn.all { dependency ->
                    services.any { it.name == dependency && it in result }
                }
            }
            
            if (service == null) {
                // 存在循环依赖，无法继续排序
                logger.warn("Circular dependency detected in services")
                result.addAll(remainingServices)
                break
            }
            
            result.add(service)
            remainingServices.remove(service)
        }
        
        return result
    }
    
    /**
     * 解析服务配置
     */
    private fun parseService(config: JsonObject): Service? {
        val name = config.getString("name") ?: return null
        val url = config.getString("url") ?: return null
        
        val methodStr = config.getString("method", "GET")
        val method = try {
            HttpMethod.valueOf(methodStr.uppercase())
        } catch (e: IllegalArgumentException) {
            HttpMethod.GET
        }
        
        val headers = mutableMapOf<String, String>()
        val headersConfig = config.getJsonObject("headers", JsonObject())
        headersConfig.forEach { (key, value) ->
            headers[key] = value.toString()
        }
        
        val params = mutableMapOf<String, String>()
        val paramsConfig = config.getJsonObject("params", JsonObject())
        paramsConfig.forEach { (key, value) ->
            params[key] = value.toString()
        }
        
        val body = config.getString("body")
        val timeout = config.getLong("timeout", 5000)
        
        val dependsOn = mutableListOf<String>()
        val dependsOnArray = config.getJsonArray("dependsOn", JsonArray())
        dependsOnArray.forEach { dependency ->
            dependsOn.add(dependency.toString())
        }
        
        return Service(name, url, method, headers, params, body, timeout, dependsOn)
    }
    
    override fun shutdown() {
        // 关闭 Web 客户端
        webClient.close()
    }
    
    /**
     * 服务配置
     */
    data class Service(
        val name: String,
        val url: String,
        val method: HttpMethod,
        val headers: Map<String, String>,
        val params: Map<String, String>,
        val body: String?,
        val timeout: Long,
        val dependsOn: List<String>
    )
    
    /**
     * 聚合上下文
     */
    class AggregationContext {
        // 原始请求
        lateinit var originalRequest: JsonObject
        
        // 服务响应
        val responses = mutableMapOf<String, JsonObject>()
        
        // 服务错误
        val errors = mutableMapOf<String, String>()
    }
    
    /**
     * 聚合类型
     */
    enum class AggregationType {
        MERGE,     // 合并
        TEMPLATE   // 模板
    }
    
    /**
     * 合并策略
     */
    enum class MergeStrategy {
        SIMPLE,  // 简单合并
        NESTED   // 嵌套合并
    }
    
    /**
     * 插件工厂
     */
    class Factory : com.louloulin.apix.plugins.PluginFactory {
        override fun create(config: PluginConfig): Plugin {
            return RequestAggregationPlugin(config.id, config, Vertx.currentContext().owner())
        }
    }
}
