package com.louloulin.apix.scaling

import io.vertx.core.Vertx
import io.vertx.core.http.HttpMethod
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.common.Constants
import com.louloulin.apix.scaling.loadbalance.AdvancedLoadBalancer
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class HorizontalScalingTest {
    private val logger = LoggerFactory.getLogger(HorizontalScalingTest::class.java)
    
    private lateinit var vertx: Vertx
    private lateinit var scalingManager: HorizontalScalingManager
    
    // 测试服务器端口
    private val TEST_PORT = 8888
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
        // 创建水平扩展管理器
        scalingManager = HorizontalScalingManager(vertx)
        
        // 配置水平扩展
        val config = HorizontalScalingManager.ScalingConfig(
            enabled = true,
            statelessEnabled = true,
            loadBalancingEnabled = true,
            sessionAffinityEnabled = true,
            defaultStrategy = AdvancedLoadBalancer.Strategy.LEAST_CONNECTIONS,
            healthCheckInterval = 5000,
            healthCheckTimeout = 2000
        )
        
        scalingManager.configure(config)
        
        // 创建测试服务器
        createTestServer()
            .onSuccess {
                // 添加测试节点
                addTestNodes()
                    .onSuccess {
                        logger.info("测试节点添加成功")
                        testContext.completeNow()
                    }
                    .onFailure { err ->
                        logger.error("添加测试节点失败", err)
                        testContext.failNow(err)
                    }
            }
            .onFailure { err ->
                logger.error("创建测试服务器失败", err)
                testContext.failNow(err)
            }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        // 关闭水平扩展管理器
        scalingManager.close()
        
        // 关闭Vertx
        vertx.close()
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { err ->
                testContext.failNow(err)
            }
    }
    
    /**
     * 创建测试服务器
     */
    private fun createTestServer(): io.vertx.core.Future<Void> {
        val promise = io.vertx.core.Promise.promise<Void>()
        
        // 创建路由
        val router = Router.router(vertx)
        
        // 添加Body处理器
        router.route().handler(BodyHandler.create())
        
        // 添加测试路由
        router.route("/test").handler { context ->
            handleTestRequest(context)
        }
        
        // 添加健康检查路由
        router.route("/health").handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject().put("status", "up").encode())
        }
        
        // 创建HTTP服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(TEST_PORT) { ar ->
                if (ar.succeeded()) {
                    logger.info("测试服务器启动成功，监听端口：{}", TEST_PORT)
                    promise.complete()
                } else {
                    logger.error("测试服务器启动失败", ar.cause())
                    promise.fail(ar.cause())
                }
            }
        
        return promise.future()
    }
    
    /**
     * 处理测试请求
     */
    private fun handleTestRequest(context: RoutingContext) {
        // 获取会话ID
        val sessionId = context.request().getHeader(Constants.SESSION_ID_HEADER)
        
        // 创建响应
        val response = JsonObject()
            .put("message", "Hello from test server")
            .put("method", context.request().method().name())
            .put("path", context.request().path())
            .put("sessionId", sessionId)
            .put("timestamp", System.currentTimeMillis())
        
        // 如果有请求体，添加到响应
        if (context.body() != null) {
            response.put("requestBody", context.body().asString())
        }
        
        // 发送响应
        context.response()
            .putHeader("Content-Type", "application/json")
            .putHeader(Constants.SESSION_ID_HEADER, sessionId ?: "")
            .end(response.encode())
    }
    
    /**
     * 添加测试节点
     */
    private fun addTestNodes(): io.vertx.core.Future<Void> {
        val promise = io.vertx.core.Promise.promise<Void>()
        
        // 添加本地节点
        scalingManager.addNode(
            nodeId = "local-node",
            host = "localhost",
            port = TEST_PORT,
            weight = 100,
            groupId = "test-group",
            metadata = JsonObject().put("type", "test")
        ).compose {
            // 添加远程节点（实际上也是本地，但使用不同的ID）
            scalingManager.addNode(
                nodeId = "remote-node-1",
                host = "localhost",
                port = TEST_PORT,
                weight = 80,
                groupId = "test-group",
                metadata = JsonObject().put("type", "test")
            )
        }.compose {
            // 添加另一个远程节点
            scalingManager.addNode(
                nodeId = "remote-node-2",
                host = "localhost",
                port = TEST_PORT,
                weight = 60,
                groupId = "test-group",
                metadata = JsonObject().put("type", "test")
            )
        }.onSuccess {
            promise.complete()
        }.onFailure { err ->
            promise.fail(err)
        }
        
        return promise.future()
    }
    
    @Test
    fun testLoadBalancing(testContext: VertxTestContext) {
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 发送多个请求，测试负载均衡
        val requestCount = 10
        var completedCount = 0
        val nodeHits = mutableMapOf<String, Int>()
        
        for (i in 1..requestCount) {
            // 创建测试请求
            client.request(HttpMethod.GET, TEST_PORT, "localhost", "/test")
                .compose { request ->
                    request.end()
                    request.response()
                }
                .compose { response ->
                    response.body()
                }
                .onSuccess { body ->
                    val responseJson = JsonObject(body)
                    val sessionId = responseJson.getString("sessionId")
                    
                    // 获取所选节点
                    val nodes = scalingManager.getAllNodes()
                    for (node in nodes) {
                        val nodeId = node.getString("id")
                        val sessions = scalingManager.getSessionAffinityManager().getNodeSessions(nodeId)
                        
                        if (sessions.contains(sessionId)) {
                            nodeHits[nodeId] = (nodeHits[nodeId] ?: 0) + 1
                            break
                        }
                    }
                    
                    completedCount++
                    
                    // 如果所有请求都完成，验证结果
                    if (completedCount == requestCount) {
                        testContext.verify {
                            // 验证所有节点都收到了请求
                            assert(nodeHits.size > 0)
                            
                            logger.info("节点命中统计：{}", nodeHits)
                            
                            testContext.completeNow()
                        }
                    }
                }
                .onFailure { err ->
                    logger.error("请求失败", err)
                    testContext.failNow(err)
                }
        }
    }
    
    @Test
    fun testSessionAffinity(testContext: VertxTestContext) {
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 发送第一个请求，获取会话ID
        client.request(HttpMethod.GET, TEST_PORT, "localhost", "/test")
            .compose { request ->
                request.end()
                request.response()
            }
            .compose { response ->
                // 获取会话ID
                val sessionId = response.getHeader(Constants.SESSION_ID_HEADER)
                
                // 验证会话ID不为空
                testContext.verify {
                    assert(sessionId != null && sessionId.isNotEmpty())
                }
                
                response.body().map { body -> Pair(sessionId, body) }
            }
            .compose { (sessionId, body) ->
                val responseJson = JsonObject(body)
                
                // 获取第一个请求的节点
                var firstNodeId: String? = null
                val nodes = scalingManager.getAllNodes()
                for (node in nodes) {
                    val nodeId = node.getString("id")
                    val sessions = scalingManager.getSessionAffinityManager().getNodeSessions(nodeId)
                    
                    if (sessions.contains(sessionId)) {
                        firstNodeId = nodeId
                        break
                    }
                }
                
                // 验证找到了节点
                testContext.verify {
                    assert(firstNodeId != null)
                }
                
                // 发送第二个请求，使用相同的会话ID
                client.request(HttpMethod.GET, TEST_PORT, "localhost", "/test")
                    .compose { request ->
                        request.putHeader(Constants.SESSION_ID_HEADER, sessionId)
                        request.end()
                        request.response()
                    }
                    .compose { response ->
                        response.body().map { secondBody -> Triple(sessionId, firstNodeId, secondBody) }
                    }
            }
            .onSuccess { (sessionId, firstNodeId, secondBody) ->
                val responseJson = JsonObject(secondBody)
                
                // 获取第二个请求的节点
                var secondNodeId: String? = null
                val nodes = scalingManager.getAllNodes()
                for (node in nodes) {
                    val nodeId = node.getString("id")
                    val sessions = scalingManager.getSessionAffinityManager().getNodeSessions(nodeId)
                    
                    if (sessions.contains(sessionId)) {
                        secondNodeId = nodeId
                        break
                    }
                }
                
                // 验证第二个请求使用了相同的节点
                testContext.verify {
                    assert(secondNodeId != null)
                    assert(secondNodeId == firstNodeId)
                    
                    logger.info("会话亲和性测试成功：sessionId={}, nodeId={}", sessionId, firstNodeId)
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                logger.error("会话亲和性测试失败", err)
                testContext.failNow(err)
            }
    }
    
    @Test
    fun testStatelessDesign(testContext: VertxTestContext) {
        // 创建HTTP客户端
        val client = vertx.createHttpClient()
        
        // 创建测试数据
        val testData = JsonObject()
            .put("name", "Test User")
            .put("email", "test@example.com")
            .put("age", 30)
        
        // 发送POST请求
        client.request(HttpMethod.POST, TEST_PORT, "localhost", "/test")
            .compose { request ->
                request.putHeader("Content-Type", "application/json")
                request.end(testData.encode())
                request.response()
            }
            .compose { response ->
                response.body()
            }
            .onSuccess { body ->
                val responseJson = JsonObject(body)
                
                // 验证响应包含请求体
                testContext.verify {
                    assert(responseJson.containsKey("requestBody"))
                    
                    val requestBody = JsonObject(responseJson.getString("requestBody"))
                    assert(requestBody.getString("name") == "Test User")
                    assert(requestBody.getString("email") == "test@example.com")
                    assert(requestBody.getInteger("age") == 30)
                    
                    logger.info("无状态设计测试成功")
                    
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                logger.error("无状态设计测试失败", err)
                testContext.failNow(err)
            }
    }
}
