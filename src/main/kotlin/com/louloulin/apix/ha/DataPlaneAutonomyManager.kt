package com.louloulin.apix.ha

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.mode.NodeMode
import com.louloulin.apix.core.mode.NodeModeManager
import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 数据平面自治管理器，负责管理数据平面节点的自治能力。
 * 确保数据平面节点在控制平面故障时能够继续处理请求。
 */
class DataPlaneAutonomyManager(private val vertx: Vertx) {
    private val logger = LoggerFactory.getLogger(DataPlaneAutonomyManager::class.java)

    // 节点模式管理器
    private lateinit var nodeModeManager: NodeModeManager

    // 自治模式是否启用
    private val autonomyEnabled = AtomicBoolean(false)

    // 当前是否处于自治模式
    private val inAutonomyMode = AtomicBoolean(false)

    // 最后一次与控制平面通信的时间
    private val lastControlPlaneContact = AtomicLong(0)

    // 控制平面健康检查定时器 ID
    private var controlPlaneCheckTimerId = -1L

    // 控制平面健康检查间隔（毫秒）
    private val controlPlaneCheckInterval = 5000L

    // 控制平面超时时间（毫秒）
    private val controlPlaneTimeout = 15000L

    // 本地缓存的配置
    private val localConfig = AtomicReference<JsonObject>(JsonObject())

    // 本地缓存的路由
    private val localRoutes = AtomicReference<JsonObject>(JsonObject())

    // 本地缓存的服务
    private val localServices = AtomicReference<JsonObject>(JsonObject())

    // 本地缓存的插件
    private val localPlugins = AtomicReference<JsonObject>(JsonObject())

    /**
     * 初始化数据平面自治管理器。
     *
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    fun initialize(config: JsonObject): Future<Void> {
        logger.info("初始化数据平面自治管理器")

        // 获取配置
        val haConfig = config.getJsonObject("ha", JsonObject())
        val autonomyConfig = haConfig.getJsonObject("dataPlaneAutonomy", JsonObject())

        // 检查自治模式是否启用
        autonomyEnabled.set(autonomyConfig.getBoolean("enabled", true))

        // 获取节点模式管理器
        nodeModeManager = NodeModeManager.getInstance(vertx)

        // 如果不是数据平面节点，则不需要启用自治模式
        if (!nodeModeManager.isDataPlane()) {
            logger.info("当前节点不是数据平面节点，不启用自治模式")
            return Future.succeededFuture()
        }

        if (!autonomyEnabled.get()) {
            logger.info("数据平面自治模式未启用")
            return Future.succeededFuture()
        }

        // 初始化本地缓存
        return initializeLocalCache(config)
            .compose { _ ->
                // 启动控制平面健康检查
                startControlPlaneCheck()

                // 注册事件总线处理器
                registerEventBusHandlers()

                Future.succeededFuture()
            }
    }

    /**
     * 初始化本地缓存。
     *
     * @param config 配置信息
     * @return 初始化完成的 Future
     */
    private fun initializeLocalCache(config: JsonObject): Future<Void> {
        logger.info("初始化本地缓存")

        // 保存初始配置
        localConfig.set(config.copy())

        // 获取路由配置
        return getRoutes()
            .compose { routes ->
                localRoutes.set(routes)

                // 获取服务配置
                getServices()
            }
            .compose { services ->
                localServices.set(services)

                // 获取插件配置
                getPlugins()
            }
            .compose { plugins ->
                localPlugins.set(plugins)

                logger.info("本地缓存初始化完成")
                Future.succeededFuture()
            }
    }

    /**
     * 获取路由配置。
     *
     * @return 包含路由配置的 Future
     */
    private fun getRoutes(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        vertx.eventBus().request<JsonObject>(EventBusAddresses.ROUTE_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    promise.complete(response.getJsonObject("result", JsonObject()))
                } else {
                    promise.fail("获取路由配置失败: ${response.getString("message")}")
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 获取服务配置。
     *
     * @return 包含服务配置的 Future
     */
    private fun getServices(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        vertx.eventBus().request<JsonObject>(EventBusAddresses.SERVICE_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    promise.complete(response.getJsonObject("result", JsonObject()))
                } else {
                    promise.fail("获取服务配置失败: ${response.getString("message")}")
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 获取插件配置。
     *
     * @return 包含插件配置的 Future
     */
    private fun getPlugins(): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        vertx.eventBus().request<JsonObject>(EventBusAddresses.PLUGIN_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()
                if (response.getBoolean("success", false)) {
                    // 获取插件对象，它应该是一个 JsonObject
                    val result = response.getJsonObject("result", JsonObject())
                    // 从结果中获取 plugins 字段
                    val plugins = result.getJsonObject("plugins", JsonObject())
                    promise.complete(plugins)
                } else {
                    promise.fail("获取插件配置失败: ${response.getString("message")}")
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 启动控制平面健康检查。
     */
    private fun startControlPlaneCheck() {
        // 记录当前时间为最后一次与控制平面通信的时间
        lastControlPlaneContact.set(System.currentTimeMillis())

        // 启动定期检查
        controlPlaneCheckTimerId = vertx.setPeriodic(controlPlaneCheckInterval) { _ ->
            checkControlPlaneHealth()
        }

        // 监听控制平面心跳
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_CONTROL_PLANE_HEARTBEAT) { message ->
            // 更新最后一次与控制平面通信的时间
            lastControlPlaneContact.set(System.currentTimeMillis())

            // 如果当前处于自治模式，尝试退出自治模式
            if (inAutonomyMode.get()) {
                exitAutonomyMode()
            }
        }
    }

    /**
     * 检查控制平面健康状态。
     */
    private fun checkControlPlaneHealth() {
        // 检查是否超过超时时间
        val now = System.currentTimeMillis()
        val lastContact = lastControlPlaneContact.get()

        if (now - lastContact > controlPlaneTimeout) {
            // 控制平面可能故障，进入自治模式
            enterAutonomyMode()
        } else {
            // 主动检查控制平面状态
            probeControlPlane()
        }
    }

    /**
     * 主动检查控制平面状态。
     */
    private fun probeControlPlane() {
        vertx.eventBus().request<JsonObject>(EventBusAddresses.HA_CONTROL_PLANE_PROBE, JsonObject(), { ar ->
            if (ar.succeeded()) {
                // 更新最后一次与控制平面通信的时间
                lastControlPlaneContact.set(System.currentTimeMillis())

                // 如果当前处于自治模式，尝试退出自治模式
                if (inAutonomyMode.get()) {
                    exitAutonomyMode()
                }
            } else {
                // 控制平面可能故障，但不立即进入自治模式
                // 等待超时检查触发自治模式
                logger.warn("无法连接到控制平面: {}", ar.cause().message)
            }
        })
    }

    /**
     * 进入自治模式。
     */
    private fun enterAutonomyMode() {
        if (inAutonomyMode.compareAndSet(false, true)) {
            logger.info("进入数据平面自治模式")

            // 发布自治模式状态变更事件
            publishAutonomyStatusChange(true)

            // 启用本地缓存
            enableLocalCache()
        }
    }

    /**
     * 退出自治模式。
     */
    private fun exitAutonomyMode() {
        if (inAutonomyMode.compareAndSet(true, false)) {
            logger.info("退出数据平面自治模式")

            // 发布自治模式状态变更事件
            publishAutonomyStatusChange(false)

            // 禁用本地缓存
            disableLocalCache()

            // 同步最新配置
            syncLatestConfiguration()
        }
    }

    /**
     * 发布自治模式状态变更事件。
     *
     * @param inAutonomyMode 是否处于自治模式
     */
    private fun publishAutonomyStatusChange(inAutonomyMode: Boolean) {
        val message = JsonObject()
            .put("inAutonomyMode", inAutonomyMode)
            .put("nodeId", vertx.hashCode().toString())
            .put("timestamp", System.currentTimeMillis())

        vertx.eventBus().publish(EventBusAddresses.HA_AUTONOMY_STATUS_CHANGE, message)
    }

    /**
     * 启用本地缓存。
     */
    private fun enableLocalCache() {
        // 在实际实现中，这里应该切换到使用本地缓存的配置
        logger.info("启用本地缓存")

        // 发布本地缓存的配置
        vertx.eventBus().publish(EventBusAddresses.CONFIG_USE_LOCAL, JsonObject()
            .put("config", localConfig.get())
            .put("routes", localRoutes.get())
            .put("services", localServices.get())
            .put("plugins", localPlugins.get())
        )
    }

    /**
     * 禁用本地缓存。
     */
    private fun disableLocalCache() {
        // 在实际实现中，这里应该切换回使用控制平面的配置
        logger.info("禁用本地缓存")

        // 发布禁用本地缓存的消息
        vertx.eventBus().publish(EventBusAddresses.CONFIG_USE_REMOTE, JsonObject())
    }

    /**
     * 同步最新配置。
     */
    private fun syncLatestConfiguration() {
        logger.info("同步最新配置")

        // 获取最新配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET_ALL, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // 更新本地缓存
                    localConfig.set(config.copy())

                    // 获取最新路由
                    getRoutes()
                        .compose { routes ->
                            localRoutes.set(routes)

                            // 获取最新服务
                            getServices()
                        }
                        .compose { services ->
                            localServices.set(services)

                            // 获取最新插件
                            getPlugins()
                        }
                        .onSuccess { plugins ->
                            localPlugins.set(plugins)
                            logger.info("配置同步完成")
                        }
                        .onFailure { cause ->
                            logger.error("配置同步失败", cause)
                        }
                }
            }
        }
    }

    /**
     * 注册事件总线处理器。
     */
    private fun registerEventBusHandlers() {
        // 处理获取自治状态请求
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.HA_AUTONOMY_STATUS_GET) { message ->
            message.reply(JsonObject()
                .put("success", true)
                .put("result", getStatus())
            )
        }

        // 处理配置更新通知
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.CONFIG_UPDATED) { message ->
            // 只有在非自治模式下才更新本地缓存
            if (!inAutonomyMode.get()) {
                val config = message.body().getJsonObject("config")
                if (config != null) {
                    localConfig.set(config.copy())
                }
            }
        }

        // 处理路由更新通知
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.ROUTE_UPDATED) { message ->
            // 只有在非自治模式下才更新本地缓存
            if (!inAutonomyMode.get()) {
                val routes = message.body().getJsonObject("routes")
                if (routes != null) {
                    localRoutes.set(routes.copy())
                }
            }
        }

        // 处理服务更新通知
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.SERVICE_UPDATED) { message ->
            // 只有在非自治模式下才更新本地缓存
            if (!inAutonomyMode.get()) {
                val services = message.body().getJsonObject("services")
                if (services != null) {
                    localServices.set(services.copy())
                }
            }
        }

        // 处理插件更新通知
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.PLUGIN_UPDATED) { message ->
            // 只有在非自治模式下才更新本地缓存
            if (!inAutonomyMode.get()) {
                val plugins = message.body().getJsonObject("plugins")
                if (plugins != null) {
                    localPlugins.set(plugins.copy())
                }
            }
        }
    }

    /**
     * 获取自治管理器状态。
     *
     * @return 包含状态信息的 JsonObject
     */
    fun getStatus(): JsonObject {
        return JsonObject()
            .put("enabled", autonomyEnabled.get())
            .put("inAutonomyMode", inAutonomyMode.get())
            .put("lastControlPlaneContact", lastControlPlaneContact.get())
            .put("timeSinceLastContact", System.currentTimeMillis() - lastControlPlaneContact.get())
            .put("controlPlaneTimeout", controlPlaneTimeout)
            .put("localCacheSize", JsonObject()
                .put("config", localConfig.get().size())
                .put("routes", localRoutes.get().size())
                .put("services", localServices.get().size())
                .put("plugins", localPlugins.get().size())
            )
    }

    /**
     * 停止数据平面自治管理器。
     *
     * @return 停止完成的 Future
     */
    fun stop(): Future<Void> {
        logger.info("停止数据平面自治管理器")

        // 停止控制平面健康检查定时器
        if (controlPlaneCheckTimerId != -1L) {
            vertx.cancelTimer(controlPlaneCheckTimerId)
            controlPlaneCheckTimerId = -1L
        }

        // 如果处于自治模式，退出自治模式
        if (inAutonomyMode.get()) {
            exitAutonomyMode()
        }

        return Future.succeededFuture()
    }

    companion object {
        // 单例实例
        @Volatile
        private var instance: DataPlaneAutonomyManager? = null

        /**
         * 获取 DataPlaneAutonomyManager 的单例实例。
         *
         * @param vertx Vert.x 实例
         * @return DataPlaneAutonomyManager 实例
         */
        fun getInstance(vertx: Vertx): DataPlaneAutonomyManager {
            return instance ?: synchronized(this) {
                instance ?: DataPlaneAutonomyManager(vertx).also { instance = it }
            }
        }
    }
}
