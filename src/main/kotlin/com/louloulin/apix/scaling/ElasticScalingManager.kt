package com.louloulin.apix.scaling

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 弹性伸缩管理器，负责管理系统的自动扩缩容。
 */
class ElasticScalingManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(ElasticScalingManager::class.java)
    
    // 弹性伸缩是否启用
    private val scalingEnabled = AtomicBoolean(false)
    
    // 自动扩缩容是否启用
    private val autoScalingEnabled = AtomicBoolean(false)
    
    // 预测式扩容是否启用
    private val predictiveScalingEnabled = AtomicBoolean(false)
    
    // 当前节点数量
    private val currentNodeCount = AtomicInteger(1)
    
    // 目标节点数量
    private val targetNodeCount = AtomicInteger(1)
    
    // 最小节点数量
    private val minNodeCount = AtomicInteger(1)
    
    // 最大节点数量
    private val maxNodeCount = AtomicInteger(10)
    
    // 扩容冷却时间（毫秒）
    private val scaleUpCooldown = AtomicLong(60000)
    
    // 缩容冷却时间（毫秒）
    private val scaleDownCooldown = AtomicLong(300000)
    
    // 上次扩容时间
    private val lastScaleUpTime = AtomicLong(0)
    
    // 上次缩容时间
    private val lastScaleDownTime = AtomicLong(0)
    
    // 负载阈值配置
    private val loadThresholds = ConcurrentHashMap<String, Double>()
    
    // 资源使用率历史数据
    private val resourceUsageHistory = ConcurrentHashMap<String, MutableList<ResourceUsage>>()
    
    // 负载检查定时器 ID
    private var loadCheckTimerId = -1L
    
    // 预测分析定时器 ID
    private var predictionTimerId = -1L
    
    // 负载检查间隔（毫秒）
    private val loadCheckInterval = AtomicLong(30000)
    
    // 预测分析间隔（毫秒）
    private val predictionInterval = AtomicLong(300000)
    
    // 历史数据保留时间（毫秒）
    private val historyRetentionTime = AtomicLong(3600000)
    
    // 扩容操作回调
    private var scaleUpCallback: ((Int) -> Future<Void>)? = null
    
    // 缩容操作回调
    private var scaleDownCallback: ((Int) -> Future<Void>)? = null
    
    /**
     * 初始化弹性伸缩管理器。
     * 
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化弹性伸缩管理器")
        
        // 获取配置
        val scalingConfig = config.getJsonObject("scaling", JsonObject())
        
        // 更新配置参数
        scalingEnabled.set(scalingConfig.getBoolean("enabled", false))
        autoScalingEnabled.set(scalingConfig.getBoolean("autoScalingEnabled", false))
        predictiveScalingEnabled.set(scalingConfig.getBoolean("predictiveScalingEnabled", false))
        
        minNodeCount.set(scalingConfig.getInteger("minNodeCount", 1))
        maxNodeCount.set(scalingConfig.getInteger("maxNodeCount", 10))
        
        scaleUpCooldown.set(scalingConfig.getLong("scaleUpCooldown", 60000))
        scaleDownCooldown.set(scalingConfig.getLong("scaleDownCooldown", 300000))
        
        loadCheckInterval.set(scalingConfig.getLong("loadCheckInterval", 30000))
        predictionInterval.set(scalingConfig.getLong("predictionInterval", 300000))
        historyRetentionTime.set(scalingConfig.getLong("historyRetentionTime", 3600000))
        
        // 初始化负载阈值
        val thresholds = scalingConfig.getJsonObject("thresholds", JsonObject())
        loadThresholds["cpuHigh"] = thresholds.getDouble("cpuHigh", 80.0)
        loadThresholds["cpuLow"] = thresholds.getDouble("cpuLow", 20.0)
        loadThresholds["memoryHigh"] = thresholds.getDouble("memoryHigh", 80.0)
        loadThresholds["memoryLow"] = thresholds.getDouble("memoryLow", 20.0)
        loadThresholds["requestsPerSecondHigh"] = thresholds.getDouble("requestsPerSecondHigh", 1000.0)
        loadThresholds["requestsPerSecondLow"] = thresholds.getDouble("requestsPerSecondLow", 100.0)
        
        // 如果弹性伸缩未启用，直接返回
        if (!scalingEnabled.get()) {
            logger.info("弹性伸缩未启用")
            return Future.succeededFuture()
        }
        
        // 启动负载检查
        if (autoScalingEnabled.get()) {
            startLoadCheck()
        }
        
        // 启动预测分析
        if (predictiveScalingEnabled.get()) {
            startPredictionAnalysis()
        }
        
        logger.info("弹性伸缩管理器初始化完成")
        return Future.succeededFuture()
    }
    
    /**
     * 启动负载检查。
     */
    private fun startLoadCheck() {
        // 停止之前的定时器
        if (loadCheckTimerId != -1L) {
            vertx.cancelTimer(loadCheckTimerId)
        }
        
        // 启动新的定时器
        loadCheckTimerId = vertx.setPeriodic(loadCheckInterval.get()) { _ ->
            checkSystemLoad()
        }
        
        logger.info("启动负载检查，间隔: ${loadCheckInterval.get()} 毫秒")
    }
    
    /**
     * 启动预测分析。
     */
    private fun startPredictionAnalysis() {
        // 停止之前的定时器
        if (predictionTimerId != -1L) {
            vertx.cancelTimer(predictionTimerId)
        }
        
        // 启动新的定时器
        predictionTimerId = vertx.setPeriodic(predictionInterval.get()) { _ ->
            predictFutureLoad()
        }
        
        logger.info("启动预测分析，间隔: ${predictionInterval.get()} 毫秒")
    }
    
    /**
     * 检查系统负载并决定是否需要扩缩容。
     */
    private fun checkSystemLoad() {
        // 收集当前系统资源使用情况
        collectResourceUsage()
            .onSuccess { resourceUsage ->
                // 记录资源使用情况
                recordResourceUsage(resourceUsage)
                
                // 检查是否需要扩容
                if (shouldScaleUp(resourceUsage)) {
                    scaleUp()
                }
                // 检查是否需要缩容
                else if (shouldScaleDown(resourceUsage)) {
                    scaleDown()
                }
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
                    
                    // 获取请求速率
                    getRequestRate()
                        .onSuccess { requestRate ->
                            // 创建资源使用情况对象
                            val resourceUsage = ResourceUsage(
                                timestamp = System.currentTimeMillis(),
                                cpuUsage = cpuUsage,
                                memoryUsage = memoryUsage,
                                requestsPerSecond = requestRate,
                                activeConnections = 0.0, // 这里需要从实际指标中获取
                                nodeCount = currentNodeCount.get()
                            )
                            
                            promise.complete(resourceUsage)
                        }
                        .onFailure { cause ->
                            promise.fail(cause)
                        }
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
     * 获取当前请求速率。
     * 
     * @return 包含请求速率的 Future
     */
    private fun getRequestRate(): Future<Double> {
        val promise = Promise.promise<Double>()
        
        // 获取请求指标
        vertx.eventBus().request<JsonObject>("apix.metrics.requests.get", JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    val metrics = response.getJsonObject("result", JsonObject())
                    val requestRate = metrics.getDouble("requestsPerSecond", 0.0)
                    promise.complete(requestRate)
                } else {
                    // 如果获取失败，返回默认值
                    promise.complete(0.0)
                }
            } else {
                // 如果获取失败，返回默认值
                promise.complete(0.0)
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
        val types = listOf("cpu", "memory", "requests")
        
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
     * 判断是否需要扩容。
     * 
     * @param resourceUsage 当前资源使用情况
     * @return 是否需要扩容
     */
    private fun shouldScaleUp(resourceUsage: ResourceUsage): Boolean {
        // 如果已经达到最大节点数量，不扩容
        if (currentNodeCount.get() >= maxNodeCount.get()) {
            return false
        }
        
        // 如果在扩容冷却期内，不扩容
        val now = System.currentTimeMillis()
        if (now - lastScaleUpTime.get() < scaleUpCooldown.get()) {
            return false
        }
        
        // 检查 CPU 使用率
        if (resourceUsage.cpuUsage > loadThresholds["cpuHigh"]!!) {
            logger.info("CPU 使用率 (${resourceUsage.cpuUsage}%) 超过阈值 (${loadThresholds["cpuHigh"]}%)，建议扩容")
            return true
        }
        
        // 检查内存使用率
        if (resourceUsage.memoryUsage > loadThresholds["memoryHigh"]!!) {
            logger.info("内存使用率 (${resourceUsage.memoryUsage}%) 超过阈值 (${loadThresholds["memoryHigh"]}%)，建议扩容")
            return true
        }
        
        // 检查请求速率
        if (resourceUsage.requestsPerSecond > loadThresholds["requestsPerSecondHigh"]!!) {
            logger.info("请求速率 (${resourceUsage.requestsPerSecond} 请求/秒) 超过阈值 (${loadThresholds["requestsPerSecondHigh"]} 请求/秒)，建议扩容")
            return true
        }
        
        return false
    }
    
    /**
     * 判断是否需要缩容。
     * 
     * @param resourceUsage 当前资源使用情况
     * @return 是否需要缩容
     */
    private fun shouldScaleDown(resourceUsage: ResourceUsage): Boolean {
        // 如果已经达到最小节点数量，不缩容
        if (currentNodeCount.get() <= minNodeCount.get()) {
            return false
        }
        
        // 如果在缩容冷却期内，不缩容
        val now = System.currentTimeMillis()
        if (now - lastScaleDownTime.get() < scaleDownCooldown.get()) {
            return false
        }
        
        // 检查所有指标是否都低于阈值
        val cpuLow = resourceUsage.cpuUsage < loadThresholds["cpuLow"]!!
        val memoryLow = resourceUsage.memoryUsage < loadThresholds["memoryLow"]!!
        val requestsLow = resourceUsage.requestsPerSecond < loadThresholds["requestsPerSecondLow"]!!
        
        // 只有当所有指标都低于阈值时才缩容
        if (cpuLow && memoryLow && requestsLow) {
            logger.info("所有资源使用率低于阈值，建议缩容")
            return true
        }
        
        return false
    }
    
    /**
     * 执行扩容操作。
     */
    private fun scaleUp() {
        // 计算新的节点数量
        val currentCount = currentNodeCount.get()
        val newCount = Math.min(currentCount + 1, maxNodeCount.get())
        
        if (newCount > currentCount) {
            logger.info("执行扩容操作，节点数量从 $currentCount 增加到 $newCount")
            
            // 更新目标节点数量
            targetNodeCount.set(newCount)
            
            // 记录扩容时间
            lastScaleUpTime.set(System.currentTimeMillis())
            
            // 调用扩容回调
            scaleUpCallback?.invoke(newCount)
                ?.onSuccess {
                    // 扩容成功，更新当前节点数量
                    currentNodeCount.set(newCount)
                    logger.info("扩容成功，当前节点数量: $newCount")
                }
                ?.onFailure { cause ->
                    logger.error("扩容失败", cause)
                }
        }
    }
    
    /**
     * 执行缩容操作。
     */
    private fun scaleDown() {
        // 计算新的节点数量
        val currentCount = currentNodeCount.get()
        val newCount = Math.max(currentCount - 1, minNodeCount.get())
        
        if (newCount < currentCount) {
            logger.info("执行缩容操作，节点数量从 $currentCount 减少到 $newCount")
            
            // 更新目标节点数量
            targetNodeCount.set(newCount)
            
            // 记录缩容时间
            lastScaleDownTime.set(System.currentTimeMillis())
            
            // 调用缩容回调
            scaleDownCallback?.invoke(newCount)
                ?.onSuccess {
                    // 缩容成功，更新当前节点数量
                    currentNodeCount.set(newCount)
                    logger.info("缩容成功，当前节点数量: $newCount")
                }
                ?.onFailure { cause ->
                    logger.error("缩容失败", cause)
                }
        }
    }
    
    /**
     * 预测未来负载并决定是否需要提前扩容。
     */
    private fun predictFutureLoad() {
        // 如果预测式扩容未启用，直接返回
        if (!predictiveScalingEnabled.get()) {
            return
        }
        
        // 获取 CPU 使用率历史数据
        val cpuHistory = resourceUsageHistory["cpu"] ?: return
        
        // 如果历史数据不足，无法进行预测
        if (cpuHistory.size < 10) {
            return
        }
        
        // 使用线性回归预测未来 CPU 使用率
        val prediction = predictLinearRegression(cpuHistory, 15 * 60 * 1000) // 预测 15 分钟后的负载
        
        // 如果预测的 CPU 使用率超过阈值，提前扩容
        if (prediction > loadThresholds["cpuHigh"]!!) {
            logger.info("预测 15 分钟后 CPU 使用率将达到 ${prediction}%，超过阈值 ${loadThresholds["cpuHigh"]}%，提前扩容")
            scaleUp()
        }
    }
    
    /**
     * 使用线性回归预测未来负载。
     * 
     * @param history 历史数据
     * @param futureTimeMs 未来时间（毫秒）
     * @return 预测的负载值
     */
    private fun predictLinearRegression(history: List<ResourceUsage>, futureTimeMs: Long): Double {
        // 如果历史数据不足，无法进行预测
        if (history.size < 2) {
            return 0.0
        }
        
        // 计算时间和 CPU 使用率的平均值
        val n = history.size
        val now = System.currentTimeMillis()
        var sumX = 0.0
        var sumY = 0.0
        
        for (usage in history) {
            // 将时间转换为相对时间（分钟）
            val relativeTime = (usage.timestamp - now) / 60000.0
            sumX += relativeTime
            sumY += usage.cpuUsage
        }
        
        val avgX = sumX / n
        val avgY = sumY / n
        
        // 计算线性回归参数
        var numerator = 0.0
        var denominator = 0.0
        
        for (usage in history) {
            val relativeTime = (usage.timestamp - now) / 60000.0
            numerator += (relativeTime - avgX) * (usage.cpuUsage - avgY)
            denominator += (relativeTime - avgX) * (relativeTime - avgX)
        }
        
        // 避免除以零
        if (denominator == 0.0) {
            return avgY
        }
        
        // 计算斜率和截距
        val slope = numerator / denominator
        val intercept = avgY - slope * avgX
        
        // 预测未来负载
        val futureTime = futureTimeMs / 60000.0
        return intercept + slope * futureTime
    }
    
    /**
     * 设置扩容操作回调。
     * 
     * @param callback 扩容操作回调函数
     */
    fun setScaleUpCallback(callback: (Int) -> Future<Void>) {
        scaleUpCallback = callback
    }
    
    /**
     * 设置缩容操作回调。
     * 
     * @param callback 缩容操作回调函数
     */
    fun setScaleDownCallback(callback: (Int) -> Future<Void>) {
        scaleDownCallback = callback
    }
    
    /**
     * 手动设置节点数量。
     * 
     * @param nodeCount 目标节点数量
     * @return 操作结果的 Future
     */
    fun setNodeCount(nodeCount: Int): Future<Void> {
        val promise = Promise.promise<Void>()
        
        // 检查节点数量是否在有效范围内
        val validNodeCount = Math.max(minNodeCount.get(), Math.min(nodeCount, maxNodeCount.get()))
        
        // 如果节点数量没有变化，直接返回
        if (validNodeCount == currentNodeCount.get()) {
            return Future.succeededFuture()
        }
        
        // 更新目标节点数量
        targetNodeCount.set(validNodeCount)
        
        // 根据目标节点数量决定扩容还是缩容
        if (validNodeCount > currentNodeCount.get()) {
            // 扩容
            scaleUpCallback?.invoke(validNodeCount)
                ?.onSuccess {
                    // 扩容成功，更新当前节点数量
                    currentNodeCount.set(validNodeCount)
                    logger.info("手动扩容成功，当前节点数量: $validNodeCount")
                    promise.complete()
                }
                ?.onFailure { cause ->
                    logger.error("手动扩容失败", cause)
                    promise.fail(cause)
                }
        } else {
            // 缩容
            scaleDownCallback?.invoke(validNodeCount)
                ?.onSuccess {
                    // 缩容成功，更新当前节点数量
                    currentNodeCount.set(validNodeCount)
                    logger.info("手动缩容成功，当前节点数量: $validNodeCount")
                    promise.complete()
                }
                ?.onFailure { cause ->
                    logger.error("手动缩容失败", cause)
                    promise.fail(cause)
                }
        }
        
        return promise.future()
    }
    
    /**
     * 获取弹性伸缩管理器状态。
     * 
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        val status = JsonObject()
            .put("enabled", scalingEnabled.get())
            .put("autoScalingEnabled", autoScalingEnabled.get())
            .put("predictiveScalingEnabled", predictiveScalingEnabled.get())
            .put("currentNodeCount", currentNodeCount.get())
            .put("targetNodeCount", targetNodeCount.get())
            .put("minNodeCount", minNodeCount.get())
            .put("maxNodeCount", maxNodeCount.get())
            .put("scaleUpCooldown", scaleUpCooldown.get())
            .put("scaleDownCooldown", scaleDownCooldown.get())
            .put("lastScaleUpTime", lastScaleUpTime.get())
            .put("lastScaleDownTime", lastScaleDownTime.get())
            .put("thresholds", JsonObject(loadThresholds.mapValues { it.value }))
        
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
     * 停止弹性伸缩管理器。
     * 
     * @return 停止完成的 Future
     */
    fun stop(): Future<Void> {
        logger.info("停止弹性伸缩管理器")
        
        // 停止负载检查定时器
        if (loadCheckTimerId != -1L) {
            vertx.cancelTimer(loadCheckTimerId)
            loadCheckTimerId = -1L
        }
        
        // 停止预测分析定时器
        if (predictionTimerId != -1L) {
            vertx.cancelTimer(predictionTimerId)
            predictionTimerId = -1L
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
        val requestsPerSecond: Double,
        val activeConnections: Double,
        val nodeCount: Int
    ) {
        /**
         * 转换为 JsonObject。
         */
        fun toJson(): JsonObject {
            return JsonObject()
                .put("timestamp", timestamp)
                .put("cpuUsage", cpuUsage)
                .put("memoryUsage", memoryUsage)
                .put("requestsPerSecond", requestsPerSecond)
                .put("activeConnections", activeConnections)
                .put("nodeCount", nodeCount)
        }
    }
    
    companion object {
        // 单例实例
        @Volatile
        private var instance: ElasticScalingManager? = null
        
        /**
         * 获取 ElasticScalingManager 的单例实例。
         * 
         * @param vertx Vert.x 实例
         * @return ElasticScalingManager 实例
         */
        fun getInstance(vertx: Vertx): ElasticScalingManager {
            return instance ?: synchronized(this) {
                instance ?: ElasticScalingManager(vertx).also { instance = it }
            }
        }
    }
}
