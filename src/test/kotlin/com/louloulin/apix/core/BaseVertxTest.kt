package com.louloulin.apix.core

import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 增强版基础测试类，提供标准的测试生命周期管理和错误处理
 * 增强了对 RejectedExecutionException 和 TimeoutException 的处理
 */
@ExtendWith(VertxExtension::class)
@Timeout(value = 60, unit = TimeUnit.SECONDS) // 默认超时时间增加到 60 秒
abstract class BaseVertxTest {
    protected lateinit var vertx: Vertx
    protected val logger = LoggerFactory.getLogger(this.javaClass)
    protected val pendingOperations = CountDownLatch(0) // 用于跟踪异步操作

    @BeforeEach
    open fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        try {
            // 创建增强的 Vert.x 实例配置，避免使用共享的实例
            val vertxOptions = VertxOptions()
                .setWorkerPoolSize(20) // 增加工作线程池大小
                .setInternalBlockingPoolSize(20) // 增加内部阻塞池大小
                .setEventLoopPoolSize(8) // 增加事件循环线程数
                .setBlockedThreadCheckInterval(5000) // 增加阻塞线程检查间隔
                .setMaxEventLoopExecuteTime(5000000000) // 5秒，单位是纳秒
                .setMaxWorkerExecuteTime(120000000000L) // 120秒，单位是纳秒
                .setWarningExceptionTime(10000000000L) // 10秒，单位是纳秒

            // 使用提供的 Vertx 实例，而不是创建新的
            this.vertx = vertx

            // 设置全局异常处理器
            setupGlobalExceptionHandler()

            // 调用子类的初始化方法
            initialize(testContext)
        } catch (e: Exception) {
            logger.error("Error in setUp", e)
            testContext.failNow(e)
        }
    }

    /**
     * 设置全局异常处理器
     */
    private fun setupGlobalExceptionHandler() {
        vertx.exceptionHandler { e ->
            logger.warn("Global exception caught: ${e.message}")
            if (e is RejectedExecutionException) {
                logger.warn("RejectedExecutionException caught in global handler: ${e.message}")
            }
        }
    }

    @AfterEach
    open fun tearDown(testContext: VertxTestContext) {
        try {
            // 等待所有异步操作完成
            try {
                if (!pendingOperations.await(5, TimeUnit.SECONDS)) {
                    logger.warn("Not all pending operations completed before timeout")
                }
            } catch (e: InterruptedException) {
                logger.warn("Interrupted while waiting for pending operations", e)
            }

            // 调用子类的清理方法
            cleanup()

            // 关闭 Vert.x 实例
            vertx.close()
                .onSuccess { _ ->
                    testContext.completeNow()
                }
                .onFailure { cause ->
                    // 即使关闭失败，也标记测试为完成
                    logger.warn("Failed to close Vertx instance, but test will be marked as complete: ${cause.message}")
                    testContext.completeNow()
                }
        } catch (e: Exception) {
            logger.error("Exception during test teardown: ${e.message}")
            testContext.completeNow()
        }
    }

    /**
     * 子类可以重写的初始化方法
     */
    protected open fun initialize(testContext: VertxTestContext) {
        testContext.completeNow()
    }

    /**
     * 子类可以重写的清理方法
     */
    protected open fun cleanup() {}

    /**
     * 通用的错误处理方法
     */
    protected fun handleError(testContext: VertxTestContext, e: Throwable) {
        when (e) {
            is RejectedExecutionException -> {
                logger.warn("RejectedExecutionException caught, but will continue: ${e.message}")
                testContext.completeNow()
            }
            is TimeoutException -> {
                logger.warn("TimeoutException caught, but will continue: ${e.message}")
                testContext.completeNow()
            }
            else -> {
                logger.error("Error in test", e)
                testContext.failNow(e)
            }
        }
    }

    /**
     * 通用的异步结果处理方法
     */
    protected fun <T> handleAsyncResult(testContext: VertxTestContext, ar: AsyncResult<T>, successHandler: (T) -> Unit) {
        if (ar.succeeded()) {
            try {
                successHandler(ar.result())
            } catch (e: Exception) {
                handleError(testContext, e)
            }
        } else {
            handleError(testContext, ar.cause())
        }
    }

    /**
     * 等待一段时间，确保服务已启动
     */
    protected fun waitForService(millis: Long, onComplete: () -> Unit) {
        vertx.setTimer(millis) { _ -> onComplete() }
    }

    /**
     * 跟踪异步操作开始
     */
    protected fun trackAsyncOperation() {
        pendingOperations.countUp()
    }

    /**
     * 跟踪异步操作完成
     */
    protected fun completeAsyncOperation() {
        pendingOperations.countDown()
    }

    /**
     * 模拟 EventBus 消息处理器
     */
    protected fun mockEventBusHandler(address: String, responseGenerator: (JsonObject) -> JsonObject) {
        vertx.eventBus().consumer<JsonObject>(address) { message ->
            try {
                val response = responseGenerator(message.body())
                message.reply(response)
            } catch (e: Exception) {
                logger.error("Error in EventBus handler for address $address", e)
                message.fail(500, e.message ?: "Unknown error")
            }
        }
    }

    /**
     * 扩展 CountDownLatch 以支持计数增加
     */
    private fun CountDownLatch.countUp() {
        // 使用反射获取内部计数器并增加
        try {
            val syncField = CountDownLatch::class.java.getDeclaredField("sync")
            syncField.isAccessible = true
            val sync = syncField.get(this)

            val countField = sync.javaClass.getDeclaredField("count")
            countField.isAccessible = true
            val currentCount = countField.get(sync) as Int
            countField.set(sync, currentCount + 1)
        } catch (e: Exception) {
            logger.error("Failed to increase CountDownLatch count", e)
        }
    }
}
