package com.louloulin.apix.scaling.affinity

import java.util.SortedMap
import java.util.TreeMap
import java.util.zip.CRC32

/**
 * 一致性哈希环
 * 
 * 提供一致性哈希算法实现，用于会话亲和性和负载均衡
 */
class ConsistentHashRing<T>(private val numberOfReplicas: Int) {
    // 哈希环
    private val circle = TreeMap<Long, T>()
    
    // 节点列表
    private val nodes = mutableSetOf<T>()
    
    /**
     * 添加节点
     * 
     * @param node 节点
     */
    fun add(node: T) {
        if (nodes.add(node)) {
            // 为每个节点创建多个虚拟节点
            for (i in 0 until numberOfReplicas) {
                val key = generateKey("$node-$i")
                circle[key] = node
            }
        }
    }
    
    /**
     * 移除节点
     * 
     * @param node 节点
     */
    fun remove(node: T) {
        if (nodes.remove(node)) {
            // 移除所有虚拟节点
            for (i in 0 until numberOfReplicas) {
                val key = generateKey("$node-$i")
                circle.remove(key)
            }
        }
    }
    
    /**
     * 获取节点
     * 
     * @param key 键
     * @return 节点
     */
    fun get(key: String): T? {
        if (circle.isEmpty()) {
            return null
        }
        
        // 计算键的哈希值
        val hash = generateKey(key)
        
        // 如果哈希环中没有大于等于该哈希值的节点，则返回第一个节点
        if (!circle.containsKey(hash)) {
            val tailMap = circle.tailMap(hash)
            val hashKey = if (tailMap.isEmpty()) circle.firstKey() else tailMap.firstKey()
            return circle[hashKey]
        }
        
        // 返回大于等于该哈希值的第一个节点
        return circle[hash]
    }
    
    /**
     * 获取所有节点
     * 
     * @return 节点列表
     */
    fun getNodes(): Set<T> {
        return nodes.toSet()
    }
    
    /**
     * 生成键的哈希值
     * 
     * @param key 键
     * @return 哈希值
     */
    private fun generateKey(key: String): Long {
        val crc32 = CRC32()
        crc32.update(key.toByteArray())
        return crc32.value
    }
    
    /**
     * 获取哈希环大小
     * 
     * @return 哈希环大小
     */
    fun size(): Int {
        return circle.size
    }
    
    /**
     * 清空哈希环
     */
    fun clear() {
        nodes.clear()
        circle.clear()
    }
}
