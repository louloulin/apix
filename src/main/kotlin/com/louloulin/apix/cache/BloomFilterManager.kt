package com.louloulin.apix.cache

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 分布式布隆过滤器管理器，用于高效判断一个元素是否在集合中。
 */
class BloomFilterManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(BloomFilterManager::class.java)
    
    // 是否启用布隆过滤器
    private val bloomFilterEnabled = AtomicBoolean(true)
    
    // 布隆过滤器预期元素数量
    private val expectedElements = AtomicInteger(1000000)
    
    // 布隆过滤器误判率
    private val falsePositiveRate = AtomicReference<Double>(0.01)
    
    // 布隆过滤器同步间隔（毫秒）
    private val syncInterval = AtomicLong(60000) // 1 分钟
    
    // 布隆过滤器同步定时器 ID
    private var syncTimerId = -1L
    
    // 缓存命名空间
    private val cacheNamespace = AtomicReference<String>("apix")
    
    // 布隆过滤器
    private val bloomFilters = ConcurrentHashMap<String, BloomFilter>()
    
    /**
     * 初始化布隆过滤器管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化布隆过滤器管理器")
        
        // 获取缓存配置
        val cacheConfig = config.getJsonObject("cache", JsonObject())
        val bloomFilterConfig = cacheConfig.getJsonObject("bloomFilter", JsonObject())
        
        // 更新配置
        bloomFilterEnabled.set(bloomFilterConfig.getBoolean("enabled", true))
        expectedElements.set(bloomFilterConfig.getInteger("expectedElements", 1000000))
        falsePositiveRate.set(bloomFilterConfig.getDouble("falsePositiveRate", 0.01))
        syncInterval.set(bloomFilterConfig.getLong("syncInterval", 60000))
        cacheNamespace.set(cacheConfig.getString("cacheNamespace", "apix"))
        
        // 如果布隆过滤器未启用，直接返回
        if (!bloomFilterEnabled.get()) {
            logger.info("布隆过滤器未启用")
            return Future.succeededFuture()
        }
        
        // 创建默认布隆过滤器
        createBloomFilter(cacheNamespace.get())
        
        // 启动布隆过滤器同步任务
        startBloomFilterSyncTask()
        
        // 注册布隆过滤器同步事件处理器
        registerBloomFilterSyncHandlers()
        
        logger.info("布隆过滤器管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 创建布隆过滤器。
     * 
     * @param namespace 命名空间
     * @return 布隆过滤器
     */
    private fun createBloomFilter(namespace: String): BloomFilter {
        val expectedElementsValue = expectedElements.get()
        val falsePositiveRateValue = falsePositiveRate.get()
        
        // 计算布隆过滤器参数
        val bitsPerElement = Math.ceil(-Math.log(falsePositiveRateValue) / Math.log(2.0)).toInt()
        val bitSetSize = expectedElementsValue * bitsPerElement
        val hashFunctionsCount = Math.ceil(bitSetSize / expectedElementsValue * Math.log(2.0)).toInt()
        
        // 创建布隆过滤器
        val bloomFilter = BloomFilter(bitSetSize, hashFunctionsCount)
        
        // 存储布隆过滤器
        bloomFilters[namespace] = bloomFilter
        
        logger.info("创建布隆过滤器: namespace=$namespace, bitSetSize=$bitSetSize, hashFunctionsCount=$hashFunctionsCount")
        
        return bloomFilter
    }
    
    /**
     * 启动布隆过滤器同步任务。
     */
    private fun startBloomFilterSyncTask() {
        // 获取同步间隔
        val syncIntervalValue = syncInterval.get()
        
        // 停止之前的定时器
        if (syncTimerId != -1L) {
            vertx.cancelTimer(syncTimerId)
        }
        
        // 启动新的定时器
        syncTimerId = vertx.setPeriodic(syncIntervalValue) { _ ->
            syncBloomFilters()
        }
        
        logger.info("布隆过滤器同步任务已启动，间隔: $syncIntervalValue ms")
    }
    
    /**
     * 注册布隆过滤器同步事件处理器。
     */
    private fun registerBloomFilterSyncHandlers() {
        // 监听布隆过滤器同步事件
        vertx.eventBus().consumer<JsonObject>("${cacheNamespace.get()}.bloomfilter.sync") { message ->
            val namespace = message.body().getString("namespace", cacheNamespace.get())
            val bitSetBase64 = message.body().getString("bitSet")
            
            if (bitSetBase64 != null) {
                // 解码 BitSet
                val bitSet = decodeBitSet(bitSetBase64)
                
                // 获取布隆过滤器
                val bloomFilter = bloomFilters[namespace] ?: createBloomFilter(namespace)
                
                // 合并 BitSet
                bloomFilter.mergeBitSet(bitSet)
                
                logger.debug("同步布隆过滤器: namespace=$namespace")
            }
        }
    }
    
    /**
     * 同步布隆过滤器。
     */
    private fun syncBloomFilters() {
        // 同步所有布隆过滤器
        for ((namespace, bloomFilter) in bloomFilters) {
            // 编码 BitSet
            val bitSetBase64 = encodeBitSet(bloomFilter.bitSet)
            
            // 发布布隆过滤器同步事件
            vertx.eventBus().publish("${namespace}.bloomfilter.sync", JsonObject()
                .put("namespace", namespace)
                .put("bitSet", bitSetBase64)
                .put("timestamp", System.currentTimeMillis())
            )
            
            logger.debug("发布布隆过滤器同步事件: namespace=$namespace")
        }
    }
    
    /**
     * 编码 BitSet 为 Base64 字符串。
     * 
     * @param bitSet BitSet
     * @return Base64 字符串
     */
    private fun encodeBitSet(bitSet: BitSet): String {
        val bytes = bitSet.toByteArray()
        return java.util.Base64.getEncoder().encodeToString(bytes)
    }
    
    /**
     * 解码 Base64 字符串为 BitSet。
     * 
     * @param base64 Base64 字符串
     * @return BitSet
     */
    private fun decodeBitSet(base64: String): BitSet {
        val bytes = java.util.Base64.getDecoder().decode(base64)
        return BitSet.valueOf(bytes)
    }
    
    /**
     * 添加元素到布隆过滤器。
     * 
     * @param key 元素键
     * @param namespace 命名空间
     * @return 操作结果的 Future
     */
    fun add(key: String, namespace: String = cacheNamespace.get()): Future<Void> {
        // 如果布隆过滤器未启用，直接返回
        if (!bloomFilterEnabled.get()) {
            return Future.succeededFuture()
        }
        
        // 获取布隆过滤器
        val bloomFilter = bloomFilters[namespace] ?: createBloomFilter(namespace)
        
        // 添加元素
        bloomFilter.add(key)
        
        return Future.succeededFuture()
    }
    
    /**
     * 检查元素是否可能在布隆过滤器中。
     * 
     * @param key 元素键
     * @param namespace 命名空间
     * @return 元素是否可能在布隆过滤器中
     */
    fun mightContain(key: String, namespace: String = cacheNamespace.get()): Boolean {
        // 如果布隆过滤器未启用，返回 true
        if (!bloomFilterEnabled.get()) {
            return true
        }
        
        // 获取布隆过滤器
        val bloomFilter = bloomFilters[namespace] ?: createBloomFilter(namespace)
        
        // 检查元素
        return bloomFilter.mightContain(key)
    }
    
    /**
     * 获取布隆过滤器管理器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", bloomFilterEnabled.get())
            .put("expectedElements", expectedElements.get())
            .put("falsePositiveRate", falsePositiveRate.get())
            .put("syncInterval", syncInterval.get())
        
        // 添加布隆过滤器信息
        val bloomFiltersJson = JsonObject()
        for ((namespace, bloomFilter) in bloomFilters) {
            bloomFiltersJson.put(namespace, JsonObject()
                .put("bitSetSize", bloomFilter.bitSetSize)
                .put("hashFunctionsCount", bloomFilter.hashFunctionsCount)
                .put("bitCount", bloomFilter.bitSet.cardinality())
            )
        }
        status.put("bloomFilters", bloomFiltersJson)
        
        return status
    }
    
    /**
     * 布隆过滤器实现。
     */
    class BloomFilter(
        val bitSetSize: Int,
        val hashFunctionsCount: Int
    ) {
        // 位集合
        val bitSet = BitSet(bitSetSize)
        
        /**
         * 添加元素到布隆过滤器。
         * 
         * @param key 元素键
         */
        fun add(key: String) {
            // 计算哈希值
            val hashes = createHashes(key, hashFunctionsCount)
            
            // 设置位
            for (hash in hashes) {
                bitSet.set(Math.abs(hash % bitSetSize))
            }
        }
        
        /**
         * 检查元素是否可能在布隆过滤器中。
         * 
         * @param key 元素键
         * @return 元素是否可能在布隆过滤器中
         */
        fun mightContain(key: String): Boolean {
            // 计算哈希值
            val hashes = createHashes(key, hashFunctionsCount)
            
            // 检查位
            for (hash in hashes) {
                if (!bitSet.get(Math.abs(hash % bitSetSize))) {
                    return false
                }
            }
            
            return true
        }
        
        /**
         * 合并另一个 BitSet。
         * 
         * @param other 另一个 BitSet
         */
        fun mergeBitSet(other: BitSet) {
            bitSet.or(other)
        }
        
        /**
         * 创建哈希值。
         * 
         * @param key 元素键
         * @param hashFunctionsCount 哈希函数数量
         * @return 哈希值数组
         */
        private fun createHashes(key: String, hashFunctionsCount: Int): IntArray {
            val result = IntArray(hashFunctionsCount)
            
            // 使用简单的哈希算法
            val hash1 = key.hashCode()
            val hash2 = hash1 * 31
            
            // 使用双重哈希法生成多个哈希值
            for (i in 0 until hashFunctionsCount) {
                result[i] = hash1 + i * hash2
            }
            
            return result
        }
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: BloomFilterManager? = null
        
        /**
         * 获取 BloomFilterManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return BloomFilterManager 实例
         */
        fun getInstance(vertx: Vertx): BloomFilterManager {
            return instance ?: synchronized(this) {
                instance ?: BloomFilterManager(vertx).also { instance = it }
            }
        }
    }
}
