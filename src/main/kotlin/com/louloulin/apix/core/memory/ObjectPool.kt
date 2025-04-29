package com.louloulin.apix.core.memory

import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 对象池，用于重用对象，减少对象创建和垃圾回收的开销
 */
class ObjectPool<T>(
    private val name: String,
    private val factory: () -> T,
    initialSize: Int = 10,
    private val maxSize: Int = 100
) {
    private val logger = LoggerFactory.getLogger(ObjectPool::class.java)
    
    // 对象池
    private val pool = ConcurrentLinkedQueue<T>()
    
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
        for (i in 0 until initialSize) {
            try {
                val obj = factory()
                pool.offer(obj)
                created.incrementAndGet()
            } catch (e: Exception) {
                logger.error("创建对象失败", e)
            }
        }
        
        logger.info("对象池 '$name' 初始化完成，初始大小: $initialSize")
    }
    
    /**
     * 从对象池中借出一个对象
     */
    fun borrow(): T {
        val obj = pool.poll() ?: createObject()
        
        synchronized(borrowedObjects) {
            borrowedObjects[obj] = System.currentTimeMillis()
        }
        
        borrowed.incrementAndGet()
        return obj
    }
    
    /**
     * 将对象归还到对象池
     */
    fun release(obj: T) {
        val borrowTime: Long
        
        synchronized(borrowedObjects) {
            val borrowedAt = borrowedObjects.remove(obj) ?: return
            borrowTime = System.currentTimeMillis() - borrowedAt
        }
        
        totalBorrowTime.addAndGet(borrowTime)
        borrowCount.incrementAndGet()
        
        if (pool.size < maxSize) {
            pool.offer(obj)
            returned.incrementAndGet()
        } else {
            // 对象池已满，销毁对象
            destroyed.incrementAndGet()
        }
    }
    
    /**
     * 清空对象池
     */
    fun clear() {
        pool.clear()
        
        synchronized(borrowedObjects) {
            borrowedObjects.clear()
        }
        
        logger.info("对象池 '$name' 已清空")
    }
    
    /**
     * 获取对象池统计信息
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
            .put("avgBorrowTime", avgBorrowTime)
    }
    
    /**
     * 创建新对象
     */
    private fun createObject(): T {
        val obj = factory()
        created.incrementAndGet()
        return obj
    }
}
