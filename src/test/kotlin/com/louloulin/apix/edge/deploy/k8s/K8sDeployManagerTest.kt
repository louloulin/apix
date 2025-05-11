package com.louloulin.apix.edge.deploy.k8s

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
class K8sDeployManagerTest {
    private val logger = LoggerFactory.getLogger(K8sDeployManagerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var deployManager: K8sDeployManager

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        deployManager = K8sDeployManager(vertx)

        // 创建测试配置
        val config = JsonObject()
            .put("clusters", JsonArray()
                .add(JsonObject()
                    .put("id", "test-cluster")
                    .put("name", "Test Cluster")
                    .put("type", "k3s")
                    .put("endpoint", "https://localhost:6443")
                )
            )
            .put("helmCharts", JsonArray()
                .add(JsonObject()
                    .put("id", "test-chart")
                    .put("name", "Test Chart")
                    .put("version", "1.0.0")
                    .put("repository", "https://charts.example.com")
                )
            )
            .put("operators", JsonArray()
                .add(JsonObject()
                    .put("id", "test-operator")
                    .put("name", "Test Operator")
                    .put("version", "1.0.0")
                )
            )
            .put("crds", JsonArray()
                .add(JsonObject()
                    .put("id", "test-crd")
                    .put("name", "Test CRD")
                    .put("group", "apix.louloulin.com")
                    .put("version", "v1")
                    .put("kind", "Gateway")
                )
            )

        // 初始化部署管理器
        deployManager.initialize(config)
            .onSuccess {
                testContext.completeNow()
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        deployManager.close()
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
    fun testGetCRDs(testContext: VertxTestContext) {
        deployManager.getCRDs()
            .onSuccess { crds ->
                testContext.verify {
                    assert(crds.size() == 1)
                    assert(crds.getJsonObject(0).getString("id") == "test-crd")
                    assert(crds.getJsonObject(0).getString("name") == "Test CRD")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetCRDDetails(testContext: VertxTestContext) {
        deployManager.getCRDDetails("test-crd")
            .onSuccess { crd ->
                testContext.verify {
                    assert(crd.getString("id") == "test-crd")
                    assert(crd.getString("name") == "Test CRD")
                    assert(crd.getString("group") == "apix.louloulin.com")
                    assert(crd.getString("version") == "v1")
                    assert(crd.getString("kind") == "Gateway")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testInstallCRD(testContext: VertxTestContext) {
        deployManager.installCRD("test-cluster", "test-crd")
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "CRD安装中")
                    assert(result.getString("installId") != null)

                    // 等待异步安装完成
                    vertx.setTimer(3000) {
                        deployManager.getCRDInstallStatus(result.getString("installId"))
                            .onSuccess { status ->
                                testContext.verify {
                                    assert(status.getString("phase") == "Deployed")
                                    assert(status.getString("message") == "CRD安装成功")
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
    fun testUninstallCRD(testContext: VertxTestContext) {
        // 先安装CRD
        deployManager.installCRD("test-cluster", "test-crd")
            .compose { result ->
                val installId = result.getString("installId")
                
                // 等待异步安装完成
                vertx.setTimer(3000) {
                    // 卸载CRD
                    deployManager.uninstallCRD(installId)
                        .onSuccess { uninstallResult ->
                            testContext.verify {
                                assert(uninstallResult.getBoolean("success"))
                                assert(uninstallResult.getString("message") == "CRD卸载中")
                                
                                // 等待异步卸载完成
                                vertx.setTimer(3000) {
                                    deployManager.getCRDInstallStatus(installId)
                                        .onSuccess { status ->
                                            testContext.failNow("应该找不到已卸载的CRD")
                                        }
                                        .onFailure { cause ->
                                            // 预期会失败，因为CRD已卸载
                                            testContext.completeNow()
                                        }
                                }
                            }
                        }
                        .onFailure { cause ->
                            testContext.failNow(cause)
                        }
                }
                
                return@compose null
            }
    }

    @Test
    fun testGetAllCRDInstallStatus(testContext: VertxTestContext) {
        // 先安装CRD
        deployManager.installCRD("test-cluster", "test-crd")
            .compose { result ->
                // 等待异步安装完成
                vertx.setTimer(3000) {
                    // 获取所有CRD安装状态
                    deployManager.getAllCRDInstallStatus()
                        .onSuccess { statusList ->
                            testContext.verify {
                                assert(statusList.size() >= 1)
                                val status = statusList.getJsonObject(0)
                                assert(status.getString("phase") == "Deployed")
                                assert(status.getString("message") == "CRD安装成功")
                                assert(status.getString("clusterId") == "test-cluster")
                                assert(status.getString("crdId") == "test-crd")
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
