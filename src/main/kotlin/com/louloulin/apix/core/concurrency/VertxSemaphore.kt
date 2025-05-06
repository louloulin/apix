package com.louloulin.apix.core.concurrency

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Vert.x 异步信号量实现，用于控制并发访问。
 * 这个类提供了异步的获取和释放操作，适合在Vert.x的事件循环中使用。
 * 
 * @property name 信号量名称
 * @property vertx Vertx实例
 * @property maxPermits 最大许可数
 * @property timeout 获取许可的超时时间（毫秒）
 */
class VertxSemaphore(
    private val name: String,
    private val vertx: Vertx,
    private val maxPermits: Int,
    private val timeout: Long = 30000
) {
    private val logger = LoggerFactory.getLogger(VertxSemaphore::class.java)
    
    // 当前可用许可数
    private val availablePermits = AtomicInteger(maxPermits)
    
    // 等待队列
    private val waitQueue = ConcurrentLinkedQueue<Promise<Boolean>>()
    
    // 统计信息
    private val acquireCount = AtomicLong(0)
    private val releaseCount = AtomicLong(0)
    private val timeoutCount = AtomicLong(0)
    private val totalWaitTime = AtomicLong(0)
    private val waitCount = AtomicLong(0)
    
    // 等待的请求及其开始等待时间
    private val waitingRequests = ConcurrentHashMap<Promise<Boolean>, Long>()
    
    init {
        // 定期检查超时的请求
        vertx.setPeriodic(1000) { _ ->
            checkTimeouts()
        }
        
        logger.info("创建Vert.x信号量: $name, 最大许可数: $maxPermits, 超时: ${timeout}ms")
    }
    
    /**
     * 获取许可
     * 
     * @return 包含获取结果的Future，true表示获取成功，false表示超时
     */
    fun acquire(): Future<Boolean> {
        val promise = Promise.promise<Boolean>()
        
        vertx.runOnContext { _ ->
            // 尝试获取许可
            if (tryAcquire()) {
                acquireCount.incrementAndGet()
                promise.complete(true)
            } else {
                // 无法立即获取许可，加入等待队列
                waitingRequests[promise] = System.currentTimeMillis()
                waitQueue.offer(promise)
                logger.debug("请求加入等待队列: $name, 当前等待数: ${waitQueue.size}")
            }
        }
        
        return promise.future()
    }
    
    /**
     * 尝试立即获取许可，不等待
     * 
     * @return 是否获取成功
     */
    private fun tryAcquire(): Boolean {
        var current: Int
        var next: Int
        do {
            current = availablePermits.get()
            if (current <= 0) {
                return false
            }
            next = current - 1
        } while (!availablePermits.compareAndSet(current, next))
        
        return true
    }
    
    /**
     * 释放许可
     */
    fun release() {
        vertx.runOnContext { _ ->
            releaseCount.incrementAndGet()
            
            // 检查等待队列
            val waitingPromise = waitQueue.poll()
            if (waitingPromise != null) {
                // 有等待的请求，直接将许可给等待的请求
                val waitStartTime = waitingRequests.remove(waitingPromise)
                if (waitStartTime != null) {
                    val waitTime = System.currentTimeMillis() - waitStartTime
                    totalWaitTime.addAndGet(waitTime)
                    waitCount.incrementAndGet()
                }
                
                acquireCount.incrementAndGet()
                waitingPromise.complete(true)
                logger.debug("许可直接给等待的请求: $name, 剩余等待数: ${waitQueue.size}")
            } else {
                // 没有等待的请求，增加可用许可数
                availablePermits.incrementAndGet()
            }
        }
    }
    
    /**
     * 检查超时的请求
     */
    private fun checkTimeouts() {
        val now = System.currentTimeMillis()
        val expiredPromises = waitingRequests.entries
            .filter { now - it.value > timeout }
            .map { it.key }
        
        for (promise in expiredPromises) {
            if (waitingRequests.remove(promise) != null) {
                waitQueue.remove(promise)
                timeoutCount.incrementAndGet()
                promise.complete(false) // 超时返回false
                logger.debug("请求超时: $name")
            }
        }
    }
    
    /**
     * 获取信号量统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        val avgWaitTime = if (waitCount.get() > 0) totalWaitTime.get() / waitCount.get() else 0
        
        return JsonObject()
            .put("name", name)
            .put("maxPermits", maxPermits)
            .put("availablePermits", availablePermits.get())
            .put("waitingCount", waitQueue.size)
            .put("acquireCount", acquireCount.get())
            .put("releaseCount", releaseCount.get())
            .put("timeoutCount", timeoutCount.get())
            .put("avgWaitTime", avgWaitTime)
    }
    
    /**
     * 重置信号量
     */
    fun reset() {
        vertx.runOnContext { _ ->
            // 拒绝所有等待的请求
            while (true) {
                val waitingPromise = waitQueue.poll() ?: break
                waitingRequests.remove(waitingPromise)
                waitingPromise.complete(false)
            }
            
            // 重置可用许可数
            availablePermits.set(maxPermits)
            
            logger.info("信号量已重置: $name")
        }
    }
    
    companion object {
        // 信号量缓存
        private val semaphores = ConcurrentHashMap<String, VertxSemaphore>()
        
        /**
         * 获取或创建信号量
         * 
         * @param vertx Vertx实例
         * @param name 信号量名称
         * @param maxPermits 最大许可数
         * @param timeout 获取许可的超时时间（毫秒）
         * @return VertxSemaphore实例
         */
        fun getOrCreate(vertx: Vertx, name: String, maxPermits: Int, timeout: Long = 30000): VertxSemaphore {
            return semaphores.computeIfAbsent(name) {
                VertxSemaphore(name, vertx, maxPermits, timeout)
            }
        }
        
        /**
         * 获取所有信号量的统计信息
         * 
         * @return 包含所有信号量统计信息的JsonObject
         */
        fun getAllStats(): JsonObject {
            val stats = JsonObject()
            
            semaphores.forEach { (name, semaphore) ->
                stats.put(name, semaphore.getStats())
            }
            
            return stats
        }
    }
}
