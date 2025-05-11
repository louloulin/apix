package com.louloulin.apix.edge.sync

import io.vertx.core.Future
import io.vertx.core.json.JsonObject

/**
 * 同步策略接口，定义数据同步的策略。
 */
interface SyncStrategy {
    /**
     * 执行数据同步。
     *
     * @param dataType 数据类型
     * @param currentVersion 当前版本
     * @return Future<JsonObject> 同步结果
     */
    fun sync(dataType: String, currentVersion: Long): Future<JsonObject>
    
    /**
     * 获取策略名称。
     *
     * @return 策略名称
     */
    fun getName(): String
    
    /**
     * 获取策略配置。
     *
     * @return 策略配置
     */
    fun getConfig(): JsonObject
}
