package com.louloulin.apix.plugins.resource

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.AsyncMap
import io.vertx.core.shareddata.LocalMap
import io.vertx.core.shareddata.SharedData
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 共享数据管理器
 * 基于 Vert.x 的共享数据结构，用于插件之间共享数据
 */
class SharedDataManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SharedDataManager::class.java)
    private val sharedData: SharedData = vertx.sharedData()

    // 异步映射缓存
    private val asyncMaps = ConcurrentHashMap<String, Future<AsyncMap<String, Buffer>>>()

    // 本地映射缓存
    private val localMaps = ConcurrentHashMap<String, LocalMap<String, Any>>()

    /**
     * 获取异步映射
     *
     * @param name 映射名称
     * @return 异步映射的 Future
     */
    fun getAsyncMap(name: String): Future<AsyncMap<String, Buffer>> {
        return asyncMaps.computeIfAbsent(name) {
            logger.info("Creating async map: {}", name)
            sharedData.getAsyncMap(name)
        }
    }

    /**
     * 获取集群范围的映射
     *
     * @param name 映射名称
     * @return 集群范围的映射的 Future
     */
    fun getClusterWideMap(name: String): Future<AsyncMap<String, Buffer>> {
        logger.info("Getting cluster-wide map: {}", name)
        // 在非集群模式下，使用普通的 AsyncMap
        return sharedData.getAsyncMap(name)
    }

    /**
     * 获取本地映射
     *
     * @param name 映射名称
     * @return 本地映射
     */
    fun getLocalMap(name: String): LocalMap<String, Any> {
        return localMaps.computeIfAbsent(name) {
            logger.info("Creating local map: {}", name)
            sharedData.getLocalMap(name)
        }
    }

    /**
     * 在异步映射中设置值
     *
     * @param mapName 映射名称
     * @param key 键
     * @param value 值
     * @param ttl 过期时间（毫秒），0 表示永不过期
     * @return 设置完成的 Future
     */
    fun putInAsyncMap(mapName: String, key: String, value: JsonObject, ttl: Long = 0): Future<Void> {
        return getAsyncMap(mapName).compose { map ->
            val buffer = Buffer.buffer(value.encode())
            if (ttl > 0) {
                map.put(key, buffer, ttl)
            } else {
                map.put(key, buffer)
            }
        }
    }

    /**
     * 从异步映射中获取值
     *
     * @param mapName 映射名称
     * @param key 键
     * @return 值的 Future
     */
    fun getFromAsyncMap(mapName: String, key: String): Future<JsonObject?> {
        return getAsyncMap(mapName).compose { map ->
            map.get(key).map { buffer ->
                if (buffer != null) {
                    JsonObject(buffer)
                } else {
                    null
                }
            }
        }
    }

    /**
     * 从异步映射中删除值
     *
     * @param mapName 映射名称
     * @param key 键
     * @return 删除完成的 Future
     */
    fun removeFromAsyncMap(mapName: String, key: String): Future<Void> {
        return getAsyncMap(mapName).compose { map ->
            map.remove(key).map { null as Void? }
        }
    }

    /**
     * 清空异步映射
     *
     * @param mapName 映射名称
     * @return 清空完成的 Future
     */
    fun clearAsyncMap(mapName: String): Future<Void> {
        return getAsyncMap(mapName).compose { map ->
            map.clear()
        }
    }

    /**
     * 在本地映射中设置值
     *
     * @param mapName 映射名称
     * @param key 键
     * @param value 值
     */
    fun putInLocalMap(mapName: String, key: String, value: Any) {
        getLocalMap(mapName).put(key, value)
    }

    /**
     * 从本地映射中获取值
     *
     * @param mapName 映射名称
     * @param key 键
     * @return 值
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getFromLocalMap(mapName: String, key: String): T? {
        return getLocalMap(mapName)[key] as? T
    }

    /**
     * 从本地映射中删除值
     *
     * @param mapName 映射名称
     * @param key 键
     * @return 被删除的值
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> removeFromLocalMap(mapName: String, key: String): T? {
        return getLocalMap(mapName).remove(key) as? T
    }

    /**
     * 清空本地映射
     *
     * @param mapName 映射名称
     */
    fun clearLocalMap(mapName: String) {
        getLocalMap(mapName).clear()
    }

    /**
     * 关闭共享数据管理器
     *
     * @return 关闭完成的 Future
     */
    fun close(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 清空本地映射
            localMaps.forEach { (name, map) ->
                logger.info("Clearing local map: {}", name)
                map.clear()
            }
            localMaps.clear()

            // 清空异步映射缓存
            asyncMaps.clear()

            promise.complete()
        } catch (e: Exception) {
            logger.error("Error closing shared data manager", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取共享计数器
     *
     * @param name 计数器名称
     * @return 共享计数器的 Future
     */
    fun getCounter(name: String): Future<io.vertx.core.shareddata.Counter> {
        logger.info("Getting counter: {}", name)
        return sharedData.getCounter(name)
    }

    /**
     * 获取集群范围的计数器
     *
     * @param name 计数器名称
     * @return 集群范围的计数器的 Future
     */
    fun getClusterWideCounter(name: String): Future<io.vertx.core.shareddata.Counter> {
        logger.info("Getting cluster-wide counter: {}", name)
        // 在非集群模式下，使用普通的 Counter
        return sharedData.getCounter(name)
    }

    /**
     * 获取分布式锁
     *
     * @param name 锁名称
     * @return 分布式锁的 Future
     */
    fun getLock(name: String): Future<io.vertx.core.shareddata.Lock> {
        logger.info("Getting lock: {}", name)
        return sharedData.getLock(name)
    }

    /**
     * 获取集群范围的锁
     *
     * @param name 锁名称
     * @return 集群范围的锁的 Future
     */
    fun getClusterWideLock(name: String): Future<io.vertx.core.shareddata.Lock> {
        logger.info("Getting cluster-wide lock: {}", name)
        // 在非集群模式下，使用普通的 Lock
        return sharedData.getLock(name)
    }

    /**
     * 获取带超时的分布式锁
     *
     * @param name 锁名称
     * @param timeout 超时时间（毫秒）
     * @return 分布式锁的 Future
     */
    fun getLockWithTimeout(name: String, timeout: Long): Future<io.vertx.core.shareddata.Lock> {
        logger.info("Getting lock with timeout: {}, timeout: {}", name, timeout)
        return sharedData.getLockWithTimeout(name, timeout)
    }

    /**
     * 获取带超时的集群范围的锁
     *
     * @param name 锁名称
     * @param timeout 超时时间（毫秒）
     * @return 集群范围的锁的 Future
     */
    fun getClusterWideLockWithTimeout(name: String, timeout: Long): Future<io.vertx.core.shareddata.Lock> {
        logger.info("Getting cluster-wide lock with timeout: {}, timeout: {}", name, timeout)
        // 在非集群模式下，使用普通的带超时的 Lock
        return sharedData.getLockWithTimeout(name, timeout)
    }

    /**
     * 获取共享数据管理器统计信息
     *
     * @return 统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("asyncMaps", asyncMaps.size)
            .put("localMaps", localMaps.size)
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: SharedDataManager? = null

        /**
         * 获取 SharedDataManager 的单例实例
         */
        fun getInstance(vertx: Vertx): SharedDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SharedDataManager(vertx).also { INSTANCE = it }
            }
        }
    }
}
