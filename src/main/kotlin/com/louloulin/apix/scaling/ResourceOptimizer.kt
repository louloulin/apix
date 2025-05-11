package com.louloulin.apix.scaling

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * 资源优化器，负责监控和优化系统资源利用率。
 */
class ResourceOptimizer(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ResourceOptimizer::class.java)
    
    // 资源优化是否启用
    private val optimizationEnabled = AtomicBoolean(false)
    
    // 资源监控定时器 ID
    private var monitorTimerId = -1L
    
    // 资源优化定时器 ID
    private var optimizeTimerId = -1L
    
    // 资源监控间隔（毫秒）
    private val monitorInterval = AtomicLong(30000)
    
    // 资源优化间隔（毫秒）
    private val optimizeInterval = AtomicLong(300000)
    
    // 资源使用率阈值
    private val resourceThresholds = ConcurrentHashMap<String, Double>()
    
    // 资源使用率历史数据
    private val resourceUsageHistory = ConcurrentHashMap<String, MutableList<ResourceUsage>>()
    
    // 历史数据保留时间（毫秒）
    private val historyRetentionTime = AtomicLong(3600000)
    
    // 优化建议回调
    private var optimizationSuggestionCallback: ((JsonObject) -> Unit)? = null
    
    /**
     * 初始化资源优化器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化资源优化器")
        
        // 获取配置
        val optimizationConfig = config.getJsonObject("resourceOptimization", JsonObject())
        
        // 更新配置参数
        optimizationEnabled.set(optimizationConfig.getBoolean("enabled", false))
        
        monitorInterval.set(optimizationConfig.getLong("monitorInterval", 30000))
        optimizeInterval.set(optimizationConfig.getLong("optimizeInterval", 300000))
        historyRetentionTime.set(optimizationConfig.getLong("historyRetentionTime", 3600000))
        
        // 初始化资源阈值
        val thresholds = optimizationConfig.getJsonObject("thresholds", JsonObject())
        resourceThresholds["cpuHigh"] = thresholds.getDouble("cpuHigh", 80.0)
        resourceThresholds["memoryHigh"] = thresholds.getDouble("memoryHigh", 80.0)
        resourceThresholds["diskHigh"] = thresholds.getDouble("diskHigh", 80.0)
        resourceThresholds["networkHigh"] = thresholds.getDouble("networkHigh", 80.0)
        
        // 如果资源优化未启用，直接返回
        if (!optimizationEnabled.get()) {
            logger.info("资源优化未启用")
            return Future.succeededFuture()
        }
        
        // 启动资源监控
        startResourceMonitoring()
        
        // 启动资源优化
        startResourceOptimization()
        
        logger.info("资源优化器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 启动资源监控。
     */
    private fun startResourceMonitoring() {
        // 停止之前的定时器
        if (monitorTimerId != -1L) {
            vertx.cancelTimer(monitorTimerId)
        }
        
        // 启动新的定时器
        monitorTimerId = vertx.setPeriodic(monitorInterval.get()) { _ ->
            monitorResources()
        }
        
        logger.info("启动资源监控，间隔: ${monitorInterval.get()} 毫秒")
    }
    
    /**
     * 启动资源优化。
     */
    private fun startResourceOptimization() {
        // 停止之前的定时器
        if (optimizeTimerId != -1L) {
            vertx.cancelTimer(optimizeTimerId)
        }
        
        // 启动新的定时器
        optimizeTimerId = vertx.setPeriodic(optimizeInterval.get()) { _ ->
            optimizeResources()
        }
        
        logger.info("启动资源优化，间隔: ${optimizeInterval.get()} 毫秒")
    }
    
    /**
     * 监控系统资源使用情况。
     */
    private fun monitorResources() {
        // 收集当前系统资源使用情况
        collectResourceUsage()
            .onSuccess { resourceUsage ->
                // 记录资源使用情况
                recordResourceUsage(resourceUsage)
                
                // 检查资源使用情况是否超过阈值
                checkResourceThresholds(resourceUsage)
            }
            .onFailure { cause ->
                logger.error("收集资源使用情况失败", cause)
            }
    }
    
    /**
     * 收集当前系统资源使用情况。
     * 
     * @return 包含资源使用情况的 Future
     */
    private fun collectResourceUsage(): Future<ResourceUsage> {
        val promise = Promise.promise<ResourceUsage>()
        
        // 获取系统指标
        vertx.eventBus().request<JsonObject>("apix.metrics.system.get", JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    val metrics = response.getJsonObject("result", JsonObject())
                    
                    // 解析 CPU 使用率
                    val cpuMetrics = metrics.getJsonObject("cpu", JsonObject())
                    val cpuUsage = cpuMetrics.getDouble("processCpuLoad", 0.0) * 100
                    
                    // 解析内存使用率
                    val memoryMetrics = metrics.getJsonObject("memory", JsonObject())
                    val heapUsed = memoryMetrics.getLong("heapUsed", 0L)
                    val heapMax = memoryMetrics.getLong("heapMax", 1L)
                    val memoryUsage = (heapUsed.toDouble() / heapMax) * 100
                    
                    // 解析磁盘使用率
                    val diskMetrics = metrics.getJsonObject("disk", JsonObject())
                    val diskUsage = diskMetrics?.getDouble("usagePercent", 0.0) ?: 0.0
                    
                    // 解析网络使用率
                    val networkMetrics = metrics.getJsonObject("network", JsonObject())
                    val networkUsage = networkMetrics?.getDouble("usagePercent", 0.0) ?: 0.0
                    
                    // 创建资源使用情况对象
                    val resourceUsage = ResourceUsage(
                        timestamp = System.currentTimeMillis(),
                        cpuUsage = cpuUsage,
                        memoryUsage = memoryUsage,
                        diskUsage = diskUsage,
                        networkUsage = networkUsage,
                        gcPauseTime = metrics.getJsonObject("jvm")?.getDouble("gcPauseTime", 0.0) ?: 0.0,
                        threadCount = metrics.getJsonObject("threads")?.getInteger("threadCount", 0) ?: 0
                    )
                    
                    promise.complete(resourceUsage)
                } else {
                    promise.fail("获取系统指标失败: ${response.getString("message", "未知错误")}")
                }
            } else {
                promise.fail(ar.cause())
            }
        }
        
        return promise.future()
    }
    
    /**
     * 记录资源使用情况。
     * 
     * @param resourceUsage 资源使用情况
     */
    private fun recordResourceUsage(resourceUsage: ResourceUsage) {
        // 获取当前时间
        val now = System.currentTimeMillis()
        
        // 为每种资源类型记录使用情况
        val types = listOf("cpu", "memory", "disk", "network")
        
        for (type in types) {
            val history = resourceUsageHistory.computeIfAbsent(type) { mutableListOf() }
            
            // 添加新的使用情况
            history.add(resourceUsage)
            
            // 清理过期的历史数据
            val cutoffTime = now - historyRetentionTime.get()
            history.removeIf { it.timestamp < cutoffTime }
        }
    }
    
    /**
     * 检查资源使用情况是否超过阈值。
     * 
     * @param resourceUsage 当前资源使用情况
     */
    private fun checkResourceThresholds(resourceUsage: ResourceUsage) {
        // 检查 CPU 使用率
        if (resourceUsage.cpuUsage > resourceThresholds["cpuHigh"]!!) {
            logger.warn("CPU 使用率 (${resourceUsage.cpuUsage}%) 超过阈值 (${resourceThresholds["cpuHigh"]}%)")
        }
        
        // 检查内存使用率
        if (resourceUsage.memoryUsage > resourceThresholds["memoryHigh"]!!) {
            logger.warn("内存使用率 (${resourceUsage.memoryUsage}%) 超过阈值 (${resourceThresholds["memoryHigh"]}%)")
        }
        
        // 检查磁盘使用率
        if (resourceUsage.diskUsage > resourceThresholds["diskHigh"]!!) {
            logger.warn("磁盘使用率 (${resourceUsage.diskUsage}%) 超过阈值 (${resourceThresholds["diskHigh"]}%)")
        }
        
        // 检查网络使用率
        if (resourceUsage.networkUsage > resourceThresholds["networkHigh"]!!) {
            logger.warn("网络使用率 (${resourceUsage.networkUsage}%) 超过阈值 (${resourceThresholds["networkHigh"]}%)")
        }
    }
    
    /**
     * 优化系统资源使用。
     */
    private fun optimizeResources() {
        // 分析资源使用情况
        analyzeResourceUsage()
            .onSuccess { suggestions ->
                // 如果有优化建议，通知回调
                if (suggestions.size() > 0) {
                    logger.info("生成资源优化建议: ${suggestions.encode()}")
                    optimizationSuggestionCallback?.invoke(suggestions)
                }
            }
            .onFailure { cause ->
                logger.error("分析资源使用情况失败", cause)
            }
    }
    
    /**
     * 分析资源使用情况并生成优化建议。
     * 
     * @return 包含优化建议的 Future
     */
    private fun analyzeResourceUsage(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()
        
        // 创建优化建议对象
        val suggestions = JsonObject()
        val suggestionsList = JsonArray()
        
        // 分析 CPU 使用情况
        analyzeCpuUsage()
            .onSuccess { cpuSuggestions ->
                if (cpuSuggestions.size() > 0) {
                    suggestionsList.addAll(cpuSuggestions)
                }
                
                // 分析内存使用情况
                analyzeMemoryUsage()
                    .onSuccess { memorySuggestions ->
                        if (memorySuggestions.size() > 0) {
                            suggestionsList.addAll(memorySuggestions)
                        }
                        
                        // 分析 GC 情况
                        analyzeGcUsage()
                            .onSuccess { gcSuggestions ->
                                if (gcSuggestions.size() > 0) {
                                    suggestionsList.addAll(gcSuggestions)
                                }
                                
                                // 分析线程使用情况
                                analyzeThreadUsage()
                                    .onSuccess { threadSuggestions ->
                                        if (threadSuggestions.size() > 0) {
                                            suggestionsList.addAll(threadSuggestions)
                                        }
                                        
                                        // 完成所有分析
                                        suggestions.put("suggestions", suggestionsList)
                                        promise.complete(suggestions)
                                    }
                                    .onFailure { cause ->
                                        promise.fail(cause)
                                    }
                            }
                            .onFailure { cause ->
                                promise.fail(cause)
                            }
                    }
                    .onFailure { cause ->
                        promise.fail(cause)
                    }
            }
            .onFailure { cause ->
                promise.fail(cause)
            }
        
        return promise.future()
    }
    
    /**
     * 分析 CPU 使用情况并生成优化建议。
     * 
     * @return 包含 CPU 优化建议的 Future
     */
    private fun analyzeCpuUsage(): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()
        
        // 获取 CPU 使用率历史数据
        val cpuHistory = resourceUsageHistory["cpu"] ?: emptyList()
        
        // 如果历史数据不足，无法进行分析
        if (cpuHistory.size < 10) {
            promise.complete(JsonArray())
            return promise.future()
        }
        
        // 计算 CPU 使用率平均值和最大值
        var sumCpu = 0.0
        var maxCpu = 0.0
        
        for (usage in cpuHistory) {
            sumCpu += usage.cpuUsage
            maxCpu = Math.max(maxCpu, usage.cpuUsage)
        }
        
        val avgCpu = sumCpu / cpuHistory.size
        
        // 创建 CPU 优化建议
        val suggestions = JsonArray()
        
        // 如果平均 CPU 使用率过高，建议优化 CPU 密集型操作
        if (avgCpu > 70.0) {
            suggestions.add(JsonObject()
                .put("type", "cpu")
                .put("severity", "high")
                .put("message", "CPU 平均使用率过高 (${String.format("%.2f", avgCpu)}%)，建议优化 CPU 密集型操作")
                .put("suggestion", "考虑使用异步处理、增加缓存或优化算法")
            )
        }
        
        // 如果最大 CPU 使用率过高，建议检查 CPU 峰值
        if (maxCpu > 90.0) {
            suggestions.add(JsonObject()
                .put("type", "cpu")
                .put("severity", "high")
                .put("message", "CPU 峰值使用率过高 (${String.format("%.2f", maxCpu)}%)，建议检查 CPU 峰值")
                .put("suggestion", "检查是否有定期执行的 CPU 密集型任务，考虑将其分散执行")
            )
        }
        
        promise.complete(suggestions)
        return promise.future()
    }
    
    /**
     * 分析内存使用情况并生成优化建议。
     * 
     * @return 包含内存优化建议的 Future
     */
    private fun analyzeMemoryUsage(): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()
        
        // 获取内存使用率历史数据
        val memoryHistory = resourceUsageHistory["memory"] ?: emptyList()
        
        // 如果历史数据不足，无法进行分析
        if (memoryHistory.size < 10) {
            promise.complete(JsonArray())
            return promise.future()
        }
        
        // 计算内存使用率平均值和最大值
        var sumMemory = 0.0
        var maxMemory = 0.0
        
        for (usage in memoryHistory) {
            sumMemory += usage.memoryUsage
            maxMemory = Math.max(maxMemory, usage.memoryUsage)
        }
        
        val avgMemory = sumMemory / memoryHistory.size
        
        // 创建内存优化建议
        val suggestions = JsonArray()
        
        // 如果平均内存使用率过高，建议优化内存使用
        if (avgMemory > 70.0) {
            suggestions.add(JsonObject()
                .put("type", "memory")
                .put("severity", "high")
                .put("message", "内存平均使用率过高 (${String.format("%.2f", avgMemory)}%)，建议优化内存使用")
                .put("suggestion", "检查内存泄漏、优化对象创建和销毁、增加 JVM 堆内存")
            )
        }
        
        // 如果最大内存使用率过高，建议检查内存峰值
        if (maxMemory > 90.0) {
            suggestions.add(JsonObject()
                .put("type", "memory")
                .put("severity", "high")
                .put("message", "内存峰值使用率过高 (${String.format("%.2f", maxMemory)}%)，建议检查内存峰值")
                .put("suggestion", "检查是否有大量临时对象创建，考虑使用对象池或优化算法")
            )
        }
        
        promise.complete(suggestions)
        return promise.future()
    }
    
    /**
     * 分析 GC 情况并生成优化建议。
     * 
     * @return 包含 GC 优化建议的 Future
     */
    private fun analyzeGcUsage(): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()
        
        // 获取 CPU 使用率历史数据（包含 GC 信息）
        val cpuHistory = resourceUsageHistory["cpu"] ?: emptyList()
        
        // 如果历史数据不足，无法进行分析
        if (cpuHistory.size < 10) {
            promise.complete(JsonArray())
            return promise.future()
        }
        
        // 计算 GC 暂停时间平均值和最大值
        var sumGcPause = 0.0
        var maxGcPause = 0.0
        
        for (usage in cpuHistory) {
            sumGcPause += usage.gcPauseTime
            maxGcPause = Math.max(maxGcPause, usage.gcPauseTime)
        }
        
        val avgGcPause = sumGcPause / cpuHistory.size
        
        // 创建 GC 优化建议
        val suggestions = JsonArray()
        
        // 如果平均 GC 暂停时间过长，建议优化 GC
        if (avgGcPause > 200.0) {
            suggestions.add(JsonObject()
                .put("type", "gc")
                .put("severity", "medium")
                .put("message", "GC 平均暂停时间过长 (${String.format("%.2f", avgGcPause)} ms)，建议优化 GC")
                .put("suggestion", "考虑使用 G1GC 或 ZGC，调整 GC 参数，减少对象创建")
            )
        }
        
        // 如果最大 GC 暂停时间过长，建议检查 GC 峰值
        if (maxGcPause > 500.0) {
            suggestions.add(JsonObject()
                .put("type", "gc")
                .put("severity", "high")
                .put("message", "GC 峰值暂停时间过长 (${String.format("%.2f", maxGcPause)} ms)，建议检查 GC 峰值")
                .put("suggestion", "检查是否有大量对象同时进入老年代，考虑增加年轻代大小或优化对象生命周期")
            )
        }
        
        promise.complete(suggestions)
        return promise.future()
    }
    
    /**
     * 分析线程使用情况并生成优化建议。
     * 
     * @return 包含线程优化建议的 Future
     */
    private fun analyzeThreadUsage(): Future<JsonArray> {
        val promise = Promise.promise<JsonArray>()
        
        // 获取 CPU 使用率历史数据（包含线程信息）
        val cpuHistory = resourceUsageHistory["cpu"] ?: emptyList()
        
        // 如果历史数据不足，无法进行分析
        if (cpuHistory.size < 10) {
            promise.complete(JsonArray())
            return promise.future()
        }
        
        // 计算线程数平均值和最大值
        var sumThreads = 0
        var maxThreads = 0
        
        for (usage in cpuHistory) {
            sumThreads += usage.threadCount
            maxThreads = Math.max(maxThreads, usage.threadCount)
        }
        
        val avgThreads = sumThreads / cpuHistory.size
        
        // 创建线程优化建议
        val suggestions = JsonArray()
        
        // 如果平均线程数过多，建议优化线程使用
        if (avgThreads > 200) {
            suggestions.add(JsonObject()
                .put("type", "thread")
                .put("severity", "medium")
                .put("message", "线程平均数量过多 ($avgThreads)，建议优化线程使用")
                .put("suggestion", "考虑使用线程池，减少线程创建和销毁，检查是否有线程泄漏")
            )
        }
        
        // 如果最大线程数过多，建议检查线程峰值
        if (maxThreads > 300) {
            suggestions.add(JsonObject()
                .put("type", "thread")
                .put("severity", "high")
                .put("message", "线程峰值数量过多 ($maxThreads)，建议检查线程峰值")
                .put("suggestion", "检查是否有短时间内大量创建线程的情况，考虑使用异步处理或事件驱动模型")
            )
        }
        
        promise.complete(suggestions)
        return promise.future()
    }
    
    /**
     * 设置优化建议回调。
     * 
     * @param callback 优化建议回调函数
     */
    fun setOptimizationSuggestionCallback(callback: (JsonObject) -> Unit) {
        optimizationSuggestionCallback = callback
    }
    
    /**
     * 获取资源优化器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", optimizationEnabled.get())
            .put("monitorInterval", monitorInterval.get())
            .put("optimizeInterval", optimizeInterval.get())
            .put("thresholds", JsonObject(resourceThresholds.mapValues { it.value }))
        
        // 添加资源使用率历史数据
        val historyJson = JsonObject()
        for ((type, history) in resourceUsageHistory) {
            val historyArray = JsonArray()
            for (usage in history) {
                historyArray.add(usage.toJson())
            }
            historyJson.put(type, historyArray)
        }
        status.put("resourceUsageHistory", historyJson)
        
        return status
    }
    
    /**
     * 停止资源优化器。
     * 
     * @return 停止完成的 Future
     */
    fun stop(): Future<Void> {
        logger.info("停止资源优化器")
        
        // 停止资源监控定时器
        if (monitorTimerId != -1L) {
            vertx.cancelTimer(monitorTimerId)
            monitorTimerId = -1L
        }
        
        // 停止资源优化定时器
        if (optimizeTimerId != -1L) {
            vertx.cancelTimer(optimizeTimerId)
            optimizeTimerId = -1L
        }
        
        return Future.succeededFuture()
    }
    
    /**
     * 资源使用情况数据类。
     */
    data class ResourceUsage(
        val timestamp: Long,
        val cpuUsage: Double,
        val memoryUsage: Double,
        val diskUsage: Double,
        val networkUsage: Double,
        val gcPauseTime: Double,
        val threadCount: Int
    ) {
        /**
         * 转换为 JsonObject。
         */
        fun toJson(): JsonObject {
            return JsonObject()
                .put("timestamp", timestamp)
                .put("cpuUsage", cpuUsage)
                .put("memoryUsage", memoryUsage)
                .put("diskUsage", diskUsage)
                .put("networkUsage", networkUsage)
                .put("gcPauseTime", gcPauseTime)
                .put("threadCount", threadCount)
        }
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: ResourceOptimizer? = null
        
        /**
         * 获取 ResourceOptimizer 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return ResourceOptimizer 实例
         */
        fun getInstance(vertx: Vertx): ResourceOptimizer {
            return instance ?: synchronized(this) {
                instance ?: ResourceOptimizer(vertx).also { instance = it }
            }
        }
    }
}
