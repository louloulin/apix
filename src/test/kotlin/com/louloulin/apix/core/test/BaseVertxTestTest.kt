package com.louloulin.apix.core.test

import io.vertx.core.Future
import io.vertx.core.json.JsonObject
import io.vertx.junit5.VertxTestContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

/**
 * 测试 BaseVertxTest 类的功能
 */
class BaseVertxTestTest : BaseVertxTest() {
    
    private var initialized = false
    
    override fun initialize(testContext: VertxTestContext) {
        initialized = true
        testContext.completeNow()
    }
    
    @Test
    fun testInitialization(testContext: VertxTestContext) {
        testContext.verify {
            assert(initialized) { "BaseVertxTest should call initialize method" }
            assert(vertx.isClustered == false) { "Vertx instance should not be clustered by default" }
            testContext.completeNow()
        }
    }
    
    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    fun testHandleError(testContext: VertxTestContext) {
        // 测试处理 RejectedExecutionException
        handleError(testContext, RejectedExecutionException("Test exception"))
        
        // 如果测试没有失败，说明 handleError 正确处理了 RejectedExecutionException
        assert(true)
    }
    
    @Test
    fun testHandleAsyncResult(testContext: VertxTestContext) {
        // 测试处理成功的异步结果
        val successFuture = Future.succeededFuture("success")
        handleAsyncResult(testContext, successFuture) { result ->
            testContext.verify {
                assert(result == "success") { "Result should be 'success'" }
                testContext.completeNow()
            }
        }
    }
    
    @Test
    fun testWaitForService(testContext: VertxTestContext) {
        // 测试等待服务
        val startTime = System.currentTimeMillis()
        waitForService(500) {
            val endTime = System.currentTimeMillis()
            testContext.verify {
                assert(endTime - startTime >= 500) { "Should wait at least 500ms" }
                testContext.completeNow()
            }
        }
    }
    
    @Test
    fun testExecuteBlockingOperation(testContext: VertxTestContext) {
        // 测试执行阻塞操作
        vertx.executeBlocking<JsonObject> { promise ->
            try {
                // 模拟阻塞操作
                Thread.sleep(100)
                promise.complete(JsonObject().put("success", true))
            } catch (e: Exception) {
                promise.fail(e)
            }
        }.onComplete { ar ->
            handleAsyncResult(testContext, ar) { result ->
                testContext.verify {
                    assert(result.getBoolean("success")) { "Result should have success=true" }
                    testContext.completeNow()
                }
            }
        }
    }
}
