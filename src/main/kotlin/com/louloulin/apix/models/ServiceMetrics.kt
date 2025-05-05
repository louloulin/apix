package com.louloulin.apix.models

import io.vertx.core.json.JsonObject

/**
 * 表示服务指标的模型类。
 * 用于在EventBus上传输服务性能指标数据。
 */
data class ServiceMetrics(
    val serviceId: String,
    val activeRequests: Int = 0,
    val concurrencyLimit: Int = 0,
    val averageResponseTime: Double = 0.0,
    val errorRate: Double = 0.0,
    val rejectionRate: Double = 0.0,
    val totalRequests: Long = 0,
    val utilizationRate: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 将服务指标转换为JSON对象。
     */
    fun toJson(): JsonObject {
        return JsonObject()
            .put("serviceId", serviceId)
            .put("activeRequests", activeRequests)
            .put("concurrencyLimit", concurrencyLimit)
            .put("averageResponseTime", averageResponseTime)
            .put("errorRate", errorRate)
            .put("rejectionRate", rejectionRate)
            .put("totalRequests", totalRequests)
            .put("utilizationRate", utilizationRate)
            .put("timestamp", timestamp)
    }
    
    companion object {
        /**
         * 从JSON对象创建服务指标。
         */
        fun fromJson(json: JsonObject): ServiceMetrics {
            return ServiceMetrics(
                serviceId = json.getString("serviceId"),
                activeRequests = json.getInteger("activeRequests", 0),
                concurrencyLimit = json.getInteger("concurrencyLimit", 0),
                averageResponseTime = json.getDouble("averageResponseTime", 0.0),
                errorRate = json.getDouble("errorRate", 0.0),
                rejectionRate = json.getDouble("rejectionRate", 0.0),
                totalRequests = json.getLong("totalRequests", 0),
                utilizationRate = json.getDouble("utilizationRate", 0.0),
                timestamp = json.getLong("timestamp", System.currentTimeMillis())
            )
        }
    }
}
