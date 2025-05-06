#!/bin/bash

# 脚本用于全面移除JCTools相关代码，以支持Native Image编译

echo "===== 开始移除JCTools相关代码 ====="

# 1. 修改build.gradle.kts，移除JCTools依赖
echo "修改build.gradle.kts，移除JCTools依赖..."
sed -i.bak '/jctools/d' build.gradle.kts

# 2. 重命名JCToolsEventBus为SimpleEventBus
echo "重命名JCToolsEventBus为SimpleEventBus..."
mkdir -p src/main/kotlin/com/louloulin/apix/core/eventbus/backup
cp src/main/kotlin/com/louloulin/apix/core/eventbus/JCToolsEventBus.kt src/main/kotlin/com/louloulin/apix/core/eventbus/backup/

# 3. 创建新的SimpleEventBus实现
echo "创建新的SimpleEventBus实现..."
cat > src/main/kotlin/com/louloulin/apix/core/eventbus/SimpleEventBus.kt << 'EOF'
package com.louloulin.apix.core.eventbus

import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 简化版EventBus实现，直接使用Vert.x原生EventBus
 * 移除了JCTools依赖以支持Native Image编译
 */
class SimpleEventBus(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(SimpleEventBus::class.java)

    // 原生EventBus
    private val originalEventBus: EventBus = vertx.eventBus()

    // 是否已启动
    private val started = AtomicBoolean(false)

    // 性能统计
    private val messagesSent = AtomicLong(0)
    private val messagesProcessed = AtomicLong(0)
    private val messagesDropped = AtomicLong(0)

    // 地址缓存
    private val addressCache = ConcurrentHashMap<String, Long>()

    /**
     * 启动SimpleEventBus
     */
    fun start() {
        if (started.compareAndSet(false, true)) {
            logger.info("Starting simplified EventBus wrapper")
            logger.info("EventBus wrapper started successfully")
        } else {
            logger.info("EventBus wrapper already started")
        }
    }

    /**
     * 获取原生EventBus
     */
    fun getOriginalEventBus(): EventBus {
        return originalEventBus
    }

    /**
     * 发送消息，直接使用原生EventBus
     */
    fun sendToQueue(address: String, message: Any) {
        // 检查是否已启动
        if (!started.get()) {
            start()
        }

        // 更新统计信息
        messagesSent.incrementAndGet()

        // 更新地址缓存
        addressCache.put(address, System.currentTimeMillis())

        // 直接使用原生EventBus发送
        originalEventBus.send(address, message)

        // 更新统计信息
        messagesProcessed.incrementAndGet()
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        return JsonObject()
            .put("started", started.get())
            .put("messages_sent", messagesSent.get())
            .put("messages_processed", messagesProcessed.get())
            .put("messages_dropped", messagesDropped.get())
            .put("address_count", addressCache.size)
            .put("timestamp", System.currentTimeMillis())
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        messagesSent.set(0)
        messagesProcessed.set(0)
        messagesDropped.set(0)
        addressCache.clear()

        logger.info("Statistics reset")
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: SimpleEventBus? = null

        /**
         * 获取SimpleEventBus的单例实例
         */
        fun getInstance(vertx: Vertx): SimpleEventBus {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SimpleEventBus(vertx).also { INSTANCE = it }
            }
        }
    }
}
EOF

# 4. 修改EventBusManager，使用SimpleEventBus替代JCToolsEventBus
echo "修改EventBusManager，使用SimpleEventBus替代JCToolsEventBus..."
cp src/main/kotlin/com/louloulin/apix/core/eventbus/EventBusManager.kt src/main/kotlin/com/louloulin/apix/core/eventbus/backup/

cat > src/main/kotlin/com/louloulin/apix/core/eventbus/EventBusManager.kt << 'EOF'
package com.louloulin.apix.core.eventbus

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.eventbus.EventBus
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * EventBus管理器，用于管理EventBus实例
 */
class EventBusManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(EventBusManager::class.java)

    // 当前EventBus类型
    private val currentType = AtomicReference(EventBusType.VERTX)

    // SimpleEventBus实例
    private val simpleEventBus = SimpleEventBus.getInstance(vertx)

    // 性能统计
    private val messagesSent = AtomicLong(0)
    private val switchCount = AtomicLong(0)
    private val lastSwitchTime = AtomicLong(0)
    private val startTime = AtomicLong(System.currentTimeMillis())

    /**
     * EventBus类型
     */
    enum class EventBusType {
        VERTX,      // 原生Vert.x EventBus
        SIMPLE      // 简化版EventBus
    }

    /**
     * 获取当前EventBus类型
     */
    fun getCurrentType(): EventBusType {
        return currentType.get()
    }

    /**
     * 获取EventBus实例
     */
    fun getEventBus(): EventBus {
        return when (currentType.get()) {
            EventBusType.VERTX -> vertx.eventBus()
            EventBusType.SIMPLE -> simpleEventBus.getOriginalEventBus()
        }
    }

    /**
     * 发送消息
     */
    fun send(address: String, message: Any) {
        // 更新统计信息
        messagesSent.incrementAndGet()

        when (currentType.get()) {
            EventBusType.VERTX -> vertx.eventBus().send(address, message)
            EventBusType.SIMPLE -> simpleEventBus.sendToQueue(address, message)
        }
    }

    /**
     * 发布消息
     */
    fun publish(address: String, message: Any) {
        // 更新统计信息
        messagesSent.incrementAndGet()

        // 发布消息始终使用原生EventBus
        vertx.eventBus().publish(address, message)
    }

    /**
     * 切换EventBus类型
     */
    fun switchType(type: EventBusType): Future<Boolean> {
        val promise = Promise.promise<Boolean>()

        try {
            // 如果类型相同，直接返回成功
            if (currentType.get() == type) {
                promise.complete(true)
                return promise.future()
            }

            logger.info("Switching EventBus type from {} to {}", currentType.get(), type)

            // 更新统计信息
            switchCount.incrementAndGet()
            lastSwitchTime.set(System.currentTimeMillis())

            // 切换类型
            when (type) {
                EventBusType.VERTX -> {
                    // 切换到原生EventBus
                    currentType.set(EventBusType.VERTX)
                    logger.info("Switched to VERTX EventBus")
                    promise.complete(true)
                }
                EventBusType.SIMPLE -> {
                    // 切换到SimpleEventBus
                    simpleEventBus.start()
                    currentType.set(EventBusType.SIMPLE)
                    logger.info("Switched to SimpleEventBus")
                    promise.complete(true)
                }
            }
        } catch (e: Exception) {
            logger.error("Error switching EventBus type", e)
            promise.fail(e)
        }

        return promise.future()
    }

    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        val currentTimeMillis = System.currentTimeMillis()
        val uptime = currentTimeMillis - startTime.get()

        val stats = JsonObject()
            .put("type", currentType.get().name)
            .put("messages_sent", messagesSent.get())
            .put("switch_count", switchCount.get())
            .put("last_switch_time", lastSwitchTime.get())
            .put("start_time", startTime.get())
            .put("uptime_ms", uptime)
            .put("timestamp", currentTimeMillis)

        // 添加SimpleEventBus统计信息
        if (currentType.get() == EventBusType.SIMPLE) {
            stats.put("simple", simpleEventBus.getStats())
        }

        return stats
    }

    /**
     * 重置统计信息
     */
    fun resetStats() {
        messagesSent.set(0)

        // 重置SimpleEventBus统计信息
        simpleEventBus.resetStats()

        logger.info("Statistics reset")
    }

    companion object {
        // 单例实例
        @Volatile
        private var INSTANCE: EventBusManager? = null

        /**
         * 获取EventBusManager的单例实例
         */
        fun getInstance(vertx: Vertx): EventBusManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: EventBusManager(vertx).also { INSTANCE = it }
            }
        }
    }
}
EOF

# 5. 修改Main.kt，使用SimpleEventBus替代JCToolsEventBus
echo "修改Main.kt，使用SimpleEventBus替代JCToolsEventBus..."
cp src/main/kotlin/com/louloulin/apix/Main.kt src/main/kotlin/com/louloulin/apix/backup_Main.kt

# 使用sed替换JCToolsEventBus为SimpleEventBus
sed -i.bak 's/JCToolsEventBus/SimpleEventBus/g' src/main/kotlin/com/louloulin/apix/Main.kt
sed -i.bak 's/JCTools EventBus/Simple EventBus/g' src/main/kotlin/com/louloulin/apix/Main.kt

# 6. 修改run-100k.sh，移除JCTools相关配置
echo "修改run-100k.sh，移除JCTools相关配置..."
cp run-100k.sh run-100k.sh.bak

# 替换JCTools相关配置
sed -i.bak 's/# JCToolsEventBus 优化 - 极致性能/# EventBus 优化 - 简化版/g' run-100k.sh
sed -i.bak 's/-Dapix.eventbus.type=jctools/-Dapix.eventbus.type=simple/g' run-100k.sh
sed -i.bak '/apix.eventbus.queue.capacity/d' run-100k.sh
sed -i.bak '/apix.eventbus.batch.min/d' run-100k.sh
sed -i.bak '/apix.eventbus.batch.max/d' run-100k.sh
sed -i.bak '/apix.eventbus.batch.initial/d' run-100k.sh
sed -i.bak '/apix.eventbus.partition.count/d' run-100k.sh

# 7. 移除JCTools相关的native-image配置
echo "移除JCTools相关的native-image配置..."
cp src/main/resources/META-INF/native-image/jctools-config.json src/main/resources/META-INF/native-image/backup-jctools-config.json 2>/dev/null || :
rm -f src/main/resources/META-INF/native-image/jctools-config.json 2>/dev/null || :

# 修改native-image.properties，移除JCTools相关配置
sed -i.bak 's/,org.jctools//g' src/main/resources/META-INF/native-image/native-image.properties

# 8. 删除原JCToolsEventBus文件
echo "删除原JCToolsEventBus文件..."
rm -f src/main/kotlin/com/louloulin/apix/core/eventbus/JCToolsEventBus.kt

echo "===== JCTools相关代码移除完成 ====="
echo "现在可以尝试编译Native Image了"
