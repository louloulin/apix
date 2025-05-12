package com.louloulin.apix.edge.config

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 配置存储工厂类
 * 负责创建和管理不同的配置存储
 * 实现 plan7.md 中的 4.2.2 节"配置管理"功能
 */
class ConfigStoreFactory(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ConfigStoreFactory::class.java)

    // 配置存储实例映射
    private val stores = ConcurrentHashMap<String, ConfigStore>()

    /**
     * 获取 ConfigStoreFactory 实例
     */
    companion object {
        private var instance: ConfigStoreFactory? = null

        @Synchronized
        fun getInstance(vertx: Vertx): ConfigStoreFactory {
            if (instance == null) {
                instance = ConfigStoreFactory(vertx)
            }
            return instance!!
        }
    }

    /**
     * 创建配置存储
     *
     * @param type 配置存储类型
     * @param config 配置
     * @return Future<ConfigStore> 配置存储实例
     */
    fun createStore(type: ConfigStoreType, config: JsonObject): Future<ConfigStore> {
        val promise = Promise.promise<ConfigStore>()

        try {
            // 生成存储ID
            val storeId = config.getString("id", "${type.name.toLowerCase()}-${System.currentTimeMillis()}")

            // 检查是否已存在
            if (stores.containsKey(storeId)) {
                promise.complete(stores[storeId])
                return promise.future()
            }

            // 创建配置存储实例
            val store = when (type) {
                ConfigStoreType.GIT -> GitConfigStore(vertx)
                ConfigStoreType.GITOPS -> throw UnsupportedOperationException("GitOps 配置存储尚未实现")
                ConfigStoreType.FILE -> FileConfigStore(vertx)
                ConfigStoreType.DATABASE -> throw UnsupportedOperationException("数据库配置存储尚未实现")
                ConfigStoreType.KUBERNETES -> throw UnsupportedOperationException("Kubernetes 配置存储尚未实现")
                ConfigStoreType.CONSUL -> throw UnsupportedOperationException("Consul 配置存储尚未实现")
                ConfigStoreType.ETCD -> throw UnsupportedOperationException("Etcd 配置存储尚未实现")
                ConfigStoreType.ZOOKEEPER -> throw UnsupportedOperationException("ZooKeeper 配置存储尚未实现")
                ConfigStoreType.REDIS -> throw UnsupportedOperationException("Redis 配置存储尚未实现")
                ConfigStoreType.MEMORY -> throw UnsupportedOperationException("内存配置存储尚未实现")
            }

            // 更新配置中的 ID
            val updatedConfig = config.copy().put("id", storeId)

            // 初始化配置存储
            store.initialize(updatedConfig)
                .onSuccess {
                    // 保存配置存储实例
                    stores[storeId] = store

                    logger.info("创建配置存储成功: {}, {}", type, storeId)
                    promise.complete(store)
                }
                .onFailure { cause ->
                    logger.error("初始化配置存储失败: {}, {}", type, storeId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("创建配置存储失败: {}", type, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取配置存储
     *
     * @param storeId 配置存储ID
     * @return ConfigStore? 配置存储实例，如果不存在则返回 null
     */
    fun getStore(storeId: String): ConfigStore? {
        return stores[storeId]
    }

    /**
     * 获取所有配置存储
     *
     * @return Map<String, ConfigStore> 所有配置存储实例
     */
    fun getAllStores(): Map<String, ConfigStore> {
        return stores.toMap()
    }

    /**
     * 关闭配置存储
     *
     * @param storeId 配置存储ID
     * @return Future<Void> 关闭结果
     */
    fun closeStore(storeId: String): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 检查是否存在
            val store = stores[storeId]
            if (store == null) {
                promise.complete()
                return promise.future()
            }

            // 关闭配置存储
            store.close()
                .onSuccess {
                    // 移除配置存储实例
                    stores.remove(storeId)

                    logger.info("关闭配置存储成功: {}", storeId)
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("关闭配置存储失败: {}", storeId, cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("关闭配置存储失败: {}", storeId, e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 关闭所有配置存储
     *
     * @return Future<Void> 关闭结果
     */
    fun closeAllStores(): Future<Void> {
        val promise = Promise.promise<Void>()

        try {
            // 创建关闭任务列表
            val closeTasks = mutableListOf<Future<Void>>()

            // 关闭所有配置存储
            for (storeId in stores.keys) {
                closeTasks.add(closeStore(storeId))
            }

            // 等待所有关闭任务完成
            Future.all(closeTasks)
                .onSuccess {
                    logger.info("关闭所有配置存储成功")
                    promise.complete()
                }
                .onFailure { cause ->
                    logger.error("关闭所有配置存储失败", cause)
                    promise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("关闭所有配置存储失败", e)
            promise.fail(e)
        }

        return promise.future()
    }
}
