package com.louloulin.apix.cache.semantic

import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import com.louloulin.apix.core.verticle.BaseVerticle
import com.louloulin.apix.core.common.EventBusAddresses
import org.slf4j.LoggerFactory

/**
 * 语义缓存Verticle，负责启动语义缓存服务。
 */
class SemanticCacheVerticle : BaseVerticle() {
    private val logger = LoggerFactory.getLogger(SemanticCacheVerticle::class.java)

    // 语义缓存管理器
    private lateinit var semanticCacheManager: SemanticCacheManager

    override fun start(startPromise: Promise<Void>) {
        super.start(startPromise)

        // 初始化语义缓存管理器
        semanticCacheManager = SemanticCacheManager.getInstance(vertx)

        // 获取配置
        val cacheConfig = config.getJsonObject("semanticCache", JsonObject())

        // 初始化语义缓存管理器
        semanticCacheManager.initialize(cacheConfig)
            .onSuccess { _ ->
                // 注册事件总线处理器
                registerEventBusHandlers()

                logger.info("语义缓存Verticle启动成功")
                startPromise.complete()
            }
            .onFailure { cause ->
                logger.error("语义缓存Verticle启动失败", cause)
                startPromise.fail(cause)
            }
    }

    /**
     * 注册事件总线处理器。
     */
    private fun registerEventBusHandlers() {
        // 处理语义查询请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_QUERY) { message ->
            val request = message.body()
            val query = request.getString("query")
            val threshold = request.getDouble("threshold", 0.7)

            if (query == null) {
                sendError(message, 400, "Missing query parameter")
                return@consumer
            }

            semanticCacheManager.semanticQuery(query, threshold)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 处理语义缓存请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_STORE) { message ->
            val request = message.body()
            val query = request.getString("query")
            val result = request.getJsonObject("result")
            val vector = request.getJsonArray("vector")

            if (query == null || result == null) {
                sendError(message, 400, "Missing required parameters")
                return@consumer
            }

            semanticCacheManager.semanticStore(query, result, vector)
                .onSuccess { _ ->
                    sendSuccess(message)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 处理语义缓存失效请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_INVALIDATE) { message ->
            val request = message.body()
            val key = request.getString("key")

            if (key == null) {
                sendError(message, 400, "Missing key parameter")
                return@consumer
            }

            semanticCacheManager.semanticInvalidate(key)
                .onSuccess { _ ->
                    sendSuccess(message)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 处理语义缓存同步请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_SYNC) { message ->
            val request = message.body()
            val targetRegion = request.getString("targetRegion")
            val fromVersion = request.getLong("fromVersion", 0L)

            if (targetRegion == null) {
                sendError(message, 400, "Missing targetRegion parameter")
                return@consumer
            }

            semanticCacheManager.syncCache(targetRegion, fromVersion)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 处理向量索引分片请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SEMANTIC_CACHE_SHARD) { message ->
            val request = message.body()
            val action = request.getString("action")

            if (action == null) {
                sendError(message, 400, "Missing action parameter")
                return@consumer
            }

            when (action) {
                "rebalance" -> {
                    semanticCacheManager.rebalanceShards()
                        .onSuccess { result ->
                            sendSuccess(message, result)
                        }
                        .onFailure { cause ->
                            sendError(message, cause)
                        }
                }
                "status" -> {
                    val status = semanticCacheManager.getShardStatus()
                    sendSuccess(message, status)
                }
                else -> {
                    sendError(message, 400, "Unknown action: $action")
                }
            }
        }

        // 处理语义缓存状态请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CACHE_STATUS) { message ->
            val status = semanticCacheManager.getStatus()
            sendSuccess(message, status)
        }
    }
}
