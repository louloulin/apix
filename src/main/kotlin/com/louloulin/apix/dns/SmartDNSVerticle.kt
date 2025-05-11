package com.louloulin.apix.dns

import com.louloulin.apix.core.common.EventBusAddresses
import com.louloulin.apix.core.verticle.BaseVerticle
import io.vertx.core.Promise
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * 智能DNS Verticle
 * 处理DNS相关的操作，提供EventBus接口
 */
class SmartDNSVerticle : BaseVerticle() {
    // 使用BaseVerticle中的logger

    // 智能DNS管理器
    private lateinit var dnsManager: SmartDNSManager

    /**
     * 注册EventBus处理器
     */
    override fun registerEventBusHandlers() {
        // 获取DNS状态
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_STATUS_GET) { message ->
            val status = dnsManager.getStatus()
            sendSuccess(message, status)
        }

        // 根据IP地址获取地理位置
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_GEO_LOCATION_GET) { message ->
            val ip = message.body().getString("ip", "")

            if (ip.isEmpty()) {
                sendError(message, 400, "IP parameter is required")
                return@consumer
            }

            dnsManager.getGeoLocation(ip)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 根据地理位置获取最佳节点
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_BEST_NODE_GET) { message ->
            val geoLocation = message.body().getJsonObject("geoLocation", JsonObject())

            if (geoLocation.isEmpty) {
                sendError(message, 400, "geoLocation parameter is required")
                return@consumer
            }

            dnsManager.getBestNode(geoLocation)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 创建DNS记录
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_RECORD_CREATE) { message ->
            val recordJson = message.body().getJsonObject("record", JsonObject())

            if (recordJson.isEmpty) {
                sendError(message, 400, "record parameter is required")
                return@consumer
            }

            try {
                // 解析DNS记录
                val record = parseDNSRecord(recordJson)

                dnsManager.createRecord(record)
                    .onSuccess { result ->
                        sendSuccess(message, result)
                    }
                    .onFailure { cause ->
                        sendError(message, cause)
                    }
            } catch (e: Exception) {
                sendError(message, 400, e.message ?: "Invalid DNS record")
            }
        }

        // 更新DNS记录
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_RECORD_UPDATE) { message ->
            val recordId = message.body().getString("recordId", "")
            val recordJson = message.body().getJsonObject("record", JsonObject())

            if (recordId.isEmpty()) {
                sendError(message, 400, "recordId parameter is required")
                return@consumer
            }

            if (recordJson.isEmpty) {
                sendError(message, 400, "record parameter is required")
                return@consumer
            }

            try {
                // 解析DNS记录
                val record = parseDNSRecord(recordJson)

                dnsManager.updateRecord(recordId, record)
                    .onSuccess { result ->
                        sendSuccess(message, result)
                    }
                    .onFailure { cause ->
                        sendError(message, cause)
                    }
            } catch (e: Exception) {
                sendError(message, 400, e.message ?: "Invalid DNS record")
            }
        }

        // 删除DNS记录
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_RECORD_DELETE) { message ->
            val recordId = message.body().getString("recordId", "")

            if (recordId.isEmpty()) {
                sendError(message, 400, "recordId parameter is required")
                return@consumer
            }

            dnsManager.deleteRecord(recordId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 获取DNS记录
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_RECORD_GET) { message ->
            val recordId = message.body().getString("recordId", "")

            if (recordId.isEmpty()) {
                sendError(message, 400, "recordId parameter is required")
                return@consumer
            }

            dnsManager.getRecord(recordId)
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 获取所有DNS记录
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_RECORDS_GET) { message ->
            dnsManager.getAllRecords()
                .onSuccess { result ->
                    sendSuccess(message, result)
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }

        // 更新DNS配置
        vertx.eventBus().consumer<JsonObject>(EventBusAddresses.DNS_CONFIG_UPDATE) { message ->
            val config = message.body()

            dnsManager.updateConfig(config)
                .onSuccess {
                    sendSuccess(message, JsonObject().put("updated", true))
                }
                .onFailure { cause ->
                    sendError(message, cause)
                }
        }
    }

    /**
     * 解析DNS记录
     *
     * @param recordJson DNS记录JSON
     * @return DNSRecord DNS记录
     */
    private fun parseDNSRecord(recordJson: JsonObject): DNSRecord {
        val name = recordJson.getString("name") ?: throw IllegalArgumentException("name is required")
        val typeStr = recordJson.getString("type") ?: throw IllegalArgumentException("type is required")
        val content = recordJson.getString("content") ?: throw IllegalArgumentException("content is required")
        val ttl = recordJson.getInteger("ttl", 300)
        val priority = recordJson.getInteger("priority", 0)
        val proxied = recordJson.getBoolean("proxied", false)

        // 解析记录类型
        val type = try {
            DNSRecordType.valueOf(typeStr)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid DNS record type: $typeStr")
        }

        // 解析地理位置限制
        val geoRestrictionsJson = recordJson.getJsonArray("geoRestrictions", JsonArray())
        val geoRestrictions = mutableListOf<GeoRestriction>()

        for (i in 0 until geoRestrictionsJson.size()) {
            val geoRestrictionJson = geoRestrictionsJson.getJsonObject(i)
            val geoTypeStr = geoRestrictionJson.getString("type") ?: continue
            val geoValue = geoRestrictionJson.getString("value") ?: continue

            val geoType = try {
                GeoRestrictionType.valueOf(geoTypeStr)
            } catch (e: IllegalArgumentException) {
                continue
            }

            geoRestrictions.add(GeoRestriction(geoType, geoValue))
        }

        // 解析健康检查
        val healthChecksJson = recordJson.getJsonArray("healthChecks", JsonArray())
        val healthChecks = mutableListOf<HealthCheck>()

        for (i in 0 until healthChecksJson.size()) {
            val healthCheckJson = healthChecksJson.getJsonObject(i)
            val healthTypeStr = healthCheckJson.getString("type") ?: continue
            val healthTarget = healthCheckJson.getString("target") ?: continue
            val healthInterval = healthCheckJson.getInteger("interval", 60)
            val healthTimeout = healthCheckJson.getInteger("timeout", 5)
            val healthRetries = healthCheckJson.getInteger("retries", 3)

            val healthType = try {
                HealthCheckType.valueOf(healthTypeStr)
            } catch (e: IllegalArgumentException) {
                continue
            }

            healthChecks.add(HealthCheck(healthType, healthTarget, healthInterval, healthTimeout, healthRetries))
        }

        return DNSRecord(name, type, content, ttl, priority, proxied, geoRestrictions, healthChecks)
    }

    /**
     * 启动Verticle
     */
    override fun onStart(startPromise: Promise<Void>) {
        logger.info("启动智能DNS Verticle")

        // 获取配置
        vertx.eventBus().request<JsonObject>(EventBusAddresses.CONFIG_GET, JsonObject()) { ar ->
            if (ar.succeeded()) {
                val configResponse = ar.result().body()
                if (configResponse.getBoolean("success", false)) {
                    val config = configResponse.getJsonObject("result", JsonObject())

                    // 获取DNS配置
                    val dnsConfig = config.getJsonObject("dns", JsonObject())

                    // 初始化智能DNS管理器
                    dnsManager = SmartDNSManager.getInstance(vertx)

                    dnsManager.initialize(dnsConfig)
                        .onSuccess {
                            logger.info("智能DNS管理器初始化成功")

                            // 注册DNS组件状态
                            registerComponentStatus()

                            startPromise.complete()
                        }
                        .onFailure { cause ->
                            logger.error("智能DNS管理器初始化失败", cause)
                            startPromise.fail(cause)
                        }
                } else {
                    val error = "获取配置失败: ${configResponse.getString("error", "未知错误")}"
                    logger.error(error)
                    startPromise.fail(error)
                }
            } else {
                logger.error("获取配置失败", ar.cause())
                startPromise.fail(ar.cause())
            }
        }
    }

    /**
     * 注册DNS组件状态
     */
    private fun registerComponentStatus() {
        val status = dnsManager.getStatus()
        val enabled = status.getBoolean("enabled", false)

        vertx.eventBus().send(EventBusAddresses.HEALTH_COMPONENT_STATUS, JsonObject()
            .put("component", "dns")
            .put("status", enabled)
        )
    }

    /**
     * 停止Verticle
     */
    override fun onStop(stopPromise: Promise<Void>) {
        logger.info("停止智能DNS Verticle")

        if (::dnsManager.isInitialized) {
            dnsManager.close()
                .onSuccess {
                    logger.info("智能DNS管理器关闭成功")
                    stopPromise.complete()
                }
                .onFailure { cause ->
                    logger.error("智能DNS管理器关闭失败", cause)
                    stopPromise.fail(cause)
                }
        } else {
            stopPromise.complete()
        }
    }
}
