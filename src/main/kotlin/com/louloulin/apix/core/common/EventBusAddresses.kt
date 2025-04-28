package com.louloulin.apix.core.common

/**
 * 定义所有 EventBus 地址常量
 */
object EventBusAddresses {
    // 配置相关
    const val CONFIG_GET = "apix.config.get"
    const val CONFIG_GET_ALL = "apix.config.get.all"
    const val CONFIG_SET = "apix.config.set"
    const val CONFIG_UPDATE = "apix.config.update"
    const val CONFIG_RELOAD = "apix.config.reload"

    // 路由相关
    const val ROUTE_GET_ALL = "apix.route.get.all"
    const val ROUTE_GET_BY_ID = "apix.route.get.byId"
    const val ROUTE_CREATE = "apix.route.create"
    const val ROUTE_UPDATE = "apix.route.update"
    const val ROUTE_DELETE = "apix.route.delete"
    const val ROUTE_DEPLOY = "apix.route.deploy"
    const val ROUTE_UNDEPLOY = "apix.route.undeploy"

    // 服务相关
    const val SERVICE_GET_ALL = "apix.service.get.all"
    const val SERVICE_GET_BY_ID = "apix.service.get.byId"
    const val SERVICE_CREATE = "apix.service.create"
    const val SERVICE_UPDATE = "apix.service.update"
    const val SERVICE_DELETE = "apix.service.delete"

    // 插件相关
    const val PLUGIN_GET_ALL = "apix.plugin.get.all"
    const val PLUGIN_GET_BY_ID = "apix.plugin.get.byId"
    const val PLUGIN_CREATE = "apix.plugin.create"
    const val PLUGIN_UPDATE = "apix.plugin.update"
    const val PLUGIN_DELETE = "apix.plugin.delete"
    const val PLUGIN_ENABLE = "apix.plugin.enable"
    const val PLUGIN_DISABLE = "apix.plugin.disable"
    const val PLUGIN_RELOAD = "apix.plugin.reload"
    const val PLUGIN_LOAD_JAR = "apix.plugin.load.jar"
    const val PLUGIN_SCAN_DIR = "apix.plugin.scan.dir"

    // 监控相关
    const val METRICS_GET = "apix.metrics.get"
    const val METRICS_RESET = "apix.metrics.reset"

    // 系统相关
    const val SYSTEM_HEALTH = "apix.system.health"
    const val SYSTEM_INFO = "apix.system.info"
    const val SYSTEM_INFO_GET = "apix.system.info.get"
    const val SYSTEM_SHUTDOWN = "apix.system.shutdown"

    // 部署相关
    const val DEPLOYMENT_DEPLOY_API = "apix.deployment.deploy.api"
    const val DEPLOYMENT_UNDEPLOY_API = "apix.deployment.undeploy.api"
    const val DEPLOYMENT_GET_STATUS = "apix.deployment.get.status"

    // 认证相关
    const val AUTH_VALIDATE_API_KEY = "apix.auth.validate.apiKey"
    const val AUTH_CREATE_API_KEY = "apix.auth.create.apiKey"
    const val AUTH_GET_API_KEYS = "apix.auth.get.apiKeys"
    const val AUTH_DELETE_API_KEY = "apix.auth.delete.apiKey"
    const val AUTH_VALIDATE_JWT = "apix.auth.validate.jwt"
    const val AUTH_GENERATE_JWT = "apix.auth.generate.jwt"

    // 缓存相关
    const val CACHE_GET = "apix.cache.get"
    const val CACHE_PUT = "apix.cache.put"
    const val CACHE_INVALIDATE = "apix.cache.invalidate"
    const val CACHE_CLEAR = "apix.cache.clear"
    const val CACHE_STATS = "apix.cache.stats"

    // 管理 API 相关
    const val ADMIN_GET_ROUTES = "apix.admin.routes.get"
    const val ADMIN_GET_ROUTE_BY_ID = "apix.admin.routes.get.byId"
    const val ADMIN_CREATE_ROUTE = "apix.admin.routes.create"
    const val ADMIN_UPDATE_ROUTE = "apix.admin.routes.update"
    const val ADMIN_DELETE_ROUTE = "apix.admin.routes.delete"
    const val ADMIN_GET_PLUGINS = "apix.admin.plugins.get"
    const val ADMIN_GET_PLUGIN_BY_ID = "apix.admin.plugins.get.byId"
    const val ADMIN_GET_CONFIG = "apix.admin.config.get"
    const val ADMIN_UPDATE_CONFIG = "apix.admin.config.update"
    const val ADMIN_GET_SYSTEM_INFO = "apix.admin.system.info.get"
    const val ADMIN_GET_METRICS = "apix.admin.metrics.get"

    // AI 特定功能
    const val AI_MODEL_LIST = "apix.ai.model.list"
    const val AI_USAGE_GET = "apix.ai.usage.get"
    const val AI_CACHE_CLEAR = "apix.ai.cache.clear"
}
