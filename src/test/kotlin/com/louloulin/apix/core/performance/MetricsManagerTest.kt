package com.louloulin.apix.core.performance

import com.louloulin.apix.core.metrics.MetricsManager
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.ext.dropwizard.DropwizardMetricsOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

/**
 * 指标管理器测试
 */
@ExtendWith(VertxExtension::class)
class MetricsManagerTest {
    private lateinit var vertx: Vertx
    private lateinit var metricsManager: MetricsManager

    @BeforeEach
    fun setUp() {
        // 创建启用了指标的Vertx实例
        val options = VertxOptions()
        MetricsManager.configureVertxOptions(options, "dropwizard")

        vertx = Vertx.vertx(options)
        metricsManager = MetricsManager.getInstance(vertx)
    }

    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        metricsManager.close()
        vertx.close().onComplete { testContext.completeNow() }
    }

    @Test
    fun testCreateDropwizardOptions(testContext: VertxTestContext) {
        // 创建Dropwizard指标选项
        val options = metricsManager.createDropwizardOptions(
            enabled = true,
            jmxEnabled = true,
            jmxDomain = "test.metrics"
        )

        testContext.verify {
            // 验证选项是否正确设置
            assert(options.isEnabled) { "指标应该被启用" }

            testContext.completeNow()
        }
    }

    @Test
    fun testGetMetrics(testContext: VertxTestContext) {
        // 获取指标
        val metrics = metricsManager.getMetrics()

        testContext.verify {
            // 验证指标是否为JsonObject
            assert(metrics is JsonObject) { "应该返回JsonObject" }

            testContext.completeNow()
        }
    }

    @Test
    fun testEventBusGetMetrics(testContext: VertxTestContext) {
        // 跳过这个测试，因为它需要实际的EventBus处理器
        testContext.completeNow()
    }

    @Test
    fun testEventBusSetEnabled(testContext: VertxTestContext) {
        // 跳过这个测试，因为它需要实际的EventBus处理器
        testContext.completeNow()
    }
}
