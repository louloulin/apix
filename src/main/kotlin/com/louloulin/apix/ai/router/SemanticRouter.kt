package com.louloulin.apix.ai.router

import com.louloulin.apix.cache.semantic.EmbeddingEngine
import com.louloulin.apix.cache.semantic.SemanticCacheFactory
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 语义路由器，基于内容的语义相似度进行路由。
 */
class SemanticRouter(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SemanticRouter::class.java)

    // 嵌入引擎，用于计算文本的嵌入向量
    private lateinit var embeddingEngine: EmbeddingEngine

    // 语义路由规则
    private val semanticRules = ConcurrentHashMap<String, SemanticRoutingRule>()

    // 相似度阈值
    private var similarityThreshold = 0.5f

    /**
     * 初始化语义路由器。
     */
    fun initialize(config: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 创建嵌入引擎
            embeddingEngine = SemanticCacheFactory.createSimpleEmbeddingEngine(vertx)

            // 设置相似度阈值
            similarityThreshold = config.getFloat("similarityThreshold", 0.5f)

            // 加载语义路由规则
            val rulesArray = config.getJsonArray("semanticRules", JsonArray())
            for (i in 0 until rulesArray.size()) {
                val ruleJson = rulesArray.getJsonObject(i)
                val rule = SemanticRoutingRule.fromJson(ruleJson)
                semanticRules[rule.id] = rule
            }

            logger.info("语义路由器初始化完成，加载了 ${semanticRules.size} 条规则")
            promise.complete()
        } catch (e: Exception) {
            logger.error("初始化语义路由器失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 基于内容的语义相似度进行路由。
     */
    fun route(content: String, defaultModel: String): Future<String> {
        val promise = Promise.promise<String>()

        if (semanticRules.isEmpty()) {
            // 如果没有语义路由规则，直接返回默认模型
            promise.complete(defaultModel)
            return promise.future()
        }

        // 计算内容的嵌入向量
        embeddingEngine.embed(content)
            .onSuccess { contentEmbedding ->
                // 查找最匹配的规则
                findBestMatchingRule(content, contentEmbedding)
                    .onSuccess { rule ->
                        if (rule != null) {
                            logger.debug("找到匹配的语义路由规则: ${rule.name}")
                            promise.complete(rule.targetModel)
                        } else {
                            logger.debug("没有找到匹配的语义路由规则，使用默认模型: $defaultModel")
                            promise.complete(defaultModel)
                        }
                    }
                    .onFailure { err ->
                        logger.error("查找匹配的语义路由规则失败", err)
                        promise.complete(defaultModel)
                    }
            }
            .onFailure { err ->
                logger.error("计算内容嵌入向量失败", err)
                promise.complete(defaultModel)
            }

        return promise.future()
    }

    /**
     * 查找最匹配的语义路由规则。
     */
    private fun findBestMatchingRule(content: String, contentEmbedding: FloatArray): Future<SemanticRoutingRule?> {
        val promise = Promise.promise<SemanticRoutingRule?>()

        // 创建一个列表来存储所有规则的Future
        val ruleFutures = mutableListOf<Future<Pair<SemanticRoutingRule, Float>>>()

        // 为每个规则计算相似度
        for (rule in semanticRules.values) {
            val ruleFuture = calculateRuleSimilarity(rule, content, contentEmbedding)
            ruleFutures.add(ruleFuture)
        }

        // 等待所有规则的相似度计算完成
        Future.all(ruleFutures)
            .onSuccess {
                // 获取所有规则的相似度结果
                val results = ruleFutures.mapNotNull {
                    if (it.succeeded()) it.result() else null
                }

                // 按相似度降序排序
                val sortedResults = results.sortedByDescending { it.second }

                // 找到相似度超过阈值的最高优先级规则
                val bestMatch = sortedResults.firstOrNull { it.second >= similarityThreshold }

                if (bestMatch != null) {
                    promise.complete(bestMatch.first)
                } else {
                    promise.complete(null)
                }
            }
            .onFailure { err ->
                logger.error("计算规则相似度失败", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * 计算规则与内容的相似度。
     */
    private fun calculateRuleSimilarity(
        rule: SemanticRoutingRule,
        content: String,
        contentEmbedding: FloatArray
    ): Future<Pair<SemanticRoutingRule, Float>> {
        val promise = Promise.promise<Pair<SemanticRoutingRule, Float>>()

        // 如果规则没有示例，使用规则的模式作为示例
        val examples = if (rule.examples.isEmpty()) listOf(rule.pattern) else rule.examples

        // 计算所有示例的嵌入向量
        embeddingEngine.embedBatch(examples)
            .onSuccess { exampleEmbeddings ->
                // 计算内容与每个示例的相似度
                val similarities = exampleEmbeddings.map { exampleEmbedding ->
                    embeddingEngine.similarity(contentEmbedding, exampleEmbedding)
                }

                // 使用最高相似度作为规则的相似度
                val maxSimilarity = similarities.maxOrNull() ?: 0f

                promise.complete(Pair(rule, maxSimilarity))
            }
            .onFailure { err ->
                logger.error("计算示例嵌入向量失败", err)
                promise.fail(err)
            }

        return promise.future()
    }

    /**
     * 添加语义路由规则。
     */
    fun addRule(rule: SemanticRoutingRule): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            semanticRules[rule.id] = rule
            logger.info("添加语义路由规则: ${rule.name}")
            promise.complete()
        } catch (e: Exception) {
            logger.error("添加语义路由规则失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 移除语义路由规则。
     */
    fun removeRule(ruleId: String): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            val removed = semanticRules.remove(ruleId) != null
            if (removed) {
                logger.info("移除语义路由规则: $ruleId")
            } else {
                logger.warn("语义路由规则不存在: $ruleId")
            }
            promise.complete(removed)
        } catch (e: Exception) {
            logger.error("移除语义路由规则失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取所有语义路由规则。
     */
    fun getRules(): List<SemanticRoutingRule> {
        return semanticRules.values.toList()
    }

    /**
     * 清除所有语义路由规则。
     */
    fun clearRules(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            semanticRules.clear()
            logger.info("清除所有语义路由规则")
            promise.complete()
        } catch (e: Exception) {
            logger.error("清除语义路由规则失败", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 关闭语义路由器。
     */
    fun close(): Future<Void> {
        return embeddingEngine.close()
    }
}
