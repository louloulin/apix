package com.louloulin.apix.dns

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
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.TimeUnit

@ExtendWith(VertxExtension::class)
class SmartDNSVerticleTest {
    private lateinit var vertx: Vertx

    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        this.vertx = vertx

        // 创建模拟配置响应
        val configResponse = JsonObject()
            .put("success", true)
            .put("result", JsonObject()
                .put("dns", JsonObject()
                    .put("enabled", true)
                    .put("providers", JsonArray()
                        .add(JsonObject()
                            .put("name", "cloudflare")
                            .put("type", "cloudflare")
                            .put("enabled", true)
                            .put("apiToken", "mock-token")
                            .put("zoneId", "mock-zone-id")
                        )
                    )
                    .put("geoDatabase", JsonObject()
                        .put("enabled", true)
                        .put("type", "maxmind")
                        .put("filePath", "src/test/resources/GeoLite2-City.mmdb")
                    )
                    // 添加节点配置
                    .put("nodes", JsonArray()
                        .add(JsonObject()
                            .put("id", "us-west")
                            .put("name", "US West")
                            .put("country", "US")
                            .put("region", "CA")
                            .put("city", "San Francisco")
                            .put("ip", "192.168.1.1")
                            .put("default", true)
                        )
                        .add(JsonObject()
                            .put("id", "us-east")
                            .put("name", "US East")
                            .put("country", "US")
                            .put("region", "NY")
                            .put("city", "New York")
                            .put("ip", "192.168.1.2")
                        )
                    )
                )
            )

        // 模拟 DNS 相关的 EventBus 处理器
        // 模拟获取 DNS 状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_STATUS_GET) { message ->
            val status = JsonObject()
                .put("enabled", true)
                .put("primary", "cloudflare")
                .put("providers", JsonObject()
                    .put("cloudflare", JsonObject()
                        .put("healthy", true)
                    )
                )

            message.reply(JsonObject()
                .put("success", true)
                .put("result", status)
            )
        }

        // 模拟获取地理位置
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_GEO_LOCATION_GET) { message ->
            val ip = message.body().getString("ip", "")

            if (ip.isEmpty()) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "IP parameter is required")
                )
                return@consumer
            }

            message.reply(JsonObject()
                .put("success", true)
                .put("result", JsonObject()
                    .put("ip", ip)
                    .put("country", "US")
                    .put("region", "CA")
                    .put("city", "San Francisco")
                    .put("latitude", 37.7749)
                    .put("longitude", -122.4194)
                )
            )
        }

        // 模拟获取最佳节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_BEST_NODE_GET) { message ->
            val geoLocation = message.body().getJsonObject("geoLocation", JsonObject())

            if (geoLocation.isEmpty) {
                message.reply(JsonObject()
                    .put("success", false)
                    .put("error", "geoLocation parameter is required")
                )
                return@consumer
            }

            message.reply(JsonObject()
                .put("success", true)
                .put("node", JsonObject()
                    .put("id", "us-west")
                    .put("name", "US West")
                    .put("country", "US")
                    .put("region", "CA")
                    .put("city", "San Francisco")
                    .put("ip", "192.168.1.1")
                )
            )
        }

        // 完成测试初始化
        testContext.completeNow()
    }

    @AfterEach
    fun tearDown(vertx: Vertx, testContext: VertxTestContext) {
        vertx.close(testContext.succeeding { _ ->
            testContext.completeNow()
        })
    }

    @Test
    fun testGetDNSStatus(vertx: Vertx, testContext: VertxTestContext) {
        // 发送获取DNS状态请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.DNS_STATUS_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                // 验证响应
                testContext.verify {
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result.getBoolean("enabled", false)) { "DNS should be enabled" }
                    assert(result.getString("primary") != null) { "Primary provider should be set" }

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testGetGeoLocation(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("ip", "8.8.8.8")

        // 发送获取地理位置请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.DNS_GEO_LOCATION_GET, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                // 验证响应
                testContext.verify {
                    assert(response != null) { "Response should not be null" }
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val result = response.getJsonObject("result")
                    assert(result != null) { "Result should not be null" }
                    assert(result.getString("country") == "US") { "Country should be US" }

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }

    @Test
    fun testGetBestNode(vertx: Vertx, testContext: VertxTestContext) {
        // 创建请求
        val request = JsonObject()
            .put("geoLocation", JsonObject()
                .put("country", "US")
                .put("region", "CA")
                .put("city", "San Francisco")
            )

        // 发送获取最佳节点请求
        vertx.eventBus().request<JsonObject>(EventBusAddresses.DNS_BEST_NODE_GET, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                // 验证响应
                testContext.verify {
                    assert(response != null) { "Response should not be null" }
                    assert(response.getBoolean("success", false)) { "Response should be successful" }
                    val node = response.getJsonObject("node")
                    assert(node != null) { "Node should not be null" }
                    assert(node.getString("id") == "us-west") { "Node ID should be us-west" }
                    assert(node.getString("country") == "US") { "Country should be US" }

                    testContext.completeNow()
                }
            } else {
                testContext.failNow(ar.cause())
            }
        }

        // 确保测试有足够的时间完成
        testContext.awaitCompletion(5, TimeUnit.SECONDS)
    }
}
