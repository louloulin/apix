package com.louloulin.apix.edge.deploy.cloud

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
class MultiCloudDeployManagerTest {
    private val logger = LoggerFactory.getLogger(MultiCloudDeployManagerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var deployManager: MultiCloudDeployManager

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()
        deployManager = MultiCloudDeployManager.getInstance(vertx)

        // 创建测试配置
        val config = JsonObject()
            .put("deploys", JsonArray()
                .add(JsonObject()
                    .put("id", "test-deploy-1")
                    .put("name", "Test Deploy 1")
                    .put("providerType", "AWS")
                    .put("deployType", "container")
                    .put("region", "us-east-1")
                    .put("clusterConfig", JsonObject()
                        .put("name", "test-cluster-1")
                        .put("version", "1.27")
                        .put("nodeCount", 3)
                        .put("nodeType", "t3.medium")
                    )
                )
                .add(JsonObject()
                    .put("id", "test-deploy-2")
                    .put("name", "Test Deploy 2")
                    .put("providerType", "AWS")
                    .put("deployType", "serverless")
                    .put("region", "us-west-2")
                    .put("serviceConfig", JsonObject()
                        .put("name", "test-function-1")
                        .put("runtime", "nodejs18.x")
                        .put("memory", 128)
                        .put("timeout", 30)
                    )
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
    fun testCreateCloudProvider(testContext: VertxTestContext) {
        // 创建 AWS 云提供商
        val config = JsonObject()
            .put("accessKey", "test-access-key")
            .put("secretKey", "test-secret-key")
            .put("region", "us-east-1")

        deployManager.createCloudProvider(CloudProviderType.AWS, config)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("type") == "AWS")
                    assert(result.getString("name") == "Amazon Web Services")
                    assert(result.containsKey("status"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetCloudProviders(testContext: VertxTestContext) {
        // 先创建一个云提供商
        val config = JsonObject()
            .put("accessKey", "test-access-key")
            .put("secretKey", "test-secret-key")
            .put("region", "us-east-1")

        deployManager.createCloudProvider(CloudProviderType.AWS, config)
            .compose {
                // 获取云提供商列表
                deployManager.getCloudProviders()
            }
            .onSuccess { providers ->
                testContext.verify {
                    assert(providers.size() == 1)
                    assert(providers.getJsonObject(0).getString("type") == "AWS")
                    assert(providers.getJsonObject(0).getString("name") == "Amazon Web Services")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetDeployConfigs(testContext: VertxTestContext) {
        deployManager.getDeployConfigs()
            .onSuccess { configs ->
                testContext.verify {
                    assert(configs.size() == 2)
                    assert(configs.getJsonObject(0).getString("id") == "test-deploy-1")
                    assert(configs.getJsonObject(1).getString("id") == "test-deploy-2")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetDeployConfigDetails(testContext: VertxTestContext) {
        deployManager.getDeployConfigDetails("test-deploy-1")
            .onSuccess { config ->
                testContext.verify {
                    assert(config.getString("id") == "test-deploy-1")
                    assert(config.getString("name") == "Test Deploy 1")
                    assert(config.getString("providerType") == "AWS")
                    assert(config.getString("deployType") == "container")
                    assert(config.getString("region") == "us-east-1")
                    assert(config.containsKey("clusterConfig"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testCreateDeployConfig(testContext: VertxTestContext) {
        val config = JsonObject()
            .put("name", "Test Deploy 3")
            .put("providerType", "AWS")
            .put("deployType", "container")
            .put("region", "eu-west-1")
            .put("clusterConfig", JsonObject()
                .put("name", "test-cluster-3")
                .put("version", "1.27")
                .put("nodeCount", 3)
                .put("nodeType", "t3.medium")
            )

        deployManager.createDeployConfig(config)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("name") == "Test Deploy 3")
                    assert(result.getString("providerType") == "AWS")
                    assert(result.getString("deployType") == "container")
                    assert(result.getString("region") == "eu-west-1")
                    assert(result.containsKey("clusterConfig"))
                    assert(result.containsKey("id"))
                    assert(result.containsKey("createdAt"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testUpdateDeployConfig(testContext: VertxTestContext) {
        val config = JsonObject()
            .put("name", "Updated Test Deploy 1")
            .put("clusterConfig", JsonObject()
                .put("nodeCount", 5)
            )

        deployManager.updateDeployConfig("test-deploy-1", config)
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getString("id") == "test-deploy-1")
                    assert(result.getString("name") == "Updated Test Deploy 1")
                    assert(result.getString("providerType") == "AWS")
                    assert(result.getString("deployType") == "container")
                    assert(result.getString("region") == "us-east-1")
                    assert(result.getJsonObject("clusterConfig").getInteger("nodeCount") == 5)
                    assert(result.containsKey("updatedAt"))
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testDeleteDeployConfig(testContext: VertxTestContext) {
        deployManager.deleteDeployConfig("test-deploy-1")
            .compose {
                // 获取部署配置列表
                deployManager.getDeployConfigs()
            }
            .onSuccess { configs ->
                testContext.verify {
                    assert(configs.size() == 1)
                    assert(configs.getJsonObject(0).getString("id") == "test-deploy-2")
                    testContext.completeNow()
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testExecuteDeploy(testContext: VertxTestContext) {
        // 先创建一个云提供商
        val providerConfig = JsonObject()
            .put("accessKey", "test-access-key")
            .put("secretKey", "test-secret-key")
            .put("region", "us-east-1")

        deployManager.createCloudProvider(CloudProviderType.AWS, providerConfig)
            .compose {
                // 执行部署
                deployManager.executeDeploy("test-deploy-1")
            }
            .onSuccess { result ->
                testContext.verify {
                    assert(result.getBoolean("success"))
                    assert(result.getString("message") == "部署成功")
                    assert(result.getString("deployId") == "test-deploy-1")
                    assert(result.containsKey("statusId"))
                    assert(result.containsKey("clusterId"))

                    // 获取部署状态
                    val statusId = result.getString("statusId")
                    deployManager.getDeployStatus(statusId)
                        .onSuccess { status ->
                            testContext.verify {
                                assert(status.getString("id") == statusId)
                                assert(status.getString("deployId") == "test-deploy-1")
                                assert(status.getString("providerType") == "AWS")
                                assert(status.getString("deployType") == "container")
                                assert(status.getString("phase") == "Deployed")
                                assert(status.getString("message") == "部署成功")
                                assert(status.containsKey("startTime"))
                                assert(status.containsKey("completionTime"))
                                assert(status.containsKey("clusterId"))
                                testContext.completeNow()
                            }
                        }
                        .onFailure { cause ->
                            testContext.failNow(cause)
                        }
                }
            }
            .onFailure { cause ->
                testContext.failNow(cause)
            }
    }

    @Test
    fun testGetAllDeployStatus(testContext: VertxTestContext) {
        // 先创建一个云提供商
        val providerConfig = JsonObject()
            .put("accessKey", "test-access-key")
            .put("secretKey", "test-secret-key")
            .put("region", "us-east-1")

        deployManager.createCloudProvider(CloudProviderType.AWS, providerConfig)
            .compose {
                // 执行部署
                deployManager.executeDeploy("test-deploy-1")
            }
            .compose<Void> { result ->
                // 等待部署完成
                vertx.setTimer(1000) {
                    // 获取所有部署状态
                    deployManager.getAllDeployStatus()
                        .onSuccess { statusList ->
                            testContext.verify {
                                assert(statusList.size() == 1)
                                assert(statusList.getJsonObject(0).getString("deployId") == "test-deploy-1")
                                assert(statusList.getJsonObject(0).getString("providerType") == "AWS")
                                assert(statusList.getJsonObject(0).getString("deployType") == "container")
                                assert(statusList.getJsonObject(0).getString("phase") == "Deployed")
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

    @Test
    fun testGetStatus(testContext: VertxTestContext) {
        // 先创建一个云提供商
        val providerConfig = JsonObject()
            .put("accessKey", "test-access-key")
            .put("secretKey", "test-secret-key")
            .put("region", "us-east-1")

        deployManager.createCloudProvider(CloudProviderType.AWS, providerConfig)
            .compose {
                // 执行部署
                deployManager.executeDeploy("test-deploy-1")
            }
            .compose<Void> {
                // 获取状态
                val status = deployManager.getStatus()
                testContext.verify {
                    assert(status.getInteger("deployConfigCount") == 2)
                    assert(status.getInteger("deployStatusCount") == 1)
                    assert(status.getInteger("cloudProviderCount") == 1)
                    assert(status.containsKey("timestamp"))
                    testContext.completeNow()
                }

                return@compose null
            }
    }
}
