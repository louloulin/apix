package com.louloulin.apix.edge.control

import com.louloulin.apix.core.common.EventBusAddresses
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(VertxExtension::class)
class EdgeControlVerticleTest {
    private lateinit var vertx: Vertx
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx
        
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
        
        // 设置EventBus消息处理器来模拟ConfigVerticle
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_GET) { message ->
            message.reply(configResponse)
        }
        
        // 部署EdgeControlVerticle
        vertx.deployVerticle(EdgeControlVerticle::class.java.name, testContext.succeeding { _ ->
            testContext.completeNow()
        })
    }
    
    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close(testContext.succeeding { _ ->
            testContext.completeNow()
        })
    }
    
    @Test
    fun testGetEdgeControlStatus(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取边缘控制中心状态请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_CONTROL_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getJsonObject("nodeManager") != null) { "Node manager status should be present" }
                    assert(result.getJsonObject("monitoringManager") != null) { "Monitoring manager status should be present" }
                    assert(result.getJsonObject("authManager") != null) { "Auth manager status should be present" }
                    assert(result.getJsonObject("auditLogger") != null) { "Audit logger status should be present" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testGetNodes(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取边缘节点列表请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonArray("result")
                    assert(result.size() == 2) { "Should have 2 nodes" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testGetNodeDetails(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取边缘节点详情请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_DETAILS_GET, JsonObject()
            .put("nodeId", "node1")
        ) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getString("id") == "node1") { "Node ID should match" }
                    assert(result.getString("name") == "Node 1") { "Node name should match" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testGetNodeGroups(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取节点组列表请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_NODE_GROUPS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonArray("result")
                    assert(result.size() == 1) { "Should have 1 group" }
                    assert(result.getJsonObject(0).getString("id") == "group1") { "Group ID should match" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testGetRoles(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取角色列表请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_ROLES_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonArray("result")
                    assert(result.size() == 2) { "Should have 2 roles" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
    
    @Test
    fun testGetAuditLogs(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取审计日志请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.EDGE_AUDIT_LOGS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                
                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonArray("result")
                    assert(result.size() == 1) { "Should have 1 log entry" }
                    
                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }
    }
}
