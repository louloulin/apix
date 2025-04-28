package com.louloulin.apix.plugins.cache

import com.louloulin.apix.plugins.PluginConfig
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.web.Router
import io.vertx.ext.web.client.WebClient
import io.vertx.ext.web.client.WebClientOptions
import io.vertx.ext.web.handler.BodyHandler
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 请求缓存插件测试
 */
@ExtendWith(VertxExtension::class)
class RequestCachePluginTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient
    private val testPort = 8888

    @BeforeEach
    fun setUp(testContext: VertxTestContext) {
        vertx = Vertx.vertx()

        // 创建 WebClient
        webClient = WebClient.create(vertx, WebClientOptions()
            .setDefaultHost("localhost")
            .setDefaultPort(testPort)
        )

        testContext.completeNow()
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    /**
     * 测试基本缓存功能
     */
    @Test
    fun testBasicCaching(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)

        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())

        // 创建插件配置
        val config = JsonObject()
            .put("enabled", true)
            .put("ttl", 5)
            .put("methods", JsonArray().add("GET"))
            .put("conditions", JsonObject()
                .put("statusCodes", JsonArray().add(200))
            )

        // 创建插件
        val pluginConfig = PluginConfig("test-request-cache", "requestCache", config)
        val plugin = RequestCachePlugin(pluginConfig.id, pluginConfig, vertx)

        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }

        // 请求计数器
        val requestCount = AtomicInteger(0)

        // 添加测试处理器
        router.get("/test").handler { context ->
            // 增加请求计数
            val count = requestCount.incrementAndGet()

            // 返回响应
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("count", count)
                    .put("time", System.currentTimeMillis())
                    .encode()
                )
        }

        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送第一个请求
                    webClient.get("/test")
                        .send()
                        .onComplete { firstAr ->
                            if (firstAr.succeeded()) {
                                val firstResponse = firstAr.result()
                                val firstBody = firstResponse.bodyAsJsonObject()
                                testContext.verify {
                                    assert(firstResponse.statusCode() == 200) { "Expected status code 200 but got ${firstResponse.statusCode()}" }
                                    assert(firstBody.getInteger("count") == 1) { "Expected count to be 1 but got ${firstBody.getInteger("count")}" }
                                }

                                // 发送第二个请求（应该命中缓存）
                                webClient.get("/test")
                                    .send()
                                    .onComplete { secondAr ->
                                        if (secondAr.succeeded()) {
                                            val secondResponse = secondAr.result()
                                            testContext.verify {
                                                assert(secondResponse.statusCode() == 200) { "Expected status code 200 but got ${secondResponse.statusCode()}" }
                                                val secondBody = secondResponse.bodyAsJsonObject()
                                                assert(secondBody.getInteger("count") == 1) { "Expected count to be 1 but got ${secondBody.getInteger("count")}" }

                                                // 验证两个响应的时间戳相同（表示第二个响应来自缓存）
                                                val firstTime = firstBody.getLong("time")
                                                val secondTime = secondBody.getLong("time")
                                                assert(firstTime == secondTime) { "Expected second response to be cached (same timestamp)" }
                                            }

                                            // 等待缓存过期
                                            vertx.setTimer(6000) {
                                                // 发送第三个请求（缓存应该已过期）
                                                webClient.get("/test")
                                                    .send()
                                                    .onComplete { thirdAr ->
                                                        if (thirdAr.succeeded()) {
                                                            val thirdResponse = thirdAr.result()
                                                            testContext.verify {
                                                                assert(thirdResponse.statusCode() == 200) { "Expected status code 200 but got ${thirdResponse.statusCode()}" }
                                                                val thirdBody = thirdResponse.bodyAsJsonObject()
                                                                assert(thirdBody.getInteger("count") == 2) { "Expected count to be 2 but got ${thirdBody.getInteger("count")}" }

                                                                // 验证第三个响应的时间戳不同（表示第三个响应不是来自缓存）
                                                                val firstTime = firstBody.getLong("time")
                                                                val thirdTime = thirdBody.getLong("time")
                                                                assert(firstTime != thirdTime) { "Expected third response to be fresh (different timestamp)" }

                                                                testContext.completeNow()
                                                            }
                                                        } else {
                                                            testContext.failNow(thirdAr.cause())
                                                        }
                                                    }
                                            }
                                        } else {
                                            testContext.failNow(secondAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(firstAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(ar.cause())
                }
            }

        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }

    /**
     * 测试缓存键生成
     */
    @Test
    fun testCacheKeyGeneration(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)

        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())

        // 创建插件配置
        val config = JsonObject()
            .put("enabled", true)
            .put("ttl", 5)
            .put("keyGenerator", JsonObject()
                .put("includePath", true)
                .put("includeMethod", true)
                .put("includeParams", JsonArray().add("param1").add("param2"))
                .put("includeHeaders", JsonArray().add("X-Test-Header"))
            )

        // 创建插件
        val pluginConfig = PluginConfig("test-request-cache", "requestCache", config)
        val plugin = RequestCachePlugin(pluginConfig.id, pluginConfig, vertx)

        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }

        // 请求计数器
        val requestCount = AtomicInteger(0)

        // 添加测试处理器
        router.get("/test").handler { context ->
            // 增加请求计数
            val count = requestCount.incrementAndGet()

            // 返回响应
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .put("count", count)
                    .put("params", JsonObject().apply {
                        context.queryParams().forEach { param ->
                            put(param.key, param.value)
                        }
                    })
                    .put("headers", JsonObject().apply {
                        context.request().headers().forEach { header ->
                            put(header.key, header.value)
                        }
                    })
                    .encode()
                )
        }

        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送第一个请求
                    webClient.get("/test?param1=value1&param2=value2")
                        .putHeader("X-Test-Header", "test-value")
                        .send()
                        .onComplete { firstAr ->
                            if (firstAr.succeeded()) {
                                val firstResponse = firstAr.result()
                                testContext.verify {
                                    assert(firstResponse.statusCode() == 200) { "Expected status code 200 but got ${firstResponse.statusCode()}" }
                                    val firstBody = firstResponse.bodyAsJsonObject()
                                    assert(firstBody.getInteger("count") == 1) { "Expected count to be 1 but got ${firstBody.getInteger("count")}" }
                                }

                                // 发送第二个请求（相同的参数和头部，应该命中缓存）
                                webClient.get("/test?param1=value1&param2=value2")
                                    .putHeader("X-Test-Header", "test-value")
                                    .send()
                                    .onComplete { secondAr ->
                                        if (secondAr.succeeded()) {
                                            val secondResponse = secondAr.result()
                                            testContext.verify {
                                                assert(secondResponse.statusCode() == 200) { "Expected status code 200 but got ${secondResponse.statusCode()}" }
                                                val secondBody = secondResponse.bodyAsJsonObject()
                                                assert(secondBody.getInteger("count") == 1) { "Expected count to be 1 but got ${secondBody.getInteger("count")}" }
                                            }

                                            // 发送第三个请求（不同的参数，不应该命中缓存）
                                            webClient.get("/test?param1=value1&param2=different")
                                                .putHeader("X-Test-Header", "test-value")
                                                .send()
                                                .onComplete { thirdAr ->
                                                    if (thirdAr.succeeded()) {
                                                        val thirdResponse = thirdAr.result()
                                                        testContext.verify {
                                                            assert(thirdResponse.statusCode() == 200) { "Expected status code 200 but got ${thirdResponse.statusCode()}" }
                                                            val thirdBody = thirdResponse.bodyAsJsonObject()
                                                            assert(thirdBody.getInteger("count") == 2) { "Expected count to be 2 but got ${thirdBody.getInteger("count")}" }
                                                        }

                                                        // 发送第四个请求（不同的头部，不应该命中缓存）
                                                        webClient.get("/test?param1=value1&param2=value2")
                                                            .putHeader("X-Test-Header", "different-value")
                                                            .send()
                                                            .onComplete { fourthAr ->
                                                                if (fourthAr.succeeded()) {
                                                                    val fourthResponse = fourthAr.result()
                                                                    testContext.verify {
                                                                        assert(fourthResponse.statusCode() == 200) { "Expected status code 200 but got ${fourthResponse.statusCode()}" }
                                                                        val fourthBody = fourthResponse.bodyAsJsonObject()
                                                                        assert(fourthBody.getInteger("count") == 3) { "Expected count to be 3 but got ${fourthBody.getInteger("count")}" }

                                                                        testContext.completeNow()
                                                                    }
                                                                } else {
                                                                    testContext.failNow(fourthAr.cause())
                                                                }
                                                            }
                                                    } else {
                                                        testContext.failNow(thirdAr.cause())
                                                    }
                                                }
                                        } else {
                                            testContext.failNow(secondAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(firstAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(ar.cause())
                }
            }

        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }

    /**
     * 测试缓存控制
     */
    @Test
    fun testCacheControl(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)

        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())

        // 创建插件配置
        val config = JsonObject()
            .put("enabled", true)
            .put("ttl", 5)
            .put("cacheControl", JsonObject()
                .put("enabled", true)
                .put("maxAge", 5)
                .put("sMaxAge", 10)
                .put("mustRevalidate", true)
            )

        // 创建插件
        val pluginConfig = PluginConfig("test-request-cache", "requestCache", config)
        val plugin = RequestCachePlugin(pluginConfig.id, pluginConfig, vertx)

        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }

        // 添加测试处理器
        router.get("/test").handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        }

        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送请求
                    webClient.get("/test")
                        .send()
                        .onComplete { responseAr ->
                            if (responseAr.succeeded()) {
                                val response = responseAr.result()
                                testContext.verify {
                                    assert(response.statusCode() == 200) { "Expected status code 200 but got ${response.statusCode()}" }

                                    // 验证缓存控制头
                                    val cacheControl = response.getHeader("Cache-Control")
                                    assert(cacheControl != null) { "Expected Cache-Control header to be present" }
                                    assert(cacheControl.contains("max-age=5")) { "Expected Cache-Control to contain max-age=5" }
                                    assert(cacheControl.contains("s-maxage=10")) { "Expected Cache-Control to contain s-maxage=10" }
                                    assert(cacheControl.contains("must-revalidate")) { "Expected Cache-Control to contain must-revalidate" }

                                    testContext.completeNow()
                                }
                            } else {
                                testContext.failNow(responseAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(ar.cause())
                }
            }

        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }

    /**
     * 测试缓存统计
     */
    @Test
    fun testCacheStats(testContext: VertxTestContext) {
        // 创建路由
        val router = Router.router(vertx)

        // 添加 BodyHandler
        router.route().handler(BodyHandler.create())

        // 创建插件配置
        val config = JsonObject()
            .put("enabled", true)
            .put("ttl", 5)

        // 创建插件
        val pluginConfig = PluginConfig("test-request-cache", "requestCache", config)
        val plugin = RequestCachePlugin(pluginConfig.id, pluginConfig, vertx)

        // 添加插件到路由
        router.route().handler { context ->
            plugin.execute(context)
        }

        // 添加测试处理器
        router.get("/test").handler { context ->
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonObject()
                    .put("success", true)
                    .encode()
                )
        }

        // 添加统计接口
        router.get("/stats").handler { context ->
            val stats = plugin.getStats()
            context.response()
                .putHeader("Content-Type", "application/json")
                .end(stats.encode())
        }

        // 启动 HTTP 服务器
        vertx.createHttpServer()
            .requestHandler(router)
            .listen(testPort)
            .onComplete { ar ->
                if (ar.succeeded()) {
                    // 发送第一个请求
                    webClient.get("/test")
                        .send()
                        .onComplete { firstAr ->
                            if (firstAr.succeeded()) {
                                // 发送第二个请求（应该命中缓存）
                                webClient.get("/test")
                                    .send()
                                    .onComplete { secondAr ->
                                        if (secondAr.succeeded()) {
                                            // 获取统计信息
                                            webClient.get("/stats")
                                                .send()
                                                .onComplete { statsAr ->
                                                    if (statsAr.succeeded()) {
                                                        val statsResponse = statsAr.result()
                                                        testContext.verify {
                                                            assert(statsResponse.statusCode() == 200) { "Expected status code 200 but got ${statsResponse.statusCode()}" }

                                                            val stats = statsResponse.bodyAsJsonObject()
                                                            assert(stats.getInteger("size") == 1) { "Expected size to be 1 but got ${stats.getInteger("size")}" }
                                                            assert(stats.getLong("hits") == 1L) { "Expected hits to be 1 but got ${stats.getLong("hits")}" }
                                                            assert(stats.getLong("misses") == 1L) { "Expected misses to be 1 but got ${stats.getLong("misses")}" }
                                                            assert(stats.getDouble("hitRate") == 0.5) { "Expected hitRate to be 0.5 but got ${stats.getDouble("hitRate")}" }

                                                            testContext.completeNow()
                                                        }
                                                    } else {
                                                        testContext.failNow(statsAr.cause())
                                                    }
                                                }
                                        } else {
                                            testContext.failNow(secondAr.cause())
                                        }
                                    }
                            } else {
                                testContext.failNow(firstAr.cause())
                            }
                        }
                } else {
                    testContext.failNow(ar.cause())
                }
            }

        // 确保测试在 10 秒内完成
        assert(testContext.awaitCompletion(10, TimeUnit.SECONDS)) { "Test timed out" }
    }
}
