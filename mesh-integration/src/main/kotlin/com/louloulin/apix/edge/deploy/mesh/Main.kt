package com.louloulin.apix.edge.deploy.mesh

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject

/**
 * 主类，用于测试服务网格集成功能
 */
fun main() {
    val vertx = Vertx.vertx()
    
    // 创建 Istio 集成配置
    val istioConfig = JsonObject()
        .put("virtualServices", JsonArray()
            .add(JsonObject()
                .put("id", "test-vs")
                .put("name", "Test VirtualService")
                .put("hosts", JsonArray().add("test.example.com"))
                .put("gateways", JsonArray().add("test-gateway"))
            )
        )
        .put("destinationRules", JsonArray()
            .add(JsonObject()
                .put("id", "test-dr")
                .put("name", "Test DestinationRule")
                .put("host", "test-service")
                .put("subsets", JsonArray()
                    .add(JsonObject()
                        .put("name", "v1")
                        .put("labels", JsonObject().put("version", "v1"))
                    )
                )
            )
        )
    
    // 创建 Consul Connect 集成配置
    val consulConfig = JsonObject()
        .put("services", JsonArray()
            .add(JsonObject()
                .put("id", "test-service")
                .put("name", "test-service")
                .put("address", "127.0.0.1")
                .put("port", 8080)
            )
        )
        .put("intentions", JsonArray()
            .add(JsonObject()
                .put("id", "test-intention")
                .put("sourceName", "web")
                .put("destinationName", "api")
                .put("action", "allow")
            )
        )
    
    // 创建服务网格工厂
    val meshFactory = ServiceMeshFactory.getInstance(vertx)
    
    // 创建 Istio 集成
    meshFactory.createMeshIntegration(ServiceMeshFactory.MeshType.ISTIO, istioConfig)
        .onSuccess { result ->
            println("Istio 集成初始化成功: $result")
            
            // 创建 Consul Connect 集成
            meshFactory.createMeshIntegration(ServiceMeshFactory.MeshType.CONSUL_CONNECT, consulConfig)
                .onSuccess { result2 ->
                    println("Consul Connect 集成初始化成功: $result2")
                    
                    // 创建服务网格功能复用工具
                    val meshFeatures = ServiceMeshFeatures.getInstance(vertx)
                    
                    // 启用 Istio 流量管理功能
                    val trafficConfig = JsonObject()
                        .put("virtualService", JsonObject()
                            .put("name", "Traffic VirtualService")
                            .put("hosts", JsonArray().add("traffic.example.com"))
                            .put("gateways", JsonArray().add("traffic-gateway"))
                        )
                        .put("destinationRule", JsonObject()
                            .put("name", "Traffic DestinationRule")
                            .put("host", "traffic-service")
                        )
                    
                    meshFeatures.enableTrafficManagement(ServiceMeshFactory.MeshType.ISTIO, trafficConfig)
                        .onSuccess { result3 ->
                            println("Istio 流量管理功能启用成功: $result3")
                            
                            // 启用 Istio 安全功能
                            val securityConfig = JsonObject()
                                .put("namespace", "default")
                                .put("mtlsMode", "STRICT")
                                .put("authorizationPolicy", JsonObject()
                                    .put("name", "Security AuthorizationPolicy")
                                    .put("namespace", "default")
                                    .put("selector", JsonObject().put("app", "security-app"))
                                )
                            
                            meshFeatures.enableSecurity(ServiceMeshFactory.MeshType.ISTIO, securityConfig)
                                .onSuccess { result4 ->
                                    println("Istio 安全功能启用成功: $result4")
                                    
                                    // 获取 Istio 功能状态
                                    meshFeatures.getFeatureStatus(ServiceMeshFactory.MeshType.ISTIO)
                                        .onSuccess { status ->
                                            println("Istio 功能状态: $status")
                                            
                                            // 关闭 Vertx
                                            vertx.close()
                                                .onSuccess {
                                                    println("Vertx 关闭成功")
                                                }
                                                .onFailure { cause ->
                                                    println("Vertx 关闭失败: ${cause.message}")
                                                }
                                        }
                                        .onFailure { cause ->
                                            println("获取 Istio 功能状态失败: ${cause.message}")
                                        }
                                }
                                .onFailure { cause ->
                                    println("启用 Istio 安全功能失败: ${cause.message}")
                                }
                        }
                        .onFailure { cause ->
                            println("启用 Istio 流量管理功能失败: ${cause.message}")
                        }
                }
                .onFailure { cause ->
                    println("Consul Connect 集成初始化失败: ${cause.message}")
                }
        }
        .onFailure { cause ->
            println("Istio 集成初始化失败: ${cause.message}")
        }
}
