package com.louloulin.apix.edge.sync

import io.vertx.core.Future
import io.vertx.core.Promise
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory
import com.louloulin.apix.core.common.EventBusAddresses

/**
 * 增量同步策略，实现基于差异检测的增量数据同步。
 */
class IncrementalSyncStrategy(
    private val vertx: Vertx,
    private val config: JsonObject
) : SyncStrategy {
    private val logger = LoggerFactory.getLogger(IncrementalSyncStrategy::class.java)

    // 差异计算器
    private val diffCalculator = DataDiffCalculator()

    /**
     * 执行数据同步。
     *
     * @param dataType 数据类型
     * @param currentVersion 当前版本
     * @return Future<JsonObject> 同步结果
     */
    override fun sync(dataType: String, currentVersion: Long): Future<JsonObject> {
        logger.info("执行增量同步: $dataType, 当前版本: $currentVersion")

        val promise = Promise.promise<JsonObject>()

        // 获取最新版本信息
        getLatestVersion(dataType)
            .compose { latestVersion ->
                logger.info("获取到最新版本: $latestVersion")

                // 如果当前版本已经是最新的，则不需要同步
                if (currentVersion >= latestVersion) {
                    logger.info("当前版本已是最新，无需同步")
                    return@compose Future.succeededFuture(JsonObject()
                        .put("dataType", dataType)
                        .put("version", currentVersion)
                        .put("status", "UP_TO_DATE")
                        .put("message", "Already up to date")
                    )
                }

                // 如果当前版本为0或差距过大，执行全量同步
                if (currentVersion == 0L || (latestVersion - currentVersion) > config.getInteger("maxIncrementalVersionGap", 10)) {
                    logger.info("执行全量同步")
                    return@compose fullSync(dataType, latestVersion)
                }

                // 否则执行增量同步
                logger.info("执行增量同步")
                return@compose incrementalSync(dataType, currentVersion, latestVersion)
            }
            .onSuccess { result ->
                promise.complete(result)
            }
            .onFailure { cause ->
                logger.error("同步失败", cause)
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 获取最新版本。
     *
     * @param dataType 数据类型
     * @return Future<Long> 最新版本
     */
    private fun getLatestVersion(dataType: String): Future<Long> {
        val promise = Promise.promise<Long>()

        // 构建请求
        val request = JsonObject()
            .put("dataType", dataType)

        // 发送请求到控制平面
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_LATEST_VERSION, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getLong("version", 0L))
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 执行全量同步。
     *
     * @param dataType 数据类型
     * @param latestVersion 最新版本
     * @return Future<JsonObject> 同步结果
     */
    private fun fullSync(dataType: String, latestVersion: Long): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // 构建请求
        val request = JsonObject()
            .put("dataType", dataType)
            .put("version", latestVersion)

        // 发送请求到控制平面
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_FULL_DATA, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    // 保存数据到本地
                    saveData(dataType, latestVersion, response.getJsonObject("data", JsonObject()))
                        .onSuccess {
                            promise.complete(JsonObject()
                                .put("dataType", dataType)
                                .put("version", latestVersion)
                                .put("status", "SYNCED")
                                .put("syncType", "FULL")
                                .put("message", "Full sync completed")
                            )
                        }
                        .onFailure { cause ->
                            promise.fail(cause)
                        }
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 执行增量同步。
     *
     * @param dataType 数据类型
     * @param currentVersion 当前版本
     * @param latestVersion 最新版本
     * @return Future<JsonObject> 同步结果
     */
    private fun incrementalSync(dataType: String, currentVersion: Long, latestVersion: Long): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // 获取当前数据
        getData(dataType)
            .compose<JsonObject> { currentData ->
                // 构建请求
                val request = JsonObject()
                    .put("dataType", dataType)
                    .put("baseVersion", currentVersion)
                    .put("targetVersion", latestVersion)

                // 发送请求到控制平面获取差异
                vertx.eventBus().request<JsonObject>(EventBusAddresses.CONTROL_PLANE_GET_DIFF, request) { ar ->
                    if (ar.succeeded()) {
                        val response = ar.result().body()

                        if (response.getBoolean("success", false)) {
                            val diff = response.getJsonObject("diff", JsonObject())

                            // 应用差异
                            try {
                                val mergedData = diffCalculator.applyDiff(currentData, diff)

                                // 保存合并后的数据
                                saveData(dataType, latestVersion, mergedData)
                                    .onSuccess {
                                        promise.complete(JsonObject()
                                            .put("dataType", dataType)
                                            .put("version", latestVersion)
                                            .put("status", "SYNCED")
                                            .put("syncType", "INCREMENTAL")
                                            .put("message", "Incremental sync completed")
                                            .put("diffSize", diff.encode().length)
                                        )
                                    }
                                    .onFailure { cause ->
                                        promise.fail(cause)
                                    }
                            } catch (e: Exception) {
                                logger.error("应用差异失败", e)
                                promise.fail(e)
                            }
                        } else {
                            promise.fail(response.getString("error", "Unknown error"))
                        }
                    } else {
                        promise.fail(ar.cause())
                    }
                }

                return@compose Future.succeededFuture()
            }
            .onFailure { cause ->
                promise.fail(cause)
            }

        return promise.future()
    }

    /**
     * 获取数据。
     *
     * @param dataType 数据类型
     * @return Future<JsonObject> 数据
     */
    private fun getData(dataType: String): Future<JsonObject> {
        val promise = Promise.promise<JsonObject>()

        // 构建请求
        val request = JsonObject()
            .put("dataType", dataType)

        // 发送请求到本地存储
        vertx.eventBus().request<JsonObject>(EventBusAddresses.LOCAL_STORAGE_GET, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete(response.getJsonObject("data", JsonObject()))
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 保存数据。
     *
     * @param dataType 数据类型
     * @param version 版本
     * @param data 数据
     * @return Future<Void> 保存结果
     */
    private fun saveData(dataType: String, version: Long, data: JsonObject): Future<Void> {
        val promise = Promise.promise<Void>()

        // 构建请求
        val request = JsonObject()
            .put("dataType", dataType)
            .put("version", version)
            .put("data", data)

        // 发送请求到本地存储
        vertx.eventBus().request<JsonObject>(EventBusAddresses.LOCAL_STORAGE_SAVE, request) { ar ->
            if (ar.succeeded()) {
                val response = ar.result().body()

                if (response.getBoolean("success", false)) {
                    promise.complete()
                } else {
                    promise.fail(response.getString("error", "Unknown error"))
                }
            } else {
                promise.fail(ar.cause())
            }
        }

        return promise.future()
    }

    /**
     * 获取策略名称。
     *
     * @return 策略名称
     */
    override fun getName(): String {
        return "incremental"
    }

    /**
     * 获取策略配置。
     *
     * @return 策略配置
     */
    override fun getConfig(): JsonObject {
        return config.copy()
    }
}
