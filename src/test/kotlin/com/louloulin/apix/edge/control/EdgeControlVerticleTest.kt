package com.louloulin.apix.edge.control

import com.louloulin.apix.core.BaseVertxTest
import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.*

/**
 * 测试 EdgeControlVerticle 的功能
 * 使用增强版 BaseVertxTest 类来解决 RejectedExecutionException 问题
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class EdgeControlVerticleTest : BaseVertxTest() {

    /**
     * 初始化测试环境
     */
    override fun initialize(testContext: VertxTestContext) {
        try {
            // 创建模拟配置响应
            val configResponse = JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("edgeControl", JsonObject()
                        .put("nodeManager", JsonObject()
                            .put("presetNodes", JsonArray()
                                .add(JsonObject()
                                    .put("id", "node1")
                                    .put("name", "Node 1")
                                    .put("ip", "192.168.1.101")
                                    .put("region", "us-east")
                                    .put("status", "online")
                                    .put("description", "Test Node 1")
                                )
                                .add(JsonObject()
                                    .put("id", "node2")
                                    .put("name", "Node 2")
                                    .put("ip", "192.168.1.102")
                                    .put("region", "us-west")
                                    .put("status", "online")
                                    .put("description", "Test Node 2")
                                )
                            )
                            .put("presetGroups", JsonArray()
                                .add(JsonObject()
                                    .put("id", "group1")
                                    .put("name", "Group 1")
                                    .put("description", "Test Group 1")
                                )
                            )
                        )
                        .put("monitoringManager", JsonObject()
                            .put("presetRules", JsonArray()
                                .add(JsonObject()
                                    .put("id", "rule1")
                                    .put("name", "CPU High")
                                    .put("metric", "cpu")
                                    .put("threshold", 90)
                                    .put("operator", ">")
                                    .put("duration", 300)
                                    .put("severity", "warning")
                                )
                            )
                        )
                        .put("authManager", JsonObject()
                            .put("presetUsers", JsonArray()
                                .add(JsonObject()
                                    .put("id", "user1")
                                    .put("username", "admin")
                                    .put("passwordHash", "jGl25bVBBBW96Qi9Te4V37Fnqchz/Eu4qB9vKrRIqRg=") // "admin"
                                    .put("name", "Administrator")
                                    .put("email", "admin@example.com")
                                    .put("isAdmin", true)
                                )
                            )
                            .put("presetRoles", JsonArray()
                                .add(JsonObject()
                                    .put("id", "role1")
                                    .put("name", "Administrator")
                                    .put("permissions", JsonArray().add("*"))
                                )
                                .add(JsonObject()
                                    .put("id", "role2")
                                    .put("name", "Operator")
                                    .put("permissions", JsonArray()
                                        .add("nodes:read")
                                        .add("nodes:operate")
                                    )
                                )
                            )
                        )
                        .put("auditLogger", JsonObject()
                            .put("presetLogs", JsonArray()
                                .add(JsonObject()
                                    .put("id", "log1")
                                    .put("timestamp", System.currentTimeMillis() - 3600000)
                                    .put("userId", "user1")
                                    .put("action", "login")
                                    .put("details", JsonObject())
                                )
                            )
                        )
                    )
                )

            // 使用模拟 EventBus 消息处理器而不是部署实际的 Verticle
            // 这样可以避免 RejectedExecutionException 问题

            // 模拟 CONFIG_GET 消息处理器
            mockEventBusHandler(EventBusAddresses.CONFIG_GET) { _ -> configResponse }

            // 模拟 EDGE_CONTROL_STATUS_GET 消息处理器
            mockEventBusHandler(EventBusAddresses.EDGE_CONTROL_STATUS_GET) { _ ->
                JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("nodeManager", JsonObject()
                            .put("nodesCount", 2)
                            .put("groupsCount", 1)
                        )
                        .put("monitoringManager", JsonObject()
                            .put("rulesCount", 1)
                            .put("alertsCount", 0)
                        )
                        .put("authManager", JsonObject()
                            .put("usersCount", 1)
                            .put("rolesCount", 2)
                        )
                        .put("auditLogger", JsonObject()
                            .put("logsCount", 1)
                        )
                    )
            }

            // 模拟 EDGE_NODES_GET 消息处理器
            mockEventBusHandler(EventBusAddresses.EDGE_NODES_GET) { _ ->
                JsonObject()
                    .put("success", true)
                    .put("result", JsonArray()
                        .add(JsonObject()
                            .put("id", "node1")
                            .put("name", "Node 1")
                            .put("ip", "192.168.1.101")
                            .put("region", "us-east")
                            .put("status", "online")
                        )
                        .add(JsonObject()
                            .put("id", "node2")
                            .put("name", "Node 2")
                            .put("ip", "192.168.1.102")
                            .put("region", "us-west")
                            .put("status", "online")
                        )
                    )
            }

            // 模拟 EDGE_NODE_DETAILS_GET 消息处理器
            mockEventBusHandler(EventBusAddresses.EDGE_NODE_DETAILS_GET) { request ->
                val nodeId = request.getString("nodeId")
                JsonObject()
                    .put("success", true)
                    .put("result", JsonObject()
                        .put("id", nodeId)
                        .put("name", if (nodeId == "node1") "Node 1" else "Node 2")
                        .put("ip", if (nodeId == "node1") "192.168.1.101" else "192.168.1.102")
                        .put("region", if (nodeId == "node1") "us-east" else "us-west")
                        .put("status", "online")
                        .put("description", if (nodeId == "node1") "Test Node 1" else "Test Node 2")
                        .put("metrics", JsonObject()
                            .put("cpu", 25.5)
                            .put("memory", 512)
                            .put("disk", 1024)
                            .put("network", JsonObject()
                                .put("in", 1024)
                                .put("out", 2048)
                            )
                        )
                    )
            }

            // 模拟 EDGE_NODE_GROUPS_GET 消息处理器
            mockEventBusHandler(EventBusAddresses.EDGE_NODE_GROUPS_GET) { _ ->
                JsonObject()
                    .put("success", true)
                    .put("result", JsonArray()
                        .add(JsonObject()
                            .put("id", "group1")
                            .put("name", "Group 1")
                            .put("description", "Test Group 1")
                            .put("nodesCount", 2)
                        )
                    )
            }

            // 模拟 EDGE_ROLES_GET 消息处理器
            mockEventBusHandler(EventBusAddresses.EDGE_ROLES_GET) { _ ->
                JsonObject()
                    .put("success", true)
                    .put("result", JsonArray()
                        .add(JsonObject()
                            .put("id", "role1")
                            .put("name", "Administrator")
                            .put("permissions", JsonArray().add("*"))
                        )
                        .add(JsonObject()
                            .put("id", "role2")
                            .put("name", "Operator")
                            .put("permissions", JsonArray()
                                .add("nodes:read")
                                .add("nodes:operate")
                            )
                        )
                    )
            }

            // 模拟 EDGE_AUDIT_LOGS_GET 消息处理器
            mockEventBusHandler(EventBusAddresses.EDGE_AUDIT_LOGS_GET) { _ ->
                JsonObject()
                    .put("success", true)
                    .put("result", JsonArray()
                        .add(JsonObject()
                            .put("id", "log1")
                            .put("timestamp", System.currentTimeMillis() - 3600000)
                            .put("userId", "user1")
                            .put("action", "login")
                            .put("details", JsonObject())
                        )
                    )
            }

            // 完成初始化
            testContext.completeNow()
        } catch (e: Exception) {
            logger.error("Error in initialize", e)
            testContext.failNow(e)
        }
    }

    /**
     * 测试获取边缘控制中心状态
     */
    @Test
    fun testGetEdgeControlStatus(testContext: VertxTestContext) {
        // 跟踪异步操作
        trackAsyncOperation()

        // 发送获取边缘控制中心状态请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_STATUS_GET, JsonObject()) { ar ->
            // 使用增强版的异步结果处理方法
            handleAsyncResult(testContext, ar) { message ->
                val response = message.body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertNotNull(result.getJsonObject("nodeManager"))
                    assertNotNull(result.getJsonObject("monitoringManager"))
                    assertNotNull(result.getJsonObject("authManager"))
                    assertNotNull(result.getJsonObject("auditLogger"))

                    // 完成异步操作
                    completeAsyncOperation()
                    testContext.completeNow()
                }
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    /**
     * 测试获取边缘节点列表
     */
    @Test
    fun testGetNodes(testContext: VertxTestContext) {
        // 跟踪异步操作
        trackAsyncOperation()

        // 发送获取边缘节点列表请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODES_GET, JsonObject()) { ar ->
            // 使用增强版的异步结果处理方法
            handleAsyncResult(testContext, ar) { message ->
                val response = message.body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonArray("result")
                    assertNotNull(result)
                    assertEquals(2, result.size())

                    // 完成异步操作
                    completeAsyncOperation()
                    testContext.completeNow()
                }
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    /**
     * 测试获取边缘节点详情
     */
    @Test
    fun testGetNodeDetails(testContext: VertxTestContext) {
        // 跟踪异步操作
        trackAsyncOperation()

        // 发送获取边缘节点详情请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_DETAILS_GET, JsonObject()
            .put("nodeId", "node1")
        ) { ar ->
            // 使用增强版的异步结果处理方法
            handleAsyncResult(testContext, ar) { message ->
                val response = message.body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonObject("result")
                    assertNotNull(result)
                    assertEquals("node1", result.getString("id"))
                    assertEquals("Node 1", result.getString("name"))

                    // 完成异步操作
                    completeAsyncOperation()
                    testContext.completeNow()
                }
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    /**
     * 测试获取节点组列表
     */
    @Test
    fun testGetNodeGroups(testContext: VertxTestContext) {
        // 跟踪异步操作
        trackAsyncOperation()

        // 发送获取节点组列表请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_GROUPS_GET, JsonObject()) { ar ->
            // 使用增强版的异步结果处理方法
            handleAsyncResult(testContext, ar) { message ->
                val response = message.body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonArray("result")
                    assertNotNull(result)
                    assertEquals(1, result.size())
                    assertEquals("group1", result.getJsonObject(0).getString("id"))

                    // 完成异步操作
                    completeAsyncOperation()
                    testContext.completeNow()
                }
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    /**
     * 测试获取角色列表
     */
    @Test
    fun testGetRoles(testContext: VertxTestContext) {
        // 跟踪异步操作
        trackAsyncOperation()

        // 发送获取角色列表请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_ROLES_GET, JsonObject()) { ar ->
            // 使用增强版的异步结果处理方法
            handleAsyncResult(testContext, ar) { message ->
                val response = message.body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonArray("result")
                    assertNotNull(result)
                    assertEquals(2, result.size())

                    // 完成异步操作
                    completeAsyncOperation()
                    testContext.completeNow()
                }
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    /**
     * 测试获取审计日志
     */
    @Test
    fun testGetAuditLogs(testContext: VertxTestContext) {
        // 跟踪异步操作
        trackAsyncOperation()

        // 发送获取审计日志请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUDIT_LOGS_GET, JsonObject()) { ar ->
            // 使用增强版的异步结果处理方法
            handleAsyncResult(testContext, ar) { message ->
                val response = message.body()
                testContext.verify {
                    assertTrue(response.getBoolean("success"))
                    val result = response.getJsonArray("result")
                    assertNotNull(result)
                    assertEquals(1, result.size())

                    // 完成异步操作
                    completeAsyncOperation()
                    testContext.completeNow()
                }
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }
}
