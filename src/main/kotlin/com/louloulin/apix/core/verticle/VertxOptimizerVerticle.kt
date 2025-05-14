package com.louloulin.apix.core.verticle

import com.louloulin.apix.core.config.VertxConfigOptimizer
import com.louloulin.apix.core.deploy.AdaptiveDeploymentManager
import com.louloulin.apix.core.eventbus.EnhancedEventBus
import com.louloulin.apix.core.eventbus.EventBusManager
import io.vertx.core.Promise
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Vert.x优化器Verticle，用于优化Vert.x配置、EventBus性能和Verticle部署策略
 */
class VertxOptimizerVerticle : BaseVerticle() {

    // Vert.x配置优化器
    private lateinit var vertxConfigOptimizer: VertxConfigOptimizer

    // 增强版EventBus
    private lateinit var enhancedEventBus: EnhancedEventBus

    // 自适应部署管理器
    private lateinit var adaptiveDeploymentManager: AdaptiveDeploymentManager

    // EventBus管理器
    private lateinit var eventBusManager: EventBusManager

    // 事件总线地址
    object EventBusAddresses {
        // Vert.x配置优化器
        const val VERTX_CONFIG_GET = "apix.vertx.config.get"
        const val VERTX_CONFIG_SET = "apix.vertx.config.set"
        const val VERTX_CONFIG_PROFILE_GET = "apix.vertx.config.profile.get"
        const val VERTX_CONFIG_PROFILE_SET = "apix.vertx.config.profile.set"
        const val VERTX_CONFIG_AUTO_SELECT = "apix.vertx.config.auto.select"

        // 增强版EventBus
        const val EVENTBUS_ENHANCED_CONFIG_GET = "apix.eventbus.enhanced.config.get"
        const val EVENTBUS_ENHANCED_CONFIG_SET = "apix.eventbus.enhanced.config.set"
        const val EVENTBUS_ENHANCED_STATS_GET = "apix.eventbus.enhanced.stats.get"
        const val EVENTBUS_ENHANCED_CIRCUIT_BREAKER_RESET = "apix.eventbus.enhanced.circuit.breaker.reset"
        const val EVENTBUS_ENHANCED_CIRCUIT_BREAKER_RESET_ALL = "apix.eventbus.enhanced.circuit.breaker.reset.all"

        // 自适应部署管理器
        const val DEPLOYMENT_ADAPTIVE_CONFIG_GET = "apix.deployment.adaptive.config.get"
        const val DEPLOYMENT_ADAPTIVE_CONFIG_SET = "apix.deployment.adaptive.config.set"
        const val DEPLOYMENT_ADAPTIVE_STATS_GET = "apix.deployment.adaptive.stats.get"
        const val DEPLOYMENT_ADAPTIVE_DEPLOY = "apix.deployment.adaptive.deploy"
        const val DEPLOYMENT_ADAPTIVE_UNDEPLOY = "apix.deployment.adaptive.undeploy"
        const val DEPLOYMENT_ADAPTIVE_INFO_GET = "apix.deployment.adaptive.info.get"
        const val DEPLOYMENT_ADAPTIVE_INFO_GET_ALL = "apix.deployment.adaptive.info.get.all"
    }

    override fun registerEventBusHandlers() {
        // Vert.x配置优化器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.VERTX_CONFIG_GET, this::handleGetVertxConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.VERTX_CONFIG_SET, this::handleSetVertxConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.VERTX_CONFIG_PROFILE_GET, this::handleGetVertxConfigProfile)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.VERTX_CONFIG_PROFILE_SET, this::handleSetVertxConfigProfile)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.VERTX_CONFIG_AUTO_SELECT, this::handleAutoSelectVertxConfigProfile)

        // 增强版EventBus
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_ENHANCED_CONFIG_GET, this::handleGetEnhancedEventBusConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_ENHANCED_CONFIG_SET, this::handleSetEnhancedEventBusConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_ENHANCED_STATS_GET, this::handleGetEnhancedEventBusStats)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_ENHANCED_CIRCUIT_BREAKER_RESET, this::handleResetEnhancedEventBusCircuitBreaker)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.EVENTBUS_ENHANCED_CIRCUIT_BREAKER_RESET_ALL, this::handleResetAllEnhancedEventBusCircuitBreakers)

        // 自适应部署管理器
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_ADAPTIVE_CONFIG_GET, this::handleGetAdaptiveDeploymentConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_ADAPTIVE_CONFIG_SET, this::handleSetAdaptiveDeploymentConfig)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_ADAPTIVE_STATS_GET, this::handleGetAdaptiveDeploymentStats)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_ADAPTIVE_DEPLOY, this::handleAdaptiveDeploy)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_ADAPTIVE_UNDEPLOY, this::handleAdaptiveUndeploy)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_ADAPTIVE_INFO_GET, this::handleGetAdaptiveDeploymentInfo)
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DEPLOYMENT_ADAPTIVE_INFO_GET_ALL, this::handleGetAllAdaptiveDeploymentInfo)
    }

    override fun onStart(startPromise: Promise<Void>) {
        try {
            // 初始化Vert.x配置优化器
            vertxConfigOptimizer = VertxConfigOptimizer.getInstance()

            // 初始化增强版EventBus
            enhancedEventBus = EnhancedEventBus.getInstance(vertx)

            // 初始化自适应部署管理器
            adaptiveDeploymentManager = AdaptiveDeploymentManager.getInstance(vertx)

            // 初始化EventBus管理器
            eventBusManager = EventBusManager.getInstance(vertx)

            // 启动增强版EventBus
            enhancedEventBus.start()
                .compose { _ ->
                    // 启动自适应部署管理器
                    adaptiveDeploymentManager.start()
                }
                .onSuccess { _ ->
                    logger.info("VertxOptimizerVerticle started successfully")
                    startPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("Failed to start VertxOptimizerVerticle", cause)
                    startPromise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("Error starting VertxOptimizerVerticle", e)
            startPromise.fail(e)
        }
    }

    override fun onStop(stopPromise: Promise<Void>) {
        try {
            // 关闭增强版EventBus
            enhancedEventBus.close()
                .compose { _ ->
                    // 关闭自适应部署管理器
                    adaptiveDeploymentManager.close()
                }
                .onSuccess { _ ->
                    logger.info("VertxOptimizerVerticle stopped successfully")
                    stopPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("Failed to stop VertxOptimizerVerticle", cause)
                    stopPromise.fail(cause)
                }
        } catch (e: Exception) {
            logger.error("Error stopping VertxOptimizerVerticle", e)
            stopPromise.fail(e)
        }
    }

    /**
     * 处理获取Vert.x配置请求
     */
    private fun handleGetVertxConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val profile = message.body().getString("profile")
            val configProfile = if (profile != null) {
                VertxConfigOptimizer.PerformanceProfile.valueOf(profile)
            } else {
                vertxConfigOptimizer.getCurrentProfile()
            }

            val config = vertxConfigOptimizer.getOptimizedConfig(configProfile)
            replyWithSuccess(message, config)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理设置Vert.x配置请求
     */
    private fun handleSetVertxConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val config = message.body().getJsonObject("config")
            if (config == null) {
                sendError(message, 400, "Config is required")
                return
            }

            // 更新自定义配置
            val customProfile = VertxConfigOptimizer.PerformanceProfile.CUSTOM
            vertxConfigOptimizer.setCurrentProfile(customProfile)

            replyWithSuccess(message, JsonObject().put("profile", customProfile.name))
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理获取Vert.x配置文件请求
     */
    private fun handleGetVertxConfigProfile(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val currentProfile = vertxConfigOptimizer.getCurrentProfile()
            replyWithSuccess(message, JsonObject().put("profile", currentProfile.name))
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理设置Vert.x配置文件请求
     */
    private fun handleSetVertxConfigProfile(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val profile = message.body().getString("profile")
            if (profile == null) {
                replyWithError(message, 400, "Profile is required")
                return
            }

            val configProfile = VertxConfigOptimizer.PerformanceProfile.valueOf(profile)
            vertxConfigOptimizer.setCurrentProfile(configProfile)

            replyWithSuccess(message, JsonObject().put("profile", configProfile.name))
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理自动选择Vert.x配置文件请求
     */
    private fun handleAutoSelectVertxConfigProfile(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val profile = vertxConfigOptimizer.autoSelectProfile()
            replyWithSuccess(message, JsonObject().put("profile", profile.name))
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理获取增强版EventBus配置请求
     */
    private fun handleGetEnhancedEventBusConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val stats = enhancedEventBus.getStats()
            replyWithSuccess(message, stats)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理设置增强版EventBus配置请求
     */
    private fun handleSetEnhancedEventBusConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val config = message.body().getJsonObject("config")
            if (config == null) {
                replyWithError(message, 400, "Config is required")
                return
            }

            enhancedEventBus.updateConfig(config)
                .onSuccess { _ ->
                    replyWithSuccess(message, null)
                }
                .onFailure { cause ->
                    replyWithError(message, cause)
                }
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理获取增强版EventBus统计信息请求
     */
    private fun handleGetEnhancedEventBusStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val stats = enhancedEventBus.getStats()
            val circuitBreakerStats = enhancedEventBus.getCircuitBreakerStats()
            val messageProcessorStats = enhancedEventBus.getMessageProcessorStats()

            val result = JsonObject()
                .put("stats", stats)
                .put("circuitBreakers", circuitBreakerStats)
                .put("messageProcessors", messageProcessorStats)

            replyWithSuccess(message, result)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理重置增强版EventBus断路器请求
     */
    private fun handleResetEnhancedEventBusCircuitBreaker(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val address = message.body().getString("address")
            if (address == null) {
                replyWithError(message, 400, "Address is required")
                return
            }

            enhancedEventBus.resetCircuitBreaker(address)
                .onSuccess { _ ->
                    replyWithSuccess(message, null)
                }
                .onFailure { cause ->
                    replyWithError(message, cause)
                }
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理重置所有增强版EventBus断路器请求
     */
    private fun handleResetAllEnhancedEventBusCircuitBreakers(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            enhancedEventBus.resetAllCircuitBreakers()
                .onSuccess { _ ->
                    replyWithSuccess(message, null)
                }
                .onFailure { cause ->
                    replyWithError(message, cause)
                }
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理获取自适应部署管理器配置请求
     */
    private fun handleGetAdaptiveDeploymentConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val stats = adaptiveDeploymentManager.getStats()
            replyWithSuccess(message, stats)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理设置自适应部署管理器配置请求
     */
    private fun handleSetAdaptiveDeploymentConfig(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val config = message.body().getJsonObject("config")
            if (config == null) {
                replyWithError(message, 400, "Config is required")
                return
            }

            adaptiveDeploymentManager.updateConfig(config)
                .onSuccess { _ ->
                    replyWithSuccess(message, null)
                }
                .onFailure { cause ->
                    replyWithError(message, cause)
                }
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理获取自适应部署管理器统计信息请求
     */
    private fun handleGetAdaptiveDeploymentStats(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val stats = adaptiveDeploymentManager.getStats()
            replyWithSuccess(message, stats)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理自适应部署请求
     */
    private fun handleAdaptiveDeploy(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val verticleName = message.body().getString("verticleName")
            if (verticleName == null) {
                replyWithError(message, 400, "Verticle name is required")
                return
            }

            val options = message.body().getJsonObject("options")
            val deploymentOptions = if (options != null) {
                io.vertx.core.DeploymentOptions(options)
            } else {
                io.vertx.core.DeploymentOptions()
            }

            adaptiveDeploymentManager.deployVerticle(verticleName, deploymentOptions)
                .onSuccess { deploymentId ->
                    replyWithSuccess(message, JsonObject().put("deploymentId", deploymentId))
                }
                .onFailure { cause ->
                    replyWithError(message, cause)
                }
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理自适应卸载请求
     */
    private fun handleAdaptiveUndeploy(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val deploymentId = message.body().getString("deploymentId")
            if (deploymentId == null) {
                replyWithError(message, 400, "Deployment ID is required")
                return
            }

            adaptiveDeploymentManager.undeployVerticle(deploymentId)
                .onSuccess { _ ->
                    replyWithSuccess(message, null)
                }
                .onFailure { cause ->
                    replyWithError(message, cause)
                }
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理获取自适应部署信息请求
     */
    private fun handleGetAdaptiveDeploymentInfo(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val deploymentId = message.body().getString("deploymentId")
            if (deploymentId == null) {
                replyWithError(message, 400, "Deployment ID is required")
                return
            }

            val deploymentInfo = adaptiveDeploymentManager.getDeploymentInfo(deploymentId)
            if (deploymentInfo != null) {
                val result = JsonObject()
                    .put("verticleName", deploymentInfo.verticleName)
                    .put("deploymentId", deploymentInfo.deploymentId)
                    .put("instances", deploymentInfo.instances.get())
                    .put("workerPoolSize", deploymentInfo.workerPoolSize.get())
                    .put("cpuAffinity", deploymentInfo.cpuAffinity)
                    .put("isolationGroup", deploymentInfo.isolationGroup)
                    .put("isolationLevel", deploymentInfo.isolationLevel.name)
                    .put("priority", deploymentInfo.priority)
                    .put("status", AdaptiveDeploymentManager.Status.values()[deploymentInfo.status.get()].name)
                    .put("deployTime", deploymentInfo.deployTime)

                replyWithSuccess(message, result)
            } else {
                replyWithError(message, 404, "Deployment not found: $deploymentId")
            }
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 处理获取所有自适应部署信息请求
     */
    private fun handleGetAllAdaptiveDeploymentInfo(message: io.vertx.core.eventbus.Message<JsonObject>) {
        try {
            val deployments = adaptiveDeploymentManager.getAllDeployments()
            val result = JsonObject()

            deployments.forEach { (deploymentId, deploymentInfo) ->
                result.put(deploymentId, JsonObject()
                    .put("verticleName", deploymentInfo.verticleName)
                    .put("deploymentId", deploymentInfo.deploymentId)
                    .put("instances", deploymentInfo.instances.get())
                    .put("workerPoolSize", deploymentInfo.workerPoolSize.get())
                    .put("cpuAffinity", deploymentInfo.cpuAffinity)
                    .put("isolationGroup", deploymentInfo.isolationGroup)
                    .put("isolationLevel", deploymentInfo.isolationLevel.name)
                    .put("priority", deploymentInfo.priority)
                    .put("status", AdaptiveDeploymentManager.Status.values()[deploymentInfo.status.get()].name)
                    .put("deployTime", deploymentInfo.deployTime)
                )
            }

            replyWithSuccess(message, result)
        } catch (e: Exception) {
            replyWithError(message, e)
        }
    }

    /**
     * 发送成功响应
     */
    private fun replyWithSuccess(message: io.vertx.core.eventbus.Message<JsonObject>, result: Any?, statusCode: Int = 200) {
        val response = JsonObject()
            .put("success", true)
            .put("statusCode", statusCode)

        if (result != null) {
            response.put("result", result)
        }

        message.reply(response)
    }

    /**
     * 发送错误响应
     */
    private fun replyWithError(message: io.vertx.core.eventbus.Message<JsonObject>, cause: Throwable) {
        val response = JsonObject()
            .put("success", false)
            .put("statusCode", 500)
            .put("message", cause.message)

        message.reply(response)
    }

    /**
     * 发送错误响应
     */
    private fun replyWithError(message: io.vertx.core.eventbus.Message<JsonObject>, statusCode: Int, errorMessage: String) {
        val response = JsonObject()
            .put("success", false)
            .put("statusCode", statusCode)
            .put("message", errorMessage)

        message.reply(response)
    }
}
