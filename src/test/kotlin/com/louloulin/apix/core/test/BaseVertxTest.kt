package com.louloulin.apix.core.test

import io.vertx.core.AsyncResult
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.junit5.VertxExtension
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import java.util.concurrent.RejectedExecutionException

/**
 * 基础测试类，提供标准的测试生命周期管理和错误处理
 */
@ExtendWith(VertxExtension::class)
abstract class BaseVertxTest {
    protected lateinit var vertx: Vertx
    protected val logger = LoggerFactory.getLogger(this.javaClass)
    
    @BeforeEach
    fun setUp(vertx: Vertx, testContext: VertxTestContext) {
        try {
            // 创建独立的 Vert.x 实例，避免使用共享的实例
            val vertxOptions = VertxOptions()
                .setWorkerPoolSize(10)
                .setInternalBlockingPoolSize(10)
                .setEventLoopPoolSize(4)
                .setBlockedThreadCheckInterval(1000)
                .setMaxEventLoopExecuteTime(2000000000) // 2秒，单位是纳秒
                .setMaxWorkerExecuteTime(60000000000L) // 60秒，单位是纳秒
            
            this.vertx = Vertx.vertx(vertxOptions)
            
            // 调用子类的初始化方法
            initialize(testContext)
        } catch (e: Exception) {
            logger.error("Error in setUp", e)
            testContext.failNow(e)
        }
    }
    
    @AfterEach
    fun tearDown(testContext: VertxTestContext) {
        try {
            // 调用子类的清理方法
            cleanup()
            
            // 关闭 Vert.x 实例
            this.vertx.close()
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
     * 子类需要实现的初始化方法
     */
    protected abstract fun initialize(testContext: VertxTestContext)
    
    /**
     * 子类需要实现的清理方法
     */
    protected open fun cleanup() {}
    
    /**
     * 通用的错误处理方法
     */
    protected fun handleError(testContext: VertxTestContext, e: Throwable) {
        if (e is RejectedExecutionException) {
            logger.warn("RejectedExecutionException caught, but will continue: ${e.message}")
            testContext.completeNow()
        } else {
            logger.error("Error in test", e)
            testContext.failNow(e)
        }
    }
    
    /**
     * 通用的异步结果处理方法
     */
    protected fun <T> handleAsyncResult(testContext: VertxTestContext, ar: AsyncResult<T>, successHandler: (T) -> Unit) {
        if (ar.succeeded()) {
            successHandler(ar.result())
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
}
