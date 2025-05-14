package com.louloulin.apix.cache.semantic

import com.louloulin.apix.cache.CacheFactory
import com.louloulin.apix.cache.CacheManager
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 语义缓存工厂类，用于创建语义缓存管理器
 */
object SemanticCacheFactory {
    private val logger = LoggerFactory.getLogger(SemanticCacheFactory::class.java)

    /**
     * 创建简单的嵌入式引擎
     * @param vertx Vertx 实例
     * @return 嵌入式引擎
     */
    fun createSimpleEmbeddingEngine(vertx: Vertx): EmbeddingEngine {
        return SimpleEmbeddingEngine(vertx)
    }

    /**
     * 创建优化的嵌入式引擎
     * @param vertx Vertx 实例
     * @param config 配置参数
     * @return 嵌入式引擎
     */
    fun createOptimizedEmbeddingEngine(vertx: Vertx, config: JsonObject = JsonObject()): EmbeddingEngine {
        return OptimizedEmbeddingEngine(vertx, config)
    }

    /**
     * 创建语义缓存管理器
     * @param vertx Vertx 实例
     * @param embeddingEngine 嵌入式引擎
     * @param underlyingCache 底层缓存管理器
     * @param similarityThreshold 相似度阈值
     * @return 语义缓存管理器
     */
    fun createSemanticCache(
        vertx: Vertx,
        embeddingEngine: EmbeddingEngine,
        underlyingCache: CacheManager,
        similarityThreshold: Float = 0.8f,
        config: JsonObject = JsonObject()
    ): SemanticCacheManager {
        return SemanticCacheManager(vertx, embeddingEngine, underlyingCache, similarityThreshold, config)
    }

    /**
     * 从配置创建语义缓存管理器
     * @param vertx Vertx 实例
     * @param config 缓存配置
     * @return 语义缓存管理器
     */
    fun createFromConfig(vertx: Vertx, config: JsonObject): SemanticCacheManager {
        // 创建嵌入式引擎
        val embeddingConfig = config.getJsonObject("embedding", JsonObject())
        val embeddingType = embeddingConfig.getString("type", "simple")

        val embeddingEngine = when (embeddingType.lowercase()) {
            "simple" -> createSimpleEmbeddingEngine(vertx)
            "optimized" -> createOptimizedEmbeddingEngine(vertx, embeddingConfig)
            else -> {
                logger.warn("Unknown embedding type: $embeddingType, falling back to simple embedding engine")
                createSimpleEmbeddingEngine(vertx)
            }
        }

        // 创建底层缓存
        val underlyingCacheConfig = config.getJsonObject("underlyingCache", JsonObject())
        val underlyingCache = CacheFactory.createFromConfig(vertx, underlyingCacheConfig)

        // 创建语义缓存
        val similarityThreshold = config.getFloat("similarityThreshold", 0.8f)

        // 传递完整的配置对象
        return createSemanticCache(vertx, embeddingEngine, underlyingCache, similarityThreshold, config)
    }
}
