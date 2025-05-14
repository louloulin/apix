package com.louloulin.apix.plugins.ai

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.plugins.AbstractPlugin
import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.RoutingContext
import org.slf4j.LoggerFactory

/**
 * AI模型选择插件
 *
 * 该插件根据请求内容自动选择最合适的AI模型。
 * 它使用语义路由和负载均衡功能来选择模型。
 *
 * 配置参数：
 * - defaultModel: 默认模型，当无法确定最佳模型时使用
 * - useSemanticRouting: 是否使用语义路由，默认为true
 * - useLoadBalancing: 是否使用负载均衡，默认为true
 * - modelGroupId: 负载均衡模型组ID，当useLoadBalancing为true时使用
 * - contentPath: 请求体中内容的路径，默认为"prompt"或"messages"
 * - modelPath: 请求体中模型的路径，默认为"model"
 * - headerName: 请求头中模型的名称，默认为"X-Model"
 */
class ModelSelectorPlugin(
    override val id: String,
    override val type: String = "modelSelector",
    override val config: PluginConfig
) : AbstractPlugin() {
    private val logger = LoggerFactory.getLogger(ModelSelectorPlugin::class.java)

    // 默认模型
    private val defaultModel: String

    // 是否使用语义路由
    private val useSemanticRouting: Boolean

    // 是否使用负载均衡
    private val useLoadBalancing: Boolean

    // 负载均衡模型组ID
    private val modelGroupId: String

    // 请求体中内容的路径
    private val contentPath: String

    // 请求体中模型的路径
    private val modelPath: String

    // 请求头中模型的名称
    private val headerName: String

    // Vertx实例
    private lateinit var vertx: Vertx

    init {
        // 从配置中获取参数
        defaultModel = config.getString("defaultModel") ?: "gpt-3.5-turbo"
        useSemanticRouting = config.getBoolean("useSemanticRouting") ?: true
        useLoadBalancing = config.getBoolean("useLoadBalancing") ?: true
        modelGroupId = config.getString("modelGroupId") ?: ""
        contentPath = config.getString("contentPath") ?: ""
        modelPath = config.getString("modelPath") ?: "model"
        headerName = config.getString("headerName") ?: "X-Model"

        logger.info("ModelSelectorPlugin initialized with defaultModel: {}", defaultModel)
    }

    override fun initialize(vertx: Vertx): Future<Void> {
        this.vertx = vertx
        logger.info("ModelSelectorPlugin initialized")
        return Future.succeededFuture()
    }

    override fun execute(context: RoutingContext): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 检查请求是否已经指定了模型
            val modelFromHeader = context.request().getHeader(headerName)
            if (!modelFromHeader.isNullOrBlank()) {
                // 如果请求头中已经指定了模型，则不需要选择模型
                logger.debug("Model already specified in header: {}", modelFromHeader)
                promise.complete()
                return promise.future()
            }

            // 获取请求体
            val body = context.body().asJsonObject()
            if (body == null) {
                logger.debug("Request body is null, skipping model selection")
                promise.complete()
                return promise.future()
            }

            // 检查请求体中是否已经指定了模型
            val modelFromBody = getModelFromBody(body)
            if (modelFromBody != null) {
                // 如果请求体中已经指定了模型，则不需要选择模型
                logger.debug("Model already specified in body: {}", modelFromBody)
                promise.complete()
                return promise.future()
            }

            // 获取请求内容
            val content = getContentFromBody(body)
            if (content.isNullOrBlank()) {
                // 如果无法获取内容，则使用默认模型
                logger.debug("Cannot extract content from request, using default model: {}", defaultModel)
                setModel(context, body, defaultModel)
                promise.complete()
                return promise.future()
            }

            // 选择模型
            selectModel(content)
                .onSuccess { model ->
                    // 设置模型
                    setModel(context, body, model)
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("Error selecting model", err)
                    // 使用默认模型
                    setModel(context, body, defaultModel)
                    promise.complete()
                }
        } catch (e: Exception) {
            logger.error("Error executing ModelSelectorPlugin", e)
            // 出错时使用默认模型
            try {
                val body = context.body().asJsonObject()
                if (body != null) {
                    setModel(context, body, defaultModel)
                }
            } catch (ex: Exception) {
                logger.error("Error setting default model", ex)
            }
            promise.complete()
        }

        return promise.future()
    }

    /**
     * 从请求体中获取模型
     */
    private fun getModelFromBody(body: JsonObject): String? {
        return if (modelPath.isNotBlank()) {
            val paths = modelPath.split(".")
            var current: Any? = body

            for (path in paths) {
                current = when (current) {
                    is JsonObject -> (current as JsonObject).getValue(path)
                    else -> null
                }

                if (current == null) {
                    break
                }
            }

            current?.toString()
        } else {
            body.getString("model")
        }
    }

    /**
     * 从请求体中获取内容
     */
    private fun getContentFromBody(body: JsonObject): String? {
        // 如果指定了内容路径，则按路径获取
        if (contentPath.isNotBlank()) {
            val paths = contentPath.split(".")
            var current: Any? = body

            for (path in paths) {
                current = when (current) {
                    is JsonObject -> (current as JsonObject).getValue(path)
                    else -> null
                }

                if (current == null) {
                    break
                }
            }

            return current?.toString()
        }

        // 尝试从常见字段中获取内容
        val prompt = body.getString("prompt")
        if (!prompt.isNullOrBlank()) {
            return prompt
        }

        // 尝试从messages字段获取内容
        val messages = body.getJsonArray("messages")
        if (messages != null && messages.size() > 0) {
            val contentBuilder = StringBuilder()

            for (i in 0 until messages.size()) {
                val message = messages.getJsonObject(i)
                val content = message.getString("content")
                if (!content.isNullOrBlank()) {
                    contentBuilder.append(content).append(" ")
                }
            }

            return contentBuilder.toString().trim()
        }

        return null
    }

    /**
     * 选择模型
     */
    private fun selectModel(content: String): Future<String> {
        val promise = Promise.promise<String>()

        // 首先尝试使用语义路由
        if (useSemanticRouting) {
            val request = JsonObject()
                .put("content", content)
                .put("defaultModel", defaultModel)

            vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_SEMANTIC_ROUTE, request) { ar ->
                if (ar.succeeded() && ar.result().body().getBoolean("success", false)) {
                    val result = ar.result().body().getJsonObject("result")
                    val model = result.getString("model")

                    if (model != null && model != defaultModel) {
                        // 语义路由成功选择了模型
                        logger.debug("Semantic routing selected model: {}", model)
                        promise.complete(model)
                        return@request
                    }
                }

                // 语义路由失败或返回默认模型，尝试使用负载均衡
                if (useLoadBalancing && modelGroupId.isNotBlank()) {
                    val lbRequest = JsonObject()
                        .put("groupId", modelGroupId)
                        .put("content", content)
                        .put("contentType", "text")
                        .put("requestType", "chat")
                        .put("defaultModel", defaultModel)

                    vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_ROUTE, lbRequest) { lbAr ->
                        if (lbAr.succeeded() && lbAr.result().body().getBoolean("success", false)) {
                            val lbResult = lbAr.result().body().getJsonObject("result")
                            val lbModel = lbResult.getString("model")

                            if (lbModel != null) {
                                // 负载均衡成功选择了模型
                                logger.debug("Load balancing selected model: {}", lbModel)
                                promise.complete(lbModel)
                                return@request
                            }
                        }

                        // 负载均衡也失败了，使用默认模型
                        logger.debug("Using default model: {}", defaultModel)
                        promise.complete(defaultModel)
                    }
                } else {
                    // 不使用负载均衡，直接使用默认模型
                    logger.debug("Using default model: {}", defaultModel)
                    promise.complete(defaultModel)
                }
            }
        } else if (useLoadBalancing && modelGroupId.isNotBlank()) {
            // 不使用语义路由，直接使用负载均衡
            val lbRequest = JsonObject()
                .put("groupId", modelGroupId)
                .put("content", content)
                .put("contentType", "text")
                .put("requestType", "chat")
                .put("defaultModel", defaultModel)

            vertx.eventBus().request<JsonObject>(EventBusAddresses.AI_LOAD_BALANCED_ROUTE, lbRequest) { lbAr ->
                if (lbAr.succeeded() && lbAr.result().body().getBoolean("success", false)) {
                    val lbResult = lbAr.result().body().getJsonObject("result")
                    val lbModel = lbResult.getString("model")

                    if (lbModel != null) {
                        // 负载均衡成功选择了模型
                        logger.debug("Load balancing selected model: {}", lbModel)
                        promise.complete(lbModel)
                        return@request
                    }
                }

                // 负载均衡失败，使用默认模型
                logger.debug("Using default model: {}", defaultModel)
                promise.complete(defaultModel)
            }
        } else {
            // 既不使用语义路由也不使用负载均衡，直接使用默认模型
            logger.debug("Using default model: {}", defaultModel)
            promise.complete(defaultModel)
        }

        return promise.future()
    }

    /**
     * 设置模型
     */
    private fun setModel(context: RoutingContext, body: JsonObject, model: String) {
        // 设置请求头
        context.request().headers().set(headerName, model)

        // 设置请求体中的模型
        if (modelPath.isNotBlank()) {
            val paths = modelPath.split(".")
            var current = body

            for (i in 0 until paths.size - 1) {
                val path = paths[i]
                if (!current.containsKey(path) || current.getValue(path) !is JsonObject) {
                    current.put(path, JsonObject())
                }
                current = current.getJsonObject(path)
            }

            current.put(paths.last(), model)
        } else {
            body.put("model", model)
        }

        // 更新请求体
        context.setBody(body.toBuffer())
    }

    override fun shutdown() {
        logger.info("ModelSelectorPlugin shutdown")
    }
}

/**
 * AI模型选择插件工厂
 */
class ModelSelectorPluginFactory : com.louloulin.apix.plugins.PluginFactory {
    override fun create(config: PluginConfig): com.louloulin.apix.plugins.Plugin {
        return ModelSelectorPlugin(config.id, "modelSelector", config)
    }
}
