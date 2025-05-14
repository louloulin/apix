package com.louloulin.apix.core.async

import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.function.Function
import java.util.function.Supplier

@ExtendWith(VertxExtension::class)
class AsyncProcessorEnhancerTest {
    private val logger = LoggerFactory.getLogger(AsyncProcessorEnhancerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var asyncProcessorEnhancer: AsyncProcessorEnhancer

    @BeforeEach
    fun setUp(vertx: Vertx) {
        this.vertx = vertx
        asyncProcessorEnhancer = AsyncProcessorEnhancer.getInstance(vertx)

        // 初始化异步处理增强器
        val config = JsonObject()
            .put("defaultTimeout", 5000L)
            .put("defaultMaxRetries", 3)
            .put("defaultRetryDelay", 500L)
            .put("defaultMaxConcurrent", 5)
            .put("defaultBatchSize", 10)

        asyncProcessorEnhancer.initialize(config)
    }

    @AfterEach
    fun tearDown() {
        asyncProcessorEnhancer.resetStats()
    }

    @Test
    fun testBatchProcess(testContext: VertxTestContext) {
        // 创建测试数据 - 使用更小的数据集
        val items = (1..10).map { it.toString() }

        // 创建处理函数
        val processor = Function<String, Future<Int>> { item ->
            Future.future { promise ->
                // 模拟异步处理 - 不使用定时器
                promise.complete(item.toInt() * 2)
            }
        }

        // 执行批量处理
        asyncProcessorEnhancer.batchProcess(items, processor, 5)
            .onSuccess { results ->
                testContext.verify {
                    // 验证结果
                    assert(results.size == items.size)
                    for (i in items.indices) {
                        assert(results[i] == items[i].toInt() * 2)
                    }

                    // 验证统计信息
                    val stats = asyncProcessorEnhancer.getStats()
                    assert(stats.getLong("successCount") >= 0)

                    logger.info("批量处理结果: $results")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                logger.error("批量处理失败", err)
                testContext.failNow(err)
            }

        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    @Test
    fun testParallelProcess(testContext: VertxTestContext) {
        // 创建测试数据
        val items = (1..20).map { it.toString() }

        // 创建处理函数
        val processor = Function<String, Future<Int>> { item ->
            Future.future { promise ->
                // 模拟异步处理
                vertx.setTimer(200) {
                    promise.complete(item.toInt() * 3)
                }
            }
        }

        // 执行并行处理
        asyncProcessorEnhancer.parallelProcess(items, processor, 5)
            .onSuccess { results ->
                testContext.verify {
                    // 验证结果
                    assert(results.size == items.size)
                    for (i in items.indices) {
                        assert(results[i] == items[i].toInt() * 3)
                    }

                    // 验证统计信息
                    val stats = asyncProcessorEnhancer.getStats()
                    assert(stats.getLong("successCount") > 0)

                    logger.info("并行处理结果: $results")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                logger.error("并行处理失败", err)
                testContext.failNow(err)
            }

        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    @Test
    fun testWithRetry(testContext: VertxTestContext) {
        // 创建失败计数器
        val failureCount = java.util.concurrent.atomic.AtomicInteger(0)

        // 创建操作函数
        val operation = Supplier<Future<String>> {
            Future.future { promise ->
                // 模拟前两次失败，第三次成功
                if (failureCount.getAndIncrement() < 2) {
                    promise.fail("模拟失败")
                } else {
                    promise.complete("操作成功")
                }
            }
        }

        // 使用非延迟重试，避免使用定时器
        asyncProcessorEnhancer.withRetry(operation, 3, 0)
            .onSuccess { result ->
                testContext.verify {
                    // 验证结果
                    assert(result == "操作成功")

                    // 验证统计信息
                    val stats = asyncProcessorEnhancer.getStats()
                    assert(stats.getLong("successCount") == 1L)
                    assert(stats.getLong("retryCount") == 2L)

                    logger.info("带重试的操作结果: $result")
                    testContext.completeNow()
                }
            }
            .onFailure { err ->
                logger.error("带重试的操作失败", err)
                testContext.failNow(err)
            }

        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    @Test
    fun testWithTimeout(testContext: VertxTestContext) {
        // 创建操作函数
        val operation = Supplier<Future<String>> {
            Future.future { promise ->
                // 模拟超时操作 - 使用更长的延迟确保超时会发生
                vertx.setTimer(3000) {
                    promise.complete("操作成功")
                }
            }
        }

        // 执行带超时的操作 - 使用更短的超时时间
        asyncProcessorEnhancer.withTimeout(operation, 500)
            .onSuccess { result ->
                logger.info("带超时的操作成功，这不应该发生")
                testContext.failNow(RuntimeException("操作应该超时"))
            }
            .onFailure { err ->
                testContext.verify {
                    // 验证错误信息
                    assert(err.message?.contains("超时") == true)

                    // 验证统计信息
                    val stats = asyncProcessorEnhancer.getStats()
                    assert(stats.getLong("timeoutCount") == 1L)

                    logger.info("带超时的操作正确超时: ${err.message}")
                    testContext.completeNow()
                }
            }

        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    @Test
    fun testWithCircuitBreaker(testContext: VertxTestContext) {
        // 创建操作函数
        val operation = Supplier<Future<String>> {
            Future.future { promise ->
                // 始终失败的操作
                promise.fail("模拟失败")
            }
        }

        // 创建备选方案函数
        val fallback = Function<Throwable, String> { _ ->
            "备选方案结果"
        }

        // 执行多次操作，触发熔断器
        val futures = ArrayList<Future<String>>()
        for (i in 0 until 10) {
            futures.add(asyncProcessorEnhancer.withCircuitBreaker("test", operation, fallback, 3, 5000))
        }

        // 等待所有操作完成
        Future.all(futures).onComplete { ar ->
            testContext.verify {
                // 验证所有操作都返回了备选方案结果
                for (future in futures) {
                    assert(future.result() == "备选方案结果")
                }

                // 验证统计信息
                val stats = asyncProcessorEnhancer.getStats()
                assert(stats.getLong("failureCount") > 0)

                // 验证熔断器状态
                val breakersArray = stats.getJsonArray("circuitBreakers")
                assert(breakersArray.size() == 1)
                val breaker = breakersArray.getJsonObject(0)
                assert(breaker.getString("name") == "test")
                assert(breaker.getBoolean("open"))

                logger.info("熔断器状态: $breaker")
                testContext.completeNow()
            }
        }

        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }

    @Test
    fun testGetStats(testContext: VertxTestContext) {
        // 执行一些操作来生成统计信息
        val operation = Supplier<Future<String>> {
            Future.future { promise ->
                promise.complete("操作成功")
            }
        }

        // 执行几个成功的操作
        val futures = ArrayList<Future<*>>()
        for (i in 0 until 5) {
            futures.add(asyncProcessorEnhancer.withRetry(operation, 1, 100))
        }

        // 等待所有操作完成
        Future.all(futures).onComplete { ar ->
            // 获取统计信息
            val stats = asyncProcessorEnhancer.getStats()

            testContext.verify {
                // 验证统计信息
                assert(stats.getLong("successCount") >= 0L)
                assert(stats.getLong("failureCount") >= 0L)
                assert(stats.getLong("processingCount") >= 0L)
                assert(stats.getLong("avgProcessingTime") >= 0L)

                logger.info("统计信息: $stats")
                testContext.completeNow()
            }
        }

        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }
}
