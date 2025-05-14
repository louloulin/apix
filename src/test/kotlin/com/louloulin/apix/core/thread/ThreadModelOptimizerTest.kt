package com.louloulin.apix.core.thread

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

@ExtendWith(VertxExtension::class)
class ThreadModelOptimizerTest {
    private val logger = LoggerFactory.getLogger(ThreadModelOptimizerTest::class.java)
    private lateinit var vertx: Vertx
    private lateinit var threadModelOptimizer: ThreadModelOptimizer
    
    @BeforeEach
    fun setUp(vertx: Vertx) {
        this.vertx = vertx
        threadModelOptimizer = ThreadModelOptimizer.getInstance(vertx)
        
        // 初始化线程模型优化器
        val config = JsonObject()
            .put("monitoringInterval", 1000L)
            .put("adjustmentInterval", 5000L)
            .put("cpuTargetUsage", 70.0)
            .put("maxEventLoopPoolSize", 16)
            .put("maxWorkerPoolSize", 64)
            .put("maxInternalBlockingPoolSize", 32)
            .put("enabled", true)
            .put("threadAffinityEnabled", false)
        
        threadModelOptimizer.initialize(config)
    }
    
    @AfterEach
    fun tearDown() {
        threadModelOptimizer.stopMonitoring()
    }
    
    @Test
    fun testGetThreadModelStats(testContext: VertxTestContext) {
        // 启动监控
        threadModelOptimizer.startMonitoring()
        
        // 等待一段时间，让监控收集一些数据
        vertx.setTimer(3000) {
            threadModelOptimizer.getThreadModelStats()
                .onSuccess { stats ->
                    testContext.verify {
                        // 验证统计信息包含预期的字段
                        assert(stats.containsKey("eventLoopPoolSize"))
                        assert(stats.containsKey("workerPoolSize"))
                        assert(stats.containsKey("cpuUsage"))
                        assert(stats.containsKey("threadCount"))
                        assert(stats.containsKey("recommendations"))
                        
                        // 验证建议包含预期的字段
                        val recommendations = stats.getJsonObject("recommendations")
                        assert(recommendations.containsKey("cpuUsage"))
                        assert(recommendations.containsKey("eventLoopPoolSize"))
                        assert(recommendations.containsKey("workerPoolSize"))
                        assert(recommendations.containsKey("message"))
                        
                        logger.info("线程模型统计信息: $stats")
                        testContext.completeNow()
                    }
                }
                .onFailure { err ->
                    logger.error("获取线程模型统计信息失败", err)
                    testContext.failNow(err)
                }
        }
        
        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }
    
    @Test
    fun testStartAndStopMonitoring(testContext: VertxTestContext) {
        // 启动监控
        threadModelOptimizer.startMonitoring()
        
        // 等待一段时间
        vertx.setTimer(2000) {
            // 停止监控
            threadModelOptimizer.stopMonitoring()
            
            // 再次启动监控
            threadModelOptimizer.startMonitoring()
            
            // 等待一段时间
            vertx.setTimer(2000) {
                // 再次停止监控
                threadModelOptimizer.stopMonitoring()
                
                testContext.completeNow()
            }
        }
        
        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }
    
    @Test
    fun testThreadPoolAdjustment(testContext: VertxTestContext) {
        // 启动监控
        threadModelOptimizer.startMonitoring()
        
        // 创建一些CPU负载
        createCpuLoad()
        
        // 等待一段时间，让监控收集一些数据并调整线程池
        vertx.setTimer(6000) {
            threadModelOptimizer.getThreadModelStats()
                .onSuccess { stats ->
                    testContext.verify {
                        // 验证统计信息包含预期的字段
                        assert(stats.containsKey("eventLoopPoolSize"))
                        assert(stats.containsKey("workerPoolSize"))
                        assert(stats.containsKey("cpuUsage"))
                        assert(stats.containsKey("threadCount"))
                        assert(stats.containsKey("recommendations"))
                        
                        logger.info("线程模型统计信息: $stats")
                        testContext.completeNow()
                    }
                }
                .onFailure { err ->
                    logger.error("获取线程模型统计信息失败", err)
                    testContext.failNow(err)
                }
        }
        
        // 设置超时
        testContext.awaitCompletion(10, TimeUnit.SECONDS)
    }
    
    /**
     * 创建一些CPU负载
     */
    private fun createCpuLoad() {
        // 创建一些工作线程来生成CPU负载
        val numThreads = Runtime.getRuntime().availableProcessors()
        val threads = ArrayList<Thread>()
        
        for (i in 0 until numThreads) {
            val thread = Thread {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 5000) {
                    // 执行一些CPU密集型操作
                    var sum = 0.0
                    for (j in 0 until 1000000) {
                        sum += Math.sin(j.toDouble())
                    }
                }
            }
            threads.add(thread)
            thread.start()
        }
    }
}
