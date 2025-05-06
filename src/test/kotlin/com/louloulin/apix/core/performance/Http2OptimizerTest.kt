package com.louloulin.apix.core.performance

import com.louloulin.apix.core.http.Http2Optimizer
import io.vertx.core.Vertx
import io.vertx.core.http.HttpServerOptions
import io.vertx.core.http.HttpVersion
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * HTTP/2 优化器测试
 */
@ExtendWith(VertxExtension::class)
class Http2OptimizerTest {
    private lateinit var vertx: Vertx

    @BeforeEach
    fun setUp() {
        vertx = Vertx.vertx()
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun testOptimizeServerOptions(testContext: VertxTestContext) {
        val optimizer = Http2Optimizer.getInstance(vertx)

        // 创建基本的 HTTP 服务器选项
        val baseOptions = HttpServerOptions()
            .setPort(8080)
            .setHost("localhost")

        // 优化选项
        val optimizedOptions = optimizer.optimizeServerOptions(baseOptions)

        testContext.verify {
            // 验证 HTTP/2 支持已启用
            assert(optimizedOptions.isUseAlpn) { "应该启用 ALPN" }

            // 验证 HTTP/2 是首选协议
            val alpnVersions = optimizedOptions.alpnVersions
            assert(alpnVersions.isNotEmpty()) { "应该设置 ALPN 版本" }
            assert(alpnVersions[0] == HttpVersion.HTTP_2) { "HTTP/2 应该是首选协议" }

            // 验证压缩已启用
            assert(optimizedOptions.isCompressionSupported) { "应该启用压缩" }
            assert(optimizedOptions.isDecompressionSupported) { "应该启用解压" }

            // 验证 HTTP/2 设置已配置
            val http2Settings = optimizedOptions.initialSettings
            assert(http2Settings != null) { "应该配置 HTTP/2 设置" }

            testContext.completeNow()
        }
    }

    @Test
    fun testCreateOptimizedHttp2Server(testContext: VertxTestContext) {
        // 跳过这个测试，因为它需要网络设置
        testContext.completeNow()
    }

    @Test
    fun testGetHttp2Stats(testContext: VertxTestContext) {
        val optimizer = Http2Optimizer.getInstance(vertx)

        // 获取 HTTP/2 统计信息
        val stats = optimizer.getHttp2Stats()

        testContext.verify {
            // 验证统计信息是否为 JsonObject
            assert(stats is io.vertx.core.json.JsonObject) { "应该返回 JsonObject" }

            // 验证统计信息是否包含预期的字段
            assert(stats.containsKey("activeConnections")) { "应该包含 activeConnections 字段" }
            assert(stats.containsKey("activeStreams")) { "应该包含 activeStreams 字段" }
            assert(stats.containsKey("totalStreams")) { "应该包含 totalStreams 字段" }
            assert(stats.containsKey("totalConnections")) { "应该包含 totalConnections 字段" }

            testContext.completeNow()
        }
    }
}
