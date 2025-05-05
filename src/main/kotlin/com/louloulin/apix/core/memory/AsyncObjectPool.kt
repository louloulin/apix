package com.louloulin.apix.core.memory

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 异步对象池，使用Vert.x的异步API重构对象池，减少线程阻塞。
 * 这个类提供了异步的对象借用和归还操作，适合在Vert.x的事件循环中使用。
 *
 * @param T 对象类型
 * @property name 对象池名称
 * @property vertx Vertx实例
 * @property factory 对象工厂函数
 * @property initialSize 初始大小
 * @property maxSize 最大大小
 */
class AsyncObjectPool<T>(
    private val name: String,
    private val vertx: Vertx,
    private val factory: () -> T,
    initialSize: Int = 10,
    private val maxSize: Int = 100
) {
    private val logger = LoggerFactory.getLogger(AsyncObjectPool::class.java)
    
    // 对象池
    private val pool = ConcurrentLinkedQueue<T>()
    
    // 等待队列
    private val waitQueue = ConcurrentLinkedQueue<Promise<T>>()
    
    // 统计信息
    private val created = AtomicInteger(0)
    private val borrowed = AtomicInteger(0)
    private val returned = AtomicInteger(0)
    private val destroyed = AtomicInteger(0)
    private val totalBorrowTime = AtomicLong(0)
    private val borrowCount = AtomicLong(0)
    
    // 借出的对象及其借出时间
    private val borrowedObjects = mutableMapOf<T, Long>()
    
    init {
        // 初始化对象池
        vertx.executeBlocking<Unit>({ promise ->
            try {
                for (i in 0 until initialSize) {
                    val obj = factory()
                    pool.offer(obj)
                    created.incrementAndGet()
                }
                promise.complete()
            } catch (e: Exception) {
                logger.error("创建对象失败", e)
                promise.fail(e)
            }
        })
        
        logger.info("异步对象池 '$name' 初始化完成，初始大小: $initialSize")
    }
    
    /**
     * 从对象池中借用一个对象
     * 
     * @return 包含借用对象的Future
     */
    fun borrow(): Future<T> {
        val promise = Promise.promise<T>()
        
        vertx.runOnContext { _ ->
            val obj = pool.poll()
            if (obj != null) {
                // 对象池中有可用对象
                recordBorrow(obj, promise)
            } else if (created.get() < maxSize) {
                // 对象池已空，但未达到最大大小，创建新对象
                try {
                    val newObj = factory()
                    created.incrementAndGet()
                    recordBorrow(newObj, promise)
                } catch (e: Exception) {
                    logger.error("创建对象失败", e)
                    promise.fail(e)
                }
            } else {
                // 对象池已满，加入等待队列
                waitQueue.offer(promise)
                logger.debug("对象池已满，请求加入等待队列，当前等待数: {}", waitQueue.size)
            }
        }
        
        return promise.future()
    }
    
    /**
     * 记录借用信息并完成Promise
     */
    private fun recordBorrow(obj: T, promise: Promise<T>) {
        synchronized(borrowedObjects) {
            borrowedObjects[obj] = System.currentTimeMillis()
        }
        
        borrowed.incrementAndGet()
        promise.complete(obj)
    }
    
    /**
     * 将对象归还到对象池
     * 
     * @param obj 要归还的对象
     * @return 包含操作结果的Future
     */
    fun release(obj: T): Future<Void> {
        val promise = Promise.promise<Void>()
        
        vertx.runOnContext { _ ->
            val borrowTime: Long
            
            synchronized(borrowedObjects) {
                val borrowedAt = borrowedObjects.remove(obj)
                if (borrowedAt == null) {
                    // 对象不是从这个池借出的
                    promise.complete()
                    return@runOnContext
                }
                borrowTime = System.currentTimeMillis() - borrowedAt
            }
            
            totalBorrowTime.addAndGet(borrowTime)
            borrowCount.incrementAndGet()
            
            // 检查等待队列
            val waitingPromise = waitQueue.poll()
            if (waitingPromise != null) {
                // 有等待的请求，直接将对象交给等待的请求
                recordBorrow(obj, waitingPromise)
                logger.debug("对象直接交给等待的请求，剩余等待数: {}", waitQueue.size)
            } else if (pool.size < maxSize) {
                // 没有等待的请求，将对象放回池中
                pool.offer(obj)
                returned.incrementAndGet()
            } else {
                // 对象池已满，销毁对象
                destroyed.incrementAndGet()
            }
            
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 清空对象池
     * 
     * @return 包含操作结果的Future
     */
    fun clear(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        vertx.runOnContext { _ ->
            pool.clear()
            
            synchronized(borrowedObjects) {
                borrowedObjects.clear()
            }
            
            // 拒绝所有等待的请求
            while (true) {
                val waitingPromise = waitQueue.poll() ?: break
                waitingPromise.fail("对象池已清空")
            }
            
            logger.info("异步对象池 '$name' 已清空")
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 获取对象池统计信息
     * 
     * @return 包含统计信息的JsonObject
     */
    fun getStats(): JsonObject {
        val avgBorrowTime = if (borrowCount.get() > 0) totalBorrowTime.get() / borrowCount.get() else 0
        
        return JsonObject()
            .put("name", name)
            .put("size", pool.size)
            .put("maxSize", maxSize)
            .put("created", created.get())
            .put("borrowed", borrowed.get())
            .put("returned", returned.get())
            .put("destroyed", destroyed.get())
            .put("active", borrowedObjects.size)
            .put("waiting", waitQueue.size)
            .put("avgBorrowTime", avgBorrowTime)
    }
}
