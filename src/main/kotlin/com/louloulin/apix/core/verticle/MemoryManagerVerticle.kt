package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.memory.MemoryManager
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

/**
 * 内存管理 Verticle，负责监控和优化系统内存使用
 */
class MemoryManagerVerticle : CoroutineVerticle() {
    private val logger = LoggerFactory.getLogger(MemoryManagerVerticle::class.java)
    private lateinit var memoryManager: MemoryManager

    override suspend fun start() {
        logger.info("启动 MemoryManagerVerticle...")

        // 初始化内存管理器
        memoryManager = MemoryManager(vertx)

        // 获取配置
        val configMessage = vertx.eventBus().request<JsonObject>(
            EventBusAddresses.CONFIG_GET,
            JsonObject().put("section", "memory")
        ).await()

        val configResponse = configMessage.body()
        val config = if (configResponse.getBoolean("success", false)) {
            configResponse.getJsonObject("result", JsonObject())
        } else {
            JsonObject()
        }

        // 初始化内存管理器
        memoryManager.initialize(config)

        // 注册 EventBus 处理器
        registerEventBusHandlers()

        // 注册内存状态变化处理器
        registerMemoryHandlers()

        logger.info("MemoryManagerVerticle 启动完成")
    }

    /**
     * 注册 EventBus 处理器
     */
    private fun registerEventBusHandlers() {
        // 获取内存使用情况
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.MEMORY_USAGE_GET) { message ->
            launch {
                try {
                    val usage = memoryManager.getMemoryUsage()
                    val response = JsonObject()
                        .put("success", true)
                        .put("usage", usage)

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("获取内存使用情况失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 触发垃圾回收
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.MEMORY_GC_TRIGGER) { message ->
            launch {
                try {
                    memoryManager.triggerGC()
                    val usage = memoryManager.getMemoryUsage()
                    val response = JsonObject()
                        .put("success", true)
                        .put("message", "Garbage collection triggered")
                        .put("usage", usage)

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("触发垃圾回收失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 清理内存缓存
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.MEMORY_CACHE_CLEAR) { message ->
            launch {
                try {
                    memoryManager.clearCaches()
                    val usage = memoryManager.getMemoryUsage()
                    val response = JsonObject()
                        .put("success", true)
                        .put("message", "Memory caches cleared")
                        .put("usage", usage)

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("清理内存缓存失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 创建对象池
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.OBJECT_POOL_CREATE) { message ->
            launch {
                try {
                    val request = message.body()
                    val poolName = request.getString("name")
                    val initialSize = request.getInteger("initialSize", 10)
                    val maxSize = request.getInteger("maxSize", 100)

                    if (poolName == null) {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", "Pool name is required")
                        )
                        return@launch
                    }

                    // 创建对象池
                    // 注意：这里我们只创建一个简单的字符串对象池作为示例
                    val pool = memoryManager.getOrCreateObjectPool<String>(
                        poolName,
                        { "" },  // 工厂函数
                        initialSize,
                        maxSize
                    )

                    val stats = pool.getStats()
                    val response = JsonObject()
                        .put("success", true)
                        .put("message", "Object pool created")
                        .put("stats", stats)

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("创建对象池失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 移除对象池
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.OBJECT_POOL_REMOVE) { message ->
            launch {
                try {
                    val request = message.body()
                    val poolName = request.getString("name")

                    if (poolName == null) {
                        message.reply(JsonObject()
                            .put("success", false)
                            .put("error", "Pool name is required")
                        )
                        return@launch
                    }

                    // 移除对象池
                    memoryManager.removeObjectPool(poolName)

                    val response = JsonObject()
                        .put("success", true)
                        .put("message", "Object pool removed")

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("移除对象池失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }

        // 获取对象池统计信息
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.OBJECT_POOL_GET_STATS) { message ->
            launch {
                try {
                    val usage = memoryManager.getMemoryUsage()
                    val objectPools = usage.getJsonObject("objectPools", JsonObject())

                    val response = JsonObject()
                        .put("success", true)
                        .put("objectPools", objectPools)

                    message.reply(response)
                } catch (e: Exception) {
                    logger.error("获取对象池统计信息失败", e)
                    message.reply(JsonObject()
                        .put("success", false)
                        .put("error", e.message)
                    )
                }
            }
        }
    }

    /**
     * 注册内存状态变化处理器
     */
    private fun registerMemoryHandlers() {
        // 低内存处理
        memoryManager.addLowMemoryHandler {
            logger.warn("低内存警告：系统将采取措施释放内存")

            // 通知其他组件进入低内存模式
            vertx.eventBus().publish(EventBusAddresses.MEMORY_LOW_NOTIFY, JsonObject()
                .put("status", "low")
                .put("usage", memoryManager.getMemoryUsage())
            )

            // 可以在这里添加一些低内存处理逻辑
            // 例如：清理不必要的缓存、减少并发请求数等
        }

        // 严重内存不足处理
        memoryManager.addCriticalMemoryHandler {
            logger.error("严重内存不足警告：系统将采取紧急措施释放内存")

            // 通知其他组件进入严重内存不足模式
            vertx.eventBus().publish(EventBusAddresses.MEMORY_CRITICAL_NOTIFY, JsonObject()
                .put("status", "critical")
                .put("usage", memoryManager.getMemoryUsage())
            )

            // 触发垃圾回收
            memoryManager.triggerGC()

            // 清理内存缓存
            memoryManager.clearCaches()

            // 可以在这里添加一些严重内存不足处理逻辑
            // 例如：拒绝新请求、关闭非关键功能等
        }

        // 内存恢复处理
        memoryManager.addMemoryRestoredHandler {
            logger.info("内存已恢复正常")

            // 通知其他组件内存已恢复正常
            vertx.eventBus().publish(EventBusAddresses.MEMORY_RESTORED_NOTIFY, JsonObject()
                .put("status", "normal")
                .put("usage", memoryManager.getMemoryUsage())
            )

            // 可以在这里添加一些内存恢复处理逻辑
            // 例如：恢复正常的并发请求数、重新启用缓存等
        }
    }

    override suspend fun stop() {
        logger.info("停止 MemoryManagerVerticle...")
    }
}
