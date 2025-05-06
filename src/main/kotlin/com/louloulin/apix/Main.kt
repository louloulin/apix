package com.louloulin.apix

import com.louloulin.apix.cluster.ClusterConfig
import com.louloulin.apix.cluster.ClusterManagerFactory
import com.louloulin.apix.core.ApixVerticle
import com.louloulin.apix.core.connection.ConnectionWarmer
import com.louloulin.apix.core.connection.ConnectionWarmerManager
import com.louloulin.apix.core.eventbus.BatchMessageProcessor
import com.louloulin.apix.core.eventbus.EventBusManager
import com.louloulin.apix.core.eventbus.JCToolsEventBus
import com.louloulin.apix.core.metrics.LatencyRecorder
import com.louloulin.apix.core.metrics.PerformanceMonitor
import com.louloulin.apix.core.resource.ResourceManager
import com.louloulin.apix.core.http.Http2Optimizer
import com.louloulin.apix.core.io.ZeroCopyHandler
import com.louloulin.apix.core.logging.LoggingManager
import com.louloulin.apix.core.metrics.MetricsManager
import com.louloulin.apix.core.monitoring.PerformanceMonitor
import com.louloulin.apix.core.monitoring.SystemMonitor
import com.louloulin.apix.core.network.NetworkOptimizer
import com.louloulin.apix.core.network.TcpTuner
import com.louloulin.apix.core.tracing.TracingManager

import com.louloulin.apix.core.verticle.AdminVerticle
import com.louloulin.apix.core.verticle.AuthVerticle
import com.louloulin.apix.core.verticle.CacheVerticle
import com.louloulin.apix.core.verticle.ClusterVerticle
import com.louloulin.apix.core.verticle.ConfigVerticle
import com.louloulin.apix.core.verticle.ConnectionWarmerVerticle
import com.louloulin.apix.core.verticle.DeploymentVerticle
import com.louloulin.apix.core.verticle.EventBusManagerVerticle
import com.louloulin.apix.core.verticle.HealthVerticle
import com.louloulin.apix.core.verticle.ModelRouterVerticle
import com.louloulin.apix.core.verticle.MonitorVerticle
import com.louloulin.apix.core.verticle.PluginVerticle
import com.louloulin.apix.core.verticle.PromptEnhancerVerticle
import com.louloulin.apix.core.verticle.ConcurrencyControlVerticle
import com.louloulin.apix.core.verticle.MemoryManagerVerticle
import com.louloulin.apix.core.verticle.RateLimitVerticle
import com.louloulin.apix.core.verticle.RequestQueueVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.core.CompositeFuture
import io.vertx.core.metrics.MetricsOptions
import io.vertx.core.DeploymentOptions
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.eventbus.EventBusOptions
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.louloulin.apix.Main")

fun main() {
    // 设置事件循环亲和性相关的系统属性
    System.setProperty("vertx.disableThreadChecks", "true")
    System.setProperty("vertx.threadChecks", "false")
    System.setProperty("vertx.preferNativeTransport", "true")

    logger.info("Starting APIX - AI Agent Gateway")

    // Load configuration
    val configPath = System.getProperty("apix.config.path", "config/apix.json")
    val configFile = java.nio.file.Paths.get(configPath)

    // Default configuration
    var config = io.vertx.core.json.JsonObject()

    // Try to load configuration from file
    if (java.nio.file.Files.exists(configFile)) {
        try {
            val configContent = java.nio.file.Files.readString(configFile)
            config = io.vertx.core.json.JsonObject(configContent)
            logger.info("Loaded configuration from: {}", configPath)
        } catch (e: Exception) {
            logger.error("Failed to load configuration from: {}", configPath, e)
        }
    } else {
        logger.warn("Configuration file not found at: {}, using default configuration", configPath)
    }

    // Parse cluster configuration
    val clusterConfig = ClusterConfig(config)

    // Load optimized Vert.x configuration for high concurrency
    val vertxConfigPath = System.getProperty("apix.vertx.config.path", "src/main/resources/vertx-high-concurrency.json")
    val vertxConfigFile = java.nio.file.Paths.get(vertxConfigPath)

    // Default Vert.x configuration for ultra-high concurrency (100K+ connections)
    val availableProcessors = Runtime.getRuntime().availableProcessors()
    var vertxOptions = VertxOptions()
        // Event loop pool - critical for handling many concurrent connections
        .setEventLoopPoolSize(availableProcessors * 4) // 4 event loop threads per core
        // Worker pool - for handling blocking operations
        .setWorkerPoolSize(availableProcessors * 16)   // 16 worker threads per core
        // Internal blocking pool - for internal Vert.x operations
        .setInternalBlockingPoolSize(availableProcessors * 8) // 8 internal blocking threads per core
        // Increase event loop execute time for high load scenarios
        .setMaxEventLoopExecuteTime(5000000000L)  // 5 seconds in nanoseconds
        // Enable native transport for better performance
        .setPreferNativeTransport(true)
        // Enable metrics
        .setMetricsOptions(MetricsManager.configureVertxOptions(VertxOptions()).metricsOptions)

    // Try to load Vert.x configuration from file
    if (java.nio.file.Files.exists(vertxConfigFile)) {
        try {
            val vertxConfigContent = java.nio.file.Files.readString(vertxConfigFile)
            val vertxConfig = io.vertx.core.json.JsonObject(vertxConfigContent)
            vertxOptions = VertxOptions(vertxConfig)
            logger.info("Loaded Vert.x configuration from: {}", vertxConfigPath)
        } catch (e: Exception) {
            logger.error("Failed to load Vert.x configuration from: {}", vertxConfigPath, e)
        }
    } else {
        logger.warn("Vert.x configuration file not found at: {}, using default configuration", vertxConfigPath)
    }

    // Add additional configuration
    vertxOptions.setMaxWorkerExecuteTime(120000000000L)   // 120 seconds in nanoseconds
    vertxOptions.setWarningExceptionTime(5000000000L)    // 5 seconds in nanoseconds
    vertxOptions.setBlockedThreadCheckInterval(2000)     // 2 seconds

    // Increase event bus options for better internal communication
    vertxOptions.setEventBusOptions(
        EventBusOptions()
            .setConnectTimeout(30000) // 30 seconds
            .setReconnectAttempts(10)
            .setReconnectInterval(2000) // 2 seconds
    )

    // Configure clustering if enabled
    if (clusterConfig.enabled) {
        logger.info("Configuring clustering with type: {}", clusterConfig.type)
        val clusterManager = ClusterManagerFactory.createClusterManager(clusterConfig)
        if (clusterManager != null) {
            vertxOptions.setClusterManager(clusterManager)
        } else {
            logger.error("Failed to create cluster manager, clustering will be disabled")
        }
    }

    // Create Vert.x instance (clustered or non-clustered)
    val vertxFuture = if (clusterConfig.enabled) {
        logger.info("Creating clustered Vert.x instance")
        Future.future<Vertx> { promise ->
            Vertx.clusteredVertx(vertxOptions)
                .onSuccess { clusteredVertx ->
                    logger.info("Successfully created clustered Vert.x instance")
                    promise.complete(clusteredVertx)
                }
                .onFailure { cause ->
                    logger.error("Failed to create clustered Vert.x instance", cause)
                    promise.fail(cause)
                }
        }
    } else {
        logger.info("Creating non-clustered Vert.x instance")
        Future.succeededFuture(Vertx.vertx(vertxOptions))
    }

    // Deploy verticles once Vert.x is created
    vertxFuture.onSuccess { vertx ->
        // 注册EventBus本地消息编解码器
        com.louloulin.apix.core.eventbus.EventBusCodecRegistry.registerLocalCodecs(vertx)

        // 初始化性能优化组件
        initializePerformanceComponents(vertx)

        // Deploy verticles in the correct order
        deployVerticles(vertx, availableProcessors)
            .onSuccess {
                logger.info("APIX Gateway successfully deployed all verticles")
            }
            .onFailure { cause ->
                logger.error("Failed to deploy APIX Gateway verticles", cause)
                vertx.close()
            }
    }
    .onFailure { cause ->
        logger.error("Failed to create Vert.x instance", cause)
    }

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutting down APIX Gateway...")
        // Note: We can't access vertx from here as it's a local variable in the main function
        // The Vert.x instance will be closed properly when the application exits
    })
}

/**
 * Deploy all verticles in the correct order
 */
private fun deployVerticles(vertx: Vertx, availableProcessors: Int): Future<Void> {
    // Standard deployment options
    val standardOptions = DeploymentOptions()
        .setInstances(1) // Single instance for service verticles

    // High concurrency deployment options for gateway verticle
    val gatewayOptions = DeploymentOptions()
        .setInstances(availableProcessors * 2) // Deploy two instances per core
        .setWorkerPoolSize(availableProcessors * 20) // 20 worker threads per core

    // Deploy ConfigVerticle first
    return deployVerticle(vertx, ConfigVerticle::class.java.name, standardOptions)
        .compose {
            // Then deploy MonitorVerticle
            deployVerticle(vertx, MonitorVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy ClusterVerticle if Vert.x is clustered
            if (vertx.isClustered()) {
                logger.info("Deploying ClusterVerticle for clustered mode")
                deployVerticle(vertx, ClusterVerticle::class.java.name, standardOptions)
            } else {
                logger.info("Skipping ClusterVerticle for non-clustered mode")
                Future.succeededFuture("cluster-skipped")
            }
        }
        .compose {
            // Then deploy AuthVerticle
            deployVerticle(vertx, AuthVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy CacheVerticle
            deployVerticle(vertx, CacheVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy ModelRouterVerticle
            deployVerticle(vertx, ModelRouterVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy PromptEnhancerVerticle
            deployVerticle(vertx, PromptEnhancerVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy ConcurrencyControlVerticle
            deployVerticle(vertx, ConcurrencyControlVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy RequestQueueVerticle
            deployVerticle(vertx, RequestQueueVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy RateLimitVerticle
            deployVerticle(vertx, RateLimitVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy MemoryManagerVerticle
            deployVerticle(vertx, MemoryManagerVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy PluginVerticle
            deployVerticle(vertx, PluginVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy AdminVerticle
            deployVerticle(vertx, AdminVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy HealthVerticle
            deployVerticle(vertx, HealthVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy DeploymentVerticle
            deployVerticle(vertx, DeploymentVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy EventBusManagerVerticle
            deployVerticle(vertx, EventBusManagerVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy ConnectionWarmerVerticle
            deployVerticle(vertx, ConnectionWarmerVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy PerformanceMonitorVerticle
            deployVerticle(vertx, PerformanceMonitorVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Then deploy ResourceManagerVerticle
            deployVerticle(vertx, ResourceManagerVerticle::class.java.name, standardOptions)
        }
        .compose {
            // Finally deploy the main ApixVerticle
            deployVerticle(vertx, ApixVerticle::class.java.name, gatewayOptions)
        }
        .mapEmpty()
}

/**
 * Deploy a single verticle
 */
private fun deployVerticle(vertx: Vertx, verticleName: String, options: DeploymentOptions): Future<String> {
    logger.info("Deploying verticle: {}", verticleName)
    return Future.future { promise ->
        vertx.deployVerticle(verticleName, options)
            .onSuccess { deploymentId ->
                logger.info("Successfully deployed {}: {}", verticleName, deploymentId)
                promise.complete(deploymentId)
            }
            .onFailure { cause ->
                logger.error("Failed to deploy {}", verticleName, cause)
                promise.fail(cause)
            }
    }
}

/**
 * 初始化性能优化组件
 */
private fun initializePerformanceComponents(vertx: Vertx) {
    try {
        // 初始化 HTTP/2 优化器
        val http2Optimizer = Http2Optimizer.getInstance(vertx)
        logger.info("HTTP/2 Optimizer initialized")

        // 初始化批量消息处理器
        val batchProcessor = BatchMessageProcessor.getInstance(vertx)
        logger.info("Batch Message Processor initialized")

        // 初始化JCToolsEventBus
        val jcToolsEventBus = JCToolsEventBus.getInstance(vertx)
        logger.info("JCTools EventBus initialized")

        // 初始化EventBus管理器
        val eventBusManager = EventBusManager.getInstance(vertx)
        logger.info("EventBus Manager initialized")

        // 初始化连接预热器
        val connectionWarmer = ConnectionWarmer.getInstance(vertx)
        logger.info("Connection Warmer initialized")

        // 初始化连接预热管理器
        val connectionWarmerManager = ConnectionWarmerManager.getInstance(vertx)
        logger.info("Connection Warmer Manager initialized")

        // 初始化延迟记录器
        val latencyRecorder = LatencyRecorder.getInstance()
        logger.info("Latency Recorder initialized")

        // 初始化性能监控器
        val performanceMonitor = PerformanceMonitor.getInstance(vertx)
        logger.info("Performance Monitor initialized")

        // 初始化资源管理器
        val resourceManager = ResourceManager.getInstance(vertx)
        logger.info("Resource Manager initialized")

        // 初始化零拷贝处理器
        val zeroCopyHandler = ZeroCopyHandler.getInstance(vertx)
        logger.info("Zero Copy Handler initialized")

        // 初始化日志管理器
        val loggingManager = LoggingManager.getInstance(vertx)
        logger.info("Logging Manager initialized")

        // 初始化指标管理器
        val metricsManager = MetricsManager.getInstance(vertx)
        logger.info("Metrics Manager initialized")

        // 初始化追踪管理器
        val tracingManager = TracingManager.getInstance(vertx)
        logger.info("Tracing Manager initialized")

        // 初始化网络优化器
        val networkOptimizer = NetworkOptimizer.getInstance(vertx)
        logger.info("Network Optimizer initialized")

        // 初始化TCP调优器
        val tcpTuner = TcpTuner.getInstance(vertx)
        logger.info("TCP Tuner initialized")

        // 初始化系统监控器
        val systemMonitor = SystemMonitor.getInstance(vertx)
        logger.info("System Monitor initialized")

        // 初始化性能监控器
        val performanceMonitor = PerformanceMonitor.getInstance(vertx)
        logger.info("Performance Monitor initialized")

        // 加载 HTTP/2 设置
        val http2ConfigPath = System.getProperty("apix.http2.config.path", "src/main/resources/vertx-high-performance.json")
        http2Optimizer.loadHttp2SettingsFromConfig(http2ConfigPath)
            .onSuccess { settings ->
                logger.info("HTTP/2 settings loaded from: {}", http2ConfigPath)
            }
            .onFailure { cause ->
                logger.warn("Failed to load HTTP/2 settings from: {}, using defaults", http2ConfigPath)
            }

        // 配置日志优化
        loggingManager.optimizeLoggingForLoad(0.7)
        logger.info("Logging optimized for high load")

        // 配置追踪
        tracingManager.configureTracing(true, 0.1) // 启用追踪，采样率10%
        logger.info("Tracing configured with 10% sampling rate")

        // 设置慢请求阈值
        performanceMonitor.setSlowRequestThreshold(1000) // 1秒
        logger.info("Slow request threshold set to 1000ms")
    } catch (e: Exception) {
        logger.error("Failed to initialize performance components", e)
    }
}
