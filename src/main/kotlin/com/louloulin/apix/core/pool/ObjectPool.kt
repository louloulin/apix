package com.louloulin.apix.core.pool

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

/**
 * 通用对象池实现，用于减少GC压力
 * 
 * @param maxSize 池的最大大小
 * @param factory 创建新对象的工厂函数
 * @param reset 重置对象状态的函数（可选）
 */
class ObjectPool<T>(
    private val maxSize: Int,
    private val factory: Supplier<T>,
    private val reset: ((T) -> Unit)? = null
) {
    // 使用无锁队列存储对象
    private val pool = ConcurrentLinkedQueue<T>()
    
    // 统计信息
    private val created = AtomicInteger(0)
    private val borrowed = AtomicInteger(0)
    private val returned = AtomicInteger(0)
    
    /**
     * 从池中获取一个对象
     * 如果池为空，则创建一个新对象
     */
    fun acquire(): T {
        val obj = pool.poll() ?: factory.get().also { created.incrementAndGet() }
        borrowed.incrementAndGet()
        return obj
    }
    
    /**
     * 将对象返回到池中
     * 如果池已满，则丢弃对象
     */
    fun release(obj: T) {
        reset?.invoke(obj)
        if (pool.size < maxSize) {
            pool.offer(obj)
        }
        returned.incrementAndGet()
    }
    
    /**
     * 获取池的当前大小
     */
    fun size(): Int = pool.size
    
    /**
     * 获取统计信息
     */
    fun stats(): Map<String, Int> = mapOf(
        "size" to pool.size,
        "created" to created.get(),
        "borrowed" to borrowed.get(),
        "returned" to returned.get()
    )
    
    /**
     * 预热池，创建指定数量的对象
     */
    fun warmup(count: Int) {
        val warmupCount = minOf(count, maxSize)
        repeat(warmupCount) {
            pool.offer(factory.get().also { created.incrementAndGet() })
        }
    }
}
