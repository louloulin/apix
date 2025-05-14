package com.louloulin.apix.cluster.sync

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.shareddata.AsyncMap
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 增量同步管理器
 * 
 * 提供基于版本的增量同步机制，优化集群状态同步效率
 */
class IncrementalSyncManager(
    private val vertx: Vertx,
    private val config: JsonObject = JsonObject()
) {
    private val logger = LoggerFactory.getLogger(IncrementalSyncManager::class.java)
    
    // 数据类型映射
    private val dataTypes = ConcurrentHashMap<String, DataTypeConfig>()
    
    // 版本计数器
    private val versionCounters = ConcurrentHashMap<String, AtomicLong>()
    
    // 变更日志
    private val changeLogs = ConcurrentHashMap<String, MutableList<ChangeLogEntry>>()
    
    // 共享数据映射
    private val sharedMaps = ConcurrentHashMap<String, AsyncMap<String, String>>()
    
    // 配置参数
    private val syncInterval: Long
    private val maxChangeLogSize: Int
    private val changeLogRetentionTime: Long
    
    // 运行状态
    private val running = AtomicBoolean(false)
    private var syncTimerId: Long = -1
    private var cleanupTimerId: Long = -1
    
    // 同步监听器
    private val syncListeners = ConcurrentHashMap<String, MutableList<(String, JsonObject, SyncOperation) -> Unit>>()
    
    /**
     * 数据类型配置
     */
    data class DataTypeConfig(
        val name: String,
        val mapName: String,
        val retentionTime: Long = 86400000, // 默认保留1天
        val syncPriority: Int = 0 // 同步优先级，数字越小优先级越高
    )
    
    /**
     * 变更日志条目
     */
    data class ChangeLogEntry(
        val version: Long,
        val timestamp: Long,
        val operation: SyncOperation,
        val key: String,
        val value: JsonObject?,
        val metadata: JsonObject = JsonObject()
    )
    
    /**
     * 同步操作类型
     */
    enum class SyncOperation {
        CREATE, UPDATE, DELETE, TRUNCATE
    }
    
    init {
        // 解析配置
        syncInterval = config.getLong("syncInterval", 5000)
        maxChangeLogSize = config.getInteger("maxChangeLogSize", 1000)
        changeLogRetentionTime = config.getLong("changeLogRetentionTime", 86400000) // 默认1天
        
        // 初始化数据类型
        val dataTypesArray = config.getJsonArray("dataTypes", JsonArray())
        for (i in 0 until dataTypesArray.size()) {
            val dataTypeObj = dataTypesArray.getJsonObject(i)
            val name = dataTypeObj.getString("name")
            
            if (name != null) {
                val mapName = dataTypeObj.getString("mapName", "apix.sync.$name")
                val retentionTime = dataTypeObj.getLong("retentionTime", changeLogRetentionTime)
                val syncPriority = dataTypeObj.getInteger("syncPriority", 0)
                
                dataTypes[name] = DataTypeConfig(name, mapName, retentionTime, syncPriority)
                versionCounters[name] = AtomicLong(0)
                changeLogs[name] = mutableListOf()
                
                logger.info("初始化数据类型：name={}, mapName={}, retentionTime={}ms, syncPriority={}",
                    name, mapName, retentionTime, syncPriority)
            }
        }
        
        // 如果没有配置数据类型，添加默认数据类型
        if (dataTypes.isEmpty()) {
            val defaultTypes = listOf("config", "routes", "services", "plugins")
            
            for (type in defaultTypes) {
                dataTypes[type] = DataTypeConfig(type, "apix.sync.$type")
                versionCounters[type] = AtomicLong(0)
                changeLogs[type] = mutableListOf()
                
                logger.info("初始化默认数据类型：name={}, mapName={}", type, "apix.sync.$type")
            }
        }
    }
    
    /**
     * 启动增量同步管理器
     */
    fun start(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        if (running.compareAndSet(false, true)) {
            logger.info("启动增量同步管理器")
            
            // 初始化共享数据映射
            initializeSharedMaps()
                .compose { loadVersionCounters() }
                .compose { startSyncTimer() }
                .compose { startCleanupTimer() }
                .onSuccess {
                    logger.info("增量同步管理器启动成功")
                    promise.complete()
                }
                .onFailure { err ->
                    logger.error("增量同步管理器启动失败", err)
                    running.set(false)
                    promise.fail(err)
                }
        } else {
            logger.info("增量同步管理器已经启动")
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 停止增量同步管理器
     */
    fun stop(): Future<Void> {
        val promise = Promise.promise<Void>()
        
        if (running.compareAndSet(true, false)) {
            logger.info("停止增量同步管理器")
            
            // 停止定时器
            if (syncTimerId != -1L) {
                vertx.cancelTimer(syncTimerId)
                syncTimerId = -1L
            }
            
            if (cleanupTimerId != -1L) {
                vertx.cancelTimer(cleanupTimerId)
                cleanupTimerId = -1L
            }
            
            // 保存版本计数器
            saveVersionCounters()
                .onComplete {
                    promise.complete()
                }
        } else {
            logger.info("增量同步管理器已经停止")
            promise.complete()
        }
        
        return promise.future()
    }
    
    /**
     * 初始化共享数据映射
     */
    private fun initializeSharedMaps(): Future<Void> {
        val promise = Promise.promise<Void>()
        val futures = mutableListOf<Future<Void>>()
        
        for ((_, config) in dataTypes) {
            val future = initializeSharedMap(config.mapName)
            futures.add(future)
        }
        
        // 初始化版本计数器映射
        val versionMapFuture = initializeSharedMap("apix.sync.versions")
        futures.add(versionMapFuture)
        
        // 等待所有映射初始化完成
        Future.all(futures)
            .onSuccess {
                promise.complete()
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 初始化单个共享数据映射
     */
    private fun initializeSharedMap(mapName: String): Future<Void> {
        val promise = Promise.promise<Void>()
        
        vertx.sharedData().getAsyncMap<String, String>(mapName) { ar ->
            if (ar.succeeded()) {
                sharedMaps[mapName] = ar.result()
                promise.complete()
            } else {
                logger.error("初始化共享数据映射失败：mapName={}", mapName, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 加载版本计数器
     */
    private fun loadVersionCounters(): Future<Void> {
        val promise = Promise.promise<Void>()
        val versionMap = sharedMaps["apix.sync.versions"]
        
        if (versionMap == null) {
            promise.fail("版本计数器映射未初始化")
            return promise.future()
        }
        
        val futures = mutableListOf<Future<Void>>()
        
        for ((dataType, counter) in versionCounters) {
            val future = Future.future<Void> { p ->
                versionMap.get(dataType) { ar ->
                    if (ar.succeeded()) {
                        val version = ar.result()?.toLongOrNull() ?: 0
                        counter.set(version)
                        logger.info("加载版本计数器：dataType={}, version={}", dataType, version)
                        p.complete()
                    } else {
                        logger.warn("加载版本计数器失败：dataType={}", dataType, ar.cause())
                        p.complete() // 失败时使用默认值0
                    }
                }
            }
            
            futures.add(future)
        }
        
        // 等待所有版本计数器加载完成
        Future.all(futures)
            .onSuccess {
                promise.complete()
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 保存版本计数器
     */
    private fun saveVersionCounters(): Future<Void> {
        val promise = Promise.promise<Void>()
        val versionMap = sharedMaps["apix.sync.versions"]
        
        if (versionMap == null) {
            promise.fail("版本计数器映射未初始化")
            return promise.future()
        }
        
        val futures = mutableListOf<Future<Void>>()
        
        for ((dataType, counter) in versionCounters) {
            val version = counter.get()
            
            val future = Future.future<Void> { p ->
                versionMap.put(dataType, version.toString()) { ar ->
                    if (ar.succeeded()) {
                        logger.debug("保存版本计数器：dataType={}, version={}", dataType, version)
                        p.complete()
                    } else {
                        logger.warn("保存版本计数器失败：dataType={}, version={}", dataType, version, ar.cause())
                        p.fail(ar.cause())
                    }
                }
            }
            
            futures.add(future)
        }
        
        // 等待所有版本计数器保存完成
        Future.all(futures)
            .onSuccess {
                promise.complete()
            }
            .onFailure { err ->
                promise.fail(err)
            }
        
        return promise.future()
    }
    
    /**
     * 启动同步定时器
     */
    private fun startSyncTimer(): Future<Void> {
        syncTimerId = vertx.setPeriodic(syncInterval) { _ ->
            synchronizeData()
        }
        
        return Future.succeededFuture()
    }
    
    /**
     * 启动清理定时器
     */
    private fun startCleanupTimer(): Future<Void> {
        cleanupTimerId = vertx.setPeriodic(changeLogRetentionTime / 2) { _ ->
            cleanupChangeLogs()
        }
        
        return Future.succeededFuture()
    }
    
    /**
     * 同步数据
     */
    private fun synchronizeData() {
        if (!running.get()) {
            return
        }
        
        try {
            // 按优先级排序数据类型
            val sortedDataTypes = dataTypes.values
                .sortedBy { it.syncPriority }
                .map { it.name }
            
            // 同步每种数据类型
            for (dataType in sortedDataTypes) {
                synchronizeDataType(dataType)
            }
        } catch (e: Exception) {
            logger.error("同步数据失败", e)
        }
    }
    
    /**
     * 同步指定数据类型
     */
    private fun synchronizeDataType(dataType: String) {
        val config = dataTypes[dataType] ?: return
        val map = sharedMaps[config.mapName] ?: return
        
        // 获取变更日志
        val log = changeLogs[dataType] ?: return
        
        // 如果没有变更，不需要同步
        if (log.isEmpty()) {
            return
        }
        
        logger.debug("同步数据类型：dataType={}, changeLogSize={}", dataType, log.size)
        
        // 应用变更到共享数据映射
        for (entry in log) {
            when (entry.operation) {
                SyncOperation.CREATE, SyncOperation.UPDATE -> {
                    if (entry.value != null) {
                        map.put(entry.key, entry.value.encode()) { ar ->
                            if (ar.failed()) {
                                logger.warn("同步数据失败：dataType={}, key={}, operation={}",
                                    dataType, entry.key, entry.operation, ar.cause())
                            }
                        }
                    }
                }
                SyncOperation.DELETE -> {
                    map.remove(entry.key) { ar ->
                        if (ar.failed()) {
                            logger.warn("同步数据失败：dataType={}, key={}, operation={}",
                                dataType, entry.key, entry.operation, ar.cause())
                        }
                    }
                }
                SyncOperation.TRUNCATE -> {
                    map.clear() { ar ->
                        if (ar.failed()) {
                            logger.warn("同步数据失败：dataType={}, operation={}",
                                dataType, entry.operation, ar.cause())
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 清理变更日志
     */
    private fun cleanupChangeLogs() {
        if (!running.get()) {
            return
        }
        
        try {
            val now = System.currentTimeMillis()
            
            for ((dataType, log) in changeLogs) {
                val config = dataTypes[dataType] ?: continue
                
                // 移除过期的变更日志
                val iterator = log.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    
                    if (now - entry.timestamp > config.retentionTime) {
                        iterator.remove()
                    }
                }
                
                // 如果日志超过最大大小，移除最旧的条目
                if (log.size > maxChangeLogSize) {
                    val removeCount = log.size - maxChangeLogSize
                    repeat(removeCount) {
                        log.removeAt(0)
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("清理变更日志失败", e)
        }
    }
    
    /**
     * 创建或更新数据
     */
    fun put(dataType: String, key: String, value: JsonObject, metadata: JsonObject = JsonObject()): Future<Long> {
        val promise = Promise.promise<Long>()
        
        if (!running.get()) {
            promise.fail("增量同步管理器未启动")
            return promise.future()
        }
        
        val config = dataTypes[dataType]
        if (config == null) {
            promise.fail("未知的数据类型：$dataType")
            return promise.future()
        }
        
        val map = sharedMaps[config.mapName]
        if (map == null) {
            promise.fail("共享数据映射未初始化：${config.mapName}")
            return promise.future()
        }
        
        // 检查数据是否存在
        map.get(key) { ar ->
            if (ar.succeeded()) {
                val exists = ar.result() != null
                val operation = if (exists) SyncOperation.UPDATE else SyncOperation.CREATE
                
                // 递增版本计数器
                val counter = versionCounters[dataType]
                if (counter == null) {
                    promise.fail("版本计数器未初始化：$dataType")
                    return@get
                }
                
                val version = counter.incrementAndGet()
                
                // 添加到变更日志
                val entry = ChangeLogEntry(
                    version = version,
                    timestamp = System.currentTimeMillis(),
                    operation = operation,
                    key = key,
                    value = value,
                    metadata = metadata
                )
                
                val log = changeLogs[dataType]
                if (log == null) {
                    promise.fail("变更日志未初始化：$dataType")
                    return@get
                }
                
                log.add(entry)
                
                // 保存到共享数据映射
                map.put(key, value.encode()) { putAr ->
                    if (putAr.succeeded()) {
                        // 通知监听器
                        notifySyncListeners(dataType, key, value, operation)
                        
                        logger.debug("数据已保存：dataType={}, key={}, operation={}, version={}",
                            dataType, key, operation, version)
                        
                        promise.complete(version)
                    } else {
                        logger.error("保存数据失败：dataType={}, key={}", dataType, key, putAr.cause())
                        promise.fail(putAr.cause())
                    }
                }
            } else {
                logger.error("检查数据是否存在失败：dataType={}, key={}", dataType, key, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取数据
     */
    fun get(dataType: String, key: String): Future<JsonObject?> {
        val promise = Promise.promise<JsonObject?>()
        
        if (!running.get()) {
            promise.fail("增量同步管理器未启动")
            return promise.future()
        }
        
        val config = dataTypes[dataType]
        if (config == null) {
            promise.fail("未知的数据类型：$dataType")
            return promise.future()
        }
        
        val map = sharedMaps[config.mapName]
        if (map == null) {
            promise.fail("共享数据映射未初始化：${config.mapName}")
            return promise.future()
        }
        
        map.get(key) { ar ->
            if (ar.succeeded()) {
                val value = ar.result()
                if (value != null) {
                    try {
                        val jsonValue = JsonObject(value)
                        promise.complete(jsonValue)
                    } catch (e: Exception) {
                        logger.error("解析JSON失败：dataType={}, key={}", dataType, key, e)
                        promise.fail(e)
                    }
                } else {
                    promise.complete(null)
                }
            } else {
                logger.error("获取数据失败：dataType={}, key={}", dataType, key, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取所有数据
     */
    fun getAll(dataType: String): Future<Map<String, JsonObject>> {
        val promise = Promise.promise<Map<String, JsonObject>>()
        
        if (!running.get()) {
            promise.fail("增量同步管理器未启动")
            return promise.future()
        }
        
        val config = dataTypes[dataType]
        if (config == null) {
            promise.fail("未知的数据类型：$dataType")
            return promise.future()
        }
        
        val map = sharedMaps[config.mapName]
        if (map == null) {
            promise.fail("共享数据映射未初始化：${config.mapName}")
            return promise.future()
        }
        
        map.entries { ar ->
            if (ar.succeeded()) {
                val entries = ar.result()
                val result = mutableMapOf<String, JsonObject>()
                
                for (entry in entries) {
                    try {
                        val jsonValue = JsonObject(entry.value)
                        result[entry.key] = jsonValue
                    } catch (e: Exception) {
                        logger.error("解析JSON失败：dataType={}, key={}", dataType, entry.key, e)
                    }
                }
                
                promise.complete(result)
            } else {
                logger.error("获取所有数据失败：dataType={}", dataType, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 删除数据
     */
    fun remove(dataType: String, key: String, metadata: JsonObject = JsonObject()): Future<Long> {
        val promise = Promise.promise<Long>()
        
        if (!running.get()) {
            promise.fail("增量同步管理器未启动")
            return promise.future()
        }
        
        val config = dataTypes[dataType]
        if (config == null) {
            promise.fail("未知的数据类型：$dataType")
            return promise.future()
        }
        
        val map = sharedMaps[config.mapName]
        if (map == null) {
            promise.fail("共享数据映射未初始化：${config.mapName}")
            return promise.future()
        }
        
        // 递增版本计数器
        val counter = versionCounters[dataType]
        if (counter == null) {
            promise.fail("版本计数器未初始化：$dataType")
            return promise.future()
        }
        
        val version = counter.incrementAndGet()
        
        // 添加到变更日志
        val entry = ChangeLogEntry(
            version = version,
            timestamp = System.currentTimeMillis(),
            operation = SyncOperation.DELETE,
            key = key,
            value = null,
            metadata = metadata
        )
        
        val log = changeLogs[dataType]
        if (log == null) {
            promise.fail("变更日志未初始化：$dataType")
            return promise.future()
        }
        
        log.add(entry)
        
        // 从共享数据映射中移除
        map.remove(key) { ar ->
            if (ar.succeeded()) {
                // 通知监听器
                notifySyncListeners(dataType, key, null, SyncOperation.DELETE)
                
                logger.debug("数据已删除：dataType={}, key={}, version={}", dataType, key, version)
                promise.complete(version)
            } else {
                logger.error("删除数据失败：dataType={}, key={}", dataType, key, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 清空数据类型
     */
    fun clear(dataType: String, metadata: JsonObject = JsonObject()): Future<Long> {
        val promise = Promise.promise<Long>()
        
        if (!running.get()) {
            promise.fail("增量同步管理器未启动")
            return promise.future()
        }
        
        val config = dataTypes[dataType]
        if (config == null) {
            promise.fail("未知的数据类型：$dataType")
            return promise.future()
        }
        
        val map = sharedMaps[config.mapName]
        if (map == null) {
            promise.fail("共享数据映射未初始化：${config.mapName}")
            return promise.future()
        }
        
        // 递增版本计数器
        val counter = versionCounters[dataType]
        if (counter == null) {
            promise.fail("版本计数器未初始化：$dataType")
            return promise.future()
        }
        
        val version = counter.incrementAndGet()
        
        // 添加到变更日志
        val entry = ChangeLogEntry(
            version = version,
            timestamp = System.currentTimeMillis(),
            operation = SyncOperation.TRUNCATE,
            key = "*",
            value = null,
            metadata = metadata
        )
        
        val log = changeLogs[dataType]
        if (log == null) {
            promise.fail("变更日志未初始化：$dataType")
            return promise.future()
        }
        
        log.add(entry)
        
        // 清空共享数据映射
        map.clear { ar ->
            if (ar.succeeded()) {
                // 通知监听器
                notifySyncListeners(dataType, "*", null, SyncOperation.TRUNCATE)
                
                logger.debug("数据已清空：dataType={}, version={}", dataType, version)
                promise.complete(version)
            } else {
                logger.error("清空数据失败：dataType={}", dataType, ar.cause())
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 获取变更日志
     */
    fun getChangeLog(dataType: String, fromVersion: Long = 0): List<ChangeLogEntry> {
        if (!running.get()) {
            return emptyList()
        }
        
        val log = changeLogs[dataType] ?: return emptyList()
        
        return log.filter { it.version > fromVersion }
    }
    
    /**
     * 获取当前版本
     */
    fun getCurrentVersion(dataType: String): Long {
        val counter = versionCounters[dataType] ?: return 0
        return counter.get()
    }
    
    /**
     * 添加同步监听器
     */
    fun addSyncListener(dataType: String, listener: (String, JsonObject, SyncOperation) -> Unit) {
        val listeners = syncListeners.computeIfAbsent(dataType) { mutableListOf() }
        listeners.add(listener)
    }
    
    /**
     * 移除同步监听器
     */
    fun removeSyncListener(dataType: String, listener: (String, JsonObject, SyncOperation) -> Unit) {
        val listeners = syncListeners[dataType] ?: return
        listeners.remove(listener)
    }
    
    /**
     * 通知同步监听器
     */
    private fun notifySyncListeners(dataType: String, key: String, value: JsonObject?, operation: SyncOperation) {
        val listeners = syncListeners[dataType] ?: return
        
        for (listener in listeners) {
            try {
                listener(key, value ?: JsonObject(), operation)
            } catch (e: Exception) {
                logger.error("调用同步监听器失败", e)
            }
        }
    }
    
    /**
     * 获取统计信息
     */
    fun getStats(): JsonObject {
        val result = JsonObject()
        
        for ((dataType, counter) in versionCounters) {
            val log = changeLogs[dataType] ?: continue
            
            result.put(dataType, JsonObject()
                .put("version", counter.get())
                .put("changeLogSize", log.size)
            )
        }
        
        return result
    }
}
