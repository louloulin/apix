package com.louloulin.apix.edge.deploy.mesh

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class IstioIntegrationManagerTest {
    private val logger = LoggerFactory.getLogger(IstioIntegrationManagerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var istioManager: IstioIntegrationManager

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        istioManager = IstioIntegrationManager(vertx)

        // 创建测试配置
        val config = JsonObject()
            .put("virtualServices", JsonArray()
                .add(JsonObject()
                    .put("id", "test-vs")
                    .put("name", "Test VirtualService")
                    .put("hosts", JsonArray().add("test.example.com"))
                    .put("gateways", JsonArray().add("test-gateway"))
                )
            )
            .put("destinationRules", JsonArray()
                .add(JsonObject()
                    .put("id", "test-dr")
                    .put("name", "Test DestinationRule")
                    .put("host", "test-service")
                    .put("subsets", JsonArray()
                        .add(JsonObject()
                            .put("name", "v1")
                            .put("labels", JsonObject().put("version", "v1"))
                        )
                    )
                )
            )
            .put("gateways", JsonArray()
                .add(JsonObject()
                    .put("id", "test-gateway")
                    .put("name", "Test Gateway")
                    .put("selector", JsonObject().put("istio", "ingressgateway"))
                    .put("servers", JsonArray()
                        .add(JsonObject()
                            .put("port", JsonObject()
                                .put("number", 80)
                                .put("name", "http")
                                .put("protocol", "HTTP")
                            )
                            .put("hosts", JsonArray().add("*"))
                        )
                    )
                )
            )
            .put("securityPolicies", JsonArray()
                .add(JsonObject()
                    .put("id", "test-policy")
                    .put("name", "Test AuthorizationPolicy")
                    .put("namespace", "default")
                    .put("selector", JsonObject().put("app", "test-app"))
                )
            )

        // 初始化 Istio 集成管理器
        istioManager.initialize(config)
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        istioManager.close()
            .onSuccess {
                vertx.close()
                    .onSuccess {
                        testContext.completeNow()
                    }
                    .onFailure { cause ->
                        testContext.failNow(cause)
                    }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetVirtualServices(testContext: VertxTestContext) {
        istioManager.getVirtualServices()
            .onSuccess { virtualServices ->
                testContext.verify {
                    assert(virtualServices.size() == 1)
                    assert(virtualServices.getJsonObject(0).getString("id") == "test-vs")
                    assert(virtualServices.getJsonObject(0).getString("name") == "Test VirtualService")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetVirtualServiceDetails(testContext: VertxTestContext) {
        istioManager.getVirtualServiceDetails("test-vs")
            .onSuccess { vs ->
                testContext.verify {
                    assert(vs.getString("id") == "test-vs")
                    assert(vs.getString("name") == "Test VirtualService")
                    assert(vs.getJsonArray("hosts").getString(0) == "test.example.com")
                    assert(vs.getJsonArray("gateways").getString(0) == "test-gateway")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testCreateVirtualService(testContext: VertxTestContext) {
        val newVs = JsonObject()
            .put("name", "New VirtualService")
            .put("hosts", JsonArray().add("new.example.com"))
            .put("gateways", JsonArray().add("test-gateway"))
            .put("http", JsonArray()
                .add(JsonObject()
                    .put("route", JsonArray()
                        .add(JsonObject()
                            .put("destination", JsonObject()
                                .put("host", "new-service")
                                .put("port", JsonObject().put("number", 8080))
                            )
                        )
                    )
                )
            )

        istioManager.createVirtualService(newVs)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "虚拟服务创建中")
                    assert(result.getString("id") != null)

                    // 等待异步创建完成
                    vertx.setTimer(3000) {
                        istioManager.getResourceStatus(result.getString("id"))
                            .onSuccess { status ->
                                testContext.verify {
                                    assert(status.getString("phase") == "Active")
                                    assert(status.getString("message") == "虚拟服务创建成功")
                                    testContext.completeNow()
                                }
                            }
                            .onFailure { cause ->
                                testContext.failNow(cause)
                            }
                    }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testUpdateVirtualService(testContext: VertxTestContext) {
        val updatedVs = JsonObject()
            .put("hosts", JsonArray().add("updated.example.com"))

        istioManager.updateVirtualService("test-vs", updatedVs)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "虚拟服务更新中")
                    assert(result.getString("id") == "test-vs")

                    // 等待异步更新完成
                    vertx.setTimer(3000) {
                        istioManager.getVirtualServiceDetails("test-vs")
                            .onSuccess { vs ->
                                testContext.verify {
                                    assert(vs.getJsonArray("hosts").getString(0) == "updated.example.com")
                                    testContext.completeNow()
                                }
                            }
                            .onFailure { cause ->
                                testContext.failNow(cause)
                            }
                    }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testDeleteVirtualService(testContext: VertxTestContext) {
        istioManager.deleteVirtualService("test-vs")
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "虚拟服务删除中")
                    assert(result.getString("id") == "test-vs")

                    // 等待异步删除完成
                    vertx.setTimer(3000) {
                        istioManager.getVirtualServices()
                            .onSuccess { virtualServices ->
                                testContext.verify {
                                    assert(virtualServices.size() == 0)
                                    testContext.completeNow()
                                }
                            }
                            .onFailure { cause ->
                                testContext.failNow(cause)
                            }
                    }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetDestinationRules(testContext: VertxTestContext) {
        istioManager.getDestinationRules()
            .onSuccess { destinationRules ->
                testContext.verify {
                    assert(destinationRules.size() == 1)
                    assert(destinationRules.getJsonObject(0).getString("id") == "test-dr")
                    assert(destinationRules.getJsonObject(0).getString("name") == "Test DestinationRule")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetDestinationRuleDetails(testContext: VertxTestContext) {
        istioManager.getDestinationRuleDetails("test-dr")
            .onSuccess { dr ->
                testContext.verify {
                    assert(dr.getString("id") == "test-dr")
                    assert(dr.getString("name") == "Test DestinationRule")
                    assert(dr.getString("host") == "test-service")
                    assert(dr.getJsonArray("subsets").getJsonObject(0).getString("name") == "v1")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testCreateDestinationRule(testContext: VertxTestContext) {
        val newDr = JsonObject()
            .put("name", "New DestinationRule")
            .put("host", "new-service")
            .put("trafficPolicy", JsonObject()
                .put("loadBalancer", JsonObject()
                    .put("simple", "ROUND_ROBIN")
                )
            )
            .put("subsets", JsonArray()
                .add(JsonObject()
                    .put("name", "v1")
                    .put("labels", JsonObject().put("version", "v1"))
                )
                .add(JsonObject()
                    .put("name", "v2")
                    .put("labels", JsonObject().put("version", "v2"))
                )
            )

        istioManager.createDestinationRule(newDr)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "目标规则创建中")
                    assert(result.getString("id") != null)

                    // 等待异步创建完成
                    vertx.setTimer(3000) {
                        istioManager.getResourceStatus(result.getString("id"))
                            .onSuccess { status ->
                                testContext.verify {
                                    assert(status.getString("phase") == "Active")
                                    assert(status.getString("message") == "目标规则创建成功")
                                    testContext.completeNow()
                                }
                            }
                            .onFailure { cause ->
                                testContext.failNow(cause)
                            }
                    }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testEnableMTLS(testContext: VertxTestContext) {
        istioManager.enableMTLS("default", "STRICT")
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "mTLS 启用中")
                    assert(result.getString("id") != null)

                    // 等待异步创建完成
                    vertx.setTimer(3000) {
                        istioManager.getResourceStatus(result.getString("id"))
                            .onSuccess { status ->
                                testContext.verify {
                                    assert(status.getString("phase") == "Active")
                                    assert(status.getString("message") == "mTLS 启用成功")
                                    testContext.completeNow()
                                }
                            }
                            .onFailure { cause ->
                                testContext.failNow(cause)
                            }
                    }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testCreateAuthorizationPolicy(testContext: VertxTestContext) {
        val newPolicy = JsonObject()
            .put("name", "New AuthorizationPolicy")
            .put("namespace", "default")
            .put("selector", JsonObject().put("app", "new-app"))
            .put("action", "ALLOW")
            .put("rules", JsonArray()
                .add(JsonObject()
                    .put("from", JsonArray()
                        .add(JsonObject()
                            .put("source", JsonObject()
                                .put("namespaces", JsonArray().add("default"))
                            )
                        )
                    )
                    .put("to", JsonArray()
                        .add(JsonObject()
                            .put("operation", JsonObject()
                                .put("methods", JsonArray().add("GET").add("POST"))
                                .put("paths", JsonArray().add("/api/*"))
                            )
                        )
                    )
                )
            )

        istioManager.createAuthorizationPolicy(newPolicy)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "授权策略创建中")
                    assert(result.getString("id") != null)

                    // 等待异步创建完成
                    vertx.setTimer(3000) {
                        istioManager.getResourceStatus(result.getString("id"))
                            .onSuccess { status ->
                                testContext.verify {
                                    assert(status.getString("phase") == "Active")
                                    assert(status.getString("message") == "授权策略创建成功")
                                    testContext.completeNow()
                                }
                            }
                            .onFailure { cause ->
                                testContext.failNow(cause)
                            }
                    }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetAllResourceStatus(testContext: VertxTestContext) {
        // 先创建一些资源
        val newVs = JsonObject()
            .put("name", "Status Test VirtualService")
            .put("hosts", JsonArray().add("status.example.com"))
            .put("gateways", JsonArray().add("test-gateway"))

        istioManager.createVirtualService(newVs)
            .compose { result ->
                val vsId = result.getString("id")

                // 创建授权策略
                val newPolicy = JsonObject()
                    .put("name", "Status Test AuthorizationPolicy")
                    .put("namespace", "default")
                    .put("selector", JsonObject().put("app", "status-app"))

                istioManager.createAuthorizationPolicy(newPolicy)
            }
            .compose<Void> { result ->
                // 等待异步创建完成
                vertx.setTimer(3000) {
                    // 获取所有资源状态
                    istioManager.getAllResourceStatus()
                        .onSuccess { statusList ->
                            testContext.verify {
                                assert(statusList.size() >= 2)
                                for (i in 0 until statusList.size()) {
                                    val status = statusList.getJsonObject(i)
                                    assert(status.getString("phase") == "Active" || status.getString("phase") == "Creating")
                                    assert(status.getString("type") != null)
                                    assert(status.getString("message") != null)
                                }
                                testContext.completeNow()
                            }
                        }
                        .onFailure { cause ->
                            testContext.failNow(cause)
                        }
                }

                return@compose null
            }
    }
}
