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
    const val PLUGIN_GET_TYPES = "apix.plugin.get.types"

    // 监控相关
    const val METRICS_GET = "apix.metrics.get"
    const val METRICS_RESET = "apix.metrics.reset"

    // 系统相关
    const val SYSTEM_HEALTH = "apix.system.health"
    const val SYSTEM_INFO = "apix.system.info"
    const val SYSTEM_INFO_GET = "apix.system.info.get"
    const val SYSTEM_SHUTDOWN = "apix.system.shutdown"

    // 健康检查相关
    const val HEALTH_CHECK = "apix.health.check"
    const val HEALTH_COMPONENT_STATUS = "apix.health.component.status"
    const val HEALTH_SYSTEM_INFO = "apix.health.system.info"

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

    // AI 模型路由
    const val AI_MODEL_ROUTE = "apix.ai.model.route"
    const val AI_MODEL_RULES_GET = "apix.ai.model.rules.get"
    const val AI_MODEL_RULE_ADD = "apix.ai.model.rule.add"
    const val AI_MODEL_RULE_REMOVE = "apix.ai.model.rule.remove"
    const val AI_MODEL_RULES_CLEAR = "apix.ai.model.rules.clear"

    // AI 提示词增强
    const val AI_PROMPT_ENHANCE = "apix.ai.prompt.enhance"
    const val AI_PROMPT_TEMPLATES_GET = "apix.ai.prompt.templates.get"
    const val AI_PROMPT_TEMPLATE_GET = "apix.ai.prompt.template.get"
    const val AI_PROMPT_TEMPLATE_ADD = "apix.ai.prompt.template.add"
    const val AI_PROMPT_TEMPLATE_REMOVE = "apix.ai.prompt.template.remove"
    const val AI_PROMPT_RULES_GET = "apix.ai.prompt.rules.get"
    const val AI_PROMPT_RULE_ADD = "apix.ai.prompt.rule.add"
    const val AI_PROMPT_RULE_REMOVE = "apix.ai.prompt.rule.remove"
    const val AI_PROMPT_RULES_CLEAR = "apix.ai.prompt.rules.clear"

    // 集群相关
    const val CLUSTER_CONFIG_GET = "apix.cluster.config.get"
    const val CLUSTER_NODE_INFO = "apix.cluster.node.info"
    const val CLUSTER_NODES_GET = "apix.cluster.nodes.get"
    const val CLUSTER_METRICS_GET = "apix.cluster.metrics.get"
    const val CLUSTER_CONFIG_SYNC = "apix.cluster.config.sync"
    const val CLUSTER_ROUTES_SYNC = "apix.cluster.routes.sync"
    const val CLUSTER_SERVICES_SYNC = "apix.cluster.services.sync"
    const val CLUSTER_PLUGINS_SYNC = "apix.cluster.plugins.sync"

    // 节点模式相关
    const val NODE_MODE_GET = "apix.node.mode.get"
    const val NODE_INFO_GET = "apix.node.info.get"
    const val NODE_MODE_CHANGE = "apix.node.mode.change"

    // EventBus增强相关
    const val EVENTBUS_TYPE_GET = "apix.eventbus.type.get"
    const val EVENTBUS_TYPE_SET = "apix.eventbus.type.set"
    const val EVENTBUS_STATS_GET = "apix.eventbus.stats.get"
    const val EVENTBUS_DISTRIBUTED_CONFIG = "apix.eventbus.distributed.config"
    const val EVENTBUS_HIGHPERF_CONFIG = "apix.eventbus.highperf.config"

    // 高可用性相关
    const val HA_STATUS_GET = "apix.ha.status.get"
    const val HA_LEADER_STATUS_CHANGE = "apix.ha.leader.status.change"
    const val HA_CONTROL_PLANE_PROBE = "apix.ha.control.plane.probe"
    const val HA_CONTROL_PLANE_HEARTBEAT = "apix.ha.control.plane.heartbeat"
    const val HA_AUTONOMY_STATUS_GET = "apix.ha.autonomy.status.get"
    const val HA_AUTONOMY_STATUS_CHANGE = "apix.ha.autonomy.status.change"
    const val HA_NODES_GET = "apix.ha.nodes.get"
    const val HA_REGIONS_GET = "apix.ha.regions.get"
    const val HA_CURRENT_REGION_GET = "apix.ha.current.region.get"
    const val HA_REGION_SYNC = "apix.ha.region.sync"
    const val HA_NODE_HEALTH = "apix.ha.node.health"
    const val CONFIG_USE_LOCAL = "apix.config.use.local"
    const val CONFIG_USE_REMOTE = "apix.config.use.remote"
    const val CONFIG_UPDATED = "apix.config.updated"
    const val ROUTE_UPDATED = "apix.route.updated"
    const val SERVICE_UPDATED = "apix.service.updated"
    const val PLUGIN_UPDATED = "apix.plugin.updated"

    // 弹性伸缩相关
    const val SCALING_STATUS_GET = "apix.scaling.status.get"
    const val SCALING_NODE_COUNT_SET = "apix.scaling.node.count.set"
    const val SCALING_NODE_SCALED_UP = "apix.scaling.node.scaled.up"
    const val SCALING_NODE_SCALED_DOWN = "apix.scaling.node.scaled.down"
    const val SCALING_GRACEFUL_SCALEDOWN_START = "apix.scaling.graceful.scaledown.start"
    const val SCALING_GRACEFUL_SCALEDOWN_CANCEL = "apix.scaling.graceful.scaledown.cancel"
    const val SCALING_GRACEFUL_SCALEDOWN_STATUS = "apix.scaling.graceful.scaledown.status"
    const val SCALING_RESOURCE_OPTIMIZATION_STATUS = "apix.scaling.resource.optimization.status"
    const val SCALING_RESOURCE_OPTIMIZATION_SUGGESTIONS = "apix.scaling.resource.optimization.suggestions"
    const val SCALING_RESOURCE_OPTIMIZATION_SUGGESTIONS_UPDATED = "apix.scaling.resource.optimization.suggestions.updated"

    // 故障隔离相关
    const val RESILIENCE_STATUS_GET = "apix.resilience.status.get"
    const val RESILIENCE_BULKHEAD_EXECUTE = "apix.resilience.bulkhead.execute"
    const val RESILIENCE_BULKHEAD_STATS_GET = "apix.resilience.bulkhead.stats.get"
    const val RESILIENCE_CIRCUIT_BREAKER_EXECUTE = "apix.resilience.circuit.breaker.execute"
    const val RESILIENCE_CIRCUIT_BREAKER_STATS_GET = "apix.resilience.circuit.breaker.stats.get"
    const val RESILIENCE_FALLBACK_APPLY = "apix.resilience.fallback.apply"
    const val RESILIENCE_FALLBACK_LEVEL_SET = "apix.resilience.fallback.level.set"
    const val RESILIENCE_FALLBACK_LEVEL_GET = "apix.resilience.fallback.level.get"
    const val RESILIENCE_FALLBACK_STATS_GET = "apix.resilience.fallback.stats.get"
    const val RESILIENCE_FAULT_INJECTION_MODE_SET = "apix.resilience.fault.injection.mode.set"
    const val RESILIENCE_FAULT_INJECTION_CHECK = "apix.resilience.fault.injection.check"
    const val RESILIENCE_FAULT_INJECTION_INJECT = "apix.resilience.fault.injection.inject"
    const val RESILIENCE_FAULT_INJECTION_STATS_GET = "apix.resilience.fault.injection.stats.get"

    // 并发控制相关
    const val CONCURRENCY_TRY_ACQUIRE = "apix.concurrency.try.acquire"
    const val CONCURRENCY_RELEASE = "apix.concurrency.release"
    const val CONCURRENCY_GET_METRICS = "apix.concurrency.get.metrics"
    const val CONCURRENCY_SET_LIMIT = "apix.concurrency.set.limit"
    const val CONCURRENCY_RESET_METRICS = "apix.concurrency.reset.metrics"

    // 内存管理相关
    const val MEMORY_USAGE_GET = "apix.memory.usage.get"
    const val MEMORY_GC_TRIGGER = "apix.memory.gc.trigger"
    const val MEMORY_CACHE_CLEAR = "apix.memory.cache.clear"
    const val MEMORY_LOW_NOTIFY = "apix.memory.low.notify"
    const val MEMORY_CRITICAL_NOTIFY = "apix.memory.critical.notify"
    const val MEMORY_RESTORED_NOTIFY = "apix.memory.restored.notify"

    // 对象池管理相关
    const val OBJECT_POOL_CREATE = "apix.memory.object.pool.create"
    const val OBJECT_POOL_REMOVE = "apix.memory.object.pool.remove"
    const val OBJECT_POOL_GET_STATS = "apix.memory.object.pool.stats.get"

    // 请求队列相关
    const val QUEUE_ENQUEUE = "apix.queue.enqueue"
    const val QUEUE_STATUS = "apix.queue.status"
    const val QUEUE_CONTROL = "apix.queue.control"
    const val QUEUE_CONFIG = "apix.queue.config"
    const val QUEUE_UPDATE = "apix.queue.update"
    const val QUEUE_STATS = "apix.queue.stats"

    // 限流相关
    const val RATE_LIMIT_CHECK = "apix.ratelimit.check"
    const val RATE_LIMIT_CREATE = "apix.ratelimit.create"
    const val RATE_LIMIT_DELETE = "apix.ratelimit.delete"
    const val RATE_LIMIT_STATUS = "apix.ratelimit.status"
}
