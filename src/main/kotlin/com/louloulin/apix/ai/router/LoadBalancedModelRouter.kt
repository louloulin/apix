package com.louloulin.apix.ai.router

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 负载均衡模型路由器，支持多模型负载均衡。
 */
class LoadBalancedModelRouter(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(LoadBalancedModelRouter::class.java)

    // 模型组配置
    private val modelGroups = ConcurrentHashMap<String, ModelGroup>()

    // 模型使用统计
    private val modelStats = ConcurrentHashMap<String, ModelStats>()

    // 上次健康检查时间
    private val lastHealthCheck = AtomicLong(0)

    // 健康检查间隔（毫秒）
    private var healthCheckInterval = 60000L

    // 是否启用自适应负载均衡
    private var adaptiveLoadBalancing = true

    /**
     * 初始化负载均衡模型路由器。
     */
    fun initialize(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 加载配置
            healthCheckInterval = config.getLong("healthCheckInterval", 60000L)
            adaptiveLoadBalancing = config.getBoolean("adaptiveLoadBalancing", true)

            // 加载模型组
            val groupsArray = config.getJsonArray("modelGroups", JsonArray())
            for (i in 0 until groupsArray.size()) {
                val groupJson = groupsArray.getJsonObject(i)
                val group = ModelGroup.fromJson(groupJson)
                modelGroups[group.id] = group

                // 初始化模型统计
                for (model in group.models) {
                    modelStats[model.id] = ModelStats(model.id, model.name)
                }
            }

            // 启动健康检查
            if (healthCheckInterval > 0) {
                startHealthCheck()
            }

            logger.info("负载均衡模型路由器初始化完成，加载了 ${modelGroups.size} 个模型组")
            promise.complete()
        } catch (e: Exception) {
            logger.error("初始化负载均衡模型路由器失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 启动健康检查。
     */
    private fun startHealthCheck() {
        vertx.setPeriodic(healthCheckInterval) { _ ->
            checkModelsHealth()
        }
    }

    /**
     * 检查模型健康状态。
     */
    private fun checkModelsHealth() {
        val now = System.currentTimeMillis()
        lastHealthCheck.set(now)

        logger.debug("开始检查模型健康状态")

        for (group in modelGroups.values) {
            for (model in group.models) {
                // 检查模型健康状态
                checkModelHealth(model)
                    .onSuccess { healthy ->
                        model.healthy = healthy
                        if (!healthy) {
                            logger.warn("模型 ${model.name} (${model.id}) 不健康，将暂时从负载均衡中移除")
                        }
                    }
                    .onFailure { err ->
                        logger.error("检查模型 ${model.name} (${model.id}) 健康状态失败", err)
                        model.healthy = false
                    }
            }
        }
    }

    /**
     * 检查单个模型的健康状态。
     */
    private fun checkModelHealth(model: Model): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        // 这里可以实现实际的健康检查逻辑
        // 例如，发送一个简单的请求到模型，检查响应

        // 简单示例：假设所有模型都是健康的
        promise.complete(true)

        return promise.future()
    }

    /**
     * 路由请求到适当的模型。
     */
    fun routeRequest(request: JsonObject): Future<String> {
        val promise = Promise.promise<String>()

        try {
            // 提取请求信息
            val groupId = request.getString("groupId", "")
            val content = request.getString("content", "")
            val contentType = request.getString("contentType", "text")
            val requestType = request.getString("requestType", "completion")
            val defaultModel = request.getString("defaultModel", "gpt-3.5-turbo")

            // 如果指定了模型组，使用负载均衡选择模型
            if (groupId.isNotEmpty() && modelGroups.containsKey(groupId)) {
                val group = modelGroups[groupId]!!
                val model = selectModel(group, content, contentType, requestType)

                if (model != null) {
                    // 更新模型统计
                    updateModelStats(model.id)

                    logger.debug("将请求路由到模型组 ${group.name} 中的模型 ${model.name}")
                    promise.complete(model.id)
                } else {
                    logger.debug("模型组 ${group.name} 中没有可用模型，使用默认模型 $defaultModel")
                    promise.complete(defaultModel)
                }
            } else {
                // 如果没有指定模型组，使用默认模型
                logger.debug("未指定模型组或模型组不存在，使用默认模型 $defaultModel")
                promise.complete(defaultModel)
            }
        } catch (e: Exception) {
            logger.error("路由请求失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 从模型组中选择一个模型。
     */
    private fun selectModel(group: ModelGroup, content: String, contentType: String, requestType: String): Model? {
        // 获取健康的模型
        val healthyModels = group.models.filter { it.healthy }

        if (healthyModels.isEmpty()) {
            return null
        }

        // 根据负载均衡策略选择模型
        return when (group.strategy) {
            LoadBalancingStrategy.ROUND_ROBIN -> selectModelRoundRobin(healthyModels)
            LoadBalancingStrategy.WEIGHTED -> selectModelWeighted(healthyModels)
            LoadBalancingStrategy.LEAST_CONNECTIONS -> selectModelLeastConnections(healthyModels)
            LoadBalancingStrategy.ADAPTIVE -> selectModelAdaptive(healthyModels, content, contentType, requestType)
            LoadBalancingStrategy.RANDOM -> selectModelRandom(healthyModels)
        }
    }

    /**
     * 使用轮询策略选择模型。
     */
    private fun selectModelRoundRobin(models: List<Model>): Model {
        // 使用静态计数器
        val index = roundRobinCounter.getAndIncrement() % models.size
        return models[index]
    }

    // 轮询计数器
    private val roundRobinCounter = AtomicInteger(0)

    /**
     * 使用加权策略选择模型。
     */
    private fun selectModelWeighted(models: List<Model>): Model {
        // 计算总权重
        val totalWeight = models.sumOf { it.weight }

        // 生成随机数
        val random = (0 until totalWeight).random()

        // 选择模型
        var currentWeight = 0
        for (model in models) {
            currentWeight += model.weight
            if (random < currentWeight) {
                return model
            }
        }

        // 如果没有选择到模型（理论上不应该发生），返回第一个模型
        return models.first()
    }

    /**
     * 使用最少连接策略选择模型。
     */
    private fun selectModelLeastConnections(models: List<Model>): Model {
        // 选择活跃连接数最少的模型
        return models.minByOrNull { modelStats[it.id]?.activeConnections?.get() ?: 0 } ?: models.first()
    }

    /**
     * 使用自适应策略选择模型。
     */
    private fun selectModelAdaptive(models: List<Model>, content: String, contentType: String, requestType: String): Model {
        if (!adaptiveLoadBalancing) {
            // 如果自适应负载均衡被禁用，使用轮询策略
            return selectModelRoundRobin(models)
        }

        // 计算每个模型的得分
        val modelScores = models.associateWith { model ->
            calculateModelScore(model, content, contentType, requestType)
        }

        // 选择得分最高的模型
        return modelScores.maxByOrNull { it.value }?.key ?: models.first()
    }

    /**
     * 使用随机策略选择模型。
     */
    private fun selectModelRandom(models: List<Model>): Model {
        return models.random()
    }

    /**
     * 计算模型得分。
     */
    private fun calculateModelScore(model: Model, content: String, contentType: String, requestType: String): Double {
        val stats = modelStats[model.id] ?: return 0.0

        // 基础分数
        var score = 100.0

        // 根据活跃连接数调整分数
        val activeConnections = stats.activeConnections.get()
        score -= activeConnections * 5.0

        // 根据错误率调整分数
        val errorRate = stats.calculateErrorRate()
        score -= errorRate * 50.0

        // 根据平均响应时间调整分数
        val avgResponseTime = stats.calculateAverageResponseTime()
        score -= avgResponseTime / 100.0

        // 根据内容类型和请求类型调整分数
        if (model.preferredContentTypes.contains(contentType)) {
            score += 20.0
        }

        if (model.preferredRequestTypes.contains(requestType)) {
            score += 20.0
        }

        // 根据模型权重调整分数
        score += model.weight * 5.0

        return score
    }

    /**
     * 更新模型统计信息。
     */
    private fun updateModelStats(modelId: String) {
        val stats = modelStats[modelId] ?: return

        // 增加请求计数
        stats.requestCount.incrementAndGet()

        // 增加活跃连接数
        stats.activeConnections.incrementAndGet()

        // 记录请求开始时间
        val requestId = UUID.randomUUID().toString()
        stats.requestStartTimes[requestId] = System.currentTimeMillis()

        // 设置定时器，在请求完成后更新统计信息
        vertx.setTimer(30000) { _ ->
            // 减少活跃连接数
            stats.activeConnections.decrementAndGet()

            // 如果请求未完成，记录为超时
            if (stats.requestStartTimes.containsKey(requestId)) {
                stats.requestStartTimes.remove(requestId)
                stats.timeoutCount.incrementAndGet()
            }
        }
    }

    /**
     * 记录请求完成。
     */
    fun recordRequestCompletion(modelId: String, requestId: String, success: Boolean, errorType: String = ""): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            val stats = modelStats[modelId] ?: return Future.succeededFuture()

            // 减少活跃连接数
            stats.activeConnections.decrementAndGet()

            // 获取请求开始时间
            val startTime = stats.requestStartTimes.remove(requestId) ?: return Future.succeededFuture()

            // 计算响应时间
            val responseTime = System.currentTimeMillis() - startTime

            // 更新统计信息
            if (success) {
                stats.successCount.incrementAndGet()
                stats.totalResponseTime.addAndGet(responseTime)
            } else {
                stats.errorCount.incrementAndGet()
                stats.errorTypes.compute(errorType) { _, count -> (count ?: 0) + 1 }
            }

            promise.complete()
        } catch (e: Exception) {
            logger.error("记录请求完成失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取模型组列表。
     */
    fun getModelGroups(): List<ModelGroup> {
        return modelGroups.values.toList()
    }

    /**
     * 获取模型统计信息。
     */
    fun getModelStats(): List<JsonObject> {
        return modelStats.values.map { it.toJson() }
    }

    /**
     * 获取模型统计对象。
     */
    fun getModelStatsObject(modelId: String): ModelStats? {
        return modelStats[modelId]
    }

    /**
     * 添加模型组。
     */
    fun addModelGroup(group: ModelGroup): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            modelGroups[group.id] = group

            // 初始化模型统计
            for (model in group.models) {
                if (!modelStats.containsKey(model.id)) {
                    modelStats[model.id] = ModelStats(model.id, model.name)
                }
            }

            logger.info("添加模型组: ${group.name}")
            promise.complete()
        } catch (e: Exception) {
            logger.error("添加模型组失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 移除模型组。
     */
    fun removeModelGroup(groupId: String): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            val removed = modelGroups.remove(groupId) != null
            if (removed) {
                logger.info("移除模型组: $groupId")
            } else {
                logger.warn("模型组不存在: $groupId")
            }
            promise.complete(removed)
        } catch (e: Exception) {
            logger.error("移除模型组失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 清除所有模型组。
     */
    fun clearModelGroups(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            modelGroups.clear()
            logger.info("清除所有模型组")
            promise.complete()
        } catch (e: Exception) {
            logger.error("清除模型组失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 关闭负载均衡模型路由器。
     */
    fun close(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 清理资源
            modelGroups.clear()
            modelStats.clear()

            promise.complete()
        } catch (e: Exception) {
            logger.error("关闭负载均衡模型路由器失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 负载均衡策略。
     */
    enum class LoadBalancingStrategy {
        ROUND_ROBIN,    // 轮询
        WEIGHTED,       // 加权
        LEAST_CONNECTIONS, // 最少连接
        ADAPTIVE,       // 自适应
        RANDOM          // 随机
    }

    /**
     * 模型组。
     */
    data class ModelGroup(
        val id: String,
        val name: String,
        val description: String,
        val strategy: LoadBalancingStrategy,
        val models: List<Model>
    ) {
        /**
         * 转换为JSON对象。
         */
        fun toJson(): JsonObject {
            val modelsArray = JsonArray()
            models.forEach { model ->
                modelsArray.add(model.toJson())
            }

            return JsonObject()
                .put("id", id)
                .put("name", name)
                .put("description", description)
                .put("strategy", strategy.name)
                .put("models", modelsArray)
        }

        companion object {
            /**
             * 从JSON对象创建模型组。
             */
            fun fromJson(json: JsonObject): ModelGroup {
                val id = json.getString("id", UUID.randomUUID().toString())
                val name = json.getString("name", "未命名模型组")
                val description = json.getString("description", "")
                val strategyStr = json.getString("strategy", LoadBalancingStrategy.ROUND_ROBIN.name)
                val strategy = try {
                    LoadBalancingStrategy.valueOf(strategyStr)
                } catch (e: Exception) {
                    LoadBalancingStrategy.ROUND_ROBIN
                }

                val modelsArray = json.getJsonArray("models", JsonArray())
                val models = mutableListOf<Model>()

                for (i in 0 until modelsArray.size()) {
                    val modelJson = modelsArray.getJsonObject(i)
                    models.add(Model.fromJson(modelJson))
                }

                return ModelGroup(id, name, description, strategy, models)
            }
        }
    }

    /**
     * 模型。
     */
    data class Model(
        val id: String,
        val name: String,
        val description: String,
        val weight: Int,
        val preferredContentTypes: List<String>,
        val preferredRequestTypes: List<String>,
        var healthy: Boolean = true
    ) {
        /**
         * 转换为JSON对象。
         */
        fun toJson(): JsonObject {
            val contentTypesArray = JsonArray()
            preferredContentTypes.forEach { contentTypesArray.add(it) }

            val requestTypesArray = JsonArray()
            preferredRequestTypes.forEach { requestTypesArray.add(it) }

            return JsonObject()
                .put("id", id)
                .put("name", name)
                .put("description", description)
                .put("weight", weight)
                .put("preferredContentTypes", contentTypesArray)
                .put("preferredRequestTypes", requestTypesArray)
                .put("healthy", healthy)
        }

        companion object {
            /**
             * 从JSON对象创建模型。
             */
            fun fromJson(json: JsonObject): Model {
                val id = json.getString("id", UUID.randomUUID().toString())
                val name = json.getString("name", "未命名模型")
                val description = json.getString("description", "")
                val weight = json.getInteger("weight", 1)

                val contentTypesArray = json.getJsonArray("preferredContentTypes", JsonArray())
                val preferredContentTypes = mutableListOf<String>()
                for (i in 0 until contentTypesArray.size()) {
                    preferredContentTypes.add(contentTypesArray.getString(i))
                }

                val requestTypesArray = json.getJsonArray("preferredRequestTypes", JsonArray())
                val preferredRequestTypes = mutableListOf<String>()
                for (i in 0 until requestTypesArray.size()) {
                    preferredRequestTypes.add(requestTypesArray.getString(i))
                }

                val healthy = json.getBoolean("healthy", true)

                return Model(id, name, description, weight, preferredContentTypes, preferredRequestTypes, healthy)
            }
        }
    }

    /**
     * 模型统计信息。
     */
    data class ModelStats(
        val modelId: String,
        val modelName: String,
        val requestCount: AtomicLong = AtomicLong(0),
        val successCount: AtomicLong = AtomicLong(0),
        val errorCount: AtomicLong = AtomicLong(0),
        val timeoutCount: AtomicLong = AtomicLong(0),
        val totalResponseTime: AtomicLong = AtomicLong(0),
        val activeConnections: AtomicInteger = AtomicInteger(0),
        val requestStartTimes: ConcurrentHashMap<String, Long> = ConcurrentHashMap(),
        val errorTypes: ConcurrentHashMap<String, Int> = ConcurrentHashMap()
    ) {
        /**
         * 计算错误率。
         */
        fun calculateErrorRate(): Double {
            val total = successCount.get() + errorCount.get()
            return if (total > 0) errorCount.get().toDouble() / total else 0.0
        }

        /**
         * 计算平均响应时间。
         */
        fun calculateAverageResponseTime(): Double {
            val successfulRequests = successCount.get()
            return if (successfulRequests > 0) totalResponseTime.get().toDouble() / successfulRequests else 0.0
        }

        /**
         * 转换为JSON对象。
         */
        fun toJson(): JsonObject {
            val errorTypesJson = JsonObject()
            errorTypes.forEach { (type, count) ->
                errorTypesJson.put(type, count)
            }

            return JsonObject()
                .put("modelId", modelId)
                .put("modelName", modelName)
                .put("requestCount", requestCount.get())
                .put("successCount", successCount.get())
                .put("errorCount", errorCount.get())
                .put("timeoutCount", timeoutCount.get())
                .put("errorRate", calculateErrorRate())
                .put("averageResponseTime", calculateAverageResponseTime())
                .put("activeConnections", activeConnections.get())
                .put("errorTypes", errorTypesJson)
        }
    }
}
