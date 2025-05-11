package com.louloulin.apix.cache.semantic

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.*
import com.louloulin.apix.core.common.EventBusAddresses
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class SemanticCacheManagerTest {

    private lateinit var vertx: Vertx
    private lateinit var semanticCacheManager: SemanticCacheManager

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 注册模拟的事件总线处理器
        setupMockEventBusHandlers()
        
        // 创建语义缓存管理器
        semanticCacheManager = SemanticCacheManager.getInstance(vertx)
        
        // 初始化语义缓存管理器
        val config = JsonObject()
            .put("vector", JsonObject()
                .put("maxResults", 10)
            )
            .put("shard", JsonObject()
                .put("initialShardCount", 3)
                .put("maxShardSize", 1000)
            )
            .put("sync", JsonObject()
                .put("localRegion", "region1")
                .put("regions", JsonArray().add("region1").add("region2"))
                .put("syncInterval", 5000)
                .put("maxLogSize", 1000)
            )
        
        semanticCacheManager.initialize(config)
            .onComplete(testContext.succeedingThenComplete())
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        testContext.completeNow()
    }

    /**
     * 设置模拟的事件总线处理器。
     */
    private fun setupMockEventBusHandlers() {
        // 模拟向量生成
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.VECTOR_GENERATE) { message ->
            val request = message.body()
            val text = request.getString("text")
            
            // 生成模拟向量
            val vector = JsonArray()
            for (i in 0 until 10) {
                vector.add(Math.random())
            }
            
            message.reply(JsonObject()
                .put("success", true)
                .put("vector", vector)
            )
        }
        
        // 模拟集群服务
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_LOCAL_NODE_ID) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("nodeId", "node1")
            )
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_SHARD_ASSIGNMENT) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("assignment", JsonObject())
            )
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_SET_SHARD_ASSIGNMENT) { message ->
            message.reply(JsonObject()
                .put("success", true)
            )
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_LOCAL_REGION) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("region", "region1")
            )
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_REGIONS) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("regions", JsonArray().add("region1").add("region2"))
            )
        }
        
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CLUSTER_GET_NODES) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("nodes", JsonArray().add(JsonObject().put("id", "node1")))
            )
        }
    }

    @Test
    fun testSemanticStoreAndQuery(testContext: VertxTestContext) {
        // 存储语义缓存
        val query = "What is the capital of France?"
        val result = JsonObject()
            .put("answer", "Paris")
            .put("confidence", 0.95)
        
        semanticCacheManager.semanticStore(query, result, null)
            .compose { _ ->
                // 查询语义缓存
                semanticCacheManager.semanticQuery(query, 0.7)
            }
            .onComplete(testContext.succeeding { queryResult ->
                // 验证查询结果
                testContext.verify {
                    assertTrue(queryResult.getInteger("count") > 0)
                    val results = queryResult.getJsonArray("results")
                    assertTrue(results.size() > 0)
                    
                    val firstResult = results.getJsonObject(0)
                    assertTrue(firstResult.getDouble("similarity") > 0.7)
                    
                    val resultData = firstResult.getJsonObject("result")
                    assertEquals("Paris", resultData.getString("answer"))
                    assertEquals(0.95, resultData.getDouble("confidence"))
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testSemanticInvalidate(testContext: VertxTestContext) {
        // 存储语义缓存
        val query = "What is the capital of Germany?"
        val result = JsonObject()
            .put("answer", "Berlin")
            .put("confidence", 0.95)
        
        semanticCacheManager.semanticStore(query, result, null)
            .compose { _ ->
                // 生成缓存键
                val key = "semantic:${query.hashCode()}"
                
                // 使缓存失效
                semanticCacheManager.semanticInvalidate(key)
            }
            .compose { _ ->
                // 查询语义缓存
                semanticCacheManager.semanticQuery(query, 0.7)
            }
            .onComplete(testContext.succeeding { queryResult ->
                // 验证查询结果
                testContext.verify {
                    assertEquals(0, queryResult.getInteger("count"))
                    val results = queryResult.getJsonArray("results")
                    assertEquals(0, results.size())
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testRebalanceShards(testContext: VertxTestContext) {
        // 重新平衡分片
        semanticCacheManager.rebalanceShards()
            .onComplete(testContext.succeeding { result ->
                // 验证结果
                testContext.verify {
                    assertTrue(result.containsKey("migratedShards"))
                    assertTrue(result.containsKey("totalShards"))
                    assertTrue(result.containsKey("shards"))
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testGetStatus(testContext: VertxTestContext) {
        // 获取状态
        val status = semanticCacheManager.getStatus()
        
        // 验证状态
        testContext.verify {
            assertTrue(status.containsKey("cacheSize"))
            assertTrue(status.containsKey("cacheVersion"))
            assertTrue(status.containsKey("vectorIndexSize"))
            assertTrue(status.containsKey("shardStatus"))
            assertTrue(status.containsKey("syncStatus"))
        }
        
        testContext.completeNow()
    }

    @Test
    fun testSimilarQueries(testContext: VertxTestContext) {
        // 存储语义缓存
        val query1 = "What is the capital of France?"
        val result1 = JsonObject()
            .put("answer", "Paris")
            .put("confidence", 0.95)
        
        semanticCacheManager.semanticStore(query1, result1, null)
            .compose { _ ->
                // 查询相似的问题
                semanticCacheManager.semanticQuery("What's the capital city of France?", 0.7)
            }
            .onComplete(testContext.succeeding { queryResult ->
                // 验证查询结果
                testContext.verify {
                    assertTrue(queryResult.getInteger("count") > 0)
                    val results = queryResult.getJsonArray("results")
                    assertTrue(results.size() > 0)
                    
                    val firstResult = results.getJsonObject(0)
                    assertTrue(firstResult.getDouble("similarity") > 0.7)
                    
                    val resultData = firstResult.getJsonObject("result")
                    assertEquals("Paris", resultData.getString("answer"))
                }
                
                testContext.completeNow()
            })
    }

    @Test
    fun testMultipleQueriesAndSharding(testContext: VertxTestContext) {
        // 存储多个语义缓存
        val queries = listOf(
            "What is the capital of France?",
            "What is the capital of Germany?",
            "What is the capital of Italy?",
            "What is the capital of Spain?",
            "What is the capital of Portugal?"
        )
        
        val results = listOf(
            JsonObject().put("answer", "Paris").put("confidence", 0.95),
            JsonObject().put("answer", "Berlin").put("confidence", 0.95),
            JsonObject().put("answer", "Rome").put("confidence", 0.95),
            JsonObject().put("answer", "Madrid").put("confidence", 0.95),
            JsonObject().put("answer", "Lisbon").put("confidence", 0.95)
        )
        
        // 存储所有查询
        var future = io.vertx.core.Future.succeededFuture<Void>()
        for (i in queries.indices) {
            val query = queries[i]
            val result = results[i]
            future = future.compose { semanticCacheManager.semanticStore(query, result, null) }
        }
        
        future
            .compose { _ ->
                // 获取分片状态
                val shardStatus = semanticCacheManager.getShardStatus()
                
                // 验证分片状态
                testContext.verify {
                    assertTrue(shardStatus.getInteger("totalShards") > 0)
                    assertTrue(shardStatus.getInteger("localShards") > 0)
                }
                
                // 查询一个问题
                semanticCacheManager.semanticQuery("What is the capital of Italy?", 0.7)
            }
            .onComplete(testContext.succeeding { queryResult ->
                // 验证查询结果
                testContext.verify {
                    assertTrue(queryResult.getInteger("count") > 0)
                    val results = queryResult.getJsonArray("results")
                    assertTrue(results.size() > 0)
                    
                    val firstResult = results.getJsonObject(0)
                    assertTrue(firstResult.getDouble("similarity") > 0.7)
                    
                    val resultData = firstResult.getJsonObject("result")
                    assertEquals("Rome", resultData.getString("answer"))
                }
                
                testContext.completeNow()
            })
    }
}
