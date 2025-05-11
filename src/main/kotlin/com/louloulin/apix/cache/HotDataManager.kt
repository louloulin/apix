package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 热点数据管理器，用于识别和特殊处理热点数据。
 */
class HotDataManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(HotDataManager::class.java)
    
    // 是否启用热点数据识别
    private val hotDataEnabled = AtomicBoolean(true)
    
    // 热点数据识别算法
    private val hotDataAlgorithm = AtomicReference<String>("frequency")
    
    // 热点数据阈值
    private val hotDataThreshold = AtomicInteger(100)
    
    // 热点数据时间窗口（毫秒）
    private val hotDataTimeWindow = AtomicLong(60000) // 1 分钟
    
    // 热点数据最大数量
    private val hotDataMaxSize = AtomicInteger(1000)
    
    // 热点数据特殊处理策略
    private val hotDataStrategy = AtomicReference<String>("local")
    
    // 访问计数器
    private val accessCounters = ConcurrentHashMap<String, AccessCounter>()
    
    // 热点数据集合
    private val hotDataSet = ConcurrentHashMap<String, HotDataEntry>()
    
    // 热点数据优先队列
    private val hotDataQueue = PriorityBlockingQueue<HotDataEntry>(
        1000,
        Comparator<HotDataEntry> { o1, o2 -> o2.accessCount.get() - o1.accessCount.get() }
    )
    
    // 热点数据检测定时器 ID
    private var hotDataDetectionTimerId = -1L
    
    // 缓存命名空间
    private val cacheNamespace = AtomicReference<String>("apix")
    
    // 多级缓存管理器
    private lateinit var cacheManager: MultiLevelCacheManager
    
    /**
     * 初始化热点数据管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化热点数据管理器")
        
        // 获取缓存配置
        val cacheConfig = config.getJsonObject("cache", JsonObject())
        val hotDataConfig = cacheConfig.getJsonObject("hotData", JsonObject())
        
        // 更新配置
        hotDataEnabled.set(hotDataConfig.getBoolean("enabled", true))
        hotDataAlgorithm.set(hotDataConfig.getString("algorithm", "frequency"))
        hotDataThreshold.set(hotDataConfig.getInteger("threshold", 100))
        hotDataTimeWindow.set(hotDataConfig.getLong("timeWindow", 60000))
        hotDataMaxSize.set(hotDataConfig.getInteger("maxSize", 1000))
        hotDataStrategy.set(hotDataConfig.getString("strategy", "local"))
        cacheNamespace.set(cacheConfig.getString("cacheNamespace", "apix"))
        
        // 获取多级缓存管理器
        cacheManager = MultiLevelCacheManager.getInstance(vertx)
        
        // 如果热点数据识别未启用，直接返回
        if (!hotDataEnabled.get()) {
            logger.info("热点数据识别未启用")
            return Future.succeededFuture()
        }
        
        // 启动热点数据检测任务
        startHotDataDetectionTask()
        
        // 注册缓存访问事件处理器
        registerCacheAccessHandlers()
        
        logger.info("热点数据管理器初始化完成，算法: ${hotDataAlgorithm.get()}, 策略: ${hotDataStrategy.get()}")
        return Future.succeededFuture()
    }
    
    /**
     * 启动热点数据检测任务。
     */
    private fun startHotDataDetectionTask() {
        // 获取检测间隔
        val detectionInterval = 10000L // 10 秒
        
        // 停止之前的定时器
        if (hotDataDetectionTimerId != -1L) {
            vertx.cancelTimer(hotDataDetectionTimerId)
        }
        
        // 启动新的定时器
        hotDataDetectionTimerId = vertx.setPeriodic(detectionInterval) { _ ->
            detectHotData()
        }
        
        logger.info("热点数据检测任务已启动，间隔: $detectionInterval ms")
    }
    
    /**
     * 注册缓存访问事件处理器。
     */
    private fun registerCacheAccessHandlers() {
        // 监听缓存访问事件
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.cache.access") { message ->
            val key = message.body().getString("key")
            val namespace = message.body().getString("namespace", cacheNamespace.get())
            
            if (key != null) {
                // 记录缓存访问
                recordAccess(key, namespace)
            }
        }
    }
    
    /**
     * 检测热点数据。
     */
    private fun detectHotData() {
        val now = System.currentTimeMillis()
        val timeWindow = hotDataTimeWindow.get()
        val threshold = hotDataThreshold.get()
        
        // 清空热点数据集合和队列
        hotDataSet.clear()
        hotDataQueue.clear()
        
        // 检测热点数据
        for ((key, counter) in accessCounters) {
            // 获取时间窗口内的访问次数
            val count = counter.getAccessCount(now - timeWindow, now)
            
            // 如果访问次数超过阈值，认为是热点数据
            if (count >= threshold) {
                val entry = HotDataEntry(key, AtomicInteger(count))
                hotDataSet[key] = entry
                hotDataQueue.offer(entry)
                
                // 如果热点数据数量超过最大值，移除访问次数最少的
                if (hotDataSet.size > hotDataMaxSize.get()) {
                    val leastHot = hotDataQueue.poll()
                    if (leastHot != null) {
                        hotDataSet.remove(leastHot.key)
                    }
                }
            }
        }
        
        // 处理热点数据
        handleHotData()
        
        logger.debug("检测到 ${hotDataSet.size} 个热点数据")
    }
    
    /**
     * 处理热点数据。
     */
    private fun handleHotData() {
        // 根据策略处理热点数据
        when (hotDataStrategy.get()) {
            "local" -> handleHotDataLocal()
            "replicate" -> handleHotDataReplicate()
            "preload" -> handleHotDataPreload()
            else -> handleHotDataLocal()
        }
    }
    
    /**
     * 本地缓存策略处理热点数据。
     */
    private fun handleHotDataLocal() {
        // 将热点数据保存到本地缓存
        for ((key, entry) in hotDataSet) {
            val parts = key.split(":", limit = 2)
            if (parts.size == 2) {
                val namespace = parts[0]
                val cacheKey = parts[1]
                
                // 尝试从缓存中获取数据
                cacheManager.get(cacheKey, namespace)
                    .onSuccess { value ->
                        // 缓存命中，更新本地缓存
                        logger.debug("热点数据本地缓存: key=$key")
                    }
                    .onFailure { cause ->
                        // 缓存未命中，忽略
                    }
            }
        }
    }
    
    /**
     * 复制策略处理热点数据。
     */
    private fun handleHotDataReplicate() {
        // 将热点数据复制到所有节点
        for ((key, entry) in hotDataSet) {
            val parts = key.split(":", limit = 2)
            if (parts.size == 2) {
                val namespace = parts[0]
                val cacheKey = parts[1]
                
                // 发布热点数据复制事件
                vertx.eventBus().publish("${namespace}.cache.hotdata.replicate", JsonObject()
                    .put("key", cacheKey)
                    .put("namespace", namespace)
                    .put("accessCount", entry.accessCount.get())
                    .put("timestamp", System.currentTimeMillis())
                )
                
                logger.debug("热点数据复制: key=$key, accessCount=${entry.accessCount.get()}")
            }
        }
    }
    
    /**
     * 预加载策略处理热点数据。
     */
    private fun handleHotDataPreload() {
        // 预加载热点数据
        for ((key, entry) in hotDataSet) {
            val parts = key.split(":", limit = 2)
            if (parts.size == 2) {
                val namespace = parts[0]
                val cacheKey = parts[1]
                
                // 发布热点数据预加载事件
                vertx.eventBus().publish("${namespace}.cache.hotdata.preload", JsonObject()
                    .put("key", cacheKey)
                    .put("namespace", namespace)
                    .put("accessCount", entry.accessCount.get())
                    .put("timestamp", System.currentTimeMillis())
                )
                
                logger.debug("热点数据预加载: key=$key, accessCount=${entry.accessCount.get()}")
            }
        }
    }
    
    /**
     * 记录缓存访问。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     */
    fun recordAccess(key: String, namespace: String) {
        // 如果热点数据识别未启用，直接返回
        if (!hotDataEnabled.get()) {
            return
        }
        
        val counterKey = "$namespace:$key"
        val counter = accessCounters.computeIfAbsent(counterKey) { AccessCounter() }
        
        // 记录访问
        counter.recordAccess(System.currentTimeMillis())
    }
    
    /**
     * 检查是否是热点数据。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @return 是否是热点数据
     */
    fun isHotData(key: String, namespace: String = cacheNamespace.get()): Boolean {
        // 如果热点数据识别未启用，返回 false
        if (!hotDataEnabled.get()) {
            return false
        }
        
        val hotDataKey = "$namespace:$key"
        return hotDataSet.containsKey(hotDataKey)
    }
    
    /**
     * 获取热点数据访问次数。
     * 
     * @param key 缓存键
     * @param namespace 命名空间
     * @return 访问次数，如果不是热点数据则返回 0
     */
    fun getHotDataAccessCount(key: String, namespace: String = cacheNamespace.get()): Int {
        // 如果热点数据识别未启用，返回 0
        if (!hotDataEnabled.get()) {
            return 0
        }
        
        val hotDataKey = "$namespace:$key"
        return hotDataSet[hotDataKey]?.accessCount?.get() ?: 0
    }
    
    /**
     * 获取所有热点数据。
     * 
     * @return 热点数据列表
     */
    fun getAllHotData(): List<JsonObject> {
        return hotDataSet.map { (key, entry) ->
            val parts = key.split(":", limit = 2)
            val namespace = if (parts.size == 2) parts[0] else cacheNamespace.get()
            val cacheKey = if (parts.size == 2) parts[1] else key
            
            JsonObject()
                .put("key", cacheKey)
                .put("namespace", namespace)
                .put("accessCount", entry.accessCount.get())
        }
    }
    
    /**
     * 获取热点数据管理器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", hotDataEnabled.get())
            .put("algorithm", hotDataAlgorithm.get())
            .put("threshold", hotDataThreshold.get())
            .put("timeWindow", hotDataTimeWindow.get())
            .put("maxSize", hotDataMaxSize.get())
            .put("strategy", hotDataStrategy.get())
            .put("hotDataCount", hotDataSet.size)
        
        // 添加热点数据信息
        val hotDataJson = JsonObject()
        for ((key, entry) in hotDataSet) {
            hotDataJson.put(key, entry.accessCount.get())
        }
        status.put("hotData", hotDataJson)
        
        return status
    }
    
    /**
     * 访问计数器。
     */
    class AccessCounter {
        // 访问时间戳列表
        private val accessTimestamps = ConcurrentHashMap.newKeySet<Long>()
        
        /**
         * 记录访问。
         * 
         * @param timestamp 访问时间戳
         */
        fun recordAccess(timestamp: Long) {
            accessTimestamps.add(timestamp)
        }
        
        /**
         * 获取指定时间范围内的访问次数。
         * 
         * @param start 开始时间戳
         * @param end 结束时间戳
         * @return 访问次数
         */
        fun getAccessCount(start: Long, end: Long): Int {
            return accessTimestamps.count { it in start..end }
        }
        
        /**
         * 清理旧的访问记录。
         * 
         * @param before 清理此时间戳之前的记录
         */
        fun cleanupOldAccesses(before: Long) {
            accessTimestamps.removeIf { it < before }
        }
    }
    
    /**
     * 热点数据条目。
     */
    data class HotDataEntry(
        val key: String,
        val accessCount: AtomicInteger
    )
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: HotDataManager? = null
        
        /**
         * 获取 HotDataManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return HotDataManager 实例
         */
        fun getInstance(vertx: Vertx): HotDataManager {
            return instance ?: synchronized(this) {
                instance ?: HotDataManager(vertx).also { instance = it }
            }
        }
    }
}
